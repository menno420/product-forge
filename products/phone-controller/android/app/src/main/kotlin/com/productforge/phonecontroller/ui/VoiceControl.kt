/*
 * VoiceControl — user-defined voice phrases that fire controller actions
 * (Slice 11).
 *
 * Design stance (owner constraint: no bloat, nothing people won't understand):
 *   * ONE Settings entry owns the whole feature: an enable switch + a phrase
 *     list. No new chips, pads, or modes.
 *   * Phrases are user-defined and matched against what the device's own
 *     speech recognizer hears in the device's own language — the feature is
 *     language-agnostic by construction (a Dutch user says Dutch phrases).
 *   * Foreground-only: listening starts in onResume and STOPS in onPause.
 *     Private (no background mic), battery-sane, easy to reason about.
 *   * Honest latency: recognition finalizes ~a second after the phrase ends —
 *     right for media control, presenting, accessibility; wrong for twitch
 *     gaming, and the UI copy says so.
 *
 * Engine: android.speech.SpeechRecognizer (in the platform — no new deps),
 * offline-preferred, FINAL results only (partials would false-trigger),
 * continuous via restart-on-result/error with a short backoff. Matching is
 * normalized containment: "please pause now" fires the "pause" command.
 */
package com.productforge.phonecontroller.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONArray
import org.json.JSONObject

/** One phrase → action binding. Type/code use the shared action vocabulary. */
data class VoiceCommand(
    val phrase: String,
    val actionType: String,
    val actionCode: String,
    val actionLabel: String,
)

/** Prefs-backed command list (fail-soft JSON, same pattern as LayoutStore). */
class VoiceStore(private val prefs: SharedPreferences) {

    fun all(): List<VoiceCommand> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VoiceCommand(
                    o.getString("p"), o.getString("t"), o.getString("c"),
                    o.optString("l", o.getString("t")),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun add(command: VoiceCommand) = persist(all() + command)

    fun removeAt(index: Int) {
        val list = all().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            persist(list)
        }
    }

    private fun persist(list: List<VoiceCommand>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().put("p", c.phrase).put("t", c.actionType)
                    .put("c", c.actionCode).put("l", c.actionLabel),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "voice_commands"
    }
}

/** Continuous listen loop. Create/start/stop on the MAIN thread only. */
class VoiceDriver(
    private val context: Context,
    private val commands: () -> List<VoiceCommand>,
    private val onMatch: (VoiceCommand) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val intent: Intent
        get() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    fun start() {
        if (running || !available) return
        running = true
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                match(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                restart(RESTART_MS)
            }

            override fun onError(error: Int) {
                // BUSY/CLIENT need a fresh cycle; everything else just retries.
                restart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) BUSY_RESTART_MS else RESTART_MS)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listenOnce()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private fun listenOnce() {
        if (!running) return
        recognizer?.let { runCatching { it.startListening(intent) } }
    }

    private fun restart(delayMs: Long) {
        if (!running) return
        handler.postDelayed({ listenOnce() }, delayMs)
    }

    private fun match(candidates: List<String>?) {
        if (candidates.isNullOrEmpty()) return
        val active = commands()
        if (active.isEmpty()) return
        for (heard in candidates) {
            val normalized = normalize(heard)
            val hit = active.firstOrNull { c ->
                val phrase = normalize(c.phrase)
                phrase.isNotEmpty() && (normalized == phrase || " $normalized ".contains(" $phrase "))
            }
            if (hit != null) {
                onMatch(hit)
                return // one action per utterance — predictable beats clever
            }
        }
    }

    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()
            .replace(Regex(" +"), " ")

    private companion object {
        const val RESTART_MS = 250L
        const val BUSY_RESTART_MS = 800L
    }
}
