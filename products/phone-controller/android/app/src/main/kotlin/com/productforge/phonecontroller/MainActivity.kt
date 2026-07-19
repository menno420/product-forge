/*
 * MainActivity — minimal capability-screen entry point (Slice 3 stub).
 *
 * Slice 3's agent-buildable scope is compile-green proof that the Android app module
 * wires together: the AGP build, the shared :capability-core decision model, and the
 * real BluetoothHidDeviceTransport. This Activity is deliberately the smallest thing
 * that exercises that wiring end to end at compile time — it constructs the transport
 * and renders the verdict the shared `evaluate()` returns. The full customisable
 * controller UI (button grid, pairing flow, reconnection) is a later, owner-facing
 * slice; the real receiver-driving playtest is hardware-gated (owner playtests).
 *
 * Kept dependency-light on purpose: plain android.app.Activity + a programmatic
 * TextView, no AndroidX/appcompat, no resource layouts — so `assembleDebug` needs only
 * the Android SDK, not a wider dependency graph.
 */
package com.productforge.phonecontroller

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import com.productforge.phonecontroller.capability.Verdict
import com.productforge.phonecontroller.transport.BluetoothHidDeviceTransport
import com.productforge.phonecontroller.transport.HidTransportListener

class MainActivity : Activity(), HidTransportListener {

    private lateinit var statusView: TextView
    private var transport: BluetoothHidDeviceTransport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = getString(R.string.probing)
        }
        setContentView(statusView)

        // Build the real transport over the default Bluetooth adapter. On a device the
        // transport runs the live registerApp() probe; here (Slice 3 compile proof) we
        // ask the shared decision model for the verdict the current runtime facts imply,
        // so the entry point demonstrably reaches evaluate() through the transport.
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val hid = BluetoothHidDeviceTransport(applicationContext, adapter, this)
        transport = hid

        // Not-yet-probed baseline: no live registration, BLE capability unknown → the
        // engine classifies from OS/platform alone. Real probing is started by the
        // controller UI in a later slice.
        onVerdict(hid.probeAndEvaluate(hidRoleAvailable = false, blePeripheral = false))
    }

    override fun onDestroy() {
        transport?.stop()
        transport = null
        super.onDestroy()
    }

    // --- HidTransportListener -----------------------------------------------------

    override fun onVerdict(verdict: Verdict) {
        statusView.text = getString(R.string.verdict_fmt, verdict.code.name, verdict.reason)
    }

    override fun onRegistered() {
        /* No-op in the stub — the controller UI reacts to this in a later slice. */
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, connected: Boolean) {
        /* No-op in the stub. */
    }

    override fun onError(message: String) {
        statusView.text = getString(R.string.error_fmt, message)
    }
}
