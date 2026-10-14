package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.R
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.IslandWidget
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush

/**
 * Мультитул 2.1: живые плитки вместо длинных списков.
 *
 * Здесь собрано всё, что делают «на ходу»: фонарик, режим тишины, сирена,
 * быстрые системные экраны, «хакерский набор», локальный ИИ и профиль устройства.
 * Модель определяется автоматически — переключателя набора функций нет.
 */
@Composable
fun ToolsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenNfc: () -> Unit,
    onHostGame: () -> Unit = {},
    onJoinGame: (com.gsynk.app.core.NfcLink.GameInvite) -> Unit = {},
    openTool: String? = null,
    openFlag: String? = null,
    openLab: String? = null,
    onOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val tools: ToolsViewModel = viewModel()
    val torch by tools.torch.collectAsStateWithLifecycle()
    val dnd by tools.dnd.collectAsStateWithLifecycle()
    val siren by tools.siren.collectAsStateWithLifecycle()
    val stats by tools.stats.collectAsStateWithLifecycle()
    val toolsMessage by tools.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val updateAvailable by vm.updateAvailable.collectAsStateWithLifecycle()
    val turboActive by vm.turboActive.collectAsStateWithLifecycle()
    val turboSpeed by com.gsynk.app.core.TurboState.cpuGflops.collectAsStateWithLifecycle()

    val profile = remember { DeviceProfile.detect(context) }
    var openExtra by remember { mutableStateOf<String?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showScreensaver by remember { mutableStateOf(false) }
    var showTurbo by remember { mutableStateOf(false) }
    var showChronicle by remember { mutableStateOf(false) }
    var showBench by remember { mutableStateOf(false) }
    var labStart by remember { mutableStateOf<String?>(null) }

    // Заявка от роутера: любому экрану приложения разрешено открыть любой инструмент.
    LaunchedEffect(openTool, openFlag, openLab) {
        if (openTool == null && openFlag == null && openLab == null) return@LaunchedEffect
        when {
            openLab != null -> {
                labStart = openLab
                openExtra = "lab"
            }

            openTool != null -> openExtra = openTool
            openFlag != null -> when (openFlag) {
                "turbo" -> showTurbo = true
                "chronicle" -> showChronicle = true
                "chat" -> showChat = true
                "screensaver" -> showScreensaver = true
                "diagnostics" -> showDiagnostics = true
                "update" -> showUpdate = true
                "bench" -> showBench = true
            }
        }
        onOpened()
    }

    DisposableEffect(Unit) {
        tools.startStats()
        onDispose { tools.stopStats() }
    }

    LaunchedEffect(Unit) {
        if (toolsMessage != null) {
            vm.showMessage(toolsMessage!!)
            tools.clearMessage()
        }
    }

    when (openExtra) {
        "password" -> PasswordToolScreen(onBack = { openExtra = null })
        "morse" -> MorseToolScreen(onBack = { openExtra = null })
        "metal" -> MetalDetectorScreen(onBack = { openExtra = null })
        "level" -> LevelToolScreen(onBack = { openExtra = null })
        "sound" -> SoundMeterScreen(onBack = { openExtra = null })
        "sysinfo" -> SysDashboardScreen(onBack = { openExtra = null })
        "coder" -> EncoderScreen(onBack = { openExtra = null })
        "subnet" -> SubnetScreen(onBack = { openExtra = null })
        "lab" -> LabScreen(onBack = { openExtra = null; labStart = null }, initial = labStart)
        "nearby" -> NearbyScreen(
            vm = vm,
            onBack = { openExtra = null },
            onOpenNfc = onOpenNfc,
            onHostGame = onHostGame,
            onJoinGame = onJoinGame
        )
    }

    if (openExtra != null) return

    if (showDiagnostics) {
        DiagnosticsScreen(vm = vm, onBack = { showDiagnostics = false })
        return
    }

    // Панели обязаны заканчиваться return: без него список инструментов рисовался
    // ПОВЕРХ панели и перехватывал нажатия — «Нагрузка» и «Хроника» не открывались.
    if (showTurbo) {
        TurboScreen(vm = vm, onBack = { showTurbo = false })
        return
    }

    if (showChronicle) {
        ChronicleScreen(onBack = { showChronicle = false })
        return
    }

    if (showBench) {
        BenchScreen(vm = vm, onBack = { showBench = false })
        return
    }

    if (showChat) {
        ChatScreen(onBack = { showChat = false })
        return
    }

    if (showScreensaver) {
        ScreensaverScreen(onBack = { showScreensaver = false })
        return
    }

    if (showUpdate) {
        UpdateSheet(vm = vm, onDismiss = { showUpdate = false })
        return
    }

    GrapeScreen(
        title = "Инструменты",
        subtitle = "всё в одном приложении",
        onBack = onBack
    ) {
        // ————— быстрые действия —————
        SectionTitle("На ходу")
        Spacer(Modifier.height(9.dp))
        TileRow {
            ToggleTile(
                title = "Фонарик",
                on = torch,
                iconRes = R.drawable.ic_brightness,
                onText = "светит",
                offText = "выключен",
                modifier = Modifier.weight(1f)
            ) { tools.toggleTorch() }
            ToggleTile(
                title = "Тишина",
                on = dnd,
                iconRes = R.drawable.ic_shield,
                onText = "не беспокоить",
                offText = "звук включён",
                modifier = Modifier.weight(1f)
            ) { tools.toggleDnd() }
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            ToggleTile(
                title = "Сирена",
                on = siren,
                iconRes = R.drawable.ic_volume,
                onText = "громко зовёт",
                offText = "найти телефон",
                modifier = Modifier.weight(1f)
            ) { tools.toggleSiren() }
            LiveTile(
                title = "Вибрация",
                subtitle = "проверка мотора",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Pink,
                liveText = "разок",
                modifier = Modifier.weight(1f)
            ) { tools.vibrate() }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Разделы")
        Spacer(Modifier.height(9.dp))
        TileRow {
            NavTile(
                title = "Grape Link",
                subtitle = "NFC-обмен",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Cyan,
                liveText = "касаний: ${settings.nfcTouches}",
                modifier = Modifier.weight(1f),
                onClick = onOpenNfc
            )
            NavTile(
                title = "GSYNK рядом",
                subtitle = "Bluetooth и Wi-Fi Direct",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Blue,
                liveText = if (com.gsynk.app.core.Nearby.isConnected()) "связь есть" else "связаться",
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "nearby" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Локальный ИИ",
                subtitle = "чат офлайн",
                iconRes = R.drawable.ic_shield,
                brush = TileBrush.Violet,
                modifier = Modifier.weight(1f),
                onClick = { showChat = true }
            )
            NavTile(
                title = "Скринсейвер",
                subtitle = "полка с часами",
                iconRes = R.drawable.ic_schedule,
                brush = TileBrush.Ink,
                modifier = Modifier.weight(1f),
                onClick = { showScreensaver = true }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Обновления",
                subtitle = "версия ${com.gsynk.app.BuildConfig.VERSION_NAME}",
                iconRes = R.drawable.ic_bolt,
                brush = if (updateAvailable) TileBrush.Green else TileBrush.Ink,
                badge = if (updateAvailable) "новое" else null,
                liveText = if (updateAvailable) "Обновить" else "Проверить",
                modifier = Modifier.weight(1f),
                onClick = {
                    vm.checkUpdate(silent = true)
                    showUpdate = true
                }
            )
        }

        // ————— лаборатория: низкоуровневые опыты с железом —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Лаборатория железа")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Мигание экраном, AMOLED и ШИМ, все датчики, катушки вибромотора, " +
                "индукция, ИК-пульт, скрытые экраны Android и HyperOS — без root и без риска.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(9.dp))
        LiveTile(
            title = "Лаборатория",
            subtitle = "то, чего нет в магазинах приложений",
            brush = TileBrush.Violet,
            iconRes = R.drawable.ic_tune,
            liveText = "строб · экран · датчики · катушки · HyperOS",
            height = 108.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { openExtra = "lab" }
        )

        // ————— хакерский набор —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Хакерский набор")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Измерения, кодирование, радиочастоты — всё офлайн, без интернета",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(9.dp))
        TileRow {
            NavTile(
                title = "Пароль",
                subtitle = "и QR-код",
                iconRes = R.drawable.ic_shield,
                brush = TileBrush.Amber,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "password" }
            )
            NavTile(
                title = "Морзянка",
                subtitle = "мигание фонариком",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Violet,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "morse" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Металл",
                subtitle = "магнитометр",
                iconRes = R.drawable.ic_shield,
                brush = TileBrush.Blue,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "metal" }
            )
            NavTile(
                title = "Уровень",
                subtitle = "наклон в градусах",
                iconRes = R.drawable.ic_tune,
                brush = TileBrush.Green,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "level" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Шумомер",
                subtitle = "уровень в дБ",
                iconRes = R.drawable.ic_volume,
                brush = TileBrush.Red,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "sound" }
            )
            NavTile(
                title = "Система",
                subtitle = "живой дашборд",
                iconRes = R.drawable.ic_schedule,
                brush = TileBrush.Ink,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "sysinfo" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Кодер",
                subtitle = "base64, URL, hex",
                iconRes = R.drawable.ic_copy,
                brush = TileBrush.Violet,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "coder" }
            )
            NavTile(
                title = "Подсети",
                subtitle = "калькулятор IP",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Cyan,
                modifier = Modifier.weight(1f),
                onClick = { openExtra = "subnet" }
            )
        }

        // ————— системные экраны —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Системные экраны")
        Spacer(Modifier.height(9.dp))
        ChipFlow {
            listOf(
                "Звук" to "sound",
                "Батарея" to "battery",
                "NFC" to "nfc",
                "Wi-Fi" to "wifi",
                "Bluetooth" to "bluetooth",
                "Геолокация" to "location",
                "Дисплей" to "display",
                "Данные" to "data",
                "Приложения" to "apps"
            ).forEach { (title, key) ->
                SortChip(label = title, selected = false) { tools.openSettings(key) }
            }
        }

        // ————— постоянный остров —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Остров у камеры")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = CardBorder) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (settings.islandPersistent) "Постоянный остров включён" else "Только вспышки",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Позиция: " + com.gsynk.app.core.IslandPosition.label(settings.islandPosition) +
                            if (settings.islandCompact) " • компактный" else " • обычный",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
                PrimaryButton(
                    text = if (settings.islandPersistent) "Выключить" else "Включить",
                    onClick = { vm.setIslandPersistent(!settings.islandPersistent) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Показывать:", color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                IslandWidget.ALL.forEach { widget ->
                    SortChip(
                        label = IslandWidget.label(widget),
                        selected = widget in settings.islandWidgets
                    ) { vm.toggleIslandWidget(widget, widget !in settings.islandWidgets) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Расположение:", color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                com.gsynk.app.core.IslandPosition.ALL.forEach { position ->
                    SortChip(
                        label = com.gsynk.app.core.IslandPosition.label(position)
                            .replace("Сверху ", "")
                            .replace("По центру ", "центр "),
                        selected = settings.islandPosition == position
                    ) { vm.setIslandPosition(position) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SortChip("Компактный", settings.islandCompact) { vm.setIslandCompact(true) }
                SortChip("Обычный", !settings.islandCompact) { vm.setIslandCompact(false) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Сдвиг по вертикали: ${settings.islandOffsetY} точек",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SortChip(label = "Выше на 4", selected = false) { vm.setIslandOffset((settings.islandOffsetY - 4).coerceIn(-40, 80)) }
                SortChip(label = "Ниже на 4", selected = false) { vm.setIslandOffset((settings.islandOffsetY + 4).coerceIn(-40, 80)) }
                SortChip(label = "Сброс", selected = false) { vm.setIslandOffset(0) }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Показать образец",
                onClick = { vm.fullIslandPreview() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ————— нагрузка: процессор, ускоритель и ИИ —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Нагрузка и вычисления")
        Spacer(Modifier.height(9.dp))
        TileRow {
            NavTile(
                title = "Нагрузка",
                subtitle = "все ядра и ускоритель",
                iconRes = R.drawable.ic_bolt,
                brush = TileBrush.Cyan,
                badge = if (turboActive) "идёт" else null,
                liveText = if (turboActive) "%.1f млрд оп/с".format(turboSpeed) else "выключено",
                modifier = Modifier.weight(1f),
                onClick = { showTurbo = true }
            )
            NavTile(
                title = "Хроника",
                subtitle = "что было за день",
                iconRes = R.drawable.ic_schedule,
                brush = TileBrush.Violet,
                liveText = "события",
                modifier = Modifier.weight(1f),
                onClick = { showChronicle = true }
            )
        }

        // ————— приложение: отчёты и диагностика —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Приложение")
        Spacer(Modifier.height(9.dp))
        TileRow {
            NavTile(
                title = "Диагностика",
                subtitle = "отчёты и журнал",
                iconRes = R.drawable.ic_tune,
                brush = TileBrush.Ink,
                badge = if (vm.crashCountNow() > 0) vm.crashCountNow().toString() else null,
                liveText = "Сбоев: ${vm.crashCountNow()}",
                modifier = Modifier.weight(1f),
                onClick = { showDiagnostics = true }
            )
            NavTile(
                title = "Собрать отчёт",
                subtitle = "состояние телефона",
                iconRes = R.drawable.ic_copy,
                brush = TileBrush.Violet,
                liveText = "Сейчас",
                modifier = Modifier.weight(1f),
                onClick = { vm.collectReport("Отчёт по кнопке из мультитула") }
            )
        }

        // ————— профиль устройства (только показ) —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Профиль устройства")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = if (profile.isFull) GrapeGreen.copy(alpha = 0.45f) else CardBorder) {
            Text(
                text = profile.model,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light)
            )
            Text(profile.summary, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(12.dp))
            InfoRow("Набор функций", if (profile.isFull) "полный" else "базовый")
            InfoRow("Android", profile.androidLabel)
            InfoRow("NFC", if (profile.hasNfc) "есть" else "нет")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Определяется автоматически по модели и системе — переключать вручную не нужно.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }

        // ————— живой дашборд —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Сейчас в телефоне")
        Spacer(Modifier.height(9.dp))
        SurfaceCard {
            val snapshot = stats
            if (snapshot == null) {
                Text("Считываю показатели…", color = TextDim, style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
            } else {
                InfoRow("Память", "${snapshot.ramUsedMb} / ${snapshot.ramTotalMb} МБ")
                BarIndicator(percent = snapshot.ramPercent, brush = TileBrush.Violet)
                Spacer(Modifier.height(10.dp))
                InfoRow("Накопитель", "свободно %.1f ГБ".format(snapshot.storageFreeGb))
                BarIndicator(percent = 100 - snapshot.storageUsedPercent, brush = TileBrush.Cyan)
                Spacer(Modifier.height(10.dp))
                // процент и форматирование разделены: «%» рядом со «•» валил приложение
                InfoRow(
                    "Батарея",
                    snapshot.batteryPercent.toString() + "% • " +
                        "%.1f".format(snapshot.batteryTempC) + "°C"
                )
                BarIndicator(percent = snapshot.batteryPercent, brush = TileBrush.Green)
                Spacer(Modifier.height(12.dp))
                InfoRow("Ядер", snapshot.cores.toString())
                InfoRow("Включён", "${snapshot.uptimeHours} ч")
            }
        }
    }
}
