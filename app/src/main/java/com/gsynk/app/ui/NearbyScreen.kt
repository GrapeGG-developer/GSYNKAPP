package com.gsynk.app.ui

import android.Manifest
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.BuildConfig
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.Nearby
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.NfcShow
import com.gsynk.app.core.PayloadCodec
import com.gsynk.app.core.UpdateShare
import com.gsynk.app.core.WifiCoop
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush

/**
 * «GSYNK рядом» — связаться с другим человеком, у которого стоит GSYNK.
 *
 * Три дороги связи, и все три настоящие:
 *  * **Bluetooth** — без роутера и без интернета: показ, метки, обновление, сценарий, сообщение;
 *  * **Wi-Fi Direct** — телефоны сами создают сеть: по ней идут игра и файлы;
 *  * **NFC** — одно касание как «рукопожатие» (кнопка внизу ведёт в Grape Link).
 *
 * Тут же главная просьба: **передать обновление**. APK в касание не влезет, поэтому
 * уезжает описание релиза — версия и прямая ссылка, а второй телефон скачивает сам.
 */
@Composable
fun NearbyScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenNfc: () -> Unit = {},
    onHostGame: () -> Unit = {},
    onJoinGame: (NfcLink.GameInvite) -> Unit = {}
) {
    val context = LocalContext.current
    val status by Nearby.status.collectAsStateWithLifecycle()
    val peers by Nearby.peers.collectAsStateWithLifecycle()
    val log by Nearby.log.collectAsStateWithLifecycle()
    val partner by Nearby.partner.collectAsStateWithLifecycle()
    val hostAddress by Nearby.hostAddress.collectAsStateWithLifecycle()
    val incoming by Nearby.incomingProfile.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    var channel by remember { mutableStateOf(Nearby.Channel.BLUETOOTH) }
    var showKind by remember { mutableStateOf(NfcShow.Kind.FIREWORKS) }
    var showText by remember { mutableStateOf("") }
    var sayText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var waiting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val all = granted.values.all { it }
        note = if (all) {
            Nearby.startDiscovery(context, channel)
            "Ищу устройства…"
        } else {
            "Без разрешений «Рядом с устройствами» поиск невозможен"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Nearby.stopDiscovery(context)
        }
    }

    fun guardedStart() {
        val missing = Nearby.permissionsFor(channel).filter { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            Nearby.startDiscovery(context, channel)
            note = "Ищу устройства…"
        } else {
            val askable = missing.filter { it != Manifest.permission.ACCESS_FINE_LOCATION || android.os.Build.VERSION.SDK_INT < 33 }
            permissionLauncher.launch((askable.ifEmpty { missing }).toTypedArray())
        }
    }

    GrapeScreen(
        title = "GSYNK рядом",
        subtitle = "Bluetooth, Wi-Fi Direct и NFC — связаться с другим GSYNK",
        onBack = onBack,
        actions = {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .clickable { onOpenNfc() },
                contentAlignment = Alignment.Center
            ) { Text("NFC", color = GrapeCyan, style = MaterialTheme.typography.labelSmall) }
        }
    ) {
        // ————— состояние связи —————
        SurfaceCard(
            borderColor = when (status) {
                is Nearby.Status.Ready -> GrapeGreen.copy(alpha = 0.5f)
                is Nearby.Status.Failed -> GrapePink.copy(alpha = 0.5f)
                else -> CardBorder
            }
        ) {
            Text(
                text = status.label,
                color = when (status) {
                    is Nearby.Status.Ready -> GrapeGreen
                    is Nearby.Status.Failed -> GrapePink
                    else -> TextPrimary
                },
                style = MaterialTheme.typography.titleSmall
            )
            if (partner.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("На связи: $partner", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            if (hostAddress.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Хозяин сети: $hostAddress — можно заходить в GTBOI",
                    color = GrapeVioletSoft,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Nearby.Channel.entries.forEach { option ->
                    SortChip(label = option.title, selected = channel == option) {
                        channel = option
                        note = option.hint
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable { guardedStart() },
                    contentAlignment = Alignment.Center
                ) { Text("Найти рядом", color = GrapeCyan, style = MaterialTheme.typography.labelLarge) }

                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            if (channel == Nearby.Channel.BLUETOOTH) {
                                Nearby.host(context, "gsynk-" + android.os.Build.MODEL.take(12))
                                waiting = true
                                note = "Жду подключения: пусть друг нажмёт «Найти рядом» и выберет этот телефон"
                            } else {
                                note = if (Nearby.hostFast()) {
                                    waiting = true
                                    "Открыл быстрый канал: друг подключается по Wi-Fi Direct"
                                } else {
                                    "Не удалось открыть канал — попробуйте ещё раз"
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text(if (waiting) "Жду гостя…" else "Ждать гостя", color = GrapeAmber, style = MaterialTheme.typography.labelLarge) }
            }
            if (Nearby.isConnected()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, GrapePink.copy(alpha = 0.5f), RectangleShape)
                        .clickable {
                            Nearby.disconnect(context)
                            waiting = false
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Отключить", color = GrapePink, style = MaterialTheme.typography.labelLarge) }
            }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(note, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall)
            }
        }

        // ————— найденные устройства —————
        Spacer(Modifier.height(14.dp))
        SectionTitle("Устройства рядом")
        Spacer(Modifier.height(8.dp))
        if (peers.isEmpty()) {
            Text(
                text = "Пока никого. Включите Bluetooth или Wi-Fi и нажмите «Найти рядом» — " +
                    "второй телефон при этом может просто ждать на этом же экране.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            peers.forEach { peer ->
                SurfaceCard(borderColor = CardBorder, onClick = { Nearby.connect(context, peer) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(peer.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = peer.channel.title + " • " + peer.address +
                                    if (peer.isPaired) " • уже сопряжён" else "",
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text("связать", color = GrapeCyan, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ————— что можно сделать по связи —————
        Spacer(Modifier.height(10.dp))
        SectionTitle("По связи")
        Spacer(Modifier.height(8.dp))

        SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
            Text("Передать обновление GSYNK", color = GrapeGreen, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Друг получает версию и прямую ссылку на APK. Сам файл он скачает сам — " +
                    "по этой же связи или из интернета.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            val share = UpdateShare.Share(
                version = update?.latestTag?.takeIf { it.isNotBlank() } ?: ("v" + BuildConfig.VERSION_NAME),
                url = update?.apkUrl?.takeIf { it.isNotBlank() }
                    ?: UpdateShare.apkUrl(
                        settings.updateRepo,
                        "v" + BuildConfig.VERSION_NAME
                    ),
                from = android.os.Build.MODEL
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            note = if (Nearby.sendUpdate(share)) {
                                "Обновление ${share.version} передано"
                            } else {
                                "Нет связи — сначала свяжитесь с телефоном"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Передать обновление", color = GrapeGreen, style = MaterialTheme.typography.labelLarge) }

                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            note = if (Nearby.sendUpdatePing(android.os.Build.MODEL)) {
                                "Сигнал «проверь обновление» отправлен"
                            } else {
                                "Нет связи"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Просить проверить", color = GrapeAmber, style = MaterialTheme.typography.labelLarge) }
            }
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.4f)) {
            Text("Показ сцен", color = GrapeCyan, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NfcShow.Kind.entries.take(4).forEach { option ->
                    SortChip(label = option.label, selected = showKind == option) { showKind = option }
                }
            }
            if (showKind == NfcShow.Kind.TEXT) {
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = showText,
                    onValueChange = { showText = it },
                    label = { Text("Надпись на экране друга") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = "Показать на телефоне друга",
                onClick = {
                    note = if (Nearby.sendShow(showKind, if (showKind == NfcShow.Kind.TEXT) showText else "")) {
                        "Показ отправлен: " + showKind.label.lowercase()
                    } else {
                        "Нет связи"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Команды (фонарик, вибрация, сирена) можно отправить с экрана Grape Link — " +
                    "там тот же список сцен.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.4f)) {
            Text("Сценарии и сообщения", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Правил у вас: " + rules.size + ". Отправка передаёт весь список расписания.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = "Отправить мои правила",
                onClick = {
                    note = if (Nearby.sendProfile("правила", PayloadCodec.encodeRules(rules))) {
                        "Правила ушли другу"
                    } else {
                        "Нет связи"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (incoming != null) {
                Spacer(Modifier.height(8.dp))
                SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.5f)) {
                    Text("Пришёл сценарий", color = GrapeAmber, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Можно применить: правила заменятся присланными.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Применить сценарий",
                        onClick = {
                            val decoded = PayloadCodec.decodeRules(incoming.orEmpty())
                            if (decoded.isEmpty()) {
                                note = "Сценарий не распознан"
                            } else {
                                decoded.forEach { vm.upsert(it, announce = false) }
                                note = "Применено правил: " + decoded.size
                                Nearby.reset()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = sayText,
                onValueChange = { sayText = it },
                label = { Text("Сообщение другу") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "Отправить сообщение",
                onClick = {
                    if (sayText.isNotBlank()) {
                        note = if (Nearby.sendSay(sayText)) "Отправлено" else "Нет связи"
                        if (note == "Отправлено") sayText = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = GrapePink.copy(alpha = 0.4f)) {
            Text("Играть вдвоём по этой связи", color = GrapePink, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Быстрый канал Wi-Fi Direct держит игру: телефоны создают свою сеть. " +
                    "Хозяин нажимает «Я хозяин», гость — «Играть у друга».",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            Nearby.sendHost(WifiCoop.localAddress())
                            Nearby.disconnect(context)
                            onHostGame()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Я хозяин", color = GrapeGreen, style = MaterialTheme.typography.labelLarge) }

                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            val host = hostAddress.ifBlank { Nearby.activeChannel().let { "" } }
                            if (host.isBlank()) {
                                note = "Адрес хозяина ещё не пришёл — пусть он нажмёт «Я хозяин»"
                            } else {
                                Nearby.disconnect(context)
                                onJoinGame(
                                    NfcLink.GameInvite(
                                        game = "gtboi",
                                        from = partner.ifBlank { "второй телефон" },
                                        seed = System.currentTimeMillis(),
                                        host = host,
                                        port = WifiCoop.PORT,
                                        code = "NEARBY"
                                    )
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Играть у друга", color = GrapeCyan, style = MaterialTheme.typography.labelLarge) }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Журнал связи")
        Spacer(Modifier.height(8.dp))
        SurfaceCard(borderColor = CardBorder) {
            if (log.isEmpty()) {
                Text("Пока пусто.", color = TextDim, style = MaterialTheme.typography.labelSmall)
            } else {
                log.takeLast(14).forEach { line ->
                    Text("• " + line, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Что нужно от телефона", color = TextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Bluetooth: включить в шторке и разрешить «Рядом с устройствами». " +
                    "Wi-Fi Direct: включить Wi-Fi, разрешения выдаются при первом поиске. " +
                    "NFC: приложить телефоны — это самый быстрый способ, и он работает без интернета. " +
                    "Все действия видны в журнале приложения (раздел «Диагностика»).",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
