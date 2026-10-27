package com.gsynk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gsynk.app.core.Pulse
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Пульс камерой.
 *
 * Палец закрывает объектив и вспышку: кровь с каждым ударом меняет, сколько света
 * проходит через палец. Камера это видит, а приложение считает частоту автокорреляцией.
 * Пока идёт замер, волна рисуется живьём — видно, что сигнал настоящий.
 */
@Composable
fun PulseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
    }

    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var live by remember { mutableStateOf(FloatArray(0)) }
    var report by remember { mutableStateOf<Pulse.Report?>(null) }
    var error by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Int>>(emptyList()) }
    var smoothMode by remember { mutableStateOf(true) }

    // Камера пишет кадры из своего потока: держим кольцевой буфер и читаем его для показа.
    val tick = remember { AtomicInteger(0) }
    val ring = remember { FloatArray(420) }
    val writeIndex = remember { AtomicInteger(0) }

    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }

    // Живая волна: раз в 120 мс забираем то, что успела записать камера.
    LaunchedEffect(running) {
        while (running) {
            val value = writeIndex.get()
            val out = FloatArray(minOf(value, ring.size))
            for (index in out.indices) {
                out[index] = ring[(value - out.size + index + ring.size * 2) % ring.size]
            }
            live = out
            tick.incrementAndGet()
            delay(120)
        }
    }

    fun start() {
        if (running) return
        running = true
        report = null
        error = ""
        seconds = 0
        writeIndex.set(0)
        scope.launch {
            val started = System.currentTimeMillis()
            val progress = launch {
                while (running) {
                    seconds = ((System.currentTimeMillis() - started) / 1000L).toInt()
                    delay(500)
                }
            }
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    Pulse.read(context, Pulse.DEFAULT_MILLIS) { value ->
                        ring[writeIndex.getAndIncrement() % ring.size] = value
                    }
                }
            }
            progress.cancel()
            result.onSuccess { reading ->
                val analysed = Pulse.analyse(reading)
                report = analysed
                // Запоминаем только разобранные и уверенные замеры: иначе в списке будет мусор.
                val bpm = analysed.bpm
                if (bpm != null && analysed.quality >= 25) history = (listOf(bpm) + history).take(6)
            }.onFailure { failure ->
                error = failure.message ?: "камера не дала записать"
            }
            running = false
        }
    }

    GrapeScreen(
        title = "пульс камерой",
        subtitle = "сколько ударов в минуту — по свету через палец",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapePink.copy(alpha = 0.45f)) {
            Text(
                "как приложить палец",
                color = GrapePink,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Накройте пальцем объектив и вспышку сразу — вспышка начнёт светить " +
                    "сквозь палец. Давить сильно не нужно: от давления кровь уходит, и сигнал пропадает. " +
                    "Рука должна лежать на столе, а не висеть.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = if (running) GrapeGreen.copy(alpha = 0.5f) else ChipBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (running) "замер идёт: " + seconds.toString() + " с" else "готов записывать",
                        color = if (running) GrapeGreen else TextPrimary,
                        fontSize = Gioui.Type.body
                    )
                    Text(
                        text = "нужно " + (Pulse.DEFAULT_MILLIS / 1000).toString() +
                            " с и немного терпения",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
                if (running) PulseDot(GrapeRed)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                if (live.size > 4) {
                    PulseWave(live.toList())
                } else {
                    Text(
                        text = if (running) "камера греет палец…" else "здесь появится волна пульса",
                        color = TextDim,
                        fontSize = Gioui.Type.caption,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (running) "идёт замер…" else "измерить пульс",
                onClick = { if (allowed) start() else request.launch(Manifest.permission.CAMERA) },
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.5f)) {
                Text("не смог записать", color = GrapeRed, fontSize = Gioui.Type.body)
                Spacer(Modifier.height(4.dp))
                Text(error, color = TextDim, fontSize = Gioui.Type.caption)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Часто причина одна: камеру уже занял другой экран — например, замер мерцания " +
                        "или микроскоп. Закройте их и попробуйте снова.",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
        }

        val reportValue = report
        if (reportValue != null) {
            Spacer(Modifier.height(10.dp))
            val bpm = reportValue.bpm
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
                Text("результат", color = TextDim, fontSize = Gioui.Type.caption)
                Text(
                    text = if (bpm == null) "—" else bpm.toString(),
                    color = if (bpm == null) GrapeAmber else GrapeGreen,
                    fontSize = Gioui.Type.pageTitle,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (bpm == null) "ударов в минуту не найдено" else "ударов в минуту",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
                Spacer(Modifier.height(8.dp))
                InfoRow("качество сигнала", reportValue.quality.toString() + " из 100")
                InfoRow("кадров в секунду", String.format(Locale.US, "%.1f", reportValue.fps))
                InfoRow("точек в записи", reportValue.samples.size.toString())
                Spacer(Modifier.height(8.dp))
                Text(reportValue.note, color = GrapeAmber, fontSize = Gioui.Type.caption)
                if (bpm != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(Pulse.verdict(bpm), color = TextPrimary, fontSize = Gioui.Type.body)
                }
            }
            if (reportValue.samples.size > Pulse.MIN_SAMPLES) {
                Spacer(Modifier.height(10.dp))
                SurfaceCard(borderColor = GrapeViolet.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "обездвиженная волна",
                            color = TextPrimary,
                            fontSize = Gioui.Type.body,
                            modifier = Modifier.weight(1f)
                        )
                        TagChip(text = if (smoothMode) "сглажено" else "как есть", selected = smoothMode) {
                            smoothMode = !smoothMode
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        PulseWave(
                            data = reportValue.samples,
                            smooth = smoothMode
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Автокорреляция ищет сдвиг, на котором волна похожа сама на себя. " +
                            "Период этого сдвига — один удар сердца.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionTitle("замеры за сеанс")
            SurfaceCard {
                history.forEachIndexed { index, bpm ->
                    InfoRow("замер " + (index + 1), bpm.toString() + " уд/мин")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "среднее " + String.format(Locale.US, "%.0f", history.average()) + " уд/мин. " +
                        "Разброс в 5–10 ударов между замерами — нормальное дело, пульс всё время меняется.",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = ChipBorder) {
            Text("это не медицинский прибор", color = GrapeAmber, fontSize = Gioui.Type.body)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Измерение честное — с настоящей камеры, но точность ниже, чем у пульсоксиметра: " +
                    "камера снимает не на просвет, как датчик на палец, а с небольшой глубины. " +
                    "Никаких диагнозов: только цифра и её разбор.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Волна пульса: живая при замере и полная после него. */
@Composable
private fun PulseWave(data: List<Float>, smooth: Boolean = false) {
    val prepared = remember(data, smooth) {
        if (smooth) Pulse.smooth(Pulse.detrend(data), 5).toFloatArray() else data.toFloatArray()
    }
    val step = (prepared.size / 260).coerceAtLeast(1)
    val points = ArrayList<Float>(300)
    var index = 0
    while (index < prepared.size) {
        points.add(prepared[index])
        index += step
    }
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 1f
    val span = (max - min).coerceAtLeast(0.00001f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(if (smooth) 140.dp else 110.dp)
    ) {
        val middle = size.height / 2f
        drawLine(
            color = ChipBorder,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = 1f
        )
        val path = Path()
        points.forEachIndexed { position, value ->
            val x = size.width * position / (points.size - 1).coerceAtLeast(1).toFloat()
            val normalized = (value - min) / span
            val y = size.height - 6f - normalized * (size.height - 12f)
            if (position == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, GrapeCyan, style = Stroke(width = 2f))
        // Заливка под волной: так её видно даже на маленьком экране.
        val filled = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            filled,
            Brush.verticalGradient(listOf(GrapeViolet.copy(alpha = 0.28f), GrapeViolet.copy(alpha = 0f)))
        )
    }
}
