@file:OptIn(ExperimentalMaterial3Api::class)

package com.gsynk.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.R
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.IslandPosition
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleScheduler
import com.gsynk.app.core.RuleSort
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGradient
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Главный экран 2.1.
 *
 * Сверху — крупные живые плитки (автопилот, NFC, разделы) и переключатели, ниже —
 * расписание правил с сортировкой. Панель обновления выезжает сама, когда вышла
 * новая версия: пользователю не нужно ничего искать.
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val hasUpdate by vm.updateAvailable.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showGames by remember { mutableStateOf(false) }
    var showNfc by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(Permissions.snapshot(context)) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showScenes by remember { mutableStateOf(false) }
    val profile = remember { DeviceProfile.detect(context) }

    // пересчёт разрешений при возвращении из системных экранов
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, showSettings, showTools, showNfc) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = Permissions.snapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // пришли по касанию метки — открываем Grape Link сразу
    LaunchedEffect(Unit) {
        if (PendingNfcPayload.payload != null) showNfc = true
    }

    LaunchedEffect(hasUpdate) {
        if (hasUpdate) showUpdateSheet = true
    }

    val sortedRules = remember(rules, settings.ruleSort) { RuleSort.apply(rules, settings.ruleSort) }
    val nextRule = remember(rules) { nextOccurrence(rules) }

    AnimatedGrapeBackground {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { HomeHeader(model = profile.model, onSettings = { showSettings = true }) }

                item {
                    AnimatedVisibility(
                        visible = hasUpdate,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        UpdateBanner(onClick = { showUpdateSheet = true })
                    }
                }

                item {
                    DeviceTile(
                        model = profile.model.ifBlank { "Устройство" },
                        tierLabel = if (profile.isFull) {
                            "функции определены автоматически"
                        } else {
                            "базовый набор функций"
                        },
                        isFull = profile.isFull,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showTools = true }
                    )
                }

                item {
                    AutopilotTile(
                        enabled = settings.autopilot,
                        countdown = nextRule?.let { "через " + humanDelta(ZonedDateTime.now(), it) } ?: "правил нет",
                        rulesCount = rules.size,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.setAutopilot(!settings.autopilot) }
                    )
                }

                item {
                    TileRow {
                        ToggleTile(
                            title = "Остров",
                            on = settings.islandPersistent,
                            iconRes = R.drawable.ic_brightness,
                            onText = "постоянный",
                            offText = "только вспышки",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setIslandPersistent(!settings.islandPersistent) }
                        )
                        ToggleTile(
                            title = "Уведомления",
                            on = settings.notifications,
                            iconRes = R.drawable.ic_shield,
                            onText = "приходят",
                            offText = "тишина",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.setNotifications(!settings.notifications) }
                        )
                    }
                }

                item {
                    TileRow {
                        NavTile(
                            title = "Grape Link",
                            subtitle = "обмен по NFC",
                            iconRes = R.drawable.ic_bolt,
                            brush = TileBrush.Cyan,
                            liveText = "касаний: ${settings.nfcTouches}",
                            modifier = Modifier.weight(1f),
                            onClick = { showNfc = true }
                        )
                        NavTile(
                            title = "Сцены",
                            subtitle = "один тап",
                            iconRes = R.drawable.ic_shield,
                            brush = TileBrush.Green,
                            liveText = "Ночь · Кино",
                            modifier = Modifier.weight(1f),
                            onClick = { showScenes = true }
                        )
                    }
                }

                item {
                    TileRow {
                        NavTile(
                            title = "Инструменты",
                            subtitle = "мультитул",
                            iconRes = R.drawable.ic_tune,
                            brush = TileBrush.Blue,
                            modifier = Modifier.weight(1f),
                            onClick = { showTools = true }
                        )
                        NavTile(
                            title = "Мини-игры",
                            subtitle = "на счёт",
                            iconRes = R.drawable.ic_schedule,
                            brush = TileBrush.Pink,
                            modifier = Modifier.weight(1f),
                            onClick = { showGames = true }
                        )
                    }
                }

                if (!permissions.allGranted) {
                    item {
                        PermissionsCard(
                            snapshot = permissions,
                            onOpen = { id -> Permissions.open(context, id) }
                        )
                    }
                }

                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Расписание",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${rules.count { it.enabled }} из ${rules.size} активны",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        RuleSort.ALL.forEach { mode ->
                            SortChip(
                                label = RuleSort.label(mode),
                                selected = settings.ruleSort == mode
                            ) { vm.setRuleSort(mode) }
                        }
                    }
                }

                if (sortedRules.isEmpty()) {
                    item { EmptyState(onAdd = { creating = true }) }
                }

                items(sortedRules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        isNext = rule.id == nextRuleId(rules),
                        onEdit = { editing = rule },
                        onApply = { vm.applyNow(rule) },
                        onDuplicate = { vm.duplicate(rule) },
                        onToggle = { vm.setEnabled(rule, it) },
                        onDelete = { vm.delete(rule) }
                    )
                }

                item {
                    AddRuleTile(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { creating = true }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 12.dp)
            )
        }
    }

    if (creating || editing != null) {
        RuleEditorSheet(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { rule ->
                vm.upsert(rule)
                creating = false
                editing = null
            }
        )
    }

    if (showSettings) {
        SettingsSheet(
            vm = vm,
            onDismiss = { showSettings = false },
            onOpenTools = {
                showSettings = false
                showTools = true
            }
        )
    }

    if (showTools) {
        ToolsScreen(vm = vm, onBack = { showTools = false }, onOpenNfc = { showNfc = true })
    }

    if (showScenes) {
        ScenesScreen(onBack = { showScenes = false })
    }

    if (showGames) {
        GamesScreen(onBack = { showGames = false })
    }

    if (showNfc) {
        NfcLinkScreen(onBack = { showNfc = false })
    }

    if (showUpdateSheet) {
        UpdateSheet(vm = vm, onDismiss = {
            showUpdateSheet = false
            vm.dismissUpdate()
        })
    }
}

