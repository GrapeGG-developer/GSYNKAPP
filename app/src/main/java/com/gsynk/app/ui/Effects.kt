package com.gsynk.app.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Эффекты GIOUI — «стёкла» из одного набора правил.
 *
 * Идея простая: интерфейс собран из плиток GIOUI без скруглений, а объём и глубину
 * дают четыре вещи — матовое стекло под панелями, живое размытие фона, световые лучи
 * и трассировка теней. Всё считается средствами Compose (RuntimeShader/GpuField —
 * отдельно, для шейдерного поля), поэтому одинаково работает и на слабом телефоне:
 * там включается облегчённый набор, где blur меньше, а лучи заменены градиентом.
 */
object Glass {

    /** Плотность стекла: чем больше — тем плотнее матовый слой. */
    enum class Kind(val title: String) {
        PRG("GSYNKPRG"),
        MATTE("матовое"),
        LIQUID("жидкое"),
        FROST("морозное"),
        AQUA("аква")
    }

    /** Рецепт стекла: цвет подложки, её прозрачность, толщина рамки и радиус. */
    data class Recipe(
        val tint: Color,
        val alpha: Float,
        val border: Color,
        val radius: Dp,
        val blur: Dp
    )

    fun recipe(kind: Kind, accent: Color = Gioui.Palette.Purple, radius: Dp = Gioui.Radius.card): Recipe =
        when (kind) {
            Kind.PRG -> Recipe(accent, 0.18f, Color.White.copy(alpha = 0.34f), radius, 14.dp)
            Kind.MATTE -> Recipe(accent, 0.16f, Color.White.copy(alpha = 0.14f), radius, 0.dp)
            Kind.LIQUID -> Recipe(accent, 0.22f, Color.White.copy(alpha = 0.22f), radius, 6.dp)
            Kind.FROST -> Recipe(Color.White, 0.10f, Color.White.copy(alpha = 0.30f), radius, 10.dp)
            Kind.AQUA -> Recipe(Gioui.Palette.Teal, 0.24f, Gioui.Palette.Cyan.copy(alpha = 0.35f), radius, 8.dp)
        }
}

/**
 * Панель стекла: размывает то, что под ней, добавляет тонкую рамку и подкраску.
 * На уровне эффектов PLAIN размытие выключается совсем — остаётся полупрозрачный слой.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    kind: Glass.Kind = Glass.Kind.PRG,
    accent: Color = Gioui.Palette.Purple,
    shape: Shape = Gioui.Shapes.card,
    contentPadding: Dp = Gioui.unit * 1.5f,
    content: @Composable BoxScope.() -> Unit
) {
    val skin = LocalSkinValues.current
    val recipe = Glass.recipe(kind, accent, Gioui.Radius.card)
    val density = skin.prgOpacity.coerceIn(5, 90) / 100f
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f + 0.08f * (1f - density)),
                            recipe.tint.copy(alpha = 0.08f + recipe.alpha * density),
                            Color(0xFF090614).copy(alpha = 0.16f + 0.42f * density)
                        )
                    )
                )
                .border(1.dp, recipe.border, shape)
        )
        PrgGlassSheen(Modifier.matchParentSize(), accent = recipe.tint, seed = kind.ordinal * 131)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .padding(contentPadding),
            content = content
        )
    }
}

/** Полупрозрачная плитка-стекло для мелких элементов: без размытия, но с рамкой. */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    accent: Color = Gioui.Palette.Cyan,
    selected: Boolean = false
) {
    Box(
        modifier
            .clip(Gioui.Shapes.toggle)
            .background(accent.copy(alpha = if (selected) 0.34f else 0.12f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.55f else 0.20f), Gioui.Shapes.toggle)
    )
}

/**
 * Лучи и растр — «сетка» GIOUI: рисуются поверх фона и дают ощущение глубины.
 * Трассировка теней: под каждым лучом мягкое затемнение со смещением.
 */
@Composable
fun LightRays(
    modifier: Modifier = Modifier,
    accent: Color = Gioui.Palette.Purple,
    rays: Int = 5,
    intensity: Float = 1f
) {
    val effects = LocalGiouiEffects.current
    if (effects == Gioui.Effects.PLAIN) return
    val transition = rememberInfiniteTransition(label = "rays")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (effects == Gioui.Effects.FULL) 14_000 else 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "raysPhase"
    )
    Box(
        modifier.drawWithCacheCompat { size ->
            val w = size.width
            val h = size.height
            val step = step(rays, intensity)
            var i = 0
            while (i < rays) {
                val base = (i * w / rays) + (phase - 0.5f) * w / (rays * 2f)
                val angle = -18f + i * 7f
                rotate(angle, Offset(base, h * 0.1f)) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.16f * step),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(base, 0f),
                        size = androidx.compose.ui.geometry.Size(step * 26f, h)
                    )
                    // Трассировка тени от луча: тёмная копия со смещением.
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.10f * step), Color.Transparent)
                        ),
                        topLeft = Offset(base + 16f, 0f),
                        size = androidx.compose.ui.geometry.Size(step * 8f, h)
                    )
                }
                i++
            }
        }
    )
}

