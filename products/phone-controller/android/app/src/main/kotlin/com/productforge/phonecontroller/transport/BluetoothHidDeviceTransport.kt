/*
 * BluetoothHidDeviceTransport — the REAL Classic-BT-HID transport.
 *
 * Slice 2 proved the skeleton (bind the HID_DEVICE proxy, one registerApp() probe,
 * sendReport on the interrupt channel, feed the probe result into the shared verdict
 * engine). Slice 4 makes it a usable input device:
 *
 *   * registers the COMBO descriptor from :hid-core (consumer media remote +
 *     keyboard + gamepad — the shapes emulator receivers consume);
 *   * exposes hold-capable input APIs (key/gamepad press AND release are separate
 *     calls, so hold-to-run works) backed by the pure-JVM report builders;
 *   * can initiate a connection to an already-bonded host (connectTo / bondedHosts)
 *     in addition to waiting for the host to connect;
 *   * releases all inputs on host disconnect and on stop() — no stuck keys;
 *   * surfaces SecurityException (missing runtime Bluetooth permission) as a
 *     transport error instead of a crash.
 *
 * The probe semantics are unchanged from Slice 2: registerApp() failing on an
 * API-28+ phone is exactly the verdict engine's OEM_DISABLED case, and every probe
 * outcome still flows through the SHARED decision model (:capability-core evaluate()),
 * so the Android layer reaches the same verdict the portable Python core would.
 */
package com.productforge.phonecontroller.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.productforge.phonecontroller.capability.ProbeInput
import com.productforge.phonecontroller.capability.Verdict
import com.productforge.phonecontroller.capability.VerdictCode
import com.productforge.phonecontroller.capability.evaluate
import com.productforge.phonecontroller.hid.ComboHidDescriptor
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.GamepadState
import com.productforge.phonecontroller.hid.KeyboardState
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.hid.MouseButton
import com.productforge.phonecontroller.hid.MouseState
import java.util.concurrent.Executors

/**
 * Callbacks the UI layer implements to observe transport state. Grown in Slice 4
 * (the controller UI reacts to registration + per-host connection changes).
 */
interface HidTransportListener {
    /** The runtime probe resolved to a verdict from the shared decision model. */
    fun onVerdict(verdict: Verdict)

    /** registerApp() succeeded — the phone is now advertising as a HID combo device. */
    fun onRegistered()

    /** A HID host connected/disconnected on the control+interrupt channels. */
    fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean)

    /** A non-fatal transport error (bind failure, registerApp rejection, send failure). */
    fun onError(message: String)
}

/**
 * Drives the Classic Bluetooth HID *device* role for the combo controller profile.
 *
 * Lifecycle: [start] -> (proxy binds) -> registerApp() -> [onRegistered] ->
 * host pairs/connects (or [connectTo] a bonded host) -> input calls while connected
 * -> [stop].
 *
 * Threading: UI-thread calls mutate the input states; profile callbacks arrive on a
 * single-worker executor. All mutable state is confined behind @Synchronized.
 *
 * @param context an application Context (used only to bind the profile proxy).
 * @param adapter the default BluetoothAdapter, or null on a device with no Bluetooth.
 * @param listener transport-state sink.
 */
