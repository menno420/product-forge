/*
 * SavedGesture + GestureStore — named recorded gestures (Slice 13).
 *
 * A saved gesture is a user-named TouchGesture persisted as its codec string
 * (TouchGestureCodec). Stored as one JSON array in prefs, fail-soft on read (same
 * pattern as LayoutStore/VoiceStore): a corrupt store yields an empty list, never
 * a crash. Overlay buttons of PadActionType.GESTURE reference a gesture by id.
 */
package com.productforge.phonecontroller.overlay

import android.content.SharedPreferences
import com.productforge.phonecontroller.hid.GestureGeometry
import com.productforge.phonecontroller.hid.TouchGesture
import com.productforge.phonecontroller.hid.TouchGestureCodec
import org.json.JSONArray
import org.json.JSONObject

data class SavedGesture(val id: String, val name: String, val codec: String) {
    /** Decoded gesture, or null if the stored codec is somehow corrupt. */
    fun gesture(): TouchGesture? = TouchGestureCodec.decode(codec)

    /** Rough duration in ms for the manager summary. */
    fun durationMs(): Long = gesture()?.let { GestureGeometry.totalDurationMs(it) } ?: 0L
}

class GestureStore(private val prefs: SharedPreferences) {

    fun all(): List<SavedGesture> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SavedGesture(o.getString("id"), o.getString("name"), o.getString("g"))
            }
        }.getOrElse { emptyList() }
    }

    fun byId(id: String): SavedGesture? = all().firstOrNull { it.id == id }

    fun save(gesture: SavedGesture) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.id == gesture.id }
        if (i >= 0) list[i] = gesture else list.add(gesture)
        persist(list)
    }

    fun delete(id: String) = persist(all().filterNot { it.id == id })

    private fun persist(list: List<SavedGesture>) {
        val arr = JSONArray()
        list.forEach { g ->
            arr.put(JSONObject().put("id", g.id).put("name", g.name).put("g", g.codec))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_gestures"
    }
}
