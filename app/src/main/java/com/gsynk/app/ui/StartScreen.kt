package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.max

internal data class StartPlacement(val x: Int, val y: Int, val w: Int, val h: Int)

private data class StartGroupData(
    val key: String,
    val title: String,
    val hint: String,
    val entries: List<Catalog.Entry>
)

private data class StartPageData(
    val key: String,
    val title: String,
    val hint: String,
    val entries: List<Catalog.Entry>,
    val pageIndex: Int,
    val pageCount: Int
)

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
    1 -> 96
    3 -> 128
    else -> 112
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
 * Единственный экран в портретной ориентации. Он специально ничего не запускает
 * по касанию: GSYNK сам откроется, когда телефон повернут горизонтально.
 */
@Composable
fun LandscapeRequiredScreen() {
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
                        .size(width = 282.dp, height = 150.dp)
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
                        contentDescription = "Горизонтальный телефон",
                        tint = Color.White.copy(alpha = 0.96f),
                        modifier = Modifier.size(width = 246.dp, height = 123.dp)
                    )
                }
            }
        }
    }
}

/**
 * Пустой альбомный дом после закрытия «Пуска» или сворачивания плитки.
 * Первичный запуск касания не требует: MainScreen сам открывает «Пуск»
 * сразу, как только конфигурация становится альбомной.
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
                        "альбомная ориентация включена"
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

/** GSYNKPRG Start: полноэкранные страницы категорий листаются вправо, сетка целиком внутри экрана. */
@Composable
fun StartMenuScreen(
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry) -> Unit,
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
                    Text("пуск", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "GSYNK " + BuildConfig.VERSION_NAME + " · GSYNKPRG · Premium Realtime Glass",
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
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight
                val contentWidthDp = (viewportWidth.value.toInt() - 20).coerceAtLeast(1)
                val gridHeightDp = (viewportHeight.value.toInt() - 50).coerceAtLeast(1)
                val columns = startColumnsForWidth(contentWidthDp, desiredCell)
                val targetRows = startRowsForHeight(gridHeightDp, desiredCell)
                val pages = remember(groups, columns, targetRows) {
                    groups.flatMap { groupData ->
                        val chunks = startPageChunks(groupData.entries, columns, targetRows)
                        chunks.mapIndexed { pageIndex, pageEntries ->
                            StartPageData(
                                key = groupData.key + "-page-" + pageIndex,
                                title = groupData.title,
                                hint = if (pageIndex == 0) groupData.hint else "продолжение: " + groupData.title,
                                entries = pageEntries,
                                pageIndex = pageIndex,
                                pageCount = chunks.size
                            )
                        }
                    }
                }
                val startListState = rememberLazyListState()
                val pageSnap = rememberSnapFlingBehavior(startListState)
                LazyRow(
                    state = startListState,
                    flingBehavior = pageSnap,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(pages, key = { _, page -> page.key }) { pageNumber, page ->
                        StartGroupPage(
                            page = page,
                            pageWidth = viewportWidth,
                            pageHeight = viewportHeight,
                            columns = columns,
                            pageNumber = pageNumber,
                            hasNextPage = pageNumber < pages.lastIndex,
                            favorites = favorites,
                            minimizedRoute = minimizedRoute,
                            liveLinesFor = liveLinesFor,
                            onOpen = onOpen,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartGroupPage(
    page: StartPageData,
    pageWidth: Dp,
    pageHeight: Dp,
    columns: Int,
    pageNumber: Int,
    hasNextPage: Boolean,
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit
) {
    val gap = START_GAP_DP.dp
    val entries = page.entries
    val placements = remember(entries, columns) { startPlacements(entries, columns) }
    val rows = max(1, placements.maxOfOrNull { it.y + it.h } ?: 1)
    val availableWidthDp = (pageWidth.value.toInt() - 20).coerceAtLeast(1)
    val availableGridHeightDp = (pageHeight.value.toInt() - 50).coerceAtLeast(1)
    val cellWidthDp = startCellWidthDp(availableWidthDp, columns)
    val cellWidth = cellWidthDp.dp
    val cellHeight = startCellHeightDp(availableGridHeightDp, rows, cellWidthDp).dp
    val gridWidth = cellWidth * columns + gap * (columns - 1).coerceAtLeast(0)
    val gridHeight = cellHeight * rows + gap * (rows - 1).coerceAtLeast(0)
    val shownTitle = buildString {
        append(page.title)
        if (page.pageCount > 1) append(" · " + (page.pageIndex + 1) + "/" + page.pageCount)
        if (hasNextPage) append("  →")
    }

    Column(
        Modifier
            .width(pageWidth)
            .height(pageHeight)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            shownTitle,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            page.hint,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(gridWidth).height(gridHeight)) {
                entries.forEachIndexed { index, entry ->
                    val place = placements[index]
                    StartTile(
                        entry = entry,
                        width = cellWidth * place.w + gap * (place.w - 1),
                        height = cellHeight * place.h + gap * (place.h - 1),
                        liveLines = liveLinesFor(entry),
                        favorite = entry.id in favorites,
                        minimized = entry.route == minimizedRoute,
                        index = pageNumber * 18 + index,
                        modifier = Modifier.offset(x = (cellWidth + gap) * place.x, y = (cellHeight + gap) * place.y),
                        onClick = { onOpen(entry) },
                        onLongClick = { onToggleFavorite(entry) }
                    )
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
    onClick: () -> Unit,
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
    val pressScale by animateFloatAsState(
        if (pressed) 0.962f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "tilePressScale"
    )
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
                scaleX = enterScale * pressScale
                scaleY = enterScale * pressScale
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
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
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
                            .clickable { onMinimize() },
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
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            var visible by remember(title) { mutableStateOf(false) }
            LaunchedEffect(title) {
                visible = false
                delay(16L)
                visible = true
            }
            val contentAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "shellAlpha")
            val contentScale by animateFloatAsState(
                if (visible) 1f else 0.985f,
                spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
                label = "shellScale"
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = contentAlpha
                        scaleX = contentScale
                        scaleY = contentScale
                        translationY = (1f - contentAlpha) * 14f
                    }
            ) {
                content()
            }
        }
    }
}
