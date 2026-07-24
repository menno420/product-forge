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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
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
import com.productforge.phonecontroller.layout.PadShape
import com.productforge.phonecontroller.ui.ButtonStyler
import com.productforge.phonecontroller.transport.BluetoothHidDeviceTransport
import com.productforge.phonecontroller.transport.HidTransportListener
import com.productforge.phonecontroller.ui.ControllerPads
import com.productforge.phonecontroller.ui.CustomPadView
import com.productforge.phonecontroller.ui.GyroDriver
import com.productforge.phonecontroller.ui.GyroToggle
import com.productforge.phonecontroller.ui.Haptics
import com.productforge.phonecontroller.ui.PadHost
import com.productforge.phonecontroller.ui.TouchpadConfig
import com.productforge.phonecontroller.ui.TurboEngine

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
    }

    private val gyroDriver by lazy {
        GyroDriver(this) { z, rz -> transport?.rightStick(z, rz) }
    }

    private val gyroToggle = object : GyroToggle {
        override val available: Boolean get() = gyroDriver.available
        override val running: Boolean get() = gyroDriver.running
        override fun toggle(): Boolean {
            if (gyroDriver.running) gyroDriver.stop() else gyroDriver.start()
            return gyroDriver.running
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Haptics.enabled = prefs().getBoolean(PREF_HAPTICS, true)
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
        gyroDriver.stop()
        turbo.cancelAll()
        transport?.stop()
        transport = null
        super.onDestroy()
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
        if (requestCode != RC_BLUETOOTH) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startTransport()
        } else {
            setStatus(getString(R.string.permission_denied))
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
                turbo.cancelAll()
                gyroDriver.stop()
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

    // NDS is appended, not inserted: saved selections are "b:<ordinal>" strings, so
    // existing ordinals must stay stable across updates.
    private enum class Pad(val labelRes: Int) {
        FULL_GAMEPAD(R.string.layout_full_gamepad),
        GBA(R.string.layout_gba),
        ANALOG(R.string.layout_analog),
        TOUCHPAD(R.string.layout_touchpad),
        KEYBOARD(R.string.layout_keyboard),
        EMU_KEYS(R.string.layout_emu_keys),
        MEDIA(R.string.layout_media),
        NDS(R.string.layout_nds),
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
        turbo.cancelAll()
        gyroDriver.stop()
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
            CustomPadView(this, layout, editMode = false, actionResolver = ::resolveAction, turbo = turbo)
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
            }
        }
        padContainer.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    /** Map a custom-layout action spec to the matching transport call. */
    private fun resolveAction(spec: PadButtonSpec): (Boolean) -> Unit {
        val action: PadAction = spec.action
        return runCatching<(Boolean) -> Unit> {
            when (action.type) {
                PadActionType.GAMEPAD -> {
                    val b = GamepadButton.valueOf(action.code)
                    ({ down -> onGamepadButton(b, down) })
                }
                PadActionType.DPAD -> {
                    val d = DpadDirection.valueOf(action.code)
                    ({ down -> onDpad(d, down) })
                }
                PadActionType.KEY -> {
                    val usage = action.code.toInt()
                    ({ down -> onKey(usage, down) })
                }
                PadActionType.MODIFIER -> {
                    val mask = action.code.toInt()
                    ({ down -> onModifier(mask, down) })
                }
                PadActionType.MEDIA -> {
                    val m = MediaButton.valueOf(action.code)
                    ({ down -> if (down) onMediaTap(m) })
                }
                PadActionType.MOUSE -> {
                    val mb = MouseButton.valueOf(action.code)
                    ({ down -> onMouseButton(mb, down) })
                }
            }
        }.getOrElse { { _ -> } } // a corrupt action never crashes the pad
    }

    // --- layout manager + editor -----------------------------------------------------------

    private fun openLayoutManager() {
        val layouts = layoutStore.all()
        val items = layouts.map { it.name } + getString(R.string.new_layout)
        AlertDialog.Builder(this)
            .setTitle(R.string.layouts_title)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == layouts.size) {
                    promptText(getString(R.string.new_layout_name), "") { name ->
                        val layout = CustomLayout.template(
                            "cl_${System.currentTimeMillis()}",
                            name.ifBlank { getString(R.string.new_layout) },
                        )
                        layoutStore.save(layout)
                        startEditor(layout)
                    }
                } else {
                    layoutOptions(layouts[which])
                }
            }
            .show()
    }

    private fun layoutOptions(layout: CustomLayout) {
        val options = arrayOf(
            getString(R.string.layout_use),
            getString(R.string.layout_edit),
            getString(R.string.layout_background),
            getString(R.string.layout_duplicate),
            getString(R.string.layout_rename),
            getString(R.string.layout_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(layout.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showSelection("c:${layout.id}")
                        rebuildSpinnerSelection()
                    }
                    1 -> startEditor(layout)
                    2 -> AlertDialog.Builder(this)
                        .setTitle(R.string.layout_background)
                        .setItems(ButtonStyler.PAD_BACKGROUNDS.map { it.first }.toTypedArray()) { _, bg ->
                            layout.bgColorArgb = ButtonStyler.PAD_BACKGROUNDS[bg].second
                            layoutStore.save(layout)
                            if (currentSelection == "c:${layout.id}") showSelection(currentSelection)
                        }
                        .show()
                    3 -> {
                        val copy = layout.duplicate(
                            "cl_${System.currentTimeMillis()}",
                            getString(R.string.layout_copy_name_fmt, layout.name),
                        )
                        layoutStore.save(copy)
                        rebuildSpinnerSelection()
                    }
                    4 -> promptText(getString(R.string.layout_rename), layout.name) { name ->
                        layout.name = name.ifBlank { layout.name }
                        layoutStore.save(layout)
                        rebuildSpinnerSelection()
                    }
                    5 -> {
                        layoutStore.delete(layout.id)
                        if (currentSelection == "c:${layout.id}") showSelection("b:0")
                        rebuildSpinnerSelection()
                    }
                }
            }
            .show()
    }

    private fun startEditor(layout: CustomLayout) {
        turbo.cancelAll()
        gyroDriver.stop()
        transport?.releaseHeldInputs()
        editingLayout = layout

        padContainer.removeAllViews()
        val editView = CustomPadView(
            this, layout, editMode = true,
            actionResolver = ::resolveAction, turbo = turbo,
            onEditButton = { spec -> buttonConfigDialog(layout, spec) },
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
                getString(R.string.editor_hint_move) to {
                    setDetail(getString(R.string.editor_hint))
                },
                getString(R.string.editor_save) to {
                    layoutStore.save(layout)
                    showSelection("c:${layout.id}")
                    rebuildSpinnerSelection()
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

    private fun buttonConfigDialog(layout: CustomLayout, spec: PadButtonSpec) {
        val turboLabel = if (spec.turbo) R.string.editor_turbo_off else R.string.editor_turbo_on
        val options = arrayOf(
            getString(R.string.editor_change_action),
            getString(turboLabel),
            getString(R.string.editor_size),
            getString(R.string.editor_color),
            getString(R.string.editor_shape),
            getString(R.string.editor_opacity),
            getString(R.string.editor_text_size),
            getString(R.string.editor_label),
            getString(R.string.editor_duplicate),
            getString(R.string.editor_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(spec.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickActionType(spec)
                    1 -> {
                        spec.turbo = !spec.turbo
                        editingView?.rebuild()
                    }
                    2 -> pickSize(spec)
                    3 -> pickColor(spec)
                    4 -> pickShape(spec)
                    5 -> pickOpacity(spec)
                    6 -> pickTextSize(spec)
                    7 -> promptText(getString(R.string.editor_label), spec.label) { text ->
                        spec.label = text.ifBlank { spec.label }
                        editingView?.rebuild()
                    }
                    8 -> {
                        val copy = spec.copy(xPct = spec.xPct + 0.05f, yPct = spec.yPct + 0.05f)
                        copy.clampToPad()
                        layout.buttons.add(copy)
                        editingView?.rebuild()
                    }
                    9 -> {
                        layout.buttons.remove(spec)
                        editingView?.rebuild()
                    }
                }
            }
            .show()
    }

    /** S–XL presets + 2% width/height steppers (fine control past the presets). */
    private fun pickSize(spec: PadButtonSpec) {
        val presets = arrayOf(
            "S" to (0.10f to 0.14f),
            "M" to (0.14f to 0.18f),
            "L" to (0.18f to 0.24f),
            "XL" to (0.24f to 0.30f),
        )
        val items = presets.map { it.first } + listOf(
            getString(R.string.size_wider), getString(R.string.size_narrower),
            getString(R.string.size_taller), getString(R.string.size_shorter),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_size)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which < presets.size -> {
                        spec.wPct = presets[which].second.first
                        spec.hPct = presets[which].second.second
                    }
                    which == presets.size -> spec.wPct += 0.02f
                    which == presets.size + 1 -> spec.wPct -= 0.02f
                    which == presets.size + 2 -> spec.hPct += 0.02f
                    else -> spec.hPct -= 0.02f
                }
                spec.clampToPad()
                editingView?.rebuild()
            }
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

    private fun pickActionCode(spec: PadButtonSpec, type: PadActionType) {
        val choices: List<Pair<String, String>> = when (type) {
            PadActionType.GAMEPAD -> GamepadButton.entries.map { it.name to it.name }
            PadActionType.DPAD -> DpadDirection.entries.map { it.name to it.name }
            PadActionType.MEDIA -> MediaButton.entries.map { it.name to it.name }
            PadActionType.MOUSE -> MouseButton.entries.map { it.name to it.name }
            PadActionType.MODIFIER -> listOf(
                "SHIFT" to KeyUsage.MOD_LEFT_SHIFT.toString(),
                "CTRL" to KeyUsage.MOD_LEFT_CTRL.toString(),
                "ALT" to KeyUsage.MOD_LEFT_ALT.toString(),
            )
            PadActionType.KEY -> buildList {
                add("↑" to KeyUsage.ARROW_UP.toString())
                add("↓" to KeyUsage.ARROW_DOWN.toString())
                add("←" to KeyUsage.ARROW_LEFT.toString())
                add("→" to KeyUsage.ARROW_RIGHT.toString())
                add("SPACE" to KeyUsage.SPACE.toString())
                add("ENTER" to KeyUsage.ENTER.toString())
                add("ESC" to KeyUsage.ESCAPE.toString())
                add("TAB" to KeyUsage.TAB.toString())
                add("⌫" to KeyUsage.BACKSPACE.toString())
                for (c in 'a'..'z') add(c.uppercase() to KeyUsage.letterUsage(c).toString())
                for (c in '0'..'9') add(c.toString() to KeyUsage.digitUsage(c).toString())
            }
        }
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
        actionButton(R.string.dim) { toggleDim() },
        actionButton(R.string.immersive) { toggleImmersive() },
        actionButton(R.string.focus) { setFocusMode(true) },
        actionButton(R.string.settings_title) { openSettings() },
    )

    private fun buildPortraitRoot(): View {
        val buttons = actionButtons()
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            buttons.take(3).forEach {
                addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            buttons.drop(3).forEach {
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
        const val DISCOVERABLE_SECONDS = 120
        const val SWATCH_COLUMNS = 4
        const val PREF_SELECTION = "layout_sel"
        const val PREF_SENSITIVITY = "touchpad_sensitivity"
        const val PREF_HAPTICS = "haptics"
        const val PREF_DEADZONE = "stick_deadzone"
        const val PREF_LAST_HOST = "last_host"
        const val PREF_DESC_PREFIX = "desc_"
        const val PREF_HOST_LAYOUT_PREFIX = "host_layout_"
        const val PREF_APP_BG = "app_bg"
        const val PREF_NDS_PEN = "nds_pen"
    }
}
