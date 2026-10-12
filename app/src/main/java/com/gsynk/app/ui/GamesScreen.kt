package com.gsynk.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGradient
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/** Экран «Игры»: хаб с плитками + мини-игры. */
@Composable
fun GamesScreen(
    bestSnake: Int,
    bestReaction: Int,
    onSaveSnake: (Int) -> Unit,
    onSaveReaction: (Int) -> Unit,
    onBack: () -> Unit
) {
    var game by remember { mutableStateOf<String?>(null) }

    GrapeScreen(title = "Игры", onBack = onBack) {
        when (game) {
            "snake" -> SnakeGame(best = bestSnake, onSave = onSaveSnake, onExit = { game = null })
            "reaction" -> ReactionGame(best = bestReaction, onSave = onSaveReaction, onExit = { game = null })
            else -> {
                TileRow {
                    LiveTile(
                        title = "Змейка",
                        subtitle = "рекорд: $bestSnake",
                        tint = TileTint.Green,
                        liveText = "Играть",
                        modifier = Modifier.weight(1f),
                        onClick = { game = "snake" }
                    )
                    LiveTile(
                        title = "Реакция",
                        subtitle = if (bestReaction == 0) "нет рекорда" else "рекорд: $bestReaction мс",
                        tint = TileTint.Violet,
                        liveText = "Играть",
                        modifier = Modifier.weight(1f),
                        onClick = { game = "reaction" }
                    )
                }
                Spacer(Modifier.height(12.dp))
                TileRow {
                    LiveTile(
                        title = "NFC-дуэль",
                        subtitle = "два телефона касанием",
                        tint = TileTint.Dark,
                        badge = "скоро",
                        liveText = "В разработке",
                        modifier = Modifier.weight(1f),
                        onClick = { }
                    )
                    LiveTile(
                        title = "Остров-тир",
                        subtitle = "стрельба по плиткам",
                        tint = TileTint.Dark,
                        badge = "идея",
                        liveText = "В планах",
                        modifier = Modifier.weight(1f),
                        onClick = { }
                    )
                }
            }
        }
    }
}

// ————————————————————————————————————————————————
//  Змейка на плитках
// ————————————————————————————————————————————————

@Composable
private fun SnakeGame(best: Int, onSave: (Int) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val size = 14

    var snake by remember { mutableStateOf(listOf(7 to 7, 6 to 7, 5 to 7)) }
    var food by remember { mutableStateOf(10 to 7) }
    var dir by remember { mutableStateOf(1 to 0) } // вправо
    var score by remember { mutableIntStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var speed by remember { mutableLongStateOf(320L) }

    LaunchedEffect(alive) {
        while (alive) {
            delay(speed)
            val head = snake.first()
            val next = (head.first + dir.first) to (head.second + dir.second)

            val crashed = next.first !in 0 until size ||
                next.second !in 0 until size ||
                snake.dropLast(1).contains(next)

            if (crashed) {
                alive = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSave(score)
                break
            }

            snake = if (next == food) {
                score += 1
                speed = (speed - 6L).coerceAtLeast(140L)
                var candidate: Pair<Int, Int>
                do {
                    candidate = Random.nextInt(size) to Random.nextInt(size)
                } while (snake.contains(candidate))
                food = candidate
                listOf(next) + snake
            } else {
                listOf(next) + snake.dropLast(1)
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Очки: $score", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("Рекорд: $best", color = TextDim, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF120B22))
                .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (y in 0 until size) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (x in 0 until size) {
                            val isSnake = snake.contains(x to y)
                            val isHead = snake.firstOrNull() == (x to y)
                            val isFood = food == (x to y)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            isHead -> GrapeGreen
                                            isSnake -> GrapeGreenSoft.copy(alpha = 0.75f)
                                            isFood -> GrapeViolet
                                            else -> Color.White.copy(alpha = 0.04f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (alive) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DpadButton("▲") { if (dir != 0 to 1) dir = 0 to -1 }
                Row {
                    DpadButton("◀") { if (dir != 1 to 0) dir = -1 to 0 }
                    Spacer(Modifier.width(56.dp))
                    DpadButton("▶") { if (dir != -1 to 0) dir = 1 to 0 }
                }
                DpadButton("▼") { if (dir != 0 to -1) dir = 0 to 1 }
            }
        } else {
            SurfaceCard {
                Text("Игра окончена — очки $score", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Заново",
                    onClick = {
                        snake = listOf(7 to 7, 6 to 7, 5 to 7)
                        food = 10 to 7
                        dir = 1 to 0
                        score = 0
                        speed = 320L
                        alive = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = "К играм", onClick = onExit, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DpadButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1430))
            .border(1.dp, ChipBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GrapeVioletSoft, style = MaterialTheme.typography.titleLarge)
    }
}

// ————————————————————————————————————————————————
//  Проверка реакции
// ————————————————————————————————————————————————

@Composable
private fun ReactionGame(best: Int, onSave: (Int) -> Unit, onExit: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) } // 0 ждём, 1 готов, 2 рано, 3 результат
    var startAt by remember { mutableLongStateOf(0L) }
    var result by remember { mutableIntStateOf(0) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "reactionPulse"
    )

    LaunchedEffect(phase) {
        if (phase == 1) {
            val wait = Random.nextLong(900, 3200)
            delay(wait)
            if (phase == 1) {
                startAt = System.currentTimeMillis()
                phase = 2
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Рекорд: ${if (best == 0) "—" else "$best мс"}", color = TextDim, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when (phase) {
                        0 -> Brush.linearGradient(listOf(Color(0xFF1C1430), Color(0xFF241A3E)))
                        1 -> Brush.linearGradient(listOf(Color(0xFF3B2668), Color(0xFF5B21B6)))
                        2 -> Brush.linearGradient(listOf(GrapeGreen, GrapeViolet))
                        else -> Brush.linearGradient(listOf(Color(0xFF166534), GrapeGreen))
                    }
                )
                .border(1.dp, ChipBorder, RoundedCornerShape(24.dp))
                .clickable {
                    when (phase) {
                        0 -> phase = 1
                        1 -> {
                            phase = 3
                            result = 0
                        }
                        2 -> {
                            val ms = (System.currentTimeMillis() - startAt).toInt()
                            result = ms
                            onSave(ms)
                            phase = 3
                        }
                        else -> phase = 0
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (phase) {
                    0 -> "Нажмите, чтобы начать"
                    1 -> "Ждите зелёного…"
                    2 -> "ЖМИ!"
                    else -> if (result == 0) "Рано! Попробуйте снова" else "$result мс"
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.scale(if (phase == 2) pulse else 1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when (phase) {
                0 -> "Правило простое: как только поле станет зелёным — жмите как можно быстрее."
                1 -> "Держите палец наготове."
                2 -> "Сейчас!"
                else -> if (result == 0) "Слишком рано." else "Отличный результат!"
            },
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(14.dp))
        SecondaryButton(text = "К играм", onClick = onExit, modifier = Modifier.fillMaxWidth())
    }
}

/** Общая рамка полноэкранного раздела: фон, заголовок с кнопкой «назад». */
@Composable
fun GrapeScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedGrapeBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                GradientText(text = title, style = MaterialTheme.typography.headlineMedium, brush = GrapeGradient)
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}
