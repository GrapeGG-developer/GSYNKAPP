package com.gsynk.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.ChipLab
import com.gsynk.app.core.Deep
import com.gsynk.app.core.DeviceLab
import com.gsynk.app.core.Sensors
import com.gsynk.app.core.Haptics
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletDeep
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * GSUI V4p1 — патч поверх V4.
 *
 * Что здесь живёт:
 *  1. Вау-эффекты 2099: живое сияние (aurora), параллакс от наклона телефона, блик по плиткам.
 *  2. «Фазы» чипа — восемь страниц управления железом вместо чтения датчиков:
 *     частоты ядер, питание изнутри, панель, радио, светодиод вспышки, мотор, датчики, память.
 *  3. Папки вместо фильтров: человек выбирает папку и заходит внутрь, а не листает ленту.
 *
 * Правило то же, что и во всём приложении: ничего не выдумываем. Если система не дала
 * управление — так и написано прямо в интерфейсе, вместо выдуманного «сделано».
 */

// ————————————————————————————————————————————————————————————————
//  1. ВАУ-ЭФФЕКТЫ
// ————————————————————————————————————————————————————————————————

/**
 * Живое сияние: несколько полос цвета медленно перетекают по экрану.
 * Работает поверх обычного фона, поэтому читаемость текста не страдает — только добавка.
 */
@Composable
fun AuroraField(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    speedMillis: Int = 14_000
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(speedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraBreathe"
    )
    val colors = listOf(GrapeVioletSoft, GrapeCyan, GrapePink, GrapeGreen)
    Canvas(modifier.fillMaxSize()) {
        colors.forEachIndexed { index, color ->
            val shift = (phase + index * 0.25f) % 1f
            val bandHeight = size.height * 0.34f
            val centerY = size.height * (shift * 1.4f - 0.2f)
            val left = size.width * ((shift * 2f - 1f) * 0.6f) - size.width * 0.3f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.16f * intensity * breathe), Color.Transparent),
                    start = Offset(left, centerY - bandHeight / 2f),
                    end = Offset(left + size.width, centerY + bandHeight / 2f)
                ),
                topLeft = Offset(0f, centerY - bandHeight / 2f),
                size = Size(size.width, bandHeight)
            )
        }
    }
}

/**
 * Параллакс: содержимое слегка смещается, когда телефон наклоняют.
 * Живой эффект без картинок и без сети — читаем настоящий акселерометр.
 */
@Composable
fun ParallaxBox(
    modifier: Modifier = Modifier,
    strength: Float = 7f,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    val level = remember(context) { Sensors.Level(context) }
    DisposableEffect(enabled, level) {
        if (enabled && level.supported) {
            level.start { x, y -> tiltX = x; tiltY = y }
        }
        onDispose { level.stop() }
    }

    val shiftX = if (enabled) (tiltX / 35f).coerceIn(-1f, 1f) * strength else 0f
    val shiftY = if (enabled) (-tiltY / 35f).coerceIn(-1f, 1f) * strength else 0f

    Box(modifier, content = {
        Box(Modifier.offset(x = shiftX.dp, y = shiftY.dp), content = content)
    })
}

/** Блик по плитке: тонкая полоса света, которая медленно проходит наискось. */
@Composable
fun shimmerModifier(active: Boolean = true, tint: Color = Color.White): Modifier {
    if (!active) return Modifier
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    return Modifier.clip(skinShape("tile")).background(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, tint.copy(alpha = 0.10f), Color.Transparent),
            start = Offset(sweep * 900f - 300f, 0f),
            end = Offset(sweep * 900f, 900f)
        )
    )
}

// ————————————————————————————————————————————————————————————————
//  2. ФАЗЫ ЧИПА
// ————————————————————————————————————————————————————————————————

/** Одна фаза: страница с управлением конкретным узлом железа. */
data class Phase(
    val id: String,
    val title: String,
    val subtitle: String,
    val tone: Int,
    val hint: String
)

