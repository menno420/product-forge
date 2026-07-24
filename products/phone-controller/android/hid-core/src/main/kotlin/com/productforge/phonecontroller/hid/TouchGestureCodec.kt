/*
 * TouchGestureCodec — compact, versioned text form for a TouchGesture (Slice 13).
 *
 * Format (version 1):
 *   g1|<stroke>|<stroke>|…
 *   stroke = startMs:durationMs:x,y;x,y;…      (x/y = percent, 4 decimals)
 *
 * Example — a 300 ms diagonal swipe: "g1|0:300:0.1000,0.9000;0.8000,0.2000"
 *
 * Decode is FAIL-SOFT (null on anything malformed) and bounded (stroke/point
 * caps) so a corrupt store or hostile paste can never crash or balloon memory.
 * Pure JVM — round-trip pinned in TouchGestureCodecTest. This is also the future
 * share format for recorded gestures (same philosophy as the layout envelope).
 */
package com.productforge.phonecontroller.hid

object TouchGestureCodec {

    const val PREFIX = "g1"
    const val MAX_STROKES = 8
    const val MAX_POINTS = 256

    fun encode(gesture: TouchGesture): String =
        buildString {
            append(PREFIX)
            for (stroke in gesture.strokes.take(MAX_STROKES)) {
                append('|')
                append(stroke.startMs).append(':').append(stroke.durationMs).append(':')
                stroke.points.take(MAX_POINTS).forEachIndexed { i, p ->
                    if (i > 0) append(';')
                    append(fmt(p.xPct)).append(',').append(fmt(p.yPct))
                }
            }
        }

    /** Null on malformed/oversized/unknown-version input — callers skip, never crash. */
    fun decode(text: String): TouchGesture? {
        val parts = text.trim().split('|')
        if (parts.isEmpty() || parts[0] != PREFIX) return null
        val strokeParts = parts.drop(1)
        if (strokeParts.isEmpty() || strokeParts.size > MAX_STROKES) return null
        val strokes = mutableListOf<GestureStroke>()
        for (raw in strokeParts) {
            val seg = raw.split(':')
            if (seg.size != 3) return null
            val start = seg[0].toLongOrNull() ?: return null
            val duration = seg[1].toLongOrNull() ?: return null
            if (start < 0 || duration <= 0) return null
            val pointsRaw = seg[2].split(';')
            if (pointsRaw.isEmpty() || pointsRaw.size > MAX_POINTS) return null
            val points = mutableListOf<GesturePoint>()
            for (pr in pointsRaw) {
                val xy = pr.split(',')
                if (xy.size != 2) return null
                val x = xy[0].toFloatOrNull() ?: return null
                val y = xy[1].toFloatOrNull() ?: return null
                if (x.isNaN() || y.isNaN()) return null
                points.add(GesturePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
            }
            strokes.add(GestureStroke(points, start, GestureGeometry.clampDuration(duration)))
        }
        return TouchGesture(strokes)
    }

    /** Fixed 4-decimal formatting, locale-independent (no String.format locale trap). */
    private fun fmt(v: Float): String {
        val clamped = v.coerceIn(0f, 1f)
        val scaled = (clamped * 10000f + 0.5f).toInt()
        val whole = scaled / 10000
        val frac = scaled % 10000
        return "$whole." + frac.toString().padStart(4, '0')
    }
}
