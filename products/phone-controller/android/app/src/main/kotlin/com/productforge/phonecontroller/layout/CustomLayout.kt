/*
 * CustomLayout — the user-editable layout model + JSON codec + prefs store
 * (Slice 6, the customization moat from the market research).
 *
 * A layout is a named list of buttons positioned in PERCENT of the pad area
 * (x/y = top-left corner, w/h = size, all 0..1), each carrying one action and an
 * optional per-button turbo flag. Serialized with org.json (in the Android SDK —
 * no new dependency); stored as one JSON array in SharedPreferences.
 *
 * Action codes are stored as STRINGS (enum names, or the integer usage/mask for
 * keyboard actions) so saved layouts survive enum reordering.
 */
package com.productforge.phonecontroller.layout

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class PadActionType {
    /** code = GamepadButton name. */
    GAMEPAD,

    /** code = DpadDirection name. */
    DPAD,

    /** code = keyboard usage int (decimal string). */
    KEY,

    /** code = modifier mask int (decimal string). */
    MODIFIER,

    /** code = MediaButton name (tap semantics; turbo not applicable). */
    MEDIA,

    /** code = MouseButton name. */
    MOUSE,

    /** code = "modifierMask:usage" decimal pair (Slice 9 chords, e.g. Ctrl+C = "1:6"). */
    COMBO,

    /**
     * code = JSON step array (Slice 10 macros): [{"t":type,"c":code,"l":label,
     * "h":holdMs,"g":gapMs}, …]. Steps may be any type except MACRO (no nesting).
     */
    MACRO,
}

data class PadAction(val type: PadActionType, val code: String)

/** Button shapes the styler can render (stored by name; default ROUNDED). */
enum class PadShape { ROUNDED, CIRCLE, PILL, SQUARE }

/** Supporter style-pack fills (Slice 9): flat is free; the rest are the €1 treats. */
enum class PadFx { FLAT, GRADIENT, GLOW }

