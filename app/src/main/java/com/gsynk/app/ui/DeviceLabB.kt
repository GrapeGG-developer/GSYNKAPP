package com.gsynk.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.gsynk.app.core.BleRadar
import com.gsynk.app.core.Deep
import com.gsynk.app.core.DeviceLab
import com.gsynk.app.core.Eggs
import com.gsynk.app.core.Haptics
import com.gsynk.app.core.ToneSynth
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Расширенная лаборатория железа, часть вторая: звук, моторика, радио, периферия.
 *
 * Осциллограф и анализатор спектра читают настоящий микрофон, камертон играет
 * настоящим генератором, вибро-орган дёргает настоящий моторчик, радар ловит
 * настоящие Bluetooth-пакеты, а сканер каналов показывает загрузку эфира вокруг.
 */

// ————— 1. Осциллограф (микрофон) —————

@Composable
fun LabOscilloscopeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var wave by remember { mutableStateOf(FloatArray(0)) }
    var decibels by remember { mutableStateOf(0f) }
    var peakDb by remember { mutableStateOf(0f) }
    var hertz by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val size = minBuffer.coerceAtLeast(2_048)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, size * 2)
        }.getOrNull()
        if (record == null) {
            error = "микрофон занят или запрещён системой"
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                record.startRecording()
                val buffer = ShortArray(size)
                while (true) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val copy = buffer.copyOf(read)
                    withContext(Dispatchers.Main) {
                        wave = copy.map { it / 32_768f }.toFloatArray()
                        val amplitude = copy.maxOf { abs(it.toInt()) } / 32_768f
                        val db = DeviceLab.decibelsFromAmplitude(amplitude)
                        decibels = db
                        if (db > peakDb) {
                            peakDb = db
                            // Свист выше 90 дБ открывает пасхалку «девяносто».
                            if (db > 90f) Eggs.unlock(context, "whistle90")
                        }
                        hertz = estimateHz(copy, rate)
                    }
                }
            }.onFailure { error = it.message ?: "микрофон не отдал данные" }
                .also { runCatching { record.stop(); record.release() } }
        }
    }

    DisposableEffect(Unit) {
        onDispose { /* запись останавливается по выходу из корутины */ }
    }

    GrapeScreen(title = "Осциллограф", subtitle = "звук на графике", onBack = onBack) {
        if (!granted) {
            SurfaceCard(borderColor = CardBorder) {
                Text("Нужен микрофон", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Разрешение нужно только для замера: звук никуда не отправляется и не пишется в файл.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Разрешить микрофон",
                    onClick = { request.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@GrapeScreen
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(Metro.Shapes.tile)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val half = size.height / 2f
                drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, half), Offset(size.width, half), 1.5f)
                if (wave.size < 2) return@Canvas
                val step = size.width / (wave.size - 1)
                val path = androidx.compose.ui.graphics.Path()
                wave.forEachIndexed { index, value ->
                    val x = index * step
                    val y = half - value.coerceIn(-1f, 1f) * half * 0.92f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = GrapeCyan, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(decibels.roundToInt().toString() + " дБ", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text("сейчас", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(peakDb.roundToInt().toString() + " дБ", color = GrapeGreen, style = MaterialTheme.typography.titleMedium)
                Text("пик", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (hertz > 20f) hertz.roundToInt().toString() + " Гц" else "—",
                    color = GrapeCyan,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("главный тон", color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Свистни, спой ноту или постучи по столу — форма волны изменится сразу. " +
                    "Главный тон считается по числу переходов волны через ноль.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Metro.Palette.Red, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Оценка главного тона: считаем переходы через ноль за окно записи. */
private fun estimateHz(buffer: ShortArray, rate: Int): Float {
    if (buffer.size < 256) return 0f
    var crossings = 0
    var previous = buffer[0].toInt()
    for (index in 1 until buffer.size) {
        val value = buffer[index].toInt()
        if ((previous < 0 && value >= 0) || (previous >= 0 && value < 0)) crossings++
        previous = value
    }
    val seconds = buffer.size.toFloat() / rate
    val hz = (crossings / 2f) / seconds
    return if (hz < 20f || hz > 12_000f) 0f else hz
}

// ————— 2. Анализатор спектра —————

@Composable
fun LabSpectrumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val bands = remember { List(10) { 0f } }
    var bandState by remember { mutableStateOf(List(10) { 0f }) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val rate = 22_050
        val size = 4_096
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2
            )
        }.getOrNull() ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                record.startRecording()
                val buffer = ShortArray(size)
                while (true) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val spectrum = goertzelBands(buffer, read, rate)
                    withContext(Dispatchers.Main) { bandState = spectrum }
                }
            }.also { runCatching { record.stop(); record.release() } }
        }
    }

    GrapeScreen(title = "Спектр звука", subtitle = "полосы частот", onBack = onBack) {
        if (!granted) {
            SurfaceCard(borderColor = CardBorder) {
                Text("Нужен микрофон", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Разрешить микрофон",
                    onClick = { request.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@GrapeScreen
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(Metro.Shapes.tile)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val count = bandState.size
                val barWidth = size.width / count
                bandState.forEachIndexed { index, value ->
                    val height = (value.coerceIn(0f, 1f)) * size.height * 0.94f
                    drawRect(
                        color = when {
                            index < 3 -> Metro.Palette.Cobalt
                            index < 7 -> GrapeCyan
                            else -> Metro.Palette.Magenta
                        },
                        topLeft = Offset(index * barWidth + 4f, size.height - height),
                        size = androidx.compose.ui.geometry.Size(barWidth - 8f, height)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("Полосы", "60 · 120 · 250 · 500 · 1к · 2к · 4к · 8к · 12к · 16к Гц")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Это настоящий спектр: каждая полоса считается алгоритмом Гёрцеля по своему " +
                    "диапазону частот. Свистни — увидишь, как вспыхнет правая часть, спой низко — левая.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Энергия в десяти полосах — прямой алгоритм Гёрцеля, без сторонних библиотек. */
private fun goertzelBands(buffer: ShortArray, length: Int, rate: Int): List<Float> {
    val centers = listOf(60, 120, 250, 500, 1_000, 2_000, 4_000, 8_000, 12_000, 16_000)
    return centers.map { hz ->
        val k = (0.5 + length * hz.toDouble() / rate).toInt()
        val omega = 2.0 * Math.PI * k / length
        val coefficient = 2.0 * kotlin.math.cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        for (index in 0 until length.coerceAtMost(buffer.size)) {
            val window = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / length)
            val sample = buffer[index] / 32_768.0 * window
            val s0 = sample + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = sqrt(s1 * s1 + s2 * s2 - coefficient * s1 * s2) / length
        (power * 34.0).toFloat().coerceIn(0f, 1f)
    }
}

// ————— 3. Камертон-свип —————

@Composable
fun LabSweepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var from by remember { mutableIntStateOf(20) }
    var to by remember { mutableIntStateOf(18_000) }
    var seconds by remember { mutableIntStateOf(8) }
    var current by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (!running) {
            ToneSynth.stop()
            return@LaunchedEffect
        }
        val steps = seconds * 10
        val stepMs = 100L
        var heldHigh = 0
        for (index in 0..steps) {
            val progress = index.toDouble() / steps
            val hz = from + (to - from) * progress
            ToneSynth.start(hz)
            current = hz.roundToInt()
            if (hz >= 17_500) {
                heldHigh++
                if (heldHigh >= 50) Eggs.unlock(context, "hz18")
            }
            delay(stepMs)
        }
        ToneSynth.stop()
        running = false
    }
    DisposableEffect(Unit) { onDispose { ToneSynth.stop() } }

    GrapeScreen(title = "Свип-тон", subtitle = if (running) current.toString() + " Гц" else "от и до", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = if (running) current.toString() + " Гц" else from.toString() + " → " + to + " Гц",
                color = TextPrimary,
                fontSize = Metro.Type.pageTitle
            )
            Text(
                text = "плавный переход за " + seconds + " секунд",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Откуда")
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(20, 100, 440, 1_000).forEach { value ->
                SortChip(value.toString() + " Гц", from == value) { from = value }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionTitle("Куда")
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(8_000, 12_000, 16_000, 18_000).forEach { value ->
                SortChip(value.toString() + " Гц", to == value) { to = value }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionTitle("За сколько")
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(4, 8, 15, 30).forEach { value ->
                SortChip(value.toString() + " с", seconds == value) { seconds = value }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (running) "Остановить" else "Поехали",
            onClick = { running = !running },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Держи телефон подальше от уха: на высокой частоте звук становится резким. " +
                    "Если ты слышишь 18 000 Гц — у тебя молодой слух, серьёзно: с возрастом этот " +
                    "порог падает до 12–14 кГц.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ————— 4. Вибро-орган —————

@Composable
fun LabHapticScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var length by remember { mutableIntStateOf(40) }
    var pause by remember { mutableIntStateOf(60) }
    var count by remember { mutableIntStateOf(4) }
    var lastPlayed by remember { mutableStateOf(0) }
    var heldNote by remember { mutableStateOf<LongArray?>(null) }

    GrapeScreen(title = "Вибро-орган", subtitle = "азбука мотором", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = length.toString() + " мс × " + count,
                color = TextPrimary,
                fontSize = Metro.Type.pageSubtitle
            )
            Text("пауза между точками " + pause + " мс", color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Длина одного сигнала")
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(10, 20, 40, 80, 160).forEach { value ->
                SortChip(value.toString() + " мс", length == value) { length = value }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionTitle("Сколько раз")
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf(1, 2, 3, 4, 6, 9).forEach { value ->
                SortChip(value.toString(), count == value) { count = value }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = "Проиграть",
                onClick = {
                    val long = mutableListOf(0L, length.toLong())
                    repeat(count - 1) {
                        long.add(pause.toLong())
                        long.add(length.toLong())
                    }
                    Haptics.play(context, long.toLongArray())
                    lastPlayed = count
                    if (count >= 9) Eggs.unlock(context, "coil9")
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Один удар",
                onClick = { Haptics.play(context, longArrayOf(0L, 60L)) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Что уже изучено", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            InfoRow("Точек в SOS", "3 · 3 · 3 с паузами 120 мс")
            InfoRow("Сила вибрации", if (Haptics.available(context)) "мотор есть" else "мотор недоступен")
            InfoRow("Играли", lastPlayed.toString() + " раз")
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Позвать SOS",
                onClick = {
                    Haptics.play(
                        context,
                        longArrayOf(
                            0, 60, 60, 180, 60, 60, 60, 180, 60, 60, 60, 420,
                            180, 60, 60, 180, 180, 60, 60, 180, 180, 60, 60, 420,
                            60, 60, 60, 180, 60, 60, 60, 180, 60, 60, 60
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ————— 5. Радар Bluetooth —————

@Composable
fun LabBleRadarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val devices by BleRadar.devices.collectAsStateWithLifecycle()
    var running by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            running = BleRadar.start(context)
            note = if (running) "Сканирую эфир…" else "Bluetooth выключен или сканер занят"
        } else {
            note = "Без разрешений «Рядом с устройствами» сканировать нельзя"
        }
    }

    DisposableEffect(Unit) { onDispose { BleRadar.stop() } }

    GrapeScreen(title = "BLE-радар", subtitle = devices.size.toString() + " устройств вокруг", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (running) "Остановить" else "Сканировать",
                onClick = {
                    if (running) {
                        BleRadar.stop()
                        running = false
                        note = "Остановлено"
                    } else if (BleRadar.canScan(context)) {
                        BleRadar.reset()
                        running = BleRadar.start(context)
                        note = if (running) "Сканирую эфир…" else "Bluetooth выключен"
                    } else {
                        launcher.launch(BleRadar.permissionFor().toTypedArray())
                    }
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Очистить",
                onClick = { BleRadar.reset(); note = "Список очищен" },
                modifier = Modifier.weight(1f)
            )
        }
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        devices.sortedByDescending { it.rssi }.forEach { device ->
            SurfaceCard(borderColor = if (device.meters < 2.0) GrapeGreen.copy(alpha = 0.45f) else CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = device.name.ifBlank { "без имени" },
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(device.address, color = TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (device.meters < 10.0) Deep.round1(device.meters.toFloat()).toString() + " м"
                            else "далеко",
                            color = GrapeCyan,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(device.rssi.toString() + " дБм", color = TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                BarIndicator(
                    percent = BleRadar.rssiToStrength(device.rssi),
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(GrapeGreen, GrapeCyan)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Расстояние считается по силе сигнала (примерная формула «дБм на метр»), " +
                    "поэтому оно приблизительное: стены и тело сильно ослабляют Bluetooth.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ————— 6. Каналы Wi-Fi —————

@Composable
fun LabWifiChannelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scan by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) performWifiScan(context) { result, failure ->
            scan = result
            note = failure
            scanning = false
            if (result.size >= 5) Eggs.unlock(context, "wifi5")
        } else {
            note = "Без разрешения на местоположение список сетей недоступен"
            scanning = false
        }
    }

    GrapeScreen(title = "Каналы Wi-Fi", subtitle = scan.size.toString() + " сетей вокруг", onBack = onBack) {
        PrimaryButton(
            text = if (scanning) "Сканирую…" else "Найти сети",
            onClick = {
                scanning = true
                note = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    performWifiScan(context) { result, failure ->
                        scan = result
                        note = failure
                        scanning = false
                        if (result.size >= 5) Eggs.unlock(context, "wifi5")
                    }
                } else {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TextDim, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))

        val perChannel = scan.groupBy({ it.second }).mapValues { it.value.size }.toList().sortedByDescending { it.second }
        if (perChannel.isNotEmpty()) {
            SectionTitle("Загруженность каналов")
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(Metro.Shapes.tile)
                    .background(CardBgAlt)
                    .border(1.dp, CardBorder, Metro.Shapes.tile)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val max = (perChannel.maxOf { it.second }).coerceAtLeast(1)
                    val barWidth = size.width / perChannel.size
                    perChannel.forEachIndexed { index, (channel, count) ->
                        val height = count.toFloat() / max * size.height * 0.92f
                        drawRect(
                            color = when {
                                count >= 5 -> Metro.Palette.Red
                                count >= 3 -> Metro.Palette.Amber
                                else -> GrapeGreen
                            },
                            topLeft = Offset(index * barWidth + 3f, size.height - height),
                            size = androidx.compose.ui.geometry.Size(barWidth - 6f, height)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = perChannel.joinToString("  ·  ") { (channel, count) -> channel.toString() + ": " + count },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
        }

        scan.sortedByDescending { it.first }.forEach { (rssi, channel, ssid) ->
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ssid.ifBlank { "скрытая сеть" },
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("канал " + channel, color = TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(rssi.toString() + " дБм", color = GrapeCyan, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Если твой роутер стоит на канале, где ещё пять соседей — эфир забит, и Wi-Fi " +
                    "тормозит. Свободный канал обычно 1, 6 или 11 — они не перекрывают друг друга.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Скан Wi-Fi: система отдаёт результаты асинхронно, поэтому слушаем широковещание. */
@android.annotation.SuppressLint("MissingPermission")
private fun performWifiScan(
    context: Context,
    done: (List<Triple<Int, Int, String>>, String?) -> Unit
) {
    val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (manager == null) {
        done(emptyList(), "Wi-Fi модуль недоступен")
        return
    }
    if (!manager.isWifiEnabled) {
        done(emptyList(), "Wi-Fi выключен — включи и попробуй снова")
        return
    }
    val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(innerContext: Context?, intent: android.content.Intent?) {
            runCatching { context.applicationContext.unregisterReceiver(this) }
            val results = runCatching { manager.scanResults }.getOrDefault(emptyList())
            val mapped = results.map { result ->
                Triple(result.level, Deep.channelOf(result.frequency), result.SSID ?: "")
            }.distinctBy { it.third to it.second }
            done(mapped, if (mapped.isEmpty()) "Система не отдала ни одной сети (ограничение Android 13+)" else null)
        }
    }
    runCatching {
        androidx.core.content.ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        manager.startScan()
    }.onFailure {
        done(emptyList(), "Скан запрещён системой: " + (it.message ?: "нет доступа"))
    }
}

// ————— 7. Сигнал своей сети —————

@Composable
fun LabWifiSignalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rssi by remember { mutableIntStateOf(0) }
    var ssid by remember { mutableStateOf("") }
    var linkSpeed by remember { mutableIntStateOf(0) }
    var frequency by remember { mutableIntStateOf(0) }
    val series = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = manager?.connectionInfo
                if (info != null && info.networkId != -1) {
                    rssi = info.rssi
                    ssid = info.ssid?.trim('"') ?: ""
                    linkSpeed = info.linkSpeed
                    frequency = info.frequency
                    series.add(rssi.toFloat())
                    while (series.size > 120) series.removeAt(0)
                }
            }
            delay(1_500)
        }
    }

    GrapeScreen(title = "Сигнал Wi-Fi", subtitle = ssid.ifBlank { "нет подключения" }, onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text(rssi.toString() + " дБм", color = TextPrimary, fontSize = Metro.Type.pageTitle)
            Text(
                text = when {
                    rssi == 0 -> "подключения нет"
                    rssi > -55 -> "отличный сигнал"
                    rssi > -67 -> "хороший сигнал"
                    rssi > -75 -> "слабый, но рабочий"
                    else -> "почти не тянет"
                },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(Metro.Shapes.tile)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (series.size < 2) return@Canvas
                val step = size.width / (series.size - 1)
                val path = androidx.compose.ui.graphics.Path()
                series.forEachIndexed { index, value ->
                    val normalized = ((value + 95f) / 65f).coerceIn(0f, 1f)
                    val y = size.height - normalized * size.height
                    if (index == 0) path.moveTo(index * step, y) else path.lineTo(index * step, y)
                }
                drawPath(path, GrapeCyan, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("Частота", if (frequency > 0) frequency.toString() + " МГц · " + Deep.bandOf(frequency) else "—")
        InfoRow("Канал", if (frequency > 0) Deep.channelOf(frequency).toString() else "—")
        InfoRow("Скорость канала", linkSpeed.toString() + " Мбит/с")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Гуляй по квартире и смотри график: −70 дБм — это примерно «через две стены», " +
                    "−85 дБм — предел, дальше сеть отваливается.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ————— 8. USB и OTG —————

@Composable
fun LabUsbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val devices = remember { Deep.usb(context) }
    val host = remember { Deep.hasUsbHost(context) }
    // Пасхалка «переходник»: подключил что-нибудь через OTG — секрет открыт.
    LaunchedEffect(devices.size) {
        if (devices.isNotEmpty()) Eggs.unlock(context, "usbplug")
    }

    GrapeScreen(title = "USB и OTG", subtitle = devices.size.toString() + " устройств", onBack = onBack) {
        SurfaceCard(borderColor = if (host) GrapeGreen.copy(alpha = 0.4f) else CardBorder) {
            Text(
                text = if (host) "Режим хоста есть" else "Режим хоста недоступен",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (host) {
                    "Можно подключить клавиатуру, мышь, флешку или геймпад через переходник."
                } else {
                    "Прошивка не заявляет USB-хост: внешние устройства через OTG работать не будут."
                },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(10.dp))
        devices.forEach { device ->
            SurfaceCard(borderColor = CardBorder) {
                Text(device.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                InfoRow("Производитель", "0x" + device.vendor.toString(16))
                InfoRow("Модель", "0x" + device.product.toString(16))
                InfoRow("Интерфейсы", device.classes)
                InfoRow("Доступ", if (device.hasPermission) "разрешён" else "не разрешён")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (devices.isEmpty()) {
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = "Подключи что-нибудь через OTG-переходник — устройство появится здесь сразу, " +
                        "без root и без сторонних приложений.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ————— 9. Визитка на метку —————

@Composable
fun LabNfcCardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var written by remember { mutableStateOf(0) }
    // Счётчик пригодится, когда метка запишется: экран подсказывает, что дальше.

    val profile = remember { com.gsynk.app.core.DeviceProfile.detect(context) }

    GrapeScreen(title = "Метка-визитка", subtitle = "записать себя на метку", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text("Что попадёт на метку", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            InfoRow("Модель", profile.model)
            InfoRow("Android", profile.androidLabel)
            InfoRow("Возможности", profile.tierLabel)
            InfoRow("Размер", "около 150 байт из " + com.gsynk.app.core.NfcTagWriter.TYPICAL_CAPACITY)
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = "Открыть запись меток",
            onClick = { Router.panel("nfc") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Запись идёт в разделе Grape Link: там же видно, сколько байт осталось и " +
                    "записалась ли метка. Одно касание — и метка отдаёт визитку любому телефону, " +
                    "даже без GSYNK.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            if (written > 0) Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Проверка меток", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Приложи любую метку к задней части телефона в Grape Link: приложение " +
                    "покажет её тип, размер и что на ней записано.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "К NFC-разделу",
                onClick = { Router.panel("nfc") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
