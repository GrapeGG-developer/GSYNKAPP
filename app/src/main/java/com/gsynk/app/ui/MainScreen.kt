@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.R
import com.gsynk.app.core.Ciphers
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.ReleaseNews
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
import com.gsynk.app.ui.theme.GioBrush
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
    val hadCrash by vm.lastCrash.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val agentUnseen by vm.agentUnseen.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(Permissions.snapshot(context)) }
    var rogueInvite by remember { mutableStateOf<NfcLinkInvite?>(null) }
    // Ровно одна панель на экране: раньше их можно было открыть несколько сразу,
    // и они накладывались друг на друга. null — главный экран.
    var panel by remember { mutableStateOf<String?>(null) }
    var panelBeforeUpdate by remember { mutableStateOf<String?>(null) }
    // Каталог: заявки роутера и состояние плиток главного экрана.
    var pendingTool by remember { mutableStateOf<String?>(null) }
    var pendingFlag by remember { mutableStateOf<String?>(null) }
    var pendingLab by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(Catalog.loadFavorites(context)) }
    val toolsVm: ToolsViewModel = viewModel()

    // подземелье на два телефона: сопряжение NFC + игра по Wi-Fi
    var coopInvite by remember { mutableStateOf<com.gsynk.app.core.NfcLink.GameInvite?>(null) }
    var coopRole by remember { mutableStateOf<com.gsynk.app.core.WifiCoop.Role?>(null) }
    // открыть комнату сразу, без лишнего нажатия: так делает касание в режиме «отдаю»
    var coopAutoHost by remember { mutableStateOf(false) }
    val profile = remember { DeviceProfile.detect(context) }

    // пересчёт разрешений при возвращении из системных экранов
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, panel == "settings", panel == "tools", panel == "nfc") {
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

    // Настройки внешнего вида живут в одном месте: акцент, скорость фона, отклик.
    LaunchedEffect(settings) {
        UiTweaks.apply(settings)
        // Пасхалка «десять меток»: счётчик касаний растёт только от настоящих меток.
        if (settings.nfcTouches >= 10) com.gsynk.app.core.Eggs.unlock(context, "nfc10")
    }

    // пришли по касанию метки — открываем Grape Link сразу.
    // Исключение: касание принесло обновление или просьбу «проверь версию» — тогда
    // открываем панель обновлений и сразу дёргаем проверку.
    LaunchedEffect(Unit) {
        val pending = PendingNfcPayload.payload
        when (pending?.kind) {
            com.gsynk.app.core.NfcLink.KIND_UPDATE -> {
                com.gsynk.app.core.UpdateShare.parse(pending.body)?.let {
                    com.gsynk.app.core.UpdateShare.put(it)
                }
                com.gsynk.app.core.UpdateShare.takePing()
                vm.checkUpdate(silent = true)
                panel = "update"
            }

            com.gsynk.app.core.NfcLink.KIND_UPDATE_PING -> {
                com.gsynk.app.core.UpdateShare.putPing(
                    com.gsynk.app.core.UpdateShare.pingFrom(pending.body)
                )
                vm.checkUpdate(silent = true)
                panel = "update"
            }

            null -> Unit
            else -> panel = "nfc"
        }
    }

    // Единый роутер: любому экрану разрешено открыть что угодно одной строкой.
    val route by Router.request.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        val request = route ?: return@LaunchedEffect
        val (kind, name) = Router.split(request)
        when (kind) {
            "panel" -> {
                if (name == "schedule_new") {
                    panel = "schedule"
                    creating = true
                } else {
                    panel = name
                }
                Router.consume()
            }

            "page" -> {
                panel = "page:" + name
                Router.consume()
            }

            "sys" -> {
                // «report» — не системный экран, а сбор отчёта: его собирает модель.
                if (name == "report") {
                    vm.collectReport("Отчёт по кнопке из каталога")
                } else {
                    toolsVm.openSettings(name)
                }
                Router.consume()
            }

            "tool" -> {
                pendingTool = name
                panel = "tools"
                Router.consume()
            }

            "flag" -> {
                pendingFlag = name
                panel = "tools"
                Router.consume()
            }

            "lab" -> {
                pendingLab = name
                pendingTool = "lab"
                panel = "tools"
                Router.consume()
            }
        }
    }

    // Новая версия сама вылезает на экран. Раньше это работало только на «изменение флага»:
    // закрыл панель один раз — и она больше не появится. Теперь модель зовёт панель событием
    // (первый раз при находке и потом раз в 20 минут), поэтому версия не теряется.
    // Единственное исключение — живая игра: там перебивать нельзя, дождётся.
    val updatePrompt by vm.updatePrompt.collectAsStateWithLifecycle()
    LaunchedEffect(updatePrompt) {
        if (updatePrompt == 0) return@LaunchedEffect
        if (!hasUpdate) return@LaunchedEffect
        if (panel == "rogue" || panel == "coop") return@LaunchedEffect
        panelBeforeUpdate = panel
        panel = "update"
        vm.noteUpdatePanelShown()
    }

    // Автопроверка почаще: при возвращении в приложение, если прошло больше 15 минут.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkUpdateIfStale()
                // Заодно смотрим, не ответил ли агент: ответ появится обычным уведомлением в приложении.
                vm.checkAgentMail(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nextRule = remember(rules) { nextOccurrence(rules) }
    val nextId = remember(rules) { nextRuleId(rules) }

    fun homeTileLines(entry: Catalog.Entry): List<String> = when (entry.id) {
        "nearby" -> listOf(
            "NFC · Grape Link · Bluetooth · Wi-Fi Direct",
            "касаний NFC на этом телефоне: " + settings.nfcTouches,
            profile.model.ifBlank { "передача рядом" }
        )
        "schedule", "schedulestatus" -> listOf(
            "активны " + rules.count { it.enabled } + " из " + rules.size,
            nextRule?.let { "ближайшее изменение через " + humanDelta(ZonedDateTime.now(), it) } ?: "правил пока нет",
            if (settings.autopilot) "автопилот включён" else "автопилот выключен"
        )
        "schedulenew" -> listOf("дни · время · яркость · громкость", "создай правило в категории расписания")
        "ciphers" -> listOf(
            Ciphers.ALL.size.toString() + " шифров на одном экране",
            "🐽 ноль · 🐷 единица",
            "GS-TLS · GANONYM · байт-код · ASM"
        )
        "gstls" -> listOf("ECDH P-256 + AES-256-GCM", "новый эфемерный ключ у каждого пакета", "открытый ключ получателя")
        "ganonym" -> listOf("без автора · устройства · времени", "сжатие и ступени размера", "PBKDF2 + AES-256-GCM")
        "anonymity", "nickgen", "avatarlab", "tracecheck" -> listOf(entry.subtitle, "всё локально на телефоне", "персона из текстового зерна")
        "news" -> ReleaseNews.items.map { it.title }
        "update" -> listOf(
            if (hasUpdate) "новая версия доступна" else "обновлений сейчас нет",
            updateStatus.ifBlank { "проверка через GitHub" }
        )
        "agent" -> listOf(
            if (agentUnseen > 0) "новых ответов: " + agentUnseen else "связь через GitHub",
            "вопросы и ответы агента"
        )
        "lowlevel", "registers" -> listOf("HEX · биты · BE/LE", "IP · A/B/C · флаги", "CRC32 · SHA-256 · дампы")
        else -> buildList {
            add(entry.subtitle)
            if (entry.fresh) add("новое в GSYNK 5.1")
            add(entry.group.hint)
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                item { HomeHeader(model = profile.model, onSettings = { panel = "settings" }, vm = vm) }

                item {
                    AnimatedVisibility(
                        visible = hadCrash,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CrashBanner(onClick = { panel = "diagnostics" })
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = agentUnseen > 0,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AgentMailBanner(unseen = agentUnseen, onClick = { panel = "page:agent" })
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = hasUpdate,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        UpdateBanner(
                            subtitle = updateStatus.ifBlank { "Нажмите, чтобы обновить приложение" },
                            onClick = {
                                vm.noteUpdatePanelShown()
                                panel = "update"
                            }
                        )
                    }
                }

                // ————— новости релиза: фирменная иллюстрация и пункты прямо с главного —————
                item {
                    SurfaceCard(Modifier.fillMaxWidth(), onClick = { panel = "page:news" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.grapescript_logo),
                                contentDescription = "Логотип Grape Script",
                                modifier = Modifier.width(112.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "новости GSYNK 5.1",
                                    color = TextPrimary,
                                    fontSize = Gioui.Type.tileLive,
                                    fontWeight = FontWeight.Light
                                )
                                Text(
                                    text = "шифры до процессора · анонимность · живые плитки",
                                    color = TextSecondary,
                                    fontSize = Gioui.Type.caption
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ReleaseNews.items.take(3).forEach { news ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("•", color = GrapeGreen, fontSize = Gioui.Type.body, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(news.title, color = TextSecondary, fontSize = Gioui.Type.caption)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("открыть все пункты с картинками →", color = GrapeGreen, fontSize = Gioui.Type.caption, fontWeight = FontWeight.SemiBold)
                    }
                }

                // ————— GIOUI: поиск по всем функциям —————
                item {
                    GrapeTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Найти функцию: шифр, пульс, nfc, нагрузка…",
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    ScheduleSummaryCard(
                        active = rules.count { it.enabled },
                        total = rules.size,
                        next = nextRule,
                        autopilot = settings.autopilot,
                        onOpen = { panel = "schedule" }
                    )
                }

                // ————— GIOUI: поиск показывает результат сразу, без лишних экранов —————
                if (query.isNotBlank()) {
                    val found = Catalog.ALL.filter { it.matches(query) }.sortedByDescending { it.hot }
                    item {
                        SectionTitle("найдено: " + found.size)
                        if (found.isEmpty()) {
                            Text(
                                text = "Ничего такого нет — попробуй другое слово: «шифр», «пульс», «nfc», «звук».",
                                color = TextDim, fontSize = Gioui.Type.caption
                            )
                        } else {
                            GioGrid(Modifier.fillMaxWidth()) {
                                found.forEach { entry ->
                                    GioCard(
                                        title = entry.title,
                                        subtitle = entry.subtitle,
                                        size = entry.size,
                                        color = entry.color,
                                        icon = ImageVector.vectorResource(entry.iconRes),
                                        liveLines = homeTileLines(entry),
                                        badge = when {
                                            entry.fresh -> "новое"
                                            entry.id in favorites -> "моё"
                                            else -> null
                                        },
                                        onClick = { Catalog.open(context, entry) },
                                        onLongClick = {
                                            favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
                                            Catalog.saveFavorites(context, favorites)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ————— категории сразу на главном: вертикальная лента живых плиток —————
                    Catalog.Group.entries.forEach { grp ->
                        val entries = Catalog.ALL
                            .filter { it.group == grp }
                            .sortedByDescending { it.hot }
                        if (entries.isNotEmpty()) {
                            stickyHeader(key = "home-group-" + grp.name) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(skinShape("card"))
                                        .background(CardBg.copy(alpha = 0.9f))
                                        .border(1.dp, CardBorder, skinShape("card"))
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            GioGroup(grp.title)
                                            Text(grp.hint, color = TextDim, fontSize = Gioui.Type.caption)
                                        }
                                        TagChip(text = entries.size.toString() + " функций")
                                    }
                                }
                            }
                            item(key = "home-grid-" + grp.name) {
                                GioGrid(Modifier.fillMaxWidth()) {
                                    entries.forEach { entry ->
                                        GioCard(
                                            title = entry.title,
                                            subtitle = entry.subtitle,
                                            size = entry.size,
                                            color = entry.color,
                                            icon = ImageVector.vectorResource(entry.iconRes),
                                            liveLines = homeTileLines(entry),
                                            badge = when {
                                                entry.fresh -> "5.1"
                                                entry.id in favorites -> "моё"
                                                else -> null
                                            },
                                            onClick = { Catalog.open(context, entry) },
                                            onLongClick = {
                                                favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
                                                Catalog.saveFavorites(context, favorites)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }

            SnackbarHost(
                hostState = snackbar,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = skinShape("sheet"),
                        containerColor = CardBgAlt,
                        contentColor = TextPrimary
                    )
                },
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

    if (panel == "settings") {
        SettingsSheet(
            vm = vm,
            onDismiss = { panel = null },
            onOpenTools = {
                panel = null
                panel = "tools"
            }
        )
    }

    if (panel == "schedule") {
        ScheduleScreen(
            vm = vm,
            onBack = { panel = null },
            onCreate = { creating = true },
            onEdit = { editing = it }
        )
    }

    if (panel == "tools") {
        ToolsScreen(
            vm = vm,
            onBack = { panel = null },
            openTool = pendingTool,
            openFlag = pendingFlag,
            openLab = pendingLab,
            onOpened = {
                pendingTool = null
                pendingFlag = null
                pendingLab = null
            },
            // Единственная пользовательская дверь к Grape Link — кнопка NFC внутри «GSYNK рядом».
            onOpenNfc = { panel = "nfc" },
            // «GSYNK рядом» умеет передать игру в те же экраны, что и NFC
            onHostGame = {
                coopInvite = null
                coopAutoHost = true
                panel = null
                panel = "coop"
            },
            onJoinGame = { invite ->
                coopInvite = invite
                coopRole = com.gsynk.app.core.WifiCoop.Role.GUEST
                rogueInvite = NfcLinkInvite(invite.seed, invite.from.ifBlank { "второй телефон" })
                panel = null
                panel = "rogue"
            }
        )
    }

    // Страницы GIOUI: эффекты, скругления, каждый день, шифры, анонимность, новости, ИИ, бенчмарк.
    panel?.takeIf { it.startsWith("page:") }?.let { name ->
        val page = name.removePrefix("page:")
        if (GiouiPage(page, vm, onBack = { panel = null })) return@let
        if (GiouiExtraPage(page, vm, onBack = { panel = null })) return@let
        panel = null
    }

    if (panel == "diagnostics") {
        DiagnosticsScreen(vm = vm, onBack = { panel = null })
    }

    if (panel == "scenes") {
        ScenesScreen(onBack = { panel = null })
    }

    if (panel == "games") {
        GamesScreen(
            onBack = { panel = null },
            onDungeon = { coop ->
                panel = null
                if (coop) {
                    coopInvite = null
                    coopAutoHost = true
                    panel = "coop"
                } else {
                    rogueInvite = null
                    coopRole = null
                    panel = "rogue"
                }
            }
        )
    }

    if (panel == "nfc") {
        NfcLinkScreen(
            onBack = { panel = null },
            // касание в режиме «мой телефон отдаёт» — сразу открываем комнату и игру
            onHostGame = {
                coopInvite = null
                coopAutoHost = true
                panel = null
                panel = "coop"
            },
            // касание с чужим приглашением — подключаемся и сразу играем
            onJoinGame = { invite ->
                coopInvite = invite
                coopRole = com.gsynk.app.core.WifiCoop.Role.GUEST
                rogueInvite = NfcLinkInvite(invite.seed, invite.from.ifBlank { "второй телефон" })
                panel = null
                panel = "rogue"
            },
            // «Передать обновление по NFC»: APK в метку не влезет, поэтому уезжает
            // описание релиза — версия и прямая ссылка, а второй телефон скачает сам.
            provideUpdateShare = {
                val tag = vm.update.value?.latestTag?.takeIf { it.isNotBlank() }
                    ?: ("v" + com.gsynk.app.BuildConfig.VERSION_NAME)
                com.gsynk.app.core.UpdateShare.Share(
                    version = tag,
                    url = vm.update.value?.apkUrl?.takeIf { it.isNotBlank() }
                        ?: com.gsynk.app.core.UpdateShare.apkUrl(settings.updateRepo, tag),
                    sha256 = vm.update.value?.sha256.orEmpty(),
                    from = profile.model
                )
            },
            onCheckUpdate = {
                vm.checkUpdate(silent = true)
                panelBeforeUpdate = "nfc"
                panel = "update"
            }
        )
    }

    if (panel == "coop") {
        CoopScreen(
            onBack = {
                panel = null
                coopInvite = null
                coopAutoHost = false
            },
            autoInvite = coopInvite,
            autoHost = coopAutoHost,
            onPlay = { role, seed ->
                coopRole = role
                coopAutoHost = false
                rogueInvite = NfcLinkInvite(seed, profile.model.ifBlank { "второй телефон" })
                panel = null
                panel = "rogue"
            }
        )
    }

    if (panel == "rogue") {
        RogueScreen(
            onBack = {
                if (coopRole != null) com.gsynk.app.core.WifiCoop.stop()
                coopRole = null
                panel = null
            },
            invite = rogueInvite,
            onInviteByNfc = {
                pendingTool = "nearby"
                panel = "tools"
            },
            coop = coopRole,
            onCoopSetup = {
                panel = null
                coopInvite = null
                panel = "coop"
            }
        )
    }

        // ————— слои поверх всего —————
        // Панель разрешений обязательна и перекрывает приложение, пока всё не выдано.
        PermissionGate()
        // «Показ по касанию»: салют, сердечки, надпись от второго телефона.
        NfcShowHost()
        // Обновление — самый верхний слой: вылезает само и закрывает прочие панели.
        if (panel == "update") {
            UpdateSheet(vm = vm, onDismiss = {
                panel = panelBeforeUpdate
                panelBeforeUpdate = null
                vm.dismissUpdate()
            })
        }
    }
}


@Composable
private fun ScheduleSummaryCard(
    active: Int,
    total: Int,
    next: ZonedDateTime?,
    autopilot: Boolean,
    onOpen: () -> Unit
) {
    SurfaceCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("расписание", color = TextPrimary, fontSize = Gioui.Type.groupHeader, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "активны " + active + " из " + total + " · автопилот " + if (autopilot) "включён" else "выключен",
                    color = TextSecondary,
                    fontSize = Gioui.Type.caption
                )
                Text(
                    text = next?.let {
                        "ближайшее: " + it.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) +
                            " · через " + humanDelta(ZonedDateTime.now(), it)
                    } ?: "ближайшего изменения нет",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
            Text("→", color = GrapeGreen, fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
        }
    }
}

// ————————————————————————————————————————————————
//  Заголовок и живая строка состояния
// ————————————————————————————————————————————————

@Composable
private fun HomeHeader(model: String, onSettings: () -> Unit, vm: MainViewModel? = null) {
    // нажатие на логотип рассыпает виноградную гроздь — простая радость, ничего лишнего
    var logoBurst by remember { mutableIntStateOf(0) }
    val logoContext = LocalContext.current
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.grapescript_logo),
                contentDescription = "Логотип Grape Script",
                modifier = Modifier.width(92.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box {
                    GradientText(
                        text = "GSYNK",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light),
                        brush = GrapeGradient,
                        modifier = Modifier.clickable {
                            logoBurst++
                            com.gsynk.app.core.AutomationActions.vibrate(logoContext, 28L)
                            // Пасхалка «семь касаний»: счётчик живёт в настройках,
                            // поэтому её можно найти и потерять только по своей воле.
                            val (taps, opened) = com.gsynk.app.core.Eggs.tapLogo(logoContext)
                            if (opened) {
                                vm?.showMessage("Пасхалка: " + (com.gsynk.app.core.Eggs.byId("logo7")?.title ?: ""))
                            } else if (taps % 7 == 3) {
                                vm?.showMessage("Уже " + taps + " касаний…")
                            }
                        }
                    )
                    GrapeLogoBurst(
                        trigger = logoBurst,
                        modifier = Modifier
                            .size(180.dp)
                            .offset(x = (-24).dp, y = (-46).dp)
                    )
                }
                Text(
                    text = model.ifBlank { "мультитул для телефона" },
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(skinShape("card"))
                    .background(CardBg)
                    .border(1.dp, ChipBorder, skinShape("card"))
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

/** Сообщение о том, что приложение закрывалось с ошибкой: отчёт уже готов. */
@Composable
private fun CrashBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skinShape("card"))
            .background(GioBrush.Red)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Был сбой",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Отчёт собран — нажмите, чтобы посмотреть и отправить",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Text("→", color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light))
    }
}

/** Панель обновления: выезжает сама, ничего искать не нужно. */
@Composable
private fun UpdateBanner(subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skinShape("card"))
            .background(GioBrush.Green)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Доступно обновление",
                color = Color(0xFF052E1F),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            )
            Text(
                text = subtitle,
                color = Color(0xFF052E1F).copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Text("→", color = Color(0xFF052E1F), style = MaterialTheme.typography.headlineSmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light))
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
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = snapshot.items.count { !it.granted }.toString(),
                color = GrapeVioletSoft,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Откройте и выдайте — иначе часть функций не работает",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
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
            .clip(skinShape("card"))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f)
        )
        Text("выдать →", color = GrapeVioletSoft, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
    }
}

// ————————————————————————————————————————————————
//  Карточка правила (компактнее и мягче прежней)
// ————————————————————————————————————————————————

@Composable
internal fun RuleCard(
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
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
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
                Text(rule.name, color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                val scheduleText = buildString {
                    append(rule.daysLabel())
                    if (rule.hasDates) append(" • разово: ${rule.datesLabel(2)}")
                    if (rule.useInterval) append(" • ${rule.intervalLabel} с ${rule.intervalStartLabel}")
                }
                Text(scheduleText, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
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
                            .clip(skinShape("card"))
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
                        shape = skinShape("sheet"),
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
                ToggleDot(checked = rule.enabled, onChange = { onToggle(!rule.enabled) })
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
internal fun EmptyState(onAdd: () -> Unit) {
    SurfaceCard {
        Text("Правил пока нет", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Правило меняет яркость и громкость по расписанию — например «вечером тише»",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
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

internal fun nextOccurrence(rules: List<Rule>): ZonedDateTime? {
    val now = ZonedDateTime.now()
    return rules.filter { it.enabled }
        .mapNotNull { RuleScheduler.nextOccurrence(it, now) }
        .minOrNull()
}

internal fun nextRuleId(rules: List<Rule>): String? {
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
