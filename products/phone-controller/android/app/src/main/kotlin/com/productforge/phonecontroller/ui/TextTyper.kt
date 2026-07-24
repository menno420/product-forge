/*
 * TextTyper — replays a string as paced HID keystrokes (Slice 9, send-text).
 *
 * The user types (or DICTATES — the phone IME's mic button) into a local dialog;
 * this walks the text on a background thread and presses/releases each character's
 * stroke via the transport's @Synchronized key/modifier APIs. Pacing keeps hosts
 * happy: every report pair is spaced so slow BT links and picky receivers don't
 * drop keys. Unmappable characters (non-ASCII — HID sends POSITIONS, not glyphs)
 * are counted and skipped, never crashed on.
 *
 * One job at a time; cancel() is called on disconnect/destroy and before a new
 * job. Cancellation releases any in-flight shift so nothing stays held.
 */
package com.productforge.phonecontroller.ui

import android.os.Handler
import android.os.Looper
import com.productforge.phonecontroller.hid.KeyChars
import com.productforge.phonecontroller.hid.KeyUsage

class TextTyper(
    private val key: (usage: Int, down: Boolean) -> Unit,
    private val modifier: (mask: Int, down: Boolean) -> Unit,
    /** Progress callback, invoked on the MAIN thread: (typed, total, skipped, done). */
    private val onProgress: (typed: Int, total: Int, skipped: Int, done: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false
    @Volatile private var worker: Thread? = null

    val busy: Boolean get() = worker?.isAlive == true

    /** Start typing [text]; cancels any job already running. */
    fun type(text: String) {
        cancel()
        cancelled = false
        val t = Thread {
            val total = text.length
            var typed = 0
            var skipped = 0
            for ((index, c) in text.withIndex()) {
                if (cancelled) break
                val stroke = KeyChars.strokeFor(c)
                if (stroke == null) {
                    skipped++
                } else {
                    if (stroke.shift) modifier(KeyUsage.MOD_LEFT_SHIFT, true)
                    key(stroke.usage, true)
                    pause(PRESS_MS)
                    key(stroke.usage, false)
                    if (stroke.shift) modifier(KeyUsage.MOD_LEFT_SHIFT, false)
                    pause(GAP_MS)
                    typed++
                }
                if (index % PROGRESS_EVERY == 0 || index == total - 1) {
                    post(typed, total, skipped, done = false)
                }
            }
            // Belt-and-braces: never leave shift held, even on cancellation mid-char.
            modifier(KeyUsage.MOD_LEFT_SHIFT, false)
            post(typed, total, skipped, done = true)
        }
        worker = t
        t.start()
    }

    fun cancel() {
        cancelled = true
        worker?.let { runCatching { it.join(JOIN_MS) } }
        worker = null
    }

    private fun post(typed: Int, total: Int, skipped: Int, done: Boolean) {
        main.post { onProgress(typed, total, skipped, done) }
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            cancelled = true
        }
    }

    private companion object {
        /** Hold time per key press — long enough for any host to latch the report. */
        const val PRESS_MS = 14L

        /** Gap between characters (~28 chars/s total with PRESS_MS). */
        const val GAP_MS = 12L

        const val PROGRESS_EVERY = 10
        const val JOIN_MS = 400L
    }
}
