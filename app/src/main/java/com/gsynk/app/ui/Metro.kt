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
import androidx.compose.ui.graphics.RectangleShape
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
 * Метро-раскладка GSYNK (GSUI V4).
 *
 * Взято из исходников Metro:
 *  * **сетка 20×20** — все размеры кратны ей;
 *  * **размеры плиток** как в Windows 8: малая 1×1, средняя 2×2, широкая 4×2, большая 4×4;
 *  * **зазор между плитками** — один юнит сетки (в телефоне это 10 px при 720p, у нас 6 dp);
 *  * **типографика Segoe-стиля**: заголовки Light, только четыре размера;
 *  * **плитки строго квадратные**, цвет — акцентная заливка.
 *
 * Числа не выдуманы: это опубликованные Microsoft размеры (малая 159, средняя 336,
 * широкая 691×336 при 720p; поля 120 px слева и 140 px сверху; заголовки Segoe UI Light
 * 11/20/42 pt).
 */
object Metro {

    /** Юнит сетки Metro: 20 px в исходнике, у нас — 8 dp, чтобы попадать в экран. */
    val unit: Dp = 8.dp

    /** Зазор между плитками: один юнит (в исходнике 10 px на 720p). */
    val gutter: Dp = 6.dp

    /** Сколько маленьких клеток в строке: 4 — как у широкой плитки Windows 8. */
    const val COLUMNS = 4

    /** Панели больше не прячут функции глубже второго уровня — всё плитками. */
    val radius = Radius

    /** Округления GSUI V4: плитки квадратные, важное — скруглено. */
    object Radius {
        /** Плитки Metro — строго прямые углы. */
        val tile: Dp = 0.dp
        /** Карточки и списки — мягкие, но не «капсулы». */
        val card: Dp = 14.dp
        /** Листы и диалоги — заметно скруглённые. */
        val sheet: Dp = 22.dp
        /** Кнопки — почти прямые, чтобы не спорить с плитками. */
        val button: Dp = 8.dp
        /** Переключатели и чипы — посередине. */
        val switch: Dp = 10.dp
        /** Остров — самый скруглённый элемент приложения. */
        val island: Dp = 28.dp
        /** Панель обновления — как лист. */
        val panel: Dp = 20.dp
    }

    object Shapes {
        val tile = RectangleShape
        val card = RoundedCornerShape(Radius.card)
        val sheet = RoundedCornerShape(Radius.sheet)
        val button = RoundedCornerShape(Radius.button)
        val toggle = RoundedCornerShape(Radius.switch)
        val island = RoundedCornerShape(Radius.island)
        val panel = RoundedCornerShape(Radius.panel)
    }

    /** Типографика Metro: четыре размера, разные начертания, ничего лишнего. */
    object Type {
        val pageTitle = 42.sp       // 42 pt Segoe UI Light — заголовок страницы
        val pageSubtitle = 20.sp    // 20 pt Segoe UI Light — подзаголовок
        val groupHeader = 15.sp     // заголовок группы: 11 pt Semilight + разрядка
        val tileTitle = 13.sp       // подпись плитки: 11 pt Semilight
        val tileLive = 24.sp        // живое число на плитке: 20 pt Light
        val body = 14.sp            // текст: 11 pt
        val caption = 11.sp         // подписи: 9 pt
    }

    /**
     * Палитра Metro. Первая половина — цвета Windows Phone 8, вторая — цвета
     * Windows 8 (их Microsoft показывала на презентации Metro как основные).
     */
    object Palette {
        // Windows Phone 8
        val Cobalt = Color(0xFF0050EF)
        val Indigo = Color(0xFF6A00FF)
        val Violet = Color(0xFFAA00FF)
        val Purple = Color(0xFFA200FF)
        val Magenta = Color(0xFFD80073)
        val Crimson = Color(0xFFA20025)
        val Red = Color(0xFFE51400)
        val Orange = Color(0xFFFA6800)
        val Amber = Color(0xFFF0A30A)
        val Yellow = Color(0xFFD8C100)
        val Brown = Color(0xFF825A2C)
        val Olive = Color(0xFF6D8764)
        val Steel = Color(0xFF647687)
        val Mauve = Color(0xFF76608A)
        val Taupe = Color(0xFF87794E)
        val Sienna = Color(0xFFA0522D)

        // Windows Phone 8, вторая десятка
        val Lime = Color(0xFFA4C400)
        val Green = Color(0xFF60A917)
        val Emerald = Color(0xFF008A00)
        val Teal = Color(0xFF00ABA9)
        val Cyan = Color(0xFF1BA1E2)
        val Blue = Color(0xFF3A87AD)
        val Pink = Color(0xFFF472D0)
        val Mango = Color(0xFFF09609)
        val Gray = Color(0xFF333333)
        val White = Color(0xFFFFFFFF)

        /** Порядок для плиток приложения — от холодного к тёплому. */
        val tiles = listOf(
            Cobalt, Emerald, Purple, Amber, Magenta, Teal, Orange, Indigo,
            Green, Crimson, Cyan, Violet, Olive, Red, Steel, Lime, Mauve, Sienna, Yellow, Mango
        )

        /** Акцент по имени — тот же список, что в настройках кастомизации. */
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

