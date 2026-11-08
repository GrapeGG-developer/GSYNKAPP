package com.gsynk.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.BuildConfig
import com.gsynk.app.R
import com.gsynk.app.core.Script
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * GSYNK Script UI / Monolith.
 *
 * Каркас экрана не меняется никогда: состояние сверху, глава, активная строка,
 * нумерованные строки функций и контекстные действия снизу. Категории меняют
 * только содержимое «грани» монолита. Всё плоское: 2D-градиенты, чёткая палитра,
 * асимметричные скругления, без стекла, размытия и 3D-перспективы.
 */
private data class ScriptChapter(
    val key: String,
    val title: String,
    val hint: String,
    val iconRes: Int,
    val entries: List<Catalog.Entry>
)

private enum class ScriptAxis { NONE, HORIZONTAL, VERTICAL }

private val ScriptSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

@Composable
fun ScriptStartScreen(
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val chapters = remember(favorites) { scriptChapters(favorites) }
    val chapterCount = chapters.size.coerceAtLeast(1)
    val itemPhase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var selectedChapter by remember { mutableIntStateOf(0) }
    var selectedLine by remember { mutableIntStateOf(0) }
    var focusedOrigin by remember { mutableStateOf<LiquidWindowOrigin?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val chapter = chapters.getOrNull(selectedChapter.coerceIn(0, chapters.lastIndex.coerceAtLeast(0)))
    val entries = chapter?.entries.orEmpty()
    val entryCount = entries.size.coerceAtLeast(1)
    val focusedEntry = entries.getOrNull(selectedLine.coerceIn(0, entries.lastIndex.coerceAtLeast(0)))
    val itemStepPx = with(LocalDensity.current) { Script.LINE_STEP_DP.dp.toPx() }

    fun feedback(long: Boolean = false) {
        UiTweaks.tap(context, long = long)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    suspend fun settleItem(velocityIndexPerSecond: Float) {
        val target = Script.settleTarget(itemPhase.value, velocityIndexPerSecond)
        selectedLine = Script.wrap(target.roundToInt(), entryCount)
        itemPhase.animateTo(target, animationSpec = ScriptSpring)
    }

    fun moveChapter(delta: Int) {
        feedback()
        selectedChapter = Script.wrap(selectedChapter + delta, chapterCount)
    }

    fun selectChapter(index: Int) {
        feedback()
        selectedChapter = index.coerceIn(0, chapterCount - 1)
    }

    fun moveLine(delta: Int) {
        feedback()
        scope.launch {
            val current = Script.wrap(itemPhase.value.roundToInt(), entryCount)
            val index = Script.wrap(current + delta, entryCount)
            val target = Script.nearestPhase(itemPhase.value, index, entryCount)
            selectedLine = index
            itemPhase.animateTo(target, animationSpec = ScriptSpring)
        }
    }

    fun activateLine(index: Int, entry: Catalog.Entry, origin: LiquidWindowOrigin?) {
        if (index == selectedLine) {
            feedback()
            onOpen(entry, origin)
        } else {
            feedback()
            scope.launch {
                val target = Script.nearestPhase(itemPhase.value, index, entryCount)
                selectedLine = index
                itemPhase.animateTo(target, animationSpec = ScriptSpring)
            }
        }
    }

    fun openFocused() {
        val entry = focusedEntry ?: return
        feedback()
        onOpen(entry, focusedOrigin)
    }

    fun toggleFavorite() {
        val entry = focusedEntry ?: return
        feedback(long = true)
        onToggleFavorite(entry)
    }

    LaunchedEffect(selectedChapter) {
        selectedLine = 0
        focusedOrigin = null
        itemPhase.animateTo(0f, animationSpec = ScriptSpring)
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        ScriptBackground(Modifier.matchParentSize())
        ScriptShards(Modifier.matchParentSize(), accent = ScriptPalette.Accent)
        ScriptDepthBackdrop(
            Modifier.matchParentSize(),
            scrollPixels = selectedChapter * 120f + itemPhase.value * 18f
        )
        ScriptFocusBar(Modifier.matchParentSize(), strength = 0.06f)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScriptMonolithHeader(
                minimizedCount = minimizedRoutes.size,
                onCloseHome = onCloseHome
            )
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = selectedChapter,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) },
                label = "scriptChapter"
            ) { chapterIndex ->
                ScriptChapterHeader(
                    chapter = chapters.getOrNull(chapterIndex),
                    index = chapterIndex + 1,
                    total = chapters.size
                )
            }
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = focusedEntry,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "scriptActiveLine"
            ) { entry ->
                if (entry != null) {
                    ScriptActiveLine(
                        entry = entry,
                        liveLine = liveLinesFor(entry).firstOrNull { it.isNotBlank() }?.trim(),
                        onOpen = { openFocused() }
                    )
                } else {
                    ScriptSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp),
                        shape = ScriptShapes.isoPanel,
                        focused = true
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("в этой главе пока нет строк", color = ScriptPalette.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .scriptMotionGestures(
                        itemStepPx = itemStepPx,
                        itemPhase = itemPhase,
                        motionScope = scope,
                        onChapterMove = { direction -> moveChapter(direction) },
                        onItemSettle = { velocity -> scope.launch { settleItem(velocity) } },
                        onDragChange = { dragging = it }
                    )
            ) {
                ScriptSurface(
                    modifier = Modifier.matchParentSize(),
                    shape = ScriptShapes.isoPanel,
                    focused = false
                ) {
                    ScriptLineColumn(
                        entries = entries,
                        phase = itemPhase.value,
                        selectedIndex = selectedLine,
                        favorites = favorites,
                        minimizedRoutes = minimizedRoutes,
                        liveLinesFor = liveLinesFor,
                        onActivate = { index, entry, origin -> activateLine(index, entry, origin) },
                        onToggleFavorite = { toggleFavorite() },
                        onFocusOrigin = { origin -> focusedOrigin = origin }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            ScriptControlBar(
                onLeft = { moveChapter(-1) },
                onRight = { moveChapter(1) },
                onUp = { moveLine(-1) },
                onDown = { moveLine(1) },
                onOpen = { openFocused() },
                onFavorite = { toggleFavorite() }
            )
            Text(
                text = if (dragging) "тяните строки · отпустите с инерцией" else "← → главы · ↑ ↓ строки · касание или OK · ★ закрепить",
                color = ScriptPalette.Muted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private fun scriptChapters(favorites: Set<String>): List<ScriptChapter> = buildList {
    if (favorites.isNotEmpty()) {
        val favoriteEntries = Catalog.ALL
            .filter { it.id in favorites }
            .sortedByDescending { it.hot }
        if (favoriteEntries.isNotEmpty()) {
            add(
                ScriptChapter(
                    key = "favorites",
                    title = "моё",
                    hint = "закреплённые строки — ★ добавляет или убирает",
                    iconRes = R.drawable.ic_bolt,
                    entries = favoriteEntries
                )
            )
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
            add(
                ScriptChapter(
                    key = group.name,
                    title = group.title,
                    hint = group.hint,
                    iconRes = scriptGroupIcon(group),
                    entries = entries
                )
            )
        }
    }
}

private fun scriptGroupIcon(group: Catalog.Group): Int = when (group) {
    Catalog.Group.CONNECT -> R.drawable.ic_devices
    Catalog.Group.SCHEDULE -> R.drawable.ic_schedule
    Catalog.Group.SCREEN -> R.drawable.ic_brightness
    Catalog.Group.HARDWARE -> R.drawable.ic_devices
    Catalog.Group.LOAD -> R.drawable.ic_bolt
    Catalog.Group.AI -> R.drawable.ic_shield
    Catalog.Group.CRYPTO -> R.drawable.ic_copy
    Catalog.Group.ANON -> R.drawable.ic_shield
    Catalog.Group.SYSTEM -> R.drawable.ic_tune
    Catalog.Group.FUN -> R.drawable.ic_volume
}

/** Один общий 2D-детектор: непрерывные строки, дискретные главы, инерция без 3D. */
private fun Modifier.scriptMotionGestures(
    itemStepPx: Float,
    itemPhase: Animatable<Float, AnimationVector1D>,
    motionScope: CoroutineScope,
    onChapterMove: (Int) -> Unit,
    onItemSettle: (Float) -> Unit,
    onDragChange: (Boolean) -> Unit
): Modifier = pointerInput(itemStepPx) {
    val safeItemStep = itemStepPx.coerceAtLeast(1f)
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var previous = down.position
        var lastTime = down.uptimeMillis
        var totalX = 0f
        var totalY = 0f
        var axis = ScriptAxis.NONE
        var gestureDragging = false
        var chapterMoved = false
        var lastItemVelocity = 0f

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) break
            val current = change.position
            val dx = current.x - previous.x
            val dy = current.y - previous.y
            totalX += dx
            totalY += dy
            val now = change.uptimeMillis
            val dt = ((now - lastTime) / 1000f).coerceIn(0.008f, 0.05f)
            lastTime = now

            if (!gestureDragging && (abs(totalX) > slop || abs(totalY) > slop)) {
                gestureDragging = true
                axis = if (abs(totalX) >= abs(totalY)) ScriptAxis.HORIZONTAL else ScriptAxis.VERTICAL
                onDragChange(true)
                motionScope.launch { itemPhase.stop() }
            }
            if (gestureDragging) {
                change.consume()
                when (axis) {
                    ScriptAxis.HORIZONTAL -> {
                        if (!chapterMoved && abs(totalX) > slop * 3f) {
                            onChapterMove(if (totalX < 0f) 1 else -1)
                            chapterMoved = true
                        }
                    }
                    ScriptAxis.VERTICAL -> {
                        val phaseDelta = -dy / safeItemStep
                        motionScope.launch { itemPhase.snapTo(itemPhase.value + phaseDelta) }
                        val instant = (phaseDelta / dt).coerceIn(-12f, 12f)
                        lastItemVelocity = lastItemVelocity * 0.55f + instant * 0.45f
                    }
                    ScriptAxis.NONE -> Unit
                }
            }
            previous = current
        }

        if (gestureDragging) {
            onDragChange(false)
            if (axis == ScriptAxis.VERTICAL) onItemSettle(lastItemVelocity)
        }
    }
}

@Composable
private fun ScriptMonolithHeader(
    minimizedCount: Int,
    onCloseHome: () -> Unit
) {
    ScriptSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = ScriptShapes.isoPanel,
        focused = false
    ) {
        ScriptTexture(Modifier.matchParentSize(), accent = ScriptPalette.Accent, seed = 600, density = 0.22f)
        ScriptCorners(Modifier.matchParentSize(), accent = ScriptPalette.Accent2, strength = 0.22f)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.grapescript_logo),
                contentDescription = "Логотип Grape Script",
                modifier = Modifier.height(28.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("GSYNK", color = ScriptPalette.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(
                    "SCRIPT UI · " + BuildConfig.VERSION_NAME + " EXP",
                    color = ScriptPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                )
            }
            if (minimizedCount > 0) {
                Box(
                    Modifier
                        .clip(ScriptShapes.isoChip)
                        .background(ScriptPalette.SurfaceAlt)
                        .border(1.dp, ScriptPalette.Accent2, ScriptShapes.isoChip)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(minimizedCount.toString() + " окн.", color = ScriptPalette.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(7.dp))
            }
            ScriptControlButton(
                text = "⌂",
                modifier = Modifier.size(34.dp),
                onClick = onCloseHome
            )
        }
    }
}

@Composable
private fun ScriptChapterHeader(
    chapter: ScriptChapter?,
    index: Int,
    total: Int
) {
    ScriptSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        shape = ScriptShapes.isoCard,
        focused = true
    ) {
        ScriptEdgeMark(Modifier.matchParentSize(), accent = ScriptPalette.Accent2, seed = index)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScriptIcon(
                iconRes = chapter?.iconRes ?: R.drawable.ic_tune,
                modifier = Modifier.size(40.dp),
                active = true
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (index).toString().padStart(2, '0') + " / " + total.toString().padStart(2, '0') + " · глава",
                    color = ScriptPalette.Accent2,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    chapter?.title?.lowercase().orEmpty(),
                    color = ScriptPalette.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    chapter?.hint.orEmpty().lowercase(),
                    color = ScriptPalette.Muted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ScriptActiveLine(
    entry: Catalog.Entry,
    liveLine: String?,
    onOpen: () -> Unit
) {
    ScriptSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        shape = ScriptShapes.isoPanel,
        focused = true
    ) {
        ScriptTexture(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode(), density = 0.30f)
        ScriptCorners(Modifier.matchParentSize(), accent = ScriptPalette.Accent3, strength = 0.26f)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScriptGlyph(
                modifier = Modifier.size(62.dp),
                accent = entry.color,
                iconRes = entry.iconRes,
                seed = entry.id.hashCode(),
                active = true,
                iconScale = 0.52f
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "АКТИВНАЯ СТРОКА",
                    color = ScriptPalette.Accent3,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    entry.title.lowercase(),
                    color = ScriptPalette.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    liveLine?.takeIf { it.isNotBlank() } ?: "готова к открытию",
                    color = ScriptPalette.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ScriptControlButton(
                text = "ОТКРЫТЬ",
                modifier = Modifier
                    .width(92.dp)
                    .height(40.dp),
                onClick = onOpen
            )
        }
    }
}

@Composable
private fun ScriptLineColumn(
    entries: List<Catalog.Entry>,
    phase: Float,
    selectedIndex: Int,
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onActivate: (Int, Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: () -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val localDensity = LocalDensity.current
        val rowHeight = minOf(Script.LINE_STEP_DP.dp, maxHeight / (Script.VISIBLE_LINES + 0.6f))
        val rowStepPx = with(localDensity) { (rowHeight + 5.dp).toPx() }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("в этой главе пока нет строк", color = ScriptPalette.Muted, fontSize = 12.sp)
            }
            return@BoxWithConstraints
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            entries.forEachIndexed { index, entry ->
                val distance = Script.cyclicDistance(index, phase, entries.size)
                if (abs(distance) <= 3.6f) {
                    ScriptLineRow(
                        entry = entry,
                        index = index,
                        distance = distance,
                        focused = index == selectedIndex,
                        rowHeight = rowHeight,
                        rowStepPx = rowStepPx,
                        liveLine = liveLinesFor(entry).firstOrNull { it.isNotBlank() }?.trim(),
                        favorite = entry.id in favorites,
                        minimized = entry.route in minimizedRoutes,
                        onActivate = onActivate,
                        onToggleFavorite = onToggleFavorite,
                        onFocusOrigin = onFocusOrigin
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScriptLineRow(
    entry: Catalog.Entry,
    index: Int,
    distance: Float,
    focused: Boolean,
    rowHeight: Dp,
    rowStepPx: Float,
    liveLine: String?,
    favorite: Boolean,
    minimized: Boolean,
    onActivate: (Int, Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: () -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    val context = LocalContext.current
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val badge = when {
        minimized -> "активно"
        entry.fresh -> "ново"
        favorite -> "моё"
        else -> null
    }
    val scale = Script.lineScale(distance)
    val layerAlpha = Script.lineAlpha(distance)

    LaunchedEffect(focused, launchOrigin) {
        if (focused) onFocusOrigin(launchOrigin)
    }

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.98f)
            .height(rowHeight)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .graphicsLayer {
                translationY = Script.lineTranslationY(distance) + distance * rowStepPx
                scaleX = scale
                scaleY = scale
                alpha = layerAlpha
            }
    ) {
        ScriptSurface(
            modifier = Modifier.matchParentSize(),
            shape = ScriptShapes.isoCard,
            focused = focused
        ) {
            ScriptEdgeMark(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode())
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onActivate(index, entry, launchOrigin) },
                        onLongClick = {
                            UiTweaks.tap(context, long = true)
                            onToggleFavorite()
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    color = if (focused) ScriptPalette.Accent2 else ScriptPalette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(26.dp)
                )
                ScriptGlyph(
                    modifier = Modifier.size(rowHeight * 0.58f),
                    accent = entry.color,
                    iconRes = entry.iconRes,
                    seed = entry.id.hashCode(),
                    active = focused,
                    iconScale = 0.50f
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title.lowercase(),
                        color = if (focused) ScriptPalette.White else ScriptPalette.Text,
                        fontSize = if (focused) 15.sp else 12.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (focused && !liveLine.isNullOrBlank()) {
                        Text(
                            liveLine,
                            color = ScriptPalette.Muted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(ScriptShapes.isoChip)
                            .background(ScriptPalette.SurfaceAlt)
                            .border(1.dp, ScriptPalette.Accent, ScriptShapes.isoChip)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = ScriptPalette.Accent2, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptControlBar(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScriptControlButton("←", Modifier.size(38.dp), onClick = onLeft)
        ScriptControlButton("→", Modifier.size(38.dp), onClick = onRight)
        ScriptControlButton("↑", Modifier.size(38.dp), onClick = onUp)
        ScriptControlButton("↓", Modifier.size(38.dp), onClick = onDown)
        ScriptControlButton("★", Modifier.size(38.dp), accent = ScriptPalette.Accent3, onClick = onFavorite)
        ScriptControlButton(
            "OK",
            Modifier
                .weight(1f)
                .height(38.dp),
            onClick = onOpen
        )
    }
}
