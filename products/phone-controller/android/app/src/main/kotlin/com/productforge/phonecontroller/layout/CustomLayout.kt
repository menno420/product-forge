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
import com.productforge.phonecontroller.hid.DpadDirection
import com.productforge.phonecontroller.hid.GamepadButton
import com.productforge.phonecontroller.hid.MediaButton
import com.productforge.phonecontroller.hid.MouseButton
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

/**
 * Whether (type, code) is a well-formed LONG-PRESS ALTERNATE (Codex review,
 * PR #49): the renderer switches a button into delayed tap/hold mode whenever an
 * alternate is non-null, so a malformed import must degrade to "no alternate" —
 * not to a hold that resolves to a no-op. MACRO/GESTURE are rejected by the same
 * exclusion set the editor enforces.
 */
internal fun isValidAltAction(type: PadActionType, code: String): Boolean = when (type) {
    PadActionType.GAMEPAD -> runCatching { GamepadButton.valueOf(code) }.isSuccess
    PadActionType.DPAD -> runCatching { DpadDirection.valueOf(code) }.isSuccess
    PadActionType.MEDIA -> runCatching { MediaButton.valueOf(code) }.isSuccess
    PadActionType.MOUSE -> runCatching { MouseButton.valueOf(code) }.isSuccess
    PadActionType.KEY, PadActionType.MODIFIER -> code.toIntOrNull() != null
    PadActionType.COMBO -> Regex("""\d+:\d+""").matches(code)
    PadActionType.MACRO, PadActionType.GESTURE -> false
}

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

/**
 * A placed widget: a type + percent rect (bigger min size than a button), plus
 * OPTIONAL per-widget behavior (Slice 18) — every field defaults to "inherit the
 * global setting / classic behavior" and is serialized only when set, so layouts
 * saved by any earlier version round-trip byte-identical (the standing guard
 * recipe: never make a new field required).
 */
