package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Пустой главный экран: только GSYNK и подсказка про портрет.
 * Касание где угодно открывает Пуск — как рабочий стол Windows 8 перед Start screen.
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
                        .background(Color(0xFF4C1D95).copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RectangleShape)
                        .clickable { onResumeMinimized() }
                        .padding(10.dp)
                ) {
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
                    "телефон обязательно нужно повернуть в портретный режим",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "коснитесь экрана, чтобы запустить меню «Пуск»",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

/** Windows 8 Start: группы идут вправо, заголовок категории стоит над её плитками. */
@Composable
fun StartMenuScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    val groups = remember(query, favorites) {
        buildList {
            if (query.isBlank() && favorites.isNotEmpty()) {
                val favoriteEntries = Catalog.ALL
                    .filter { it.id in favorites }
                    .sortedByDescending { it.hot }
                if (favoriteEntries.isNotEmpty()) {
                    add(StartGroupData("favorites", "моё", "закреплённые долгим нажатием плитки", favoriteEntries))
                }
            }
            Catalog.Group.entries.forEach { group ->
                val entries = Catalog.ALL
                    .filter { it.group == group && it.matches(query) }
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
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF170326), Color(0xFF2A0B45), Color(0xFF12021E))
                )
            )
            val step = 54.dp.toPx()
            var y = -step
            var row = 0
            while (y < size.height + step) {
                var x = if (row % 2 == 0) -step * 0.3f else step * 0.2f
                while (x < size.width + step) {
                    drawCircle(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.075f),
                        radius = step * 0.28f,
                        center = Offset(x, y),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.026f),
                        topLeft = Offset(x - step * 0.14f, y - step * 0.14f),
                        size = androidx.compose.ui.geometry.Size(step * 0.28f, step * 0.28f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    x += step
                }
                y += step
                row++
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF7C3AED).copy(alpha = 0.28f), Color.Transparent)
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.82f, size.height * 0.12f)
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("пуск", color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    Text("GSYNK 5.2 · все плитки", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RectangleShape)
                        .background(Color.White.copy(alpha = 0.09f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RectangleShape)
                        .clickable { onCloseHome() },
                    contentAlignment = Alignment.Center
                ) { Text("⌂", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Light) }
            }
            GrapeTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "поиск в пуске: шифры, школа, nfc, нагрузка…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        "ничего не нашлось — попробуйте «шифр», «школа», «настройки» или «новости»",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 28.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    items(groups, key = { it.key }) { groupData ->
                        StartGroup(
                            title = groupData.title,
                            hint = groupData.hint,
                            entries = groupData.entries,
                            favorites = favorites,
                            minimizedRoute = minimizedRoute,
                            liveLinesFor = liveLinesFor,
                            onOpen = onOpen,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
            Text(
                "листайте вправо →   ·   долгое нажатие — «моё»",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun StartGroup(
    title: String,
    hint: String,
    entries: List<Catalog.Entry>,
    favorites: Set<String>,
    minimizedRoute: String?,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit
) {
    val columns = 5
    val gap = 4.dp
    val placements = remember(entries) { startPlacements(entries, columns) }
    val rows = max(1, placements.maxOfOrNull { it.y + it.h } ?: 1)
    BoxWithConstraints {
        // Самая большая группа не должна уезжать за нижний край: на невысоких
        // телефонах ячейка автоматически становится меньше канонических 62 dp.
        val availableForGrid = (maxHeight - 62.dp).coerceAtLeast(180.dp)
        val cell = ((availableForGrid - gap * (rows - 1)) / rows).coerceIn(42.dp, 62.dp)
        val gridWidth = cell * columns + gap * (columns - 1)
        val gridHeight = cell * rows + gap * (rows - 1)
    Column(Modifier.width(gridWidth)) {
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Text(hint, color = Color.White.copy(alpha = 0.64f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
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
                    modifier = Modifier.offset(x = (cell + gap) * place.x, y = (cell + gap) * place.y),
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
    val shape = RoundedCornerShape(2.dp)
    val wide = width >= 104.dp
    val compact = height < 54.dp
    Box(
        modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(entry.color.copy(alpha = 0.97f), entry.color.copy(alpha = 0.68f))
                )
            )
            .border(
                if (minimized) 2.dp else 1.dp,
                if (minimized) Color.White else Color.White.copy(alpha = 0.18f),
                shape
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(if (compact) 5.dp else 7.dp)
    ) {
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ImageVector.vectorResource(entry.iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(if (compact) 13.dp else if (wide) 18.dp else 16.dp)
            )
            Spacer(Modifier.weight(1f))
            val badge = when {
                minimized -> "активно"
                entry.fresh -> "5.2"
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
                fontSize = if (compact) 9.sp else if (wide) 12.sp else 10.sp,
                lineHeight = if (compact) 10.sp else if (wide) 13.sp else 11.sp,
                fontWeight = FontWeight.Light,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (wide) {
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
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF170326))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(52.dp)
                .background(Color(0xFF1E0733))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RectangleShape)
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 40.dp)
                    .clip(RectangleShape)
                    .clickable { onMinimize() },
                contentAlignment = Alignment.Center
            ) {
                Text("—", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
            Text(
                "активный левый угол",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title.lowercase(),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("GSYNK · metro-приложение", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp)
            }
            Box(
                Modifier
                    .size(width = 48.dp, height = 40.dp)
                    .clip(RectangleShape)
                    .background(Color(0xFFE81123).copy(alpha = 0.92f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
