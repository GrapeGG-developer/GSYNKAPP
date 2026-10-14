package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кожа GSUI V4: скругления и «стёкла».
 *
 * Раньше форма была задана в коде и её нельзя было тронуть. Теперь это один объект
 * настроек: каждое скругление своё, стекло выбирается по вкусу, и всё это сразу
 * видно на экране. Значения хранятся в настройках телефона и отдаются потоком,
 * чтобы интерфейс перерисовывался сам.
 */
object Skin {

    /** Ползунок: 0 — совсем прямое, 32 — почти капсула. */
    data class Values(
        val tile: Int = 0,
        val card: Int = 14,
        val sheet: Int = 22,
        val button: Int = 8,
        val toggle: Int = 10,
        val island: Int = 28,
        val glass: String = "liquid",
        val blur: Int = 12,
        val focus: Int = 40,
        val rays: Int = 5,
        val grid: Boolean = true,
        val speed: Int = 100,
        val level: Int = 2
    ) {
        /** Уровень эффектов: 0 — простые, 1 — облегчённые, 2 — полные. */
        val effectsLevel: Int get() = level.coerceIn(0, 2)
        val blurOn: Boolean get() = blur > 2 && level > 0
    }

    val GLASSES: List<Pair<String, String>> = listOf(
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
            tile = prefs.getInt("tile", defaults.tile),
            card = prefs.getInt("card", defaults.card),
            sheet = prefs.getInt("sheet", defaults.sheet),
            button = prefs.getInt("button", defaults.button),
            toggle = prefs.getInt("toggle", defaults.toggle),
            island = prefs.getInt("island", defaults.island),
            glass = prefs.getString("glass", defaults.glass) ?: defaults.glass,
            blur = prefs.getInt("blur", defaults.blur),
            focus = prefs.getInt("focus", defaults.focus),
            rays = prefs.getInt("rays", defaults.rays),
            grid = prefs.getBoolean("grid", defaults.grid),
            speed = prefs.getInt("speed", defaults.speed),
            level = prefs.getInt("level", defaults.level)
        )
        _values.value = loaded
        return loaded
    }

    /** Приводим кожу к допустимым значениям: один вход — один порядок. */
    fun clamp(values: Values): Values = values.copy(
        tile = values.tile.coerceIn(0, 32),
        card = values.card.coerceIn(0, 32),
        sheet = values.sheet.coerceIn(0, 36),
        button = values.button.coerceIn(0, 32),
        toggle = values.toggle.coerceIn(0, 32),
        island = values.island.coerceIn(8, 40),
        blur = values.blur.coerceIn(0, 30),
        focus = values.focus.coerceIn(0, 100),
        rays = values.rays.coerceIn(0, 12),
        speed = values.speed.coerceIn(40, 200),
        level = values.level.coerceIn(0, 2)
    )

    fun save(context: Context, values: Values) {
        val clean = clamp(values)
        _values.value = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("tile", clean.tile)
            .putInt("card", clean.card)
            .putInt("sheet", clean.sheet)
            .putInt("button", clean.button)
            .putInt("toggle", clean.toggle)
            .putInt("island", clean.island)
            .putString("glass", clean.glass)
            .putInt("blur", clean.blur)
            .putInt("focus", clean.focus)
            .putInt("rays", clean.rays)
            .putBoolean("grid", clean.grid)
            .putInt("speed", clean.speed)
            .putInt("level", clean.level)
            .apply()
    }

    /** Готовые наборы: не надо крутить шесть ползунков, чтобы получить другое лицо. */
    val PRESETS: List<Triple<String, String, Values>> = listOf(
        Triple("metri", "metro чистое", Values(tile = 0, card = 0, sheet = 0, button = 0, toggle = 6, island = 28, glass = "matte", blur = 0, focus = 20, rays = 3, grid = true, speed = 100, level = 1)),
        Triple("glass", "стеклянное", Values(tile = 0, card = 16, sheet = 24, button = 10, toggle = 12, island = 30, glass = "liquid", blur = 14, focus = 60, rays = 6, grid = true, speed = 110, level = 2)),
        Triple("frost", "морозное", Values(tile = 4, card = 18, sheet = 26, button = 12, toggle = 14, island = 32, glass = "frost", blur = 20, focus = 80, rays = 4, grid = false, speed = 80, level = 2)),
        Triple("aqua", "аква", Values(tile = 2, card = 14, sheet = 20, button = 8, toggle = 16, island = 34, glass = "aqua", blur = 10, focus = 70, rays = 8, grid = true, speed = 130, level = 2)),
        Triple("light", "облегчённое", Values(tile = 0, card = 10, sheet = 16, button = 6, toggle = 8, island = 24, glass = "matte", blur = 0, focus = 30, rays = 0, grid = false, speed = 140, level = 0)),
        Triple("cube", "квадратное", Values(tile = 0, card = 0, sheet = 0, button = 0, toggle = 0, island = 20, glass = "matte", blur = 0, focus = 10, rays = 2, grid = false, speed = 100, level = 1))
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
