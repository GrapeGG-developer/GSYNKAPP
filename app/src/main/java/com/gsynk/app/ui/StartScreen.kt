package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.BuildConfig
import com.gsynk.app.R
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

internal data class StartPlacement(val x: Int, val y: Int, val w: Int, val h: Int)

private data class StartGroupData(
    val key: String,
    val title: String,
    val hint: String,
    val entries: List<Catalog.Entry>
)

/**
 * Геометрия «жидкого окна» GSYNKPRG.
 *
 * Окно приложения не появляется из воздуха и не анимирует логотип: оно
 * стартует из реального прямоугольника нажатой плитки, разными скоростями
 * растекается по ширине и высоте в полноэкранное окно, а при закрытии тем же
 * путём сжимается и уплывает обратно в плитку (или в закладку при сворачивании).
 * Все координаты — доли корневого окна, поэтому модель не зависит от dp/px.
 */
data class LiquidWindowOrigin(
    val id: Int = 0,
    val route: String = "",
    val title: String = "GSYNK",
    val iconRes: Int = 0,
    val color: Color = Color(0xFF8B5CF6),
    val left: Float = 0.16f,
    val top: Float = 0.73f,
    val width: Float = 0.68f,
    val height: Float = 0.20f
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

enum class LiquidWindowExit { CLOSE, MINIMIZE }

val LocalLiquidWindowLaunch = staticCompositionLocalOf<LiquidWindowOrigin?> { null }
val LocalLiquidWindowLaunchConsumer = staticCompositionLocalOf<(Int) -> Unit> { {} }
val LocalLiquidWindowExit = staticCompositionLocalOf<LiquidWindowExit?> { null }

internal data class LiquidWindowRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** Прямоугольник активной закладки на пустом доме: туда окно уплывает при сворачивании. */
internal val LIQUID_MINIMIZE_RECT = LiquidWindowRect(
    left = 0.035f,
    top = 0.045f,
    width = 0.34f,
    height = 0.09f
)

private fun liquidEase(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p) * (1f - p)
}

private fun liquidSmoothstep(from: Float, to: Float, progress: Float): Float {
    val p = ((progress - from) / (to - from)).coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

private fun liquidLerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

internal fun LiquidWindowOrigin.toLiquidRect(): LiquidWindowRect = LiquidWindowRect(
    left = left.coerceIn(0f, 0.98f),
    top = top.coerceIn(0f, 0.98f),
    width = width.coerceIn(0.04f, 1f),
    height = height.coerceIn(0.03f, 1f)
)

/**
 * Текущий прямоугольник окна: ширина растекается раньше высоты, центр
 * доплывает чуть позже — из-за этого движение выглядит жидким, а не просто zoom-ом.
 */
internal fun liquidWindowRect(progress: Float, origin: LiquidWindowOrigin): LiquidWindowRect {
    val p = progress.coerceIn(0f, 1f)
    val from = origin.toLiquidRect()
    val widthProgress = liquidEase(p * 1.16f)
    val heightProgress = liquidEase((p - 0.08f) / 0.92f)
    val centerProgress = liquidEase((p - 0.03f) / 0.97f)
    val width = liquidLerp(from.width, 1f, widthProgress)
    val height = liquidLerp(from.height, 1f, heightProgress)
    val centerX = liquidLerp(from.centerX, 0.5f, centerProgress)
    val centerY = liquidLerp(from.centerY, 0.5f, centerProgress)
    return LiquidWindowRect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        width = width,
        height = height
    )
}

internal fun liquidWindowContentAlpha(progress: Float): Float =
    liquidSmoothstep(0.20f, 0.68f, progress.coerceIn(0f, 1f))

internal fun liquidWindowSeedAlpha(progress: Float): Float =
    1f - liquidSmoothstep(0.30f, 0.66f, progress.coerceIn(0f, 1f))

internal fun liquidWindowCornerDp(progress: Float): Float =
    liquidLerp(24f, 3f, liquidEase(progress))