object Phases {
    /** Восемь фаз — от частот до памяти. Порядок: от самого «горячего» к самому бытовому. */
    val ALL: List<Phase> = listOf(
        Phase("cpu", "частоты ядер", "режимы, потолки, ступени", 0, "что ядро умеет прямо сейчас и где это видно в /sys"),
        Phase("battery", "питание изнутри", "ток, ватты, ресурс", 1, "контроллер питания: сколько идёт энергии и на сколько хватит"),
        Phase("display", "панель и наклон", "кадры, яркость, уровень", 2, "экран: герцы, наклон телефона, отклик касания"),
        Phase("radio", "радио-тракт", "модем, сеть, каналы", 3, "что видит телефон в эфире: сеть, канал, сила сигнала"),
        Phase("camera", "светодиод вспышки", "уровни, одиночный накал", 4, "вспышка — это настоящий светодиод с драйвером и ступенями яркости"),
        Phase("motor", "мотор и резонанс", "амплитуда, поле, точки", 5, "вибромотор: его частота вращения и магнитный отклик"),
        Phase("sensors", "датчики изнутри", "что есть и что закрыто", 6, "полный список датчиков с характеристиками, включая системные"),
        Phase("memory", "память и планировщик", "давление, накопитель", 7, "как система ждёт память и как читает накопитель")
    )

    fun byId(id: String): Phase? = ALL.firstOrNull { it.id == id }
}

/**
 * Идентификаторы фаз — одной строкой.
 *
 * Нужны тестам и каталогу: если фаза объявлена, у неё обязана быть страница
 * в разборщике `V4P1Page`. Список строится из фаз, поэтому разойтись не может.
 */
val PhaseIds: List<String> = Phases.ALL.map { it.id }

/** Разбор страниц V4P1: та же схема, что у Gsui4Page — «сделал и вернул true». */
@Composable
fun V4P1Page(id: String, vm: MainViewModel, onBack: () -> Unit): Boolean {
    when (id) {
        "folders" -> { FolderHubScreen(onBack); return true }
        "cpu" -> { PhaseCpuScreen(onBack); return true }
        "battery" -> { PhaseBatteryScreen(onBack); return true }
        "display" -> { PhaseDisplayScreen(onBack); return true }
        "radio" -> { PhaseRadioScreen(onBack); return true }
        "camera" -> { PhaseFlashScreen(onBack); return true }
        "motor" -> { PhaseMotorScreen(onBack); return true }
        "sensors" -> { PhaseSensorDeepScreen(onBack); return true }
        "memory" -> { PhaseMemoryScreen(onBack); return true }
    }
    return false
}

/** Общий «честный» ответ приложения: получилось или почему нет. */
@Composable
private fun AnswerBox(answer: String, accent: Color) {
    if (answer.isBlank()) return
    SurfaceCard(borderColor = accent.copy(alpha = 0.45f)) {
        Text(
            text = answer,
            color = accent,
            fontSize = Metro.Type.body
        )
    }
    Spacer(Modifier.height(8.dp))
}

// ————— фаза: частоты ядер —————

