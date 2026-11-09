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
        val glass: String = "prg",
        val blur: Int = 12,
        val focus: Int = 40,
        val rays: Int = 5,
        val grid: Boolean = true,
        val speed: Int = 100,
        val level: Int = 2,
        /** GSYNKPRG: плотность прозрачного стекла, контурное свечение, блики и темп бликов. */
        val prgOpacity: Int = 38,
        val prgGlow: Int = 55,
        val prgGlare: Int = 65,
        val prgMotion: Int = 100
    ) {
        /** Уровень эффектов: 0 — простые, 1 — облегчённые, 2 — полные. */
        val effectsLevel: Int get() = level.coerceIn(0, 2)
        val blurOn: Boolean get() = blur > 2 && level > 0
    }

    val GLASSES: List<Pair<String, String>> = listOf(
        "prg" to "GSYNKPRG",
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

    /** Готовые лица GIOUI: одна система форм, разное стекло и темп. */
    val PRESETS: List<Triple<String, String, Values>> = listOf(
        Triple("gioui", "GSYNKPRG Premium", Values(glass = "prg", blur = 16, focus = 55, rays = 6, grid = true, speed = 100, level = 2, prgOpacity = 38, prgGlow = 60, prgGlare = 70, prgMotion = 100)),
        Triple("strict", "GSYNKPRG Строгий", Values(tile = 0, card = 2, sheet = 4, button = 0, toggle = 2, capsule = 8, glass = "prg", blur = 6, focus = 25, rays = 2, grid = true, speed = 90, level = 1, prgOpacity = 55, prgGlow = 25, prgGlare = 25, prgMotion = 80)),
        Triple("soft", "GSYNKPRG Мягкий", Values(tile = 10, card = 14, sheet = 18, button = 12, toggle = 14, capsule = 22, glass = "prg", blur = 24, focus = 80, rays = 5, grid = false, speed = 85, level = 2, prgOpacity = 30, prgGlow = 75, prgGlare = 85, prgMotion = 90)),
        Triple("aqua", "GSYNKPRG Аква", Values(tile = 4, card = 7, sheet = 10, button = 5, toggle = 7, capsule = 16, glass = "prg", blur = 18, focus = 70, rays = 9, grid = true, speed = 125, level = 2, prgOpacity = 34, prgGlow = 65, prgGlare = 80, prgMotion = 125)),
        Triple("light", "GSYNKPRG Лёгкий", Values(tile = 2, card = 4, sheet = 6, button = 2, toggle = 4, capsule = 10, glass = "prg", blur = 0, focus = 25, rays = 0, grid = false, speed = 120, level = 0, prgOpacity = 48, prgGlow = 15, prgGlare = 15, prgMotion = 60)),
        Triple("compact", "GSYNKPRG Компактный", Values(tile = 1, card = 3, sheet = 5, button = 1, toggle = 3, capsule = 9, glass = "prg", blur = 8, focus = 35, rays = 3, grid = false, speed = 110, level = 1, prgOpacity = 48, prgGlow = 40, prgGlare = 45, prgMotion = 110))
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
