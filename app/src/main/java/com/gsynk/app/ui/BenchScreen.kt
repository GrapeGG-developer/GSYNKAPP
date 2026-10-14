package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.Bench
import com.gsynk.app.core.Nearby
import com.gsynk.app.core.TurboState
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
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Стенд нагрузки: свой бенчмарк.
 *
 * Прогон идёт по шагам, на экране видно каждый шаг и текущие цифры, а в конце —
 * общий балл, класс телефона, сравнение с прошлым прогоном и кнопка «отправить другу».
 */
@Composable
fun BenchScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf<Bench.Stage?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var partial by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Bench.Result?>(null) }
    var history by remember { mutableStateOf(Bench.load(context)) }
    var note by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var gpuRunning by remember { mutableStateOf(false) }

    fun run() {
        if (stage != null) return
        startedAt = System.currentTimeMillis()
        progress = 0
        result = null
        note = "Идёт прогон — не переключайте приложение, телефон считает на полную"
        partial = null
    }

    // Прогон: каждый шаг считается в фоне, интерфейс не замирает.
    LaunchedEffect(stage, startedAt) {
        if (stage != null || startedAt == 0L) return@LaunchedEffect
        val stages = Bench.Stage.entries
        var cpuInt = 0.0
        var cpuFloat = 0.0
        var cpuMatrix = 0.0
        var memory = 0.0
        var storage = 0.0
        var multi = 0.0
        var gpu = 0.0

        stages.forEachIndexed { index, current ->
            stage = current
            partial = current.hint
            withContext(Dispatchers.Default) {
                when (current) {
                    Bench.Stage.CPU_INT -> {
                        val ms = measureTimeMillis { Bench.intOps(3_000_000) }.coerceAtLeast(1)
                        cpuInt = 3_000_000.0 / (ms / 1000.0) / 1_000_000.0
                    }

                    Bench.Stage.CPU_FLOAT -> {
                        val ms = measureTimeMillis { Bench.floatOps(1_500_000) }.coerceAtLeast(1)
                        cpuFloat = 1_500_000.0 / (ms / 1000.0) / 1_000_000.0
                    }

                    Bench.Stage.CPU_MATRIX -> {
                        val ms = measureTimeMillis { Bench.matrixMultiply(128) }.coerceAtLeast(1)
                        cpuMatrix = 128.0 * 128.0 * 128.0 / (ms / 1000.0) / 1_000_000.0
                    }

                    Bench.Stage.MEMORY -> {
                        val bytes = 12 * 1024 * 1024
                        val ms = measureTimeMillis {
                            val array = IntArray(bytes / 4)
                            for (i in array.indices) array[i] = i
                            var sum = 0
                            for (value in array) sum += value
                            if (sum == Int.MIN_VALUE) println(sum)
                        }.coerceAtLeast(1)
                        memory = Bench.memoryBandwidthMbPerSecond(bytes * 2, ms)
                    }

                    Bench.Stage.STORAGE -> {
                        val file = java.io.File(context.cacheDir, "bench.bin")
                        val bytes = 8 * 1024 * 1024
                        val ms = measureTimeMillis {
                            file.outputStream().use { out ->
                                val chunk = ByteArray(64 * 1024)
                                var written = 0
                                while (written < bytes) {
                                    out.write(chunk)
                                    written += chunk.size
                                }
                            }
                            file.inputStream().use { input ->
                                while (input.read(chunkBuffer) > 0) Unit
                            }
                            file.delete()
                        }.coerceAtLeast(1)
                        storage = Bench.memoryBandwidthMbPerSecond(bytes * 2, ms)
                    }

                    Bench.Stage.MULTI -> {
                        val cores = Runtime.getRuntime().availableProcessors()
                        val ms = measureTimeMillis {
                            val jobs = (1..cores).map { index ->
                                Thread {
                                    Bench.intOps(400_000 + index * 1_000)
                                }.apply { start() }
                            }
                            jobs.forEach { it.join() }
                        }.coerceAtLeast(1)
                        multi = cores * 400_000.0 / (ms / 1000.0) / 1_000_000.0
                    }

                    Bench.Stage.GPU -> {
                        // Ускоритель мерим настоящим шейдерным полем: пока оно рисуется,
                        // GpuField сам считает кадры и кладёт их в TurboState.gpuFps.
                        if (gpuFieldAvailable(context)) {
                            gpuRunning = true
                            val deadline = System.currentTimeMillis() + 2_600
                            while (System.currentTimeMillis() < deadline) {
                                kotlinx.coroutines.delay(120)
                            }
                            gpuRunning = false
                            gpu = TurboState.gpuFps.value.toDouble()
                        } else {
                            gpuRunning = false
                            gpu = 0.0
                        }
                    }
                }
            }
            progress = (index + 1) * 100 / stages.size
            partial = "готово: " + current.title
        }

        val done = Bench.Result(
            cpuInt = cpuInt, cpuFloat = cpuFloat, cpuMatrix = cpuMatrix, memory = memory,
            storage = storage, multi = multi, gpu = gpu,
            cores = Runtime.getRuntime().availableProcessors()
        )
        result = done
        Bench.save(context, done)
        history = Bench.load(context)
        // Пасхалка «три прогона»: класс устройства уточняется по серии замеров, а не по одному.
        if (history.size >= 3) com.gsynk.app.core.Eggs.unlock(context, "antutu")
        stage = null
        note = "Готово за " + ((System.currentTimeMillis() - startedAt) / 1000) + " с"
        AppLog.event("Стенд", "прогон завершён: " + done.score + " баллов, " + done.tier)
    }

    DisposableEffect(Unit) { onDispose { TurboState.gpuFps.value = 0f } }

    GrapeScreen(
        title = "Стенд нагрузки",
        subtitle = "свой бенчмарк: процессор, память, накопитель, ускоритель",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = if (result != null) GrapeGreen.copy(alpha = 0.5f) else CardBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result?.let { it.score.toString() + " баллов" } ?: "Баллов пока нет",
                        color = if (result != null) GrapeGreen else TextPrimary,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light)
                    )
                    Text(
                        text = result?.let { it.tier + " • ядер: " + it.cores } ?: Bench.estimateSeconds(Runtime.getRuntime().availableProcessors()).toString() + " секунд на прогон",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
                if (stage == null) {
                    Box(
                        Modifier
                            .height(46.dp)
                            .clip(RectangleShape)
                            .background(CardBg)
                            .border(1.dp, ChipBorder, RectangleShape)
                            .padding(horizontal = 16.dp)
                            .clickable { run() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (result == null) "Запустить" else "Ещё раз",
                            color = GrapeGreen,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
            if (stage != null) {
                Spacer(Modifier.height(12.dp))
                if (gpuRunning) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(CardBg)
                    ) { GpuField(Modifier.fillMaxSize(), reportFps = true, speed = 2.4f) }
                    Spacer(Modifier.height(8.dp))
                }
                Text(stage!!.title, color = GrapeCyan, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(CardBg)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(8.dp)
                            .background(GrapeCyan)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("$progress проц. • ${partial.orEmpty()}", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            } else if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(note, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            }
        }

        val done = result
        if (done != null) {
            Spacer(Modifier.height(14.dp))
            SectionTitle("Подробно")
            Spacer(Modifier.height(9.dp))
            SurfaceCard(borderColor = CardBorder) {
                InfoRow("Целые числа", "%.1f млн оп/с".format(done.cpuInt))
                InfoRow("Дроби", "%.1f млн оп/с".format(done.cpuFloat))
                InfoRow("Матрицы", "%.1f млн оп/с".format(done.cpuMatrix))
                InfoRow("Память", "%.0f МБ/с".format(done.memory))
                InfoRow("Накопитель", "%.0f МБ/с".format(done.storage))
                InfoRow("Все ядра", "%.1f млн оп/с".format(done.multi))
                InfoRow("Ускорение от ядер", "×%.1f".format(done.multithreadBonus), GrapeCyan)
                InfoRow("Ускоритель", "%.1f млн оп/с".format(done.gpu), GrapeAmber)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Bench.compare(done, history.drop(1).firstOrNull()),
                    color = GrapeGreen,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            val ok = Nearby.sendProfile(
                                "стенд",
                                "BENCH;" + Bench.shareBody(done, android.os.Build.MODEL)
                            )
                            note = if (ok) "Баллы ушли другу" else "Нет связи — подключитесь в «GSYNK рядом»"
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Отправить другу", color = GrapeCyan, style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)) }

                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable { run() },
                    contentAlignment = Alignment.Center
                ) { Text("Повторить", color = GrapeGreen, style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)) }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Чистая нагрузка")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = GrapePink.copy(alpha = 0.45f)) {
            Text(
                text = "Без баллов и графиков: телефон держит полную нагрузку, пока вы не остановите. " +
                    "Годится, чтобы проверить охлаждение и посмотреть, как падает скорость от нагрева.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                val active by vm.turboActive.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, if (active) GrapePink else ChipBorder, RectangleShape)
                        .clickable { vm.toggleTurbo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (active) "Остановить нагрузку" else "Держать нагрузку",
                        color = if (active) GrapePink else GrapeGreen,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                    )
                }
                val speed by TurboState.cpuGflops.collectAsStateWithLifecycle()
                val heat by TurboState.batteryTemp.collectAsStateWithLifecycle()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("Сейчас: %.1f млрд оп/с".format(speed), color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                    Text(
                        text = "температура: " + "%.0f".format(heat) + "°C",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Прошлые прогоны")
        Spacer(Modifier.height(9.dp))
        if (history.isEmpty()) {
            Text("Пока пусто: первый прогон сохранится автоматически.", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
        } else {
            history.forEachIndexed { index, item ->
                SurfaceCard(borderColor = CardBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.score.toString() + " • " + item.tier, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                            Text(
                                text = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale("ru")).format(java.util.Date(item.at)) +
                                    " • ядер: " + item.cores,
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                            )
                        }
                        if (index == 0) {
                            Text("последний", color = GrapeGreen, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton(
                text = "Очистить историю",
                onClick = {
                    Bench.clear(context)
                    history = emptyList()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Как считается балл", color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Процессор: 1.0 и 0.9 веса за целые числа и дроби, 2.4 за матрицы. Память — 0.4, " +
                    "накопитель — 0.8, многопоток — 1.2, ускоритель — 1.6. Формула одна и та же для " +
                    "всех телефонов, поэтому баллы сравнимы между собой. Ориентиры: 250 тысяч — " +
                    "средний класс, 500 — почти флагман, 900 — флагман.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Небольшой буфер для чтения файла в тесте накопителя. */
private val chunkBuffer = ByteArray(64 * 1024)
