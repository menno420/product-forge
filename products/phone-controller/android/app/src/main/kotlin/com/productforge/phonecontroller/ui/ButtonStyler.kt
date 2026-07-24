/*
 * ButtonStyler — the visual layer for buttons (Slice 7, extended in Slice 8).
 *
 * Slice 7: renders a PadButtonSpec's color/shape/text choices onto a plain Button —
 * programmatic GradientDrawable backgrounds (no XML resources), a pressed-state
 * variant via StateListDrawable, automatic black/white text by background luminance.
 *
 * Slice 8 (dark controller theme): every button is flat-styled — a null spec color
 * now means the shared dark SURFACE, not the platform widget look, so built-in pads
 * and custom layouts share one visual language. Pressed states LIGHTEN dark bases
 * and DARKEN bright ones (both directions stay visible). Built-in pad buttons get
 * an inset so touching grid cells keep gutters; labels auto-size so long text
 * (SELECT) shrinks instead of wrapping.
 */
package com.productforge.phonecontroller.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.widget.Button
import com.productforge.phonecontroller.layout.PadButtonSpec
import com.productforge.phonecontroller.layout.PadFx
import com.productforge.phonecontroller.layout.PadShape

object ButtonStyler {

    /** App-wide default background (dark slate); the user override lives in prefs. */
    val DEFAULT_APP_BG = 0xFF15181E.toInt()

    /** The shared dark button surface ("default" button color in the dark theme). */
    val SURFACE = 0xFF2A313B.toInt()

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

    /** Background presets for layouts AND the whole app (label → ARGB; null = default). */
    val PAD_BACKGROUNDS: List<Pair<String, Int?>> = listOf(
        "Default" to null,
        "Black (OLED)" to 0xFF000000.toInt(),
        "Dark grey" to 0xFF121212.toInt(),
        "Deep blue" to 0xFF0D1B2A.toInt(),
        "Deep green" to 0xFF0B1F16.toInt(),
        "Deep purple" to 0xFF190F2E.toInt(),
        "Charcoal" to 0xFF262B31.toInt(),
    )

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun luminance(color: Int): Float =
        0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)

    private fun darken(color: Int): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * 0.6f).toInt()
        val g = (Color.green(color) * 0.6f).toInt()
        val b = (Color.blue(color) * 0.6f).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun lighten(color: Int): Int {
        val a = Color.alpha(color)
        fun ch(c: Int): Int = (c + (255 - c) * 0.30f).toInt().coerceAtMost(255)
        return Color.argb(a, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    /** The pressed variant must stay visible in BOTH directions: lighten dark bases. */
    private fun pressedVariant(color: Int): Int =
        if (luminance(color) < 70f) lighten(color) else darken(color)

    /** Black or white text, by the background's relative luminance (alpha-blind). */
    private fun textColorFor(background: Int): Int =
        if (luminance(background) > 150f) 0xFF1A1A1A.toInt() else 0xFFECEFF1.toInt()

    /** Configure shape/corners on a GradientDrawable per the spec (shared helper). */
    private fun applyShapeGeometry(gd: GradientDrawable, spec: PadButtonSpec, heightPx: Int) {
        when (spec.shape) {
            PadShape.CIRCLE -> {
                gd.shape = GradientDrawable.OVAL
            }
            PadShape.PILL -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = heightPx / 2f
            }
            PadShape.ROUNDED -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = heightPx * 0.18f
            }
            PadShape.SQUARE -> {
                gd.shape = GradientDrawable.RECTANGLE
                gd.cornerRadius = 0f
            }
        }
    }

    private fun shapeDrawable(spec: PadButtonSpec, color: Int, heightPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            applyShapeGeometry(this, spec, heightPx)
        }

    /** Supporter style pack: vertical light→dark gradient of the chosen color. */
    private fun gradientDrawable(spec: PadButtonSpec, color: Int, heightPx: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(lighten(color), color, darken(color)),
        ).apply { applyShapeGeometry(this, spec, heightPx) }

    /**
     * Apply a custom-layout spec's visual choices. [heightPx] shapes the corner radii —
     * pass the button's laid-out height (0 falls back to a sane radius). A null spec
     * color renders the shared dark SURFACE so custom pads match the built-in look.
     * Style-pack fills (GRADIENT/GLOW) render only while [Supporter.unlocked]; locked
     * specs fall back to FLAT — visibly complete, never broken.
     */
    fun apply(button: Button, spec: PadButtonSpec, heightPx: Int) {
        button.maxLines = 2
        button.setAutoSizeTextTypeUniformWithConfiguration(
            8, maxOf(9, spec.textSizeSp), 1, TypedValue.COMPLEX_UNIT_SP,
        )
        val color = spec.colorArgb ?: SURFACE
        val h = if (heightPx > 0) heightPx else 96
        val fx = if (Supporter.unlocked) spec.fx else PadFx.FLAT
        val strokePx = (2f * button.resources.displayMetrics.density).toInt()
        val normal = when (fx) {
            PadFx.GRADIENT -> gradientDrawable(spec, color, h)
            PadFx.GLOW -> shapeDrawable(spec, color, h).apply {
                setStroke(strokePx, withAlpha(lighten(color), 0xB3))
            }
            PadFx.FLAT -> shapeDrawable(spec, color, h)
        }
        val pressed = when (fx) {
            PadFx.GRADIENT -> gradientDrawable(spec, pressedVariant(color), h)
            PadFx.GLOW -> shapeDrawable(spec, lighten(color), h).apply {
                setStroke(strokePx * 2, 0xE6FFFFFF.toInt())
            }
            PadFx.FLAT -> shapeDrawable(spec, pressedVariant(color), h)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        button.background = states
        button.setTextColor(textColorFor(color))
        button.stateListAnimator = null
    }

    /**
     * Flat dark-theme styling for built-in pad buttons and chrome chips: rounded
     * rect + pressed variant + disabled fade, inset so adjacent grid cells keep a
     * gutter (custom GradientDrawables have no built-in inset, unlike the platform
     * button background).
     */
    fun flatStyle(button: Button, color: Int, cornerDp: Float = 10f, insetDp: Float = 3f) {
        val density = button.resources.displayMetrics.density
        val radius = cornerDp * density
        fun drawableOf(c: Int) = GradientDrawable().apply {
            setColor(c)
            cornerRadius = radius
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), drawableOf(pressedVariant(color)))
            addState(intArrayOf(android.R.attr.state_enabled), drawableOf(color))
            addState(intArrayOf(), drawableOf(withAlpha(color, 0x55)))
        }
        val inset = (insetDp * density).toInt()
        button.background = InsetDrawable(states, inset)
        button.setTextColor(textColorFor(color))
        button.stateListAnimator = null
    }
}
