package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.NdefHceService
import com.gsynk.app.core.NfcBurstOverlay
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.NfcReader
import com.gsynk.app.core.RogueGame
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.core.WifiCoop
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сопряжение для подземелья на два телефона.
 *
 * NFC здесь — рукопожатие: хозяин касанием отдаёт адрес комнаты, порт и код.
 * Дальше весь поток игры идёт по Wi-Fi, потому что NFC для кадров слишком медленный.
 * Если NFC подвёл, тот же адрес и код видно на экранах обоих — можно вписать руками.
 */
@Composable
fun CoopScreen(
    onBack: () -> Unit,
    autoInvite: NfcLink.GameInvite? = null,
    /** Открыть комнату сразу при входе — так делает касание в режиме «мой телефон отдаёт». */
    autoHost: Boolean = false,
    onPlay: (role: WifiCoop.Role, seed: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val adapter = remember(context) { runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() }
    val burst = remember(context) { NfcBurstOverlay(context) }

    val status by WifiCoop.status.collectAsState()

    var side by remember { mutableIntStateOf(if (autoInvite?.isWifi == true) 1 else 0) } // 0 хозяин, 1 гость
    var seed by remember { mutableStateOf(autoInvite?.seed?.takeIf { it > 0 } ?: RogueGame.newSeed()) }
    var address by remember { mutableStateOf(autoInvite?.host.orEmpty()) }
    var portText by remember {
        mutableStateOf(autoInvite?.port?.takeIf { it > 0 }?.toString() ?: WifiCoop.PORT.toString())
    }
    var code by remember { mutableStateOf(autoInvite?.code.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var touches by remember { mutableIntStateOf(0) }

    val model = remember { android.os.Build.MODEL ?: "GSYNK" }

    // Касание в режиме «отдаю»: комната открывается сама, а вызов уже лежит в метке —
    // человеку остаётся только приложить телефон к телефону друга.
    LaunchedEffect(autoHost) {
        if (!autoHost) return@LaunchedEffect
        if (WifiCoop.status.value is WifiCoop.Status.Live) return@LaunchedEffect
        side = 0
        WifiCoop.host(model, seed)
        note = "Комната открыта. Приложите телефон к телефону друга — и игра начнётся сама."
        // ждём, пока появится наш адрес и порт, и кладём приглашение в метку
        repeat(20) {
            val current = WifiCoop.status.value
            if (current is WifiCoop.Status.Listening) {
                val invite = NfcLink.GameInvite(
                    game = "rogue",
                    from = model,
                    seed = current.seed,
                    host = current.ip,
                    port = current.port,
                    code = current.code
                )
                NdefHceService.store(context, NfcLink.buildGameInvite(invite, context.packageName))
                NdefHceService.setEnabled(context, true)
                burst.show(2_200L)
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(150L)
        }
    }

    // Связь есть — сразу в бой, чтобы касание действительно запускало игру.
    LaunchedEffect(status) {
        val live = status as? WifiCoop.Status.Live ?: return@LaunchedEffect
        if (!autoHost) return@LaunchedEffect
        delay(900L)
        onPlay(live.role, live.seed)
    }

    // Пришли по NFC — сразу стучимся в комнату, руками ничего вводить не надо.
    LaunchedEffect(autoInvite?.host, autoInvite?.port) {
        val invite = autoInvite ?: return@LaunchedEffect
        if (!invite.isWifi) return@LaunchedEffect
        side = 1
        note = "Адрес получен по NFC — подключаемся к «${invite.from}»"
        WifiCoop.join(invite.host, invite.port, invite.code, model)
    }

    fun syncTouches() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val repository = RuleRepository(context)
                repository.saveSettings(
                    repository.currentSettings()
                        .copy(nfcTouches = repository.currentSettings().nfcTouches + 1)
                )
            }
            touches++
        }
    }

    // ————— приём меток: приложил телефон — узнал адрес комнаты —————
    DisposableEffect(activity, side, adapter) {
        val nfc = adapter
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val result = runCatching { NfcReader.read(tag) }
                .getOrElse { NfcReader.Result.Failed("ошибка чтения") }
            activity?.runOnUiThread {
                val payload = (result as? NfcReader.Result.Ok)?.let { NfcLink.parse(it.message) }
                val invite = payload?.takeIf { it.kind == NfcLink.KIND_GAME }
                    ?.let { NfcLink.parseGameInvite(it.body) }
                when {
                    payload == null ->
                        note = (result as? NfcReader.Result.Failed)?.reason ?: "Метка не распознана"

                    invite != null && invite.isWifi -> {
                        burst.show(3_200L)
                        AutomationActions.vibrate(context, 120L)
                        address = invite.host
                        portText = invite.port.toString()
                        code = invite.code
                        seed = invite.seed
                        note = "Касание принято: хозяин «${invite.from}» ждёт на ${invite.host}"
                        syncTouches()
                        WifiCoop.join(invite.host, invite.port, invite.code, model)
                    }

                    else -> {
                        burst.show(3_200L)
                        note = "Метка прочитана, но это не вызов в подземелье"
                    }
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        if (activity != null && nfc != null && side == 1) {
            // приём и эмуляция карты мешают друг другу: пока слушаем — не отдаём
            NdefHceService.setEnabled(context, false)
            runCatching { nfc.enableReaderMode(activity, callback, flags, android.os.Bundle()) }
        }
        onDispose {
            if (activity != null && nfc != null) runCatching { nfc.disableReaderMode(activity) }
            // уходя с сопряжения, телефон больше не притворяется меткой:
            // иначе NFC-касания перестают работать до перезапуска приложения
            NdefHceService.setEnabled(context, false)
        }
    }

    LaunchedEffect(Unit) {
        touches = withContext(Dispatchers.IO) {
            RuleRepository(context).currentSettings().nfcTouches
        }
        if (autoInvite != null) PendingNfcPayload.take()
    }

    GrapeScreen(
        title = "Grape Link · Wi-Fi",
        subtitle = "касаний: $touches • NFC ${if (adapter == null) "не найден" else "готов"}",
        onBack = {
            WifiCoop.stop()
            onBack()
        }
    ) {
        // Своей вертикальной прокрутки здесь быть не должно: GrapeScreen уже
        // прокручивает содержимое, а вложенный скролл падал с
        // «Vertically scrollable component was measured with an infinity maximum height».
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                SortChip("я хозяин мира", side == 0, Modifier.weight(1f)) {
                    side = 0
                    WifiCoop.stop()
                }
                SortChip("я гость", side == 1, Modifier.weight(1f)) {
                    side = 1
                    WifiCoop.stop()
                }
            }

            when (val current = status) {
                is WifiCoop.Status.Listening -> CoopCard("Комната открыта", GrapeGreen) {
                    InfoRow("Адрес", "${current.ip}:${current.port}", GrapeCyan)
                    InfoRow("Код комнаты", current.code, GrapeVioletSoft)
                    InfoRow("Ключ подземелья", current.seed.toString())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Оба телефона — в одной сети Wi-Fi. Гость прикладывает телефон " +
                            "к задней панели: адрес и код уедут сами, игра пойдёт по локальной сети.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Передать вызов по NFC",
                        onClick = {
                            val invite = NfcLink.GameInvite(
                                game = "rogue",
                                from = model,
                                seed = current.seed,
                                host = current.ip,
                                port = current.port,
                                code = current.code
                            )
                            NdefHceService.store(
                                context,
                                NfcLink.buildGameInvite(invite, context.packageName)
                            )
                            NdefHceService.setEnabled(context, true)
                            burst.show(2_600L)
                            note = "Приложите телефон к панели гостя и держите до вспышки"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = "Закрыть комнату",
                        onClick = { WifiCoop.stop() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is WifiCoop.Status.Connecting -> CoopCard("Стучимся к хозяину", GrapeAmber) {
                    InfoRow("Адрес", "${current.host}:${current.port}", GrapeCyan)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(GrapeAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ждём ответа: хозяин должен держать комнату открытой",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }

                is WifiCoop.Status.Live -> CoopCard(
                    title = if (current.role == WifiCoop.Role.HOST)
                        "Второй телефон на связи" else "Связь с хозяином есть",
                    accent = GrapeGreen
                ) {
                    InfoRow("Кто рядом", current.peer)
                    InfoRow("Канал", current.address, GrapeCyan)
                    InfoRow("Ключ подземелья", current.seed.toString())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Каждый ведёт своего героя на своём телефоне. Мир считает хозяин, " +
                            "гость видит ту же комнату.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "В подземелье",
                        onClick = { onPlay(current.role, current.seed) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is WifiCoop.Status.Failed -> CoopCard("Не получилось", GrapeRed) {
                    Text(current.reason, color = TextPrimary, style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Попробовать снова",
                        onClick = { WifiCoop.stop() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                WifiCoop.Status.Idle -> Unit
            }

            if (side == 0) {
                CoopCard("Хозяин мира", GrapeVioletSoft) {
                    Text(
                        text = "Телефон хозяина считает подземелье: врагов, слёзы, счёт. " +
                            "Второй телефон рисует ту же комнату и шлёт только свой стик.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(10.dp))
                    InfoRow("Мой адрес", WifiCoop.localAddress(), GrapeCyan)
                    InfoRow("Порт", WifiCoop.PORT.toString())
                    InfoRow("Ключ подземелья", seed.toString())
                    Spacer(Modifier.height(10.dp))
                    SortChip("новый ключ", selected = false) {
                        seed = RogueGame.newSeed()
                        note = "Ключ обновлён — он уйдёт и по NFC, и в открытую комнату"
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Открыть комнату",
                        onClick = {
                            WifiCoop.host(model, seed)
                            note = "Комната ждёт второго игрока"
                        },
                        enabled = status !is WifiCoop.Status.Listening,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (status is WifiCoop.Status.Listening) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(GrapeGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ждём подключения второго телефона",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                            )
                        }
                    }
                }
            } else {
                CoopCard("Гость", GrapeCyan) {
                    Text(
                        text = "Проще всего — коснуться телефоном панели хозяина: адрес, порт и код " +
                            "придут сами. Если NFC не сработал, впишите адрес вручную.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(10.dp))
                    GrapeTextField(
                        value = address,
                        onValueChange = { address = it.trim() },
                        label = "адрес хозяина (192.168.x.x)"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrapeTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = "порт",
                            modifier = Modifier.weight(1f)
                        )
                        GrapeTextField(
                            value = code,
                            onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = "код комнаты",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Подключиться",
                        onClick = {
                            val targetPort = portText.toIntOrNull() ?: WifiCoop.PORT
                            WifiCoop.join(address, targetPort, code, model)
                            note = "Стучимся к $address:$targetPort"
                        },
                        enabled = address.isNotBlank() && status !is WifiCoop.Status.Connecting,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (adapter == null) {
                            "NFC на этом телефоне не найден — остаётся ручной ввод адреса."
                        } else {
                            "Держите этот экран открытым и приложите телефон к панели хозяина."
                        },
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
            }

            if (note.isNotBlank()) {
                Text(note, color = GrapeGreen, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Игра вдвоём за одним телефоном осталась на месте: на экране старта " +
                    "выберите «вдвоём за телефоном».",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Карточка с острым углом, цветной засечкой слева и заголовком. */
@Composable
private fun CoopCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceCard(borderColor = accent.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(accent)
            ) { }
            Spacer(Modifier.width(8.dp))
            Text(title, color = accent, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
