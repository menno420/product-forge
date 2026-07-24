/*
 * StickView — an on-screen analog thumbstick (Slice 6).
 *
 * Circular base + draggable thumb. Emits axis values in the HID logical range
 * (-127..127) via [Listener.onStick]; auto-centers (and emits 0,0) on release.
 * The configurable [deadzonePct] is applied HERE, UI-side, with rescaling so the
 * usable range stays smooth from the deadzone edge outward — GamepadState stays a
 * pure wire-format builder (session-card guard recipe).
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class StickView(context: Context, private val listener: Listener) : View(context) {

    fun interface Listener {
        /** Axis values in -127..127 (x right-positive, y down-positive — HID frame). */
        fun onStick(x: Int, y: Int)
    }

    /** Deadzone as a fraction of the stick radius (0.0..0.5), from Settings. */
    var deadzonePct: Float = 0.08f

    private var thumbX = 0f
    private var thumbY = 0f
    private var engaged = false

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2A808080
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55808080
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Light enough to stay visible on the Slice-8 dark background.
        color = 0x88A9B4C0.toInt()
        style = Paint.Style.FILL
    }

    private fun radius(): Float = min(width, height) / 2f * 0.92f
    private fun thumbRadius(): Float = radius() * 0.38f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius(), basePaint)
        canvas.drawCircle(cx, cy, radius(), ringPaint)
        canvas.drawCircle(cx + thumbX, cy + thumbY, thumbRadius(), thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN && !engaged) {
                    engaged = true
                    Haptics.tick(this)
                }
                val cx = width / 2f
                val cy = height / 2f
                var dx = e.x - cx
                var dy = e.y - cy
                val r = radius() - thumbRadius() * 0.4f
                val len = hypot(dx, dy)
                if (len > r && len > 0f) {
                    dx *= r / len
                    dy *= r / len
                }
                thumbX = dx
                thumbY = dy
                emit(dx / r, dy / r)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                engaged = false
                thumbX = 0f
                thumbY = 0f
                listener.onStick(0, 0)
                invalidate()
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Deadzone + rescale, then map the unit vector to the HID logical range. */
    private fun emit(nx: Float, ny: Float) {
        val mag = hypot(nx, ny)
        if (mag < deadzonePct) {
            listener.onStick(0, 0)
            return
        }
        // Rescale so output magnitude runs 0..1 from the deadzone edge to full tilt.
        val scaled = ((mag - deadzonePct) / (1f - deadzonePct)).coerceIn(0f, 1f)
        val ux = if (mag > 0f) nx / mag else 0f
        val uy = if (mag > 0f) ny / mag else 0f
        listener.onStick(
            (ux * scaled * 127f).roundToInt().coerceIn(-127, 127),
            (uy * scaled * 127f).roundToInt().coerceIn(-127, 127),
        )
    }
}
