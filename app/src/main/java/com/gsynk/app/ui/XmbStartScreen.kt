package com.gsynk.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.BuildConfig
import com.gsynk.app.R
import com.gsynk.app.core.Xmb
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * GREAL XMB — новая оболочка «Пуска».
 *
 * Категории идут горизонтально, функции выбранной категории — вертикально под
 * фокусом, как в XrossMediaBar. Физические материалы, аврора, объёмные сферы,
 * краевые блики и активные углы GREAL сохранены; серые линии и кассетные круги
 * заменены цветными волнами и параллаксными световыми лентами.
 */
private data class XmbCategory(
    val key: String,
    val title: String,
    val hint: String,
    val iconRes: Int,
    val entries: List<Catalog.Entry>
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
    val accent = UiTweaks.accentStart
    val categories = remember(favorites) { xmbCategories(favorites) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var selectedItem by remember { mutableIntStateOf(0) }

    LaunchedEffect(categories.size) {
        if (selectedCategory > categories.lastIndex) selectedCategory = categories.lastIndex
    }
    LaunchedEffect(selectedCategory, categories) { selectedItem = 0 }

    val safeCategory = selectedCategory.coerceIn(0, categories.lastIndex.coerceAtLeast(0))
    val category = categories.getOrNull(safeCategory)
    val entries = category?.entries.orEmpty()
    val safeItem = selectedItem.coerceIn(0, (entries.size - 1).coerceAtLeast(0))

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
            ) {
                GrealDepthBackdrop(
                    Modifier.matchParentSize(),
                    scrollPixels = safeCategory * 140f + safeItem * 18f
                )
                GrealParallaxShards(Modifier.matchParentSize(), accent = accent)
                XmbWaveField(Modifier.matchParentSize(), accent = accent)
                XmbFocusBeam(Modifier.matchParentSize(), accent = accent)

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    XmbCategoryRail(
                        categories = categories,
                        selected = safeCategory,
                        accent = accent,
                        onSelect = { index ->
                            selectedCategory = index
                            selectedItem = 0
                        },
                        onMove = { direction ->
                            selectedCategory = Xmb.move(safeCategory, direction, categories.size)
                            selectedItem = 0
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = category?.hint.orEmpty().lowercase(),
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(entries.size, safeCategory) {
                                var accumulated = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { accumulated = 0f },
                                    onDragEnd = { accumulated = 0f },
                                    onDragCancel = { accumulated = 0f },
                                    onVerticalDrag = { _, delta ->
                                        accumulated += delta
                                        if (abs(accumulated) >= 54f) {
                                            val direction = if (accumulated < 0f) 1 else -1
                                            selectedItem = Xmb.move(safeItem, direction, entries.size)
                                            accumulated = 0f
                                        }
                                    }
                                )
                            }
                    ) {
                        XmbItemColumn(
                            entries = entries,
                            selected = safeItem,
                            favorites = favorites,
                            minimizedRoutes = minimizedRoutes,
                            liveLinesFor = liveLinesFor,
                            accent = accent,
                            onOpen = onOpen,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                    Text(
                        text = "← → категории · ↑ ↓ функции · коснитесь, чтобы открыть",
                        color = Color.White.copy(alpha = 0.44f),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
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
                "пуск · " + BuildConfig.VERSION_NAME + " EXP · GREAL XMB",
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
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    onMove: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(122.dp)
            .pointerInput(categories.size) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onHorizontalDrag = { _, delta ->
                        accumulated += delta
                        if (abs(accumulated) >= 72f) {
                            val direction = if (accumulated < 0f) 1 else -1
                            onMove(direction)
                            accumulated = 0f
                        }
                    }
                )
            }
    ) {
        val step = Xmb.CATEGORY_STEP_DP.dp
        val targetOffset = maxWidth / 2f - step / 2f - step * selected
        val offset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "xmbCategoryOffset"
        )
        Row(
            Modifier.offset(x = offset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, category ->
                val distance = index - selected
                val scale = Xmb.categoryScale(distance)
                val layerAlpha = Xmb.categoryAlpha(distance)
                val focused = distance == 0
                Column(
                    Modifier
                        .width(step)
                        .graphicsLayer {
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

@Composable
private fun XmbItemColumn(
    entries: List<Catalog.Entry>,
    selected: Int,
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    accent: Color,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = minOf(68.dp, maxHeight / (Xmb.VISIBLE_ITEMS + 0.6f))
        val range = Xmb.visibleRange(selected, entries.size)
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("в этой категории пока нет функций", color = TextDim, fontSize = 12.sp)
            }
            return@BoxWithConstraints
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            range.forEach { index ->
                val entry = entries[index]
                XmbItemRow(
                    entry = entry,
                    distance = index - selected,
                    rowHeight = rowHeight,
                    liveLine = liveLinesFor(entry).firstOrNull { it.isNotBlank() }?.trim(),
                    favorite = entry.id in favorites,
                    minimized = entry.route in minimizedRoutes,
                    accent = accent,
                    onOpen = onOpen,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun XmbItemRow(
    entry: Catalog.Entry,
    distance: Int,
    rowHeight: androidx.compose.ui.unit.Dp,
    liveLine: String?,
    favorite: Boolean,
    minimized: Boolean,
    accent: Color,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val focused = distance == 0
    val scale = Xmb.itemScale(distance)
    val layerAlpha = Xmb.itemAlpha(distance)
    val badge = when {
        minimized -> "активно"
        entry.fresh -> "ново"
        favorite -> "моё"
        else -> null
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth(0.94f)
            .height(rowHeight)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .graphicsLayer {
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
                onClick = {
                    UiTweaks.tap(context)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpen(entry, launchOrigin)
                },
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
