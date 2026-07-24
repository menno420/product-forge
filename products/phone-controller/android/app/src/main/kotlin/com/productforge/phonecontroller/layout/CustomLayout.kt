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

    /**
     * code = saved-gesture id (Slice 13). Meaningful only in Play-on-this-phone
     * overlay mode, where the overlay replays the recorded touch on the game
     * underneath; INERT in remote HID mode (a screen gesture has no remote meaning).
     */
    GESTURE,
}

data class PadAction(val type: PadActionType, val code: String)

/** Button shapes the styler can render (stored by name; default ROUNDED). */
enum class PadShape { ROUNDED, CIRCLE, PILL, SQUARE }

/** Supporter style-pack fills (Slice 9): flat is free; the rest are the €1 treats. */
enum class PadFx { FLAT, GRADIENT, GLOW }

/**
 * Anything the editor can drag/resize by percent (Slice 15) — buttons AND widgets.
 * Sharing one interface lets the editor's drag logic move either kind uniformly.
 */
interface PadPositioned {
    var xPct: Float
    var yPct: Float
    var wPct: Float
    var hPct: Float
    fun clampToPad()
}

/** Interactive widgets a custom layout can hold beyond plain buttons (Slice 15). */
enum class PadWidgetType { LEFT_STICK, RIGHT_STICK, DPAD, TOUCHPAD, GYRO }

