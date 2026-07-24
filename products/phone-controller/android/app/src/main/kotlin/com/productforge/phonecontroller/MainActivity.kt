/*
 * MainActivity — the controller (Slice 4 usable · Slice 5 layouts/landscape ·
 * Slice 6 editor/sticks/ergonomics · Slice 8 dark theme/focus/NDS).
 *
 * Owns: permission flow → live probe → pairing actions; the layout selection
 * (built-in presets + user layouts, persisted globally AND per connected host);
 * the custom-layout editor; turbo + gyro lifecycles; dim/immersive/focus toggles
 * (focus = pure controller mode: all chrome hidden, translucent exit chip); the
 * app-wide background; the stale-pairing warning (hosts cache the HID descriptor
 * per bond — a bond made before an app update with a descriptor change silently
 * drops the new reports; field-diagnosed 2026-07-23).
 *
 * Selection keys: "b:<ordinal>" = built-in pad, "c:<id>" = custom layout.
 * Still plain android.app.Activity + programmatic widgets — no AndroidX.
 */
package com.productforge.phonecontroller

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.productforge.phonecontroller.capability.Verdict
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.KeyUsage
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.hid.MouseButton
import com.productforge.phonecontroller.layout.CustomLayout
import com.productforge.phonecontroller.layout.LayoutStore
import com.productforge.phonecontroller.layout.PadAction
import com.productforge.phonecontroller.layout.PadActionType
import com.productforge.phonecontroller.layout.PadButtonSpec
import com.productforge.phonecontroller.layout.PadFx
import com.productforge.phonecontroller.layout.PadPositioned
import com.productforge.phonecontroller.layout.PadShape
import com.productforge.phonecontroller.layout.PadWidgetSpec
import com.productforge.phonecontroller.layout.PadWidgetType
import com.productforge.phonecontroller.ui.ButtonStyler
import com.productforge.phonecontroller.transport.BluetoothHidDeviceTransport
import com.productforge.phonecontroller.transport.HidTransportListener
import com.productforge.phonecontroller.ui.Combos
import com.productforge.phonecontroller.ui.ControllerPads
import com.productforge.phonecontroller.ui.CustomPadView
import com.productforge.phonecontroller.ui.GyroDriver
import com.productforge.phonecontroller.ui.GyroToggle
import com.productforge.phonecontroller.ui.GyroVisualizerView
import com.productforge.phonecontroller.ui.Haptics
import com.productforge.phonecontroller.overlay.GestureRecorderView
import com.productforge.phonecontroller.overlay.GestureStore
import com.productforge.phonecontroller.overlay.OverlayPlayService
import com.productforge.phonecontroller.overlay.SavedGesture
import com.productforge.phonecontroller.overlay.TapAccessibilityService
import com.productforge.phonecontroller.hid.TouchGestureCodec
import com.productforge.phonecontroller.ui.MacroRunner
import com.productforge.phonecontroller.ui.PadHost
import com.productforge.phonecontroller.ui.Supporter
import com.productforge.phonecontroller.ui.TextTyper
import com.productforge.phonecontroller.ui.TouchpadConfig
import com.productforge.phonecontroller.ui.TurboEngine
import com.productforge.phonecontroller.ui.VoiceCommand
import com.productforge.phonecontroller.ui.VoiceDriver
import com.productforge.phonecontroller.ui.VoiceStore

class MainActivity : Activity(), HidTransportListener, PadHost {

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var padContainer: FrameLayout

    private var transport: BluetoothHidDeviceTransport? = null
    private val turbo = TurboEngine()
    private val layoutStore by lazy { LayoutStore(prefs()) }

    private var currentSelection: String = "b:0"
    private var lastStatus: CharSequence = ""
    private var lastDetail: CharSequence = ""
    private var dimmed = false
    private var immersive = false
    private var focusMode = false
    private var autoConnectAttempted = false
    private var editingLayout: CustomLayout? = null
    private var editingView: CustomPadView? = null
    private var editingIsNew = false
    private var preEditSelection: String = "b:0"

    /** Chrome hidden by focus mode (status/detail/action rows or the side panel). */
    private val chromeViews = mutableListOf<View>()
    private var focusChip: TextView? = null
    private var rootFrame: FrameLayout? = null

    private fun prefs() = getPreferences(Context.MODE_PRIVATE)

    private val touchpadConfig = object : TouchpadConfig {
        override var sensitivity: Float
            get() = prefs().getFloat(PREF_SENSITIVITY, 1.0f)
            set(value) {
                prefs().edit().putFloat(PREF_SENSITIVITY, value).apply()
            }
        override val invertScroll: Boolean
            get() = prefs().getBoolean(PREF_SCROLL_INVERT, false)
    }

    private val macroRunner by lazy {
        // MACRO is filtered here too (belt-and-braces with the parser's no-nesting).
        MacroRunner { type, code ->
            if (type == PadActionType.MACRO.name) null else resolveRaw(type, code)
        }
    }

    private val gestureStore by lazy { GestureStore(prefs()) }
    private val voiceStore by lazy { VoiceStore(prefs()) }
    private val voiceDriver by lazy {
        VoiceDriver(this, { voiceStore.all() }) { command -> fireVoiceCommand(command) }
    }

    /** Voice commands fire as a short tap (press + timed release). */
    private fun fireVoiceCommand(command: VoiceCommand) {
        val action = resolveRaw(command.actionType, command.actionCode) ?: return
        action(true)
        statusView.postDelayed({ action(false) }, VOICE_TAP_MS)
        setDetail(getString(R.string.voice_fired_fmt, command.phrase, command.actionLabel))
    }

    private fun voiceEnabled(): Boolean = prefs().getBoolean(PREF_VOICE, false)

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startVoiceIfEnabled() {
        if (voiceEnabled() && hasMicPermission()) voiceDriver.start()
    }

    override fun onResume() {
        super.onResume()
        startVoiceIfEnabled()
    }

    override fun onPause() {
        // Foreground-only listening: the mic is never live while the app isn't.
        voiceDriver.stop()
        super.onPause()
    }

    // Gyro routing (Slice 14): the driver emits a normalized vector; we route it to
    // the chosen target only while ARMED, and always feed the live visualizer.
    private enum class GyroTarget { RIGHT_STICK, LEFT_STICK, MOUSE }

    private var gyroArmed = false
    private var gyroPreview = false
    private var gyroVisualizer: GyroVisualizerView? = null
    private var gyroMouseRemX = 0f
    private var gyroMouseRemY = 0f

    private val gyroDriver by lazy {
        GyroDriver(this) { nx, ny -> onGyroSample(nx, ny) }
    }

    private fun gyroTarget(): GyroTarget =
        GyroTarget.entries.getOrElse(prefs().getInt(PREF_GYRO_TARGET, 0)) { GyroTarget.RIGHT_STICK }

    /** Push driver settings from prefs (called before starting the sensor). */
    private fun applyGyroSettings() {
        // Sensitivity 0..100 → full-tilt 40°(least) .. 10°(most).
        val sens = prefs().getInt(PREF_GYRO_SENS, 50).coerceIn(0, 100)
        gyroDriver.fullTiltDegrees = 40f - (sens / 100f) * 30f
        gyroDriver.invertX = prefs().getBoolean(PREF_GYRO_INV_X, false)
        gyroDriver.invertY = prefs().getBoolean(PREF_GYRO_INV_Y, false)
    }

    /** Start/stop the sensor to match armed||preview intent. */
    private fun syncGyroSensor() {
        val want = gyroArmed || gyroPreview
        if (want && !gyroDriver.running) {
            applyGyroSettings()
            gyroMouseRemX = 0f; gyroMouseRemY = 0f
            gyroDriver.start()
        } else if (!want && gyroDriver.running) {
            gyroDriver.stop()
        }
    }

    /** Full disarm + sensor off (pad switch, disconnect, editor, destroy). */
    private fun stopGyro() {
        gyroArmed = false
        gyroPreview = false
        gyroDriver.stop()
    }

    private fun onGyroSample(nx: Float, ny: Float) {
        gyroVisualizer?.update(nx, ny)
        if (!gyroArmed) return
        when (gyroTarget()) {
            GyroTarget.RIGHT_STICK ->
                transport?.rightStick((nx * 127f).toInt().coerceIn(-127, 127), (ny * 127f).toInt().coerceIn(-127, 127))
            GyroTarget.LEFT_STICK ->
                transport?.leftStick((nx * 127f).toInt().coerceIn(-127, 127), (ny * 127f).toInt().coerceIn(-127, 127))
            GyroTarget.MOUSE -> {
                // Rate mapping: tilt = pointer velocity, remainder-carried (touchpad style).
                gyroMouseRemX += nx * GYRO_MOUSE_SPEED
                gyroMouseRemY += ny * GYRO_MOUSE_SPEED
                val dx = gyroMouseRemX.toInt()
                val dy = gyroMouseRemY.toInt()
                if (dx != 0 || dy != 0) {
                    gyroMouseRemX -= dx; gyroMouseRemY -= dy
                    transport?.mouseMove(dx, dy)
                }
            }
        }
    }