internal fun LayoutCoordinates.toLiquidWindowOrigin(entry: Catalog.Entry): LiquidWindowOrigin {
    var root: LayoutCoordinates = this
    while (root.parentLayoutCoordinates != null) {
        root = root.parentLayoutCoordinates!!
    }
    val rootWidth = root.size.width.coerceAtLeast(1).toFloat()
    val rootHeight = root.size.height.coerceAtLeast(1).toFloat()
    val position = positionInRoot()
    return LiquidWindowOrigin(
        route = entry.route,
        title = entry.title,
        iconRes = entry.iconRes,
        color = entry.color,
        left = (position.x / rootWidth).coerceIn(0f, 0.98f),
        top = (position.y / rootHeight).coerceIn(0f, 0.98f),
        width = (size.width / rootWidth).coerceIn(0.04f, 1f),
        height = (size.height / rootHeight).coerceIn(0.03f, 1f)
    )
}

internal fun startSpan(size: Gioui.Size): Pair<Int, Int> = when (size) {
    Gioui.Size.SMALL -> 1 to 1
    Gioui.Size.MEDIUM -> 2 to 1
    Gioui.Size.WIDE -> 2 to 1
    Gioui.Size.TALL -> 1 to 2
    Gioui.Size.LARGE -> 2 to 2
}

internal fun startPlacements(entries: List<Catalog.Entry>, columns: Int = 5): List<StartPlacement> {
    val occupied = ArrayList<BooleanArray>()
    fun fits(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (x + w > columns) return false
        while (occupied.size < y + h) occupied += BooleanArray(columns)
        for (row in y until y + h) for (col in x until x + w) if (occupied[row][col]) return false
        return true
    }
    return entries.map { entry ->
        val (w, h) = startSpan(entry.size)
        var placed: StartPlacement? = null
        var y = 0
        while (placed == null && y < 100) {
            for (x in 0..columns - w) {
                if (fits(x, y, w, h)) {
                    placed = StartPlacement(x, y, w, h)
                    break
                }
            }
            y++
        }
        val result = placed ?: StartPlacement(0, occupied.size, w, h)
        while (occupied.size < result.y + result.h) occupied += BooleanArray(columns)
        for (row in result.y until result.y + result.h) {
            for (col in result.x until result.x + result.w) occupied[row][col] = true
        }
        result
    }
}

internal const val START_GAP_DP = 6

/** Целевая ширина ячейки для выбранного размера плиток. */
internal fun startDesiredCellDp(tileScale: Int): Int = when (tileScale.coerceIn(1, 3)) {
    1 -> 64
    3 -> 84
    else -> 72
}

/** Сколько колонок целиком помещается в ширину экрана. */
internal fun startColumnsForWidth(widthDp: Int, desiredCellDp: Int = 112, gapDp: Int = START_GAP_DP): Int =
    ((widthDp + gapDp) / (desiredCellDp + gapDp)).coerceIn(3, 9)

/** Точная ячейка, при которой сетка не выходит за доступную ширину. */
internal fun startCellWidthDp(widthDp: Int, columns: Int, gapDp: Int = START_GAP_DP): Int {
    val safeColumns = columns.coerceAtLeast(1)
    return ((widthDp - gapDp * (safeColumns - 1)) / safeColumns).coerceAtLeast(48)
}

/** Сколько строк целиком помещается на одной горизонтальной странице «Пуска». */
internal fun startRowsForHeight(heightDp: Int, desiredCellDp: Int = 112, gapDp: Int = START_GAP_DP): Int =
    ((heightDp + gapDp) / (desiredCellDp + gapDp)).coerceIn(2, 4)

/** Высота ячейки страницы: сетка заполняет экран по вертикали, но не выходит из него. */
internal fun startCellHeightDp(heightDp: Int, rows: Int, cellWidthDp: Int, gapDp: Int = START_GAP_DP): Int {
    val safeRows = rows.coerceAtLeast(1)
    val byHeight = ((heightDp - gapDp * (safeRows - 1)) / safeRows).coerceAtLeast(1)
    val byWidth = ((cellWidthDp.coerceAtLeast(1) * 5 + 3) / 4).coerceAtLeast(1)
    return minOf(byHeight, byWidth)
}

