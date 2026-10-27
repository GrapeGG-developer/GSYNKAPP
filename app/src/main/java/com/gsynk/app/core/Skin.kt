package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кожа GIOUI: единый набор форм и «стекла» на всё приложение.
 *
 * В GIOUI формы больше не «у каждого элемента свой каприз», а одна система:
 * карточки 24, контролы 16, листы 28, остров — капсула. Настраивать можно вокруг
 * этих значений (экран «скругления»), но вернутся старые квадратные формы нельзя:
 * радиусы 5.0 читаются из новых ключей `gio_*`, поэтому обновление поверх 4.x
 * сразу показывает новый интерфейс, а не сохранённый старый.
 */
object Skin {

    /** Значения радиусов в dp. По умолчанию — канон GIOUI. */
    data class Values(
        val tile: Int = 24,
        val card: Int = 24,
        val sheet: Int = 28,
        val button: Int = 16,
        val toggle: Int = 16,
        val island: Int = 32,
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
            // Радиусы — только ключи GIOUI: старые формы 4.x при обновлении не воскресают.
            tile = prefs.getInt("gio_tile", defaults.tile),
            card = prefs.getInt("gio_card", defaults.card),
            sheet = prefs.getInt("gio_sheet", defaults.sheet),
            button = prefs.getInt("gio_button", defaults.button),
            toggle = prefs.getInt("gio_toggle", defaults.toggle),
            island = prefs.getInt("gio_island", defaults.island),
            glass = prefs.getString("glass", defaults.glass) ?: defaults.glass,
            blur = prefs.getInt("blur", defaults.blur),
            focus = prefs.getInt("focus", defaults.focus),
            rays = prefs.getInt("rays", defaults.rays),
            grid = prefs.getBoolean("grid", defaults.grid),
            speed = prefs.getInt("speed", defaults.speed),
            level = prefs.getInt("level", defaults.level)
        )
        val clean = clamp(loaded)
        _values.value = clean
        return clean
    }

    /** Единые границы GIOUI: формы можно смягчать/строжить, но система не распадается. */
    fun clamp(values: Values): Values = values.copy(
        tile = values.tile.coerceIn(12, 32),
        card = values.card.coerceIn(12, 32),
        sheet = values.sheet.coerceIn(16, 36),
        button = values.button.coerceIn(10, 24),
        toggle = values.toggle.coerceIn(10, 24),
        island = values.island.coerceIn(24, 40),
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
            .putInt("gio_tile", clean.tile)
            .putInt("gio_card", clean.card)
            .putInt("gio_sheet", clean.sheet)
            .putInt("gio_button", clean.button)
            .putInt("gio_toggle", clean.toggle)
            .putInt("gio_island", clean.island)
            .putString("glass", clean.glass)
            .putInt("blur", clean.blur)
            .putInt("focus", clean.focus)
            .putInt("rays", clean.rays)
            .putBoolean("grid", clean.grid)
            .putInt("speed", clean.speed)
            .putInt("level", clean.level)
            .apply()
    }

    /** Готовые лица GIOUI: одна система форм, разное стекло и темп. */
    val PRESETS: List<Triple<String, String, Values>> = listOf(
        Triple("gioui", "GIOUI единое", Values(tile = 24, card = 24, sheet = 28, button = 16, toggle = 16, island = 32, glass = "liquid", blur = 12, focus = 40, rays = 5, grid = true, speed = 100, level = 2)),
        Triple("strict", "GIOUI строгое", Values(tile = 14, card = 14, sheet = 18, button = 11, toggle = 11, island = 26, glass = "matte", blur = 0, focus = 20, rays = 2, grid = true, speed = 100, level = 1)),
        Triple("soft", "GIOUI мягкое", Values(tile = 30, card = 30, sheet = 34, button = 20, toggle = 20, island = 36, glass = "frost", blur = 18, focus = 75, rays = 4, grid = false, speed = 85, level = 2)),
        Triple("aqua", "GIOUI аква", Values(tile = 24, card = 24, sheet = 28, button = 16, toggle = 18, island = 34, glass = "aqua", blur = 10, focus = 70, rays = 8, grid = true, speed = 125, level = 2)),
        Triple("light", "GIOUI облегчённое", Values(tile = 20, card = 20, sheet = 24, button = 14, toggle = 14, island = 28, glass = "matte", blur = 0, focus = 30, rays = 0, grid = false, speed = 140, level = 0)),
        Triple("compact", "GIOUI компактное", Values(tile = 16, card = 16, sheet = 20, button = 12, toggle = 12, island = 26, glass = "matte", blur = 4, focus = 25, rays = 2, grid = false, speed = 115, level = 1))
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