// ————————————————————————————————————————————————
//  Заголовок и живая строка состояния
// ————————————————————————————————————————————————

@Composable
private fun HomeHeader(model: String, onSettings: () -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GradientText(
                    text = "GSYNK",
                    style = MaterialTheme.typography.displaySmall,
                    brush = GrapeGradient
                )
                Text(
                    text = model.ifBlank { "мультитул для телефона" },
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, CircleShape)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_tune),
                    contentDescription = "Настройки",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Панель обновления: выезжает сама, ничего искать не нужно. */
@Composable
private fun UpdateBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TileBrush.Green)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Доступно обновление",
                color = Color(0xFF052E1F),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Нажмите, чтобы обновить приложение",
                color = Color(0xFF052E1F).copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text("→", color = Color(0xFF052E1F), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PermissionsCard(
    snapshot: Permissions.Snapshot,
    onOpen: (String) -> Unit
) {
    SurfaceCard(borderColor = GrapeViolet.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(color = GrapeViolet)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Нужны доступы",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = snapshot.items.count { !it.granted }.toString(),
                color = GrapeVioletSoft,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Откройте и выдайте — иначе часть функций не работает",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        snapshot.items.filter { !it.granted }.take(6).forEach { item ->
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
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text("выдать →", color = GrapeVioletSoft, style = MaterialTheme.typography.labelMedium)
    }
}

// ————————————————————————————————————————————————
//  Карточка правила (компактнее и мягче прежней)
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
        targetValue = if (isNext) GrapeGreen else CardBorder,
        animationSpec = tween(450),
        label = "ruleBorder"
    )

    SurfaceCard(modifier = modifier, borderColor = borderColor, onClick = onEdit) {
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Правило меняет яркость и громкость по расписанию — например «вечером тише»",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = "Добавить правило",
            icon = Icons.Filled.Add,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ————————————————————————————————————————————————
//  Ближайшее правило
// ————————————————————————————————————————————————

private fun nextOccurrence(rules: List<Rule>): ZonedDateTime? {
    val now = ZonedDateTime.now()
    return rules.filter { it.enabled }
        .mapNotNull { RuleScheduler.nextOccurrence(it, now) }
        .minOrNull()
}

private fun nextRuleId(rules: List<Rule>): String? {
    val now = ZonedDateTime.now()
    return rules.filter { it.enabled }
        .mapNotNull { rule -> RuleScheduler.nextOccurrence(rule, now)?.let { it to rule } }
        .minByOrNull { it.first }
        ?.second?.id
}

fun humanDelta(from: ZonedDateTime, to: ZonedDateTime): String {
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
