/*
 * GyroVisualizerView — a live picture of the gyro's current output (Slice 14).
 *
 * A rounded box with a centre crosshair and a dot at the current normalized tilt
 * (nx, ny), so the user can SEE what tilting does — and calibrate — even before
 * connecting to a host. [update] is called from the sensor callback (main thread).
 */
package com.productforge.phonecontroller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class GyroVisualizerView(context: Context) : View(context) {

    private var nx = 0f
    private var ny = 0f

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22A9B4C0; style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55A9B4C0.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF43A047.toInt(); style = Paint.Style.FILL
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5543A047.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val box = RectF()

    /** Push a new normalized sample (each axis −1..1) and redraw. */
    fun update(nx: Float, ny: Float) {
        this.nx = nx.coerceIn(-1f, 1f)
        this.ny = ny.coerceIn(-1f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 6f * resources.displayMetrics.density
        val side = minOf(width, height) - pad * 2
        val cx = width / 2f
        val cy = height / 2f
        val half = side / 2f
        box.set(cx - half, cy - half, cx + half, cy + half)
        val radius = 14f * resources.displayMetrics.density
        canvas.drawRoundRect(box, radius, radius, boxPaint)
        // Crosshair + neutral ring.
        canvas.drawLine(cx, box.top, cx, box.bottom, gridPaint)
        canvas.drawLine(box.left, cy, box.right, cy, gridPaint)
        canvas.drawCircle(cx, cy, half * 0.12f, gridPaint)
        // The live dot, with a line from centre showing direction + magnitude.
        val dx = cx + nx * half
        val dy = cy + ny * half
        canvas.drawLine(cx, cy, dx, dy, trailPaint)
        canvas.drawCircle(dx, dy, 10f * resources.displayMetrics.density, dotPaint)
    }
}
