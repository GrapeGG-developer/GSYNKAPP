package com.gsynk.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.GameScores
import com.gsynk.app.core.TileGame
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Мини-игры 2.1 — три законченные игры со своими рекордами:
 * «Реакция», «Память» (повтори последовательность) и «2048».
 */
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    /** Подземелье: null — одиночная игра, true — сопряжение для двух телефонов. */
    onDungeon: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scores = remember { GameScores(context) }
    var open by remember { mutableStateOf<String?>(null) }

    val reactionFlow = remember { scores.reaction }
    val memoryFlow = remember { scores.memory }
    val tilesFlow = remember { scores.tiles }
    var bestReaction by remember { mutableIntStateOf(0) }
    var bestMemory by remember { mutableIntStateOf(0) }
    var bestTiles by remember { mutableIntStateOf(0) }
    var played by remember { mutableIntStateOf(0) }
    var duelWins by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        launch { reactionFlow.collect { bestReaction = it } }
        launch { memoryFlow.collect { bestMemory = it } }
        launch { tilesFlow.collect { bestTiles = it } }
        launch { scores.played.collect { played = it } }
        launch { scores.wins.collect { duelWins = it } }
    }

    when (open) {
        "reaction" -> ReactionGame(scores) { open = null }
        "memory" -> MemoryGame(scores) { open = null }
        "tiles" -> TilesGame(scores) { open = null }
    }
    if (open != null) return

    GrapeScreen(title = "Мини-игры", subtitle = "рекорды сохраняются на телефоне", onBack = onBack) {
        LiveTile(
            title = "Подземелье GSYNK",
            subtitle = "комнаты, враги и босс — поле у камеры",
            brush = TileBrush.Red,
            iconRes = R.drawable.ic_bolt,
            liveText = "играть одному или на два телефона",
            height = 112.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onDungeon(false) }
        )
        Spacer(Modifier.height(10.dp))
        LiveTile(
            title = "Два телефона по Wi-Fi",
            subtitle = "NFC — знакомство, игра — по сети",
            brush = TileBrush.Green,
            iconRes = R.drawable.ic_devices,
            liveText = "каждый ведёт своего героя",
            height = 112.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onDungeon(true) }
        )
        Spacer(Modifier.height(14.dp))
        LiveTile(
            title = "Реакция",
            subtitle = "насколько быстрая рука",
            brush = TileBrush.Cyan,
            iconRes = R.drawable.ic_bolt,
            liveText = if (bestReaction > 0) "рекорд $bestReaction мс" else "ещё не играли",
            height = 104.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { open = "reaction" }
        )
        Spacer(Modifier.height(12.dp))
        LiveTile(
            title = "Память",
            subtitle = "повтори последовательность",
            brush = TileBrush.Violet,
            iconRes = R.drawable.ic_shield,
            liveText = if (bestMemory > 0) "уровень $bestMemory" else "ещё не играли",
            height = 104.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { open = "memory" }
        )
        Spacer(Modifier.height(12.dp))
        LiveTile(
            title = "2048",
            subtitle = "складывайте плитки свайпами",
            brush = TileBrush.Amber,
            iconRes = R.drawable.ic_schedule,
            liveText = if (bestTiles > 0) "рекорд $bestTiles" else "ещё не играли",
            height = 104.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { open = "tiles" }
        )
        Spacer(Modifier.height(14.dp))
        SurfaceCard {
            InfoRow("Сыграно партий", played.toString())
            InfoRow("Побед в дуэлях", duelWins.toString())
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Рекорды хранятся на телефоне и показываются на острове. " +
                    "Вызвать друга на дуэль можно касанием в Grape Link.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  1. Реакция: ждём смену цвета и жмём как можно быстрее
// ————————————————————————————————————————————————

@Composable
private fun ReactionGame(scores: GameScores, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var waiting by remember { mutableStateOf(false) }
    var lit by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Нажмите «Старт»") }
    var rounds by remember { mutableIntStateOf(0) }

    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        lit = false
        delay(Random.nextLong(1200L, 4200L))
        startedAt = System.currentTimeMillis()
        lit = true
    }

    fun tap() {
        when {
            !waiting -> {
                waiting = true
                message = "Ждите зелёного…"
            }

            waiting && !lit -> {
                waiting = false
                message = "Рано! Дождитесь цвета."
            }

            else -> {
                val ms = (System.currentTimeMillis() - startedAt).toInt()
                lastMs = ms
                waiting = false
                lit = false
                rounds++
                message = "$ms мс" + when {
                    ms < 180 -> " — реакция киборга"
                    ms < 260 -> " — отличный результат"
                    ms < 350 -> " — хорошо"
                    else -> " — можно быстрее"
                }
                scope.launch { scores.saveReaction(ms); scores.addPlayed() }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    GrapeScreen(title = "Реакция", subtitle = "попыток: $rounds", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RectangleShape)
                .background(
                    when {
                        lit -> TileBrush.Green
                        waiting -> TileBrush.Ink
                        else -> TileBrush.Violet
                    }
                )
                .border(1.dp, ChipBorder, RectangleShape)
                .clickable { tap() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        lit -> "ЖМИ!"
                        waiting -> "ждите…"
                        lastMs > 0 -> "$lastMs мс"
                        else -> "СТАРТ"
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light),
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (waiting && !lit) "появится цвет — и сразу касание" else message,
                    color = TextPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            InfoRow("Последний результат", if (lastMs > 0) "$lastMs мс" else "—")
            InfoRow("Человеческий предел", "≈150 мс")
        }
    }
}

// ————————————————————————————————————————————————
//  2. Память: повтори растущую последовательность
// ————————————————————————————————————————————————

@Composable
private fun MemoryGame(scores: GameScores, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val palette = listOf(TileBrush.Violet, TileBrush.Cyan, TileBrush.Green, TileBrush.Amber)

    var sequence by remember { mutableStateOf(listOf<Int>()) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var showing by remember { mutableIntStateOf(-1) }
    var level by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Нажмите «Начать»") }

    fun nextRound() {
        scope.launch {
            busy = true
            val next = sequence + Random.nextInt(4)
            sequence = next
            inputIndex = 0
            level = next.size
            status = "Смотрите…"
            delay(420)
            next.forEach { pad ->
                showing = pad
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(420)
                showing = -1
                delay(180)
            }
            status = "Ваш ход: ${next.size} шагов"
            busy = false
        }
    }

    fun press(pad: Int) {
        if (busy) return
        if (sequence.isEmpty()) {
            nextRound()
            return
        }
        showing = pad
        scope.launch {
            delay(160)
            showing = -1
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (sequence.getOrNull(inputIndex) == pad) {
            inputIndex++
            if (inputIndex == sequence.size) {
                scope.launch {
                    scores.saveMemory(level)
                    scores.addPlayed()
                    status = "Верно! Уровень ${level + 1}"
                    nextRound()
                }
            }
        } else {
            scope.launch {
                status = "Ошибка. Дошли до уровня $level"
                sequence = emptyList()
                inputIndex = 0
                if (level > 0) scores.saveMemory(level)
                level = 0
            }
        }
    }

    GrapeScreen(title = "Память", subtitle = "уровень $level", onBack = onBack) {
        Text(status, color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { column ->
                        val pad = row * 2 + column
                        val active = showing == pad
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RectangleShape)
                                .background(if (active) palette[pad] else TileBrush.Ink)
                                .border(
                                    1.dp,
                                    if (active) Color.White.copy(alpha = 0.5f) else ChipBorder,
                                    RectangleShape
                                )
                                .clickable { press(pad) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = if (sequence.isEmpty()) "Начать" else "Заново",
                onClick = {
                    sequence = emptyList()
                    level = 0
                    inputIndex = 0
                    nextRound()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  3. 2048: складываем плитки свайпами
// ————————————————————————————————————————————————




@Composable
private fun TilesGame(scores: GameScores, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var board by remember { mutableStateOf(TileGame.start()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var dragSum by remember { mutableStateOf(0f to 0f) }

    LaunchedEffect(Unit) {
        scores.tiles.collect { best = it }
    }

    fun slide(direction: Int) {
        if (over) return
        val (moved, gained) = TileGame.slide(board, direction)
        if (moved == board) return
        val withTile = TileGame.spawn(moved)
        board = withTile
        score += gained
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { scores.saveTiles(score) }
        if (TileGame.isStuck(withTile)) {
            over = true
            scope.launch { scores.saveTiles(score); scores.addPlayed() }
        }
    }

    fun tileColor(value: Int): Brush = when {
        value == 0 -> TileBrush.Ink
        value <= 4 -> TileBrush.Blue
        value <= 16 -> TileBrush.Cyan
        value <= 64 -> TileBrush.Green
        value <= 256 -> TileBrush.Amber
        else -> TileBrush.Pink
    }

    GrapeScreen(title = "2048", subtitle = "счёт $score • рекорд $best", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RectangleShape)
                .background(CardBg)
                .border(1.dp, CardBorder, RectangleShape)
                .padding(8.dp)
                .pointerInput(over) {
                    detectDragGestures(
                        onDragEnd = {
                            val (dx, dy) = dragSum
                            if (dx * dx + dy * dy > 3600f) {
                                slide(
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        if (dx > 0) 1 else 0
                                    } else {
                                        if (dy > 0) 3 else 2
                                    }
                                )
                            }
                            dragSum = 0f to 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragSum = (dragSum.first + amount.x) to (dragSum.second + amount.y)
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { column ->
                            val value = board[row * 4 + column]
                            val appear = remember(value) { Animatable(if (value == 0) 0.94f else 1f) }
                            LaunchedEffect(value) {
                                if (value != 0) {
                                    appear.snapTo(0.86f)
                                    appear.animateTo(1f, tween(140))
                                }
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .scale(appear.value)
                                    .clip(RectangleShape)
                                    .background(tileColor(value)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (value != 0) {
                                    Text(
                                        text = value.toString(),
                                        color = TextPrimary,
                                        style = if (value < 1000) {
                                            MaterialTheme.typography.headlineSmall
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (over) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RectangleShape)
                        .background(Color(0xE60B0714))
                        .clickable {
                            board = TileGame.start()
                            score = 0
                            over = false
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ходов нет", color = TextPrimary, style = MaterialTheme.typography.titleLarge.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light))
                    Spacer(Modifier.height(6.dp))
                    Text("Счёт $score • нажмите для новой игры", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SecondaryButton(
                text = "Влево",
                onClick = { slide(0) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Вверх",
                onClick = { slide(2) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Вправо",
                onClick = { slide(1) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Вниз",
                onClick = { slide(3) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Свайпайте по полю или пользуйтесь кнопками. Одинаковые плитки соединяются.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
        )
    }
}
