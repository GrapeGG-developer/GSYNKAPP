package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кожа SCRIPT: единый набор материалов, света и бесшовных форм.
 *
 * SCRIPT — скевоморфная система поверх живой авроры: панели полупрозрачны,
 * кромки имеют фаску и тень, сферы и капсулы остаются главными объёмами,
 * а старые сохранённые значения по-прежнему ужимаются безопасными границами.
 */
object Skin {

    /** Значения радиусов в dp. По умолчанию — канон GIOUI. */
    data class Values(
        val tile: Int = 9,
        val card: Int = 13,
        val sheet: Int = 17,
        val button: Int = 11,
        val toggle: Int = 13,
        val capsule: Int = 21,
        val glass: String = "prg",
        val blur: Int = 18,
        val focus: Int = 58,
        val rays: Int = 7,
        val grid: Boolean = false,
        val speed: Int = 100,
        val level: Int = 2,
        /** SCRIPT: плотность материала, контурный свет, блики и темп движения света. */
        val prgOpacity: Int = 38,
        val prgGlow: Int = 68,
        val prgGlare: Int = 78,
        val prgMotion: Int = 110
    ) {
        /** Уровень эффектов: 0 — простые, 1 — облегчённые, 2 — полные. */
        val effectsLevel: Int get() = level.coerceIn(0, 2)
        val blurOn: Boolean get() = blur > 2 && level > 0
    }

    val GLASSES: List<Pair<String, String>> = listOf(
        "prg" to "SCRIPT",
        "matte" to "матовое",
        "liquid" to "жидкое",
        "frost" to "морозное",
        "aqua" to "аква"
    )

    fun glassTitle(id: String): String = GLASSES.firstOrNull { it.first == id }?.second ?: id

    private const val PREFS = "gsynk_skin"

    private val _values = MutableStateFlow(Values())
    val values: StateFlow<Values> = _values.asStateFlow()

    fun load(context: Context): Values {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = Values()
        val loaded = Values(
            // Радиусы — только ключи GIOUI: старые формы 4.x при обновлении не воскресают.
            tile = prefs.getInt("gio_tile", defaults.tile),
            card = prefs.getInt("gio_card", defaults.card),
            sheet = prefs.getInt("gio_sheet", defaults.sheet),
            button = prefs.getInt("gio_button", defaults.button),
            toggle = prefs.getInt("gio_toggle", defaults.toggle),
            capsule = prefs.getInt("gio_capsule", defaults.capsule),
            glass = prefs.getString("glass", defaults.glass) ?: defaults.glass,
            blur = prefs.getInt("blur", defaults.blur),
            focus = prefs.getInt("focus", defaults.focus),
            rays = prefs.getInt("rays", defaults.rays),
            grid = prefs.getBoolean("grid", defaults.grid),
            speed = prefs.getInt("speed", defaults.speed),
            level = prefs.getInt("level", defaults.level),
            prgOpacity = prefs.getInt("prg_opacity", defaults.prgOpacity),
            prgGlow = prefs.getInt("prg_glow", defaults.prgGlow),
            prgGlare = prefs.getInt("prg_glare", defaults.prgGlare),
            prgMotion = prefs.getInt("prg_motion", defaults.prgMotion)
        )
        val clean = clamp(loaded)
        _values.value = clean
        return clean
    }

    /** Единые границы GIOUI: формы можно смягчать/строжить, но система не распадается. */
    fun clamp(values: Values): Values = values.copy(
        tile = values.tile.coerceIn(0, 10),
        card = values.card.coerceIn(2, 14),
        sheet = values.sheet.coerceIn(4, 18),
        button = values.button.coerceIn(0, 12),
        toggle = values.toggle.coerceIn(2, 14),
        capsule = values.capsule.coerceIn(8, 22),
        blur = values.blur.coerceIn(0, 30),
        focus = values.focus.coerceIn(0, 100),
        rays = values.rays.coerceIn(0, 12),
        glass = "prg",
        speed = values.speed.coerceIn(40, 200),
        level = values.level.coerceIn(0, 2),
        prgOpacity = values.prgOpacity.coerceIn(5, 90),
        prgGlow = values.prgGlow.coerceIn(0, 100),
        prgGlare = values.prgGlare.coerceIn(0, 100),
        prgMotion = values.prgMotion.coerceIn(40, 200)
    )

