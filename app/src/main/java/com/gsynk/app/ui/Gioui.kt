package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GIOUI — GSYNK INTELLIGENT OUR UI (5.0).
 *
 * Единая дизайн-система вместо старого интерфейса:
 *  * один язык форм: карточки и плитки 24 dp, контролы 16 dp, листы 28 dp, чипы и остров — капсулы;
 *  * одна сетка карточек: функции лежат по категориям, а не свалены одной лентой;
 *  * одна типографика: компактные заголовки без огромных «плакатов»;
 *  * одна виноградная палитра акцентов на тёмном фирменном фоне;
 *  * эффекты (стекло/аврора) — по силам железа: слабому телефону честно даётся облегчённый уровень.
 */
object Gioui {

    /** Юнит сетки GIOUI: 20 px в исходнике, у нас — 8 dp, чтобы попадать в экран. */
    val unit: Dp = 8.dp

    /** Зазор между плитками: один юнит (в исходнике 10 px на 720p). */
    val gutter: Dp = 10.dp

    /** Внутренняя сетка карточек: 4 колонки-единицы для размеров SMALL…LARGE. */
    const val COLUMNS = 4

    /** Панели больше не прячут функции глубже второго уровня — всё плитками. */
    val radius = Radius

    /** Скругления GIOUI: единый язык форм. */
    object Radius {
        /** Карточки-функции: единый мягкий угол GIOUI. */
        val tile: Dp = 24.dp
        /** Карточки и списки — тот же угол, что у плиток. */
        val card: Dp = 24.dp
        /** Листы и диалоги — чуть круглее. */
        val sheet: Dp = 28.dp
        /** Кнопки и поля — единый контрольный угол. */
        val button: Dp = 16.dp
        /** Переключатели и чипы — как контролы. */
        val switch: Dp = 16.dp
        /** Остров — самый скруглённый элемент приложения. */
        val island: Dp = 28.dp
        /** Панель обновления — как лист. */
        val panel: Dp = 20.dp
    }

    object Shapes {
        val tile = RoundedCornerShape(Radius.tile)
        val card = RoundedCornerShape(Radius.card)
        val sheet = RoundedCornerShape(Radius.sheet)
        val button = RoundedCornerShape(Radius.button)
        val toggle = RoundedCornerShape(Radius.switch)
        val island = RoundedCornerShape(Radius.island)
        val panel = RoundedCornerShape(Radius.panel)
    }

    /** Типографика GIOUI: четыре размера, разные начертания, ничего лишнего. */
    object Type {
        val pageTitle = 30.sp       // заголовок страницы: крупный, но без «плаката»
        val pageSubtitle = 17.sp    // подзаголовок страницы
        val groupHeader = 16.sp     // заголовок категории
        val tileTitle = 14.sp       // название на карточке-функции
        val tileLive = 22.sp        // живое число на карточке
        val body = 15.sp            // основной текст
        val caption = 12.sp         // подписи
    }

    /**
     * Палитра GIOUI: виноградные акценты GSYNK. Цвета достаточно насыщенные для белого
     * текста на карточках, но не кислотные — единый набор на всё приложение.
     */
    object Palette {
        val Cobalt = Color(0xFF3B82F6)
        val Indigo = Color(0xFF6366F1)
        val Violet = Color(0xFF8B5CF6)
        val Purple = Color(0xFF7C3AED)
        val Magenta = Color(0xFFD946EF)
        val Crimson = Color(0xFFBE123C)
        val Red = Color(0xFFEF4444)
        val Orange = Color(0xFFF97316)
        val Amber = Color(0xFFF59E0B)
        val Yellow = Color(0xFFEAB308)
        val Brown = Color(0xFF92400E)
        val Olive = Color(0xFF65A30D)
        val Steel = Color(0xFF64748B)
        val Mauve = Color(0xFF9D7BD8)
        val Taupe = Color(0xFFA08C5B)
        val Sienna = Color(0xFFB45309)

        val Lime = Color(0xFF84CC16)
        val Green = Color(0xFF22C55E)
        val Emerald = Color(0xFF10B981)
        val Teal = Color(0xFF14B8A6)
        val Cyan = Color(0xFF06B6D4)
        val Blue = Color(0xFF0EA5E9)
        val Pink = Color(0xFFEC4899)
        val Mango = Color(0xFFFB923C)
        val Gray = Color(0xFF475569)
        val White = Color(0xFFFFFFFF)

