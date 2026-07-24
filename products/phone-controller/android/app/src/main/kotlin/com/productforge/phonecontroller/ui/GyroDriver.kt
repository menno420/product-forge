/*
 * GyroDriver — tilt as a normalized 2-axis input (Slice 6, generalized in Slice 14).
 *
 * Uses the game-rotation-vector sensor (gyro+accel fusion, no magnetometer jumps;
 * falls back to the plain rotation vector). On enable — and on [recenter] — the
 * CURRENT orientation is captured as the neutral baseline ("0 input" for however you
 * hold the phone). Pitch/roll deltas from that baseline become a normalized vector
 * (nx, ny) in −1..1: roll (tilt left/right around the long axis) → nx, pitch (tip
 * toward/away) → ny. ±[fullTiltDegrees] of tilt = full deflection (lower = more
 * sensitive). [invertX]/[invertY] flip an axis. The HOST maps the vector to a target
 * (right stick / left stick / mouse) — the driver stays target-agnostic.
 *
 * Unregistered on disable, pad switch, and disconnect (no sensor drain).
 */
package com.productforge.phonecontroller.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class GyroDriver(
    context: Context,
    /** Normalized tilt sample, each axis in −1..1 (host routes it to a target). */
    private val onSample: (nx: Float, ny: Float) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** Degrees of tilt for full deflection (10..40; lower = more sensitive). */
    var fullTiltDegrees: Float = 25f
    var invertX: Boolean = false
    var invertY: Boolean = false

    private var baseline: FloatArray? = null
    private val rotation = FloatArray(9)
    private val angles = FloatArray(3)

    /** True when a usable sensor exists on this phone. */
    val available: Boolean get() = sensor != null

    var running = false
        private set

    fun start(): Boolean {
        val sm = sensorManager ?: return false
        val s = sensor ?: return false
        if (running) return true
        baseline = null // captured on the first sample after enable
        running = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        return running
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
        baseline = null
        onSample(0f, 0f)
    }

    /** Re-capture neutral from the current hold on the next sample ("set 0 input"). */
    fun recenter() {
        baseline = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val pitchDeg = Math.toDegrees(angles[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(angles[2].toDouble()).toFloat()
        val base = baseline ?: floatArrayOf(pitchDeg, rollDeg).also { baseline = it }
        val full = fullTiltDegrees.coerceAtLeast(1f)
        var nx = ((rollDeg - base[1]) / full).coerceIn(-1f, 1f)
        var ny = ((pitchDeg - base[0]) / full).coerceIn(-1f, 1f)
        if (invertX) nx = -nx
        if (invertY) ny = -ny
        onSample(nx, ny)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not used */ }
}
