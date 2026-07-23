/*
 * MainActivity — the controller (Slice 4: usable controller · Slice 5: layouts,
 * touchpad, keyboard, landscape).
 *
 * Runs the runtime-permission flow, the REAL registerApp() probe, and the pairing
 * actions (make-discoverable / connect-to-bonded), then drives the combo HID device
 * from six switchable layouts: Full gamepad · GBA pad · Touchpad · Keyboard ·
 * Emu keys · Media. Layout choice and touchpad sensitivity persist in activity
 * prefs.
 *
 * Orientation: the manifest opts this activity out of rotation restarts
 * (configChanges) and onConfigurationChanged rebuilds the view tree by hand —
 * portrait stacks the chrome above the pad, landscape moves it into a compact side
 * panel so the pad gets the full height. The point is the TRANSPORT: rotating
 * mid-game must not tear down the HID registration or drop the host connection,
 * which an activity recreate would (owner playtest note, 2026-07-23).
 *
 * Still plain android.app.Activity + programmatic widgets — no AndroidX. The
 * verdict shown in the status line always comes from the SHARED decision model
 * (:capability-core), fed by the live probe.
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
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.productforge.phonecontroller.capability.Verdict
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.hid.MouseButton
import com.productforge.phonecontroller.transport.BluetoothHidDeviceTransport
import com.productforge.phonecontroller.transport.HidTransportListener
import com.productforge.phonecontroller.ui.ControllerPads
import com.productforge.phonecontroller.ui.PadHost
import com.productforge.phonecontroller.ui.TouchpadConfig

class MainActivity : Activity(), HidTransportListener, PadHost {

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var padContainer: FrameLayout

    private var transport: BluetoothHidDeviceTransport? = null

    private var currentPad = Pad.FULL_GAMEPAD
    private var lastStatus: CharSequence = ""
    private var lastDetail: CharSequence = ""

    private val touchpadConfig = object : TouchpadConfig {
        override var sensitivity: Float
            get() = getPreferences(Context.MODE_PRIVATE).getFloat(PREF_SENSITIVITY, 1.0f)
            set(value) {
                getPreferences(Context.MODE_PRIVATE).edit().putFloat(PREF_SENSITIVITY, value).apply()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A controller with a sleeping screen is a dead controller.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        currentPad = Pad.entries.getOrElse(
            getPreferences(Context.MODE_PRIVATE).getInt(PREF_LAYOUT, Pad.FULL_GAMEPAD.ordinal),
        ) { Pad.FULL_GAMEPAD }

        lastStatus = getString(R.string.probing)
        buildUi()
        ensurePermissionsThenStart()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Rotation: rebuild the chrome for the new orientation WITHOUT recreating the
        // activity — the transport (and the live HID connection) must survive.
        super.onConfigurationChanged(newConfig)
        buildUi()
    }

    override fun onDestroy() {
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
        val names = devices.map { d ->
            runCatching { d.name }.getOrNull() ?: d.address
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.connect_dialog_title)
            .setItems(names) { _, which ->
                setDetail(getString(R.string.connecting_fmt, names[which]))
                t.connectTo(devices[which])
            }
            .show()
    }

    // --- HidTransportListener (callbacks arrive off the main thread) -----------------

    override fun onVerdict(verdict: Verdict) {
        runOnUiThread {
            setStatus(getString(R.string.verdict_fmt, verdict.code.name, verdict.headline))
            setDetail(if (verdict.coreLoopAvailable) "" else verdict.reason)
        }
    }

    override fun onRegistered() {
        runOnUiThread { setStatus(getString(R.string.registered_hint)) }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean) {
        runOnUiThread {
            if (connected) {
                val name = transport?.connectedHostName()
                    ?: runCatching { device?.name }.getOrNull()
                    ?: device?.address
                    ?: getString(R.string.unknown_device)
                setStatus(getString(R.string.connected_fmt, name))
            } else {
                setStatus(getString(R.string.disconnected_hint))
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { setDetail(getString(R.string.error_fmt, message)) }
    }

    // --- PadHost (UI -> transport) ----------------------------------------------------

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

    // --- UI scaffolding ----------------------------------------------------------------

    private enum class Pad(val labelRes: Int) {
        FULL_GAMEPAD(R.string.layout_full_gamepad),
        GBA(R.string.layout_gba),
        TOUCHPAD(R.string.layout_touchpad),
        KEYBOARD(R.string.layout_keyboard),
        EMU_KEYS(R.string.layout_emu_keys),
        MEDIA(R.string.layout_media),
    }

    private fun showPad(pad: Pad) {
        currentPad = pad
        getPreferences(Context.MODE_PRIVATE).edit().putInt(PREF_LAYOUT, pad.ordinal).apply()
        padContainer.removeAllViews()
        val view: View = when (pad) {
            Pad.FULL_GAMEPAD -> ControllerPads.buildGamepadPad(this, this)
            Pad.GBA -> ControllerPads.buildGbaPad(this, this)
            Pad.TOUCHPAD -> ControllerPads.buildTouchpadPad(
                this, this, touchpadConfig,
                getString(R.string.touchpad_hint), getString(R.string.sensitivity),
            )
            Pad.KEYBOARD -> ControllerPads.buildKeyboardPad(this, this)
            Pad.EMU_KEYS -> ControllerPads.buildEmuKeysPad(this, this)
            Pad.MEDIA -> ControllerPads.buildMediaPad(this, this)
        }
        padContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

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

    private fun buildUi() {
        statusView = TextView(this).apply {
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(2))
            text = lastStatus
        }
        detailView = TextView(this).apply {
            textSize = 12f
            setPadding(dp(12), 0, dp(12), dp(2))
            text = lastDetail
        }
        padContainer = FrameLayout(this).apply { setPadding(dp(6), 0, dp(6), dp(6)) }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        setContentView(if (landscape) buildLandscapeRoot() else buildPortraitRoot())
        showPad(currentPad)
    }

    private fun actionButton(labelRes: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = getString(labelRes)
            isAllCaps = false
            textSize = 13f
            setOnClickListener { onClick() }
        }

    private fun layoutSpinner(): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            Pad.entries.map { getString(it.labelRes) },
        )
        setSelection(currentPad.ordinal, false)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val pad = Pad.entries[position]
                if (pad != currentPad) showPad(pad)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Portrait: chrome stacked above a full-width pad. */
    private fun buildPortraitRoot(): View {
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            listOf(
                actionButton(R.string.make_discoverable) { makeDiscoverable() },
                actionButton(R.string.connect_bonded) { pickBondedAndConnect() },
                actionButton(R.string.reprobe) { ensurePermissionsThenStart() },
            ).forEach {
                addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.layout_label)
                    textSize = 13f
                },
            )
            addView(
                layoutSpinner(),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(detailView)
            addView(actionRow)
            addView(spinnerRow)
            addView(padContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /** Landscape: compact scrollable side panel, pad gets the full height. */
    private fun buildLandscapeRoot(): View {
        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(detailView)
            addView(layoutSpinner())
            addView(actionButton(R.string.make_discoverable) { makeDiscoverable() })
            addView(actionButton(R.string.connect_bonded) { pickBondedAndConnect() })
            addView(actionButton(R.string.reprobe) { ensurePermissionsThenStart() })
        }
        val panel = ScrollView(this).apply {
            isFillViewport = true
            addView(
                panelContent,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(panel, LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(padContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private companion object {
        const val RC_BLUETOOTH = 41
        const val DISCOVERABLE_SECONDS = 120
        const val PREF_LAYOUT = "layout"
        const val PREF_SENSITIVITY = "touchpad_sensitivity"
    }
}