        /** Порядок акцентов карточек — от холодного к тёплому. */
        val tiles = listOf(
            Cobalt, Emerald, Purple, Amber, Magenta, Teal, Orange, Indigo,
            Green, Crimson, Cyan, Violet, Olive, Red, Steel, Lime, Mauve, Sienna, Yellow, Mango
        )

        /** Акцент по имени — тот же список, что в настройках внешнего вида. */
        fun byName(name: String): Color = when (name) {
            "лёд" -> Cyan
            "янтарь" -> Amber
            "изумруд" -> Emerald
            "гранат" -> Magenta
            else -> Purple
        }
    }

    /** Размер плитки в клетках: ширина × высота. */
    enum class Size(val w: Int, val h: Int) {
        SMALL(1, 1),
        MEDIUM(2, 2),
        WIDE(4, 2),
        TALL(2, 4),
        LARGE(4, 4);

        val id: String get() = w.toString() + "x" + h.toString()

        companion object {
            fun of(id: String): Size =
                entries.firstOrNull { it.id == id } ?: MEDIUM
        }
    }

    /** Сколько строк займёт набор плиток — для предварительной высоты. */
    fun rowsFor(tiles: List<Size>, columns: Int = COLUMNS): Int {
        val grid = ArrayList<BooleanArray>()
        var maxRow = 0
        for (tile in tiles) {
            val place = findPlace(grid, tile, columns)
            maxRow = max(maxRow, place.second + tile.h)
        }
        return max(1, maxRow)
    }

    /** Ищем первое свободное место в сетке — так плитки укладываются без дыр. */
    fun findPlace(grid: ArrayList<BooleanArray>, tile: Size, columns: Int): Pair<Int, Int> {
        var row = 0
        while (row < 400) {
            while (grid.size <= row + tile.h) grid.add(BooleanArray(columns))
            for (column in 0..(columns - tile.w)) {
                if (fits(grid, row, column, tile, columns)) return row to column
            }
            row++
        }
        return 0 to 0
    }

    private fun fits(grid: ArrayList<BooleanArray>, row: Int, column: Int, tile: Size, columns: Int): Boolean {
        if (column + tile.w > columns) return false
        for (r in row until row + tile.h) {
            while (grid.size <= r) grid.add(BooleanArray(columns))
            for (c in column until column + tile.w) if (grid[r][c]) return false
        }
        return true
    }

    private fun occupy(grid: ArrayList<BooleanArray>, row: Int, column: Int, tile: Size, columns: Int) {
        for (r in row until row + tile.h) {
            while (grid.size <= r) grid.add(BooleanArray(columns))
            for (c in column until column + tile.w) grid[r][c] = true
        }
    }

    /** Раскладывает плитки и отдаёт их места — этим пользуется [GioGrid]. */
    fun plan(tiles: List<Size>, columns: Int = COLUMNS): List<Pair<Int, Int>> {
        val grid = ArrayList<BooleanArray>()
        return tiles.map { tile ->
            val place = findPlace(grid, tile, columns)
            occupy(grid, place.first, place.second, tile, columns)
            place
        }
    }

    /** Размер клетки под доступную ширину — чтобы сетка всегда влезала. */
    fun cellSize(available: Dp, columns: Int = COLUMNS): Dp {
        val gutters = gutter * (columns - 1)
        return ((available - gutters) / columns).coerceAtLeast(24.dp)
    }

    /** Уровень эффектов: на старом телефоне тяжёлое стекло выключаем. */
    enum class Effects(val title: String) {
        FULL("полные"),
        LIGHT("облегчённые"),
        PLAIN("простые")
    }

    fun effectsFor(context: android.content.Context): Effects {
        val profile = DeviceProfile.detect(context)
        return when {
            profile.isFull && android.os.Build.VERSION.SDK_INT >= 31 -> Effects.FULL
            android.os.Build.VERSION.SDK_INT >= 31 -> Effects.LIGHT
            else -> Effects.PLAIN
        }
    }