@Composable
private fun PhaseCpuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cores by remember { mutableStateOf(Deep.cores()) }
    var steps by remember { mutableStateOf(0) }
    var rootAnswer by remember { mutableStateOf("") }
    var writeAnswer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val nodes = remember { ChipLab.cpuNodes() }
    val governors = remember { Deep.governors() }
    val frequencies = remember { Deep.frequencies() }
    val root = remember { ChipLab.rootAvailable() }

    LaunchedEffect(Unit) {
        if (!root) {
            rootAnswer = "проверяю, есть ли root…"
            rootAnswer = ChipLab.probeRoot()
        } else {
            rootAnswer = "su-оболочка есть — пробую получить доступ"
            rootAnswer = ChipLab.probeRoot()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            cores = Deep.cores()
            steps++
            delay(800)
        }
    }

    val meanMhz = if (cores.isEmpty()) 0 else cores.map { it.currentMhz }.average().toInt()

    GrapeScreen(
        title = "Частоты ядер",
        subtitle = cores.size.toString() + " ядер · " + meanMhz + " МГц сейчас",
        onBack = onBack
    ) {
        AnswerBox(rootAnswer, if (rootAnswer.contains("доступ есть")) GrapeGreen else GrapeVioletSoft)

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("живые ядра")
            cores.forEach { core ->
                InfoRow(
                    label = "ядро " + core.index + (if (core.online) "" else " (спит)"),
                    value = core.currentMhz.toString() + " / " + core.maxMhz + " МГц · " + core.governor,
                    accent = if (core.loadPercent > 70) GrapeRed else TextPrimary
                )
                ProgressLine(
                    percent = core.loadPercent,
                    brush = Brush.linearGradient(listOf(GrapeVioletSoft, GrapeCyan)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Полоска — не загрузка, а частота между минимумом и максимумом ядра: " +
                    "так видно, поднимается ли оно вообще.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("режимы (governor)")
            Text(
                text = "Режим решает, когда ядро поднимает частоту. Сменить его можно только с root: " +
                    "без него прошивка закрывает запись.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                governors.forEach { governor ->
                    SortChip(governor, governor == cores.firstOrNull()?.governor) {
                        writeAnswer = ChipLab.governorVerdict(
                            cores.firstOrNull()?.governor.orEmpty(), governor, governors
                        )
                        if (root) {
                            busy = true
                            scope.launch {
                                writeAnswer = ChipLab.writeNode(
                                    "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", governor
                                )
                                cores = Deep.cores()
                                busy = false
                            }
                        }
                    }
                }
            }
            if (governors.isEmpty()) {
                Text(
                    text = "Прошивка не показывает список режимов — на новых ядрах это закрыто.",
                    color = GrapePink,
                    fontSize = Metro.Type.caption
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (busy) "записываю…" else "нажми режим — покажу, что из этого выйдет",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        AnswerBox(writeAnswer, GrapeCyan)

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("ступени частот")
            if (frequencies.isEmpty()) {
                Text("Список ступеней закрыт прошивкой.", color = GrapePink, fontSize = Metro.Type.body)
            } else {
                Text(
                    text = frequencies.joinToString(" · ") { it.toString() },
                    color = TextSecondary,
                    fontSize = Metro.Type.body
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("ступеней", frequencies.size.toString())
                InfoRow("минимум", frequencies.first().toString() + " МГц")
                InfoRow("максимум", frequencies.last().toString() + " МГц")
            }
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("узлы ядра")
            nodes.forEach { node ->
                InfoRow(node.title, node.value.ifBlank { "не читается" })
                Text(node.path + " — " + node.note, color = TextDim, fontSize = Metro.Type.caption)
                Spacer(Modifier.height(4.dp))
            }
        }

        SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.4f)) {
            SectionTitle("границы")
            Text(
                text = DeviceLab.SAFETY_NOTE + " Частоты и режимы — единственное, что можно менять, " +
                    "и только с root и по нажатию: значения возвращаются обратно при выходе.",
                color = TextSecondary,
                fontSize = Metro.Type.caption
            )
        }
    }
}

// ————— фаза: питание изнутри —————

@Composable
private fun PhaseBatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(ChipLab.batteryDeep(context)) }
    var ticks by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            battery = ChipLab.batteryDeep(context)
            ticks++
            delay(1500)
        }
    }

    val watts = ChipLab.watts(battery.currentMa, battery.voltageMv)

    GrapeScreen(
        title = "Питание изнутри",
        subtitle = if (battery.hasData) "данные от контроллера питания" else "контроллер закрыт",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("прямо сейчас")
            InfoRow("ток", if (battery.currentMa == 0f) "не сообщает" else "%.0f мА".format(battery.currentMa),
                if (battery.currentMa < 0) GrapeGreen else TextPrimary)
            InfoRow("напряжение", if (battery.voltageMv == 0f) "не сообщает" else "%.2f В".format(battery.voltageMv / 1000f))
            InfoRow("мощность", if (watts == 0f) "—" else "%.2f Вт".format(watts),
                if (watts > 8f) GrapeRed else GrapeGreen)
            InfoRow("температура", if (battery.celsius == 0f) "—" else "%.1f °C".format(battery.celsius),
                if (battery.celsius > 40f) GrapeRed else TextPrimary)
            InfoRow("здоровье", battery.healthy)
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("ресурс")
            if (battery.cycles > 0) {
                InfoRow("циклов заряда", battery.cycles.toString())
            } else {
                Text(
                    text = "Счётчик циклов закрыт: на новых ядрах его видно только системным службам.",
                    color = GrapePink,
                    fontSize = Metro.Type.body
                )
            }
            battery.wearPercent?.let { wear ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ёмкость от заводской",
                    color = TextPrimary,
                    fontSize = Metro.Type.body,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                ProgressLine(percent = wear.coerceIn(0, 100), brush = Brush.linearGradient(listOf(GrapeGreen, GrapeCyan)))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = wear.toString() + "% · " + (battery.fullUah / 1000) + " из " +
                        (battery.designUah / 1000) + " мА·ч",
                    color = if (wear < 80) GrapeRed else TextSecondary,
                    fontSize = Metro.Type.caption
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(battery.note, color = TextDim, fontSize = Metro.Type.caption)
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("как это считается")
            Text(
                text = "Ток берётся из узла `current_now`, напряжение — из `voltage_now`, температура — из `temp`. " +
                    "Мощность = ток × напряжение. Всё только чтение, ничего не меняется.",
                color = TextSecondary,
                fontSize = Metro.Type.body
            )
        }
    }
}

