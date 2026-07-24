/*
 * TouchGesture + GestureGeometry — the pure, portable model for a synthetic touch
 * (Slice 12, the local-play overlay foundation).
 *
 * A gesture is one or more STROKES; a stroke is a path of points (each in
 * PERCENT of the screen, 0..1, matching the app's percent-based layout model) with
 * a start delay and a duration. A single-point stroke is a tap/hold; a two-point
 * stroke is a swipe; many points trace an arbitrary path (what the Slice-13
 * recorder will capture). Percent coordinates keep a gesture screen-size- and
 * rotation-independent — the Android side multiplies by the live display metrics
 * at dispatch time.
 *
 * Pure JVM on purpose: the coordinate/timing math lives in [GestureGeometry] and is
 * unit-tested in CI's SDK-free lane (GestureGeometryTest); the Android
 * AccessibilityService only feeds these pixel points into a GestureDescription.
 */
package com.productforge.phonecontroller.hid

/** A point in screen-percent space (0f..1f on each axis). */
data class GesturePoint(val xPct: Float, val yPct: Float)

/** One continuous stroke: a path plus its timing (all times in milliseconds). */
data class GestureStroke(
    val points: List<GesturePoint>,
    val startMs: Long = 0L,
    val durationMs: Long = GestureGeometry.DEFAULT_TAP_MS,
) {
    init {
        require(points.isNotEmpty()) { "a stroke needs at least one point" }
    }
}

/** A synthetic touch: one or more strokes (multi-stroke = multi-finger, later). */
data class TouchGesture(val strokes: List<GestureStroke>) {

    init {
        require(strokes.isNotEmpty()) { "a gesture needs at least one stroke" }
    }

    companion object {
        /** A tap at a point (short single-point stroke). */
        fun tap(xPct: Float, yPct: Float): TouchGesture =
            TouchGesture(listOf(GestureStroke(listOf(GesturePoint(xPct, yPct)), 0L, GestureGeometry.DEFAULT_TAP_MS)))

        /** A press-and-hold at a point for [ms]. */
        fun hold(xPct: Float, yPct: Float, ms: Long): TouchGesture =
            TouchGesture(listOf(GestureStroke(listOf(GesturePoint(xPct, yPct)), 0L, ms)))

        /** A straight swipe from one point to another over [ms]. */
        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, ms: Long): TouchGesture =
            TouchGesture(
                listOf(
                    GestureStroke(
                        listOf(GesturePoint(fromX, fromY), GesturePoint(toX, toY)),
                        0L, ms,
                    ),
                ),
            )
    }
}

/**
 * Percent→pixel conversion and timing rules — the durable, testable core. The
 * Android service builds a GestureDescription from [pixelStroke] outputs; nothing
 * here touches an Android type.
 */
object GestureGeometry {

    const val DEFAULT_TAP_MS = 60L
    const val MIN_DURATION_MS = 1L
    const val MAX_DURATION_MS = 60_000L

    /** A pixel point after conversion (integer device coordinates). */
    data class PixelPoint(val x: Int, val y: Int)

    /** A stroke resolved to device pixels + clamped timing, ready for dispatch. */
    data class PixelStroke(
        val points: List<PixelPoint>,
        val startMs: Long,
        val durationMs: Long,
    )

    /** Clamp a percent coordinate into [0,1] then scale to [0, size-1] pixels. */
    fun toPixel(pct: Float, sizePx: Int): Int {
        if (sizePx <= 0) return 0
        val clamped = pct.coerceIn(0f, 1f)
        return (clamped * (sizePx - 1)).toInt().coerceIn(0, sizePx - 1)
    }

    fun clampDuration(ms: Long): Long = ms.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    fun clampStart(ms: Long): Long = ms.coerceAtLeast(0L)

    /** Resolve one stroke to device pixels for the given display size. */
    fun pixelStroke(stroke: GestureStroke, widthPx: Int, heightPx: Int): PixelStroke =
        PixelStroke(
            points = stroke.points.map { PixelPoint(toPixel(it.xPct, widthPx), toPixel(it.yPct, heightPx)) },
            startMs = clampStart(stroke.startMs),
            durationMs = clampDuration(stroke.durationMs),
        )

    /** Resolve every stroke of a gesture. */
    fun pixelStrokes(gesture: TouchGesture, widthPx: Int, heightPx: Int): List<PixelStroke> =
        gesture.strokes.map { pixelStroke(it, widthPx, heightPx) }

    /** Total wall-clock span of a gesture (max start+duration across strokes). */
    fun totalDurationMs(gesture: TouchGesture): Long =
        gesture.strokes.maxOf { clampStart(it.startMs) + clampDuration(it.durationMs) }
}
