@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gsynk.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Главный экран GSYNK 5.1.
 *
 * Дом намеренно пуст, все функции живут в горизонтальном Metro-«Пуске», а каждая
 * открытая плитка работает как полноэкранное приложение с общей статус-панелью.
 */
private data class MinimizedAppState(
    val panel: String,
    val featureRoute: String?,
    val route: String?,
    val title: String
)

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
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var rogueInvite by remember { mutableStateOf<NfcLinkInvite?>(null) }
    // Ровно одна панель на экране: раньше их можно было открыть несколько сразу,
    // и они накладывались друг на друга. null — главный экран.
    var panel by remember { mutableStateOf<String?>(null) }
    var panelBeforeUpdate by remember { mutableStateOf<String?>(null) }
    // Пуск и Metro-оболочка плиток: промежуточных хабов больше нет.
    var startOpen by remember { mutableStateOf(false) }
    var featureRoute by remember { mutableStateOf<String?>(null) }
    var activeRoute by remember { mutableStateOf<String?>(null) }
    var minimizedApps by remember { mutableStateOf<List<MinimizedAppState>>(emptyList()) }
    var favorites by remember { mutableStateOf(Catalog.loadFavorites(context)) }
    // Жидкие окна: одноразовая геометрия нажатой плитки, режим выхода и
    // отложенное действие, которое выполнится только после уплывания окна.
    var liquidLaunch by remember { mutableStateOf<LiquidWindowOrigin?>(null) }
    var liquidLaunchSerial by remember { mutableIntStateOf(0) }
    var liquidExitMode by remember { mutableStateOf<LiquidWindowExit?>(null) }
    var liquidExitJob by remember { mutableStateOf<Job?>(null) }
    var homeUnderlayStart by remember { mutableStateOf(true) }
    val windowScope = rememberCoroutineScope()
    var rootWindowShown by remember { mutableStateOf(false) }

    fun prepareLiquidWindow() {
        liquidExitJob?.cancel()
        liquidExitJob = null
        liquidExitMode = null
    }

    fun requestLiquidWindowExit(mode: LiquidWindowExit, action: () -> Unit) {
        if (panel == null || liquidExitMode != null) return
        liquidExitMode = mode
        liquidExitJob = windowScope.launch {
            delay(480L)
            action()
            liquidExitMode = null
            liquidExitJob = null
        }
    }

    // подземелье на два телефона: сопряжение NFC + игра по Wi-Fi
    var coopInvite by remember { mutableStateOf<com.gsynk.app.core.NfcLink.GameInvite?>(null) }
    var coopRole by remember { mutableStateOf<com.gsynk.app.core.WifiCoop.Role?>(null) }
    // открыть комнату сразу, без лишнего нажатия: так делает касание в режиме «отдаю»
    var coopAutoHost by remember { mutableStateOf(false) }
    val profile = remember { DeviceProfile.detect(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Запуск без лишних касаний: EXP-портрет сам открывает «Пуск» — ленту времени
    // на точке «сейчас». Если открыта плитка или есть свёрнутая закладка,
    // их состояние не перебиваем.
    LaunchedEffect(isPortrait, panel, minimizedApps) {
        if (isPortrait && panel == null && minimizedApps.isEmpty()) {
            startOpen = true
        }
    }

    LaunchedEffect(Unit) {
        rootWindowShown = true
    }

    // За открытым приложением остаётся именно тот фон, из которого его открыли:
    // «Пуск» не должен на лету превращаться в пустой дом под morph-ом окна.
    LaunchedEffect(panel, startOpen) {
        if (panel == null) homeUnderlayStart = startOpen
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
            else -> {
                prepareLiquidWindow()
                activeRoute = "tool:nearby"
                startOpen = false
                panel = "nfc"
            }
        }
    }

    // Единый роутер Metro: маршрут сразу становится полноэкранной плиткой.
    val route by Router.request.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        val request = route ?: return@LaunchedEffect
        val (kind, name) = Router.split(request)
        when (kind) {
            "panel" -> {
                prepareLiquidWindow()
                activeRoute = request
                startOpen = false
                minimizedApps = minimizedApps.filterNot { it.route == request }
                if (name == "schedule_new") {
                    panel = "schedule"
                    creating = true
                } else if (name == "tools") {
                    // Старый маршрут больше не ведёт в хаб: только быстрые переключатели.
                    featureRoute = "tool:quick"
                    panel = "feature"
                } else {
                    panel = name
                }
                Router.consume()
            }

            "page" -> {
                prepareLiquidWindow()
                activeRoute = request
                startOpen = false
                minimizedApps = minimizedApps.filterNot { it.route == request }
                panel = "page:" + name
                Router.consume()
            }

            "sys" -> {
                if (name == "report") {
                    vm.collectReport("Отчёт по плитке Пуска")
                } else {
                    com.gsynk.app.core.AutomationActions.openSettings(context, name)
                }
                Router.consume()
            }

            "tool", "flag", "lab" -> {
                prepareLiquidWindow()
                activeRoute = request
                startOpen = false
                minimizedApps = minimizedApps.filterNot { it.route == request }
                if (request == "flag:update") {
                    panelBeforeUpdate = panel
                    panel = "update"
                } else {
                    featureRoute = request
                    panel = "feature"
                }
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
        if (panel == "rogue" || panel == "coop" || panel == "update") return@LaunchedEffect
        prepareLiquidWindow()
        panelBeforeUpdate = panel
        panel = "update"
        vm.noteUpdatePanelShown()
    }

    // Автопроверка почаще: при возвращении в приложение, если прошло больше 15 минут.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    vm.setUpdateForeground(true)
                    vm.checkUpdateIfStale()
                    // Заодно смотрим, не ответил ли агент: ответ появится обычным уведомлением в приложении.
                    vm.checkAgentMail(silent = true)
                }
                Lifecycle.Event.ON_PAUSE -> vm.setUpdateForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.setUpdateForeground(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val nextRule = remember(rules) { nextOccurrence(rules) }
    val nextId = remember(rules) { nextRuleId(rules) }

    fun currentRoute(): String? = when {
        panel == "feature" -> featureRoute ?: activeRoute
        panel?.startsWith("page:") == true -> panel
        panel != null -> "panel:" + panel
        else -> activeRoute
    }

    fun titleForRoute(route: String?): String {
        when (route) {
            "panel:settings" -> return "настройки"
            "panel:schedule", "panel:schedule_new" -> return "расписание"
            "panel:lifeauto", "page:lifeauto" -> return "автоматизации жизни"
            "panel:diagnostics", "flag:diagnostics" -> return "диагностика"
            "panel:games" -> return "игры"
            "panel:nfc", "tool:nearby" -> return "gsynk рядом"
            "panel:coop" -> return "игра вдвоём"
            "panel:rogue" -> return "gtboi"
            "panel:scenes" -> return "сцены сейчас"
            "panel:update", "flag:update" -> return "обновление"
            "page:news" -> return "новости"
            "page:script" -> return "настройки Script UI"
            "page:antistress" -> return "антистресс Script"
        }
        Catalog.ALL.firstOrNull { it.route == route }?.let { return it.title }
        return route
            ?.substringAfter(':')
            ?.replace('_', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: "gsynk"
    }

    fun performCloseCurrentApp() {
        if (panel == "rogue" && coopRole != null) com.gsynk.app.core.WifiCoop.stop()
        if (panel == "rogue" || panel == "coop") {
            coopRole = null
            coopInvite = null
            coopAutoHost = false
        }
        panel = null
        featureRoute = null
        activeRoute = null
        liquidLaunch = null
        startOpen = true
    }

    fun closeCurrentApp() {
        requestLiquidWindowExit(LiquidWindowExit.CLOSE) { performCloseCurrentApp() }
    }

    fun performMinimizeCurrentApp() {
        val currentPanel = panel ?: return
        val routeNow = currentRoute()
        val saved = MinimizedAppState(
            panel = currentPanel,
            featureRoute = featureRoute,
            route = routeNow,
            title = titleForRoute(routeNow)
        )
        // SCRIPT-мультизадачность: в активных 3D-углах живёт до четырёх окон.
        minimizedApps = (listOf(saved) + minimizedApps.filterNot { it.route == routeNow }).take(4)
        panel = null
        featureRoute = null
        liquidLaunch = null
        startOpen = false
    }

    fun minimizeCurrentApp() {
        requestLiquidWindowExit(LiquidWindowExit.MINIMIZE) { performMinimizeCurrentApp() }
    }

    fun resumeMinimizedApp(index: Int = 0) {
        val saved = minimizedApps.getOrNull(index) ?: return
        prepareLiquidWindow()
        val launchId = liquidLaunchSerial + 1
        liquidLaunchSerial = launchId
        val savedEntry = Catalog.ALL.firstOrNull { it.route == saved.route }
        val rect = liquidMinimizeRect(index)
        liquidLaunch = LiquidWindowOrigin(
            id = launchId,
            route = saved.route.orEmpty(),
            title = saved.title,
            iconRes = savedEntry?.iconRes ?: 0,
            color = savedEntry?.color ?: Color(0xFF8B5CF6),
            left = rect.left,
            top = rect.top,
            width = rect.width,
            height = rect.height
        )
        panel = saved.panel
        featureRoute = saved.featureRoute
        activeRoute = saved.route
        minimizedApps = minimizedApps.filterIndexed { itemIndex, _ -> itemIndex != index }
        startOpen = false
    }

    fun closeMinimizedApp(index: Int) {
        minimizedApps = minimizedApps.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun performDismissUpdatePanel() {
        panel = panelBeforeUpdate?.takeUnless { it == "update" }
        if (panel == null) startOpen = true
        panelBeforeUpdate = null
        vm.dismissUpdate()
    }

    fun dismissUpdatePanel() {
        requestLiquidWindowExit(LiquidWindowExit.CLOSE) { performDismissUpdatePanel() }
    }

    BackHandler(enabled = isPortrait && (panel != null || startOpen)) {
        when {
            panel == "update" -> dismissUpdatePanel()
            panel != null -> closeCurrentApp()
            else -> startOpen = false
        }
    }

    val shellTitle = titleForRoute(currentRoute())

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
        "diag" -> listOf(
            if (hadCrash) "был сбой — отчёт уже готов" else "сбоёв не зафиксировано",
            "журнал, отчёты и отправка"
        )
        "agent" -> listOf(
            if (agentUnseen > 0) "новых ответов: " + agentUnseen else "связь через GitHub",
            "вопросы и ответы агента"
        )
        "lowlevel", "registers" -> listOf("HEX · биты · BE/LE", "IP · A/B/C · флаги", "CRC32 · SHA-256 · дампы")
        "lifeauto" -> listOf("07:00 школа — тихо и приглушённо", "15:00 дом — YouTube ярко и громко", "12 готовых наборов правил")
        "settings" -> listOf("автопилот и доступы", "обновления и внешний вид", "отдельное Metro-приложение")
        "smartdecode" -> listOf("слой за слоем до исходного текста", "одинаковый шифр несколько раз — не проблема")
        else -> buildList {
            add(entry.subtitle)
            if (entry.fresh) add("новое в SCRIPT")
            add(entry.group.hint)
        }
    }

    val underlayStartOpen = when {
        liquidExitMode == LiquidWindowExit.MINIMIZE -> false
        panel == null -> startOpen
        else -> homeUnderlayStart
    }
    val backdropProgress by animateFloatAsState(
        targetValue = if (panel != null && liquidExitMode == null) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.88f),
        label = "liquidBackdrop"
    )
    val rootRevealProgress by animateFloatAsState(
        targetValue = if (rootWindowShown) 1f else 0f,
        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing),
        label = "rootWindowReveal"
    )

    CompositionLocalProvider(
        LocalLiquidWindowLaunch provides liquidLaunch,
        LocalLiquidWindowLaunchConsumer provides { launchId ->
            if (liquidLaunch?.id == launchId) liquidLaunch = null
        },
        LocalLiquidWindowExit provides liquidExitMode
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF160B2E))
    ) {
        // При запуске анимируется не логотип, а само окно «Пуска»: оно раскрывается
        // из приподнятой стеклянной карты в полноэкранное окно. Под открытым
        // приложением фон, наоборот, чуть уплывает назад и затемняется.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val reveal = rootRevealProgress
                    val backdrop = backdropProgress
                    scaleX = (0.88f + 0.12f * reveal) * (1f - 0.070f * backdrop)
                    scaleY = (0.92f + 0.08f * reveal) * (1f - 0.055f * backdrop)
                    alpha = reveal * (1f - 0.30f * backdrop)
                    translationY = (1f - reveal) * 58f + backdrop * 22f
                    shape = RoundedCornerShape(((1f - reveal) * 30f).dp)
                    clip = reveal < 0.999f
                }
        ) {
        AnimatedContent(
            targetState = underlayStartOpen,
            transitionSpec = {
                (fadeIn(tween(340)) + slideInVertically(
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.88f)
                ) { it / 5 } + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.88f)
                )) togetherWith
                    (fadeOut(tween(230)) + slideOutVertically(tween(380)) { it / 7 } + scaleOut(targetScale = 0.965f, animationSpec = tween(380)))
            },
            label = "startSwitch"
        ) { open ->
            if (open) {
                StartMenuScreen(
                    favorites = favorites,
                    minimizedRoutes = minimizedApps.mapNotNull { it.route }.toSet(),
                    liveLinesFor = { entry -> homeTileLines(entry) },
                    onOpen = { entry, origin ->
                        prepareLiquidWindow()
                        val launchId = liquidLaunchSerial + 1
                        liquidLaunchSerial = launchId
                        liquidLaunch = (origin ?: LiquidWindowOrigin(
                            route = entry.route,
                            title = entry.title,
                            iconRes = entry.iconRes,
                            color = entry.color
                        )).copy(
                            id = launchId,
                            route = entry.route,
                            title = entry.title,
                            iconRes = entry.iconRes,
                            color = entry.color
                        )
                        activeRoute = entry.route
                        Catalog.open(context, entry)
                    },
                    onToggleFavorite = { entry ->
                        favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
                        Catalog.saveFavorites(context, favorites)
                    },
                    onCloseHome = { startOpen = false }
                )
            } else {
                EmptyHomeScreen(
                    onOpenStart = { startOpen = true },
                    minimizedWindows = minimizedApps.map { saved ->
                        val entry = Catalog.ALL.firstOrNull { it.route == saved.route }
                        MinimizedWindowUi(
                            title = saved.title,
                            route = saved.route,
                            iconRes = entry?.iconRes ?: 0,
                            color = entry?.color ?: Color(0xFF8B5CF6)
                        )
                    },
                    onResumeMinimized = { index -> resumeMinimizedApp(index) },
                    onCloseMinimized = { index -> closeMinimizedApp(index) }
                )
            }
        }
        }

    // ModalBottomSheet живёт в отдельном окне, поэтому в ландшафте его нельзя
    // оставлять поверх экрана требования портрета.
    if (isPortrait && (creating || editing != null)) {
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
        MetroAppShell(
            title = shellTitle,
            onClose = { closeCurrentApp() },
            onMinimize = { minimizeCurrentApp() }
        ) {
            SettingsScreen(vm = vm, onBack = { closeCurrentApp() })
        }
    }

    if (panel == "schedule") {
        MetroAppShell(
            title = shellTitle,
            onClose = { closeCurrentApp() },
            onMinimize = { minimizeCurrentApp() }
        ) {
            ScheduleScreen(
                vm = vm,
                onBack = { closeCurrentApp() },
                onCreate = { creating = true },
                onEdit = { editing = it }
            )
        }
    }

    if (panel == "feature") {
        val routeNow = featureRoute
        if (routeNow != null) {
            MetroAppShell(
                title = shellTitle,
                onClose = { closeCurrentApp() },
                onMinimize = { minimizeCurrentApp() }
            ) {
                DirectFeatureScreen(
                    route = routeNow,
                    vm = vm,
                    onBack = { closeCurrentApp() },
                    onOpenNfc = {
                        activeRoute = "tool:nearby"
                        panel = "nfc"
                    },
                    onHostGame = {
                        coopInvite = null
                        coopAutoHost = true
                        panel = "coop"
                    },
                    onJoinGame = { invite ->
                        coopInvite = invite
                        coopRole = com.gsynk.app.core.WifiCoop.Role.GUEST
                        rogueInvite = NfcLinkInvite(invite.seed, invite.from.ifBlank { "второй телефон" })
                        panel = "rogue"
                    }
                )
            }
        }
    }

    if (panel == "lifeauto") {
        MetroAppShell(
            title = shellTitle,
            onClose = { closeCurrentApp() },
            onMinimize = { minimizeCurrentApp() }
        ) {
            LifeAutomationsScreen(
                vm = vm,
                onBack = { closeCurrentApp() },
                onOpenSchedule = { panel = "schedule" }
            )
        }
    }

    panel?.takeIf { it.startsWith("page:") }?.let { name ->
        MetroAppShell(
            title = shellTitle,
            onClose = { closeCurrentApp() },
            onMinimize = { minimizeCurrentApp() }
        ) {
            val page = name.removePrefix("page:")
            val handled = GiouiPage(page, vm, onBack = { closeCurrentApp() }) ||
                GiouiExtraPage(page, vm, onBack = { closeCurrentApp() })
            if (!handled) {
                GrapeScreen(title = "плитка", subtitle = "страница не найдена", onBack = { closeCurrentApp() }) {
                    SurfaceCard {
                        Text("Страница «$page» удалена вместе со старыми интерфейсами. Закройте плитку кнопкой × в статус-баре.", color = TextPrimary, fontSize = Gioui.Type.body)
                    }
                }
            }
        }
    }

    if (panel == "diagnostics") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            DiagnosticsScreen(vm = vm, onBack = { closeCurrentApp() })
        }
    }

    if (panel == "scenes") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            ScenesScreen(onBack = { closeCurrentApp() })
        }
    }

    if (panel == "games") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            GamesScreen(
                onBack = { closeCurrentApp() },
                onDungeon = { coop ->
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
    }

    if (panel == "nfc") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            NfcLinkScreen(
                onBack = { closeCurrentApp() },
                onHostGame = {
                    coopInvite = null
                    coopAutoHost = true
                    panel = "coop"
                },
                onJoinGame = { invite ->
                    coopInvite = invite
                    coopRole = com.gsynk.app.core.WifiCoop.Role.GUEST
                    rogueInvite = NfcLinkInvite(invite.seed, invite.from.ifBlank { "второй телефон" })
                    panel = "rogue"
                },
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
    }

    if (panel == "coop") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            CoopScreen(
                onBack = { closeCurrentApp() },
                autoInvite = coopInvite,
                autoHost = coopAutoHost,
                onPlay = { role, seed ->
                    coopRole = role
                    coopAutoHost = false
                    rogueInvite = NfcLinkInvite(seed, profile.model.ifBlank { "второй телефон" })
                    panel = "rogue"
                }
            )
        }
    }

    if (panel == "rogue") {
        MetroAppShell(title = shellTitle, onClose = { closeCurrentApp() }, onMinimize = { minimizeCurrentApp() }) {
            RogueScreen(
                onBack = { closeCurrentApp() },
                invite = rogueInvite,
                onInviteByNfc = {
                    activeRoute = "tool:nearby"
                    featureRoute = "tool:nearby"
                    panel = "feature"
                },
                coop = coopRole,
                onCoopSetup = {
                    coopInvite = null
                    panel = "coop"
                }
            )
        }
    }

        // ————— слои поверх всего —————
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
        // Панель разрешений обязательна и перекрывает приложение, пока всё не выдано.
        PermissionGate()
        // «Показ по касанию»: салют, сердечки, надпись от второго телефона.
        NfcShowHost()
        // Обновление — самый верхний слой: вылезает само и закрывает прочие панели.
        if (panel == "update") {
            MetroAppShell(
                title = "обновление",
                onClose = { dismissUpdatePanel() },
                onMinimize = { minimizeCurrentApp() }
            ) {
                UpdateSheet(vm = vm, onDismiss = { dismissUpdatePanel() })
            }
        }

        // Жёсткие ворота ориентации EXP: приложение теперь только портретное,
        // повернуть в горизонтальный режим нельзя (манифест фиксирует портрет).
        // Экран ниже — страховка для редких широких окон и складных устройств.
        if (!isPortrait) {
            PortraitRequiredScreen()
        }

    }
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
                    if (rule.silentMode == 1) TagChip(text = "тишина вкл.")
                    if (rule.silentMode == 0) TagChip(text = "тишина выкл.")
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
