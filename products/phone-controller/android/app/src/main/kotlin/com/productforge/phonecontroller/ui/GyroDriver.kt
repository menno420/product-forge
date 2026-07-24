/*
 * GyroDriver — tilt-as-right-stick (Slice 6, the serverless answer to Monect's
 * gyro aiming / tilt steering).
 *
 * Uses the game-rotation-vector sensor (gyro+accel fusion, no magnetometer jumps;
 * falls back to the plain rotation vector). On enable, the CURRENT orientation is
 * captured as the neutral baseline; pitch/roll deltas from that baseline map to the
 * right stick's Z/RZ axes: roll (tilt left/right around the long axis) → Z, pitch
 * (tip toward/away) → RZ. ±[fullTiltDegrees] of tilt = full deflection.
 * Unregistered on disable, pad switch, and disconnect (no sensor drain).
 */
package com.productforge.phonecontroller.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.roundToInt

class GyroDriver(
    context: Context,
    private val onStick: (z: Int, rz: Int) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var fullTiltDegrees: Float = 25f

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
        onStick(0, 0)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val pitchDeg = Math.toDegrees(angles[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(angles[2].toDouble()).toFloat()
        val base = baseline ?: floatArrayOf(pitchDeg, rollDeg).also { baseline = it }
        val dPitch = pitchDeg - base[0]
        val dRoll = rollDeg - base[1]
        val z = ((dRoll / fullTiltDegrees) * 127f).roundToInt().coerceIn(-127, 127)
        val rz = ((dPitch / fullTiltDegrees) * 127f).roundToInt().coerceIn(-127, 127)
        onStick(z, rz)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not used */ }
}