private fun step(rays: Int, intensity: Float): Float = (1f / rays) * 3f * intensity

/**
 * Мягкая тень по периметру блока — вместо elevation из Material: у нас свои правила,
 * GIOUI не признаёт тени в качестве украшения, поэтому они только «трассируют» края.
 */
@Composable
fun TraceShadow(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    strength: Float = 0.35f,
    shape: Shape = Gioui.Shapes.card
) {
    if (LocalGiouiEffects.current == Gioui.Effects.PLAIN) return
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = strength))
                )
            )
    )
}

/**
 * Живой фон: сетка GIOUI плюс плавающие пятна акцента. Рисуется одним полотном,
 * поэтому не мешает содержимому и не перекрывает интерфейс.
 */
@Composable
fun GrainField(
    modifier: Modifier = Modifier,
    accent: Color = Gioui.Palette.Purple,
    second: Color = Gioui.Palette.Cyan,
    speed: Float = 1f
) {
    val effects = LocalGiouiEffects.current
    val transition = rememberInfiniteTransition(label = "grain")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (22_000 / speed.coerceAtLeast(0.2f)).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "grainPhase"
    )
    Box(
        modifier.drawWithCacheCompat { size ->
            val w = size.width
            val h = size.height
            // Плитки-ориентиры: тонкая сетка 20 px, как в исходниках Gioui.
            val cell = 20f
            var x = 0f
            while (x < w) {
                drawLine(Color.White.copy(alpha = 0.03f), Offset(x, 0f), Offset(x, h), 1f)
                x += cell
            }
            var y = 0f
            while (y < h) {
                drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y), Offset(w, y), 1f)
                y += cell
            }
            if (effects != Gioui.Effects.PLAIN) {
                val angle = t * 6.283f
                val cx = w * (0.5f + 0.32f * cos(angle))
                val cy = h * (0.35f + 0.22f * sin(angle * 1.3f))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = w * 0.62f
                    ),
                    radius = w * 0.62f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(second.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(w - cx, h * (0.8f - 0.3f * sin(angle))),
                        radius = w * 0.5f
                    ),
                    radius = w * 0.5f,
                    center = Offset(w - cx, h * (0.8f - 0.3f * sin(angle)))
                )
            }
        }
    )
}

/**
 * Обводка «фокус»: тонкая рамка, которая дышит. Используется на активных плитках
 * и элементах, требующих внимания — без ярких кругов и без ложных состояний.
 */
@Composable
fun FocusRing(
    modifier: Modifier = Modifier,
    color: Color = Gioui.Palette.Cyan,
    active: Boolean = true
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "focus")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "focusPulse"
    )
    Box(
        modifier.drawWithCacheCompat { size ->
            drawRoundRect(
                color = color.copy(alpha = 0.20f + 0.45f * pulse),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                size = size,
                style = Stroke(width = 2f)
            )
        }
    )
}

/**
 * Полотно с кэшем рисования: Compose 1.7+ требует явного CacheDrawScope, но нам
 * достаточно пробросить размер — обёртка держит вызовы в одном месте.
 */
private fun Modifier.drawWithCacheCompat(
    block: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Size) -> Unit
): Modifier = drawWithCache {
    onDrawWithContent { block(this, size) }
}

/** Прозрачность для слоёв: чтобы не плодить состояния в каждом экране. */
@Composable
fun veil(alpha: Float): Modifier {
    val effects = LocalGiouiEffects.current
    val value = if (effects == Gioui.Effects.FULL) alpha else (alpha * 0.7f).coerceAtMost(1f)
    return Modifier.alpha(value)
}

/** Общая точка входа: единый стиль стекла для всего приложения. */
@Composable
fun gsynkAccent(): Color = UiTweaks.accentStart

/** Скругление по назначению — «везде своё», как в GIOUI. */
fun radiusOf(kind: String): Dp = when (kind) {
    "tile" -> Gioui.Radius.tile
    "card" -> Gioui.Radius.card
    "sheet" -> Gioui.Radius.sheet
    "button" -> Gioui.Radius.button
    "toggle" -> Gioui.Radius.switch
    "capsule" -> Gioui.Radius.capsule
    "panel" -> Gioui.Radius.panel
    else -> Gioui.Radius.card
}

/** Форма по назначению. */
fun shapeOf(kind: String): Shape = RoundedCornerShape(radiusOf(kind))
