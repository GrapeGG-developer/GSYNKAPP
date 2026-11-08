package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsynk.app.core.GrealPlay
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.isActive

/**
 * «Антистресс Script» — приложение, куда заходят просто покрутить что-нибудь.
 *
 * Три режима живут на одном плоском 2D-слое: бесконечная карусель из
 * градиентных плиток, лопающиеся сферы и световая рябь. Один общий кадровый цикл
 * ведёт вращение, инерцию, наклон и затухание эффектов; Canvas'ы читают State
 * только в фазе рисования, поэтому бесконечная анимация не перестраивает UI.
 */
@Composable
fun GrealAntistressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val skin = LocalSkinValues.current
    val effects = LocalGiouiEffects.current
    val accent = UiTweaks.accentStart
    val runtime = remember { GrealAntistressRuntime() }
    var mode by remember { mutableStateOf(GrealPlay.Mode.CAROUSEL) }
    var pace by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    val budget = GrealPlay.effectBudget(skin.level, effects == Gioui.Effects.FULL)
    val rippleLimit = GrealPlay.rippleLimit(budget)
    val carouselCount = GrealPlay.carouselCount(budget)

    LaunchedEffect(mode, pace, skin.level, skin.prgMotion, effects) {
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous == 0L) previous = now
                val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                val idle = GrealPlay.idleSpeed(mode, skin.prgMotion, pace)
                val spin = GrealPlay.step(
                    angle = runtime.angle.value,
                    velocity = runtime.velocity,
                    dtSeconds = dt,
                    idleDps = idle,
                    dragging = runtime.dragging
                )
                runtime.angle.value = spin.angle
                runtime.velocity = spin.velocity
                runtime.tilt.value = GrealPlay.relaxTilt(runtime.tilt.value, dt)
                val nextSecond = runtime.seconds.value + dt
                runtime.seconds.value = nextSecond

                val cleanRipples = GrealPlay.decayRipples(runtime.ripples.value, nextSecond)
                if (cleanRipples.size != runtime.ripples.value.size) runtime.ripples.value = cleanRipples
                val cleanPopped = runtime.popped.value.filterValues { nextSecond - it < GrealPlay.POP_LIFETIME }
                if (cleanPopped.size != runtime.popped.value.size) runtime.popped.value = cleanPopped
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    runtime.stageWidth = size.width.toFloat().coerceAtLeast(1f)
                    runtime.stageHeight = size.height.toFloat().coerceAtLeast(1f)
                }
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.11f),
                            accent.copy(alpha = 0.16f),
                            Color(0xFF03110E).copy(alpha = 0.30f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .pointerInput(mode) {
                    detectTapGestures { offset ->
                        UiTweaks.tap(context)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        score++
                        addRipple(runtime, offset.x, offset.y, rippleLimit)
                        when (mode) {
                            GrealPlay.Mode.CAROUSEL -> {
                                runtime.velocity += if (offset.x < runtime.stageWidth * 0.5f) -95f else 95f
                            }
                            GrealPlay.Mode.BUBBLES -> {
                                val x = (offset.x / runtime.stageWidth).coerceIn(0f, 1f)
                                val y = (offset.y / runtime.stageHeight).coerceIn(0f, 1f)
                                val now = runtime.seconds.value
                                val hit = (0 until GrealPlay.BUBBLE_COUNT).firstOrNull { index ->
                                    val pose = GrealPlay.bubblePose(index, GrealPlay.BUBBLE_COUNT, now, 7331)
                                    GrealPlay.hitBubble(pose, x, y, runtime.stageWidth, runtime.stageHeight)
                                }
                                if (hit != null) {
                                    runtime.popped.value = runtime.popped.value + (hit to now)
                                    score += 4
                                }
                            }
                            GrealPlay.Mode.RIPPLE -> Unit
                        }
                    }
                }
                .pointerInput(mode) {
                    detectDragGestures(
                        onDragStart = {
                            runtime.dragging = true
                            runtime.lastDragTime = 0L
                            runtime.velocity = 0f
                        },
                        onDragEnd = { runtime.dragging = false },
                        onDragCancel = { runtime.dragging = false },
                        onDrag = { change, dragAmount ->
                            val nowMs = change.uptimeMillis
                            val dt = if (runtime.lastDragTime == 0L) {
                                0.016f
                            } else {
                                ((nowMs - runtime.lastDragTime) / 1000f).coerceIn(0.008f, 0.05f)
                            }
                            runtime.lastDragTime = nowMs
                            runtime.angle.value = GrealPlay.wrapDegrees(
                                runtime.angle.value + dragAmount.x * GrealPlay.DRAG_DEGREES_PER_PX
                            )
                            runtime.tilt.value = (runtime.tilt.value + dragAmount.y * 0.10f).coerceIn(-28f, 28f)
                            runtime.velocity = GrealPlay.flingVelocity(dragAmount.x, dt)
                            addRipple(runtime, change.position.x, change.position.y, rippleLimit)
                        }
                    )
                }
        ) {
            GrealTexture(Modifier.matchParentSize(), accent = accent, seed = 5714, density = 0.30f)
            PrgGlassSheen(Modifier.matchParentSize(), accent = accent, seed = 5714)
            GrealParallaxShards(Modifier.matchParentSize(), accent = accent)

            when (mode) {
                GrealPlay.Mode.CAROUSEL -> GrealInfiniteCarousel(
                    modifier = Modifier.matchParentSize(),
                    angle = runtime.angle,
                    tilt = runtime.tilt,
                    accent = accent,
                    count = carouselCount
                )
                GrealPlay.Mode.BUBBLES -> GrealBubbleField(
                    modifier = Modifier.matchParentSize(),
                    seconds = runtime.seconds,
                    popped = runtime.popped,
                    accent = accent
                )
                GrealPlay.Mode.RIPPLE -> GrealRippleField(
                    modifier = Modifier.matchParentSize(),
                    seconds = runtime.seconds,
                    accent = accent
                )
            }
            GrealRippleOverlay(
                modifier = Modifier.matchParentSize(),
                seconds = runtime.seconds,
                ripples = runtime.ripples,
                accent = accent
            )

            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = mode.title.lowercase(),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = mode.hint,
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
                Text(
                    text = "∞ · касаний: $score",
                    color = GrapeVioletSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "тяни · отпускай с разгоном · касайся",
                color = Color.White.copy(alpha = 0.46f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            )
            GrealActiveCorners(Modifier.matchParentSize(), accent = accent, strength = 0.34f)
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.42f)) {
            SectionTitle("Просто покрутить")
            Text(
                text = "Один общий таймер Script, $budget адаптивных 2D-слоёв и бесконечное кольцо плиток. Никаких счётчиков жизни, рекламы и интернета.",
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                GrealPlay.Mode.entries.forEach { item ->
                    SortChip(item.title, mode == item) { mode = item }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Темп фонового вращения", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            ChipFlow {
                listOf("медленно", "обычно", "быстро").forEachIndexed { index, label ->
                    SortChip(label, pace == index) { pace = index }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Сбросить",
                    onClick = {
                        runtime.angle.value = 0f
                        runtime.tilt.value = 0f
                        runtime.velocity = 0f
                        runtime.dragging = false
                        runtime.ripples.value = emptyList()
                        runtime.popped.value = emptyMap()
                        runtime.lastRippleAt = -10f
                        score = 0
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Назад",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private class GrealAntistressRuntime {
    val angle = mutableStateOf(0f)
    val tilt = mutableStateOf(0f)
    val seconds = mutableStateOf(0f)
    val ripples = mutableStateOf<List<GrealPlay.Ripple>>(emptyList())
    val popped = mutableStateOf<Map<Int, Float>>(emptyMap())
    var velocity = 0f
    var dragging = false
    var lastDragTime = 0L
    var lastRippleAt = -10f
    var rippleSerial = 0L
    var stageWidth = 1f
    var stageHeight = 1f
}

private fun addRipple(
    runtime: GrealAntistressRuntime,
    xPx: Float,
    yPx: Float,
    limit: Int
) {
    val now = runtime.seconds.value
    if (now - runtime.lastRippleAt < 0.055f) return
    runtime.lastRippleAt = now
    val x = (xPx / runtime.stageWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val y = (yPx / runtime.stageHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)
    runtime.rippleSerial++
    val ripple = GrealPlay.Ripple(
        id = runtime.rippleSerial,
        x = x,
        y = y,
        born = now,
        tone = (runtime.rippleSerial % 5L).toInt()
    )
    runtime.ripples.value = (runtime.ripples.value + ripple).takeLast(limit)
}
