package com.gsynk.app.ui

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.core.net.toUri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.GameScores
import com.gsynk.app.core.NdefHceService
import com.gsynk.app.core.NfcBurstOverlay
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.NfcReader
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Grape Link — обмен данными по NFC.
 *
 * Ключевое отличие от версии 2.0: приём идёт через [NfcReader] — сначала системный
 * NDEF, затем чтение файла NDEF через APDU (Type 4). Именно это лечит прежнюю
 * ошибку «метка подключена, а данных нет». Каждое удачное касание сопровождается
 * тяжёлой анимацией вспышки у фронтальной камеры.
 */
@Composable
fun NfcLinkScreen(
    onBack: () -> Unit,
    onPlayGame: (seed: Long?) -> Unit = {},
    onPlayOnline: (invite: NfcLink.GameInvite?) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val adapter = remember(context) { runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() }
    val burst = remember(context) { NfcBurstOverlay(context) }

    var mode by remember { mutableIntStateOf(0) } // 0 играть, 1 передать, 2 принять
    var kind by remember { mutableStateOf(NfcLink.KIND_TEXT) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf(PendingNfcPayload.take()) }
    var invite by remember { mutableStateOf<NfcLink.GameInvite?>(null) }
    var touches by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        touches = withContext(Dispatchers.IO) {
            RuleRepository(context).currentSettings().nfcTouches
        }
    }

    fun registerTouch() {
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

    // ————— приём меток —————
    DisposableEffect(activity, mode) {
        val nfc = adapter
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val result = runCatching { NfcReader.read(tag) }.getOrElse { NfcReader.Result.Failed("ошибка чтения") }
            activity?.runOnUiThread {
                when (result) {
                    is NfcReader.Result.Ok -> {
                        val payload = NfcLink.parse(result.message)
                        if (payload == null) {
                            status = "Метка прочитана, но формат GSYNK не распознан"
                        } else {
                            received = payload
                            burst.show(3_400L)
                            AutomationActions.vibrate(context, 120L)
                            registerTouch()
                            invite = if (payload.kind == NfcLink.KIND_GAME) {
                                NfcLink.parseGameInvite(payload.body)
                            } else {
                                null
                            }
                            if (invite != null) {
                                status = "Приглашение в подземелье получено — жмите «Играть»"
                                onPlayGame(invite?.seed)
                            }
                            status = "Получено: " + payload.title
                        }
                    }

                    is NfcReader.Result.Failed -> {
                        status = "Метка подключена, но данных нет: " + result.reason
                    }
                }
            }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        if (activity != null && nfc != null && mode == 2) {
            // приём и эмуляция карты мешают друг другу: в режиме приёма передачу гасим
            NdefHceService.setEnabled(context, false)
            runCatching { nfc.enableReaderMode(activity, callback, flags, android.os.Bundle()) }
        }
        onDispose {
            if (activity != null && nfc != null) runCatching { nfc.disableReaderMode(activity) }
        }
    }

    fun prepareTransfer(payload: NfcLink.Payload) {
        sending = true
        val message = NfcLink.buildMessage(payload, context.packageName)
        NdefHceService.store(context, message)
        NdefHceService.setEnabled(context, true)
        status = "Готово. Приложите второй телефон к задней панели — и держите до вспышки."
        burst.show(2_600L)
    }

    GrapeScreen(
        title = "Grape Link",
        subtitle = "касаний: $touches • NFC ${if (adapter == null) "не найден" else "готов"}",
        onBack = onBack
    ) {
        // ————— состояние NFC —————
        SurfaceCard(
            borderColor = when {
                adapter == null -> GrapeRed.copy(alpha = 0.5f)
                adapter.isEnabled -> GrapeGreen.copy(alpha = 0.45f)
                else -> GrapeViolet.copy(alpha = 0.5f)
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            adapter == null -> "NFC не поддерживается"
                            adapter.isEnabled -> "NFC включён"
                            else -> "NFC выключен"
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Анимация подключения появляется у фронтальной камеры",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (adapter != null && !adapter.isEnabled) {
                    SortChip(label = "Включить", selected = false) {
                        runCatching { context.startActivity(NfcLink.openNfcSettingsIntent()) }
                    }
                }
            }
        }

        // ————— режимы —————
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SortChip("Играть", mode == 0) { mode = 0 }
            SortChip("Передать", mode == 1) { mode = 1 }
            SortChip("Принять", mode == 2) { mode = 2 }
        }

        when (mode) {
            // ————— главное: игра —————
            0 -> {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RectangleShape)
                        .background(TileBrush.Violet)
                        .clickable { onPlayGame(null) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ПОДЗЕМЕЛЬЕ GSYNK", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "настоящая игра: комнаты, враги, босс",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PrimaryButton(
                        text = "Играть",
                        onClick = { onPlayGame(null) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Сопряжение Wi-Fi",
                        onClick = { onPlayOnline(null) }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(TileBrush.Green)
                        .clickable { onPlayOnline(null) }
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            text = "ДВА ТЕЛЕФОНА ПО WI-FI",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "NFC — знакомство: хозяин отдаёт адрес комнаты касанием. " +
                                "Игра идёт по локальной сети, каждый ведёт своего героя со своего телефона.",
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SurfaceCard(borderColor = CardBorder) {
                    Text("Как это работает", color = TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Один экран", "вдвоём за одним телефоном: левый и правый стик")
                    InfoRow("Два телефона", "касание NFC — знакомство, игра идёт по Wi-Fi, каждый со своего экрана")
                    InfoRow("Сравнение", "у кого больше убийств и счёт, тот и победил")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Поле игры рисуется в полосе фронтальной камеры, управление — под большими " +
                            "пальцами. Ничего не скачивается: движок считается на телефоне.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (invite != null) {
                    Spacer(Modifier.height(12.dp))
                    SurfaceCard(borderColor = GrapeViolet.copy(alpha = 0.6f)) {
                        Text("Вызов по NFC", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (invite?.isWifi == true)
                                "Хозяин «${invite?.from}» ждёт на ${invite?.host}:${invite?.port}. " +
                                    "Ключ подземелья ${invite?.seed}."
                            else
                                "Ключ ${invite?.seed} от «${invite?.from}» — подземелье уже готово.",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        if (invite?.isWifi == true) {
                            PrimaryButton(
                                text = "Подключиться и играть",
                                onClick = { onPlayOnline(invite) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        PrimaryButton(
                            text = "Играть на этом ключе",
                            onClick = { onPlayGame(invite?.seed) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Нужен обмен данными? Переключитесь на «Передать» или «Принять».",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            1 -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Обмен по касанию",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ниже — что и как передаётся. Игра живёт в соседней вкладке «Играть».",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(14.dp))
                SectionTitle("Что передаём")
                Spacer(Modifier.height(9.dp))
                ChipFlow {
                    NfcLink.TRANSFER_KINDS.forEach { option ->
                                SortChip(
                                    label = option.title,
                                    selected = kind == option.kind
                                ) {
                                    kind = option.kind
                                    when (option.kind) {
                                        NfcLink.KIND_PASSWORD -> {
                                            title = "Пароль"
                                            body = com.gsynk.app.core.Passwords.generate()
                                        }

                                        NfcLink.KIND_PROFILE -> {
                                            title = "Профиль GSYNK"
                                            body = ""
                                        }

                                        NfcLink.KIND_GAME -> {
                                            title = "Дуэль"
                                            body = ""
                                        }

                                        else -> {
                                            title = ""
                                            body = ""
                                        }
                                    }
                                }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SurfaceCard {
                    Text(
                        text = NfcLink.option(kind).hint,
                        color = GrapeVioletSoft,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(10.dp))
                    if (kind != NfcLink.KIND_PROFILE && kind != NfcLink.KIND_GAME) {
                        GrapeTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = "Заголовок (необязательно)"
                        )
                        Spacer(Modifier.height(10.dp))
                        GrapeTextField(
                            value = body,
                            onValueChange = { body = it },
                            label = "Содержимое",
                            singleLine = false
                        )
                        Spacer(Modifier.height(12.dp))
                    } else if (kind == NfcLink.KIND_PROFILE) {
                        Text(
                            text = "Правила расписания уедут целиком — второй телефон импортирует их одним касанием.",
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Text(
                            text = "Вызов: второй телефон получит приглашение и сразу начнёт дуэль на реакцию.",
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    PrimaryButton(
                        text = if (sending) "Обновить передачу" else "Включить передачу",
                        onClick = {
                            if (kind == NfcLink.KIND_PROFILE) {
                                scope.launch {
                                    val rulesPayload = withContext(Dispatchers.IO) { profilePayload(context) }
                                    prepareTransfer(
                                        NfcLink.Payload(
                                            kind,
                                            title.ifBlank { NfcLink.option(kind).title },
                                            rulesPayload
                                        )
                                    )
                                }
                            } else {
                                val finalBody = if (kind == NfcLink.KIND_GAME) "" else body
                                prepareTransfer(
                                    NfcLink.Payload(kind, title.ifBlank { NfcLink.option(kind).title }, finalBody)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (sending) {
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(
                            text = "Выключить передачу",
                            onClick = {
                                sending = false
                                NdefHceService.setEnabled(context, false)
                                NdefHceService.clear(context)
                                status = "Передача выключена"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                SurfaceCard {
                    Text("Что уходит по NFC", color = TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    NfcLink.TRANSFER_KINDS.forEach { option ->
                        InfoRow(option.title, option.hint)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Ограничение протокола: до ~800 байт на касание. Фотографии, музыка " +
                            "и большие файлы по NFC не передаются — для них нужен Wi-Fi Direct.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            2 -> {
                Spacer(Modifier.height(14.dp))
                SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.45f)) {
                    Text("Режим приёма включён", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Приложите телефон к метке или ко второму телефону с включённой передачей. " +
                            "Держите, пока не появится вспышка у камеры.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (received != null) {
                    Spacer(Modifier.height(12.dp))
                    ReceivedCard(
                        payload = received!!,
                        onImported = { status = it }
                    )
                }
            }

        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RectangleShape)
                            .background(GrapeGreen)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(status, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RectangleShape)
                .background(TileBrush.Cyan)
                .clickable { burst.show(3_400L) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Показать анимацию подключения",
                color = Color(0xFF06283A),
                style = MaterialTheme.typography.titleSmall
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { burst.hide() }
    }
}

/** Карточка полученных данных: у каждого типа своё действие. */
@Composable
private fun ReceivedCard(payload: NfcLink.Payload, onImported: (String) -> Unit) {
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
            NfcLink.KIND_URL -> PrimaryButton(
                text = "Открыть ссылку",
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, payload.body.toUri())
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
                                ("geo:" + payload.body + "?q=" + payload.body).toUri()
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
                                val incoming = com.gsynk.app.core.PayloadCodec.decodeRules(payload.body)
                                if (incoming.isEmpty()) return@runCatching 0
                                val existing = repository.currentRules()
                                val merged = (existing + incoming.filterNot { new ->
                                    existing.any { it.id == new.id }
                                })
                                repository.saveRules(merged)
                                incoming.size
                            }.getOrDefault(0)
                        }
                        onImported(if (count > 0) "Добавлено правил: $count" else "Не удалось разобрать профиль")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            else -> PrimaryButton(
                text = "Скопировать текст",
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GSYNK", payload.body))
                    onImported("Скопировано")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Сериализуем правила в компактную строку для передачи по NFC (вызывать из корутины). */
private suspend fun profilePayload(context: android.content.Context): String = runCatching {
    com.gsynk.app.core.PayloadCodec.encodeRules(
        RuleRepository(context).currentRules()
    )
}.getOrDefault("")