    private val textTyper by lazy {
        TextTyper(
            key = { usage, down -> transport?.key(usage, down) },
            modifier = { mask, down -> transport?.modifier(mask, down) },
            onProgress = { typed, total, skipped, done ->
                setDetail(
                    if (done) {
                        getString(R.string.typed_done_fmt, typed, skipped)
                    } else {
                        getString(R.string.typing_progress_fmt, typed, total)
                    },
                )
            },
        )
    }

    private val gyroToggle = object : GyroToggle {
        override val available: Boolean get() = gyroDriver.available
        override val running: Boolean get() = gyroArmed
        override fun toggle(): Boolean {
            gyroArmed = !gyroArmed
            gyroMouseRemX = 0f; gyroMouseRemY = 0f
            syncGyroSensor()
            return gyroArmed
        }
        override fun recenter() = gyroDriver.recenter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Haptics.enabled = prefs().getBoolean(PREF_HAPTICS, true)
        Supporter.unlocked = prefs().getBoolean(PREF_SUPPORTER, false)
        turbo.setRateHz(prefs().getInt(PREF_TURBO_HZ, 10))
        currentSelection = prefs().getString(PREF_SELECTION, "b:0") ?: "b:0"
        lastStatus = getString(R.string.probing)
        buildUi()
        ensurePermissionsThenStart()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
    }

    override fun onDestroy() {
        voiceDriver.stop()
        textTyper.cancel()
        macroRunner.cancel()
        stopGyro()
        turbo.cancelAll()
        transport?.stop()
        transport = null
        super.onDestroy()
    }