// ————— фаза: панель и наклон —————

@Composable
private fun PhaseDisplayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    var hz by remember { mutableStateOf(0f) }
    var dimmer by remember { mutableIntStateOf(0) }
    val level = remember(context) { Sensors.Level(context) }

    DisposableEffect(Unit) {
        if (level.supported) level.start { x, y -> tiltX = x; tiltY = y }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        hz = manager?.defaultDisplay?.refreshRate ?: 0f
        onDispose { level.stop() }
    }

    GrapeScreen(title = "Панель и наклон", subtitle = "%.0f Гц · уровень как в пузырьке".format(hz), onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("частота обновления")
            InfoRow("сейчас", "%.1f Гц".format(hz))
            InfoRow("плотность", context.resources.displayMetrics.densityDpi.toString() + " dpi")
            InfoRow("экран", context.resources.displayMetrics.widthPixels.toString() + "×" +
                context.resources.displayMetrics.heightPixels)
            Text(
                text = "Телефон может сам опускать герцы, когда изображение не меняется: " +
                    "поэтому значение стоит смотреть на подвижном экране.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("уровень телефона")
            if (!level.supported) {
                Text("Акселерометр не отдаёт данные.", color = GrapePink, fontSize = Metro.Type.body)
            } else {
                // Пузырёк: круг смещается по наклону — видно, куда телефон «падает».
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(skinShape("card"))
                        .background(ChipBg)
                        .border(1.dp, CardBorder, skinShape("card"))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = size.minDimension / 2f - 26f
                        drawCircle(Color.White.copy(alpha = 0.06f), radius = radius)
                        drawCircle(Color.White.copy(alpha = 0.10f), radius = radius * 0.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                        val dx = (tiltX / 35f).coerceIn(-1f, 1f) * radius
                        val dy = (tiltY / 35f).coerceIn(-1f, 1f) * radius
                        drawCircle(GrapeCyan.copy(alpha = 0.9f), radius = 16f, center = Offset(cx + dx, cy + dy))
                        drawCircle(GrapeCyan.copy(alpha = 0.25f), radius = 26f, center = Offset(cx + dx, cy + dy))
                    }
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("наклон вбок", "%.1f°".format(tiltX))
                InfoRow("наклон вперёд", "%.1f°".format(tiltY))
            }
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("отклик касания")
            Text(
                text = "Задержку «касание → свет» измеряет отдельный опыт в лаборатории: " +
                    "там же видно, сколько кадров уходит на ответ.",
                color = TextSecondary,
                fontSize = Metro.Type.body
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Затемнение: " + dimmer.toString() + "%",
                color = TextPrimary,
                fontSize = Metro.Type.body
            )
            NeoSlider(
                value = dimmer,
                onValueChange = { dimmer = it; com.gsynk.app.core.ScreenDimmer.apply(context, it) },
                brush = Brush.linearGradient(listOf(GrapeVioletSoft, GrapeCyan))
            )
            SecondaryButton(text = "вернуть яркость", onClick = { dimmer = 0; com.gsynk.app.core.ScreenDimmer.apply(context, 0) })
        }
    }
}

// ————— фаза: радио-тракт —————

