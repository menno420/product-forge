/*
 * ButtonStyler — the visual layer for custom-layout buttons (Slice 7).
 *
 * Renders a PadButtonSpec's color/shape/text choices onto a plain Button:
 * programmatic GradientDrawable backgrounds (no XML resources), a darkened
 * pressed state via StateListDrawable so colored buttons still visibly react,
 * and automatic black/white text by background luminance. A null color keeps the
 * platform-default button look untouched.
 */
package com.productforge.phonecontroller.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.Button
import com.productforge.phonecontroller.layout.PadButtonSpec
import com.productforge.phonecontroller.layout.PadShape

object ButtonStyler {

    /** Swatch palette (label → opaque ARGB). "Default" is represented by null upstream. */
    val PALETTE: List<Pair<String, Int>> = listOf(
        "Red" to 0xFFE53935.toInt(),
        "Orange" to 0xFFFB8C00.toInt(),
        "Amber" to 0xFFFFB300.toInt(),
        "Yellow" to 0xFFFDD835.toInt(),
        "Lime" to 0xFFC0CA33.toInt(),
        "Green" to 0xFF43A047.toInt(),
        "Teal" to 0xFF00897B.toInt(),
        "Cyan" to 0xFF00ACC1.toInt(),
        "Blue" to 0xFF1E88E5.toInt(),
        "Indigo" to 0xFF3949AB.toInt(),
        "Violet" to 0xFF8E24AA.toInt(),
        "Pink" to 0xFFD81B60.toInt(),
        "Brown" to 0xFF6D4C41.toInt(),
        "Grey" to 0xFF757575.toInt(),
        "Dark" to 0xFF37474F.toInt(),
        "White" to 0xFFFAFAFA.toInt(),
    )

    /** Background-opacity presets (label → alpha 0..255) applied onto the color. */
    val OPACITY: List<Pair<String, Int>> = listOf(
        "Solid" to 0xFF,
        "Soft" to 0xB3,
        "Ghost" to 0x66,
    )

    /** Pad-background presets for a whole layout (label → ARGB; null = default). */
    val PAD_BACKGROUNDS: List<Pair<String, Int?>> = listOf(
        "Default" to null,
        "Black (OLED)" to 0xFF000000.toInt(),
        "Dark grey" to 0xFF121212.toInt(),
        "Deep blue" to 0xFF0D1B2A.toInt(),
        "Deep green" to 0xFF0B1F16.toInt(),
    )

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun darken(color: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * 0.6f).toInt()
        val g = (Color.green(color) * 0.6f).toInt()
        val b = (Color.blue(color) * 0.6f).toInt()
        return Color.argb(a, r, g, b)
    }

    /** Black or white text, by the background's relative luminance (alpha-blind). */
    private fun textColorFor(background: Int): Int {
        val lum = 0.299f * Color.red(background) +
            0.587f * Color.green(background) +
            0.114f * Color.blue(background)
        return if (lum > 150f) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun shapeDrawable(spec: PadButtonSpec, color: Int, heightPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            when (spec.shape) {
                PadShape.CIRCLE -> {
                    shape = GradientDrawable.OVAL
                }
                PadShape.PILL -> {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = heightPx / 2f
                }
                PadShape.ROUNDED -> {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = heightPx * 0.18f
                }
                PadShape.SQUARE -> {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 0f
                }
            }
        }

    /**
     * Apply the spec's visual choices. [heightPx] shapes the corner radii — pass the
     * button's laid-out height (0 falls back to a sane radius).
     */
    fun apply(button: Button, spec: PadButtonSpec, heightPx: Int) {
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.textSizeSp.toFloat())
        val color = spec.colorArgb ?: return // default platform look
        val h = if (heightPx > 0) heightPx else 96
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shapeDrawable(spec, darken(color), h))
            addState(intArrayOf(), shapeDrawable(spec, color, h))
        }
        button.background = states
        button.setTextColor(textColorFor(color))
    }
}