class BluetoothHidDeviceTransport(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val listener: HidTransportListener,
) {

    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false
    private var connectedHost: BluetoothDevice? = null

    private val keyboard = KeyboardState()
    private val gamepad = GamepadState()
    private val mouse = MouseState()

    // registerApp() requires an Executor for its callbacks; a single worker keeps the
    // callback order deterministic.
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    /** SDP record advertised to hosts: a combo keyboard/gamepad/media controller. */
    private val sdpSettings: BluetoothHidDeviceAppSdpSettings by lazy {
        BluetoothHidDeviceAppSdpSettings(
            /* name = */ "Phone Controller",
            /* description = */ "Phone-as-controller — keyboard / gamepad / media remote",
            /* provider = */ "product-forge",
            /* subclass = */ BluetoothHidDevice.SUBCLASS1_COMBO,
            /* descriptors = */ ComboHidDescriptor.DESCRIPTOR,
        )
    }

    /**
     * Bind the HID_DEVICE profile proxy and, once bound, attempt registration.
     *
     * The probe outcome (did registerApp bind + accept?) is what [probeAndEvaluate]
     * later feeds to the shared verdict engine. Returns false immediately when the
     * OS is too old or has no Bluetooth — no proxy bind is even attempted.
     */
    fun start(): Boolean {
        val bt = adapter
        if (bt == null) {
            emitVerdict(hidRoleAvailable = false, blePeripheral = false)
            listener.onError("device has no Bluetooth adapter")
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // API < 28: BluetoothHidDevice does not exist — let the verdict engine
            // classify (OS_TOO_OLD or a BLE-HOGP fallback, per BLE capability).
            emitVerdict(hidRoleAvailable = false, blePeripheral = blePeripheralSupported(bt))
            return false
        }
        return runCatching {
            bt.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        }.getOrElse {
            listener.onError("HID proxy bind failed: ${it.message}")
            false
        }
    }

    /** Unregister and release the profile proxy. Safe to call more than once. */
    @Synchronized
    fun stop() {
        val proxy = hidDevice
        if (proxy != null) {
            if (registered) {
                releaseAllInputs(proxy)
                runCatching { proxy.unregisterApp() }
                registered = false
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        }
        hidDevice = null
        connectedHost = null
        callbackExecutor.shutdown()
    }

    // --- connection management ------------------------------------------------------

    /** True while a host is connected and the app registration is live. */
    @get:Synchronized
    val isConnected: Boolean
        get() = registered && connectedHost != null

    /** The connected host's display name, or null. Needs BLUETOOTH_CONNECT on 31+. */
    @Synchronized
    fun connectedHostName(): String? =
        connectedHost?.let { runCatching { it.name }.getOrNull() ?: it.address }

    /**
     * Bonded (paired) devices this phone could actively connect to as a HID device.
     * Empty until the runtime permission is granted.
     */
    fun bondedHosts(): List<BluetoothDevice> =
        runCatching { adapter?.bondedDevices?.toList() ?: emptyList() }
            .getOrElse {
                listener.onError("cannot list bonded devices: ${it.message}")
                emptyList()
            }

    /**
     * Initiate a connection to an already-bonded host (the alternative to waiting for
     * the host to connect first). Registration must be live.
     */
    @Synchronized
    fun connectTo(device: BluetoothDevice): Boolean {
        val proxy = hidDevice ?: return false
        if (!registered) return false
        return runCatching { proxy.connect(device) }
            .getOrElse {
                listener.onError("connect(${runCatching { device.name }.getOrNull() ?: device.address}) failed: ${it.message}")
                false
            }
    }

    // --- input APIs (all hold-capable: press and release are separate calls) --------

    /**
     * Zero every input collection while KEEPING the registration. Called on pad
     * switches (views vanish mid-hold, so UP events never arrive) and after turbo
     * cancellation — the no-stuck-inputs guarantee for layout changes.
     */
    @Synchronized
    fun releaseHeldInputs() {
        keyboard.clear()
        gamepad.clear()
        mouse.clear()
        val proxy = hidDevice ?: return
        val host = connectedHost ?: return
        if (!registered) return
        runCatching {
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_CONSUMER, MediaButton.RELEASE_REPORT)
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_KEYBOARD, keyboard.report())
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report())
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report())
        }
    }

    /** The connected host's MAC address (per-host prefs key), or null. */
    @Synchronized
    fun connectedHostAddress(): String? = connectedHost?.address

    /** Send a media button as a press-then-release pair (Report 1, tap semantics). */
    @Synchronized
    fun sendMediaButton(button: MediaButton): Boolean {
        val (proxy, host) = liveHost() ?: return false
        val pressed = proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_CONSUMER, button.pressReport())
        val released = proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_CONSUMER, MediaButton.RELEASE_REPORT)
        if (!pressed || !released) listener.onError("sendReport() rejected for ${button.name}")
        return pressed && released
    }

    /** Press/release a keyboard usage (Report 2). Returns false when not connected. */
    @Synchronized
    fun key(usage: Int, down: Boolean): Boolean {
        val (proxy, host) = liveHost() ?: return false
        val changed = if (down) keyboard.keyDown(usage) else keyboard.keyUp(usage)
        if (!changed) return false
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_KEYBOARD, keyboard.report(), "key $usage")
    }

    /** Press/release a keyboard modifier mask (Report 2). */
    @Synchronized
    fun modifier(mask: Int, down: Boolean): Boolean {
        val (proxy, host) = liveHost() ?: return false
        keyboard.setModifier(mask, down)
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_KEYBOARD, keyboard.report(), "modifier $mask")
    }

    /** Press/release a gamepad button (Report 3). */
    @Synchronized
    fun gamepadButton(button: GamepadButton, down: Boolean): Boolean {
        val (proxy, host) = liveHost() ?: return false
        if (down) gamepad.buttonDown(button) else gamepad.buttonUp(button)
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report(), "gamepad ${button.name}")
    }

    /** Hold/release a D-pad direction (Report 3; folded into the hat switch). */
    @Synchronized
    fun dpad(direction: DpadDirection, down: Boolean): Boolean {
        val (proxy, host) = liveHost() ?: return false
        gamepad.dpad(direction, down)
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report(), "dpad ${direction.name}")
    }

    /** Set the LEFT analog stick (Report 3, X/Y). Streams at touch rate — quiet on failure. */
    @Synchronized
    fun leftStick(x: Int, y: Int): Boolean {
        val (proxy, host) = liveHost() ?: return false
        gamepad.setLeftStick(x, y)
        return timedSend(proxy, host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report())
    }

    /** Set the RIGHT analog stick (Report 3, Z/RZ). Streams at touch rate — quiet on failure. */
    @Synchronized
    fun rightStick(z: Int, rz: Int): Boolean {
        val (proxy, host) = liveHost() ?: return false
        gamepad.setRightStick(z, rz)
        return timedSend(proxy, host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report())
    }

    /** Press/release a mouse button (Report 4). Hold Left + move = drag-select. */
    @Synchronized
    fun mouseButton(button: MouseButton, down: Boolean): Boolean {
        val (proxy, host) = liveHost() ?: return false
        if (down) mouse.buttonDown(button) else mouse.buttonUp(button)
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report(), "mouse ${button.name}")
    }

    /** Send a click as a press-then-release pair (Report 4, tap semantics). */
    @Synchronized
    fun mouseClick(button: MouseButton): Boolean {
        val (proxy, host) = liveHost() ?: return false
        mouse.buttonDown(button)
        val pressed = proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report())
        mouse.buttonUp(button)
        val released = proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report())
        if (!pressed || !released) listener.onError("sendReport() rejected for click ${button.name}")
        return pressed && released
    }

    /** Move the pointer by a relative delta (Report 4; counts, -127..127 per report). */
    @Synchronized
    fun mouseMove(dx: Int, dy: Int): Boolean {
        if (dx == 0 && dy == 0) return true
        val (proxy, host) = liveHost() ?: return false
        // Quiet on failure: motion reports stream at touch-event rate, and a single
        // dropped delta is imperceptible — flooding onError would drown real faults.
        return timedSend(proxy, host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report(dx = dx, dy = dy))
    }

    /** Scroll the wheel by whole notches (Report 4; positive = content up). */
    @Synchronized
    fun mouseScroll(notches: Int): Boolean {
        if (notches == 0) return true
        val (proxy, host) = liveHost() ?: return false
        return sendOrReport(proxy, host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report(wheel = notches), "wheel")
    }

    // --- profile proxy + registration callbacks -----------------------------------

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            synchronized(this@BluetoothHidDeviceTransport) { hidDevice = hid }
            // THE OEM-FLAG PROBE: on some API-28+ phones this returns false / never
            // fires onAppStatusChanged(registered=true) because the OEM compiled the
            // HID device role out. That outcome is exactly OEM_DISABLED in the engine.
            val accepted = runCatching {
                hid.registerApp(sdpSettings, null, null, callbackExecutor, hidCallback)
            }.getOrElse {
                listener.onError("registerApp() threw: ${it.message} (missing Bluetooth permission?)")
                false
            }
            if (!accepted) {
                emitVerdict(hidRoleAvailable = false, blePeripheral = blePeripheralSupported(adapter))
                listener.onError("registerApp() was rejected — HID device role unavailable (OEM-gated?)")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                synchronized(this@BluetoothHidDeviceTransport) {
                    hidDevice = null
                    registered = false
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, appRegistered: Boolean) {
            synchronized(this@BluetoothHidDeviceTransport) { registered = appRegistered }
            // registerApp() actually took: the runtime probe says the HID role IS
            // available. Feed that into the shared decision model.
            emitVerdict(hidRoleAvailable = appRegistered, blePeripheral = blePeripheralSupported(adapter))
            if (appRegistered) listener.onRegistered()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val connected = state == BluetoothProfile.STATE_CONNECTED
            synchronized(this@BluetoothHidDeviceTransport) {
                connectedHost = if (connected) device else null
                if (!connected) {
                    // No stuck inputs across a reconnect: forget everything held.
                    keyboard.clear()
                    gamepad.clear()
                    mouse.clear()
                }
            }
            listener.onConnectionStateChanged(device, connected)
        }
    }

    // --- verdict bridge -----------------------------------------------------------

    /**
     * Build a [ProbeInput] from the current runtime facts and evaluate it with the
     * SHARED decision model — the Android transport and the portable Python core
     * reach the same verdict from the same inputs.
     */
    fun probeAndEvaluate(hidRoleAvailable: Boolean, blePeripheral: Boolean): Verdict =
        evaluate(
            ProbeInput(
                platform = "android",
                osApiLevel = Build.VERSION.SDK_INT,
                hidDeviceRoleAvailable = hidRoleAvailable,
                blePeripheralSupported = blePeripheral,
            ),
        )

    private fun emitVerdict(hidRoleAvailable: Boolean, blePeripheral: Boolean) {
        val verdict = probeAndEvaluate(hidRoleAvailable, blePeripheral)
        // A SUPPORTED verdict without a live registration would be a contract slip;
        // the engine only returns SUPPORTED_CLASSIC_HID when hidRoleAvailable is true.
        check(verdict.code != VerdictCode.SUPPORTED_CLASSIC_HID || hidRoleAvailable) {
            "verdict/probe mismatch"
        }
        listener.onVerdict(verdict)
    }

    // --- helpers --------------------------------------------------------------------

    /** The (proxy, host) pair when input can flow; null (and no-op) otherwise. */
    private fun liveHost(): Pair<BluetoothHidDevice, BluetoothDevice>? {
        val proxy = hidDevice ?: return null
        val host = connectedHost ?: return null
        if (!registered) return null
        return proxy to host
    }

    private fun sendOrReport(
        proxy: BluetoothHidDevice,
        host: BluetoothDevice,
        reportId: Int,
        payload: ByteArray,
        what: String,
    ): Boolean {
        val ok = timedSend(proxy, host, reportId, payload)
        if (!ok) listener.onError("sendReport() rejected for $what")
        return ok
    }

    /** sendReport() wrapped in the rolling input-path latency measurement. */
    private fun timedSend(
        proxy: BluetoothHidDevice,
        host: BluetoothDevice,
        reportId: Int,
        payload: ByteArray,
    ): Boolean {
        val start = System.nanoTime()
        val ok = runCatching { proxy.sendReport(host, reportId, payload) }.getOrDefault(false)
        val micros = (System.nanoTime() - start) / 1_000
        latencyRing[latencyIndex % latencyRing.size] = micros
        latencyIndex += 1
        return ok
    }

    private val latencyRing = LongArray(128)
    private var latencyIndex = 0

    /**
     * Rolling average of the app-side input path (UI call → sendReport handed to the
     * Bluetooth stack), in microseconds. Measured, not asserted — link/host latency
     * rides on top of this and is not observable from the device role.
     */
    @Synchronized
    fun averageSendMicros(): Long {
        val n = minOf(latencyIndex, latencyRing.size)
        if (n == 0) return 0
        var sum = 0L
        for (i in 0 until n) sum += latencyRing[i]
        return sum / n
    }

    /** All-zero reports for every collection — called before unregistering. */
    private fun releaseAllInputs(proxy: BluetoothHidDevice) {
        val host = connectedHost ?: return
        keyboard.clear()
        gamepad.clear()
        mouse.clear()
        runCatching {
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_CONSUMER, MediaButton.RELEASE_REPORT)
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_KEYBOARD, keyboard.report())
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_GAMEPAD, gamepad.report())
            proxy.sendReport(host, ComboHidDescriptor.REPORT_ID_MOUSE, mouse.report())
        }
    }

    @Suppress("DEPRECATION")
    private fun blePeripheralSupported(bt: BluetoothAdapter?): Boolean =
        runCatching { bt?.isMultipleAdvertisementSupported == true }.getOrDefault(false)

    companion object {
        /**
         * Stable fingerprint of the CURRENT combo descriptor. Hosts cache the
         * descriptor per bond at pairing time, so the UI remembers this value per
         * connected host and warns when a bond predates the running descriptor
         * (the "touchpad looked dead after the update" incident, 2026-07-23).
         */
        fun descriptorFingerprint(): Int = ComboHidDescriptor.DESCRIPTOR.contentHashCode()
    }
}
