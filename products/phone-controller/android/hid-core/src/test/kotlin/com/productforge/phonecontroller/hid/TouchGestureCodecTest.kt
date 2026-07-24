/*
 * TouchGestureCodecTest — pins the recorded-gesture text format (Slice 13).
 *
 * The store persists gestures in this form and the overlay replays what decodes;
 * a round-trip must be exact to the 4-decimal grid, and hostile/corrupt input
 * must decode to null — never crash, never mega-allocate.
 */
package com.productforge.phonecontroller.hid

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TouchGestureCodecTest {

    @Test
    fun `swipe round-trips exactly on the 4-decimal grid`() {
        val original = TouchGesture.swipe(0.1234f, 0.9876f, 0.5f, 0.25f, 300L)
        val decoded = TouchGestureCodec.decode(TouchGestureCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(1, decoded.strokes.size)
        val a = original.strokes[0].points
        val b = decoded.strokes[0].points
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertTrue(abs(a[i].xPct - b[i].xPct) < 0.0001f)
            assertTrue(abs(a[i].yPct - b[i].yPct) < 0.0001f)
        }
        assertEquals(300L, decoded.strokes[0].durationMs)
    }

    @Test
    fun `multi-point path and timing survive`() {
        val stroke = GestureStroke(
            points = (0..20).map { GesturePoint(it / 20f, 1f - it / 20f) },
            startMs = 40L,
            durationMs = 750L,
        )
        val decoded = TouchGestureCodec.decode(TouchGestureCodec.encode(TouchGesture(listOf(stroke))))
        assertNotNull(decoded)
        assertEquals(21, decoded.strokes[0].points.size)
        assertEquals(40L, decoded.strokes[0].startMs)
        assertEquals(750L, decoded.strokes[0].durationMs)
    }

    @Test
    fun `tap encodes with prefix and decodes`() {
        val text = TouchGestureCodec.encode(TouchGesture.tap(0.5f, 0.5f))
        assertTrue(text.startsWith("g1|"))
        assertNotNull(TouchGestureCodec.decode(text))
    }

    @Test
    fun `malformed inputs decode to null`() {
        assertNull(TouchGestureCodec.decode(""))
        assertNull(TouchGestureCodec.decode("nonsense"))
        assertNull(TouchGestureCodec.decode("g2|0:100:0.5,0.5")) // unknown version
        assertNull(TouchGestureCodec.decode("g1|")) // no stroke
        assertNull(TouchGestureCodec.decode("g1|0:100")) // missing points
        assertNull(TouchGestureCodec.decode("g1|0:100:0.5")) // bad point
        assertNull(TouchGestureCodec.decode("g1|0:100:a,b")) // NaN-ish
        assertNull(TouchGestureCodec.decode("g1|-5:100:0.5,0.5")) // negative start
        assertNull(TouchGestureCodec.decode("g1|0:0:0.5,0.5")) // zero duration
    }

    @Test
    fun `out-of-range percents clamp on decode`() {
        val decoded = TouchGestureCodec.decode("g1|0:100:-0.5,1.7")
        assertNotNull(decoded)
        assertEquals(0f, decoded.strokes[0].points[0].xPct)
        assertEquals(1f, decoded.strokes[0].points[0].yPct)
    }

    @Test
    fun `oversized stroke or point counts are rejected`() {
        val manyStrokes = "g1|" + List(9) { "0:100:0.5,0.5" }.joinToString("|")
        assertNull(TouchGestureCodec.decode(manyStrokes))
        val manyPoints = "g1|0:100:" + List(257) { "0.5,0.5" }.joinToString(";")
        assertNull(TouchGestureCodec.decode(manyPoints))
    }
}
