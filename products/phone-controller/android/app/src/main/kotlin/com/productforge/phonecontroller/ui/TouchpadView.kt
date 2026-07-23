/*
 * TouchpadView — the mouse surface (Slice 5).
 *
 * Gestures:
 *   * one-finger drag        → relative pointer motion (Report 4 dx/dy)
 *   * quick tap              → left click
 *   * two-finger quick tap   → right click
 *   * two-finger drag        → wheel scroll, NATURAL direction (content follows
 *                              fingers — the phone-native expectation; flagged in
 *                              the session card for a future invert toggle)
 *
 * Motion is density-normalized (dp-based) with fractional-remainder carry, so
 * pointer speed feels the same on any screen; [sensitivity] (0.5..3.0) is the
 * user-facing multiplier, persisted by the host activity. Per-report clamping to
 * the descriptor's -127..127 range happens in MouseState — at touch-event rates a
 * single event's delta never legitimately exceeds it.
 */
package com.productforge.phonecontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.productforge.phonecontroller.hid.MouseButton
import kotlin.math.abs
import kotlin.math.roundToInt

class TouchpadView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onMove(dx: Int, dy: Int)
        fun onScroll(notches: Int)
        fun onTap(button: MouseButton)
    }

    /** Pointer-speed multiplier (0.5..3.0), set by the sensitivity slider. */
    var sensitivity: Float = 1.0f

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var movedBeyondSlop = false
    private var remX = 0f
    private var remY = 0f
    private var scrollRemDp = 0f

    init {
        // A visible, slightly recessed surface so the trackpad area reads as one.
        setBackgroundColor(0x14808080)
    }

    private fun avgX(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getX(i)
        return sum / e.pointerCount
    }

    private fun avgY(e: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until e.pointerCount) sum += e.getY(i)
        return sum / e.pointerCount
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                downTime = e.eventTime
                maxPointers = 1
                movedBeyondSlop = false
                remX = 0f
                remY = 0f
                scrollRemDp = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Finger count changed: re-anchor on the new centroid so the pointer
                // doesn't jump, and remember the peak count for tap classification.
                // (On POINTER_UP the departing finger is still in the event's average;
                // the next MOVE re-anchors again — a one-event nudge at worst.)
                if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    maxPointers = maxOf(maxPointers, e.pointerCount)
                }
                lastX = avgX(e)
                lastY = avgY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                val ax = avgX(e)
                val ay = avgY(e)
                val dxPx = ax - lastX
                val dyPx = ay - lastY
                lastX = ax
                lastY = ay
                if (abs(dxPx) > touchSlop || abs(dyPx) > touchSlop) movedBeyondSlop = true

                if (e.pointerCount >= 2) {
                    // Two-finger drag = natural scroll: content follows the fingers.
                    scrollRemDp += dyPx / density
                    val notches = (scrollRemDp / SCROLL_DP_PER_NOTCH).toInt()
                    if (notches != 0) {
                        scrollRemDp -= notches * SCROLL_DP_PER_NOTCH
                        listener.onScroll(notches)
                    }
                } else {
                    val gain = GAIN * sensitivity / density
                    remX += dxPx * gain
                    remY += dyPx * gain
                    val outX = remX.roundToInt()
                    val outY = remY.roundToInt()
                    if (outX != 0 || outY != 0) {
                        remX -= outX
                        remY -= outY
                        listener.onMove(outX, outY)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downTime < TAP_MS
                if (quick && !movedBeyondSlop) {
                    listener.onTap(if (maxPointers >= 2) MouseButton.RIGHT else MouseButton.LEFT)
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { /* nothing held — motion is stateless */ }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        /** Base pointer gain in counts per dp of finger travel (before sensitivity). */
        const val GAIN = 2.2f

        /** Finger travel per wheel notch, in dp. */
        const val SCROLL_DP_PER_NOTCH = 18f

        /** Max press duration that still counts as a tap. */
        const val TAP_MS = 250L
    }
}
