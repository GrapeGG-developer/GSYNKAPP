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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
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
import kotlin.math.cos
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
 * Геометрия «жидкого окна» GREAL.
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

/** Прямоугольник конкретной закладки в стопке активных углов: сверху вниз. */
internal fun liquidMinimizeRect(index: Int): LiquidWindowRect {
    val safe = index.coerceIn(0, 3)
    return LiquidWindowRect(
        left = LIQUID_MINIMIZE_RECT.left,
        top = (LIQUID_MINIMIZE_RECT.top + safe * 0.104f).coerceAtMost(0.84f),
        width = 0.40f,
        height = 0.086f
    )
}

/** Данные одной свёрнутой GREAL-закладки для пустого дома. */
data class MinimizedWindowUi(
    val title: String,
    val route: String?,
    val iconRes: Int,
    val color: Color
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
 * Одна панель глубокой 3D-сцены GREAL. Положение берётся из реального
 * LazyListState: центр экрана смотрит прямо, прошлое и будущее уходят
 * в перспективу без кассетных линий и кругов.
 */
@Composable
private fun GrealDepthPanel(
    listState: LazyListState,
    itemKey: String,
    content: @Composable () -> Unit
) {
    val transform by remember(listState, itemKey) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            val item = info.visibleItemsInfo.firstOrNull { it.key == itemKey }
            if (item == null) {
                grealDepthTransform(0f)
            } else {
                val viewportCenter = viewport / 2f
                val itemCenter = item.offset - info.viewportStartOffset + item.size / 2f
                grealDepthTransform((itemCenter - viewportCenter) / viewportCenter.coerceAtLeast(1f))
            }
        }
    }
    Box(
        Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, transform.pivotY)
            rotationX = transform.rotationX
            rotationY = transform.rotationY
            scaleX = transform.scale
            scaleY = transform.scale
            alpha = transform.alpha
            translationX = transform.translationXD * density
            translationY = transform.translationYDp * density
            cameraDistance = 10.5f * density
            shape = RoundedCornerShape(20.dp)
            clip = false
            shadowElevation = transform.shadowElevation
            ambientShadowColor = Color(0xFF7C3AED).copy(alpha = 0.26f)
            spotShadowColor = Color(0xFF22D3EE).copy(alpha = 0.22f)
        }
    ) { content() }
}

/** Маленькая физическая кнопка-сфера GREAL для шапок и активных углов. */
@Composable
internal fun GrealControlOrb(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "grealControlScale"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = if (pressed) -16f else -3f
                rotationY = if (pressed) 13f else 3f
                cameraDistance = 9f * density
                shadowElevation = if (pressed) 4f else 16f
                ambientShadowColor = accent.copy(alpha = 0.34f)
                spotShadowColor = accent.copy(alpha = 0.42f)
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.42f),
                        (if (danger) Color(0xFFE81123) else accent).copy(alpha = 0.44f),
                        Color.Black.copy(alpha = 0.34f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        GrealTexture(Modifier.matchParentSize(), accent = accent, seed = text.hashCode(), density = 0.26f)
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Light)
    }
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
 * Пустой портретный дом GREAL. Свёрнутые окна живут не одной закладкой,
 * а стопкой активных 3D-углов слева сверху: касание возвращает окно,
 * маленький × закрывает именно эту закладку, касание фона открывает «Пуск».
 */
