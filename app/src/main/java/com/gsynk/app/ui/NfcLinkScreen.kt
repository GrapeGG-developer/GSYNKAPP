package com.gsynk.app.ui

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.BuildConfig
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.FileLink
import com.gsynk.app.core.NdefHceService
import com.gsynk.app.core.NfcBurstOverlay
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.NfcReader
import com.gsynk.app.core.NfcShow
import com.gsynk.app.core.NfcTagWriter
import com.gsynk.app.core.PayloadCodec
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.core.Passwords
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Grape Link — весь NFC в одном экране.
 *
 * Раньше здесь было три вкладки и десяток кнопок, которые делали почти одно и то же.
 * Теперь так: сверху один переключатель роли («мой телефон отдаёт» или «читаю»),
 * ниже четыре плитки — игра, файл, сценарий и метка, и короткая карточка данных.
 * Одно касание всегда делает одно понятное дело, а подсказка говорит какое.
 */
@Composable
fun NfcLinkScreen(
    onBack: () -> Unit,
    onJoinGame: (NfcLink.GameInvite) -> Unit = {},
    onHostGame: () -> Unit = {},
    /** Что положить в касание, если выбран пункт «Обновление GSYNK». */
    provideUpdateShare: () -> com.gsynk.app.core.UpdateShare.Share = {
        com.gsynk.app.core.UpdateShare.Share(
            version = "v" + BuildConfig.VERSION_NAME,
            url = com.gsynk.app.core.UpdateShare.apkUrl(
                "GrapeGG-developer/GSYNKAPP",
                "v" + BuildConfig.VERSION_NAME
            ),
            from = android.os.Build.MODEL
        )
    },
    /** Касание принесло просьбу или описание обновления — проверить версию. */
    onCheckUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val adapter = remember(context) { runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() }
    val burst = remember(context) { NfcBurstOverlay(context) }

    var giving by remember { mutableIntStateOf(0) } // 0 — мой телефон отдаёт, 1 — читаю касание
    var panel by remember { mutableStateOf("game") }
    var status by remember { mutableStateOf("") }
    var received by remember { mutableStateOf<NfcLink.Payload?>(null) }
    var touches by remember { mutableIntStateOf(0) }

    // данные для передачи
    var dataKind by remember { mutableStateOf(NfcLink.KIND_TEXT) }
    var dataTitle by remember { mutableStateOf("") }
    var dataBody by remember { mutableStateOf("") }

    // сценарий «покажи/сделай»
    var sceneKind by remember { mutableStateOf(NfcShow.Kind.FIREWORKS) }
    var sceneText by remember { mutableStateOf("ПРИВЕТ ОТ GSYNK") }

    // метка
    var tagAction by remember { mutableIntStateOf(0) } // 0 — прочитать, 1 — записать
    var tagInfo by remember { mutableStateOf("") }
    var writing by remember { mutableStateOf<NdefMessage?>(null) }

    val fileState by FileLink.state.collectAsState()

    LaunchedEffect(Unit) {
        touches = withContext(Dispatchers.IO) {
            RuleRepository(context).currentSettings().nfcTouches
        }
        val pending = PendingNfcPayload.take()
        if (pending != null) {
            received = pending
            status = "Касание принято: " + NfcLink.describe(pending)
        }
    }

    fun bumpTouches() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val repository = RuleRepository(context)
                repository.saveSettings(
                    repository.currentSettings().copy(nfcTouches = repository.currentSettings().nfcTouches + 1)
                )
            }
            touches++
        }
    }

    fun nfcMessage(payload: NfcLink.Payload): NdefMessage =
        NfcLink.buildMessage(payload, context.packageName)

    fun putToTouch(payload: NfcLink.Payload) {
        NdefHceService.store(context, nfcMessage(payload))
        NdefHceService.setEnabled(context, true)
        giving = 0
        burst.show(2_400L)
        AppLog.event("Grape Link", "в касание положено: " + payload.kind)
    }

    // ————— что происходит, когда телефон касается —————

    /** Касание: либо записываем метку, либо читаем то, что нам дали. */
    val tagWriteRequest = rememberUpdatedState(writing)

    DisposableEffect(activity, adapter, giving) {
        val nfc = adapter
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val request = tagWriteRequest.value
            if (request != null) {
                val result = NfcTagWriter.write(tag, request)
                activity?.runOnUiThread {
                    when (result) {
                        is NfcTagWriter.Result.Ok -> {
                            status = "Записано на метку: " + result.bytes + " байт. Приложите её к любому телефону."
                            AppLog.event("Grape Link", "метка записана, ${result.bytes} байт")
                        }

                        is NfcTagWriter.Result.Failed -> status = "Метка не записалась: " + result.reason
                    }
                    writing = null
                    tagInfo = NfcTagWriter.describe(tag)
                }
                return@ReaderCallback
            }

            val result = runCatching { NfcReader.read(tag) }
                .getOrElse { NfcReader.Result.Failed("ошибка чтения") }
            activity?.runOnUiThread {
                tagInfo = NfcTagWriter.describe(tag)
                val payload = (result as? NfcReader.Result.Ok)?.let { NfcLink.parse(it.message) }
                if (payload == null) {
                    status = (result as? NfcReader.Result.Failed)?.reason ?: "Метка не распознана"
                    return@runOnUiThread
                }
                burst.show(3_200L)
                AutomationActions.vibrate(context, 110L)
                bumpTouches()
                when (payload.kind) {
                    // 1) игра: касание = сразу подключаемся и запускаем GTBOI
                    NfcLink.KIND_GAME -> {
                        val invite = NfcLink.parseGameInvite(payload.body)
                        if (invite != null && invite.isWifi) {
                            status = "Хозяин «${invite.from}» ждёт — подключаемся и запускаем GTBOI"
                            onJoinGame(invite)
                        } else {
                            status = "Старый вызов без адреса комнаты — начните игру заново"
                        }
                    }

                    // 2) файл
                    NfcLink.KIND_FILE -> {
                        val invite = NfcLink.parseFileInvite(payload.body)
                        if (invite == null) {
                            status = "Приглашение на файл испорчено"
                        } else if (invite.isInline) {
                            status = "Файл «" + invite.name + "» уже в метке — сохраняю"
                            FileLink.readInline(context, invite.name, invite.size, invite.inline)
                        } else {
                            status = "Принимаю «" + invite.name + "» по Wi-Fi…"
                            FileLink.receive(
                                context, invite.host, invite.port, invite.token, invite.name, invite.size
                            )
                        }
                    }

                    // 3) сценарий: салют, сердечки, вспышка, надпись или команда
                    NfcLink.KIND_SHOW -> {
                        val decoded = NfcShow.decode(payload.body)
                        if (decoded == null) {
                            status = "Сценарий не понят"
                        } else {
                            NfcShow.show(decoded.first, decoded.second)
                            status = "Показ: " + decoded.first.label.lowercase()
                        }
                    }

                    // 4) обновление: описание релиза или сигнал «проверь версию»
                    NfcLink.KIND_UPDATE -> {
                        val share = com.gsynk.app.core.UpdateShare.parse(payload.body)
                        if (share == null) {
                            status = "Описание обновления не разобралось"
                        } else {
                            com.gsynk.app.core.UpdateShare.put(share)
                            received = payload
                            status = "Передано обновление " + share.version +
                                " — откройте панель обновлений или нажмите «Проверить»"
                        }
                    }

                    NfcLink.KIND_UPDATE_PING -> {
                        com.gsynk.app.core.UpdateShare.putPing(
                            com.gsynk.app.core.UpdateShare.pingFrom(payload.body)
                        )
                        received = payload
                        status = "Друг просит проверить обновление"
                    }

                    else -> {
                        received = payload
                        status = "Касание принято: " + NfcLink.describe(payload)
                    }
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        if (activity != null && nfc != null && giving == 1) {
            // приём и эмуляция карты друг другу мешают — в режиме чтения передачу гасим
            NdefHceService.setEnabled(context, false)
            runCatching { nfc.enableReaderMode(activity, callback, flags, android.os.Bundle()) }
        }
        onDispose {
            if (activity != null && nfc != null) runCatching { nfc.disableReaderMode(activity) }
            NdefHceService.setEnabled(context, false)
        }
    }

    // ————— выбор файла —————
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = FileLink.describe(context, uri)
        if (picked == null) {
            status = "Файл не открылся — попробуйте другой"
            return@rememberLauncherForActivityResult
        }
        val inline = if (picked.size in 1..FileLink.INLINE_LIMIT.toLong()) {
            FileLink.inlineBase64(context, picked.uri, picked.name, picked.size)
        } else {
            null
        }
        val body = if (inline != null) {
            NfcLink.fileInviteBody(
                NfcLink.FileInvite("", 0, "", picked.name, picked.size, inline)
            )
        } else {
            val token = FileLink.offer(context, picked.uri, picked)
            NfcLink.fileInviteBody(
                NfcLink.FileInvite(
                    host = com.gsynk.app.core.WifiCoop.localAddress(),
                    port = FileLink.PORT,
                    token = token,
                    name = picked.name,
                    size = picked.size
                )
            )
        }
        putToTouch(NfcLink.Payload(NfcLink.KIND_FILE, picked.name, body))
        status = if (inline != null) {
            "«" + picked.name + "» целиком лежит в метке — коснитесь вторым телефоном"
        } else {
            "«" + picked.name + "» поедет по Wi-Fi — коснитесь вторым телефоном"
        }
    }

    GrapeScreen(
        title = "Grape Link",
        subtitle = "касаний: $touches • NFC " + (if (adapter == null) "не найден" else "готов"),
        onBack = {
            FileLink.clear()
            NfcShow.clear()
            onBack()
        }
    ) {
        // ————— один переключатель роли —————
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SortChip("мой телефон отдаёт", giving == 0, Modifier.weight(1f)) { giving = 0 }
            SortChip("читаю касание", giving == 1, Modifier.weight(1f)) { giving = 1 }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (giving == 0) {
                "Выберите, что положить в касание, и приложите телефон к чужому — второй телефон прочитает."
            } else {
                "Держите этот экран открытым и приложите телефон к чужому или к метке."
            },
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(14.dp))
        // ————— четыре плитки: игра, файл, сценарий, метка —————
        BigTile(
            title = "GTBOI",
            subtitle = "касание — и играем вдвоём на двух телефонах",
            brush = TileBrush.Red,
            active = panel == "game"
        ) {
            panel = "game"
            if (giving == 0) onHostGame() else status = "Коснитесь телефона того, кто открыл комнату"
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTile("Файл", "передать целиком", TileBrush.Cyan, panel == "file") { panel = "file" }
            SmallTile("Сценарий", "салют, фонарик…", TileBrush.Violet, panel == "scene") { panel = "scene" }
            SmallTile("Метка", "прочитать и записать", TileBrush.Amber, panel == "tag") {
                panel = "tag"
                giving = 1
            }
        }

        Spacer(Modifier.height(14.dp))
        when (panel) {
            "game" -> GamePanel(giving == 0, onHost = onHostGame)
            "file" -> FilePanel(giving == 0, fileState) {
                filePicker.launch(arrayOf("*/*"))
            }
            "scene" -> ScenePanel(
                giving = giving,
                kind = sceneKind,
                text = sceneText,
                onKind = { sceneKind = it },
                onText = { sceneText = it },
                onSend = {
                    putToTouch(
                        NfcLink.Payload(
                            NfcLink.KIND_SHOW,
                            "Показ",
                            NfcShow.encode(sceneKind, if (sceneKind == NfcShow.Kind.TEXT) sceneText else "")
                        )
                    )
                    status = "Сценарий «" + sceneKind.label.lowercase() + "» ждёт касания"
                }
            )
            "tag" -> TagPanel(
                action = tagAction,
                info = tagInfo,
                onAction = { wantWrite ->
                    tagAction = if (wantWrite) 1 else 0
                    giving = 1
                    writing = if (wantWrite) {
                        nfcMessage(
                            if (sceneKind == NfcShow.Kind.TEXT) {
                                NfcLink.Payload(
                                    NfcLink.KIND_SHOW,
                                    "Показ",
                                    NfcShow.encode(NfcShow.Kind.TEXT, sceneText)
                                )
                            } else {
                                NfcLink.Payload(
                                    dataKind,
                                    dataTitle.ifBlank { NfcLink.option(dataKind).title },
                                    if (dataKind == NfcLink.KIND_PROFILE) "" else dataBody
                                )
                            }
                        )
                    } else {
                        null
                    }
                    status = if (wantWrite) {
                        "Теперь приложите телефон к метке — запишу «" + sceneKind.label.lowercase() + "» или данные"
                    } else {
                        "Приложите телефон к метке — прочитаю"
                    }
                }
            )
            else -> Unit
        }

        // ————— данные одним блоком —————
        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = if (panel == "data") GrapeVioletSoft.copy(alpha = 0.5f) else CardBorder) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { panel = if (panel == "data") "" else "data" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Данные по касанию", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (panel == "data") "свернуть" else "текст, ссылка, визитка, профиль",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (panel == "data") {
                Spacer(Modifier.height(10.dp))
                DataPanel(
                    kind = dataKind,
                    title = dataTitle,
                    body = dataBody,
                    onKind = { kind ->
                        dataKind = kind
                        when (kind) {
                            NfcLink.KIND_PASSWORD -> {
                                dataTitle = "Пароль"
                                dataBody = Passwords.generate()
                            }

                            NfcLink.KIND_PROFILE -> {
                                dataTitle = "Профиль GSYNK"
                                dataBody = ""
                            }

                            else -> Unit
                        }
                    },
                    onTitle = { dataTitle = it },
                    onBody = { dataBody = it },
                    onSend = {
                        if (dataKind == NfcLink.KIND_UPDATE) {
                            val share = provideUpdateShare()
                            putToTouch(
                                NfcLink.Payload(
                                    NfcLink.KIND_UPDATE,
                                    "Обновление GSYNK",
                                    com.gsynk.app.core.UpdateShare.body(share)
                                )
                            )
                            status = "Обновление " + share.version +
                                " ждёт касания — приложите телефон к другу"
                        } else if (dataKind == NfcLink.KIND_UPDATE_PING) {
                            putToTouch(
                                NfcLink.Payload(
                                    NfcLink.KIND_UPDATE_PING,
                                    "Сигнал обновления",
                                    com.gsynk.app.core.UpdateShare.ping(android.os.Build.MODEL)
                                )
                            )
                            status = "Сигнал «проверь обновление» готов — коснитесь телефона друга"
                        } else if (dataKind == NfcLink.KIND_PROFILE) {
                            scope.launch {
                                val rules = withContext(Dispatchers.IO) { profilePayload(context) }
                                putToTouch(
                                    NfcLink.Payload(
                                        NfcLink.KIND_PROFILE,
                                        dataTitle.ifBlank { "Профиль GSYNK" },
                                        rules
                                    )
                                )
                            }
                        } else {
                            putToTouch(
                                NfcLink.Payload(
                                    dataKind,
                                    dataTitle.ifBlank { NfcLink.option(dataKind).title },
                                    dataBody
                                )
                            )
                        }
                        status = "Данные ждут касания — приложите телефон к чужому"
                    }
                )
            }
        }

        val incoming = received
        if (incoming != null) {
            Spacer(Modifier.height(12.dp))
            ReceivedCard(
                payload = incoming,
                onImported = { status = it },
                onCheckUpdate = onCheckUpdate
            )
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .background(GrapeGreen)
                    ) { }
                    Spacer(Modifier.width(10.dp))
                    Text(status, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SmallButton("Показать анимацию") { burst.show(3_400L) }
            SmallButton("Забыть касания") {
                received = null
                FileLink.clear()
                NfcShow.clear()
                status = "Экран очищен"
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    DisposableEffect(Unit) {
        onDispose { burst.hide() }
    }
}

// ————————————————————————————————————————————————
//  Плитки и панели
// ————————————————————————————————————————————————

@Composable
private fun BigTile(
    title: String,
    subtitle: String,
    brush: androidx.compose.ui.graphics.Brush,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RectangleShape)
            .background(brush)
            .border(if (active) 2.dp else 0.dp, TextPrimary.copy(alpha = if (active) 0.55f else 0f), RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmallTile(
    title: String,
    subtitle: String,
    brush: androidx.compose.ui.graphics.Brush,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .weight(1f)
            .height(96.dp)
            .clip(RectangleShape)
            .background(brush)
            .border(if (active) 2.dp else 0.dp, TextPrimary.copy(alpha = if (active) 0.55f else 0f), RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(46.dp)
            .clip(RectangleShape)
            .background(CardBg)
            .border(1.dp, ChipBorder, RectangleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GamePanel(giving: Boolean, onHost: () -> Unit) {
    SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.5f)) {
        Text("GTBOI · подземелье", color = GrapeRed, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Один режим: игра с другом на двух телефонах. Один телефон открывает комнату, " +
                "второй касается его — и оба сразу проваливаются в подземелье на весь экран. " +
                "Каждый ведёт своего героя на своём телефоне.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(10.dp))
        if (giving) {
            PrimaryButton(text = "Открыть комнату", onClick = onHost, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                text = "Приложите телефон к тому, кто открыл комнату, — подключусь сам и запущу игру.",
                color = GrapeGreenSoft,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun FilePanel(
    giving: Boolean,
    state: FileLink.State,
    onPick: () -> Unit
) {
    SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.5f)) {
        Text("Передача файла", color = GrapeCyan, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Касание — только знакомство: второй телефон получает адрес, и файл едет по Wi-Fi. " +
                "Крошечные файлы (до 600 байт) уезжают прямо в метке, без сети. Принятое падает " +
                "в «Загрузки/GSYNK».",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(10.dp))
        if (giving) {
            PrimaryButton(
                text = "Выбрать файл",
                onClick = onPick,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Приложите телефон к тому, кто отправляет файл.",
                color = GrapeGreenSoft,
                style = MaterialTheme.typography.labelSmall
            )
        }
        when (val current = state) {
            is FileLink.State.Offering -> {
                Spacer(Modifier.height(10.dp))
                InfoRow("Готов отдать", current.name, GrapeCyan)
                InfoRow("Размер", readableSize(current.size))
            }

            is FileLink.State.Receiving -> {
                Spacer(Modifier.height(10.dp))
                InfoRow("Принимаю", current.name + " · " + current.percent + " проц.", GrapeAmber)
                Spacer(Modifier.height(8.dp))
                BarIndicator(percent = current.percent, brush = TileBrush.Cyan)
            }

            is FileLink.State.Ready -> {
                Spacer(Modifier.height(10.dp))
                InfoRow("Готово", current.name, GrapeGreen)
                InfoRow("Куда", current.path)
            }

            is FileLink.State.Failed -> {
                Spacer(Modifier.height(10.dp))
                InfoRow("Не вышло", current.reason, GrapeRed)
            }

            FileLink.State.Idle -> Unit
        }
    }
}

@Composable
private fun ScenePanel(
    giving: Int,
    kind: NfcShow.Kind,
    text: String,
    onKind: (NfcShow.Kind) -> Unit,
    onText: (String) -> Unit,
    onSend: () -> Unit
) {
    SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.5f)) {
        Text("Сценарий по касанию", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Второй телефон сразу покажет салют, сердечки, вспышку или надпись во весь экран — " +
                "либо выполнит команду: фонарик, вибрация, сирена.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            NfcShow.Kind.entries.forEach { option ->
                SortChip(label = option.label, selected = kind == option) { onKind(option) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(NfcShow.Kind.entries.first { it == kind }.hint, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall)
        if (kind == NfcShow.Kind.TEXT) {
            Spacer(Modifier.height(10.dp))
            GrapeTextField(value = text, onValueChange = onText, label = "Что написать во весь экран")
        }
        Spacer(Modifier.height(12.dp))
        if (giving == 0) {
            PrimaryButton(text = "Положить в касание", onClick = onSend, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                text = "Приложите телефон к тому, кто отдаёт, — показ сработает здесь же.",
                color = GrapeGreenSoft,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun TagPanel(
    action: Int,
    info: String,
    onAction: (Boolean) -> Unit
) {
    SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.5f)) {
        Text("Обычная NFC-метка", color = GrapeAmber, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Новое: GSYNK умеет писать на пустые наклейки и брелоки. Запишите на метку текст, " +
                "ссылку, приглашение в GTBOI или сценарий — её прочитает любой телефон, а GSYNK " +
                "выполнит сценарий сразу.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            SortChip("Прочитать метку", selected = action == 0, modifier = Modifier.weight(1f)) { onAction(false) }
            SortChip("Записать метку", selected = action == 1, modifier = Modifier.weight(1f)) { onAction(true) }
        }
        if (action == 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "На метку уйдёт то, что выбрано в «Сценарии» (если это надпись — её текст), " +
                    "иначе данные из блока «Данные по касанию».",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (info.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            InfoRow("Метка", info, GrapeAmber)
        }
    }
}

@Composable
private fun DataPanel(
    kind: String,
    title: String,
    body: String,
    onKind: (String) -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onSend: () -> Unit
) {
    ChipFlow {
        listOf(
            NfcLink.KIND_TEXT,
            NfcLink.KIND_URL,
            NfcLink.KIND_CONTACT,
            NfcLink.KIND_PASSWORD,
            NfcLink.KIND_GEO,
            NfcLink.KIND_PROFILE
        ).forEach { option ->
            SortChip(label = NfcLink.option(option).title, selected = kind == option) { onKind(option) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(NfcLink.option(kind).hint, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall)
    if (kind != NfcLink.KIND_PROFILE) {
        Spacer(Modifier.height(10.dp))
        GrapeTextField(value = title, onValueChange = onTitle, label = "Заголовок (необязательно)")
        Spacer(Modifier.height(8.dp))
        GrapeTextField(value = body, onValueChange = onBody, label = "Содержимое", singleLine = false)
    } else {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Правила расписания уедут целиком — второй телефон импортирует их одним касанием.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(text = "Положить в касание", onClick = onSend, modifier = Modifier.fillMaxWidth())
}

private fun readableSize(bytes: Long): String = when {
    bytes <= 0 -> "неизвестно"
    bytes < 1024 -> bytes.toString() + " Б"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " КБ"
    else -> (bytes / (1024 * 1024)).toString() + "," + ((bytes / 1024) % 1024 / 100) + " МБ"
}

private suspend fun profilePayload(context: android.content.Context): String = runCatching {
    PayloadCodec.encodeRules(RuleRepository(context).currentRules())
}.getOrDefault("")

/** Карточка полученных данных: одно действие по типу — без лишних кнопок. */
@Composable
private fun ReceivedCard(
    payload: NfcLink.Payload,
    onImported: (String) -> Unit,
    onCheckUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
        Text(payload.title.ifBlank { "Без названия" }, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text(NfcLink.describe(payload), color = GrapeGreenSoft, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RectangleShape)
                .background(Color(0xFF0B0714))
                .border(1.dp, ChipBorder, RectangleShape)
                .padding(12.dp)
        ) {
            Text(payload.body.ifBlank { "—" }, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))

        when (payload.kind) {
            NfcLink.KIND_UPDATE -> {
                val share = com.gsynk.app.core.UpdateShare.parse(payload.body)
                if (share != null) {
                    Text(
                        text = "Версия ${share.version}" +
                            if (share.sha256.isNotBlank()) " • сумма ${share.shortSha}" else "",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(share.url, color = TextDim, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RectangleShape)
                            .background(CardBg)
                            .border(1.dp, ChipBorder, RectangleShape)
                            .clickable { onCheckUpdate() },
                        contentAlignment = Alignment.Center
                    ) { Text("Проверить обновление", color = GrapeGreen, style = MaterialTheme.typography.labelLarge) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RectangleShape)
                            .background(CardBg)
                            .border(1.dp, ChipBorder, RectangleShape)
                            .clickable {
                                val share = com.gsynk.app.core.UpdateShare.parse(payload.body)
                                if (share != null) {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(share.url)
                                            )
                                        )
                                    }
                                    onImported("Открыл ссылку на APK")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Скачать APK", color = GrapeCyan, style = MaterialTheme.typography.labelLarge) }
                }
            }

            NfcLink.KIND_UPDATE_PING -> PrimaryButton(
                text = "Проверить обновление сейчас",
                onClick = { onCheckUpdate() },
                modifier = Modifier.fillMaxWidth()
            )

            NfcLink.KIND_URL -> PrimaryButton(
                text = "Открыть ссылку",
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(payload.body))
                        )
                    }
                    onImported("Ссылка открыта")
                },
                modifier = Modifier.fillMaxWidth()
            )

            NfcLink.KIND_CONTACT -> PrimaryButton(
                text = "Добавить контакт",
                onClick = {
                    val parts = payload.body.split("|")
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(android.provider.ContactsContract.Intents.Insert.NAME, parts.getOrNull(0))
                        putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, parts.getOrNull(1))
                    }
                    runCatching { context.startActivity(intent) }
                    onImported("Открыл карточку контакта")
                },
                modifier = Modifier.fillMaxWidth()
            )

            NfcLink.KIND_GEO -> PrimaryButton(
                text = "Показать на карте",
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("geo:" + payload.body + "?q=" + payload.body)
                            )
                        )
                    }
                    onImported("Координаты открыты")
                },
                modifier = Modifier.fillMaxWidth()
            )

            NfcLink.KIND_PASSWORD -> PrimaryButton(
                text = "Скопировать пароль",
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GSYNK", payload.body))
                    onImported("Пароль в буфере обмена")
                },
                modifier = Modifier.fillMaxWidth()
            )

            NfcLink.KIND_PROFILE -> PrimaryButton(
                text = "Импортировать правила",
                onClick = {
                    scope.launch {
                        val count = withContext(Dispatchers.IO) {
                            runCatching {
                                val repository = RuleRepository(context)
                                val incoming = PayloadCodec.decodeRules(payload.body)
                                if (incoming.isEmpty()) return@runCatching 0
                                val existing = repository.currentRules()
                                val merged = existing + incoming.filterNot { fresh ->
                                    existing.any { it.id == fresh.id }
                                }
                                repository.saveRules(merged)
                                incoming.size
                            }.getOrDefault(0)
                        }
                        onImported(
                            if (count > 0) "Импортировано правил: $count"
                            else "Правила не распознаны"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            else -> Unit
        }
    }
}
