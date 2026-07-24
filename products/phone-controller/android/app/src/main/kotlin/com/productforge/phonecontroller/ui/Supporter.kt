/*
 * Supporter — the fair-monetization flag (Slice 9 groundwork).
 *
 * The standing product decision (owner, 2026-07-24, after competitor research):
 * EVERYTHING functional is free forever — every key, pad, editor feature. No ads,
 * no subscriptions. A one-time ≤€1 "Supporter Pack" will unlock cosmetics only
 * (style-pack fills, in ButtonStyler) once the app has a Play Store listing with
 * Play Billing. Until then this flag is driven by an honest "supporter preview"
 * toggle in About — nothing is dark-patterned, nothing functional checks it.
 *
 * NEVER gate an input, a pad, a layout feature, or data import/export on this.
 */
package com.productforge.phonecontroller.ui

object Supporter {
    /** True when the style pack renders (preview toggle now; Play Billing later). */
    @Volatile
    var unlocked: Boolean = false
}
