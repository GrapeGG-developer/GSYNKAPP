@file:OptIn(ExperimentalMaterial3Api::class)

package com.gsynk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.R
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleScheduler
import com.gsynk.app.ui.theme.CardBgAlt
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
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    // мультитул: устройство, игры, инструменты
    val toolsVm: ToolsViewModel = viewModel()
    val device by toolsVm.device.collectAsStateWithLifecycle()
    val snakeBest by toolsVm.bestSnake.collectAsStateWithLifecycle()
    val reactionBest by toolsVm.bestReaction.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf("main") }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // доступы пересчитываем при каждом возврате в приложение
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val snapshot = remember(permTick) { Permissions.snapshot(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permTick++ }

    var editorOpen by remember { mutableStateOf(false) }
    var editorRule by remember { mutableStateOf<Rule?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    // анимация «плюс-кнопки»
    var fabSpin by remember { mutableFloatStateOf(0f) }
    val fabRotation by animateFloatAsState(
        targetValue = fabSpin,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fab"
    )

    // часы для обратного отсчёта
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = ZonedDateTime.now()
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.clearMessage()
    }

    val nextRun = remember(rules, now) {
        rules.filter { it.enabled }
            .mapNotNull { rule -> RuleScheduler.nextOccurrence(rule, now)?.let { it to rule } }
            .minByOrNull { it.first }
    }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0.0"
    }

    when (screen) {
        "games" -> {
            GamesScreen(
                bestSnake = snakeBest,
                bestReaction = reactionBest,
                onSaveSnake = toolsVm::saveSnakeScore,
                onSaveReaction = toolsVm::saveReaction,
                onBack = { screen = "main" }
            )
            return
        }

        "tools" -> {
            ToolsScreen(
                vm = toolsVm,
                onBack = { screen = "main" },
                onOpenScreensaver = { screen = "screensaver" },
                onOpenGrapeUi = { screen = "grape" }
            )
            return
        }

        "nfc" -> {
            NfcLinkScreen(onBack = { screen = "main" })
            return
        }

        "screensaver" -> {
            ScreensaverScreen(onBack = { screen = "main" })
            return
        }

        "grape" -> {
            GrapeUiScreen(onBack = { screen = "main" })
            return
        }
    }

    AnimatedGrapeBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                AddRuleFab(rotation = fabRotation) {
                    fabSpin += 90f
                    editorRule = null
                    editorOpen = true
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    Appear(index = 0) { AppHeader(onSettings = { settingsOpen = true }) }
                }

                item(key = "metrotiles") {
                    Appear(index = 1) {
                        Column {
                            TileRow {
                                AutopilotTile(
                                    enabled = settings.autopilot,
                                    countdown = nextRun?.let { humanDelta(now, it.first) } ?: "нет правил",
                                    rulesCount = rules.size,
                                    modifier = Modifier.weight(1f),
                                    onClick = { vm.setAutopilot(!settings.autopilot) }
                                )
                                DeviceTile(
                                    model = device.model,
                                    tierSummary = if (device.isFull) "MAIN активен" else "базовый набор",
                                    isFull = device.isFull,
                                    modifier = Modifier.weight(1f),
                                    onClick = { screen = "tools" }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            TileRow {
                                NavTile(
                                    title = "Игры",
                                    subtitle = "змейка и реакция",
                                    iconRes = R.drawable.ic_bolt,
                                    tint = TileTint.Green,
                                    liveText = if (snakeBest == 0) "Играть" else "рекорд: $snakeBest",
                                    modifier = Modifier.weight(1f),
                                    onClick = { screen = "games" }
                                )
                                NavTile(
                                    title = "Инструменты",
                                    subtitle = "задачи, чат, обновление",
                                    iconRes = R.drawable.ic_tune,
                                    tint = TileTint.Violet,
                                    liveText = "Открыть",
                                    modifier = Modifier.weight(1f),
                                    onClick = { screen = "tools" }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            TileRow {
                                NavTile(
                                    title = "Grape Link",
                                    subtitle = "передача касанием",
                                    iconRes = R.drawable.ic_copy,
                                    tint = TileTint.Mixed,
                                    badge = if (device.hasNfc) "NFC" else "нет NFC",
                                    liveText = "Коснуться",
                                    modifier = Modifier.weight(1f),
                                    onClick = { screen = "nfc" }
                                )
                                NavTile(
                                    title = "Скринсейвер",
                                    subtitle = "DVD-логотип летает",
                                    iconRes = R.drawable.ic_brightness,
                                    tint = TileTint.Dark,
                                    liveText = "Запустить",
                                    modifier = Modifier.weight(1f),
                                    onClick = { screen = "screensaver" }
                                )
                            }
                        }
                    }
                }

                item(key = "permissions") {
                    Appear(index = 2) {
                        PermissionsCard(
                            snapshot = snapshot,
                            onOpen = { id ->
                                val needRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                if (id == Permissions.ID_NOTIF && needRuntime) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    Permissions.open(context, id)
                                }
                            }
                        )
                    }
                }

                item(key = "status") {
                    Appear(index = 3) {
                        StatusCard(
                            settings = settings,
                            nextRun = nextRun,
                            now = now,
                            hasRules = rules.isNotEmpty(),
                            brightnessAllowed = snapshot.items
                                .firstOrNull { it.id == Permissions.ID_WRITE }?.granted == true,
                            onAutopilot = vm::setAutopilot
                        )
                    }
                }

                if (rules.isNotEmpty()) {
                    item(key = "rulesTitle") {
                        Appear(index = 4) { SectionTitle("Правила", Modifier.padding(start = 4.dp, top = 4.dp)) }
                    }
                }

                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        isNext = nextRun?.second?.id == rule.id && settings.autopilot,
                        onEdit = {
                            editorRule = rule
                            editorOpen = true
                        },
                        onApply = { vm.applyNow(rule) },
                        onDuplicate = { vm.duplicate(rule) },
                        onToggle = { enabled -> vm.setEnabled(rule, enabled) },
                        onDelete = { vm.delete(rule) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(260),
                            fadeOutSpec = tween(180)
                        )
                    )
                }

                if (rules.isEmpty()) {
                    item(key = "empty") {
                        Appear(index = 4) {
                            EmptyState(
                                onAdd = {
                                    fabSpin += 90f
                                    editorRule = null
                                    editorOpen = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        RuleEditorSheet(
            initial = editorRule,
            onDismiss = { editorOpen = false },
            onSave = { rule ->
                vm.upsert(rule)
                editorOpen = false
            },
            onDelete = { rule ->
                vm.delete(rule)
                editorOpen = false
            }
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            settings = settings,
            versionName = versionName,
            overlayAllowed = snapshot.items.firstOrNull { it.id == Permissions.ID_OVERLAY }?.granted == true,
            onDismiss = { settingsOpen = false },
            onAutopilot = vm::setAutopilot,
            onNotifications = vm::setNotifications,
            onIsland = vm::setIsland,
            onIslandPosition = vm::setIslandPosition,
            onSyncAfterBoot = vm::setSyncAfterBoot,
            onSyncNow = vm::syncToSchedule,
            onRestoreAutoBrightness = vm::restoreAutoBrightness,
            onPreviewIsland = vm::previewIsland,
            onOpenAutostart = { Permissions.open(context, Permissions.ID_AUTOSTART) },
            onOpenOverlay = { Permissions.open(context, Permissions.ID_OVERLAY) }
        )
    }
}

// ————————————————————————————————————————————————
//  Заголовок
// ————————————————————————————————————————————————

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            GradientText(text = "GSYNK", style = MaterialTheme.typography.displaySmall)
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(CardBgAlt)
                .border(1.dp, ChipBorder, CircleShape)
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_tune),
                contentDescription = "Настройки",
                tint = TextPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Разрешения (компактно, без описаний)
// ————————————————————————————————————————————————

@Composable
private fun PermissionsCard(snapshot: Permissions.Snapshot, onOpen: (String) -> Unit) {
    if (snapshot.missing.isEmpty()) {
        SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14512C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = GrapeGreenSoft, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Все доступы выданы", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
        return
    }

    SurfaceCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = GrapeVioletSoft,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (snapshot.missing.size == 1) "Нужен 1 доступ" else "Нужно доступов: ${snapshot.missing.size}",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        snapshot.items.forEach { item ->
            PermissionRow(item = item, onClick = { onOpen(item.id) })
        }
    }
}

@Composable
private fun PermissionRow(item: Permissions.Item, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.granted) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (item.granted) GrapeGreenSoft else GrapeVioletSoft,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            color = if (item.granted) TextSecondary else TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (item.granted) {
            Text("готово", color = TextDim, style = MaterialTheme.typography.labelSmall)
        } else if (item.optional) {
            TagChip(text = "по желанию", onClick = onClick)
        } else {
            TagChip(text = "Дать", selected = true, onClick = onClick)
        }
    }
}

