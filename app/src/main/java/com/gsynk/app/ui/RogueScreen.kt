package com.gsynk.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.GameScores
import com.gsynk.app.core.RogueGame
import com.gsynk.app.core.WifiCoop
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeBlue
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Подземелье GSYNK — главная функция Grape Link.
 *
 * Настоящая игра, а не проверка реакции: комнаты, камни, враги, босс, предметы,
 * этажи и счёт. Поле рисуется в верхней полосе экрана — там, где фронтальная
 * камера, — а под ним остаются удобные квадратные джойстики. Два игрока за одним
 * телефоном: левый и правый джойстик.
 *
 * Ключ подземелья можно передать второму телефону по NFC: тогда оба играют одну
 * и ту же карту и сравнивают результат.
 */
@Composable
fun RogueScreen(
    onBack: () -> Unit,
    invite: NfcLinkInvite? = null,
    onInviteByNfc: (seed: Long) -> Unit = {},
    /** Сетевой режим: хозяин считает мир, гость его рисует. Null — как раньше, на одном телефоне. */
    coop: WifiCoop.Role? = null,
    /** Из сетевого режима: перейти к сопряжению (кнопка «позвать по Wi-Fi»). */
    onCoopSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val scores = remember { GameScores(context) }
    val scope = rememberCoroutineScope()

    val coopStatus by WifiCoop.status.collectAsState()
    val coopSeed by WifiCoop.seed.collectAsState()
    var players by remember {
        mutableIntStateOf(if (invite != null || coop != null) 2 else 1)
    }
    var seed by remember {
        mutableStateOf(invite?.seed ?: coopSeed.takeIf { it > 0 } ?: RogueGame.newSeed())
    }
    var world by remember { mutableStateOf<RogueGame.World?>(null) }
    var best by remember { mutableIntStateOf(0) }
    var savedResult by remember { mutableStateOf(false) }

    // вектор каждого игрока: нормализованный стик
    var moveOneX by remember { mutableFloatStateOf(0f) }
    var moveOneY by remember { mutableFloatStateOf(0f) }
    var moveTwoX by remember { mutableFloatStateOf(0f) }
    var moveTwoY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        scores.rogueScore.collect { best = it }
    }

    // ————— игровой цикл: кадр в кадр, как настоящий движок —————
    LaunchedEffect(world != null, coop) {
        if (coop == WifiCoop.Role.GUEST) return@LaunchedEffect
        if (world == null) return@LaunchedEffect
        var previous = 0L
        var lastSent = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous == 0L) previous = now
                val dt = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val current = world ?: return@withFrameNanos
                val myInput = RogueGame.Input(
                    moveOneX,
                    moveOneY,
                    shooting = kotlin.math.hypot(moveOneX, moveOneY) > 0.15f
                )
                val secondInput = if (coop == WifiCoop.Role.HOST) {
                    WifiCoop.peerInput.value
                } else {
                    RogueGame.Input(
                        moveTwoX,
                        moveTwoY,
                        shooting = kotlin.math.hypot(moveTwoX, moveTwoY) > 0.15f
                    )
                }
                val next = RogueGame.step(current, dt, listOf(myInput, secondInput))
                world = next
                // хозяин рассылает картину комнаты тридцать раз в секунду — этого хватает,
                // чтобы гость видел плавную картинку и не жевал батарею
                if (coop == WifiCoop.Role.HOST && now - lastSent > 33_000_000L) {
                    lastSent = now
                    WifiCoop.publish(next)
                }
            }
        }
    }

    // ————— гость: свой стик уходит хозяину, мир приходит от него —————
    LaunchedEffect(coop) {
        if (coop != WifiCoop.Role.GUEST) return@LaunchedEffect
        var lastSent = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - lastSent > 33_000_000L) {
                    lastSent = now
                    WifiCoop.sendInput(
                        RogueGame.Input(
                            moveOneX,
                            moveOneY,
                            shooting = kotlin.math.hypot(moveOneX, moveOneY) > 0.15f
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(coop) {
        if (coop != WifiCoop.Role.GUEST) return@LaunchedEffect
        WifiCoop.remoteWorld.collect { remote ->
            if (remote != null) world = remote
        }
    }

    // запись результата
    val finished = world?.over == true
    LaunchedEffect(finished) {
        if (!finished || savedResult) return@LaunchedEffect
        savedResult = true
        val result = world ?: return@LaunchedEffect
        scores.saveRogue(result.score)
        scores.addPlayed()
        if (result.players.size > 1) {
            val winner = result.players.maxByOrNull { it.kills }
            if (winner != null) scores.saveWin()
            AppLog.event(
                "Игра",
                "подземелье: счёт ${result.score}, этаж ${result.floor}, победитель — игрок ${(winner?.id ?: 0) + 1}"
            )
        } else {
            AppLog.event("Игра", "подземелье: счёт ${result.score}, этаж ${result.floor}")
        }
    }

    BackHandler(enabled = true) {
        if (world != null && !finished) {
            AppLog.event("Игра", "выход из подземелья на этаже ${world?.floor}")
            world = null
        } else {
            onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(com.gsynk.app.ui.theme.BgTopA)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ————— поле у фронтальной камеры —————
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1.42f)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val current = world
                    if (current == null) {
                        drawCover(size, seed)
                        return@Canvas
                    }
                    drawWorld(current, size)
                }

                val snapshot = world
                if (snapshot != null) {
                    HudBar(
                        world = snapshot,
                        best = best,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .align(Alignment.TopStart)
                    )
                    if (snapshot.bannerTime > 0f) {
                            Text(
                                text = snapshot.banner,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                    }
                }
            }

            // ————— низ: управление и кнопки —————
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CardBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val current = world
                if (current == null) {
                    if (coop == WifiCoop.Role.GUEST) {
                        WaitingPanel(
                            status = coopStatus,
                            onCoopSetup = onCoopSetup,
                            onLeave = {
                                WifiCoop.stop()
                                onBack()
                            }
                        )
                    } else {
                        StartPanel(
                            seed = seed,
                            players = players,
                            coop = coop,
                            peerName = (coopStatus as? WifiCoop.Status.Live)?.peer.orEmpty(),
                            onPlayers = { players = it },
                            onSeed = { seed = it },
                            onInvite = { onInviteByNfc(seed) },
                            onCoop = onCoopSetup,
                            onStart = {
                                AppLog.event("Игра", "стартуем подземелье, игроков: $players, ключ $seed")
                                val count = if (coop != null) 2 else players
                                world = RogueGame.start(seed, count)
                                savedResult = false
                                if (coop == WifiCoop.Role.HOST) {
                                    WifiCoop.publish(world ?: RogueGame.start(seed, count))
                                }
                            }
                        )
                    }
                } else if (current.over) {
                    FinishPanel(
                        world = current,
                        best = best,
                        onAgain = {
                            seed = RogueGame.newSeed()
                            world = RogueGame.start(seed, players)
                            savedResult = false
                        },
                        onExit = { world = null }
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Joystick(
                            label = when {
                                coop == WifiCoop.Role.GUEST -> "мой герой"
                                coop == WifiCoop.Role.HOST -> "я, хозяин"
                                players > 1 -> "игрок 1"
                                else -> "ход"
                            },
                            color = if (players > 1 || coop != null) GrapeBlue else GrapeViolet,
                            modifier = Modifier.weight(1f),
                            onMove = { x, y -> moveOneX = x; moveOneY = y }
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                text = "слёзы летят туда, куда ведёт стик",
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                SortChip("Пауза", selected = false) { world = null }
                                SortChip("Заново", selected = false) {
                                    val count = if (coop != null) 2 else players
                                    val fresh = RogueGame.start(seed, count)
                                    world = fresh
                                    if (coop == WifiCoop.Role.HOST) WifiCoop.publish(fresh)
                                }
                            }
                            Text(
                                text = "ключ подземелья: $seed",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (coop != null) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "второй герой — на своём телефоне",
                                    color = GrapePink,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = when (val st = coopStatus) {
                                        is WifiCoop.Status.Live ->
                                            if (coop == WifiCoop.Role.HOST) "подключён: ${st.peer}"
                                            else "хозяин: ${st.peer}"
                                        is WifiCoop.Status.Failed -> st.reason
                                        else -> "связь по Wi-Fi"
                                    },
                                    color = TextDim,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .border(1.dp, ChipBorder, RectangleShape)
                                        .clickable {
                                            WifiCoop.stop()
                                            onBack()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "выйти\nиз сети",
                                        color = GrapeRed,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        } else if (players > 1) {
                            Joystick(
                                label = "игрок 2",
                                color = GrapePink,
                                modifier = Modifier.weight(1f),
                                onMove = { x, y -> moveTwoX = x; moveTwoY = y }
                            )
                        } else {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(1.dp, ChipBorder, RectangleShape)
                                    .clickable { onInviteByNfc(seed) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "позвать второго\nигрока по nfc",
                                    color = GrapeCyan,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Гость ещё не получил комнату: хозяин либо не нажал «в бой», либо связь пропала. */
@Composable
private fun WaitingPanel(
    status: WifiCoop.Status,
    onCoopSetup: () -> Unit,
    onLeave: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "ждём хозяина",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light
        )
        Text(
            text = "Как только на телефоне-хозяине нажмут «в бой», комната прилетит сюда " +
                "целиком: камни, враги, слёзы. Ваш стик уже работает — он уходит хозяину.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall
        )
        SurfaceCard(borderColor = ChipBorder) {
            InfoRow("Состояние", when (status) {
                is WifiCoop.Status.Live -> "связь есть, ждём старт"
                is WifiCoop.Status.Connecting -> "подключаемся"
                is WifiCoop.Status.Failed -> status.reason
                is WifiCoop.Status.Listening -> "хозяин открыл комнату"
                WifiCoop.Status.Idle -> "связи нет"
            }, GrapeCyan)
        }
        PrimaryButton(text = "К сопряжению", onClick = onCoopSetup, modifier = Modifier.fillMaxWidth())
        SecondaryButton(text = "Выйти из сети", onClick = onLeave, modifier = Modifier.fillMaxWidth())
    }
}

/** Приглашение, пришедшее по NFC: ключ подземелья и имя отправителя. */
data class NfcLinkInvite(val seed: Long, val from: String)

@Composable
private fun StartPanel(
    seed: Long,
    players: Int,
    coop: WifiCoop.Role?,
    peerName: String,
    onPlayers: (Int) -> Unit,
    onSeed: (Long) -> Unit,
    onInvite: () -> Unit,
    onCoop: () -> Unit,
    onStart: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "подземелье gsynk",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Light
        )
        Text(
            text = if (coop == null) {
                "Комнаты, камни, враги и босс сверху вниз — настоящая игра, а не проверка " +
                    "реакции. Поле живёт в полосе фронтальной камеры, управление — под большими пальцами."
            } else {
                "Сетевой бой: мир считает хозяин, второй телефон ведёт своего героя по Wi-Fi. " +
                    "Поле живёт в полосе фронтальной камеры у обоих."
            },
            color = TextDim,
            style = MaterialTheme.typography.bodySmall
        )
        if (coop == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SortChip("один игрок", players == 1) { onPlayers(1) }
                SortChip("вдвоём за телефоном", players == 2) { onPlayers(2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SortChip("новый ключ", selected = false) { onSeed(RogueGame.newSeed()) }
                SortChip("пригласить по nfc", selected = false) { onInvite() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SortChip("два телефона по wi-fi", selected = false) { onCoop() }
            }
            Text(
                text = "оба телефона с одним ключом получают одинаковое подземелье",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
                Text(
                    text = if (coop == WifiCoop.Role.HOST) "Вы хозяин мира" else "Вы гость",
                    color = GrapeGreen,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (coop == WifiCoop.Role.HOST) {
                        "Второй герой ведётся с телефона " + peerName.ifBlank { "соседа" } +
                            ". Ключ подземелья у обоих один."
                    } else {
                        "Мир считает хозяйский телефон. Ваш стик уходит ему, а сюда приходит готовая комната."
                    },
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        PrimaryButton(
            text = if (coop != null) "В бой · ключ $seed" else "Играть · ключ $seed",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FinishPanel(
    world: RogueGame.World,
    best: Int,
    onAgain: () -> Unit,
    onExit: () -> Unit
) {
    val winner = if (world.players.size > 1) world.players.maxByOrNull { it.kills } else null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (winner != null) "победил игрок ${winner.id + 1}" else "подземелье окончено",
            color = GrapeAmber,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light
        )
        Text(
            text = "счёт ${world.score} · этаж ${world.floor} · время ${world.time.toInt()} с" +
                if (best > 0) " · рекорд $best" else "",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        world.players.forEach { player ->
            Text(
                text = "игрок ${player.id + 1}: убито ${player.kills}, осталось жизней ${player.hp}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton(text = "Ещё раз", onClick = onAgain, modifier = Modifier.weight(1f))
            SecondaryButton(text = "В меню", onClick = onExit)
        }
    }
}

/** Квадратный стик: тянете палец — герой идёт, слёзы летят в ту же сторону. */
@Composable
private fun Joystick(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxSize()
            .border(1.dp, ChipBorder, RectangleShape)
            .pointerInput(Unit) {
                val radius = size.width / 2f
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        fun emit(position: Offset) {
                            val dx = ((position.x - center.x) / radius).coerceIn(-1f, 1f)
                            val dy = ((position.y - center.y) / radius).coerceIn(-1f, 1f)
                            knobX = dx
                            knobY = dy
                            onMove(dx, dy)
                        }
                        emit(down.position)
                        down.consume()
                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                active = false
                            } else {
                                emit(change.position)
                                change.consume()
                            }
                        }
                        knobX = 0f
                        knobY = 0f
                        onMove(0f, 0f)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.22f))
                .border(1.dp, color, RectangleShape)
        )
        Box(
            Modifier
                .size(26.dp)
                .offset { androidx.compose.ui.unit.IntOffset((knobX * 26 * 3f).toInt(), (knobY * 26 * 3f).toInt()) }
                .background(color)
        )
        Text(
            text = label,
            color = TextDim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        )
    }
}

@Composable
private fun HudBar(world: RogueGame.World, best: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column {
            world.players.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "и${player.id + 1}",
                        color = if (player.id == 0) GrapeBlue else GrapePink,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.width(5.dp))
                    repeat(player.maxHp.coerceAtMost(12)) { index ->
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(if (index < player.hp) GrapeRed else ChipBg)
                                .border(1.dp, ChipBorder, RectangleShape)
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "счёт ${world.score}",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "этаж ${world.floor} · комната ${world.room} / ${RogueGame.ROOMS_PER_FLOOR} " +
                    if (best > 0) "· рекорд $best" else "",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Заставка до начала игры: рамка-видоискатель и напоминание, где будет поле. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCover(canvasSize: Size, seed: Long) {
    val w = canvasSize.width
    val h = canvasSize.height
    drawRect(Color(0xFF0A0817))
    drawChecker(w, h)
    val bracket = w / 14f
    val top = h * 0.16f
    val bottom = h * 0.86f
    val stroke = 2.5f
    listOf(
        floatArrayOf(0f, top, bracket, top),
        floatArrayOf(0f, top, 0f, top + bracket),
        floatArrayOf(w - bracket, top, w, top),
        floatArrayOf(w, top, w, top + bracket),
        floatArrayOf(0f, bottom, bracket, bottom),
        floatArrayOf(0f, bottom - bracket, 0f, bottom),
        floatArrayOf(w - bracket, bottom, w, bottom),
        floatArrayOf(w, bottom - bracket, w, bottom)
    ).forEach { line ->
        drawLine(
            color = GrapeViolet.copy(alpha = 0.8f),
            start = Offset(line[0], line[1]),
            end = Offset(line[2], line[3]),
            strokeWidth = stroke
        )
    }
    // «здесь будет подземелье» — прямоугольники комнат вместо заглушки
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            if ((row + col) % 2 != 0) continue
            val left = w * (0.17f + col * 0.24f)
            val topEdge = h * (0.24f + row * 0.22f)
            drawRect(
                color = GrapeViolet.copy(alpha = 0.10f),
                topLeft = Offset(left, topEdge),
                size = Size(w * 0.18f, h * 0.15f)
            )
        }
    }
}

/** Пол подземелья: шахматка из прямоугольников в тон Metro. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(w: Float, h: Float) {
    val cells = 10
    val cw = w / cells
    val ch = h / cells
    for (row in 0 until cells) {
        for (col in 0 until cells) {
            val dark = (row + col) % 2 == 0
            drawRect(
                color = if (dark) Color(0xFF14112A) else Color(0xFF191533),
                topLeft = Offset(col * cw, row * ch),
                size = Size(cw, ch)
            )
        }
    }
}

/** Отрисовка мира: только прямые прямоугольники — в стиле всего приложения. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorld(world: RogueGame.World, canvasSize: Size) {
    val w = canvasSize.width
    val h = canvasSize.height
    drawChecker(w, h)

    fun px(x: Float) = x * w
    fun py(y: Float) = y * h

    // дверь справа
    drawRect(
        color = if (world.doorOpen) GrapeGreen.copy(alpha = 0.85f) else ChipBg,
        topLeft = Offset(w - w * 0.035f, py(0.36f)),
        size = Size(w * 0.03f, h * 0.28f)
    )

    // камни
    world.rocks.forEach { rock ->
        drawRect(
            color = Color(0xFF3A3454),
            topLeft = Offset(px(rock.x), py(rock.y)),
            size = Size(rock.w * w, rock.h * h)
        )
        drawRect(
            color = Color(0xFF4C4570),
            topLeft = Offset(px(rock.x), py(rock.y)),
            size = Size(rock.w * w, rock.h * h * 0.18f)
        )
    }

    // предметы
    world.pickups.forEach { pickup ->
        val color = when (pickup.kind) {
            RogueGame.PickupKind.HEART -> GrapeRed
            RogueGame.PickupKind.DAMAGE -> TextPrimary
            RogueGame.PickupKind.SPEED -> GrapeGreen
            RogueGame.PickupKind.RATE -> GrapeCyan
        }
        val size = w * 0.035f
        drawRect(color, Offset(px(pickup.x) - size / 2f, py(pickup.y) - size / 2f), Size(size, size))
        drawRect(
            Color.Black.copy(alpha = 0.45f),
            Offset(px(pickup.x) - size / 2f, py(pickup.y) - size / 2f),
            Size(size, size * 0.28f)
        )
    }

    // снаряды
    world.shots.forEach { shot ->
        val size = w * 0.022f
        drawRect(
            color = if (shot.hostile) GrapeRed else TextPrimary,
            topLeft = Offset(px(shot.x) - size / 2f, py(shot.y) - size / 2f),
            size = Size(size, size)
        )
    }

    // враги
    world.enemies.forEach { enemy ->
        val body = when (enemy.kind) {
            RogueGame.EnemyKind.CHASER -> GrapeRed
            RogueGame.EnemyKind.SHOOTER -> GrapeAmber
            RogueGame.EnemyKind.FLYER -> GrapePink
            RogueGame.EnemyKind.BOSS -> GrapeViolet
        }.let { if (enemy.hitFlash > 0f) Color.White else it }
        val size = enemy.size * w
        val left = px(enemy.x) - size / 2f
        val top = py(enemy.y) - size / 2f
        drawRect(body, Offset(left, top), Size(size, size))
        drawRect(
            Color.Black.copy(alpha = 0.55f),
            Offset(left, top + size * 0.62f),
            Size(size, size * 0.38f)
        )
        if (enemy.kind == RogueGame.EnemyKind.SHOOTER) {
            drawRect(Color.Black, Offset(left + size * 0.44f, top), Size(size * 0.12f, size))
        }
        if (enemy.kind == RogueGame.EnemyKind.BOSS) {
            drawRect(
                Color.Black.copy(alpha = 0.5f),
                Offset(left + size * 0.28f, top + size * 0.28f),
                Size(size * 0.44f, size * 0.2f)
            )
            // полоса здоровья босса
            val ratio = enemy.hp.toFloat() / enemy.maxHp.toFloat()
            drawRect(ChipBg, Offset(left, top - size * 0.16f), Size(size, size * 0.07f))
            drawRect(GrapeRed, Offset(left, top - size * 0.16f), Size(size * ratio, size * 0.07f))
        }
    }

    // игроки
    world.players.forEach { player ->
        val size = player.size * w
        val left = px(player.x) - size / 2f
        val top = py(player.y) - size / 2f
        val flash = player.hitFlash > 0f || (player.invulnerable > 0f && (player.invulnerable * 12f).toInt() % 2 == 0)
        val color = when {
            flash -> Color.White
            player.id == 0 -> GrapeBlue
            else -> GrapePink
        }
        drawRect(color, Offset(left, top), Size(size, size))
        if (player.down) {
            drawRect(Color.Black.copy(alpha = 0.6f), Offset(left, top), Size(size, size))
        } else {
            // глаз смотрит в сторону движения
            drawRect(
                Color(0xFF0B0817),
                Offset(left + size * 0.58f, top + size * 0.3f),
                Size(size * 0.2f, size * 0.2f)
            )
        }
    }
}
