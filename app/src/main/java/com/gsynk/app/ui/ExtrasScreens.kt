package com.gsynk.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * Скринсейвер: картинка (или встроенный логотип GSYNK) летает по экрану
 * и отскакивает от краёв, как логотип DVD. Каждое касание стены — вспышка и смена оттенка.
 */
@Composable
fun ScreensaverScreen(onBack: () -> Unit) {
    // размеры берём у окна приложения: так корректно и в складных, и в окне-мультизадаче
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenW = with(density) { windowInfo.containerSize.width.toDp().value }
    val screenH = with(density) { windowInfo.containerSize.height.toDp().value }

    val logoSize = 130f
    var x by remember { mutableFloatStateOf(20f) }
    var y by remember { mutableFloatStateOf(120f) }
    var vx by remember { mutableFloatStateOf(1.35f) }
    var vy by remember { mutableFloatStateOf(1.15f) }
    var hits by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paused) {
        while (!paused) {
            delay(16)
            var nx = x + vx
            var ny = y + vy
            var hit = false
            if (nx <= 0f || nx + logoSize >= screenW) {
                vx = -vx
                nx = x + vx
                hit = true
            }
            if (ny <= 0f || ny + logoSize >= screenH) {
                vy = -vy
                ny = y + vy
                hit = true
            }
            if (hit) hits += 1
            x = nx
            y = ny
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07040F))
    ) {
        // «парящий» логотип
        Box(
            Modifier
                .padding(start = x.dp, top = y.dp)
                .size(logoSize.dp)
        ) {
            DvdLogo(hue = (hits * 37) % 360)
        }

        // служебная панель
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Скринсейвер",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text("отскоков: $hits", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(if (paused) "Продолжить" else "Пауза") { paused = !paused }
                SmallAction("Быстрее") {
                    vx *= 1.25f
                    vy *= 1.25f
                }
                SmallAction("Медленнее") {
                    vx = (vx * 0.8f).coerceAtLeast(0.25f)
                    vy = (vy * 0.8f).coerceAtLeast(0.2f)
                }
            }
        }

        SecondaryButton(
            text = "Выйти",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/** Логотип «GSYNK» в стиле DVD: овальная подложка с блеском. */
@Composable
private fun DvdLogo(hue: Int) {
    val transition = rememberInfiniteTransition(label = "dvd")
    val shine by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    val base = Color.hsv(hue.toFloat(), 0.75f, 0.95f)

    Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height * 0.62f
        val top = (size.height - h) / 2f
        // корпус логотипа
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(base, GrapeViolet, GrapeGreen),
                start = Offset(0f, top),
                end = Offset(w, top + h)
            ),
            topLeft = Offset(0f, top),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        )
        // контур
        drawRoundRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(0f, top),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
            style = Stroke(width = 2.5f)
        )
        // блик, бегущий слева направо
        val shineX = -w + shine * (2.2f * w)
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                start = Offset(shineX, top),
                end = Offset(shineX + w * 0.4f, top + h)
            ),
            topLeft = Offset(0f, top),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        )
        // «крылья» DVD
        val wing = Path().apply {
            moveTo(w * 0.16f, top + h)
            lineTo(w * 0.30f, top + h * 1.22f)
            lineTo(w * 0.44f, top + h)
            close()
        }
        drawPath(wing, color = base.copy(alpha = 0.85f))
    }

    Text(
        text = "G S Y N K",
        color = Color(0xFF0B0616),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.align(Alignment.Center)
    )
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(CardBgAlt)
            .border(1.dp, ChipBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * GRAPEUI — «операционная система внутри GSYNK».
 * Пока это только каркас: рабочий стол, док и часы. Логику пользователь допишет сам.
 */
@Composable
fun GrapeUiScreen(onBack: () -> Unit) {
    var booted by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var openedApp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(1200)
        booted = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.LocalTime.now())
            delay(15_000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF170A2A), Color(0xFF08170F))))
    ) {
        if (!booted) {
            Text(
                "GRAPEUI\nзагрузка…",
                color = GrapeVioletSoft,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock, color = TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("GRAPEUI v0.1", color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(18.dp))

                val apps = listOf(
                    Triple("Терминал", "заготовка", GrapeGreen),
                    Triple("Файлы", "заготовка", GrapeViolet),
                    Triple("Настройки", "заготовка", GrapeVioletSoft),
                    Triple("Хранилище", "в разработке", TextDim)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    apps.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (name, note, color) ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1.35f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CardBgAlt)
                                        .border(1.dp, ChipBorder, RoundedCornerShape(18.dp))
                                        .clickable { openedApp = name },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(color)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(name, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                                        Text(note, color = TextDim, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                SurfaceCard(corner = 18.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Text(
                        text = "Это каркас будущей оболочки: GSYNK уже умеет запускать её, " +
                            "а приложения внутри GRAPEUI вы добавляете сами.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Выйти из GRAPEUI", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }

        if (openedApp != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { openedApp = null },
                contentAlignment = Alignment.Center
            ) {
                SurfaceCard(corner = 20.dp) {
                    Text("«$openedApp» — заглушка", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Здесь будет ваш код приложения GRAPEUI.", color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