// ————————————————————————————————————————————————
//  Статус и следующее срабатывание
// ————————————————————————————————————————————————

@Composable
private fun StatusCard(
    settings: AppSettings,
    nextRun: Pair<ZonedDateTime, Rule>?,
    now: ZonedDateTime,
    hasRules: Boolean,
    brightnessAllowed: Boolean,
    onAutopilot: (Boolean) -> Unit
) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Автопилот")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (settings.autopilot) "включён" else "выключен",
                    color = if (settings.autopilot) GrapeGreenSoft else TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.autopilot,
                onCheckedChange = onAutopilot,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF07160E),
                    checkedTrackColor = GrapeGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Color(0xFF2A1E40),
                    uncheckedBorderColor = ChipBorder
                )
            )
        }

        AnimatedVisibility(visible = settings.autopilot) {
            Column {
                Spacer(Modifier.height(14.dp))
                ThinDivider()
                Spacer(Modifier.height(14.dp))

                if (nextRun != null) {
                    SectionTitle("Следующее срабатывание")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientText(
                            text = nextRun.first.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseDot(color = GrapeGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = nextRun.second.name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            AnimatedContent(
                                targetState = humanDelta(now, nextRun.first),
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                                label = "delta"
                            ) { delta ->
                                Text(
                                    text = "через $delta",
                                    color = TextDim,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (nextRun.second.useBrightness) {
                            MetricPill(
                                icon = ImageVector.vectorResource(R.drawable.ic_brightness),
                                text = "${nextRun.second.brightness}%",
                                tint = GrapeVioletSoft
                            )
                        }
                        if (nextRun.second.useVolume) {
                            MetricPill(
                                icon = ImageVector.vectorResource(R.drawable.ic_volume),
                                text = "${nextRun.second.volume}%",
                                tint = GrapeGreenSoft
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (hasRules) "Все правила выключены" else "Добавьте первое правило",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(visible = settings.autopilot && !brightnessAllowed) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = GrapeVioletSoft,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Яркость не меняется без доступа",
                        color = GrapeVioletSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ————————————————————————————————————————————————
//  Карточка правила
// ————————————————————————————————————————————————

@Composable
private fun RuleCard(
    rule: Rule,
    isNext: Boolean,
    onEdit: () -> Unit,
    onApply: () -> Unit,
    onDuplicate: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isNext) GrapeGreen else ChipBorder,
        animationSpec = tween(450),
        label = "ruleBorder"
    )

    SurfaceCard(
        modifier = modifier,
        borderColor = borderColor,
        onClick = onEdit
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.timeLabel,
                        color = if (rule.enabled) TextPrimary else TextDim,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (isNext) {
                        Spacer(Modifier.width(10.dp))
                        PulseDot(color = GrapeGreen)
                    }
                    if (!rule.enabled) {
                        Spacer(Modifier.width(10.dp))
                        TagChip(text = "выкл")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(rule.name, color = TextSecondary, style = MaterialTheme.typography.titleSmall)
                val scheduleText = buildString {
                    append(rule.daysLabel())
                    if (rule.hasDates) append(" • разово: ${rule.datesLabel(2)}")
                    if (rule.useInterval) append(" • ${rule.intervalLabel} с ${rule.intervalStartLabel}")
                }
                Text(scheduleText, color = TextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rule.useBrightness) {
                        MetricPill(
                            icon = ImageVector.vectorResource(R.drawable.ic_brightness),
                            text = "${rule.brightness}%",
                            tint = GrapeVioletSoft
                        )
                    }
                    if (rule.useVolume) {
                        MetricPill(
                            icon = ImageVector.vectorResource(R.drawable.ic_volume),
                            text = "${rule.volume}%",
                            tint = GrapeGreenSoft
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Box {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Действия",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = CardBgAlt
                    ) {
                        DropdownMenuItem(
                            text = { Text("Применить сейчас", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = GrapeGreenSoft) },
                            onClick = {
                                menuOpen = false
                                onApply()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Изменить", color = TextPrimary) },
                            leadingIcon = {
                                Icon(ImageVector.vectorResource(R.drawable.ic_tune), null, tint = GrapeVioletSoft)
                            },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Дублировать", color = TextPrimary) },
                            leadingIcon = {
                                Icon(ImageVector.vectorResource(R.drawable.ic_copy), null, tint = GrapeVioletSoft)
                            },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF120720),
                        checkedTrackColor = GrapeViolet,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF2A1E40),
                        uncheckedBorderColor = ChipBorder
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rule.useBrightness) {
                ProgressLine(percent = rule.brightness, brush = GrapeGradient)
            }
            if (rule.useVolume) {
                ProgressLine(percent = rule.volume, brush = GrapeGradient)
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    SurfaceCard {
        Text("Правил пока нет", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Добавить правило",
            icon = Icons.Filled.Add,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddRuleFab(rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(18.dp)
            .size(62.dp)
            .shadow(14.dp, CircleShape, spotColor = Color.Black, ambientColor = Color.Black)
            .clip(CircleShape)
            .background(GrapeGradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Добавить правило",
            tint = Color(0xFF100619),
            modifier = Modifier
                .size(30.dp)
                .rotate(rotation)
        )
    }
}

private fun humanDelta(from: ZonedDateTime, to: ZonedDateTime): String {
    val duration = Duration.between(from, to)
    if (duration.isNegative) return "сейчас"
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return buildString {
        if (days > 0) append("$days д ")
        if (hours > 0 || days > 0) append("$hours ч ")
        append("$minutes мин")
    }.trim()
}
