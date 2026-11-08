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
import com.gsynk.app.core.Xmb
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * GREAL XMB Motion — «Пуск» в стиле XrossMediaBar с настоящей инерцией.
 *
 * Один общий gesture-детектор ведёт непрерывную фазу категорий и фазу пунктов.
 * Элементы считают циклическое расстояние до фокуса, поэтому ряд едет под
 * пальцем, после отпускания добавляет инерцию и пружинно доводится до целого
 * индекса. Явные кнопки ←/→/↑/↓ и «открыть» дублируют жесты для точного управления.
 */
private data class XmbCategory(
    val key: String,
    val title: String,
    val hint: String,
    val iconRes: Int,
    val entries: List<Catalog.Entry>
)

private enum class XmbAxis { NONE, HORIZONTAL, VERTICAL }

private val XmbSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

@Composable
fun XmbStartScreen(
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val accent = UiTweaks.accentStart
    val categories = remember(favorites) { xmbCategories(favorites) }
    val categoryCount = categories.size.coerceAtLeast(1)
    val categoryPhase = remember { Animatable(0f) }
    val itemPhase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableIntStateOf(0) }
    var selectedItem by remember { mutableIntStateOf(0) }
    var focusedOrigin by remember { mutableStateOf<LiquidWindowOrigin?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val category = categories.getOrNull(selectedCategory.coerceIn(0, categories.lastIndex.coerceAtLeast(0)))
    val entries = category?.entries.orEmpty()
    val entryCount = entries.size.coerceAtLeast(1)
    val focusedEntry = entries.getOrNull(selectedItem.coerceIn(0, entries.lastIndex.coerceAtLeast(0)))
    val categoryStepPx = with(LocalDensity.current) { Xmb.CATEGORY_STEP_DP.dp.toPx() }
    val itemStepPx = with(LocalDensity.current) { 68.dp.toPx() }

    fun feedback(long: Boolean = false) {
        UiTweaks.tap(context, long = long)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    suspend fun settleCategory(velocityIndexPerSecond: Float) {
        val target = Xmb.settleTarget(categoryPhase.value, velocityIndexPerSecond)
        val index = Xmb.wrap(target.roundToInt(), categoryCount)
        if (index != selectedCategory) {
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            scope.launch { itemPhase.animateTo(0f, animationSpec = XmbSpring) }
        }
        categoryPhase.animateTo(target, animationSpec = XmbSpring)
    }

    suspend fun settleItem(velocityIndexPerSecond: Float) {
        val target = Xmb.settleTarget(itemPhase.value, velocityIndexPerSecond)
        selectedItem = Xmb.wrap(target.roundToInt(), entryCount)
        itemPhase.animateTo(target, animationSpec = XmbSpring)
    }

    fun moveCategory(delta: Int) {
        feedback()
        scope.launch {
            val current = Xmb.wrap(categoryPhase.value.roundToInt(), categoryCount)
            val index = Xmb.wrap(current + delta, categoryCount)
            val target = Xmb.nearestPhase(categoryPhase.value, index, categoryCount)
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            launch { itemPhase.animateTo(0f, animationSpec = XmbSpring) }
            categoryPhase.animateTo(target, animationSpec = XmbSpring)
        }
    }

    fun selectCategory(index: Int) {
        feedback()
        scope.launch {
            val target = Xmb.nearestPhase(categoryPhase.value, index, categoryCount)
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            launch { itemPhase.animateTo(0f, animationSpec = XmbSpring) }
            categoryPhase.animateTo(target, animationSpec = XmbSpring)
        }
    }

    fun moveItem(delta: Int) {
        feedback()
        scope.launch {
            val current = Xmb.wrap(itemPhase.value.roundToInt(), entryCount)
            val index = Xmb.wrap(current + delta, entryCount)
            val target = Xmb.nearestPhase(itemPhase.value, index, entryCount)
            selectedItem = index
            itemPhase.animateTo(target, animationSpec = XmbSpring)
        }
    }

    fun activateItem(index: Int, entry: Catalog.Entry, origin: LiquidWindowOrigin?) {
        if (index == selectedItem) {
            feedback()
            onOpen(entry, origin)
        } else {
            feedback()
            scope.launch {
                val target = Xmb.nearestPhase(itemPhase.value, index, entryCount)
                selectedItem = index
                itemPhase.animateTo(target, animationSpec = XmbSpring)
            }
        }
    }

    fun openFocused() {
        val entry = focusedEntry ?: return
        feedback()
        onOpen(entry, focusedOrigin)
    }

    AnimatedGrapeBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            XmbHeader(
                minimizedCount = minimizedRoutes.size,
                accent = accent,
                onCloseHome = onCloseHome
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .xmbMotionGestures(
                        categoryStepPx = categoryStepPx,
                        itemStepPx = itemStepPx,
                        categoryPhase = categoryPhase,
                        itemPhase = itemPhase,
                        motionScope = scope,
                        onCategorySettle = { velocity -> scope.launch { settleCategory(velocity) } },
                        onItemSettle = { velocity -> scope.launch { settleItem(velocity) } },
                        onDragChange = { dragging = it }
                    )
            ) {
                GrealDepthBackdrop(
                    Modifier.matchParentSize(),
                    scrollPixels = categoryPhase.value * 140f + itemPhase.value * 18f
                )
                GrealParallaxShards(Modifier.matchParentSize(), accent = accent)
                XmbWaveField(
                    Modifier.matchParentSize(),
                    accent = accent,
                    motion = if (dragging) 1f else 0f
                )
                XmbFocusBeam(Modifier.matchParentSize(), accent = accent)

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    XmbCategoryRail(
                        categories = categories,
                        phase = categoryPhase.value,
                        selectedIndex = selectedCategory,
                        accent = accent,
                        onSelect = { index -> selectCategory(index) }
                    )
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = category?.hint.orEmpty().lowercase(),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "xmbCategoryHint"
                    ) { hint ->
                        Text(
                            text = hint,
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        XmbItemColumn(
                            entries = entries,
                            phase = itemPhase.value,
                            selectedIndex = selectedItem,
                            favorites = favorites,
                            minimizedRoutes = minimizedRoutes,
                            liveLinesFor = liveLinesFor,
                            accent = accent,
                            onActivate = { index, entry, origin -> activateItem(index, entry, origin) },
                            onToggleFavorite = onToggleFavorite,
                            onFocusOrigin = { origin -> focusedOrigin = origin }
                        )
                    }
                    XmbControlBar(
                        accent = accent,
                        onLeft = { moveCategory(-1) },
                        onRight = { moveCategory(1) },
                        onUp = { moveItem(-1) },
                        onDown = { moveItem(1) },
                        onOpen = { openFocused() }
                    )
                    Text(
                        text = if (dragging) "тяните · отпустите с инерцией" else "← → категории · ↑ ↓ функции · коснитесь или «открыть»",
                        color = Color.White.copy(alpha = 0.44f),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun xmbCategories(favorites: Set<String>): List<XmbCategory> = buildList {
    if (favorites.isNotEmpty()) {
        val favoriteEntries = Catalog.ALL
            .filter { it.id in favorites }
            .sortedByDescending { it.hot }
        if (favoriteEntries.isNotEmpty()) {
            add(
                XmbCategory(
                    key = "favorites",
                    title = "моё",
                    hint = "закреплённые функции — долгое нажатие добавляет или убирает",
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
                XmbCategory(
                    key = group.name,
                    title = group.title,
                    hint = group.hint,
                    iconRes = xmbGroupIcon(group),
                    entries = entries
                )
            )
        }
    }
}

private fun xmbGroupIcon(group: Catalog.Group): Int = when (group) {
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

/** Один общий детектор: непрерывный drag, инерция, без конфликта с tap/long-press. */
private fun Modifier.xmbMotionGestures(
    categoryStepPx: Float,
    itemStepPx: Float,
    categoryPhase: Animatable<Float, AnimationVector1D>,
    itemPhase: Animatable<Float, AnimationVector1D>,
    motionScope: CoroutineScope,
    onCategorySettle: (Float) -> Unit,
    onItemSettle: (Float) -> Unit,
    onDragChange: (Boolean) -> Unit
): Modifier = pointerInput(categoryStepPx, itemStepPx) {
    val safeCategoryStep = categoryStepPx.coerceAtLeast(1f)
    val safeItemStep = itemStepPx.coerceAtLeast(1f)
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var previous = down.position
        var lastTime = down.uptimeMillis
        var totalX = 0f
        var totalY = 0f
        var axis = XmbAxis.NONE
        var gestureDragging = false
        var lastCategoryVelocity = 0f
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
                axis = if (abs(totalX) >= abs(totalY)) XmbAxis.HORIZONTAL else XmbAxis.VERTICAL
                onDragChange(true)
                motionScope.launch {
                    categoryPhase.stop()
                    itemPhase.stop()
                }
            }
            if (gestureDragging) {
                change.consume()
                when (axis) {
                    XmbAxis.HORIZONTAL -> {
                        val phaseDelta = -dx / safeCategoryStep
                        motionScope.launch {
                            categoryPhase.snapTo(categoryPhase.value + phaseDelta)
                        }
                        val instant = (phaseDelta / dt).coerceIn(-12f, 12f)
                        lastCategoryVelocity = lastCategoryVelocity * 0.55f + instant * 0.45f
                    }
                    XmbAxis.VERTICAL -> {
                        val phaseDelta = -dy / safeItemStep
                        motionScope.launch {
                            itemPhase.snapTo(itemPhase.value + phaseDelta)
                        }
                        val instant = (phaseDelta / dt).coerceIn(-12f, 12f)
                        lastItemVelocity = lastItemVelocity * 0.55f + instant * 0.45f
                    }
                    XmbAxis.NONE -> Unit
                }
            }
            previous = current
        }

        if (gestureDragging) {
            onDragChange(false)
            when (axis) {
                XmbAxis.HORIZONTAL -> onCategorySettle(lastCategoryVelocity)
                XmbAxis.VERTICAL -> onItemSettle(lastItemVelocity)
                XmbAxis.NONE -> Unit
            }
        }
    }
}

@Composable
private fun XmbHeader(
    minimizedCount: Int,
    accent: Color,
    onCloseHome: () -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        accent.copy(alpha = 0.28f),
                        Color(0xFF22D3EE).copy(alpha = 0.12f),
                        Color.Black.copy(alpha = 0.18f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    ) {
        GrealTexture(Modifier.matchParentSize(), accent = accent, seed = 581, density = 0.30f)
        PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = 581)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.grapescript_logo),
                contentDescription = "Логотип Grape Script",
                modifier = Modifier.height(27.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(7.dp))
            Text("GSYNK", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "пуск · " + BuildConfig.VERSION_NAME + " EXP · GREAL XMB Motion",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (minimizedCount > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.13f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(minimizedCount.toString() + " окн.", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(7.dp))
            }
            GrealControlOrb(
                text = "⌂",
                accent = accent,
                modifier = Modifier.size(32.dp),
                onClick = onCloseHome
            )
        }
    }
}

@Composable
private fun XmbCategoryRail(
    categories: List<XmbCategory>,
    phase: Float,
    selectedIndex: Int,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(122.dp)
    ) {
        val localDensity = LocalDensity.current
        val stepPx = with(localDensity) { Xmb.CATEGORY_STEP_DP.dp.toPx() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            categories.forEachIndexed { index, category ->
                val distance = Xmb.cyclicDistance(index, phase, categories.size)
                if (abs(distance) <= 3.25f) {
                    val focused = index == selectedIndex
                    val scale = Xmb.categoryScale(distance)
                    val layerAlpha = Xmb.categoryAlpha(distance)
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .width(Xmb.CATEGORY_STEP_DP.dp)
                            .graphicsLayer {
                                translationX = distance * stepPx
                                scaleX = scale
                                scaleY = scale
                                this.alpha = layerAlpha
                                rotationY = Xmb.categoryRotationY(distance)
                                cameraDistance = 8f * density
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    UiTweaks.tap(context)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(index)
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                            if (focused) {
                                GrealInfiniteOrbit(
                                    Modifier.matchParentSize(),
                                    accent = accent,
                                    seed = category.key.hashCode(),
                                    active = true
                                )
                            }
                            GrealOrbFace(
                                modifier = Modifier.size(if (focused) 52.dp else 44.dp),
                                accent = if (focused) accent else accent.copy(alpha = 0.72f),
                                iconRes = category.iconRes,
                                seed = category.key.hashCode(),
                                active = focused,
                                iconScale = 0.50f
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = category.title.lowercase(),
                            color = if (focused) TextPrimary else Color.White.copy(alpha = 0.64f),
                            fontSize = if (focused) 11.sp else 9.sp,
                            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Light,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XmbItemColumn(
    entries: List<Catalog.Entry>,
    phase: Float,
    selectedIndex: Int,
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    accent: Color,
    onActivate: (Int, Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = minOf(68.dp, maxHeight / (Xmb.VISIBLE_ITEMS + 0.6f))
        val localDensity = LocalDensity.current
        val rowStepPx = with(localDensity) { (rowHeight + 6.dp).toPx() }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("в этой категории пока нет функций", color = TextDim, fontSize = 12.sp)
            }
            return@BoxWithConstraints
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            entries.forEachIndexed { index, entry ->
                val distance = Xmb.cyclicDistance(index, phase, entries.size)
                if (abs(distance) <= 3.6f) {
                    XmbItemRow(
                        entry = entry,
                        index = index,
                        distance = distance,
                        focused = index == selectedIndex,
                        rowHeight = rowHeight,
                        rowStepPx = rowStepPx,
                        liveLine = liveLinesFor(entry).firstOrNull { it.isNotBlank() }?.trim(),
                        favorite = entry.id in favorites,
                        minimized = entry.route in minimizedRoutes,
                        accent = accent,
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
private fun BoxScope.XmbItemRow(
    entry: Catalog.Entry,
    index: Int,
    distance: Float,
    focused: Boolean,
    rowHeight: Dp,
    rowStepPx: Float,
    liveLine: String?,
    favorite: Boolean,
    minimized: Boolean,
    accent: Color,
    onActivate: (Int, Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    val context = LocalContext.current
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val scale = Xmb.itemScale(distance)
    val layerAlpha = Xmb.itemAlpha(distance)
    val badge = when {
        minimized -> "активно"
        entry.fresh -> "ново"
        favorite -> "моё"
        else -> null
    }
    val shape = RoundedCornerShape(18.dp)

    LaunchedEffect(focused, launchOrigin) {
        if (focused) onFocusOrigin(launchOrigin)
    }

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.94f)
            .height(rowHeight)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .graphicsLayer {
                translationY = distance * rowStepPx
                scaleX = scale
                scaleY = scale
                this.alpha = layerAlpha
                translationX = Xmb.itemTranslationX(distance) * density
                rotationX = Xmb.itemRotationX(distance)
                cameraDistance = 9f * density
                shadowElevation = if (focused) 18f else 4f
                ambientShadowColor = accent.copy(alpha = 0.24f)
                spotShadowColor = entry.color.copy(alpha = 0.30f)
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = if (focused) 0.16f else 0.07f),
                        entry.color.copy(alpha = if (focused) 0.22f else 0.10f),
                        Color.Black.copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.14f),
                shape
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onActivate(index, entry, launchOrigin) },
                onLongClick = {
                    UiTweaks.tap(context, long = true)
                    onToggleFavorite(entry)
                }
            )
    ) {
        if (focused) {
            GrealTexture(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode(), density = 0.28f)
            PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = entry.id.hashCode())
            GrealActiveCorners(Modifier.matchParentSize(), accent = accent, strength = 0.28f)
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(rowHeight * 0.66f),
                contentAlignment = Alignment.Center
            ) {
                if (focused) {
                    GrealInfiniteOrbit(
                        Modifier.matchParentSize(),
                        accent = entry.color,
                        seed = entry.id.hashCode(),
                        active = true
                    )
                }
                GrealOrbFace(
                    modifier = Modifier.size(rowHeight * 0.54f),
                    accent = entry.color,
                    iconRes = entry.iconRes,
                    seed = entry.id.hashCode(),
                    active = focused,
                    iconScale = 0.50f
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title.lowercase(),
                    color = if (focused) TextPrimary else Color.White.copy(alpha = 0.78f),
                    fontSize = if (focused) 16.sp else 12.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (focused && !liveLine.isNullOrBlank()) {
                    Text(
                        text = liveLine,
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badge != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.34f))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(badge, color = GrapeVioletSoft, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun XmbControlBar(
    accent: Color,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GrealControlOrb("←", accent, Modifier.size(38.dp), onClick = onLeft)
        GrealControlOrb("→", accent, Modifier.size(38.dp), onClick = onRight)
        GrealControlOrb("↑", accent, Modifier.size(38.dp), onClick = onUp)
        GrealControlOrb("↓", accent, Modifier.size(38.dp), onClick = onDown)
        SecondaryButton(
            text = "открыть",
            onClick = onOpen,
            modifier = Modifier.weight(1f)
        )
    }
}