    fun save(context: Context, values: Values) {
        val clean = clamp(values)
        _values.value = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("gio_tile", clean.tile)
            .putInt("gio_card", clean.card)
            .putInt("gio_sheet", clean.sheet)
            .putInt("gio_button", clean.button)
            .putInt("gio_toggle", clean.toggle)
            .putInt("gio_capsule", clean.capsule)
            .putString("glass", clean.glass)
            .putInt("blur", clean.blur)
            .putInt("focus", clean.focus)
            .putInt("rays", clean.rays)
            .putBoolean("grid", clean.grid)
            .putInt("speed", clean.speed)
            .putInt("level", clean.level)
            .putInt("prg_opacity", clean.prgOpacity)
            .putInt("prg_glow", clean.prgGlow)
            .putInt("prg_glare", clean.prgGlare)
            .putInt("prg_motion", clean.prgMotion)
            .apply()
    }

    /** Готовые лица SCRIPT: одна физическая система, разный характер материала. */
    val PRESETS: List<Triple<String, String, Values>> = listOf(
        Triple("gioui", "SCRIPT Аврора", Values(tile = 9, card = 13, sheet = 17, button = 11, toggle = 13, capsule = 21, glass = "prg", blur = 18, focus = 58, rays = 7, grid = false, speed = 100, level = 2, prgOpacity = 38, prgGlow = 68, prgGlare = 78, prgMotion = 110)),
        Triple("strict", "SCRIPT Гранит", Values(tile = 2, card = 5, sheet = 8, button = 3, toggle = 5, capsule = 12, glass = "prg", blur = 8, focus = 32, rays = 3, grid = true, speed = 90, level = 1, prgOpacity = 56, prgGlow = 34, prgGlare = 38, prgMotion = 80)),
        Triple("soft", "SCRIPT Жидкое стекло", Values(tile = 10, card = 14, sheet = 18, button = 12, toggle = 14, capsule = 22, glass = "prg", blur = 24, focus = 80, rays = 6, grid = false, speed = 85, level = 2, prgOpacity = 30, prgGlow = 82, prgGlare = 90, prgMotion = 95)),
        Triple("aqua", "SCRIPT Полярное сияние", Values(tile = 8, card = 12, sheet = 16, button = 10, toggle = 12, capsule = 20, glass = "prg", blur = 20, focus = 72, rays = 10, grid = false, speed = 125, level = 2, prgOpacity = 34, prgGlow = 76, prgGlare = 86, prgMotion = 130)),
        Triple("light", "SCRIPT Лёгкий", Values(tile = 5, card = 8, sheet = 11, button = 6, toggle = 8, capsule = 16, glass = "prg", blur = 0, focus = 28, rays = 0, grid = false, speed = 120, level = 0, prgOpacity = 50, prgGlow = 22, prgGlare = 24, prgMotion = 60)),
        Triple("compact", "SCRIPT Компактный", Values(tile = 5, card = 8, sheet = 11, button = 5, toggle = 8, capsule = 15, glass = "prg", blur = 9, focus = 40, rays = 4, grid = false, speed = 110, level = 1, prgOpacity = 48, prgGlow = 48, prgGlare = 54, prgMotion = 115))
    )
    /** Итоговый уровень с поправкой на железо: слабому телефону полный набор не отдаём. */
    fun effectiveLevel(profile: DeviceProfile.Info, requested: Int): Int =
        if (!profile.isFull) minOf(requested, 1) else requested

    /** Набор с поправкой на железо: слабому телефону тяжёлое стекло не отдаём. */
    fun effectiveValues(profile: DeviceProfile.Info, values: Values): Values =
        values.copy(level = effectiveLevel(profile, values.level))

    fun label(level: Int): String = when (level) {
        0 -> "простые"
        1 -> "облегчённые"
        else -> "полные"
    }
}