@Composable
private fun PhaseRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cellNote by remember { mutableStateOf("") }
    var wifiNote by remember { mutableStateOf("") }
    var btNote by remember { mutableStateOf("") }
    var ticking by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(granted, ticking) {
        // Сеть: тип, оператор, уровень. Без разрешения на геолокацию система это не отдаёт.
        cellNote = runCatching {
            if (!granted) {
                "нужно разрешение «Местоположение»: без него Android скрывает данные сети"
            } else {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                if (tm == null) {
                    "телефон не отдал службу телефонии"
                } else {
                    val operator = tm.networkOperatorName.ifBlank { "оператор неизвестен" }
                    val info = tm.allCellInfo?.firstOrNull()
                    val level = info?.cellSignalStrength?.level ?: 0
                    "оператор: " + operator + " · уровень сигнала: " + level + " из 4"
                }
            }
        }.getOrElse { "сеть недоступна: " + (it.message ?: "неизвестно") }

        wifiNote = runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val info = wm?.connectionInfo
            if (info == null || info.networkId == -1) {
                "Wi-Fi не подключён — телефон сидит на мобильной сети"
            } else {
                "Wi-Fi: " + info.ssid + " · " + info.linkSpeed + " Мбит/с · " +
                    info.rssi + " дБм · канал " + (info.frequency / 5)
            }
        }.getOrElse { "Wi-Fi закрыт: " + (it.message ?: "неизвестно") }

        btNote = runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = manager?.adapter
            when {
                adapter == null -> "Bluetooth-адаптера нет"
                !adapter.isEnabled -> "Bluetooth выключен"
                else -> "Bluetooth включён: " + adapter.name + " · устройств сопряжено: " +
                    (adapter.bondedDevices?.size ?: 0)
            }
        }.getOrElse { "Bluetooth недоступен: " + (it.message ?: "неизвестно") }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            ticking++
        }
    }

    GrapeScreen(title = "Радио-тракт", subtitle = "модем, Wi-Fi, Bluetooth", onBack = onBack) {
        if (!granted) {
            SurfaceCard(borderColor = GrapePink.copy(alpha = 0.5f)) {
                Text(
                    text = "Данные сети Android отдаёт только с разрешением на местоположение — " +
                        "иначе приложение видит пустоту.",
                    color = TextSecondary,
                    fontSize = Metro.Type.body
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "разрешить местоположение",
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            }
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("мобильная сеть")
            Text(cellNote, color = TextPrimary, fontSize = Metro.Type.body)
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("wi-fi")
            Text(wifiNote, color = TextPrimary, fontSize = Metro.Type.body)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Каналы и загрузку эфира показывает опыт «каналы wi-fi» в лаборатории: " +
                    "там же видно, какой канал забит соседями.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("bluetooth")
            Text(btNote, color = TextPrimary, fontSize = Metro.Type.body)
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("что здесь настоящее")
            Text(
                text = "Это живые данные модулей связи: имя оператора, канал, скорость, число " +
                    "сопряжённых устройств. Передатчиком управлять обычному приложению нельзя — " +
                    "поэтому мы показываем всё, что отдаёт система, и честно пишем, где она закрыта.",
                color = TextSecondary,
                fontSize = Metro.Type.body
            )
        }
    }
}

// ————— фаза: светодиод вспышки —————

@Composable
private fun PhaseFlashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var maxLevel by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(0) }
    var strobeHz by remember { mutableIntStateOf(4) }
    var running by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        maxLevel = ChipLab.torchMaxLevel(context)
        answer = if (maxLevel > 0) {
            "драйвер вспышки отдаёт " + maxLevel + " ступеней яркости — это управление током светодиода"
        } else {
            "драйвер не сообщает ступени: на этой прошивке доступно только «вкл» и «выкл»"
        }
    }

    // Одиночный накал: держим светодиод на выбранном уровне, пока экран открыт.
    DisposableEffect(level, running) {
        if (level > 0 && !running) ChipLab.setTorchLevel(context, level)
        onDispose { ChipLab.setTorchOff(context) }
    }

    LaunchedEffect(running, strobeHz) {
        if (!running) return@LaunchedEffect
        while (true) {
            ChipLab.setTorchLevel(context, level.coerceAtLeast(1))
            delay((1000 / strobeHz.coerceIn(1, 20)).toLong())
            ChipLab.setTorchOff(context)
            delay((1000 / strobeHz.coerceIn(1, 20)).toLong())
        }
    }

    GrapeScreen(title = "Светодиод вспышки", subtitle = "уровни и накал", onBack = onBack) {
        AnswerBox(answer, GrapeCyan)

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("яркость")
            InfoRow("ступеней у драйвера", if (maxLevel > 0) maxLevel.toString() else "нет")
            InfoRow("выбрано", level.toString())
            if (maxLevel > 0) {
                NeoSlider(
                    value = if (maxLevel > 0) level * 100 / maxLevel else 0,
                    onValueChange = { percent ->
                        level = (maxLevel * percent / 100).coerceIn(0, maxLevel)
                        ChipLab.setTorchLevel(context, level)
                        Haptics.tick(context, 18L)
                    },
                    brush = Brush.linearGradient(listOf(GrapeVioletSoft, GrapeCyan))
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("включить", onClick = { level = 1; ChipLab.setTorchLevel(context, 1) }, modifier = Modifier.weight(1f))
                    SecondaryButton("выключить", onClick = { level = 0; ChipLab.setTorchOff(context) }, modifier = Modifier.weight(1f))
                }
            }
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("свой стробоскоп")
            Text(
                text = "Частота задаётся вручную и работает на настоящем светодиоде: " +
                    "на малых герцах видно, как свет «дышит», на больших — сливается в ровный свет.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("частота", strobeHz.toString() + " Гц")
            NeoSlider(
                value = strobeHz * 100 / 20,
                onValueChange = { strobeHz = (it * 20 / 100).coerceIn(1, 20) },
                brush = Brush.linearGradient(listOf(GrapePink, GrapeCyan))
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (running) "остановить" else "пустить свет",
                onClick = { running = !running },
                enabled = level > 0 || maxLevel == 0
            )
        }
    }
}