    /** Текущая метка интерфейса — показывается в настройках и отчётах. */
    const val UI_VERSION = "GIOUI 1.0"
}

/** Уровень эффектов, доступный всему интерфейсу без лишних вызовов. */
val LocalGiouiEffects = compositionLocalOf { Gioui.Effects.LIGHT }

/** Текущая кожа (скругления и стекло) для всех компонентов: один подписчик на всё дерево. */
val LocalSkinValues = compositionLocalOf { Skin.Values() }

/** Скругление по назначению — «везде своё», из настроек кожи. */
@Composable
fun skinRadius(kind: String): Dp {
    val skin = LocalSkinValues.current
    return when (kind) {
        "tile" -> skin.tile.dp
        "card" -> skin.card.dp
        "sheet" -> skin.sheet.dp
        "button" -> skin.button.dp
        "toggle" -> skin.toggle.dp
        "island" -> skin.island.dp
        "panel" -> skin.sheet.dp
        else -> skin.card.dp
    }
}

/** Форма по назначению — из той же кожи. */
@Composable
fun skinShape(kind: String): Shape = RoundedCornerShape(skinRadius(kind))

/** Стекло из настроек: имя вида → рецепт. */
@Composable
fun skinGlass(): Glass.Kind = when (LocalSkinValues.current.glass) {
    "matte" -> Glass.Kind.MATTE
    "frost" -> Glass.Kind.FROST
    "aqua" -> Glass.Kind.AQUA
    else -> Glass.Kind.LIQUID
}

/** Ширина сетки: на большом экране плитки не растягиваются в блин. */
val LocalGiouiColumns = compositionLocalOf { Gioui.COLUMNS }

/**
 * Сетка плиток Gioui.
 *
 * Каждая плитка помечается `Modifier.gioSpan(Gioui.Size.MEDIUM)` — раскладка сама
 * находит ей место без дыр и без вложенных
 * меню. Всё, что нужно человеку, лежит плиткой на одном уровне.
 */
@LayoutScopeMarker
@Composable
fun GioGrid(
    modifier: Modifier = Modifier,
    columns: Int = LocalGiouiColumns.current,
    content: @Composable () -> Unit
) {
    val gutters = Gioui.gutter.value * (columns - 1)
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val cell = ((width - gutters) / columns).coerceAtLeast(24f)
        val spans = measurables.map { it.layoutId?.toString() ?: Gioui.Size.MEDIUM.id }
        val sizes = spans.map { Gioui.Size.of(it) }
        val places = Gioui.plan(sizes, columns)
        val cellPx = cell
        val gutterPx = Gioui.gutter.value * (if (density > 0) density else 1f)

        val placeables = measurables.mapIndexed { index, measurable ->
            val size = sizes[index]
            measurable.measure(
                Constraints.fixed(
                    (cellPx * size.w + gutterPx * (size.w - 1)).roundToInt().coerceAtLeast(1),
                    (cellPx * size.h + gutterPx * (size.h - 1)).roundToInt().coerceAtLeast(1)
                )
            )
        }
        val height = placeables.mapIndexed { index, placeable ->
            places[index].first * (cellPx + gutterPx) + placeable.height
        }.maxOrNull()?.roundToInt() ?: 0

        layout(width, height.coerceAtLeast(0)) {
            placeables.forEachIndexed { index, placeable ->
                val (row, column) = places[index]
                val x = (column * (cellPx + gutterPx)).roundToInt()
                val y = (row * (cellPx + gutterPx)).roundToInt()
                placeable.placeRelative(x, y)
            }
        }
    }
}

/** Помечает карточку размером GIOUI: 1×1, 2×2, 4×2, 4×4. */
fun Modifier.gioSpan(size: Gioui.Size): Modifier = this.layoutId(size.id)

