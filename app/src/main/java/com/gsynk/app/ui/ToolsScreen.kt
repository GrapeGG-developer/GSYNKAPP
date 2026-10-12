package com.gsynk.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.MainMode
import com.gsynk.app.core.ScheduledTask
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Хаб мультитула: фонарик, DND, задачи со звонком и озвучкой, ИИ-чат, OTA, MAIN-режим. */
@Composable
fun ToolsScreen(
    vm: ToolsViewModel,
    onBack: () -> Unit,
    onOpenScreensaver: () -> Unit,
    onOpenGrapeUi: () -> Unit
) {
    val device by vm.device.collectAsStateWithLifecycle()
    val torch by vm.torch.collectAsStateWithLifecycle()
    val dnd by vm.dnd.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val full = device.isFull

    GrapeScreen(title = "Инструменты", onBack = onBack) {
        // ————— быстрые действия —————
        TileRow {
            ToggleTile(
                title = "Фонарик",
                on = torch,
                iconRes = com.gsynk.app.R.drawable.ic_bolt,
                onText = "горит",
                offText = "выключен",
                modifier = Modifier.weight(1f),
                onClick = vm::toggleTorch
            )
            if (full) {
                ToggleTile(
                    title = "Не беспокоить",
                    on = dnd,
                    iconRes = com.gsynk.app.R.drawable.ic_shield,
                    onText = "тишина",
                    offText = "уведомления идут",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.refreshDnd()
                        vm.toggleDnd()
                    }
                )
            } else {
                NavTile(
                    title = "Скринсейвер",
                    subtitle = "DVD-логотип",
                    iconRes = com.gsynk.app.R.drawable.ic_brightness,
                    tint = TileTint.Dark,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenScreensaver
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TileRow {
            if (full) {
                ToggleTile(
                    title = "Постоянный остров",
                    on = settings.islandPersistent,
                    iconRes = com.gsynk.app.R.drawable.ic_tune,
                    onText = "висит у камеры",
                    offText = "выключен",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.setPersistentIsland(!settings.islandPersistent) }
                )
                NavTile(
                    title = "Скринсейвер",
                    subtitle = "DVD-логотип",
                    iconRes = com.gsynk.app.R.drawable.ic_brightness,
                    tint = TileTint.Dark,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenScreensaver
                )
            } else {
                NavTile(
                    title = "Grape Link",
                    subtitle = "передача касанием",
                    iconRes = com.gsynk.app.R.drawable.ic_copy,
                    tint = TileTint.Mixed,
                    badge = if (device.hasNfc) "NFC" else "нет NFC",
                    modifier = Modifier.weight(1f),
                    onClick = { }
                )
                NavTile(
                    title = "Правила",
                    subtitle = "яркость и громкость",
                    iconRes = com.gsynk.app.R.drawable.ic_schedule,
                    tint = TileTint.Green,
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                )
            }
        }
        if (full) {
            Spacer(Modifier.height(12.dp))
            TileRow {
                NavTile(
                    title = "GRAPEUI",
                    subtitle = "ОС внутри GSYNK",
                    iconRes = com.gsynk.app.R.drawable.ic_tune,
                    tint = TileTint.Dark,
                    badge = "черновик",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenGrapeUi
                )
                NavTile(
                    title = "Grape Link",
                    subtitle = "передача касанием",
                    iconRes = com.gsynk.app.R.drawable.ic_copy,
                    tint = TileTint.Mixed,
                    badge = if (device.hasNfc) "NFC" else "нет NFC",
                    modifier = Modifier.weight(1f),
                    onClick = { }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ————— задачи (полный набор) —————
        if (full) {
            SectionTitle("Отложенные задачи")
            Spacer(Modifier.height(8.dp))
            TaskComposer(
                canCall = true,
                onCreate = { title, kind, number, phrase, delay -> vm.addTask(title, kind, number, phrase, delay) },
                onSpeak = vm::speak
            )

            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                tasks.sortedBy { it.triggerAt }.forEach { task ->
                    TaskRow(task = task, onCancel = { vm.removeTask(task) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(22.dp))

            // ————— ИИ-чат —————
            SectionTitle("GrapeBot — чат с помощником")
            Spacer(Modifier.height(8.dp))
            ChatSection(vm)

            Spacer(Modifier.height(22.dp))

            // ————— обновление —————
            SectionTitle("Обновление GSYNK")
            Spacer(Modifier.height(8.dp))
            UpdateSection(vm)

            Spacer(Modifier.height(22.dp))
        } else {
            SectionTitle("Базовый набор")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "На этом устройстве включены: расписание яркости и громкости, фонарик, " +
                    "мини-игры, скринсейвер и NFC-обмен. Постоянный остров, задачи со звонком, " +
                    "ИИ-чат и OTA доступны в полном (MAIN) режиме — его можно включить ниже.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(22.dp))
        }

        // ————— MAIN-режим —————
        SectionTitle("MAIN-режим устройства")
        Spacer(Modifier.height(8.dp))
        DeviceSection(vm, device)

        Spacer(Modifier.height(24.dp))
        SecondaryButton(text = "Назад", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

// ————————————————————————————————————————————————
//  Создание задач
// ————————————————————————————————————————————————

@Composable
private fun TaskComposer(
    canCall: Boolean,
    onCreate: (String, String, String, String, Long) -> Unit,
    onSpeak: (String) -> Unit
) {
    var kind by remember { mutableStateOf(ScheduledTask.KIND_CALL) }
    var target by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var delaySeconds by remember { mutableLongStateOf(180L) }

    SurfaceCard(corner = 22.dp) {
        Text("Что сделать", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip("Позвонить", kind == ScheduledTask.KIND_CALL) { kind = ScheduledTask.KIND_CALL }
            KindChip("Озвучить", kind == ScheduledTask.KIND_SPEAK) { kind = ScheduledTask.KIND_SPEAK }
            KindChip("Розыгрыш", kind == ScheduledTask.KIND_FAKE_CALL) { kind = ScheduledTask.KIND_FAKE_CALL }
        }

        Spacer(Modifier.height(12.dp))
        GrapeTextField(
            value = target,
            onValueChange = { target = it.take(60) },
            label = if (kind == ScheduledTask.KIND_FAKE_CALL) "Имя «звонящего»" else "Контакт или номер",
            keyboardType = if (kind == ScheduledTask.KIND_CALL) KeyboardType.Phone else KeyboardType.Text
        )

        Spacer(Modifier.height(10.dp))
        GrapeTextField(
            value = phrase,
            onValueChange = { phrase = it.take(160) },
            label = "Фраза для озвучки (необязательно)",
            singleLine = false
        )

        Spacer(Modifier.height(12.dp))
        Text("Когда", color = TextDim, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10L to "через 10 с", 60L to "1 мин", 180L to "3 мин", 600L to "10 мин").forEach { (seconds, label) ->
                KindChip(label, delaySeconds == seconds) { delaySeconds = seconds }
            }
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = when (kind) {
                ScheduledTask.KIND_CALL -> "Позвонить и сказать"
                ScheduledTask.KIND_FAKE_CALL -> "Устроить розыгрыш"
                else -> "Озвучить"
            },
            onClick = {
                if (kind == ScheduledTask.KIND_SPEAK && phrase.isBlank()) return@PrimaryButton
                onCreate(
                    if (target.isBlank()) "Задача" else target,
                    kind,
                    target,
                    phrase,
                    delaySeconds
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            text = "Озвучить сейчас",
            onClick = { if (phrase.isNotBlank()) onSpeak(phrase) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Подсказка: для звонка нужен контакт или номер; текст в линию читает синтез речи " +
                "телефона — на части прошивок он звучит только в динамик разговора.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) GrapeViolet else CardBgAlt)
            .border(1.dp, if (selected) Color.Transparent else ChipBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF120720) else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun TaskRow(task: ScheduledTask, onCancel: () -> Unit) {
    val time = remember(task.triggerAt) {
        DateTimeFormatter.ofPattern("HH:mm:ss").format(
            Instant.ofEpochMilli(task.triggerAt).atZone(ZoneId.systemDefault())
        )
    }
    SurfaceCard(corner = 18.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = ScheduledTask.KIND_TITLES[task.kind] ?: "Задача",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append(time)
                        if (task.contactName.isNotBlank()) append(" • ").append(task.contactName)
                        if (task.phrase.isNotBlank()) append(" • «").append(task.phrase.take(40)).append("»")
                    },
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardBgAlt)
                    .border(1.dp, ChipBorder, RoundedCornerShape(50))
                    .clickable { onCancel() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Отменить", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ————————————————————————————————————————————————
//  Чат
// ————————————————————————————————————————————————

@Composable
private fun ChatSection(vm: ToolsViewModel) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    SurfaceCard(corner = 22.dp) {
        Column(Modifier.height(240.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chat) { message ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (message.fromUser) GrapeViolet.copy(alpha = 0.85f) else CardBgAlt)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = message.text,
                                color = if (message.fromUser) Color(0xFF120720) else TextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    GrapeTextField(
                        value = input,
                        onValueChange = { input = it.take(200) },
                        label = "Команда: «яркость 40», «фонарик»…"
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrapeViolet)
                        .clickable {
                            vm.sendChat(input)
                            input = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text("→", color = Color(0xFF120720), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// ————————————————————————————————————————————————
//  OTA
// ————————————————————————————————————————————————

@Composable
private fun UpdateSection(vm: ToolsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val result by vm.update.collectAsStateWithLifecycle()
    val busy by vm.updateBusy.collectAsStateWithLifecycle()
    var repo by remember(settings.updateRepo) { mutableStateOf(settings.updateRepo) }

    SurfaceCard(corner = 22.dp) {
        GrapeTextField(
            value = repo,
            onValueChange = {
                repo = it.take(80)
                vm.setUpdateRepo(repo)
            },
            label = "Репозиторий GitHub: владелец/gsynk"
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (busy) "Проверяю…" else "Проверить обновление",
            onClick = vm::checkUpdate,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )

        val state = result
        if (state != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.message, color = if (state.updateAvailable) GrapeGreen else TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (state.latestTag.isNotBlank()) {
                Text("Текущая: ${state.currentTag} • Новейшая: ${state.latestTag}", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            if (state.updateAvailable) {
                Spacer(Modifier.height(10.dp))
                if (state.notes.isNotBlank()) {
                    Text(state.notes, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                }
                PrimaryButton(
                    text = if (busy) "Скачиваю…" else "Скачать и установить",
                    onClick = vm::downloadAndInstall,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ————————————————————————————————————————————————
//  MAIN-режим
// ————————————————————————————————————————————————

@Composable
private fun DeviceSection(vm: ToolsViewModel, device: DeviceProfile.Info) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    SurfaceCard(corner = 22.dp) {
        Text(device.model, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = "${device.brand} • Android ${device.androidLabel} (SDK ${device.androidSdk}) • NFC: ${if (device.hasNfc) "да" else "нет"}",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Определено как: ${if (device.isFull) "полный набор (MAIN)" else "базовый набор"}",
            color = if (device.isFull) GrapeGreen else GrapeVioletSoft,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(device.summary, color = TextSecondary, style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChip("Авто", settings.mainMode == MainMode.AUTO) { vm.setMainMode(MainMode.AUTO) }
            KindChip("Как на Poco X8 Pro Max", settings.mainMode == MainMode.ON) { vm.setMainMode(MainMode.ON) }
            KindChip("Базовый", settings.mainMode == MainMode.OFF) { vm.setMainMode(MainMode.OFF) }
        }

        Spacer(Modifier.height(12.dp))
        Text("Доступные функции", color = TextDim, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        DeviceProfile.Feature.TITLES.forEach { (id, title) ->
            val enabled = DeviceProfile.feature(device, id)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = if (enabled) "✓" else "—",
                    color = if (enabled) GrapeGreen else TextDim,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(8.dp))
                Text(title, color = if (enabled) TextPrimary else TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