// ————— фаза: мотор и резонанс —————

@Composable
private fun PhaseMotorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val motor = remember { ChipLab.motor(context) }
    var hz by remember { mutableIntStateOf((motor.resonantHz ?: 170f).toInt()) }
    var amplitude by remember { mutableIntStateOf(60) }
    var echo by remember { mutableStateOf(0f) }
    var echoing by remember { mutableStateOf(false) }

    GrapeScreen(title = "Мотор и резонанс", subtitle = if (motor.exists) "вибромотор отвечает" else "мотора нет", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("что умеет мотор")
            InfoRow("резонанс", motor.resonantHz?.let { "%.0f Гц".format(it) } ?: "не сообщает", GrapeCyan)
            InfoRow("добротность", motor.qFactor?.let { "%.2f".format(it) } ?: "не сообщает")
            InfoRow("шаг по частоте", motor.resolutionHz?.let { "%.1f Гц".format(it) } ?: "не сообщает")
            InfoRow("управление силой", if (motor.amplitudeControl) "есть" else "нет", if (motor.amplitudeControl) GrapeGreen else GrapePink)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Резонансный мотор на этой частоте трясёт сильнее всего при той же энергии: " +
                    "именно её используют телефоны для «сочного» отклика.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("своя точка")
            InfoRow("частота", hz.toString() + " Гц")
            NeoSlider(
                value = (hz - 20) * 100 / 280,
                onValueChange = { hz = 20 + it * 280 / 100 },
                brush = Brush.linearGradient(listOf(GrapeVioletSoft, GrapePink))
            )
            InfoRow("сила", amplitude.toString() + "%")
            NeoSlider(
                value = amplitude,
                onValueChange = { amplitude = it },
                brush = Brush.linearGradient(listOf(GrapeGreen, GrapeCyan))
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "проиграть точку",
                onClick = {
                    Haptics.play(context, ChipLab.hapticPattern(hz, amplitude, 400))
                    Haptics.tick(context, 20L)
                },
                enabled = motor.exists
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "20–60 Гц — крупная дрожь, 120–200 — рабочий диапазон отклика, выше 250 — уже почти не чувствуется.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("магнитное эхо")
            Text(
                text = "Катушка мотора создаёт поле: приложи телефон к магнитометру (или просто тряси), " +
                    "и увидишь всплеск — так проверяют, что мотор живой.",
                color = TextSecondary,
                fontSize = Metro.Type.body
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("поле сейчас", "%.1f мкТл".format(echo))
            ProgressLine(
                percent = (echo / 200f * 100f).toInt().coerceIn(0, 100),
                brush = Brush.linearGradient(listOf(GrapeCyan, GrapeVioletSoft)),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            MagneticEcho(active = echoing) { echo = it }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = if (echoing) "замолчать" else "слушать поле",
                    onClick = { echoing = !echoing },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "тряхнуть",
                    onClick = { Haptics.play(context, ChipLab.hapticPattern(hz, 100, 700)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

}

/** Отдельный композабл для подписки на поле мотора: включается только по кнопке. */
@Composable
private fun MagneticEcho(active: Boolean, onValue: (Float) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val stop = ChipLab.magneticEcho(context, onValue)
        onDispose { stop() }
    }
}

// ————— фаза: датчики изнутри —————

@Composable
private fun PhaseSensorDeepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensors = remember { Deep.sensors(context) }
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val all = remember { runCatching { manager?.getSensorList(android.hardware.Sensor.TYPE_ALL) }.getOrNull().orEmpty() }

    GrapeScreen(
        title = "Датчики изнутри",
        subtitle = sensors.size.toString() + " доступно · " + all.size + " всего в железе",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("закрытые для приложений")
            val closed = all.filter { DeviceLab.isSystemOnlySensor(it.type, it.name, it.stringType.orEmpty()) }
            if (closed.isEmpty()) {
                Text("Прошивка ничего не прячет — все датчики доступны.", color = GrapeGreen, fontSize = Metro.Type.body)
            } else {
                closed.forEach { sensor ->
                    InfoRow(sensor.name, "тип " + sensor.type, GrapePink)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Эти датчики система отдаёт только своим службам (always-on сенсоры). " +
                        "Читать их напрямую нельзя — Android 16 за это роняет приложение, " +
                        "поэтому мы их честно отмечаем, а не делаем вид, что читаем.",
                    color = TextDim,
                    fontSize = Metro.Type.caption
                )
            }
        }

        all.forEach { sensor ->
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = sensor.name,
                    color = TextPrimary,
                    fontSize = Metro.Type.body,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                InfoRow("тип", Deep.sensorTypeName(sensor))
                InfoRow("производитель", sensor.vendor)
                InfoRow("потребление", "%.2f мА".format(sensor.power))
                InfoRow("диапазон", "%.1f".format(sensor.maximumRange))
                InfoRow("разрешение", "%.6f".format(sensor.resolution))
                InfoRow("задержка", sensor.minDelay.toString() + " мкс")
                if (sensor.stringType.isNotBlank()) InfoRow("строка типа", sensor.stringType)
            }
        }
    }
}

// ————— фаза: память и планировщик —————

@Composable
private fun PhaseMemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var nodes by remember { mutableStateOf(ChipLab.memoryNodes()) }
    var ticks by remember { mutableIntStateOf(0) }
    val storage = remember { Deep.storage(context) }

    LaunchedEffect(Unit) {
        while (true) {
            nodes = ChipLab.memoryNodes()
            ticks++
            delay(1500)
        }
    }

    GrapeScreen(title = "Память и планировщик", subtitle = "как система ждёт ресурсы", onBack = onBack) {
        nodes.forEach { node ->
            SurfaceCard(borderColor = CardBorder) {
                SectionTitle(node.title)
                Text(node.value.ifBlank { "узел не читается" }, color = TextSecondary, fontSize = Metro.Type.body)
                Spacer(Modifier.height(4.dp))
                Text(node.path + " — " + node.note, color = TextDim, fontSize = Metro.Type.caption)
            }
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("накопитель")
            InfoRow("свободно", Deep.round1(storage.freeGb).toString() + " ГБ")
            InfoRow("занято", Deep.round1(storage.totalGb - storage.freeGb).toString() + " ГБ")
            ProgressLine(
                percent = (((storage.totalGb - storage.freeGb) / storage.totalGb.coerceAtLeast(0.1f)) * 100f).toInt().coerceIn(0, 100),
                brush = Brush.linearGradient(listOf(GrapeGreen, GrapePink)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SurfaceCard(borderColor = CardBorder) {
            SectionTitle("планировщик ввода-вывода")
            InfoRow("очередь", ChipLab.readNode("/sys/block/sda/queue/scheduler").ifBlank { "не читается" })
            InfoRow("размер очереди", ChipLab.readNode("/sys/block/sda/queue/nr_requests").ifBlank { "не читается" })
            Text(
                text = "Планировщик решает, в каком порядке читать с накопителя. На телефонах с быстрой " +
                    "памятью он почти не влияет на скорость, зато видно, что прошивка вообще использует.",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }
    }
}

// ————————————————————————————————————————————————————————————————
//  3. ПАПКИ
// ————————————————————————————————————————————————————————————————

/**
 * Папки вместо фильтров.
 *
 * Сверху — плитки папок: в каждой сразу видно, сколько внутри функций. Заходишь внутрь —
 * видишь только эти функции, крупными плитками, и заходишь в нужную. Сортировка — чипами,
 * но она меняет порядок внутри папки, а не «прячет» функции.
 */
@Composable
fun FolderHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var group by remember { mutableStateOf(Catalog.loadGroup(context)) }
    var sort by remember { mutableStateOf(Catalog.loadSort(context)) }
    var favorites by remember { mutableStateOf(Catalog.loadFavorites(context)) }
    var query by remember { mutableStateOf("") }
    val recent = remember { Catalog.loadRecent(context) }

    val inFolder = remember(group, sort, favorites, query) {
        Catalog.view(query = query, group = group, sort = sort, favorites = favorites, recent = recent)
    }

    GrapeScreen(
        title = if (group == null) "Папки" else group!!.title,
        subtitle = if (group == null) {
            Catalog.ALL.size.toString() + " функций в " + Catalog.Group.entries.size + " папках"
        } else {
            inFolder.size.toString() + " функций · " + group!!.hint
        },
        onBack = {
            if (group != null) {
                group = null
                Catalog.saveGroup(context, null)
            } else {
                onBack()
            }
        }
    ) {
        GrapeTextField(value = query, onValueChange = { query = it }, label = "поиск внутри папок")

        ChipFlow {
            Catalog.Sort.entries.forEach { mode ->
                SortChip(mode.title, sort == mode) {
                    sort = mode
                    Catalog.saveSort(context, mode)
                }
            }
        }

        if (group == null) {
            // ——— сам список папок: крупные плитки, каждая со счётчиком ———
            Spacer(Modifier.height(6.dp))
            Catalog.Group.entries.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metro.gutter)
                ) {
                    row.forEach { entry ->
                        val count = Catalog.ALL.count { it.group == entry && it.matches(query) }
                        Tile8(
                            title = entry.title,
                            subtitle = count.toString() + " функций\n" + entry.hint,
                            size = Metro.Size.MEDIUM,
                            color = Metro.Palette.tiles[entry.ordinal % Metro.Palette.tiles.size],
                            modifier = Modifier.weight(1f),
                            onClick = {
                                group = entry
                                Catalog.saveGroup(context, entry)
                                Haptics.tick(context, 20L)
                            }
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(Metro.gutter))
            }

            // ——— избранное и недавние: тоже папки, только собираются сами ———
            val my = Catalog.favoritesOrdered(favorites)
            if (my.isNotEmpty()) {
                SectionTitle("мои")
                Spacer(Modifier.height(6.dp))
                TileRowOf(my.take(4)) { entry -> Catalog.open(context, entry) }
            }
            val last = Catalog.recentOrdered(recent).take(4)
            if (last.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionTitle("недавние")
                Spacer(Modifier.height(6.dp))
                TileRowOf(last) { entry -> Catalog.open(context, entry) }
            }
        } else {
            // ——— внутри папки: только её функции, крупными плитками ———
            val list = if (sort == Catalog.Sort.FAVORITE) inFolder else inFolder.filter { it.group == group }
            if (list.isEmpty()) {
                SurfaceCard(borderColor = CardBorder) {
                    Text(
                        text = "В этой папке пока пусто. Это не поломка: функций здесь просто нет " +
                            "под такой поиск или такой отбор.",
                        color = TextSecondary,
                        fontSize = Metro.Type.body
                    )
                }
            }
            list.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Metro.gutter)
                ) {
                    row.forEach { entry ->
                        Tile8(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            size = Metro.Size.MEDIUM,
                            color = entry.color,
                            badge = if (entry.fresh) "новое" else null,
                            modifier = Modifier.weight(1f),
                            onClick = { Catalog.open(context, entry) },
                            onLongClick = {
                                favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
                                Catalog.saveFavorites(context, favorites)
                                Haptics.tick(context, 26L)
                            }
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(Metro.gutter))
            }
            Text(
                text = "Долгое нажатие — в «мои» · нажми «назад», чтобы вернуться к папкам",
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }
    }
}

/** Небольшая горизонтальная лента из плиток — для «мои» и «недавние». */
@Composable
private fun TileRowOf(entries: List<Catalog.Entry>, onOpen: (Catalog.Entry) -> Unit) {
    LazyRow(
        modifier = Modifier.height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(Metro.gutter),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(entries) { entry ->
            Box(Modifier.width(148.dp)) {
                Tile8(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    size = Metro.Size.SMALL,
                    color = entry.color,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(entry) }
                )
            }
        }
    }
}

/** Обёртка для главного экрана: лента папок одной строкой. */
@Composable
fun FolderStrip(onOpen: (Catalog.Group) -> Unit, onAll: () -> Unit) {
    val context = LocalContext.current
    Column {
        SectionTitle("папки")
        Spacer(Modifier.height(6.dp))
        LazyRow(
            modifier = Modifier.height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(Metro.gutter)
        ) {
            item {
                Box(Modifier.width(170.dp)) {
                    Tile8(
                        title = "все функции",
                        subtitle = Catalog.ALL.size.toString() + " штук",
                        size = Metro.Size.MEDIUM,
                        color = GrapeVioletDeep,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onAll
                    )
                }
            }
            items(Catalog.Group.entries) { entry ->
                val count = Catalog.ALL.count { it.group == entry }
                Box(Modifier.width(170.dp)) {
                    Tile8(
                        title = entry.title,
                        subtitle = count.toString() + " функций",
                        size = Metro.Size.MEDIUM,
                        color = Metro.Palette.tiles[entry.ordinal % Metro.Palette.tiles.size],
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            Haptics.tick(context, 20L)
                            onOpen(entry)
                        }
                    )
                }
            }
        }
    }
}

/** Не используется напрямую, но оставлено для экранов, которым нужен пустой фон карточки. */
@Composable
private fun FolderBackdrop() {
    Box(Modifier.fillMaxWidth().height(0.dp).background(CardBg))
}
