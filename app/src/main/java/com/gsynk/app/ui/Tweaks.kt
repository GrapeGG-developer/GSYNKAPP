package com.gsynk.app.ui

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.Haptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки внешнего вида и отклика.
 *
 * Значения лежат в обычных переменных (их читают и не-Compose места), а `revision`
 * растёт при каждом изменении — по нему интерфейс перерисовывается целиком.
 */
object UiTweaks {

    /** Варианты акцента: имя и пара цветов градиента заголовков и фона. */
    val ACCENTS: List<Pair<String, List<Color>>> = listOf(
        "виноград" to listOf(Color(0xFF7C3AED), Color(0xFF22D3EE)),
        "лёд" to listOf(Color(0xFF0EA5E9), Color(0xFF67E8F9)),
        "янтарь" to listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)),
        "изумруд" to listOf(Color(0xFF10B981), Color(0xFF34D399)),
        "гранат" to listOf(Color(0xFFDB2777), Color(0xFFFB7185))
    )

    var accent: Int = 0
    var backgroundSpeed: Int = 1
    var haptics: Boolean = true
    var clickSound: Boolean = false
    var tileScale: Int = 2
    var tileCounters: Boolean = true
    var compactList: Boolean = false

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun apply(settings: AppSettings) {
        val changed = accent != settings.accent ||
            backgroundSpeed != settings.backgroundSpeed ||
            haptics != settings.haptics ||
            clickSound != settings.clickSound ||
            tileScale != settings.tileScale ||
            tileCounters != settings.tileCounters ||
            compactList != settings.compactList
        accent = settings.accent.coerceIn(0, ACCENTS.lastIndex)
        backgroundSpeed = settings.backgroundSpeed.coerceIn(0, 2)
        haptics = settings.haptics
        clickSound = settings.clickSound
        tileScale = settings.tileScale.coerceIn(1, 3)
        tileCounters = settings.tileCounters
        compactList = settings.compactList
        if (changed) _revision.value = _revision.value + 1
    }

    /** Градиент акцента — заголовки и подсветка. */
    val accentBrush: Brush
        get() = Brush.linearGradient(ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].second)

    val accentName: String
        get() = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].first

    val accentStart: Color
        get() = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)].second.first()

    /** Дыхание фона: медленнее или быстрее по выбору. */
    val breathMs: Int get() = when (backgroundSpeed) { 0 -> 38_000; 2 -> 13_000; else -> 24_000 }
    val shimmerMs: Int get() = when (backgroundSpeed) { 0 -> 11_000; 2 -> 3_800; else -> 6_500 }
    val driftMs: Int get() = when (backgroundSpeed) { 0 -> 34_000; 2 -> 12_000; else -> 22_000 }
    val speedName: String get() = when (backgroundSpeed) {
        0 -> "спокойно"
        2 -> "быстро"
        else -> "обычно"
    }

    /** Масштаб текста на плитках. */
    val titleScale: Float get() = when (tileScale) { 1 -> 0.9f; 3 -> 1.14f; else -> 1f }
    val tileHeight: Int get() = when (tileScale) { 1 -> 92; 3 -> 132; else -> 112 }

    /** Отклик на касание плитки: вибрация и/или короткий щелчок. */
    fun tap(context: Context, long: Boolean = false) {
        if (haptics) Haptics.tick(context, if (long) 34L else 18L, Haptics.Use.TOUCH)
        if (clickSound) AutomationActions.playTone(if (long) 880 else 1_180, 55)
    }
}