    // --- hardware volume keys as inputs (Slice 10) ------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        volumeAction(keyCode)?.let { action ->
            // Holds ride the initial down/up pair; auto-repeats are noise here.
            if (event.repeatCount == 0) action(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        volumeAction(keyCode)?.let { action ->
            action(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * The mapped sink for a volume key, or null when the key should behave normally
     * (mapping off, not connected, or the editor is open). Volume-up = the RIGHT
     * half of each pair, volume-down = the LEFT.
     */
    private fun volumeAction(keyCode: Int): ((Boolean) -> Unit)? {
        val isUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isUp && !isDown) return null
        if (editingLayout != null) return null
        if (transport?.isConnected != true) return null
        return when (prefs().getInt(PREF_VOLKEYS, 0)) {
            1 -> if (isUp) ({ d -> onGamepadButton(GamepadButton.R1, d) }) else ({ d -> onGamepadButton(GamepadButton.L1, d) })
            2 -> if (isUp) ({ d -> onGamepadButton(GamepadButton.R2, d) }) else ({ d -> onGamepadButton(GamepadButton.L2, d) })
            3 -> if (isUp) ({ d -> onKey(KeyUsage.PAGE_UP, d) }) else ({ d -> onKey(KeyUsage.PAGE_DOWN, d) })
            else -> null
        }
    }

    // --- permission flow ------------------------------------------------------------

    private fun ensurePermissionsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missing = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                setStatus(getString(R.string.requesting_permissions))
                requestPermissions(missing.toTypedArray(), RC_BLUETOOTH)
                return
            }
        }
        startTransport()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            RC_BLUETOOTH -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startTransport()
                } else {
                    setStatus(getString(R.string.permission_denied))
                }
            }
            RC_MIC -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    prefs().edit().putBoolean(PREF_VOICE, true).apply()
                    startVoiceIfEnabled()
                    setDetail(getString(R.string.voice_enabled_hint))
                } else {
                    prefs().edit().putBoolean(PREF_VOICE, false).apply()
                    setDetail(getString(R.string.voice_mic_denied))
                }
            }
        }
    }

    private fun startTransport() {
        transport?.stop()
        autoConnectAttempted = false
        setStatus(getString(R.string.probing))
        setDetail("")
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        transport = BluetoothHidDeviceTransport(applicationContext, adapter, this).also { it.start() }
    }

    // --- actions ----------------------------------------------------------------------

    private fun makeDiscoverable() {
        runCatching {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS),
            )
        }.onFailure { setDetail(getString(R.string.error_fmt, it.message ?: "discoverable request failed")) }
    }

    private fun pickBondedAndConnect() {
        val t = transport ?: return
        val devices = t.bondedHosts()
        if (devices.isEmpty()) {
            setDetail(getString(R.string.no_bonded_devices))
            return
        }
        val names = devices.map { d -> runCatching { d.name }.getOrNull() ?: d.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.connect_dialog_title)
            .setItems(names) { _, which ->
                setDetail(getString(R.string.connecting_fmt, names[which]))
                t.connectTo(devices[which])
            }
            .show()
    }

    private fun toggleDim() {
        dimmed = !dimmed
        val lp = window.attributes
        lp.screenBrightness =
            if (dimmed) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    @Suppress("DEPRECATION")
    private fun toggleImmersive() {
        immersive = !immersive
        window.decorView.systemUiVisibility = if (immersive) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            0
        }
    }

    /**
     * Pure controller mode (Slice 8, owner ask): hide ALL chrome so only the pad is
     * on screen, with a small translucent chip to come back. Entering also goes
     * immersive; exiting restores. Blocked while the editor is open (Save would be
     * unreachable).
     */
    private fun setFocusMode(on: Boolean) {
        if (on && editingLayout != null) {
            setDetail(getString(R.string.focus_blocked_editing))
            return
        }
        if (focusMode == on) return
        focusMode = on
        applyFocus()
        if (immersive != on) toggleImmersive()
    }

    private fun applyFocus() {
        chromeViews.forEach { it.visibility = if (focusMode) View.GONE else View.VISIBLE }
        focusChip?.visibility = if (focusMode) View.VISIBLE else View.GONE
    }

    // --- HidTransportListener ---------------------------------------------------------

    override fun onVerdict(verdict: Verdict) {
        runOnUiThread {
            setStatus(getString(R.string.verdict_fmt, verdict.code.name, verdict.headline))
            // A radio that is simply OFF would otherwise probe as a fallback verdict —
            // say the honest, actionable thing instead (owner recording, 2026-07-24).
            val btOff = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.isEnabled == false
            setDetail(
                when {
                    verdict.coreLoopAvailable -> ""
                    btOff -> getString(R.string.bt_off_hint)
                    else -> verdict.reason
                },
            )
        }
    }

    override fun onRegistered() {
        runOnUiThread {
            setStatus(getString(R.string.registered_hint))
            // Silent auto-reconnect to the last host, once per registration.
            if (!autoConnectAttempted) {
                autoConnectAttempted = true
                val lastAddr = prefs().getString(PREF_LAST_HOST, null)
                if (lastAddr != null) {
                    transport?.bondedHosts()?.firstOrNull { it.address == lastAddr }
                        ?.let { transport?.connectTo(it) }
                }
            }
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean) {
        runOnUiThread {
            if (connected) {
                val name = transport?.connectedHostName()
                    ?: runCatching { device?.name }.getOrNull()
                    ?: device?.address
                    ?: getString(R.string.unknown_device)
                setStatus(getString(R.string.connected_fmt, name))
                device?.address?.let { addr -> onHostConnected(addr) }
            } else {
                textTyper.cancel()
                macroRunner.cancel()
                turbo.cancelAll()
                stopGyro()
                setStatus(getString(R.string.disconnected_hint))
            }
        }
    }

    /** Per-host bookkeeping: last host, stale-bond warning, remembered layout. */
    private fun onHostConnected(addr: String) {
        prefs().edit().putString(PREF_LAST_HOST, addr).apply()

        val fp = BluetoothHidDeviceTransport.descriptorFingerprint()
        val seen = prefs().getInt("$PREF_DESC_PREFIX$addr", 0)
        if (seen != 0 && seen != fp) {
            setDetail(getString(R.string.stale_pairing_warning))
        }
        prefs().edit().putInt("$PREF_DESC_PREFIX$addr", fp).apply()

        prefs().getString("$PREF_HOST_LAYOUT_PREFIX$addr", null)?.let { remembered ->
            if (remembered != currentSelection && selectionExists(remembered)) {
                showSelection(remembered)
                rebuildSpinnerSelection()
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { setDetail(getString(R.string.error_fmt, message)) }
    }

    // --- PadHost ------------------------------------------------------------------------

    override fun onGamepadButton(button: GamepadButton, down: Boolean) {
        transport?.gamepadButton(button, down)
    }

    override fun onDpad(direction: DpadDirection, down: Boolean) {
        transport?.dpad(direction, down)
    }

    override fun onKey(usage: Int, down: Boolean) {
        transport?.key(usage, down)
    }

    override fun onModifier(mask: Int, down: Boolean) {
        transport?.modifier(mask, down)
    }

    override fun onMediaTap(button: MediaButton) {
        transport?.sendMediaButton(button)
    }

    override fun onMouseMove(dx: Int, dy: Int) {
        transport?.mouseMove(dx, dy)
    }

    override fun onMouseScroll(notches: Int) {
        transport?.mouseScroll(notches)
    }

    override fun onMouseButton(button: MouseButton, down: Boolean) {
        transport?.mouseButton(button, down)
    }

    override fun onMouseClick(button: MouseButton) {
        transport?.mouseClick(button)
    }

    override fun onLeftStick(x: Int, y: Int) {
        transport?.leftStick(x, y)
    }

    override fun onRightStick(z: Int, rz: Int) {
        transport?.rightStick(z, rz)
    }

    // --- layout selection ----------------------------------------------------------------

    // New pads are APPENDED, never inserted: saved selections are "b:<ordinal>"
    // strings, so existing ordinals must stay stable across updates.
    private enum class Pad(val labelRes: Int) {
        FULL_GAMEPAD(R.string.layout_full_gamepad),
        GBA(R.string.layout_gba),
        ANALOG(R.string.layout_analog),
        TOUCHPAD(R.string.layout_touchpad),
        KEYBOARD(R.string.layout_keyboard),
        EMU_KEYS(R.string.layout_emu_keys),
        MEDIA(R.string.layout_media),
        NDS(R.string.layout_nds),
        PRESENTER(R.string.layout_presenter),
        SHORTCUTS(R.string.layout_shortcuts),
    }

    private fun selectionKeys(): List<String> =
        Pad.entries.map { "b:${it.ordinal}" } + layoutStore.all().map { "c:${it.id}" }

    private fun selectionLabels(): List<String> =
        Pad.entries.map { getString(it.labelRes) } + layoutStore.all().map { it.name }

    private fun selectionExists(key: String): Boolean = key in selectionKeys()

    private fun deadzone(): Float = prefs().getFloat(PREF_DEADZONE, 0.08f)

    /** App-wide background (Slice 8). 0 = unset sentinel → default dark slate. */
    private fun appBg(): Int {
        val stored = prefs().getInt(PREF_APP_BG, 0)
        return if (stored == 0) ButtonStyler.DEFAULT_APP_BG else stored
    }

    private fun pickAppBackground() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_background)
            .setItems(ButtonStyler.PAD_BACKGROUNDS.map { it.first }.toTypedArray()) { _, which ->
                prefs().edit().putInt(PREF_APP_BG, ButtonStyler.PAD_BACKGROUNDS[which].second ?: 0).apply()
                applyAppBackground()
            }
            .show()
    }

    private fun applyAppBackground() {
        rootFrame?.setBackgroundColor(appBg())
        window.statusBarColor = appBg()
        window.navigationBarColor = appBg()
    }

    private fun showSelection(key: String) {
        // Leaving a pad mid-hold: views vanish and UP events never arrive, so zero
        // everything and stop the auto-repeaters/sensor first (guard recipe).
        macroRunner.cancel()
        turbo.cancelAll()
        stopGyro()
        transport?.releaseHeldInputs()
        editingLayout = null
        editingView = null

        currentSelection = key
        prefs().edit().putString(PREF_SELECTION, key).apply()
        transport?.connectedHostAddress()?.let { addr ->
            prefs().edit().putString("$PREF_HOST_LAYOUT_PREFIX$addr", key).apply()
        }

        padContainer.removeAllViews()
        val view: View = if (key.startsWith("c:")) {
            val layout = layoutStore.byId(key.removePrefix("c:"))
            if (layout == null) {
                showSelection("b:0")
                return
            }
            CustomPadView(
                this, layout, editMode = false, actionResolver = ::resolveAction, turbo = turbo,
                host = this, gyro = gyroToggle, deadzonePct = deadzone(),
            )
        } else {
            when (Pad.entries.getOrElse(key.removePrefix("b:").toIntOrNull() ?: 0) { Pad.FULL_GAMEPAD }) {
                Pad.FULL_GAMEPAD -> ControllerPads.buildGamepadPad(this, this)
                Pad.GBA -> ControllerPads.buildGbaPad(this, this)
                Pad.ANALOG -> ControllerPads.buildAnalogPad(
                    this, this, deadzone(), gyroToggle,
                    getString(R.string.gyro_on), getString(R.string.gyro_off),
                )
                Pad.TOUCHPAD -> ControllerPads.buildTouchpadPad(
                    this, this, touchpadConfig,
                    getString(R.string.touchpad_hint), getString(R.string.sensitivity),
                )
                Pad.KEYBOARD -> ControllerPads.buildKeyboardPad(this, this)
                Pad.EMU_KEYS -> ControllerPads.buildEmuKeysPad(this, this)
                Pad.MEDIA -> ControllerPads.buildMediaPad(this, this)
                Pad.NDS -> ControllerPads.buildNdsPad(
                    this, this, touchpadConfig,
                    prefs().getBoolean(PREF_NDS_PEN, true),
                    { on -> prefs().edit().putBoolean(PREF_NDS_PEN, on).apply() },
                    getString(R.string.pen_on), getString(R.string.pen_off),
                    getString(R.string.sensitivity),
                )
                Pad.PRESENTER -> ControllerPads.buildPresenterPad(this, this, touchpadConfig)
                Pad.SHORTCUTS -> ControllerPads.buildShortcutsPad(this, this)
            }
        }
        padContainer.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    /**
     * Resolve a (type name, code) pair to its transport sink; null on anything
     * malformed. Shared by pad buttons AND macro steps — one action vocabulary.
     */
    private fun resolveRaw(typeName: String, code: String): ((Boolean) -> Unit)? =
        runCatching<(Boolean) -> Unit> {
            when (PadActionType.valueOf(typeName)) {
                PadActionType.GAMEPAD -> {
                    val b = GamepadButton.valueOf(code)
                    ({ down -> onGamepadButton(b, down) })
                }
                PadActionType.DPAD -> {
                    val d = DpadDirection.valueOf(code)
                    ({ down -> onDpad(d, down) })
                }
                PadActionType.KEY -> {
                    val usage = code.toInt()
                    ({ down -> onKey(usage, down) })
                }
                PadActionType.MODIFIER -> {
                    val mask = code.toInt()
                    ({ down -> onModifier(mask, down) })
                }
                PadActionType.MEDIA -> {
                    val m = MediaButton.valueOf(code)
                    ({ down -> if (down) onMediaTap(m) })
                }
                PadActionType.MOUSE -> {
                    val mb = MouseButton.valueOf(code)
                    ({ down -> onMouseButton(mb, down) })
                }
                PadActionType.COMBO -> {
                    val (mask, usage) = Combos.parse(code) ?: error("bad combo")
                    ({ down ->
                        // Modifiers wrap the key: press before, release after.
                        if (down) {
                            if (mask != 0) onModifier(mask, true)
                            onKey(usage, true)
                        } else {
                            onKey(usage, false)
                            if (mask != 0) onModifier(mask, false)
                        }
                    })
                }
                PadActionType.MACRO -> {
                    ({ down -> if (down) macroRunner.play(code) })
                }
                PadActionType.GESTURE -> {
                    // Inert in remote HID mode — a screen gesture only means
                    // something in Play-on-this-phone overlay mode (handled there).
                    ({ _ -> })
                }
            }
        }.getOrNull()

    /** Map a custom-layout action spec to the matching transport call. */
    private fun resolveAction(spec: PadButtonSpec): (Boolean) -> Unit =
        resolveRaw(spec.action.type.name, spec.action.code)
            ?: { _ -> } // a corrupt action never crashes the pad

    // --- layout manager + editor -----------------------------------------------------------

    private fun openLayoutManager() {
        val layouts = layoutStore.all()
        val items = layouts.map { it.name } +
            getString(R.string.new_layout) + getString(R.string.layout_import)
        AlertDialog.Builder(this)
            .setTitle(R.string.layouts_title)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < layouts.size -> layoutOptions(layouts[which])
                    which == layouts.size -> newLayoutFlow()
                    else -> importLayoutDialog()
                }
            }
            .show()
    }

    private fun layoutOptions(layout: CustomLayout) {
        // Label→handler pairs (indexes can never drift as entries are added).
        val entries = listOf<Pair<String, () -> Unit>>(
            getString(R.string.layout_use) to {
                showSelection("c:${layout.id}")
                rebuildSpinnerSelection()
            },
            getString(R.string.layout_edit) to { startEditor(layout) },
            getString(R.string.layout_background) to {
                AlertDialog.Builder(this)
                    .setTitle(R.string.layout_background)
                    .setItems(ButtonStyler.PAD_BACKGROUNDS.map { it.first }.toTypedArray()) { _, bg ->
                        layout.bgColorArgb = ButtonStyler.PAD_BACKGROUNDS[bg].second
                        layoutStore.save(layout)
                        if (currentSelection == "c:${layout.id}") showSelection(currentSelection)
                    }
                    .show()
                Unit
            },
            getString(R.string.layout_share) to { shareLayout(layout) },
            getString(R.string.layout_duplicate) to {
                val copy = layout.duplicate(
                    "cl_${System.currentTimeMillis()}",
                    getString(R.string.layout_copy_name_fmt, layout.name),
                )
                layoutStore.save(copy)
                rebuildSpinnerSelection()
            },
            getString(R.string.layout_rename) to {
                promptText(getString(R.string.layout_rename), layout.name) { name ->
                    layout.name = name.ifBlank { layout.name }
                    layoutStore.save(layout)
                    rebuildSpinnerSelection()
                }
            },
            getString(R.string.layout_delete) to {
                layoutStore.delete(layout.id)
                if (currentSelection == "c:${layout.id}") showSelection("b:0")
                rebuildSpinnerSelection()
            },
        )
        AlertDialog.Builder(this)
            .setTitle(layout.name)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    /** Share a layout as text (share sheet + clipboard) — the community loop. */
    private fun shareLayout(layout: CustomLayout) {
        val share = layout.toShareString()
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("phone-controller layout", share))
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, share)
                    },
                    layout.name,
                ),
            )
        }
        setDetail(getString(R.string.layout_share_copied))
    }

    private fun importLayoutDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 8
            hint = getString(R.string.layout_import_hint)
        }
        val pad = dp(16)
        AlertDialog.Builder(this)
            .setTitle(R.string.layout_import)
            .setView(FrameLayout(this).apply {
                setPadding(pad, dp(4), pad, 0)
                addView(input)
            })
            .setPositiveButton(R.string.layout_import_go) { _, _ ->
                val imported = CustomLayout.fromShareString(
                    input.text.toString(), "cl_${System.currentTimeMillis()}",
                )
                if (imported == null) {
                    setDetail(getString(R.string.layout_import_failed))
                } else {
                    if (layoutStore.all().any { it.name == imported.name }) {
                        imported.name = getString(R.string.layout_imported_name_fmt, imported.name)
                    }
                    layoutStore.save(imported)
                    rebuildSpinnerSelection()
                    setDetail(getString(R.string.layout_imported_fmt, imported.name))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * New-layout / customize-a-preset flow (Slice 15; Slice 17). Pick a starter —
     * Blank or a controller preset (GBA / Full gamepad / Analog / NDS) — name it, then
     * edit. The layout is NOT saved here; only the editor's Save persists it, so
     * backing out (Cancel) leaves nothing behind.
     */
    private fun newLayoutFlow() {
        val kinds = CustomLayout.templateKinds()
        AlertDialog.Builder(this)
            .setTitle(R.string.new_layout_template)
            .setItems(kinds.toTypedArray()) { _, which ->
                promptText(getString(R.string.new_layout_name), "") { name ->
                    val layout = CustomLayout.template(
                        "cl_${System.currentTimeMillis()}",
                        name.ifBlank { getString(R.string.new_layout) },
                        kinds[which],
                    )
                    startEditor(layout, isNew = true)
                }
            }
            .show()
    }

    /** Add-widget flow (Slice 15): pick a widget type, place it centred, then edit. */
    private fun addWidgetFlow(layout: CustomLayout, editView: CustomPadView) {
        val types = PadWidgetType.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_add_widget)
            .setItems(types.map { widgetTypeLabel(it) }.toTypedArray()) { _, which ->
                val type = types[which]
                // Sticks/D-pad want a chunky default; touchpad wider; gyro small.
                val spec = when (type) {
                    PadWidgetType.TOUCHPAD -> PadWidgetSpec(type, 0.30f, 0.30f, 0.40f, 0.40f)
                    PadWidgetType.GYRO -> PadWidgetSpec(type, 0.44f, 0.44f, 0.14f, 0.12f)
                    else -> PadWidgetSpec(type, 0.36f, 0.30f, 0.28f, 0.40f)
                }
                layout.widgets.add(spec)
                editView.rebuild()
            }
            .show()
    }

    private fun widgetTypeLabel(type: PadWidgetType): String = when (type) {
        PadWidgetType.LEFT_STICK -> getString(R.string.widget_left_stick)
        PadWidgetType.RIGHT_STICK -> getString(R.string.widget_right_stick)
        PadWidgetType.DPAD -> getString(R.string.widget_dpad)
        PadWidgetType.TOUCHPAD -> getString(R.string.widget_touchpad)
        PadWidgetType.GYRO -> getString(R.string.widget_gyro)
    }

    /** Edit dialog for a placed widget: change type, resize, or delete. */
    private fun widgetConfigDialog(layout: CustomLayout, spec: PadWidgetSpec) {
        val entries = listOf<Pair<String, () -> Unit>>(
            getString(R.string.editor_change_type) to {
                val types = PadWidgetType.entries
                AlertDialog.Builder(this)
                    .setTitle(R.string.editor_change_type)
                    .setItems(types.map { widgetTypeLabel(it) }.toTypedArray()) { _, w ->
                        spec.type = types[w]
                        editingView?.rebuild()
                    }
                    .show()
            },
            getString(R.string.editor_size) to {
                sizeSliderDialog(spec, 0.10f, 0.90f, 0.10f, 0.90f)
            },
            getString(R.string.editor_delete) to {
                layout.widgets.remove(spec)
                editingView?.rebuild()
            },
        )
        AlertDialog.Builder(this)
            .setTitle(widgetTypeLabel(spec.type))
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    private fun startEditor(layout: CustomLayout, isNew: Boolean = false) {
        turbo.cancelAll()
        stopGyro()
        transport?.releaseHeldInputs()
        // Remember where to return on discard; a new layout isn't in the store yet.
        if (editingLayout == null) preEditSelection = currentSelection
        editingIsNew = isNew
        editingLayout = layout

        padContainer.removeAllViews()
        val editView = CustomPadView(
            this, layout, editMode = true,
            actionResolver = ::resolveAction, turbo = turbo,
            onEditButton = { spec -> buttonConfigDialog(layout, spec) },
            onEditWidget = { spec -> widgetConfigDialog(layout, spec) },
        )
        editingView = editView

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(
                getString(R.string.editor_add) to {
                    val spec = PadButtonSpec(
                        0.44f, 0.44f, 0.14f, 0.18f, "A",
                        PadAction(PadActionType.GAMEPAD, GamepadButton.A.name),
                    )
                    layout.buttons.add(spec)
                    editView.rebuild()
                    buttonConfigDialog(layout, spec)
                },
                getString(R.string.editor_add_widget) to {
                    addWidgetFlow(layout, editView)
                },
                getString(R.string.editor_hint_move) to {
                    setDetail(getString(R.string.editor_hint))
                },
                getString(R.string.editor_save) to {
                    editingLayout = null
                    layoutStore.save(layout)
                    showSelection("c:${layout.id}")
                    rebuildSpinnerSelection()
                },
                getString(R.string.editor_cancel) to {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.editor_discard_title)
                        .setMessage(if (editingIsNew) R.string.editor_discard_new else R.string.editor_discard_edit)
                        .setPositiveButton(R.string.editor_discard_yes) { _, _ -> discardEditor() }
                        .setNegativeButton(R.string.editor_keep_editing, null)
                        .show()
                },
            ).forEach { (label, onClick) ->
                addView(
                    Button(context).apply {
                        text = label
                        isAllCaps = false
                        textSize = 13f
                        ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                        setOnClickListener { onClick() }
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
        }

        val editorRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar)
            addView(editView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        padContainer.addView(
            editorRoot,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    /**
     * Leave the editor WITHOUT saving. A new layout was never persisted (only Save
     * writes it), and edits to an existing layout were made on a throwaway parsed
     * copy, so returning to the stored version simply reloads the original.
     */
    private fun discardEditor() {
        editingLayout = null
        editingView = null
        val back = if (selectionExists(preEditSelection)) preEditSelection else "b:0"
        showSelection(back)
        rebuildSpinnerSelection()
    }

    private fun buttonConfigDialog(layout: CustomLayout, spec: PadButtonSpec) {
        // Label→handler pairs: indexes can never drift when entries are added.
        val turboLabel = if (spec.turbo) R.string.editor_turbo_off else R.string.editor_turbo_on
        val entries = listOf<Pair<String, () -> Unit>>(
            getString(R.string.editor_change_action) to { pickActionType(spec) },
            getString(turboLabel) to {
                if (spec.action.type == PadActionType.MACRO) {
                    // Auto-fire on a timed sequence = chaos; refuse with a hint.
                    setDetail(getString(R.string.macro_no_turbo))
                } else {
                    spec.turbo = !spec.turbo
                    editingView?.rebuild()
                }
                Unit
            },
            getString(R.string.editor_size) to { pickSize(spec) },
            getString(R.string.editor_color) to { pickColor(spec) },
            getString(R.string.editor_shape) to { pickShape(spec) },
            getString(R.string.editor_style) to { pickFx(spec) },
            getString(R.string.editor_opacity) to { pickOpacity(spec) },
            getString(R.string.editor_text_size) to { pickTextSize(spec) },
            getString(R.string.editor_label) to {
                promptText(getString(R.string.editor_label), spec.label) { text ->
                    spec.label = text.ifBlank { spec.label }
                    editingView?.rebuild()
                }
            },
            getString(R.string.editor_duplicate) to {
                val copy = spec.copy(xPct = spec.xPct + 0.05f, yPct = spec.yPct + 0.05f)
                copy.clampToPad()
                layout.buttons.add(copy)
                editingView?.rebuild()
                Unit
            },
            getString(R.string.editor_delete) to {
                layout.buttons.remove(spec)
                editingView?.rebuild()
                Unit
            },
        )
        AlertDialog.Builder(this)
            .setTitle(spec.label)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    /** Supporter style pack (fair-IAP groundwork): fills stay cosmetic-only. */
    private fun pickFx(spec: PadButtonSpec) {
        if (!Supporter.unlocked) {
            setDetail(getString(R.string.style_locked_hint))
            return
        }
        val fxs = PadFx.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_style)
            .setItems(fxs.map { it.name }.toTypedArray()) { _, which ->
                spec.fx = fxs[which]
                editingView?.rebuild()
            }
            .show()
    }

    /** S–XL presets + 2% width/height steppers (fine control past the presets). */
    private fun pickSize(spec: PadButtonSpec) =
        sizeSliderDialog(spec, 0.05f, 0.60f, 0.06f, 0.60f)

    /**
     * Fine size control (Slice 17): Width + Height sliders with LIVE preview (the
     * button/widget resizes behind the dialog as you drag), plus S–XL quick presets.
     * Works on any [PadPositioned] so buttons and widgets share it.
     */
    private fun sizeSliderDialog(spec: PadPositioned, wMin: Float, wMax: Float, hMin: Float, hMax: Float) {
        val pad = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        fun pct(v: Float) = "${(v * 100).toInt()}%"
        val wLabel = TextView(this).apply { textSize = 13f }
        val hLabel = TextView(this).apply { textSize = 13f; setPadding(0, dp(10), 0, 0) }
        val wBar = SeekBar(this).apply { max = 100 }
        val hBar = SeekBar(this).apply { max = 100 }

        fun refreshLabels() {
            wLabel.text = getString(R.string.size_width_fmt, pct(spec.wPct))
            hLabel.text = getString(R.string.size_height_fmt, pct(spec.hPct))
        }
        fun setW(p: Int) { spec.wPct = wMin + p / 100f * (wMax - wMin); spec.clampToPad(); refreshLabels(); editingView?.rebuild() }
        fun setH(p: Int) { spec.hPct = hMin + p / 100f * (hMax - hMin); spec.clampToPad(); refreshLabels(); editingView?.rebuild() }

        wBar.progress = (((spec.wPct - wMin) / (wMax - wMin)) * 100).toInt().coerceIn(0, 100)
        hBar.progress = (((spec.hPct - hMin) / (hMax - hMin)) * 100).toInt().coerceIn(0, 100)
        refreshLabels()
        wBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { setW(p) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        hBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { setH(p) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Quick presets set both bars (fractions of each range).
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        listOf("S" to 0.2f, "M" to 0.45f, "L" to 0.7f, "XL" to 0.95f).forEach { (label, frac) ->
            quick.addView(
                Button(this).apply {
                    text = label; isAllCaps = false; textSize = 13f
                    ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 14f)
                    setOnClickListener {
                        wBar.progress = (frac * 100).toInt()
                        hBar.progress = (frac * 100).toInt()
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

        content.addView(wLabel); content.addView(wBar)
        content.addView(hLabel); content.addView(hBar)
        content.addView(quick)
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_size)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** A real swatch grid (Slice 8) — colored circles beat a list of color names. */
    private fun pickColor(spec: PadButtonSpec) {
        val entries: List<Pair<String, Int?>> =
            listOf(getString(R.string.color_default) to null) + ButtonStyler.PALETTE
        var dialog: AlertDialog? = null
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        entries.chunked(SWATCH_COLUMNS).forEach { rowEntries ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowEntries.forEach { (name, color) ->
                val swatch = TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = if (color == null) "∅" else ""
                    textSize = 18f
                    setTextColor(0xFFB0BEC5.toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color ?: ButtonStyler.SURFACE)
                        if (color == null) setStroke(dp(2), 0xFF78909C.toInt())
                    }
                    contentDescription = name
                    setOnClickListener {
                        spec.colorArgb = color?.let { base ->
                            // Preserve the current opacity choice when recoloring.
                            ButtonStyler.withAlpha(base, spec.colorArgb?.ushr(24) ?: 0xFF)
                        }
                        editingView?.rebuild()
                        dialog?.dismiss()
                    }
                }
                row.addView(swatch, swatchCellParams())
            }
            repeat(SWATCH_COLUMNS - rowEntries.size) {
                row.addView(View(this), swatchCellParams())
            }
            grid.addView(row)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.editor_color)
            .setView(ScrollView(this).apply { addView(grid) })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun swatchCellParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            setMargins(dp(6), dp(6), dp(6), dp(6))
        }

    private fun pickShape(spec: PadButtonSpec) {
        val shapes = PadShape.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_shape)
            .setItems(shapes.map { it.name }.toTypedArray()) { _, which ->
                spec.shape = shapes[which]
                editingView?.rebuild()
            }
            .show()
    }

    private fun pickOpacity(spec: PadButtonSpec) {
        val current = spec.colorArgb
        if (current == null) {
            setDetail(getString(R.string.opacity_needs_color))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_opacity)
            .setItems(ButtonStyler.OPACITY.map { it.first }.toTypedArray()) { _, which ->
                spec.colorArgb = ButtonStyler.withAlpha(current, ButtonStyler.OPACITY[which].second)
                editingView?.rebuild()
            }
            .show()
    }

    private fun pickTextSize(spec: PadButtonSpec) {
        val sizes = listOf("S" to 11, "M" to 14, "L" to 18, "XL" to 22)
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_text_size)
            .setItems(sizes.map { it.first }.toTypedArray()) { _, which ->
                spec.textSizeSp = sizes[which].second
                editingView?.rebuild()
            }
            .show()
    }

    private fun pickActionType(spec: PadButtonSpec) {
        val types = PadActionType.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_change_action)
            .setItems(types.map { it.name }.toTypedArray()) { _, which ->
                pickActionCode(spec, types[which])
            }
            .show()
    }

    /** The shared key list (KEY actions AND custom-combo keys). */
    private fun keyChoices(): List<Pair<String, String>> = buildList {
        add("↑" to KeyUsage.ARROW_UP.toString())
        add("↓" to KeyUsage.ARROW_DOWN.toString())
        add("←" to KeyUsage.ARROW_LEFT.toString())
        add("→" to KeyUsage.ARROW_RIGHT.toString())
        add("SPACE" to KeyUsage.SPACE.toString())
        add("ENTER" to KeyUsage.ENTER.toString())
        add("ESC" to KeyUsage.ESCAPE.toString())
        add("TAB" to KeyUsage.TAB.toString())
        add("⌫" to KeyUsage.BACKSPACE.toString())
        add("PgUp" to KeyUsage.PAGE_UP.toString())
        add("PgDn" to KeyUsage.PAGE_DOWN.toString())
        add("Home" to KeyUsage.HOME.toString())
        add("End" to KeyUsage.END.toString())
        for (n in 1..12) add("F$n" to (KeyUsage.F1 + (n - 1)).toString())
        for (c in 'a'..'z') add(c.uppercase() to KeyUsage.letterUsage(c).toString())
        for (c in '0'..'9') add(c.toString() to KeyUsage.digitUsage(c).toString())
    }

    /** Simple per-type choice lists (label → code); COMBO/MACRO have their own flows. */
    private fun actionChoices(type: PadActionType): List<Pair<String, String>> = when (type) {
        PadActionType.GAMEPAD -> GamepadButton.entries.map { it.name to it.name }
        PadActionType.DPAD -> DpadDirection.entries.map { it.name to it.name }
        PadActionType.MEDIA -> MediaButton.entries.map { it.name to it.name }
        PadActionType.MOUSE -> MouseButton.entries.map { it.name to it.name }
        PadActionType.MODIFIER -> listOf(
            "SHIFT" to KeyUsage.MOD_LEFT_SHIFT.toString(),
            "CTRL" to KeyUsage.MOD_LEFT_CTRL.toString(),
            "ALT" to KeyUsage.MOD_LEFT_ALT.toString(),
            "WIN/CMD" to KeyUsage.MOD_LEFT_GUI.toString(),
        )
        PadActionType.KEY -> keyChoices()
        PadActionType.COMBO, PadActionType.MACRO, PadActionType.GESTURE -> emptyList()
    }

    private fun pickActionCode(spec: PadButtonSpec, type: PadActionType) {
        if (type == PadActionType.COMBO) {
            pickCombo(spec)
            return
        }
        if (type == PadActionType.GESTURE) {
            pickGesture(spec)
            return
        }
        if (type == PadActionType.MACRO) {
            // Switching to MACRO keeps any existing steps; otherwise start empty.
            if (spec.action.type != PadActionType.MACRO) {
                spec.action = PadAction(PadActionType.MACRO, "[]")
                if (spec.label.isBlank()) spec.label = getString(R.string.macro_default_label)
            }
            macroDialog(spec)
            return
        }
        val choices = actionChoices(type)
        AlertDialog.Builder(this)
            .setTitle(type.name)
            .setItems(choices.map { it.first }.toTypedArray()) { _, which ->
                val (label, code) = choices[which]
                spec.action = PadAction(type, code)
                spec.label = label
                editingView?.rebuild()
            }
            .show()
    }

    // --- gesture recorder + binding (Slice 13) --------------------------------------

    /** Bind a GESTURE action: pick a saved gesture, or record a new one. */
    private fun pickGesture(spec: PadButtonSpec) {
        val saved = gestureStore.all()
        val labels = saved.map { it.name } + getString(R.string.gesture_record_new)
        AlertDialog.Builder(this)
            .setTitle(R.string.gesture_pick)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < saved.size) {
                    bindGesture(spec, saved[which])
                } else {
                    recordGestureDialog { g -> bindGesture(spec, g) }
                }
            }
            .show()
    }

    private fun bindGesture(spec: PadButtonSpec, g: SavedGesture) {
        spec.action = PadAction(PadActionType.GESTURE, g.id)
        spec.label = g.name
        editingView?.rebuild()
    }

    /**
     * Full-screen recorder: perform the gesture on the surface (each finger-down to
     * finger-up is a stroke), then Save it named. Runs as a full-screen Dialog on
     * this phone's own screen — no overlay permission needed just to author.
     */
    private fun recordGestureDialog(onSaved: (SavedGesture) -> Unit) {
        val recorder = GestureRecorderView(this)
        val hint = TextView(this).apply {
            text = getString(R.string.gesture_record_hint)
            textSize = 12f
            setTextColor(0xFFECEFF1.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        fun chip(labelRes: Int, onClick: () -> Unit) = Button(this).apply {
            text = getString(labelRes)
            isAllCaps = false
            textSize = 13f
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 16f)
            setOnClickListener { onClick() }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC15181E.toInt())
            addView(hint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(chip(R.string.gesture_retry) { recorder.clear() })
            addView(chip(R.string.gesture_save) {
                val gesture = recorder.buildGesture()
                if (gesture == null) {
                    setDetail(getString(R.string.gesture_empty))
                } else {
                    promptText(getString(R.string.gesture_name_prompt), "") { name ->
                        val saved = SavedGesture(
                            "gs_${System.currentTimeMillis()}",
                            name.ifBlank { getString(R.string.gesture_default_name) },
                            TouchGestureCodec.encode(gesture),
                        )
                        gestureStore.save(saved)
                        dialog.dismiss()
                        onSaved(saved)
                    }
                }
            })
            addView(chip(android.R.string.cancel) { dialog.dismiss() })
        }

        val root = FrameLayout(this).apply {
            addView(recorder, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            ))
            addView(bar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ))
        }
        dialog.setContentView(root)
        dialog.show()
    }

    /** Recorded-gestures manager (Settings entry): list, rename, delete, record. */
    private fun gesturesManagerDialog() {
        val saved = gestureStore.all()
        val labels = saved.map { getString(R.string.gesture_row_fmt, it.name, it.durationMs()) } +
            getString(R.string.gesture_record_new)
        AlertDialog.Builder(this)
            .setTitle(R.string.gestures_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which >= saved.size) {
                    recordGestureDialog { gesturesManagerDialog() }
                    return@setItems
                }
                val g = saved[which]
                AlertDialog.Builder(this)
                    .setTitle(g.name)
                    .setItems(
                        arrayOf(getString(R.string.layout_rename), getString(R.string.layout_delete)),
                    ) { _, action ->
                        when (action) {
                            0 -> promptText(getString(R.string.layout_rename), g.name) { name ->
                                gestureStore.save(g.copy(name = name.ifBlank { g.name }))
                                gesturesManagerDialog()
                            }
                            1 -> {
                                gestureStore.delete(g.id)
                                gesturesManagerDialog()
                            }
                        }
                    }
                    .show()
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    // --- macro builder (Slice 10) ---------------------------------------------------

    private fun macroSteps(spec: PadButtonSpec): String =
        if (spec.action.type == PadActionType.MACRO) spec.action.code else "[]"

    private fun macroDialog(spec: PadButtonSpec) {
        val summary = MacroRunner.describe(macroSteps(spec))
            .ifEmpty { getString(R.string.macro_empty) }
        // setMessage + setItems are mutually exclusive on AlertDialog, so the step
        // summary rides as the (harmless, reopening) first list row.
        val entries = listOf<Pair<String, () -> Unit>>(
            getString(R.string.macro_steps_fmt, summary) to { macroDialog(spec) },
            getString(R.string.macro_add_step) to {
                pickStepAction { type, code, label ->
                    spec.action = PadAction(
                        PadActionType.MACRO,
                        MacroRunner.appendStep(macroSteps(spec), type, code, label),
                    )
                    macroDialog(spec)
                }
            },
            getString(R.string.macro_add_pause) to {
                spec.action = PadAction(
                    PadActionType.MACRO,
                    MacroRunner.appendStep(
                        macroSteps(spec), MacroRunner.TYPE_WAIT, "", "⏸",
                        holdMs = PAUSE_STEP_MS, gapMs = 0L,
                    ),
                )
                macroDialog(spec)
            },
            getString(R.string.macro_remove_last) to {
                spec.action = PadAction(
                    PadActionType.MACRO,
                    MacroRunner.removeLastStep(macroSteps(spec)),
                )
                macroDialog(spec)
            },
            getString(R.string.macro_done) to {
                editingView?.rebuild()
                Unit
            },
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.macro_title_fmt, MacroRunner.parse(macroSteps(spec)).size))
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .show()
    }

    /** Pick one macro step: type (no MACRO nesting) → code. COMBO steps use presets. */
    private fun pickStepAction(onPicked: (String, String, String) -> Unit) {
        // GESTURE excluded: macro steps + voice run in HID/remote context where a
        // screen gesture is inert (it only means something in overlay mode).
        val types = PadActionType.entries.filter { it != PadActionType.MACRO && it != PadActionType.GESTURE }
        AlertDialog.Builder(this)
            .setTitle(R.string.macro_step_type)
            .setItems(types.map { it.name }.toTypedArray()) { _, ti ->
                val type = types[ti]
                if (type == PadActionType.COMBO) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.combo_title)
                        .setItems(Combos.PRESETS.map { it.label }.toTypedArray()) { _, ci ->
                            val chord = Combos.PRESETS[ci]
                            onPicked(PadActionType.COMBO.name, chord.code, chord.label)
                        }
                        .show()
                } else {
                    val choices = actionChoices(type)
                    AlertDialog.Builder(this)
                        .setTitle(type.name)
                        .setItems(choices.map { it.first }.toTypedArray()) { _, ci ->
                            val (label, code) = choices[ci]
                            onPicked(type.name, code, label)
                        }
                        .show()
                }
            }
            .show()
    }

    /** COMBO picker: the everyday presets + a fully custom modifiers×key builder. */
    private fun pickCombo(spec: PadButtonSpec) {
        val labels = Combos.PRESETS.map { it.label } + getString(R.string.combo_custom)
        AlertDialog.Builder(this)
            .setTitle(R.string.combo_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < Combos.PRESETS.size) {
                    val chord = Combos.PRESETS[which]
                    spec.action = PadAction(PadActionType.COMBO, chord.code)
                    spec.label = chord.label
                    editingView?.rebuild()
                } else {
                    pickCustomCombo(spec)
                }
            }
            .show()
    }

    private fun pickCustomCombo(spec: PadButtonSpec) {
        val modNames = arrayOf("Ctrl", "Shift", "Alt", "Win/Cmd")
        val modMasks = intArrayOf(
            KeyUsage.MOD_LEFT_CTRL, KeyUsage.MOD_LEFT_SHIFT,
            KeyUsage.MOD_LEFT_ALT, KeyUsage.MOD_LEFT_GUI,
        )
        val checked = booleanArrayOf(false, false, false, false)
        AlertDialog.Builder(this)
            .setTitle(R.string.combo_pick_mods)
            .setMultiChoiceItems(modNames, checked) { _, i, isChecked -> checked[i] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var mask = 0
                for (i in modMasks.indices) if (checked[i]) mask = mask or modMasks[i]
                val choices = keyChoices()
                AlertDialog.Builder(this)
                    .setTitle(R.string.combo_pick_key)
                    .setItems(choices.map { it.first }.toTypedArray()) { _, k ->
                        val (keyLabel, code) = choices[k]
                        spec.action = PadAction(PadActionType.COMBO, "$mask:${code.toInt()}")
                        spec.label = (
                            modNames.filterIndexed { i, _ -> checked[i] } + keyLabel
                            ).joinToString("+")
                        editingView?.rebuild()
                    }
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptText(title: String, initial: String, onDone: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> onDone(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- settings ------------------------------------------------------------------------

    private fun openSettings() {
        val pad = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        val hapticsSwitch = Switch(this).apply {
            text = getString(R.string.settings_haptics)
            isChecked = Haptics.enabled
            setOnCheckedChangeListener { _, checked ->
                Haptics.enabled = checked
                prefs().edit().putBoolean(PREF_HAPTICS, checked).apply()
            }
        }
        content.addView(hapticsSwitch)

        content.addView(
            TextView(this).apply {
                text = getString(R.string.settings_deadzone)
                textSize = 13f
                setPadding(0, dp(12), 0, 0)
            },
        )
        content.addView(
            SeekBar(this).apply {
                max = 30
                progress = (deadzone() * 100).toInt().coerceIn(0, 30)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                        prefs().edit().putFloat(PREF_DEADZONE, value / 100f).apply()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            },
        )

        val turboLabel = TextView(this).apply {
            text = getString(R.string.settings_turbo_fmt, prefs().getInt(PREF_TURBO_HZ, 10))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(turboLabel)
        content.addView(
            SeekBar(this).apply {
                max = 15 // 5..20 Hz
                progress = (prefs().getInt(PREF_TURBO_HZ, 10) - 5).coerceIn(0, 15)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                        val hz = value + 5
                        prefs().edit().putInt(PREF_TURBO_HZ, hz).apply()
                        turbo.setRateHz(hz)
                        turboLabel.text = getString(R.string.settings_turbo_fmt, hz)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            },
        )

        content.addView(
            Switch(this).apply {
                text = getString(R.string.settings_scroll_invert)
                isChecked = prefs().getBoolean(PREF_SCROLL_INVERT, false)
                setOnCheckedChangeListener { _, checked ->
                    prefs().edit().putBoolean(PREF_SCROLL_INVERT, checked).apply()
                }
                setPadding(0, dp(12), 0, 0)
            },
        )

        val volumeOptions = listOf(
            getString(R.string.volkeys_off),
            getString(R.string.volkeys_l1r1),
            getString(R.string.volkeys_l2r2),
            getString(R.string.volkeys_pages),
        )
        content.addView(
            Button(this).apply {
                fun label() = getString(
                    R.string.settings_volkeys_fmt,
                    volumeOptions[prefs().getInt(PREF_VOLKEYS, 0).coerceIn(0, 3)],
                )
                text = label()
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.settings_volkeys_title)
                        .setItems(volumeOptions.toTypedArray()) { _, which ->
                            prefs().edit().putInt(PREF_VOLKEYS, which).apply()
                            text = label()
                        }
                        .show()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        content.addView(
            TextView(this).apply {
                val micros = transport?.averageSendMicros() ?: 0
                text = getString(R.string.settings_latency_fmt, micros)
                textSize = 13f
                setPadding(0, dp(12), 0, 0)
            },
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.app_background)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { pickAppBackground() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.gyro_settings)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { gyroSettingsDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.play_on_phone)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { playOnPhoneDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.gestures_title)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { gesturesManagerDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.voice_title)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { voiceCommandsDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(
            Button(this).apply {
                text = getString(R.string.about_title)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener { aboutDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.layouts_title) { _, _ -> openLayoutManager() }
            .setNegativeButton(android.R.string.ok) { _, _ ->
                // Deadzone applies on pad rebuild; refresh if the analog pad is live.
                if (currentSelection == "b:${Pad.ANALOG.ordinal}") showSelection(currentSelection)
            }
            .show()
    }

    /**
     * Gyro settings (Slice 14) — pick what tilt drives, sensitivity, invert, and set
     * the neutral; a live visualizer runs the sensor in preview so you SEE the output
     * move as you tilt, even before connecting.
     */
    private fun gyroSettingsDialog() {
        val pad = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        if (!gyroDriver.available) {
            content.addView(TextView(this).apply {
                text = getString(R.string.gyro_unavailable); textSize = 13f
            })
            AlertDialog.Builder(this).setTitle(R.string.gyro_settings)
                .setView(ScrollView(this).apply { addView(content) })
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }

        val targetLabels = listOf(
            getString(R.string.gyro_target_rstick),
            getString(R.string.gyro_target_lstick),
            getString(R.string.gyro_target_mouse),
        )
        val targetButton = Button(this).apply {
            isAllCaps = false; textSize = 13f
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
            text = getString(R.string.gyro_target_fmt, targetLabels[prefs().getInt(PREF_GYRO_TARGET, 0).coerceIn(0, 2)])
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.gyro_target_title)
                    .setItems(targetLabels.toTypedArray()) { _, which ->
                        prefs().edit().putInt(PREF_GYRO_TARGET, which).apply()
                        gyroMouseRemX = 0f; gyroMouseRemY = 0f
                        text = getString(R.string.gyro_target_fmt, targetLabels[which])
                    }.show()
            }
        }
        content.addView(targetButton)

        val sensLabel = TextView(this).apply {
            text = getString(R.string.gyro_sensitivity); textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(sensLabel)
        content.addView(SeekBar(this).apply {
            max = 100
            progress = prefs().getInt(PREF_GYRO_SENS, 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    prefs().edit().putInt(PREF_GYRO_SENS, value).apply()
                    applyGyroSettings()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        content.addView(Switch(this).apply {
            text = getString(R.string.gyro_invert_x)
            isChecked = prefs().getBoolean(PREF_GYRO_INV_X, false)
            setOnCheckedChangeListener { _, c -> prefs().edit().putBoolean(PREF_GYRO_INV_X, c).apply(); applyGyroSettings() }
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(Switch(this).apply {
            text = getString(R.string.gyro_invert_y)
            isChecked = prefs().getBoolean(PREF_GYRO_INV_Y, false)
            setOnCheckedChangeListener { _, c -> prefs().edit().putBoolean(PREF_GYRO_INV_Y, c).apply(); applyGyroSettings() }
        })

        val visualizer = GyroVisualizerView(this)
        content.addView(visualizer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)).apply {
            topMargin = dp(12)
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.gyro_hint); textSize = 12f
            setTextColor(0xFF9AA7B0.toInt()); setPadding(0, dp(6), 0, 0)
        })
        content.addView(Button(this).apply {
            text = getString(R.string.gyro_recenter)
            isAllCaps = false; textSize = 13f
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
            setOnClickListener { gyroDriver.recenter() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        // Preview: run the sensor + feed the visualizer while the dialog is open.
        gyroVisualizer = visualizer
        gyroPreview = true
        applyGyroSettings()
        syncGyroSensor()

        AlertDialog.Builder(this)
            .setTitle(R.string.gyro_settings)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener {
                gyroVisualizer = null
                gyroPreview = false
                syncGyroSensor()
            }
            .show()
    }

    /**
     * Play on this phone (Slice 12) — float a layout over another app and tap the
     * game underneath. Guides the two opt-in grants (draw-over-apps + the
     * accessibility tap service), then starts the overlay foreground service.
     */
    private fun playOnPhoneDialog() {
        val layouts = layoutStore.all()
        if (layouts.isEmpty()) {
            setDetail(getString(R.string.play_no_layouts))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.play_on_phone)
            .setMessage(R.string.play_intro)
            .setItems(layouts.map { it.name }.toTypedArray()) { _, which ->
                startOverlayPlay(layouts[which].id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startOverlayPlay(layoutId: String) {
        if (!AndroidSettings.canDrawOverlays(this)) {
            setDetail(getString(R.string.play_need_overlay))
            runCatching {
                startActivity(
                    Intent(
                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
            return
        }
        if (!TapAccessibilityService.isConnected) {
            setDetail(getString(R.string.play_need_accessibility))
            runCatching { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
            return
        }
        runCatching {
            startForegroundService(
                Intent(this, OverlayPlayService::class.java)
                    .putExtra(OverlayPlayService.EXTRA_LAYOUT_ID, layoutId),
            )
            setDetail(getString(R.string.play_started))
        }
    }

    /**
     * Voice commands (Slice 11) — the WHOLE feature lives in this one dialog:
     * enable switch, phrase list (tap to remove), and Add. Phrases fire as taps
     * through the shared action vocabulary; listening is foreground-only.
     */
    private fun voiceCommandsDialog() {
        val pad = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        if (!voiceDriver.available) {
            content.addView(TextView(this).apply {
                text = getString(R.string.voice_unavailable)
                textSize = 13f
            })
        }
        content.addView(
            Switch(this).apply {
                text = getString(R.string.voice_enable)
                isChecked = voiceEnabled() && hasMicPermission()
                isEnabled = voiceDriver.available
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        if (hasMicPermission()) {
                            prefs().edit().putBoolean(PREF_VOICE, true).apply()
                            voiceDriver.start()
                            setDetail(getString(R.string.voice_enabled_hint))
                        } else {
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC)
                        }
                    } else {
                        prefs().edit().putBoolean(PREF_VOICE, false).apply()
                        voiceDriver.stop()
                    }
                }
            },
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.voice_hint)
                textSize = 12f
                setTextColor(0xFF9AA7B0.toInt())
                setPadding(0, dp(8), 0, dp(4))
            },
        )
        val commands = voiceStore.all()
        commands.forEachIndexed { index, command ->
            content.addView(
                Button(this).apply {
                    text = getString(R.string.voice_row_fmt, command.phrase, command.actionLabel)
                    isAllCaps = false
                    textSize = 13f
                    ButtonStyler.flatStyle(this, ButtonStyler.SURFACE)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(command.phrase)
                            .setPositiveButton(R.string.voice_remove) { _, _ ->
                                voiceStore.removeAt(index)
                                voiceCommandsDialog()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.voice_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.voice_add) { _, _ ->
                promptText(getString(R.string.voice_phrase_prompt), "") { phrase ->
                    if (phrase.isNotBlank()) {
                        pickStepAction { type, code, label ->
                            voiceStore.add(VoiceCommand(phrase.trim(), type, code, label))
                            voiceCommandsDialog()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    /**
     * About & Support — the fairness promise made visible (Slice 9 groundwork):
     * everything functional is free, no ads, no subscriptions; the one-time
     * Supporter Pack (cosmetic styles) arrives with a Play listing. Until then the
     * preview switch honestly unlocks the styles for free.
     */
    private fun aboutDialog() {
        val pad = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        content.addView(
            TextView(this).apply {
                text = getString(R.string.fairness_promise)
                textSize = 14f
            },
        )
        content.addView(
            Switch(this).apply {
                text = getString(R.string.supporter_preview)
                isChecked = Supporter.unlocked
                setOnCheckedChangeListener { _, checked ->
                    Supporter.unlocked = checked
                    prefs().edit().putBoolean(PREF_SUPPORTER, checked).apply()
                    // Re-render the live pad so style-pack fills appear/disappear now.
                    if (editingLayout == null) showSelection(currentSelection)
                }
            }.also { it.setPadding(0, dp(12), 0, 0) },
        )
        content.addView(
            Button(this).apply {
                text = getString(R.string.support_button)
                isAllCaps = false
                textSize = 13f
                ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
                setOnClickListener {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SUPPORT_URL)),
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // --- UI scaffolding ---------------------------------------------------------------------

    private fun setStatus(text: CharSequence) {
        lastStatus = text
        statusView.text = text
    }

    private fun setDetail(text: CharSequence) {
        lastDetail = text
        detailView.text = text
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
    ).toInt()

    private var spinner: Spinner? = null

    private fun rebuildSpinnerSelection() {
        spinner?.let { configureSpinner(it) }
    }

    private fun configureSpinner(target: Spinner) {
        val keys = selectionKeys()
        target.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, selectionLabels(),
        )
        target.onItemSelectedListener = null
        target.setSelection(keys.indexOf(currentSelection).coerceAtLeast(0), false)
        target.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val key = selectionKeys().getOrNull(position) ?: return
                if (key != currentSelection || editingLayout != null) showSelection(key)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun layoutSpinner(): Spinner = Spinner(this).also {
        spinner = it
        configureSpinner(it)
    }

    private fun actionButton(labelRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = getString(labelRes)
            isAllCaps = false
            textSize = 13f
            ButtonStyler.flatStyle(this, ButtonStyler.SURFACE, cornerDp = 18f)
            setOnClickListener { onClick() }
        }

    private fun buildUi() {
        chromeViews.clear()
        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFECEFF1.toInt())
            setPadding(dp(12), dp(8), dp(12), dp(2))
            text = lastStatus
        }
        detailView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF9AA7B0.toInt())
            setPadding(dp(12), 0, dp(12), dp(2))
            text = lastDetail
        }
        padContainer = FrameLayout(this).apply { setPadding(dp(6), 0, dp(6), dp(6)) }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val content = if (landscape) buildLandscapeRoot() else buildPortraitRoot()

        // The focus-exit chip floats above the pad, top-center (the least-populated
        // corner of every built-in pad), translucent so it never dominates.
        val chip = TextView(this).apply {
            text = getString(R.string.focus_exit_chip)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFFECEFF1.toInt())
            alpha = 0.5f
            background = GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = dp(10).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { setFocusMode(false) }
        }
        focusChip = chip

        val root = FrameLayout(this).apply {
            setBackgroundColor(appBg())
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                chip,
                FrameLayout.LayoutParams(dp(44), dp(30), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    .apply { topMargin = dp(4) },
            )
        }
        rootFrame = root
        setContentView(root)
        applyAppBackground()
        editingLayout?.let { startEditor(it) } ?: showSelection(currentSelection)
        applyFocus()
    }

    private fun actionButtons(): List<Button> = listOf(
        actionButton(R.string.make_discoverable) { makeDiscoverable() },
        actionButton(R.string.connect_bonded) { pickBondedAndConnect() },
        actionButton(R.string.reprobe) { ensurePermissionsThenStart() },
        actionButton(R.string.send_text) { sendTextDialog() },
        actionButton(R.string.dim) { toggleDim() },
        actionButton(R.string.immersive) { toggleImmersive() },
        actionButton(R.string.focus) { setFocusMode(true) },
        actionButton(R.string.settings_title) { openSettings() },
    )

    /**
     * Send text (Slice 9): the phone's own IME — swipe typing, autocorrect and the
     * MIC key (voice dictation) — replayed on the host as real HID keystrokes.
     */
    private fun sendTextDialog() {
        if (transport?.isConnected != true) {
            setDetail(getString(R.string.connect_first))
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 8
            hint = getString(R.string.send_text_hint)
        }
        val pad = dp(16)
        AlertDialog.Builder(this)
            .setTitle(R.string.send_text_title)
            .setView(FrameLayout(this).apply {
                setPadding(pad, dp(4), pad, 0)
                addView(input)
            })
            .setPositiveButton(R.string.send_text_send) { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) textTyper.type(text)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildPortraitRoot(): View {
        val buttons = actionButtons()
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            buttons.take(4).forEach {
                addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            buttons.drop(4).forEach {
                addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            addView(TextView(this@MainActivity).apply { text = getString(R.string.layout_label); textSize = 13f })
            addView(layoutSpinner(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        chromeViews += listOf<View>(statusView, detailView, row1, row2, spinnerRow)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(detailView)
            addView(row1)
            addView(row2)
            addView(spinnerRow)
            addView(padContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun buildLandscapeRoot(): View {
        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(detailView)
            addView(layoutSpinner())
            actionButtons().forEach { addView(it) }
        }
        val panel = ScrollView(this).apply {
            isFillViewport = true
            addView(
                panelContent,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        chromeViews += panel
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(panel, LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(padContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private companion object {
        const val RC_BLUETOOTH = 41
        const val RC_MIC = 42
        const val DISCOVERABLE_SECONDS = 120
        const val SWATCH_COLUMNS = 4
        const val PAUSE_STEP_MS = 400L
        const val VOICE_TAP_MS = 120L

        /** Mouse counts per full-tilt sample (~50 Hz) when gyro drives the pointer. */
        const val GYRO_MOUSE_SPEED = 14f
        const val PREF_SELECTION = "layout_sel"
        const val PREF_SENSITIVITY = "touchpad_sensitivity"
        const val PREF_HAPTICS = "haptics"
        const val PREF_DEADZONE = "stick_deadzone"
        const val PREF_LAST_HOST = "last_host"
        const val PREF_DESC_PREFIX = "desc_"
        const val PREF_HOST_LAYOUT_PREFIX = "host_layout_"
        const val PREF_APP_BG = "app_bg"
        const val PREF_NDS_PEN = "nds_pen"
        const val PREF_SUPPORTER = "supporter_preview"
        const val PREF_TURBO_HZ = "turbo_hz"
        const val PREF_SCROLL_INVERT = "scroll_invert"
        const val PREF_VOLKEYS = "volume_keys_mode"
        const val PREF_VOICE = "voice_enabled"
        const val PREF_GYRO_TARGET = "gyro_target"
        const val PREF_GYRO_SENS = "gyro_sensitivity"
        const val PREF_GYRO_INV_X = "gyro_invert_x"
        const val PREF_GYRO_INV_Y = "gyro_invert_y"

        /** Swap to Ko-fi/GitHub Sponsors when the owner creates one (Slice-9 card). */
        const val SUPPORT_URL = "https://github.com/menno420/product-forge"
    }
}