/**
 * Карточка-функция GIOUI: градиент акцента, единый угол 24 dp, подпись внизу слева. Внутри могут жить живые значения из состояния приложения.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun GioCard(
    title: String,
    subtitle: String,
    size: Gioui.Size,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    live: String? = null,
    badge: String? = null,
    dark: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effects = LocalGiouiEffects.current
    val titleSize = when (size) {
        Gioui.Size.SMALL -> 12.sp
        Gioui.Size.MEDIUM, Gioui.Size.TALL -> Gioui.Type.tileTitle
        Gioui.Size.WIDE, Gioui.Size.LARGE -> 15.sp
    }
    val textColor = if (dark) Color.White else Color(0xFF101018)

    Box(
        modifier
            .gioSpan(size)
            .clip(Gioui.Shapes.tile)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.68f))))
            .border(1.dp, Color.White.copy(alpha = 0.16f), Gioui.Shapes.tile)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = {
                            UiTweaks.tap(context, long = true)
                            onLongClick()
                        },
                        onClick = {
                            UiTweaks.tap(context)
                            onClick()
                        }
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        UiTweaks.tap(context)
                        onClick()
                    }
                }
            )
    ) {
        // Живое переливание: только там, где телефон это вытянет.
        if (effects != Gioui.Effects.PLAIN) {
            GioSheen(color = color, strong = effects == Gioui.Effects.FULL)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(Gioui.unit * 1.5f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        painter = rememberVectorPainter(icon),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier
                            .width(Gioui.unit * 2.5f)
                            .height(Gioui.unit * 2.5f)
                    )
                    Spacer(Modifier.width(Gioui.unit))
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = Gioui.unit, vertical = 2.dp)
                    ) {
                        Text(badge, color = textColor, fontSize = Gioui.Type.caption, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column {
                if (live != null && size != Gioui.Size.SMALL) {
                    Text(
                        text = live,
                        color = textColor,
                        fontSize = Gioui.Type.tileLive,
                        fontWeight = FontWeight.Light,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = title,
                    color = textColor,
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (size == Gioui.Size.SMALL) 2 else 1
                )
                if (size != Gioui.Size.SMALL && subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = textColor.copy(alpha = 0.78f),
                        fontSize = Gioui.Type.caption,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Заголовок страницы GIOUI: крупный Light-текст и подпись под ним. */
@Composable
fun GioHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Gioui.unit * 2f, bottom = Gioui.unit * 1.5f),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = Gioui.Type.pageTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = TextDim,
                fontSize = Gioui.Type.pageSubtitle,
                fontWeight = FontWeight.Light,
                maxLines = 2
            )
        }
        trailing()
    }
}

/** Заголовок группы GIOUI: строчные буквы и лёгкая разрядка. */
@Composable
fun GioGroup(header: String, modifier: Modifier = Modifier) {
    Text(
        text = header,
        color = TextPrimary.copy(alpha = 0.86f),
        fontSize = Gioui.Type.groupHeader,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = Gioui.unit * 1.5f, bottom = Gioui.unit),
        letterSpacing = 1.6.sp
    )
}

/** Плавный блик по плитке — «живая плитка» из GIOUI, но без лишних кадров. */
@Composable
fun GioSheen(color: Color, strong: Boolean) {
    val light = if (strong) 0.22f else 0.12f
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = light),
                        Color.Transparent,
                        color.copy(alpha = 0.12f)
                    )
                )
            )
    )
}

/** Общая тема оформления: сетка и уровень эффектов для всего дерева. */
@Composable
fun ProvideGioui(
    context: android.content.Context,
    content: @Composable () -> Unit
) {
    val profile = remember(context) { DeviceProfile.detect(context) }
    val skin by Skin.values.collectAsStateWithLifecycle()
    // Уровень эффектов берём по худшему из двух: что просит кожа и что тянет железо.
    val effects = remember(profile, skin.level) {
        when (minOf(skin.level, Skin.effectiveLevel(profile, skin.level))) {
            0 -> Gioui.Effects.PLAIN
            1 -> Gioui.Effects.LIGHT
            else -> Gioui.Effects.FULL
        }
    }
    // На большом экране плиток в строке больше: так задумано в GIOUI для широких экранов.
    val columns = remember(profile) { if (profile.width >= 1440) 6 else Gioui.COLUMNS }
    CompositionLocalProvider(
        LocalGiouiEffects provides effects,
        LocalGiouiColumns provides columns,
        LocalSkinValues provides skin,
        content = content
    )
}

/** Расшифровка уровня эффектов для интерфейса и отчётов. */
fun effectsLabel(context: android.content.Context): String =
    Gioui.effectsFor(context).title + " (" + Gioui.UI_VERSION + ")"