data class PadButtonSpec(
    var xPct: Float,
    var yPct: Float,
    var wPct: Float,
    var hPct: Float,
    var label: String,
    var action: PadAction,
    var turbo: Boolean = false,
    /** ARGB background color, or null for the platform-default button look. */
    var colorArgb: Int? = null,
    var shape: PadShape = PadShape.ROUNDED,
    var textSizeSp: Int = 14,
    var fx: PadFx = PadFx.FLAT,
) {
    fun clampToPad() {
        wPct = wPct.coerceIn(0.05f, 0.6f)
        hPct = hPct.coerceIn(0.06f, 0.6f)
        xPct = xPct.coerceIn(0f, 1f - wPct)
        yPct = yPct.coerceIn(0f, 1f - hPct)
        textSizeSp = textSizeSp.coerceIn(9, 26)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("x", xPct.toDouble())
        .put("y", yPct.toDouble())
        .put("w", wPct.toDouble())
        .put("h", hPct.toDouble())
        .put("label", label)
        .put("type", action.type.name)
        .put("code", action.code)
        .put("turbo", turbo)
        .put("shape", shape.name)
        .put("textSp", textSizeSp)
        .also { o -> if (fx != PadFx.FLAT) o.put("fx", fx.name) }
        .also { o -> colorArgb?.let { o.put("color", it) } }

    companion object {
        // Visual fields are OPTIONAL with defaults so layouts saved by older
        // versions load unchanged (guard recipe: never make a new field required).
        fun fromJson(o: JSONObject): PadButtonSpec = PadButtonSpec(
            xPct = o.getDouble("x").toFloat(),
            yPct = o.getDouble("y").toFloat(),
            wPct = o.getDouble("w").toFloat(),
            hPct = o.getDouble("h").toFloat(),
            label = o.getString("label"),
            action = PadAction(PadActionType.valueOf(o.getString("type")), o.getString("code")),
            turbo = o.optBoolean("turbo", false),
            colorArgb = if (o.has("color")) o.getInt("color") else null,
            shape = runCatching { PadShape.valueOf(o.optString("shape", "ROUNDED")) }
                .getOrDefault(PadShape.ROUNDED),
            textSizeSp = o.optInt("textSp", 14),
            fx = runCatching { PadFx.valueOf(o.optString("fx", "FLAT")) }
                .getOrDefault(PadFx.FLAT),
        ).also { it.clampToPad() }
    }
}

data class CustomLayout(
    val id: String,
    var name: String,
    val buttons: MutableList<PadButtonSpec>,
    /** ARGB pad background, or null for the default window background. */
    var bgColorArgb: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("buttons", JSONArray().also { arr -> buttons.forEach { arr.put(it.toJson()) } })
        .also { o -> bgColorArgb?.let { o.put("bg", it) } }

    /** A deep copy under a new id/name (layout-manager Duplicate). */
    fun duplicate(newId: String, newName: String): CustomLayout = CustomLayout(
        id = newId,
        name = newName,
        buttons = buttons.map { it.copy() }.toMutableList(),
        bgColorArgb = bgColorArgb,
    )

    /**
     * Share format (Slice 10): a versioned envelope around the layout JSON, compact
     * enough to paste into any chat. The version marker lets future formats evolve
     * without breaking old imports.
     */
    fun toShareString(): String =
        JSONObject().put("pcl", SHARE_VERSION).put("layout", toJson()).toString()

    companion object {
        const val SHARE_VERSION = 1

        fun fromJson(o: JSONObject): CustomLayout {
            val buttons = mutableListOf<PadButtonSpec>()
            val arr = o.getJSONArray("buttons")
            for (i in 0 until arr.length()) buttons.add(PadButtonSpec.fromJson(arr.getJSONObject(i)))
            return CustomLayout(
                o.getString("id"),
                o.getString("name"),
                buttons,
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else null,
            )
        }

        /**
         * Parse a shared layout: the {"pcl":…} envelope OR a bare layout object
         * (leniency for hand-trimmed pastes). Returns null on anything malformed —
         * the importer surfaces a friendly message, never a crash. [newId] replaces
         * the embedded id so imports can't collide with existing layouts.
         */
        fun fromShareString(raw: String, newId: String): CustomLayout? = runCatching {
            val o = JSONObject(raw.trim())
            val layoutObj = if (o.has("pcl")) o.getJSONObject("layout") else o
            val parsed = fromJson(layoutObj)
            CustomLayout(newId, parsed.name, parsed.buttons, parsed.bgColorArgb)
        }.getOrNull()

        /** A starter template (GBA-ish core) the editor seeds new layouts from. */
        fun template(id: String, name: String): CustomLayout = CustomLayout(
            id = id,
            name = name,
            buttons = mutableListOf(
                PadButtonSpec(0.05f, 0.22f, 0.12f, 0.20f, "▲", PadAction(PadActionType.DPAD, "UP")),
                PadButtonSpec(0.05f, 0.62f, 0.12f, 0.20f, "▼", PadAction(PadActionType.DPAD, "DOWN")),
                PadButtonSpec(0.00f, 0.42f, 0.11f, 0.20f, "◀", PadAction(PadActionType.DPAD, "LEFT")),
                PadButtonSpec(0.11f, 0.42f, 0.11f, 0.20f, "▶", PadAction(PadActionType.DPAD, "RIGHT")),
                PadButtonSpec(0.86f, 0.30f, 0.13f, 0.22f, "A", PadAction(PadActionType.GAMEPAD, "A")),
                PadButtonSpec(0.72f, 0.52f, 0.13f, 0.22f, "B", PadAction(PadActionType.GAMEPAD, "B")),
                PadButtonSpec(0.30f, 0.84f, 0.18f, 0.14f, "SELECT", PadAction(PadActionType.GAMEPAD, "SELECT")),
                PadButtonSpec(0.52f, 0.84f, 0.18f, 0.14f, "START", PadAction(PadActionType.GAMEPAD, "START")),
                PadButtonSpec(0.00f, 0.00f, 0.16f, 0.13f, "L", PadAction(PadActionType.GAMEPAD, "L1")),
                PadButtonSpec(0.84f, 0.00f, 0.16f, 0.13f, "R", PadAction(PadActionType.GAMEPAD, "R1")),
            ),
        )
    }
}

/** CRUD over the prefs-backed layout list. */
class LayoutStore(private val prefs: SharedPreferences) {

    fun all(): MutableList<CustomLayout> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> CustomLayout.fromJson(arr.getJSONObject(i)) }
        }.getOrElse { mutableListOf() } // a corrupt store never bricks the app
    }

    fun byId(id: String): CustomLayout? = all().firstOrNull { it.id == id }

    fun save(layout: CustomLayout) {
        val layouts = all()
        val i = layouts.indexOfFirst { it.id == layout.id }
        if (i >= 0) layouts[i] = layout else layouts.add(layout)
        persist(layouts)
    }

    fun delete(id: String) {
        persist(all().filterNot { it.id == id })
    }

    private fun persist(layouts: List<CustomLayout>) {
        val arr = JSONArray()
        layouts.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "custom_layouts"
    }
}