@Composable
fun EmptyHomeScreen(
    onOpenStart: () -> Unit,
    minimizedWindows: List<MinimizedWindowUi> = emptyList(),
    onResumeMinimized: (Int) -> Unit = {},
    onCloseMinimized: (Int) -> Unit = {}
) {
    AnimatedGrapeBackground {
        GrealDepthBackdrop(Modifier.matchParentSize(), scrollPixels = 120f)
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
            if (minimizedWindows.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    minimizedWindows.forEachIndexed { index, window ->
                        Box(
                            Modifier
                                .width(184.dp)
                                .height(72.dp)
                                .graphicsLayer {
                                    rotationX = -11f
                                    rotationY = 14f
                                    cameraDistance = 9f * density
                                    shadowElevation = 27f
                                    ambientShadowColor = window.color.copy(alpha = 0.34f)
                                    spotShadowColor = window.color.copy(alpha = 0.42f)
                                    shape = RoundedCornerShape(18.dp)
                                    clip = false
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.17f),
                                            window.color.copy(alpha = 0.38f),
                                            Color.Black.copy(alpha = 0.30f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                                .clickable { onResumeMinimized(index) }
                                .padding(start = 8.dp, end = 28.dp, top = 7.dp, bottom = 7.dp)
                        ) {
                            GrealTexture(Modifier.matchParentSize(), accent = window.color, seed = window.title.hashCode() + index, density = 0.34f)
                            PrgGlassSheen(Modifier.matchParentSize(), accent = window.color, seed = 97 + index)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GrealOrbFace(
                                    modifier = Modifier.size(48.dp),
                                    accent = window.color,
                                    iconRes = window.iconRes,
                                    seed = window.title.hashCode(),
                                    active = true,
                                    iconScale = 0.46f
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("активный 3D-угол", color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, maxLines = 1)
                                    Text(
                                        window.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            GrealControlOrb(
                                text = "×",
                                accent = window.color,
                                danger = true,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 20.dp, y = (-2).dp)
                                    .size(24.dp),
                                onClick = { onCloseMinimized(index) }
                            )
                        }
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
                    if (minimizedWindows.isNotEmpty()) {
                        "активных окон: " + minimizedWindows.size + " — они свёрнуты в 3D-углы слева сверху"
                    } else {
                        "GREAL · 3D-лента времени: прошлое сверху, будущее снизу"
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (minimizedWindows.isNotEmpty()) {
                        "коснитесь закладки, чтобы развернуть окно, × — закрыть её"
                    } else {
                        "коснитесь экрана, чтобы снова открыть «Пуск»"
                    },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * GREAL XMB — портретная оболочка XrossMediaBar.
 *
 * Старая 3D-лента времени больше не рисуется: `StartMenuScreen` теперь напрямую
 * открывает [XmbStartScreen] с горизонтальными категориями, вертикальным рядом
 * функций под фокусом и теми же физическими эффектами GREAL. Старые помощники
 * раскладки ниже сохранены только для регрессионных проверок чистой геометрии.
 */
@Composable
fun StartMenuScreen(
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
    liveLinesFor: (Catalog.Entry) -> List<String>,
    onOpen: (Catalog.Entry, LiquidWindowOrigin?) -> Unit,
    onToggleFavorite: (Catalog.Entry) -> Unit,
    onCloseHome: () -> Unit
) {
    XmbStartScreen(
        favorites = favorites,
        minimizedRoutes = minimizedRoutes,
        liveLinesFor = liveLinesFor,
        onOpen = onOpen,
        onToggleFavorite = onToggleFavorite,
        onCloseHome = onCloseHome
    )
}

@Composable
private fun TimelineSectionLabel(title: String, hint: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.13f), Color(0xFF7C3AED).copy(alpha = 0.20f), Color.Black.copy(alpha = 0.20f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(50))
        ) {
            GrealTexture(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = title.hashCode(), density = 0.28f)
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    maxLines = 1
                )
                Text(
                    hint,
                    color = Color.White.copy(alpha = 0.46f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Карточка «прошлого»: слоёная физическая пластина на магнитной ленте. */
@Composable
private fun TimelinePastCard(
    entry: Catalog.Entry,
    liveLines: List<String>,
    onClick: (LiquidWindowOrigin?) -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    val line = liveLines.map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: entry.subtitle
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(68.dp)
            .onGloballyPositioned { coordinates ->
                launchOrigin = coordinates.toLiquidWindowOrigin(entry)
            }
            .graphicsLayer {
                rotationX = -6f
                rotationY = 5f
                cameraDistance = 10f * density
                shadowElevation = 20f
                ambientShadowColor = entry.color.copy(alpha = 0.30f)
                spotShadowColor = entry.color.copy(alpha = 0.38f)
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.15f),
                        entry.color.copy(alpha = 0.38f),
                        Color.Black.copy(alpha = 0.30f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable { onClick(launchOrigin) }
    ) {
        GrealTexture(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode() + 7, density = 0.34f)
        PrgGlassSheen(Modifier.matchParentSize(), accent = entry.color, seed = entry.id.hashCode() + 7)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GrealOrbFace(
                modifier = Modifier.size(50.dp),
                accent = entry.color,
                iconRes = entry.iconRes,
                seed = entry.id.hashCode(),
                active = entry.fresh,
                iconScale = 0.48f
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
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
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), entry.color.copy(alpha = 0.26f), Color.Black.copy(alpha = 0.20f))))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = Color.White.copy(alpha = 0.88f), fontSize = 20.sp, fontWeight = FontWeight.Light)
            }
        }
        GrealActiveCorners(Modifier.matchParentSize(), accent = entry.color, strength = 0.20f)
    }
}

/** Точка «сейчас»: живая часовая сфера, с которого начинается «Пуск». */
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
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .graphicsLayer {
                rotationX = -5f
                rotationY = -5f
                cameraDistance = 10f * density
                shadowElevation = 26f
                ambientShadowColor = Color(0xFF8B5CF6).copy(alpha = 0.34f)
                spotShadowColor = Color(0xFF22D3EE).copy(alpha = 0.34f)
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color(0xFF8B5CF6).copy(alpha = 0.34f),
                        Color(0xFF22D3EE).copy(alpha = 0.12f),
                        Color.Black.copy(alpha = 0.26f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.34f), shape)
    ) {
        GrealTexture(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 560, density = 0.32f)
        PrgGlassSheen(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), seed = 560)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("· сейчас ·", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                GrealOrbFace(
                    modifier = Modifier.matchParentSize(),
                    accent = Color(0xFF8B5CF6),
                    iconRes = 0,
                    seed = 5701,
                    active = true
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeText, color = TextPrimary, fontSize = 35.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    Text("лента · GREAL", color = Color.White.copy(alpha = 0.68f), fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(dateText, color = Color.White.copy(alpha = 0.84f), fontSize = 13.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(6.dp))
            Text(
                "↑ прошлое: новости и настройки · ↓ будущее: сферы-плитки",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GrealActiveCorners(Modifier.matchParentSize(), accent = Color(0xFF8B5CF6), strength = 0.34f)
    }
}

/** Одна категория «будущего»: подписанная физическая панель со сферами-плитками. */
@Composable
private fun TimelineGroupSection(
    group: StartGroupData,
    columns: Int,
    baseIndex: Int,
    favorites: Set<String>,
    minimizedRoutes: Set<String>,
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
        val panelPaddingDp = 10
        val cellWidthDp = startCellWidthDp(
            (maxWidth.value.toInt() - panelPaddingDp * 2).coerceAtLeast(1),
            columns
        )
        val cell = cellWidthDp.dp
        val gridWidth = cell * columns + gap * (columns - 1).coerceAtLeast(0)
        val gridHeight = cell * rows + gap * (rows - 1).coerceAtLeast(0)
        val shape = RoundedCornerShape(23.dp)
        val accent = entries.firstOrNull()?.color ?: Color(0xFF8B5CF6)
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationX = -3.5f
                    rotationY = 3.5f
                    cameraDistance = 11f * density
                    shadowElevation = 28f
                    ambientShadowColor = accent.copy(alpha = 0.32f)
                    spotShadowColor = accent.copy(alpha = 0.38f)
                }
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.11f),
                            accent.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.24f), shape)
        ) {
            GrealTexture(Modifier.matchParentSize(), accent = accent, seed = group.key.hashCode(), density = 0.34f)
            PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = group.key.hashCode() + 19)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(panelPaddingDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            group.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            group.hint,
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                                minimized = entry.route in minimizedRoutes,
                                index = baseIndex + index,
                                modifier = Modifier.offset(x = (cell + gap) * place.x, y = (cell + gap) * place.y),
                                onClick = { origin -> onOpen(entry, origin) },
                                onLongClick = { onToggleFavorite(entry) }
                            )
                        }
                    }
                }
            }
            GrealActiveCorners(Modifier.matchParentSize(), accent = accent, strength = 0.22f)
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
    var entered by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        delay((index % 16).coerceAtLeast(0) * 30L)
        entered = true
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(320), label = "orbAlpha")
    val enterScale by animateFloatAsState(
        if (entered) 1f else 0.82f,
        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "orbEnterScale"
    )
    val pressScaleX by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "orbPressScaleX"
    )
    val pressScaleY by animateFloatAsState(
        if (pressed) 1.10f else 1f,
        spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "orbPressScaleY"
    )
    var launchOrigin by remember(entry.id) { mutableStateOf<LiquidWindowOrigin?>(null) }
    val phaseState = rememberPrgPhaseState(entry.id.hashCode())
    val orbSize = grealOrbDiameterDp(width.value.toInt().coerceAtLeast(1), height.value.toInt().coerceAtLeast(1)).dp
    val wide = width >= 128.dp
    val compact = orbSize < 46.dp
    val hasLive = liveLines.any { it.isNotBlank() }
    val badge = when {
        minimized -> "активно"
        entry.fresh -> "ново"
        favorite -> "моё"
        else -> null
    }

    Box(
        modifier
            .graphicsLayer {
                val phase = phaseState.value
                alpha = enterAlpha
                scaleX = enterScale * pressScaleX
                scaleY = enterScale * pressScaleY
                translationY = (1f - enterAlpha) * 18f
                rotationX = (if (pressed) -17f else sin(phase * 2f * PI.toFloat()) * 7.5f)
                rotationY = (if (pressed) 14f else cos(phase * 2f * PI.toFloat()) * 9.5f)
                rotationZ = if (pressed) 0f else sin(phase * 2f * PI.toFloat() + index * 0.37f) * 9f
                cameraDistance = 8.5f * density
                shadowElevation = if (pressed) 6f else 18f
                ambientShadowColor = entry.color.copy(alpha = 0.30f)
                spotShadowColor = entry.color.copy(alpha = 0.38f)
            }
            .width(width)
            .height(height)
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(orbSize), contentAlignment = Alignment.Center) {
                // Бесконечная 3D-орбита из маленьких плиток: крутится без остановки.
                GrealInfiniteOrbit(
                    modifier = Modifier.matchParentSize(),
                    accent = entry.color,
                    seed = entry.id.hashCode(),
                    active = minimized || favorite
                )
                GrealOrbFace(
                    modifier = Modifier.matchParentSize(),
                    accent = entry.color,
                    iconRes = entry.iconRes,
                    seed = entry.id.hashCode(),
                    active = minimized || favorite,
                    iconScale = if (compact) 0.46f else 0.50f
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .border(1.dp, Color.White.copy(alpha = 0.48f), RoundedCornerShape(50))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                } else if (hasLive) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-3).dp, y = (-4).dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White, entry.color)))
                            .border(1.dp, Color.White.copy(alpha = 0.62f), CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .border(1.dp, Color.White.copy(alpha = if (minimized) 0.55f else 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = if (compact) 9.sp else if (wide) 11.sp else 10.sp,
                    lineHeight = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (wide) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
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
    val shellEntry = Catalog.ALL.firstOrNull { entry -> entry.route == targetOrigin.route } ?: fallbackEntry
    val shellDescription = shellEntry?.subtitle ?: "приложение GSYNK · GREAL"

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
        val windowBlurDp = grealWindowBlurDp(p)
        val rollDegrees = grealWindowRollDegrees(p)
        val rollSign = if (targetOrigin.centerY >= 0.5f) -1f else 1f

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
                .then(
                    if (windowBlurDp > 0.5f) {
                        Modifier.blur(windowBlurDp.dp, BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    transformOrigin = TransformOrigin(rect.centerX, rect.centerY)
                    scaleX = rect.width * (1f + liquidPulse * 0.018f)
                    scaleY = rect.height * (1f - liquidPulse * 0.055f)
                    alpha = contentAlpha
                    rotationX = (0.5f - targetOrigin.centerY) * 21f * (1f - p) + rollDegrees * rollSign * 0.72f
                    rotationY = (targetOrigin.centerX - 0.5f) * 17f * (1f - p) + liquidPulse * 3.2f
                    cameraDistance = 10f * density
                    shape = RoundedCornerShape(localCorner)
                    clip = true
                    shadowElevation = 14f + liquidPulse * 46f
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
                            .height(52.dp)
                            .clip(RoundedCornerShape(bottomStart = 19.dp, bottomEnd = 19.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        accent.copy(alpha = 0.30f),
                                        Color(0xFF22D3EE).copy(alpha = 0.11f),
                                        Color.Black.copy(alpha = 0.22f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(bottomStart = 19.dp, bottomEnd = 19.dp))
                    ) {
                        GrealTexture(Modifier.matchParentSize(), accent = accent, seed = title.hashCode(), density = 0.30f)
                        PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = title.hashCode())
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GrealControlOrb(
                                text = "—",
                                accent = accent,
                                modifier = Modifier.size(36.dp),
                                onClick = { requestExit(onMinimize) }
                            )
                            Spacer(Modifier.width(7.dp))
                            GrealOrbFace(
                                modifier = Modifier.size(39.dp),
                                accent = accent,
                                iconRes = shellEntry?.iconRes ?: targetOrigin.iconRes,
                                seed = title.hashCode() + 31,
                                active = true,
                                iconScale = 0.50f
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    title.lowercase(),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    shellDescription,
                                    color = Color.White.copy(alpha = 0.66f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "GREAL",
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(7.dp))
                            GrealControlOrb(
                                text = "×",
                                accent = accent,
                                modifier = Modifier.size(38.dp),
                                danger = true,
                                onClick = { requestExit(onClose) }
                            )
                        }
                        GrealActiveCorners(Modifier.matchParentSize(), accent = accent, strength = 0.36f)
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

            GrealActiveCorners(
                Modifier.matchParentSize(),
                accent = accent,
                strength = 0.20f + liquidPulse * 0.52f
            )

            // Тонкая «волна» по материалу GREAL на середине morph-а: окно выглядит живым,
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

        // Семя окна: сама GREAL-сфера с подписью снизу в том же летящем
        // прямоугольнике. Оно растворяется и размывается в настоящем окне.
        if (seedAlpha > 0.01f) {
            val seedWidth = maxWidth * rect.width
            val seedHeight = maxHeight * rect.height
            val seedOrbSize = grealOrbDiameterDp(
                seedWidth.value.toInt().coerceAtLeast(1),
                seedHeight.value.toInt().coerceAtLeast(1)
            ).dp
            Box(
                Modifier
                    .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                    .size(width = seedWidth, height = seedHeight)
                    .then(
                        if (windowBlurDp > 0.5f) {
                            Modifier.blur((windowBlurDp * 0.65f).dp, BlurredEdgeTreatment.Unbounded)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        alpha = seedAlpha
                        rotationX = rollDegrees * rollSign * 0.7f
                        rotationY = (targetOrigin.centerX - 0.5f) * 16f * (1f - p)
                        cameraDistance = 9f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GrealOrbFace(
                        modifier = Modifier.size(seedOrbSize),
                        accent = accent,
                        iconRes = targetOrigin.iconRes,
                        seed = targetOrigin.title.hashCode(),
                        active = true,
                        iconScale = 0.50f
                    )
                    if (seedWidth >= 62.dp && seedHeight >= 48.dp) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.34f))
                                .border(1.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                targetOrigin.title.lowercase(),
                                color = Color.White,
                                fontSize = if (seedWidth >= 126.dp) 12.sp else 9.sp,
                                lineHeight = if (seedWidth >= 126.dp) 13.sp else 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
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