/** A placed widget: a type + percent rect (bigger min size than a button). */
data class PadWidgetSpec(
    var type: PadWidgetType,
    override var xPct: Float,
    override var yPct: Float,
    override var wPct: Float,
    override var hPct: Float,
) : PadPositioned {
    override fun clampToPad() {
        wPct = wPct.coerceIn(0.10f, 0.9f)
        hPct = hPct.coerceIn(0.10f, 0.9f)
        xPct = xPct.coerceIn(0f, 1f - wPct)
        yPct = yPct.coerceIn(0f, 1f - hPct)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("wt", type.name)
        .put("x", xPct.toDouble()).put("y", yPct.toDouble())
        .put("w", wPct.toDouble()).put("h", hPct.toDouble())

    companion object {
        fun fromJson(o: JSONObject): PadWidgetSpec = PadWidgetSpec(
            type = PadWidgetType.valueOf(o.getString("wt")),
            xPct = o.getDouble("x").toFloat(),
            yPct = o.getDouble("y").toFloat(),
            wPct = o.getDouble("w").toFloat(),
            hPct = o.getDouble("h").toFloat(),
        ).also { it.clampToPad() }
    }
}

data class PadButtonSpec(
    override var xPct: Float,
    override var yPct: Float,
    override var wPct: Float,
    override var hPct: Float,
    var label: String,
    var action: PadAction,
    var turbo: Boolean = false,
    /** ARGB background color, or null for the platform-default button look. */
    var colorArgb: Int? = null,
    var shape: PadShape = PadShape.ROUNDED,
    var textSizeSp: Int = 14,
    var fx: PadFx = PadFx.FLAT,
) : PadPositioned {
    override fun clampToPad() {
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
    /** Placed widgets (Slice 15): sticks / D-pad / touchpad / gyro. */
    val widgets: MutableList<PadWidgetSpec> = mutableListOf(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("buttons", JSONArray().also { arr -> buttons.forEach { arr.put(it.toJson()) } })
        .also { o -> bgColorArgb?.let { o.put("bg", it) } }
        // "widgets" written only when present, so pre-Slice-15 layouts round-trip byte-identical.
        .also { o ->
            if (widgets.isNotEmpty()) {
                o.put("widgets", JSONArray().also { arr -> widgets.forEach { arr.put(it.toJson()) } })
            }
        }

    /** A deep copy under a new id/name (layout-manager Duplicate). */
    fun duplicate(newId: String, newName: String): CustomLayout = CustomLayout(
        id = newId,
        name = newName,
        buttons = buttons.map { it.copy() }.toMutableList(),
        bgColorArgb = bgColorArgb,
        widgets = widgets.map { it.copy() }.toMutableList(),
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
            val widgets = mutableListOf<PadWidgetSpec>()
            if (o.has("widgets")) {
                val wArr = o.getJSONArray("widgets")
                for (i in 0 until wArr.length()) {
                    // A widget type from a newer version is skipped, never fatal.
                    runCatching { widgets.add(PadWidgetSpec.fromJson(wArr.getJSONObject(i))) }
                }
            }
            return CustomLayout(
                o.getString("id"),
                o.getString("name"),
                buttons,
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else null,
                widgets = widgets,
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
            CustomLayout(newId, parsed.name, parsed.buttons, parsed.bgColorArgb, parsed.widgets)
        }.getOrNull()

        private fun gp(x: Float, y: Float, w: Float, h: Float, label: String, code: String) =
            PadButtonSpec(x, y, w, h, label, PadAction(PadActionType.GAMEPAD, code))

        /** Starter templates offered when creating a new layout (Slice 15/17). */
        fun templateKinds(): List<String> =
            listOf("Blank", "GBA", "Full gamepad", "Analog + sticks", "NDS (touch + pad)")

        fun template(id: String, name: String, kind: String = "GBA"): CustomLayout = when (kind) {
            "Blank" -> CustomLayout(id, name, mutableListOf())
            "NDS (touch + pad)" -> CustomLayout(
                id, name,
                mutableListOf(
                    gp(0.02f, 0.42f, 0.15f, 0.10f, "L", "L1"),
                    gp(0.34f, 0.42f, 0.14f, 0.10f, "SELECT", "SELECT"),
                    gp(0.52f, 0.42f, 0.14f, 0.10f, "START", "START"),
                    gp(0.83f, 0.42f, 0.15f, 0.10f, "R", "R1"),
                    gp(0.80f, 0.56f, 0.10f, 0.13f, "X", "X"),
                    gp(0.68f, 0.69f, 0.10f, 0.13f, "Y", "Y"),
                    gp(0.90f, 0.69f, 0.10f, 0.13f, "A", "A"),
                    gp(0.80f, 0.82f, 0.10f, 0.13f, "B", "B"),
                ),
                widgets = mutableListOf(
                    PadWidgetSpec(PadWidgetType.TOUCHPAD, 0.10f, 0.02f, 0.80f, 0.36f),
                    PadWidgetSpec(PadWidgetType.DPAD, 0.02f, 0.56f, 0.30f, 0.40f),
                ),
            )
            "Full gamepad" -> CustomLayout(
                id, name,
                mutableListOf(
                    gp(0.02f, 0.00f, 0.16f, 0.13f, "L1", "L1"),
                    gp(0.82f, 0.00f, 0.16f, 0.13f, "R1", "R1"),
                    gp(0.90f, 0.32f, 0.10f, 0.17f, "A", "A"),
                    gp(0.78f, 0.20f, 0.10f, 0.17f, "B", "B"),
                    gp(0.66f, 0.32f, 0.10f, 0.17f, "X", "X"),
                    gp(0.78f, 0.44f, 0.10f, 0.17f, "Y", "Y"),
                    gp(0.34f, 0.84f, 0.14f, 0.14f, "SELECT", "SELECT"),
                    gp(0.52f, 0.84f, 0.14f, 0.14f, "START", "START"),
                ),
                widgets = mutableListOf(PadWidgetSpec(PadWidgetType.DPAD, 0.02f, 0.28f, 0.30f, 0.44f)),
            )
            "Analog + sticks" -> CustomLayout(
                id, name,
                mutableListOf(
                    gp(0.44f, 0.06f, 0.12f, 0.16f, "Y", "Y"),
                    gp(0.36f, 0.24f, 0.12f, 0.16f, "X", "X"),
                    gp(0.52f, 0.24f, 0.12f, 0.16f, "B", "B"),
                    gp(0.44f, 0.42f, 0.12f, 0.16f, "A", "A"),
                    gp(0.02f, 0.00f, 0.15f, 0.12f, "L1", "L1"),
                    gp(0.83f, 0.00f, 0.15f, 0.12f, "R1", "R1"),
                    gp(0.34f, 0.86f, 0.14f, 0.12f, "SELECT", "SELECT"),
                    gp(0.52f, 0.86f, 0.14f, 0.12f, "START", "START"),
                ),
                widgets = mutableListOf(
                    PadWidgetSpec(PadWidgetType.LEFT_STICK, 0.02f, 0.42f, 0.28f, 0.5f),
                    PadWidgetSpec(PadWidgetType.RIGHT_STICK, 0.70f, 0.42f, 0.28f, 0.5f),
                    PadWidgetSpec(PadWidgetType.GYRO, 0.70f, 0.02f, 0.12f, 0.10f),
                ),
            )
            else -> CustomLayout( // "GBA" (the original default core)
                id, name,
                mutableListOf(
                    gp(0.86f, 0.30f, 0.13f, 0.22f, "A", "A"),
                    gp(0.72f, 0.52f, 0.13f, 0.22f, "B", "B"),
                    gp(0.30f, 0.84f, 0.18f, 0.14f, "SELECT", "SELECT"),
                    gp(0.52f, 0.84f, 0.18f, 0.14f, "START", "START"),
                    gp(0.00f, 0.00f, 0.16f, 0.13f, "L", "L1"),
                    gp(0.84f, 0.00f, 0.16f, 0.13f, "R", "R1"),
                ),
                widgets = mutableListOf(PadWidgetSpec(PadWidgetType.DPAD, 0.00f, 0.26f, 0.28f, 0.46f)),
            )
        }
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
