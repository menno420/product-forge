/*
 * GestureGeometryTest — pins the pure touch-gesture math (Slice 12).
 *
 * The Android AccessibilityService trusts these outputs to place synthetic touches;
 * the conversion (percent→pixel, edge clamping, duration bounds) must be exact and
 * never emit an out-of-bounds coordinate.
 */
package com.productforge.phonecontroller.hid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureGeometryTest {

    @Test
    fun `center percent maps near the middle pixel`() {
        assertEquals(499, GestureGeometry.toPixel(0.5f, 1000))
        assertEquals(1079, GestureGeometry.toPixel(0.5f, 2160))
    }

    @Test
    fun `edges clamp inside the display bounds`() {
        assertEquals(0, GestureGeometry.toPixel(0f, 1080))
        assertEquals(1079, GestureGeometry.toPixel(1f, 1080))
        // Out-of-range percents never escape the screen.
        assertEquals(0, GestureGeometry.toPixel(-0.5f, 1080))
        assertEquals(1079, GestureGeometry.toPixel(1.5f, 1080))
    }

    @Test
    fun `zero or negative size never divides or throws`() {
        assertEquals(0, GestureGeometry.toPixel(0.5f, 0))
        assertEquals(0, GestureGeometry.toPixel(0.5f, -10))
    }

    @Test
    fun `duration clamps to sane bounds`() {
        assertEquals(GestureGeometry.MIN_DURATION_MS, GestureGeometry.clampDuration(0L))
        assertEquals(GestureGeometry.MIN_DURATION_MS, GestureGeometry.clampDuration(-5L))
        assertEquals(GestureGeometry.MAX_DURATION_MS, GestureGeometry.clampDuration(999_999L))
        assertEquals(500L, GestureGeometry.clampDuration(500L))
    }

    @Test
    fun `tap builds a single-point short stroke`() {
        val g = TouchGesture.tap(0.25f, 0.75f)
        assertEquals(1, g.strokes.size)
        assertEquals(1, g.strokes[0].points.size)
        assertEquals(GestureGeometry.DEFAULT_TAP_MS, g.strokes[0].durationMs)
    }

    @Test
    fun `swipe resolves both endpoints to pixels`() {
        val g = TouchGesture.swipe(0f, 0f, 1f, 1f, 300L)
        val stroke = GestureGeometry.pixelStroke(g.strokes[0], 800, 600)
        assertEquals(GestureGeometry.PixelPoint(0, 0), stroke.points.first())
        assertEquals(GestureGeometry.PixelPoint(799, 599), stroke.points.last())
        assertEquals(300L, stroke.durationMs)
    }

    @Test
    fun `every resolved pixel stays within bounds for arbitrary percents`() {
        val pts = listOf(
            GesturePoint(-1f, 2f), GesturePoint(0.33f, 0.66f), GesturePoint(1.2f, -0.1f),
        )
        val stroke = GestureGeometry.pixelStroke(GestureStroke(pts, 0L, 100L), 1080, 2400)
        for (p in stroke.points) {
            assertTrue(p.x in 0..1079, "x ${p.x} out of bounds")
            assertTrue(p.y in 0..2399, "y ${p.y} out of bounds")
        }
    }

    @Test
    fun `total duration accounts for start offset`() {
        val g = TouchGesture(
            listOf(
                GestureStroke(listOf(GesturePoint(0.1f, 0.1f)), 0L, 100L),
                GestureStroke(listOf(GesturePoint(0.2f, 0.2f)), 200L, 150L),
            ),
        )
        assertEquals(350L, GestureGeometry.totalDurationMs(g))
    }
}