    /** Раскладывает плитки и отдаёт их места — этим пользуется [MetroGrid]. */
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
    const val UI_VERSION = "GSUI V4"
}

/** Уровень эффектов, доступный всему интерфейсу без лишних вызовов. */
val LocalMetroEffects = compositionLocalOf { Metro.Effects.LIGHT }

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
val LocalMetroColumns = compositionLocalOf { Metro.COLUMNS }

/**
 * Сетка плиток Metro.
 *
 * Каждая плитка помечается `Modifier.metroSpan(Metro.Size.MEDIUM)` — раскладка сама
 * находит ей место, как это делает начальный экран Windows 8: без дыр и без вложенных
 * меню. Всё, что нужно человеку, лежит плиткой на одном уровне.
 */
@LayoutScopeMarker
@Composable
fun MetroGrid(
    modifier: Modifier = Modifier,
    columns: Int = LocalMetroColumns.current,
    content: @Composable () -> Unit
) {
    val gutters = Metro.gutter.value * (columns - 1)
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val cell = ((width - gutters) / columns).coerceAtLeast(24f)
        val spans = measurables.map { it.layoutId?.toString() ?: Metro.Size.MEDIUM.id }
        val sizes = spans.map { Metro.Size.of(it) }
        val places = Metro.plan(sizes, columns)
        val cellPx = cell
        val gutterPx = Metro.gutter.value * (if (density > 0) density else 1f)

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

/** Помечает плитку размером Metro: 1×1, 2×2, 4×2, 4×4. */
fun Modifier.metroSpan(size: Metro.Size): Modifier = this.layoutId(size.id)

/**
 * Плитка начального экрана Windows 8: заливка акцентом, прямые углы, подпись внизу слева. Внутри могут жить живые значения — они приходят из состояния приложения.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Tile8(
    title: String,
    subtitle: String,
    size: Metro.Size,
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
    val effects = LocalMetroEffects.current
    val titleSize = when (size) {
        Metro.Size.SMALL -> 12.sp
        Metro.Size.MEDIUM, Metro.Size.TALL -> Metro.Type.tileTitle
        Metro.Size.WIDE, Metro.Size.LARGE -> 15.sp
    }
    val textColor = if (dark) Color.White else Color(0xFF101018)

    Box(
        modifier
            .metroSpan(size)
            .clip(Metro.Shapes.tile)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.10f), Metro.Shapes.tile)
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
        if (effects != Metro.Effects.PLAIN) {
            MetroSheen(color = color, strong = effects == Metro.Effects.FULL)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(Metro.unit * 1.5f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        painter = rememberVectorPainter(icon),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier
                            .width(Metro.unit * 2.5f)
                            .height(Metro.unit * 2.5f)
                    )
                    Spacer(Modifier.width(Metro.unit))
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(Metro.Shapes.tile)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = Metro.unit, vertical = 2.dp)
                    ) {
                        Text(badge, color = textColor, fontSize = Metro.Type.caption, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column {
                if (live != null && size != Metro.Size.SMALL) {
                    Text(
                        text = live,
                        color = textColor,
                        fontSize = Metro.Type.tileLive,
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
                    maxLines = if (size == Metro.Size.SMALL) 2 else 1
                )
                if (size != Metro.Size.SMALL && subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = textColor.copy(alpha = 0.78f),
                        fontSize = Metro.Type.caption,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Заголовок страницы Metro: крупный Light-текст и подпись под ним. */
@Composable
fun MetroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Metro.unit * 2f, bottom = Metro.unit * 1.5f),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = Metro.Type.pageTitle,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = TextDim,
                fontSize = Metro.Type.pageSubtitle,
                fontWeight = FontWeight.Light,
                maxLines = 2
            )
        }
        trailing()
    }
}

/** Заголовок группы: как «Рабочий стол» в Windows 8 — строчные буквы и разрядка. */
@Composable
fun MetroGroup(header: String, modifier: Modifier = Modifier) {
    Text(
        text = header,
        color = TextPrimary.copy(alpha = 0.86f),
        fontSize = Metro.Type.groupHeader,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = Metro.unit * 1.5f, bottom = Metro.unit),
        letterSpacing = 1.6.sp
    )
}

/** Плавный блик по плитке — «живая плитка» из Metro, но без лишних кадров. */
@Composable
fun MetroSheen(color: Color, strong: Boolean) {
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
fun ProvideMetro(
    context: android.content.Context,
    content: @Composable () -> Unit
) {
    val profile = remember(context) { DeviceProfile.detect(context) }
    val skin by Skin.values.collectAsStateWithLifecycle()
    // Уровень эффектов берём по худшему из двух: что просит кожа и что тянет железо.
    val effects = remember(profile, skin.level) {
        when (minOf(skin.level, Skin.effectiveLevel(profile, skin.level))) {
            0 -> Metro.Effects.PLAIN
            1 -> Metro.Effects.LIGHT
            else -> Metro.Effects.FULL
        }
    }
    // На большом экране плиток в строке больше: так задумано в Metro для широких экранов.
    val columns = remember(profile) { if (profile.width >= 1440) 6 else Metro.COLUMNS }
    CompositionLocalProvider(
        LocalMetroEffects provides effects,
        LocalMetroColumns provides columns,
        LocalSkinValues provides skin,
        content = content
    )
}

/** Расшифровка уровня эффектов для интерфейса и отчётов. */
fun effectsLabel(context: android.content.Context): String =
    Metro.effectsFor(context).title + " (" + Metro.UI_VERSION + ")"
