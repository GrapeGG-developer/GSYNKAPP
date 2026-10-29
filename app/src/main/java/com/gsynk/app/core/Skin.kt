package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кожа GIOUI: единый набор форм и «стекла» на всё приложение.
 *
 * В GIOUI 5.2 формы плоские, как в Windows 8: плитки почти квадратные,
 * карточки едва смягчены, а капсулы остаются самым круглым акцентом. Старые
 * сохранённые большие значения при загрузке ужимаются новыми границами,
 * поэтому обновление поверх 5.1 сразу показывает резкий Metro-контур.
 */
object Skin {

    /** Значения радиусов в dp. По умолчанию — канон GIOUI. */
    data class Values(
        val tile: Int = 3,
        val card: Int = 6,
        val sheet: Int = 8,
        val button: Int = 4,
        val toggle: Int = 6,
        val capsule: Int = 14,
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
            capsule = prefs.getInt("gio_capsule", defaults.capsule),
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
        tile = values.tile.coerceIn(0, 10),
        card = values.card.coerceIn(2, 14),
        sheet = values.sheet.coerceIn(4, 18),
        button = values.button.coerceIn(0, 12),
        toggle = values.toggle.coerceIn(2, 14),
        capsule = values.capsule.coerceIn(8, 22),
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
            .putInt("gio_capsule", clean.capsule)
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
        Triple("gioui", "GIOUI Metro", Values(tile = 3, card = 6, sheet = 8, button = 4, toggle = 6, capsule = 14, glass = "liquid", blur = 12, focus = 40, rays = 5, grid = true, speed = 100, level = 2)),
        Triple("strict", "GIOUI строгое", Values(tile = 0, card = 2, sheet = 4, button = 0, toggle = 2, capsule = 8, glass = "matte", blur = 0, focus = 20, rays = 2, grid = true, speed = 100, level = 1)),
        Triple("soft", "GIOUI чуть мягче", Values(tile = 10, card = 14, sheet = 18, button = 12, toggle = 14, capsule = 22, glass = "frost", blur = 18, focus = 75, rays = 4, grid = false, speed = 85, level = 2)),
        Triple("aqua", "GIOUI аква", Values(tile = 4, card = 7, sheet = 10, button = 5, toggle = 7, capsule = 16, glass = "aqua", blur = 10, focus = 70, rays = 8, grid = true, speed = 125, level = 2)),
        Triple("light", "GIOUI облегчённое", Values(tile = 2, card = 4, sheet = 6, button = 2, toggle = 4, capsule = 10, glass = "matte", blur = 0, focus = 30, rays = 0, grid = false, speed = 140, level = 0)),
        Triple("compact", "GIOUI компактное", Values(tile = 1, card = 3, sheet = 5, button = 1, toggle = 3, capsule = 9, glass = "matte", blur = 4, focus = 25, rays = 2, grid = false, speed = 115, level = 1))
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
