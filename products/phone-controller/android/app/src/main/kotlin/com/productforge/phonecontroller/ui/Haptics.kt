/*
 * Haptics — one global switch for the press-tick vibration (Slice 6).
 *
 * Uses View.performHapticFeedback(VIRTUAL_KEY): no VIBRATE permission, respects the
 * system's touch-feedback setting, and stays subtle. `enabled` is loaded from prefs
 * by MainActivity at startup and flipped by the Settings dialog.
 */
package com.productforge.phonecontroller.ui

import android.view.HapticFeedbackConstants
import android.view.View

object Haptics {

    @Volatile
    var enabled: Boolean = true

    fun tick(view: View) {
        if (enabled) {
            view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }
}
