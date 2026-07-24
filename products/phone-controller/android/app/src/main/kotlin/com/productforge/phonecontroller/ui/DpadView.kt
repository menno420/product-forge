/*
 * DpadView — an 8-way directional pad with diagonals (Slice 15).
 *
 * A finger anywhere on the pad picks one of 8 sectors by angle (plus a dead centre);
 * a diagonal sector presses TWO directions at once (e.g. UP + RIGHT), which the
 * gamepad hat combines into a true diagonal. Sliding the finger around the pad
 * presses/releases directions on the DIFF, so gliding from ▲ through the corner to ▶
 * releases and presses cleanly without lifting (the owner's "extra input between side
 * and front" ask). Used both as a built-in pad's D-pad and as a custom-layout widget.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.productforge.phonecontroller.hid.DpadDirection
import kotlin.math.atan2
import kotlin.math.hypot

class DpadView(
    context: Context,
    private val onDirection: (DpadDirection, Boolean) -> Unit,
) : View(context) {

    private val held = HashSet<DpadDirection>()

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2A808080; style = Paint.Style.FILL }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF; style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCCED6DD.toInt(); style = Paint.Style.FILL }
    private val arrow = Path()

    /** Dead-zone radius (fraction of half-size) below which no direction fires. */
    private val deadZone = 0.28f

    private fun directionsAt(x: Float, y: Float): Set<DpadDirection> {
        val cx = width / 2f
        val cy = height / 2f
        val half = minOf(width, height) / 2f
        if (half <= 0f) return emptySet()
        val dx = x - cx
        val dy = y - cy
        val mag = hypot(dx, dy) / half
        if (mag < deadZone) return emptySet()
        // 8 sectors of 45°, offset so N spans ±22.5° around straight up.
        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() // -180..180, 0 = +x (right)
        if (deg < 0) deg += 360f // 0..360, 0=right, 90=down, 180=left, 270=up
        val sector = (((deg + 22.5f) % 360f) / 45f).toInt() // 0..7, 0=right
        return when (sector) {
            0 -> setOf(DpadDirection.RIGHT)
            1 -> setOf(DpadDirection.RIGHT, DpadDirection.DOWN)
            2 -> setOf(DpadDirection.DOWN)
            3 -> setOf(DpadDirection.DOWN, DpadDirection.LEFT)
            4 -> setOf(DpadDirection.LEFT)
            5 -> setOf(DpadDirection.LEFT, DpadDirection.UP)
            6 -> setOf(DpadDirection.UP)
            else -> setOf(DpadDirection.UP, DpadDirection.RIGHT)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> apply(directionsAt(e.x, e.y))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                apply(emptySet())
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Press/release on the diff so glides don't drop or double-fire a direction. */
    private fun apply(now: Set<DpadDirection>) {
        var changed = false
        val it = held.iterator()
        while (it.hasNext()) {
            val d = it.next()
            if (d !in now) { it.remove(); onDirection(d, false); changed = true }
        }
        for (d in now) {
            if (held.add(d)) { onDirection(d, true); changed = true }
        }
        if (changed) {
            if (now.isNotEmpty()) Haptics.tick(this)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val half = minOf(width, height) / 2f * 0.94f
        canvas.drawCircle(cx, cy, half, basePaint)
        // Highlight the held arms.
        val armLen = half * 0.9f
        val armW = half * 0.30f
        fun arm(dir: DpadDirection, dxu: Float, dyu: Float) {
            val paint = if (dir in held) armPaint else null
            // Arrow triangle pointing outward.
            arrow.reset()
            val tipX = cx + dxu * armLen; val tipY = cy + dyu * armLen
            val baseX = cx + dxu * armLen * 0.45f; val baseY = cy + dyu * armLen * 0.45f
            arrow.moveTo(tipX, tipY)
            arrow.lineTo(baseX - dyu * armW, baseY - dxu * armW)
            arrow.lineTo(baseX + dyu * armW, baseY + dxu * armW)
            arrow.close()
            paint?.let { canvas.drawCircle(cx + dxu * armLen * 0.6f, cy + dyu * armLen * 0.6f, armW, it) }
            canvas.drawPath(arrow, arrowPaint)
        }
        arm(DpadDirection.UP, 0f, -1f)
        arm(DpadDirection.DOWN, 0f, 1f)
        arm(DpadDirection.LEFT, -1f, 0f)
        arm(DpadDirection.RIGHT, 1f, 0f)
    }
}