/**
 * Делит категорию на полноэкранные страницы для прокрутки вправо.
 * Порядок плиток сохраняется, а каждая страница целиком помещается в заданные строки.
 */
internal fun startPageChunks(
    entries: List<Catalog.Entry>,
    columns: Int,
    maxRows: Int
): List<List<Catalog.Entry>> {
    if (entries.isEmpty()) return emptyList()
    val safeMaxRows = maxRows.coerceAtLeast(2)
    val pages = mutableListOf<List<Catalog.Entry>>()
    var current = mutableListOf<Catalog.Entry>()
    entries.forEach { entry ->
        val candidate = current + entry
        val candidateRows = startPlacements(candidate, columns).maxOfOrNull { it.y + it.h } ?: 1
        if (current.isNotEmpty() && candidateRows > safeMaxRows) {
            pages += current
            current = mutableListOf(entry)
        } else {
            current += entry
        }
    }
    if (current.isNotEmpty()) pages += current
    return pages
}

/**
 * Единственный экран в альбомной ориентации (EXP: GSYNK теперь только портретный).
 * Он специально ничего не запускает по касанию: GSYNK сам откроется в портрете.
 */
@Composable
fun PortraitRequiredScreen() {
    AnimatedGrapeBackground {
        val transition = rememberInfiniteTransition(label = "landscapePhone")
        val rock by transition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phoneRock"
        )
        val pulse by transition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phonePulse"
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* касание намеренно ничего не делает: нужен именно поворот */ }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.grapescript_logo),
                        contentDescription = null,
                        modifier = Modifier.width(78.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "GSYNK",
                        color = TextPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(34.dp))
                Box(
                    Modifier
                        .size(width = 150.dp, height = 282.dp)
                        .graphicsLayer {
                            rotationZ = rock
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(skinShape("sheet"))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), skinShape("sheet")),
                    contentAlignment = Alignment.Center
                ) {
                    PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 531)
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_phone_landscape),
                        contentDescription = "Вертикальный телефон",
                        tint = Color.White.copy(alpha = 0.96f),
                        modifier = Modifier.size(width = 246.dp, height = 123.dp).graphicsLayer { rotationZ = 90f }
                    )
                }
            }
        }
    }
}

/**
 * Пустой портретный дом после закрытия «Пуска» или сворачивания плитки.
 * Первичный запуск касания не требует: MainScreen сам открывает «Пуск»
 * сразу, как только появляется портретный экран.
 */
