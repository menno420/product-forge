/*
 * BluetoothHidDeviceTransport — the REAL Classic-BT-HID transport (Slice-2 skeleton).
 *
 * This is the on-device counterpart to the platform-agnostic verdict engine. It:
 *   1. binds the HID_DEVICE profile proxy via BluetoothAdapter.getProfileProxy(),
 *   2. attempts ONE registerApp() with a fixed media-remote SDP record + report
 *      descriptor — this is the runtime OEM-flag probe (registerApp() failing on an
 *      API-28+ phone is exactly the OEM-disabled case the verdict engine names),
 *   3. sends input reports on the HID interrupt channel via sendReport(), and
 *   4. feeds the probe RESULT into the shared decision model
 *      (`com.productforge.phonecontroller.capability.evaluate`) so the Android layer
 *      consumes the same verdicts as the portable Python core.
 *
 * SKELETON SCOPE (Slice 2): a fixed media-remote HID device + registerApp/sendReport
 * is enough to prove the transport. Customisable layouts, pairing UI, reconnection,
 * and the BLE-HOGP fallback path are Slice 3+. This file is Android-app source and is
 * NOT built by the CI lane (which builds only the SDK-free :capability-core); it
 * compiles once the AGP build is wired in (see app/build.gradle.kts + README).
 *
 * Grounding: BluetoothHidDevice requires API 28 (Android 9); registerApp() can still
 * fail on API-28+ devices because the HID device role sits behind an OEM compile-flag
 * — so support is PROBED here, never assumed (idea doc
 * menno420/idea-engine : ideas/product-forge/bt-controller-plan-2026-07-17.md).
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
import java.util.concurrent.Executors

/**
 * Callbacks the UI layer implements to observe transport state. Kept minimal for the
 * skeleton; Slice 3 grows this (pairing progress, per-device connection state, etc.).
 */
interface HidTransportListener {
    /** The runtime probe resolved to a verdict from the shared decision model. */
    fun onVerdict(verdict: Verdict)

    /** registerApp() succeeded — the phone is now advertising as a HID media remote. */
    fun onRegistered()

    /** A HID host connected/disconnected on the control+interrupt channels. */
    fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean)

    /** A non-fatal transport error (bind failure, registerApp rejection, send failure). */
    fun onError(message: String)
}

/**
 * Drives the Classic Bluetooth HID *device* role for a fixed media-remote profile.
 *
 * Lifecycle: [start] -> (proxy binds) -> registerApp() -> [onRegistered] ->
 * [sendMediaButton] while a host is connected -> [stop].
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

    // registerApp() requires an Executor for its callbacks; a single worker is plenty
    // for the skeleton's one-report device.
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    /** Fixed SDP record advertised to hosts: a Consumer-Control "media remote". */
    private val sdpSettings: BluetoothHidDeviceAppSdpSettings by lazy {
        BluetoothHidDeviceAppSdpSettings(
            /* name = */ "Phone Controller (Media Remote)",
            /* description = */ "Customisable phone-as-controller — media-remote profile",
            /* provider = */ "product-forge",
            /* subclass = */ BluetoothHidDevice.SUBCLASS1_COMBO,
            /* descriptors = */ MediaRemoteHidDescriptor.DESCRIPTOR,
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
        return bt.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    /** Unregister and release the profile proxy. Safe to call more than once. */
    fun stop() {
        val proxy = hidDevice
        if (proxy != null) {
            if (registered) {
                runCatching { proxy.unregisterApp() }
                registered = false
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        }
        hidDevice = null
        connectedHost = null
        callbackExecutor.shutdown()
    }

    /**
     * Send a media button as a press-then-release pair on the HID interrupt channel.
     *
     * @return true if both reports were handed to sendReport() for a connected host.
     */
    fun sendMediaButton(button: MediaButton): Boolean {
        val proxy = hidDevice ?: return false
        val host = connectedHost ?: return false
        if (!registered) return false
        val pressed = proxy.sendReport(host, MediaRemoteHidDescriptor.REPORT_ID, button.pressReport())
        val released = proxy.sendReport(host, MediaRemoteHidDescriptor.REPORT_ID, MediaButton.RELEASE_REPORT)
        if (!pressed || !released) listener.onError("sendReport() rejected for ${button.name}")
        return pressed && released
    }

    // --- profile proxy + registration callbacks -----------------------------------

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid
            // THE OEM-FLAG PROBE: on some API-28+ phones this returns false / never
            // fires onAppStatusChanged(registered=true) because the OEM compiled the
            // HID device role out. That outcome is exactly OEM_DISABLED in the engine.
            val accepted = hid.registerApp(sdpSettings, null, null, callbackExecutor, hidCallback)
            if (!accepted) {
                emitVerdict(hidRoleAvailable = false, blePeripheral = blePeripheralSupported(adapter))
                listener.onError("registerApp() was rejected — HID device role unavailable (OEM-gated?)")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                registered = false
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, appRegistered: Boolean) {
            registered = appRegistered
            // registerApp() actually took: the runtime probe says the HID role IS
            // available. Feed that into the shared decision model.
            emitVerdict(hidRoleAvailable = appRegistered, blePeripheral = blePeripheralSupported(adapter))
            if (appRegistered) listener.onRegistered()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val connected = state == BluetoothProfile.STATE_CONNECTED
            connectedHost = if (connected) device else null
            listener.onConnectionStateChanged(device, connected)
        }
    }

    // --- verdict bridge -----------------------------------------------------------

    /**
     * Build a [ProbeInput] from the current runtime facts and evaluate it with the
     * SHARED decision model. This is the crux of Slice 2: the Android transport and
     * the portable Python core reach the same verdict from the same inputs.
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

    @Suppress("DEPRECATION")
    private fun blePeripheralSupported(bt: BluetoothAdapter?): Boolean =
        bt?.isMultipleAdvertisementSupported == true
}