data class PadWidgetSpec(
    var type: PadWidgetType,
    override var xPct: Float,
    override var yPct: Float,
    override var wPct: Float,
    override var hPct: Float,
    /** Sticks: deadzone override (0.0..0.30), or null = the global Settings value. */
    var deadzonePct: Float? = null,
    /** Sticks: invert the vertical axis (push up = pull down — flight-style). */
    var invertY: Boolean = false,
    /** D-pad: cardinals only — diagonal sectors snap to the nearest cardinal. */
    var fourWay: Boolean = false,
    /** Touchpad: pointer-speed percent override (25..300), or null = global. */
    var speedPct: Int? = null,
    /** Touchpad: DS-stylus semantics (contact = held LEFT button draws). */
    var penMode: Boolean = false,
) : PadPositioned {
    override fun clampToPad() {
        wPct = wPct.coerceIn(0.10f, 0.9f)
        hPct = hPct.coerceIn(0.10f, 0.9f)
        xPct = xPct.coerceIn(0f, 1f - wPct)
        yPct = yPct.coerceIn(0f, 1f - hPct)
        deadzonePct = deadzonePct?.coerceIn(0f, 0.30f)
        speedPct = speedPct?.coerceIn(25, 300)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("wt", type.name)
        .put("x", xPct.toDouble()).put("y", yPct.toDouble())
        .put("w", wPct.toDouble()).put("h", hPct.toDouble())
        // Behavior keys ride only when they differ from the default (round-trip guard).
        .also { o -> deadzonePct?.let { o.put("dz", it.toDouble()) } }
        .also { o -> if (invertY) o.put("iy", true) }
        .also { o -> if (fourWay) o.put("fw", true) }
        .also { o -> speedPct?.let { o.put("sp", it) } }
        .also { o -> if (penMode) o.put("pen", true) }

    companion object {
        fun fromJson(o: JSONObject): PadWidgetSpec = PadWidgetSpec(
            type = PadWidgetType.valueOf(o.getString("wt")),
            xPct = o.getDouble("x").toFloat(),
            yPct = o.getDouble("y").toFloat(),
            wPct = o.getDouble("w").toFloat(),
            hPct = o.getDouble("h").toFloat(),
            deadzonePct = if (o.has("dz")) o.getDouble("dz").toFloat() else null,
            invertY = o.optBoolean("iy", false),
            fourWay = o.optBoolean("fw", false),
            speedPct = if (o.has("sp")) o.getInt("sp") else null,
            penMode = o.optBoolean("pen", false),
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
    /**
     * Long-press alternate action (Slice 18), or null = classic hold semantics.
     * With an alternate set, a quick tap fires [action] and holding past the
     * Settings threshold fires THIS (held until release) — so one button carries
     * two inputs. Turbo and an alternate are mutually exclusive (the editor
     * enforces it; the renderer lets the alternate win on imported layouts).
     */
    var altAction: PadAction? = null,
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
        .also { o ->
            altAction?.let { a -> o.put("altType", a.type.name).put("altCode", a.code) }
        }

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
            // A malformed/unknown alternate degrades to "no alternate", never fatal —
            // and "malformed" includes a recognized type with a missing/invalid code
            // (Codex, PR #49): a non-null alternate flips the button into hold mode,
            // so it must only survive parsing when it can actually resolve.
            altAction = if (o.has("altType") && o.has("altCode")) {
                runCatching {
                    val type = PadActionType.valueOf(o.getString("altType"))
                    val code = o.getString("altCode")
                    if (isValidAltAction(type, code)) PadAction(type, code) else null
                }.getOrNull()
            } else {
                null
            },
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

        /** DualShock glyph colors (Slice 19) — muted toward the dark theme. */
        private val PS_TRIANGLE_GREEN = 0xFF3FA24A.toInt()
        private val PS_CIRCLE_RED = 0xFFD0342C.toInt()
        private val PS_CROSS_BLUE = 0xFF4A90D9.toInt()
        private val PS_SQUARE_PINK = 0xFFC868A8.toInt()

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

        /** A PS-diamond face button: round, glyph-colored (Slice 19). */
        private fun ps(x: Float, y: Float, label: String, code: String, color: Int) =
            PadButtonSpec(
                x, y, 0.10f, 0.15f, label, PadAction(PadActionType.GAMEPAD, code),
                colorArgb = color, shape = PadShape.CIRCLE, textSizeSp = 18,
            )

        /** Starter templates offered when creating a new layout (Slice 15/17/19). */
        fun templateKinds(): List<String> =
            listOf("Blank", "GBA", "Full gamepad", "PS2 (DualShock)", "Analog + sticks", "NDS (touch + pad)")

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
            // DualShock-2 arrangement (Slice 19): D-pad upper-left, PS diamond
            // upper-right (round, glyph-colored), BOTH sticks lower-center (the
            // DS2's signature stick placement), four digital shoulders stacked in
            // the corners (L2/R2 are descriptor bits 8/9 — no descriptor change),
            // Start/Select top-center. Positional button mapping (✕=south/A,
            // ○=east/B, □=west/X, △=north/Y per the enum's BTN_* comments);
            // emulators bind per-button anyway. No L3/R3: stick-click bits are
            // not in the descriptor, and adding them forces a re-pair fleet-wide.
            // Geometry re-cut after Codex on PR #50: Select/Start ride at y=0.13 so
            // focus mode's top-center exit chip (44 dp, ~12 % of a portrait width)
            // cannot intercept them; every interactive rect is pairwise DISJOINT —
            // FrameLayout gives the later child the overlap, so a preset must not
            // overlap at all (D-pad x ≤ 0.27 < left stick x ≥ 0.28; right stick
            // x ≤ 0.78 < ✕ x ≥ 0.79; sticks y ≥ 0.58 > □ bottom 0.55).
            "PS2 (DualShock)" -> CustomLayout(
                id, name,
                mutableListOf(
                    gp(0.00f, 0.00f, 0.15f, 0.11f, "L2", "L2"),
                    gp(0.00f, 0.12f, 0.15f, 0.11f, "L1", "L1"),
                    gp(0.85f, 0.00f, 0.15f, 0.11f, "R2", "R2"),
                    gp(0.85f, 0.12f, 0.15f, 0.11f, "R1", "R1"),
                    gp(0.30f, 0.13f, 0.16f, 0.10f, "SELECT", "SELECT"),
                    gp(0.54f, 0.13f, 0.16f, 0.10f, "START", "START"),
                    ps(0.79f, 0.26f, "△", "Y", PS_TRIANGLE_GREEN),
                    ps(0.89f, 0.40f, "○", "B", PS_CIRCLE_RED),
                    ps(0.79f, 0.54f, "✕", "A", PS_CROSS_BLUE),
                    ps(0.69f, 0.40f, "□", "X", PS_SQUARE_PINK),
                ),
                widgets = mutableListOf(
                    PadWidgetSpec(PadWidgetType.DPAD, 0.01f, 0.26f, 0.26f, 0.38f),
                    PadWidgetSpec(PadWidgetType.LEFT_STICK, 0.28f, 0.58f, 0.24f, 0.40f),
                    PadWidgetSpec(PadWidgetType.RIGHT_STICK, 0.54f, 0.58f, 0.24f, 0.40f),
                ),
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

    /**
     * Replace the WHOLE store (full-restore semantics — Codex, PR #49): layouts
     * absent from the restored snapshot are removed, not merged around.
     */
    fun replaceAll(layouts: List<CustomLayout>) = persist(layouts)

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

/** One typed settings entry inside a backup ("b"ool / "i"nt / "f"loat / "l"ong / "s"tring). */
data class BackupSetting(val key: String, val type: String, val value: Any)

/** Everything a backup blob carries; each part is optional so partial blobs restore. */
data class Backup(
    val layouts: List<CustomLayout>,
    val gesturesRaw: String?,
    val voiceRaw: String?,
    val settings: List<BackupSetting>,
    /** Key-binding store blob (Slice 21) — null in pre-Slice-21 backups. */
    val keysRaw: String? = null,
)

/**
 * Backup-everything codec (Slice 18): every custom layout + the opaque gesture and
 * voice store blobs + a typed whitelist of settings, in one pasteable text blob —
 * the same community-share mechanism as a single layout, widened to the whole
 * configuration. Exists so a reinstall (new phone, or a future release-signature
 * change) never costs the owner his layouts. Version-marked like the layout share
 * envelope so future formats can evolve without breaking old restores.
 */
object BackupCodec {
    const val VERSION = 1

    fun encode(
        layouts: List<CustomLayout>,
        gesturesRaw: String?,
        voiceRaw: String?,
        settings: List<BackupSetting>,
        keysRaw: String? = null,
    ): String {
        val o = JSONObject().put("pcb", VERSION)
        o.put("layouts", JSONArray().also { arr -> layouts.forEach { arr.put(it.toJson()) } })
        // Empty stores are encoded as explicit empties, never omitted — a full
        // restore must be able to say "there were no gestures", not stay silent
        // and leave the target's own (Codex, PR #49: restore is replace, not merge).
        o.put("gestures", gesturesRaw ?: "[]")
        o.put("voice", voiceRaw ?: "[]")
        // Key bindings (Slice 21) ride as an ADDITIVE key inside the v1 envelope:
        // a pre-Slice-21 build restoring this blob ignores the key and restores
        // everything else, where a version bump would make it reject the whole
        // blob (decide-and-flag on the Slice-21 card).
        o.put("keys", keysRaw ?: "[]")
        o.put(
            "settings",
            JSONArray().also { arr ->
                settings.forEach {
                    arr.put(JSONObject().put("k", it.key).put("t", it.type).put("v", it.value))
                }
            },
        )
        return o.toString()
    }

    /**
     * Parse a backup blob; null when the envelope is malformed OR carries a version
     * this build does not speak (Codex, PR #49: an unknown future format must be
     * rejected outright — a lenient partial read would report a successful restore
     * of a snapshot it silently misunderstood). Inside a valid v1 envelope every
     * part is best-effort: an unparseable layout or settings row is skipped, never
     * fatal — the caller reports what actually restored.
     */
    fun decode(raw: String): Backup? = runCatching {
        val o = JSONObject(raw.trim())
        if (o.optInt("pcb", 0) != VERSION) return null
        val layouts = mutableListOf<CustomLayout>()
        o.optJSONArray("layouts")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching { layouts.add(CustomLayout.fromJson(arr.getJSONObject(i))) }
            }
        }
        val settings = mutableListOf<BackupSetting>()
        o.optJSONArray("settings")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching {
                    val e = arr.getJSONObject(i)
                    settings.add(BackupSetting(e.getString("k"), e.getString("t"), e.get("v")))
                }
            }
        }
        Backup(
            layouts = layouts,
            gesturesRaw = if (o.has("gestures")) o.getString("gestures") else null,
            voiceRaw = if (o.has("voice")) o.getString("voice") else null,
            settings = settings,
            keysRaw = if (o.has("keys")) o.getString("keys") else null,
        )
    }.getOrNull()
}
