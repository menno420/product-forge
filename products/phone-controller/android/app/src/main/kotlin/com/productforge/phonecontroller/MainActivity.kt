/*
 * MainActivity — the controller (Slice 4).
 *
 * The Slice-3 stub only rendered a baseline verdict; this is the usable app the
 * owner directive asks for: request the runtime Bluetooth permissions, run the REAL
 * registerApp() probe, guide pairing (make-discoverable + connect-to-bonded), and
 * drive the combo HID device from three switchable pads (gamepad / keys / media).
 *
 * Still plain android.app.Activity + programmatic widgets — no AndroidX — so the
 * dependency graph stays exactly what Slice 3 proved in CI. The verdict shown at
 * the top always comes from the SHARED decision model (:capability-core), fed by
 * the live probe.
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
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.productforge.phonecontroller.capability.Verdict
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.transport.BluetoothHidDeviceTransport
import com.productforge.phonecontroller.transport.HidTransportListener
import com.productforge.phonecontroller.ui.ControllerPads
import com.productforge.phonecontroller.ui.PadHost

class MainActivity : Activity(), HidTransportListener, PadHost {

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var padContainer: FrameLayout
    private lateinit var tabGamepad: Button
    private lateinit var tabKeys: Button
    private lateinit var tabMedia: Button

    private var transport: BluetoothHidDeviceTransport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A controller with a sleeping screen is a dead controller.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        buildUi()
        showPad(Pad.GAMEPAD)
        ensurePermissionsThenStart()
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
                statusView.text = getString(R.string.requesting_permissions)
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
            statusView.text = getString(R.string.permission_denied)
        }
    }

    private fun startTransport() {
        transport?.stop()
        statusView.text = getString(R.string.probing)
        detailView.text = ""
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
        }.onFailure { detailView.text = getString(R.string.error_fmt, it.message ?: "discoverable request failed") }
    }

    private fun pickBondedAndConnect() {
        val t = transport ?: return
        val devices = t.bondedHosts()
        if (devices.isEmpty()) {
            detailView.text = getString(R.string.no_bonded_devices)
            return
        }
        val names = devices.map { d ->
            runCatching { d.name }.getOrNull() ?: d.address
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.connect_dialog_title)
            .setItems(names) { _, which ->
                detailView.text = getString(R.string.connecting_fmt, names[which])
                t.connectTo(devices[which])
            }
            .show()
    }

    // --- HidTransportListener (callbacks arrive off the main thread) -----------------

    override fun onVerdict(verdict: Verdict) {
        runOnUiThread {
            statusView.text = getString(R.string.verdict_fmt, verdict.code.name, verdict.headline)
            detailView.text = if (verdict.coreLoopAvailable) "" else verdict.reason
        }
    }

    override fun onRegistered() {
        runOnUiThread {
            statusView.text = getString(R.string.registered_hint)
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean) {
        runOnUiThread {
            statusView.text = if (connected) {
                val name = transport?.connectedHostName()
                    ?: runCatching { device?.name }.getOrNull()
                    ?: device?.address
                    ?: getString(R.string.unknown_device)
                getString(R.string.connected_fmt, name)
            } else {
                getString(R.string.disconnected_hint)
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { detailView.text = getString(R.string.error_fmt, message) }
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

    // --- UI scaffolding ----------------------------------------------------------------

    private enum class Pad { GAMEPAD, KEYS, MEDIA }

    private fun showPad(pad: Pad) {
        tabGamepad.isEnabled = pad != Pad.GAMEPAD
        tabKeys.isEnabled = pad != Pad.KEYS
        tabMedia.isEnabled = pad != Pad.MEDIA
        padContainer.removeAllViews()
        val view: View = when (pad) {
            Pad.GAMEPAD -> ControllerPads.buildGamepadPad(this, this)
            Pad.KEYS -> ControllerPads.buildKeysPad(this, this)
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

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
    ).toInt()

    private fun buildUi() {
        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(dp(16), dp(12), dp(16), dp(4))
            text = getString(R.string.probing)
        }
        detailView = TextView(this).apply {
            textSize = 13f
            setPadding(dp(16), 0, dp(16), dp(4))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(
                Button(context).apply {
                    text = getString(R.string.make_discoverable)
                    isAllCaps = false
                    setOnClickListener { makeDiscoverable() }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                Button(context).apply {
                    text = getString(R.string.connect_bonded)
                    isAllCaps = false
                    setOnClickListener { pickBondedAndConnect() }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                Button(context).apply {
                    text = getString(R.string.reprobe)
                    isAllCaps = false
                    setOnClickListener { ensurePermissionsThenStart() }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

        tabGamepad = Button(this).apply {
            text = getString(R.string.tab_gamepad)
            isAllCaps = false
            setOnClickListener { showPad(Pad.GAMEPAD) }
        }
        tabKeys = Button(this).apply {
            text = getString(R.string.tab_keys)
            isAllCaps = false
            setOnClickListener { showPad(Pad.KEYS) }
        }
        tabMedia = Button(this).apply {
            text = getString(R.string.tab_media)
            isAllCaps = false
            setOnClickListener { showPad(Pad.MEDIA) }
        }
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
            listOf(tabGamepad, tabKeys, tabMedia).forEach {
                addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        padContainer = FrameLayout(this).apply { setPadding(dp(8), 0, dp(8), dp(8)) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(detailView)
            addView(actionRow)
            addView(tabRow)
            addView(
                padContainer,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
    }

    private companion object {
        const val RC_BLUETOOTH = 41
        const val DISCOVERABLE_SECONDS = 120
    }
}
