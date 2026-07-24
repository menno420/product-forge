/*
 * GestureRecorderView — captures a finger path as a portable TouchGesture
 * (Slice 13, the OP-Auto-Clicker-style recorder).
 *
 * Author a gesture by performing it on this full-screen surface: each finger-down
 * to finger-up is one stroke, its points sampled in PERCENT of the view (== percent
 * of the screen, since the recorder is full-screen) with real timing. Samples are
 * thinned (min time/space between kept points) so a long drag doesn't blow past the
 * codec's point cap. The live path is drawn as you go. Multiple sequential strokes
 * (e.g. tap, then swipe) are supported; single finger only in this version.
 *
 * The host reads [buildGesture]/[strokeCount] and calls [clear] for a retake.
 */
package com.productforge.phonecontroller.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.productforge.phonecontroller.hid.GesturePoint
import com.productforge.phonecontroller.hid.GestureStroke
import com.productforge.phonecontroller.hid.TouchGesture
import kotlin.math.abs

class GestureRecorderView(context: Context) : View(context) {

    private class RawStroke(val startMs: Long) {
        val points = mutableListOf<GesturePoint>()
        var durationMs: Long = 0L
    }

    private val strokes = mutableListOf<RawStroke>()
    private var current: RawStroke? = null
    private var firstDownAt = 0L

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CC2FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CC2FF.toInt()
        style = Paint.Style.FILL
    }

    /** Number of strokes captured so far (host shows it / enables Save). */
    val strokeCount: Int get() = strokes.size

    fun clear() {
        strokes.clear()
        current = null
        firstDownAt = 0L
        invalidate()
    }

    /** The captured gesture, or null if nothing usable was recorded. */
    fun buildGesture(): TouchGesture? {
        val usable = strokes.filter { it.points.isNotEmpty() }
        if (usable.isEmpty()) return null
        return TouchGesture(
            usable.map { s ->
                GestureStroke(
                    points = s.points.toList(),
                    startMs = s.startMs,
                    // Floor very short taps so the dispatched touch actually registers.
                    durationMs = s.durationMs.coerceAtLeast(MIN_STROKE_MS),
                )
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (strokes.isEmpty()) firstDownAt = e.eventTime
                val s = RawStroke(startMs = (e.eventTime - firstDownAt).coerceAtLeast(0L))
                s.points.add(GesturePoint(e.x / w, e.y / h))
                current = s
                strokes.add(s)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                val last = s.points.lastOrNull()
                val px = e.x / w
                val py = e.y / h
                // Thin: keep a point only if it moved enough OR is the first move.
                if (last == null || abs(px - last.xPct) > MIN_SPACE || abs(py - last.yPct) > MIN_SPACE) {
                    if (s.points.size < MAX_POINTS_PER_STROKE) s.points.add(GesturePoint(px, py))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val s = current
                if (s != null) {
                    s.durationMs = (e.eventTime - (firstDownAt + s.startMs)).coerceAtLeast(1L)
                }
                current = null
                invalidate()
                performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (s in strokes) {
            if (s.points.size == 1) {
                val p = s.points[0]
                canvas.drawCircle(p.xPct * w, p.yPct * h, 14f, dotPaint)
            } else if (s.points.size > 1) {
                val path = Path()
                val first = s.points.first()
                path.moveTo(first.xPct * w, first.yPct * h)
                for (p in s.points.drop(1)) path.lineTo(p.xPct * w, p.yPct * h)
                canvas.drawPath(path, pathPaint)
            }
        }
    }

    private companion object {
        const val MIN_SPACE = 0.005f // 0.5% of the screen between kept points
        const val MIN_STROKE_MS = 50L
        const val MAX_POINTS_PER_STROKE = 200
    }
}
