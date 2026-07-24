/*
 * OverlayPlayService — floats a layout's buttons over other apps and turns each
 * press into a synthetic tap/hold on the app underneath (Slice 12).
 *
 * Architecture note (why one window PER BUTTON, not one big window): a single
 * full-screen overlay window would swallow every touch, so the game below could
 * never be touched directly. Instead each button is its own small
 * TYPE_APPLICATION_OVERLAY window sized to the button — the gaps between them are
 * covered by no window at all, so touches there fall straight through to the game.
 * This is the standard overlay-controller architecture (Octopus/Mantis).
 *
 * A press dispatches TouchGesture.tap at the button's centre through
 * TapAccessibilityService; a hold (finger down > HOLD_MS) dispatches a hold gesture
 * for as long as it's held. A separate small draggable handle window stops the
 * overlay. Foreground service (specialUse) + a persistent notification keep it
 * alive and honest while another app is in front.
 */
package com.productforge.phonecontroller.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.productforge.phonecontroller.R
import com.productforge.phonecontroller.hid.GestureGeometry
import com.productforge.phonecontroller.hid.TouchGesture
import com.productforge.phonecontroller.layout.CustomLayout
import com.productforge.phonecontroller.layout.LayoutStore
import com.productforge.phonecontroller.layout.PadActionType
import com.productforge.phonecontroller.layout.PadButtonSpec
import com.productforge.phonecontroller.ui.ButtonStyler

class OverlayPlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val buttonViews = mutableListOf<View>()
    private var handleView: View? = null
    private var gestureStore: GestureStore? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotice()
        val layoutId = intent?.getStringExtra(EXTRA_LAYOUT_ID)
        val layout = layoutId?.let { LayoutStore(prefs()).byId(it) }
        if (layout == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        gestureStore = GestureStore(prefs())
        showButtons(layout)
        showHandle()
        return START_STICKY
    }

    private fun prefs() = getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
    ).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showButtons(layout: CustomLayout) {
        val metrics = resources.displayMetrics
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels
        for (spec in layout.buttons) {
            val bw = (spec.wPct * sw).toInt().coerceAtLeast(dp(36))
            val bh = (spec.hPct * sh).toInt().coerceAtLeast(dp(36))
            val bx = (spec.xPct * sw).toInt()
            val by = (spec.yPct * sh).toInt()
            val button = Button(this).apply {
                text = spec.label
                isAllCaps = false
                ButtonStyler.apply(this, spec, bh)
                setOnTouchListener(TapDispatch(spec))
            }
            val lp = WindowManager.LayoutParams(
                bw, bh, bx, by, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            runCatching {
                windowManager.addView(button, lp)
                buttonViews.add(button)
            }
        }
    }

    /**
     * Turns finger-down/up on an overlay button into a tap (or hold) at the button's
     * saved centre. Percent centre of the button == percent point on screen, since
     * the button window sits at the layout's percent position.
     */
    private inner class TapDispatch(private val spec: PadButtonSpec) : View.OnTouchListener {
        private var downAt = 0L

        // Resolved once at bind time: a GESTURE button's recorded path (or null).
        private val boundGesture: TouchGesture? =
            if (spec.action.type == PadActionType.GESTURE) {
                gestureStore?.byId(spec.action.code)?.gesture()
            } else {
                null
            }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val cx = spec.xPct + spec.wPct / 2f
            val cy = spec.yPct + spec.hPct / 2f
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downAt = SystemClock.uptimeMillis()
                    v.isPressed = true
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    val heldMs = SystemClock.uptimeMillis() - downAt
                    val gesture = when {
                        // A GESTURE button replays its recorded path verbatim (its
                        // points are absolute screen percents from the recording).
                        spec.action.type == PadActionType.GESTURE ->
                            boundGesture ?: TouchGesture.tap(cx, cy)
                        heldMs >= HOLD_MS ->
                            TouchGesture.hold(cx, cy, heldMs.coerceAtMost(GestureGeometry.MAX_DURATION_MS))
                        else -> TouchGesture.tap(cx, cy)
                    }
                    TapAccessibilityService.play(gesture)
                    v.performClick()
                    return true
                }
            }
            return false
        }
    }

    private fun showHandle() {
        val handle = TextView(this).apply {
            text = getString(R.string.overlay_stop_handle)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCCB00020.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(8), dp(8), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // Drag to move the handle; a tap (no drag) stops the overlay.
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var origX = 0
            private var origY = 0
            private var dragged = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY
                        origX = lp.x; origY = lp.y; dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startX).toInt()
                        val dy = (event.rawY - startY).toInt()
                        if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) dragged = true
                        lp.x = origX + dx; lp.y = origY + dy
                        runCatching { windowManager.updateViewLayout(v, lp) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) stopSelf()
                        v.performClick()
                        return true
                    }
                }
                return false
            }
        })
        runCatching {
            windowManager.addView(handle, lp)
            handleView = handle
        }
    }

    private fun startForegroundNotice() {
        val channelId = "overlay_play"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, OverlayPlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIcon = android.graphics.drawable.Icon.createWithResource(
            this, android.R.drawable.ic_menu_close_clear_cancel,
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .addAction(Notification.Action.Builder(stopIcon, getString(R.string.overlay_stop), stopPi).build())
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        buttonViews.forEach { runCatching { windowManager.removeView(it) } }
        buttonViews.clear()
        handleView?.let { runCatching { windowManager.removeView(it) } }
        handleView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LAYOUT_ID = "layout_id"
        const val ACTION_STOP = "com.productforge.phonecontroller.OVERLAY_STOP"
        private const val NOTIF_ID = 71
        private const val HOLD_MS = 250L
    }
}
