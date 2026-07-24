/*
 * TapAccessibilityService — dispatches synthetic touches into whatever app is in
 * front (Slice 12, local-play overlay).
 *
 * This is the ONLY sanctioned no-root way for one app to inject taps/swipes into
 * another (Android forbids cross-app touch injection otherwise). We use it purely
 * to WRITE input — the service config sets canRetrieveWindowContent=false, so it
 * never reads screen content: it cannot see what you type or what's on screen.
 *
 * The overlay reaches the live instance through a weak reference; a gesture arrives
 * as a portable [TouchGesture] (percent coords) and is resolved to device pixels
 * via the pure [GestureGeometry] against the real display metrics, then handed to
 * dispatchGesture.
 */
package com.productforge.phonecontroller.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import com.productforge.phonecontroller.hid.GestureGeometry
import com.productforge.phonecontroller.hid.TouchGesture
import java.lang.ref.WeakReference

class TapAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = WeakReference(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = WeakReference(null)
        super.onDestroy()
    }

    // We only inject; nothing to do with incoming events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Build a GestureDescription from the portable gesture and dispatch it. */
    private fun dispatch(gesture: TouchGesture) {
        val metrics: DisplayMetrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val builder = GestureDescription.Builder()
        var added = false
        for (stroke in GestureGeometry.pixelStrokes(gesture, w, h)) {
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (p in stroke.points.drop(1)) path.lineTo(p.x.toFloat(), p.y.toFloat())
            runCatching {
                builder.addStroke(
                    GestureDescription.StrokeDescription(path, stroke.startMs, stroke.durationMs),
                )
                added = true
            }
        }
        if (!added) return
        runCatching { dispatchGesture(builder.build(), null, null) }
    }

    companion object {
        private var instance: WeakReference<TapAccessibilityService> = WeakReference(null)

        /** True when the service is enabled + connected (the overlay checks before firing). */
        val isConnected: Boolean get() = instance.get() != null

        /** Dispatch a gesture if the service is live; returns false if it isn't. */
        fun play(gesture: TouchGesture): Boolean {
            val svc = instance.get() ?: return false
            svc.dispatch(gesture)
            return true
        }
    }
}