@Composable
fun EmptyHomeScreen(
    onOpenStart: () -> Unit,
    minimizedTitle: String? = null,
    onResumeMinimized: () -> Unit = {}
) {
    AnimatedGrapeBackground {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenStart() }
        ) {
            if (minimizedTitle != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 12.dp)
                        .width(132.dp)
                        .height(78.dp)
                        .clip(RectangleShape)
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color(0xFF4C1D95).copy(alpha = 0.58f))))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RectangleShape)
                        .clickable { onResumeMinimized() }
                        .padding(10.dp)
                ) {
                    PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 97)
                    Column {
                        Text("активный угол", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
                        Text(
                            minimizedTitle,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.grapescript_logo),
                        contentDescription = "Логотип Grape Script",
                        modifier = Modifier.width(96.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "GSYNK",
                        color = TextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (minimizedTitle != null) {
                        "приложение свёрнуто в левый верхний угол"
                    } else {
                        "EXP · лента времени: прошлое сверху, будущее снизу"
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (minimizedTitle != null) {
                        "коснитесь активной закладки, чтобы продолжить"
                    } else {
                        "коснитесь экрана, чтобы снова открыть «Пуск»"
                    },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

/**
 * GSYNKPRG Start EXP — «лента времени» в духе Pebble. Приложение портретное,
 * «Пуск» открывается на точке «сейчас»: вверх — прошлое (новости, уведомления
 * приложения и настройки), вниз — будущее (уменьшенные плитки по категориям).
 */
@Composable
fun StartMenuScreen(
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    val groups = remember(favorites) {
        buildList {
            if (favorites.isNotEmpty()) {
                val favoriteEntries = Catalog.ALL
                    .filter { it.id in favorites }
                    .sortedByDescending { it.hot }
                if (favoriteEntries.isNotEmpty()) {
                    add(StartGroupData("favorites", "моё", "закреплённые долгим нажатием плитки", favoriteEntries))
                }
            }
            Catalog.Group.entries.forEach { group ->
                val entries = Catalog.ALL
                    .filter { it.group == group }
                    .sortedWith(
                        compareByDescending<Catalog.Entry> { it.id in favorites }
                            .thenByDescending { it.hot }
                    )
                if (entries.isNotEmpty()) {
                    add(StartGroupData(group.name, group.title, group.hint, entries))
                }
            }
        }
    }
    // Прошлое ленты: от самого давнего (настройки) к ближайшему к «сейчас» (новости).
    val pastEntries = remember {
        listOf("settings", "permissions", "diag", "agent", "update", "news")
            .mapNotNull { Catalog.byId(it) }
    }
    val desiredCell = startDesiredCellDp(UiTweaks.tileScale)
    AnimatedGrapeBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RectangleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.09f),
                                Color(0xFF8B5CF6).copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RectangleShape)
            ) {
                PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 11)
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.grapescript_logo),
                        contentDescription = "Логотип Grape Script",
                        modifier = Modifier.height(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("GSYNK", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "пуск · " + BuildConfig.VERSION_NAME + " EXP · лента времени",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RectangleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RectangleShape)
                            .clickable { onCloseHome() },
                        contentAlignment = Alignment.Center
                    ) { Text("⌂", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Light) }
                }
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val contentWidthDp = (maxWidth.value.toInt() - 20).coerceAtLeast(1)
                val columns = startColumnsForWidth(contentWidthDp, desiredCell)
                val listState = rememberLazyListState()
                val nowIndex = pastEntries.size + 1
                var positioned by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!positioned) {
                        listState.scrollToItem(nowIndex)
                        positioned = true
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "past-head") {
                        TimelineSectionLabel("↑ прошлое", "новости, уведомления и настройки — листай вверх")
                    }
                    items(pastEntries, key = { "past-" + it.id }) { entry ->
                        TimelinePastCard(
                            entry = entry,
                            liveLines = liveLinesFor(entry),
                            onClick = { origin -> onOpen(entry, origin) }
                        )
                    }
                    item(key = "now") { TimelineNowCard() }
                    var runningIndex = 0
                    groups.forEach { group ->
                        val baseIndex = runningIndex
                        runningIndex += group.entries.size
                        item(key = "future-" + group.key) {
                            TimelineGroupSection(
                                group = group,
                                columns = columns,
                                baseIndex = baseIndex,
                                favorites = favorites,
                                minimizedRoute = minimizedRoute,
                                liveLinesFor = liveLinesFor,
                                onOpen = onOpen,
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                    }
                    item(key = "future-end") {
                        TimelineSectionLabel("конец будущего", "дальше — только следующие версии GSYNK")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionLabel(title: String, hint: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            maxLines = 1
        )
        Text(
            hint,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Карточка «прошлого»: широкая строка на всю ширину ленты. */
@Composable
private fun TimelinePastCard(
    entry: Catalog.Entry,
    liveLines: List<String>,
    onClick: (LiquidWindowOrigin?) -> Unit
) {
    val shape = skinShape("tile")
    val line = liveLines.map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: entry.subtitle
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(entry.color.copy(alpha = 0.42f), Color.White.copy(alpha = 0.07f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
            .clickable { onClick(launchOrigin) }
    ) {
        PrgGlassSheen(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode() + 7)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(entry.iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    line,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("›", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp)
        }
    }
}

/** Точка «сейчас» ленты: живые часы, с которых начинается каждый вход в «Пуск». */
@Composable
private fun TimelineNowCard() {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val locale = remember { Locale("ru", "RU") }
    val timeText = remember(nowMs) { SimpleDateFormat("HH:mm", locale).format(Date(nowMs)) }
    val dateText = remember(nowMs) { SimpleDateFormat("EEEE, d MMMM", locale).format(Date(nowMs)) }
    val shape = skinShape("sheet")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF8B5CF6).copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
    ) {
        PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 560)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("· сейчас ·", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, letterSpacing = 3.sp)
            Text(timeText, color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(dateText, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text(
                "↑ прошлое: новости и настройки · ↓ будущее: плитки",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Одна категория «будущего»: заголовок и плотная сетка уменьшенных квадратных плиток. */
@Composable
private fun TimelineGroupSection(
    group: StartGroupData,
    columns: Int,
    baseIndex: Int,
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit
) {
    val gap = START_GAP_DP.dp
    val entries = group.entries
    val placements = remember(entries, columns) { startPlacements(entries, columns) }
    val rows = max(1, placements.maxOfOrNull { it.y + it.h } ?: 1)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        val cellWidthDp = startCellWidthDp(maxWidth.value.toInt().coerceAtLeast(1), columns)
        val cell = cellWidthDp.dp
        val gridWidth = cell * columns + gap * (columns - 1).coerceAtLeast(0)
        val gridHeight = cell * rows + gap * (rows - 1).coerceAtLeast(0)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                group.title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                group.hint,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(gridWidth).height(gridHeight)) {
                    entries.forEachIndexed { index, entry ->
                        val place = placements[index]
                        StartTile(
                            entry = entry,
                            width = cell * place.w + gap * (place.w - 1),
                            height = cell * place.h + gap * (place.h - 1),
                            liveLines = liveLinesFor(entry),
                            favorite = entry.id in favorites,
                            minimized = entry.route == minimizedRoute,
                            index = baseIndex + index,
                            modifier = Modifier.offset(x = (cell + gap) * place.x, y = (cell + gap) * place.y),
                            onClick = { origin -> onOpen(entry, origin) },
                            onLongClick = { onToggleFavorite(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartTile(
    entry: Catalog.Entry,
    width: Dp,
    height: Dp,
    liveLines: List<String>,
    favorite: Boolean,
    minimized: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: (LiquidWindowOrigin?) -> Unit,
    onLongClick: () -> Unit
) {
    val cleanLines = remember(liveLines, entry.subtitle) {
        liveLines.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(entry.subtitle) }
    }
    var lineIndex by remember(entry.id, cleanLines) { mutableIntStateOf(0) }
    LaunchedEffect(entry.id, cleanLines) {
        lineIndex = 0
        if (cleanLines.size > 1) {
            while (true) {
                delay(2600L + (entry.id.hashCode().toLong() and 0x3ffL))
                lineIndex = (lineIndex + 1) % cleanLines.size
            }
        }
    }
    var entered by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        delay((index % 16).coerceAtLeast(0) * 30L)
        entered = true
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(320), label = "tileAlpha")
    val enterScale by animateFloatAsState(
        if (entered) 1f else 0.90f,
        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "tileEnterScale"
    )
    // Нажатая плитка чуть «приплющивается» по ширине и вытягивается по высоте:
    // это первый кадр того же жидкого движения, которым потом растекается окно.
    val pressScaleX by animateFloatAsState(
        if (pressed) 0.945f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "tilePressScaleX"
    )
    val pressScaleY by animateFloatAsState(
        if (pressed) 1.035f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "tilePressScaleY"
    )
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val skin = LocalSkinValues.current
    val density = skin.prgOpacity.coerceIn(5, 90) / 100f
    val shape = skinShape("tile")
    val wide = width >= 112.dp
    val roomy = height >= 76.dp
    val compact = height < 64.dp
    Box(
        modifier
            .graphicsLayer {
                alpha = enterAlpha
                scaleX = enterScale * pressScaleX
                scaleY = enterScale * pressScaleY
                translationY = (1f - enterAlpha) * 18f
            }
            .width(width)
            .height(height)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        entry.color.copy(alpha = 0.50f + 0.34f * density),
                        entry.color.copy(alpha = 0.28f + 0.34f * density),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                if (minimized) 2.dp else 1.dp,
                when {
                    minimized -> Color.White
                    favorite -> Color.White.copy(alpha = 0.55f)
                    else -> Color.White.copy(alpha = 0.24f)
                },
                shape
            )
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(launchOrigin) },
                onLongClick = onLongClick
            )
    ) {
        PrgGlassSheen(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode())
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (compact) 6.dp else 9.dp)
        ) {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ImageVector.vectorResource(entry.iconRes),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(if (compact) 16.dp else if (wide) 26.dp else 22.dp)
                )
                Spacer(Modifier.weight(1f))
                val badge = when {
                    minimized -> "активно"
                    entry.fresh -> "PRG"
                    favorite -> "моё"
                    else -> null
                }
                if (badge != null) {
                    Text(
                        badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = if (compact) 10.sp else if (wide) 15.sp else 13.sp,
                    lineHeight = if (compact) 11.sp else if (wide) 16.sp else 14.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (wide || roomy) {
                    AnimatedContent(
                        targetState = cleanLines[lineIndex],
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "start-live"
                    ) { line ->
                        Text(
                            line,
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetroAppShell(
    title: String,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    content: @Composable () -> Unit
) {
    val incomingLaunch = LocalLiquidWindowLaunch.current
    val consumeLaunch = LocalLiquidWindowLaunchConsumer.current
    val exitMode = LocalLiquidWindowExit.current

    var capturedLaunch by remember(title) { mutableStateOf(incomingLaunch) }
    LaunchedEffect(title, incomingLaunch?.id) {
        val launch = incomingLaunch ?: return@LaunchedEffect
        capturedLaunch = launch
        // Даём оболочке зафиксировать стартовый прямоугольник, после чего MainScreen
        // может убрать одноразовый origin: следующая внутренняя страница его не унаследует.
        delay(80L)
        consumeLaunch(launch.id)
    }

    val fallbackEntry = remember(title) {
        Catalog.ALL.firstOrNull { entry -> entry.title.equals(title, ignoreCase = true) }
    }
    val launchOrigin = capturedLaunch ?: LiquidWindowOrigin(
        title = title,
        iconRes = fallbackEntry?.iconRes ?: 0,
        color = fallbackEntry?.color ?: Color(0xFF8B5CF6)
    )
    val targetOrigin = if (exitMode == LiquidWindowExit.MINIMIZE) {
        launchOrigin.copy(
            left = LIQUID_MINIMIZE_RECT.left,
            top = LIQUID_MINIMIZE_RECT.top,
            width = LIQUID_MINIMIZE_RECT.width,
            height = LIQUID_MINIMIZE_RECT.height
        )
    } else {
        launchOrigin
    }

    val progress = remember(title) { Animatable(0f) }
    LaunchedEffect(title, exitMode) {
        if (exitMode == null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = 0.86f
                )
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
            )
        }
    }

    fun requestExit(action: () -> Unit) {
        if (exitMode == null) action()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = progress.value.coerceIn(0f, 1f)
        val rect = liquidWindowRect(p, targetOrigin)
        val contentAlpha = liquidWindowContentAlpha(p)
        val seedAlpha = liquidWindowSeedAlpha(p)
        val physicalCorner = liquidWindowCornerDp(p)
        val localCorner = (physicalCorner / max(rect.height, 0.18f)).dp
        val liquidPulse = sin(p * PI.toFloat()).coerceIn(0f, 1f)
        val accent = targetOrigin.color

        // Пока окно летит, фон под ним не должен ловить случайные касания.
        if ((progress.isRunning && p < 0.985f) || exitMode != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            )
        }

        // Мягкий цветной ореол: плитка сначала «расплывается» светом, затем в нём
        // собирается само окно. На закрытии тот же ореол сопровождает уплывание.
        if (liquidPulse > 0.01f) {
            Box(
                Modifier
                    .offset(
                        x = maxWidth * rect.left - 10.dp,
                        y = maxHeight * rect.top - 10.dp
                    )
                    .size(
                        width = maxWidth * rect.width + 20.dp,
                        height = maxHeight * rect.height + 20.dp
                    )
                    .graphicsLayer { alpha = liquidPulse * 0.32f }
                    .clip(RoundedCornerShape((physicalCorner + 10f).dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.74f),
                                Color.White.copy(alpha = 0.20f),
                                accent.copy(alpha = 0.24f)
                            )
                        )
                    )
            )
        }

        // Настоящее окно целиком: шапка и содержимое масштабируются одним слоем,
        // как снимок приложения в iOS, а не перестраиваются по частям.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(rect.centerX, rect.centerY)
                    scaleX = rect.width * (1f + liquidPulse * 0.018f)
                    scaleY = rect.height * (1f - liquidPulse * 0.011f)
                    alpha = contentAlpha
                    rotationX = (0.5f - targetOrigin.centerY) * 5f * (1f - p)
                    rotationY = (targetOrigin.centerX - 0.5f) * 4f * (1f - p)
                    cameraDistance = 18f * density
                    shape = RoundedCornerShape(localCorner)
                    clip = true
                    shadowElevation = liquidPulse * 28f
                    ambientShadowColor = accent.copy(alpha = 0.38f)
                    spotShadowColor = accent.copy(alpha = 0.46f)
                }
        ) {
            AnimatedGrapeBackground {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(34.dp)
                            .clip(RectangleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.09f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.17f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.13f), RectangleShape)
                    ) {
                        PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = title.hashCode())
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(width = 36.dp, height = 28.dp)
                                    .clip(RectangleShape)
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { requestExit(onMinimize) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("—", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Light)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                title.lowercase(),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Light,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "GSYNKPRG",
                                color = Color.White.copy(alpha = 0.48f),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(width = 40.dp, height = 28.dp)
                                    .clip(RectangleShape)
                                    .background(Color(0xFFE81123).copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RectangleShape)
                                    .clickable { requestExit(onClose) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        content()
                    }
                }
            }

            // Тонкая «волна» по стеклу на середине morph-а: окно выглядит живым,
            // но после раскрытия полностью исчезает и не мешает содержимому.
            if (liquidPulse > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = liquidPulse * 0.16f }
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    accent.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        // Семя окна: сама плитка (цвет, иконка и подпись) в том же летящем
        // прямоугольнике. Оно растворяется в настоящем окне на середине пути.
        if (seedAlpha > 0.01f) {
            val seedWidth = maxWidth * rect.width
            val seedHeight = maxHeight * rect.height
            Box(
                Modifier
                    .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                    .size(width = seedWidth, height = seedHeight)
                    .graphicsLayer { alpha = seedAlpha }
                    .clip(RoundedCornerShape(physicalCorner.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.90f),
                                accent.copy(alpha = 0.58f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.46f), RoundedCornerShape(physicalCorner.dp))
            ) {
                PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = targetOrigin.title.hashCode())
                if (targetOrigin.iconRes != 0) {
                    Icon(
                        imageVector = ImageVector.vectorResource(targetOrigin.iconRes),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.97f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(if (seedWidth >= 112.dp) 25.dp else 19.dp)
                    )
                }
                if (seedWidth >= 76.dp && seedHeight >= 48.dp) {
                    Text(
                        targetOrigin.title.lowercase(),
                        color = Color.White,
                        fontSize = if (seedWidth >= 126.dp) 14.sp else 11.sp,
                        lineHeight = if (seedWidth >= 126.dp) 15.sp else 12.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    )
                }
            }
        }

        // Верхний прозрачный щит на время полёта: содержимое уже видно, но ещё
        // рано нажимать — иначе внутренняя навигация могла бы обогнать выход окна.
        if ((progress.isRunning && p < 0.985f) || exitMode != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            )
        }
    }
}
