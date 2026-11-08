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
import com.gsynk.app.core.Blade
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * GSYNK Blades — «Пуск» в духе классического Xbox 360 Blade Dashboard.
 *
 * Горизонтальные вкладки-лезвия, вертикальный список функций внутри выбранного
 * лезвия, лаймовая рамка фокуса, белые иконки и зелёно-чёрный фон. Навигация
 * остаётся плавной: непрерывная фаза, инерция, пружинное доведение и крупные
 * экранные кнопки ←/→/↑/↓/OK.
 */
private data class BladeCategory(
    val key: String,
    val title: String,
    val hint: String,
    val iconRes: Int,
    val entries: List<Catalog.Entry>
)

private enum class BladeAxis { NONE, HORIZONTAL, VERTICAL }

private val BladeSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

@Composable
fun BladeStartScreen(
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val categories = remember(favorites) { bladeCategories(favorites) }
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
    val categoryStepPx = with(LocalDensity.current) { Blade.TAB_STEP_DP.dp.toPx() }
    val itemStepPx = with(LocalDensity.current) { Blade.ITEM_STEP_DP.dp.toPx() }

    fun feedback(long: Boolean = false) {
        UiTweaks.tap(context, long = long)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    suspend fun settleCategory(velocityIndexPerSecond: Float) {
        val target = Blade.settleTarget(categoryPhase.value, velocityIndexPerSecond)
        val index = Blade.wrap(target.roundToInt(), categoryCount)
        if (index != selectedCategory) {
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            scope.launch { itemPhase.animateTo(0f, animationSpec = BladeSpring) }
        }
        categoryPhase.animateTo(target, animationSpec = BladeSpring)
    }

    suspend fun settleItem(velocityIndexPerSecond: Float) {
        val target = Blade.settleTarget(itemPhase.value, velocityIndexPerSecond)
        selectedItem = Blade.wrap(target.roundToInt(), entryCount)
        itemPhase.animateTo(target, animationSpec = BladeSpring)
    }

    fun moveCategory(delta: Int) {
        feedback()
        scope.launch {
            val current = Blade.wrap(categoryPhase.value.roundToInt(), categoryCount)
            val index = Blade.wrap(current + delta, categoryCount)
            val target = Blade.nearestPhase(categoryPhase.value, index, categoryCount)
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            scope.launch { itemPhase.animateTo(0f, animationSpec = BladeSpring) }
            categoryPhase.animateTo(target, animationSpec = BladeSpring)
        }
    }

    fun selectCategory(index: Int) {
        feedback()
        scope.launch {
            val target = Blade.nearestPhase(categoryPhase.value, index, categoryCount)
            selectedCategory = index
            selectedItem = 0
            focusedOrigin = null
            scope.launch { itemPhase.animateTo(0f, animationSpec = BladeSpring) }
            categoryPhase.animateTo(target, animationSpec = BladeSpring)
        }
    }

    fun moveItem(delta: Int) {
        feedback()
        scope.launch {
            val current = Blade.wrap(itemPhase.value.roundToInt(), entryCount)
            val index = Blade.wrap(current + delta, entryCount)
            val target = Blade.nearestPhase(itemPhase.value, index, entryCount)
            selectedItem = index
            itemPhase.animateTo(target, animationSpec = BladeSpring)
        }
    }

    fun activateItem(index: Int, entry: Catalog.Entry, origin: LiquidWindowOrigin?) {
        if (index == selectedItem) {
            feedback()
            onOpen(entry, origin)
        } else {
            feedback()
            scope.launch {
                val target = Blade.nearestPhase(itemPhase.value, index, entryCount)
                selectedItem = index
                itemPhase.animateTo(target, animationSpec = BladeSpring)
            }
        }
    }

    fun openFocused() {
        val entry = focusedEntry ?: return
        feedback()
        onOpen(entry, focusedOrigin)
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BladeBackground(Modifier.matchParentSize())
        BladeFocusFrame(Modifier.matchParentSize(), strength = 0.10f)
        Column(Modifier.fillMaxSize()) {
            BladeHeader(
                minimizedCount = minimizedRoutes.size,
                onCloseHome = onCloseHome
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .bladeMotionGestures(
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
                BladeSurface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(14.dp),
                    focused = false
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BladeTabRail(
                            categories = categories,
                            phase = categoryPhase.value,
                            selectedIndex = selectedCategory,
                            onSelect = { index -> selectCategory(index) }
                        )
                        Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = category?.hint.orEmpty().lowercase(),
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
                            label = "bladeCategoryHint"
                        ) { hint ->
                            Text(
                                text = hint,
                                color = BladePalette.DimWhite,
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
                            BladeItemColumn(
                                entries = entries,
                                phase = itemPhase.value,
                                selectedIndex = selectedItem,
                                favorites = favorites,
                                minimizedRoutes = minimizedRoutes,
                                liveLinesFor = liveLinesFor,
                                onActivate = { index, entry, origin -> activateItem(index, entry, origin) },
                                onToggleFavorite = onToggleFavorite,
                                onFocusOrigin = { origin -> focusedOrigin = origin }
                            )
                        }
                        BladeControlBar(
                            onLeft = { moveCategory(-1) },
                            onRight = { moveCategory(1) },
                            onUp = { moveItem(-1) },
                            onDown = { moveItem(1) },
                            onOpen = { openFocused() }
                        )
                        Text(
                            text = if (dragging) "тяните лезвие · отпустите с инерцией" else "← → лезвия · ↑ ↓ функции · касание или OK",
                            color = BladePalette.DimWhite.copy(alpha = 0.62f),
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
}

private fun bladeCategories(favorites: Set<String>): List<BladeCategory> = buildList {
    if (favorites.isNotEmpty()) {
        val favoriteEntries = Catalog.ALL
            .filter { it.id in favorites }
            .sortedByDescending { it.hot }
        if (favoriteEntries.isNotEmpty()) {
            add(
                BladeCategory(
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
                BladeCategory(
                    key = group.name,
                    title = group.title,
                    hint = group.hint,
                    iconRes = bladeGroupIcon(group),
                    entries = entries
                )
            )
        }
    }
}

private fun bladeGroupIcon(group: Catalog.Group): Int = when (group) {
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
private fun Modifier.bladeMotionGestures(
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
        var axis = BladeAxis.NONE
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
                axis = if (abs(totalX) >= abs(totalY)) BladeAxis.HORIZONTAL else BladeAxis.VERTICAL
                onDragChange(true)
                motionScope.launch {
                    categoryPhase.stop()
                    itemPhase.stop()
                }
            }
            if (gestureDragging) {
                change.consume()
                when (axis) {
                    BladeAxis.HORIZONTAL -> {
                        val phaseDelta = -dx / safeCategoryStep
                        motionScope.launch { categoryPhase.snapTo(categoryPhase.value + phaseDelta) }
                        val instant = (phaseDelta / dt).coerceIn(-12f, 12f)
                        lastCategoryVelocity = lastCategoryVelocity * 0.55f + instant * 0.45f
                    }
                    BladeAxis.VERTICAL -> {
                        val phaseDelta = -dy / safeItemStep
                        motionScope.launch { itemPhase.snapTo(itemPhase.value + phaseDelta) }
                        val instant = (phaseDelta / dt).coerceIn(-12f, 12f)
                        lastItemVelocity = lastItemVelocity * 0.55f + instant * 0.45f
                    }
                    BladeAxis.NONE -> Unit
                }
            }
            previous = current
        }

        if (gestureDragging) {
            onDragChange(false)
            when (axis) {
                BladeAxis.HORIZONTAL -> onCategorySettle(lastCategoryVelocity)
                BladeAxis.VERTICAL -> onItemSettle(lastItemVelocity)
                BladeAxis.NONE -> Unit
            }
        }
    }
}

@Composable
private fun BladeHeader(
    minimizedCount: Int,
    onCloseHome: () -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
    BladeSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = shape,
        focused = false
    ) {
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
            Spacer(Modifier.width(7.dp))
            Text("GSYNK", color = BladePalette.White, fontSize = 18.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "blades · " + BuildConfig.VERSION_NAME + " EXP · " + Blade.VERSION.takeWhile { it != '—' }.trim(),
                color = BladePalette.DimWhite,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (minimizedCount > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BladePalette.Green.copy(alpha = 0.32f))
                        .border(1.dp, BladePalette.Lime.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(minimizedCount.toString() + " окн.", color = BladePalette.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(7.dp))
            }
            BladeControlOrb(
                text = "⌂",
                modifier = Modifier.size(34.dp),
                onClick = onCloseHome
            )
        }
    }
}

@Composable
private fun BladeTabRail(
    categories: List<BladeCategory>,
    phase: Float,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        val localDensity = LocalDensity.current
        val stepPx = with(localDensity) { Blade.TAB_STEP_DP.dp.toPx() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            categories.forEachIndexed { index, category ->
                val distance = Blade.cyclicDistance(index, phase, categories.size)
                if (abs(distance) <= 3.2f) {
                    val focused = index == selectedIndex
                    val scale = Blade.tabScale(distance)
                    val layerAlpha = Blade.tabAlpha(distance)
                    BladeSurface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(Blade.TAB_STEP_DP.dp)
                            .height(94.dp)
                            .graphicsLayer {
                                translationX = distance * stepPx
                                scaleX = scale
                                scaleY = scale
                                this.alpha = layerAlpha
                                rotationY = Blade.tabSkew(distance)
                                cameraDistance = 9f * density
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
                        shape = RoundedCornerShape(10.dp),
                        focused = focused
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BladeIcon(
                                iconRes = category.iconRes,
                                modifier = Modifier.size(40.dp),
                                active = focused
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = category.title.lowercase(),
                                color = if (focused) BladePalette.White else BladePalette.DimWhite,
                                fontSize = if (focused) 10.sp else 8.sp,
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
}

@Composable
private fun BladeItemColumn(
    entries: List<Catalog.Entry>,
    phase: Float,
    selectedIndex: Int,
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onActivate: (Int, Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val localDensity = LocalDensity.current
        val rowHeight = minOf(Blade.ITEM_STEP_DP.dp, maxHeight / (Blade.VISIBLE_ITEMS + 0.6f))
        val rowStepPx = with(localDensity) { (rowHeight + 5.dp).toPx() }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("в этом лезвии пока нет функций", color = TextDim, fontSize = 12.sp)
            }
            return@BoxWithConstraints
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            entries.forEachIndexed { index, entry ->
                val distance = Blade.cyclicDistance(index, phase, entries.size)
                if (abs(distance) <= 3.6f) {
                    BladeItemRow(
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
private fun BoxScope.BladeItemRow(
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
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onFocusOrigin: (LiquidWindowOrigin?) -> Unit
) {
    val context = LocalContext.current
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val scale = Blade.itemScale(distance)
    val layerAlpha = Blade.itemAlpha(distance)
    val badge = when {
        minimized -> "активно"
        entry.fresh -> "ново"
        favorite -> "моё"
        else -> null
    }

    LaunchedEffect(focused, launchOrigin) {
        if (focused) onFocusOrigin(launchOrigin)
    }

    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.96f)
            .height(rowHeight)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .graphicsLayer {
                translationY = distance * rowStepPx
                scaleX = scale
                scaleY = scale
                this.alpha = layerAlpha
                translationX = Blade.itemTranslationX(distance) * density
                rotationX = Blade.itemSkew(distance)
                cameraDistance = 10f * density
                shadowElevation = if (focused) 16f else 3f
                ambientShadowColor = BladePalette.Lime.copy(alpha = 0.18f)
                spotShadowColor = BladePalette.Green.copy(alpha = 0.28f)
            }
    ) {
        BladeSurface(
            modifier = Modifier.matchParentSize(),
            shape = RoundedCornerShape(10.dp),
            focused = focused
        ) {
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
                            onToggleFavorite(entry)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BladeIcon(
                    iconRes = entry.iconRes,
                    modifier = Modifier.size(rowHeight * 0.62f),
                    active = focused
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.title.lowercase(),
                        color = if (focused) BladePalette.White else TextPrimary,
                        fontSize = if (focused) 15.sp else 12.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (focused && !liveLine.isNullOrBlank()) {
                        Text(
                            text = liveLine,
                            color = BladePalette.DimWhite,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(BladePalette.Green.copy(alpha = 0.34f))
                            .border(1.dp, BladePalette.Lime.copy(alpha = 0.58f), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = BladePalette.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BladeControlBar(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BladeControlOrb("←", Modifier.size(38.dp), onClick = onLeft)
        BladeControlOrb("→", Modifier.size(38.dp), onClick = onRight)
        BladeControlOrb("↑", Modifier.size(38.dp), onClick = onUp)
        BladeControlOrb("↓", Modifier.size(38.dp), onClick = onDown)
        BladeControlOrb(
            "OK",
            Modifier
                .weight(1f)
                .height(38.dp),
            onClick = onOpen
        )
    }
}
