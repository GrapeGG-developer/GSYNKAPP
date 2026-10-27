package com.gsynk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gsynk.app.core.Echo
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Эхо-линейка — расстояние звуком.
 *
 * Экран честный во всём: показывает и сырую волну записи, и найденную задержку, и то,
 * как температура воздуха повлияла на расчёт. Если эхо не нашлось — так и говорит.
 */
@Composable
fun EchoRulerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
    }

    var tempC by remember { mutableStateOf(22) }
    var running by remember { mutableStateOf(false) }
    var envelope by remember { mutableStateOf(FloatArray(0)) }
    var estimate by remember { mutableStateOf<Echo.Estimate?>(null) }
    var note by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Float>>(emptyList()) }

    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.RECORD_AUDIO) }

    fun measure() {
        if (running) return
        running = true
        note = "слушаю: щелчок и его отражение…"
        envelope = FloatArray(0)
        scope.launch {
            val result = runCatching { withContext(Dispatchers.Default) { shoot(context) } }
            result.onSuccess { samples ->
                val env = Echo.envelope(samples, window = 24)
                envelope = env
                val found = Echo.estimate(env, SAMPLE_RATE, tempC)
                estimate = found
                note = if (found == null) {
                    "эхо не нашлось: поверните телефон к стене ближе, уберите чехол или измеряйте в тихой комнате"
                } else {
                    Echo.note(found.cm, found.delayMs)
                }
                if (found != null && found.cm in 35f..800f) {
                    history = (listOf(found.cm) + history).take(6)
                }
            }.onFailure { error ->
                note = "не получилось записать: " + (error.message ?: "микрофон занят")
            }
            running = false
        }
    }

    GrapeScreen(
        title = "эхо-линейка",
        subtitle = "расстояние звуком: щелчок, эхо, скорость звука",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.45f)) {
            Text(
                "как это работает",
                color = GrapeCyan,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Телефон щёлкает динамиком и слушает микрофоном. Щелчок идёт прямо в микрофон, " +
                    "его отражение возвращается позже — по разнице времени и считается расстояние. " +
                    "Скорость звука зависит от температуры: 331,3 м/с при нуле плюс 0,606 на градус.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("температура воздуха")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "в комнате " + tempC.toString() + " °C",
                        color = TextPrimary,
                        fontSize = Gioui.Type.body
                    )
                    Text(
                        text = "скорость звука " + String.format(Locale.US, "%.1f", Echo.soundSpeed(tempC)) +
                            " м/с",
                        color = GrapeCyan,
                        fontSize = Gioui.Type.caption
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1f", Echo.cmPerMilli(tempC)) + " см/мс",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
            Spacer(Modifier.height(8.dp))
            NeoSlider(
                value = tempC + 10,
                onValueChange = { tempC = it - 10 },
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(GrapeCyan, GrapeViolet)
                ),
                enabled = !running
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Температура редко кто учитывает, а на 3 метрах разница между 0 и 30 градусами — 17 см.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }

        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = if (running) "слушаю…" else "щёлкнуть и измерить",
            onClick = { if (allowed) measure() else request.launch(Manifest.permission.RECORD_AUDIO) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        )

        if (envelope.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SurfaceCard(borderColor = GrapeViolet.copy(alpha = 0.4f)) {
                Text("что услышал микрофон", color = TextPrimary, fontSize = Gioui.Type.body)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    WaveCanvas(envelope)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Первый горб — сам щелчок, второй — вернувшееся эхо. Разница между ними и есть задержка.",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
        }

        val found = estimate
        if (found != null) {
            Spacer(Modifier.height(10.dp))
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
                Text("расстояние", color = TextDim, fontSize = Gioui.Type.caption)
                Text(
                    text = String.format(Locale.US, "%.0f см", found.cm),
                    color = GrapeGreen,
                    fontSize = Gioui.Type.tileLive,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("задержка эха", found.delayMs.toString() + " мс")
                InfoRow("уровень эха", String.format(Locale.US, "%.0f", found.level))
                InfoRow(
                    "по расчёту без температуры",
                    String.format(Locale.US, "%.0f см", Echo.distanceCm(found.delayMs.toFloat(), 0))
                )
                Spacer(Modifier.height(6.dp))
                Text(note, color = GrapeAmber, fontSize = Gioui.Type.caption)
            }
        } else if (note.isNotBlank() && !running) {
            Spacer(Modifier.height(10.dp))
            SurfaceCard(borderColor = GrapePink.copy(alpha = 0.45f)) {
                Text("что получилось", color = TextPrimary, fontSize = Gioui.Type.body)
                Spacer(Modifier.height(4.dp))
                Text(note, color = GrapePink, fontSize = Gioui.Type.caption)
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionTitle("последние замеры")
            SurfaceCard {
                history.forEachIndexed { index, cm ->
                    InfoRow("замер " + (index + 1), String.format(Locale.US, "%.0f см", cm))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "среднее: " + String.format(Locale.US, "%.0f", history.average()) + " см — " +
                        "по нему видно, насколько замеры повторяются",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = ChipBorder) {
            Text("честно о точности", color = GrapeAmber, fontSize = Gioui.Type.body)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Динамик и микрофон разнесены на несколько сантиметров, поэтому путь у эха " +
                    "чуть длиннее расчётного. Ближе 35 см эхо сливается с самим щелчком — там " +
                    "приложение прямо скажет, что верить нельзя. Лучше всего работает от 0,5 до 4 м " +
                    "по ровной стене или столу.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseDot(GrapeGreen)
                Text(
                    text = "масштаб записи: " + Echo.RECORD_MS.toString() + " мс, это больше 30 метров пути",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Запись щелчка и эха целиком: сначала слушаем, потом играем, потом ждём отражение. */
private fun shoot(context: android.content.Context): ShortArray {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        throw SecurityException("нет разрешения на микрофон")
    }
    val rate = SAMPLE_RATE
    val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val bufferSize = minBuffer.coerceAtLeast(rate / 2)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.UNPROCESSED,
        rate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize.coerceAtLeast(4_096)
    )
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        runCatching { recorder.release() }
        throw IllegalStateException("микрофон занят другим приложением")
    }
    val click = Echo.chirp(rate)
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(click.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    val total = rate * Echo.RECORD_MS / 1000
    val data = ShortArray(total)
    try {
        recorder.startRecording()
        // Стартовый запас: запись точно идёт, когда играет щелчок.
        Thread.sleep(60)
        runCatching {
            track.write(click, 0, click.size)
            track.play()
        }
        var read = 0
        while (read < total) {
            val got = recorder.read(data, read, total - read)
            if (got <= 0) break
            read += got
        }
    } finally {
        runCatching { recorder.stop() }
        runCatching { recorder.release() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }
    return data
}

/** Волна записи: рисуем огибающую, а не каждый отсчёт — так видно и щелчок, и эхо. */
@Composable
private fun WaveCanvas(envelope: FloatArray) {
    val peak = envelope.maxOrNull() ?: 1f
    val safe = if (peak <= 1f) 1f else peak
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val middle = size.height / 2f
        // Сетка по горизонтали: 25 мс на деление — по ней глазом видно задержку.
        val stepMs = 25
        val perMs = size.width / Echo.RECORD_MS.toFloat()
        var ms = stepMs
        while (ms < Echo.RECORD_MS) {
            drawLine(
                color = ChipBorder,
                start = Offset(perMs * ms, 0f),
                end = Offset(perMs * ms, size.height),
                strokeWidth = 1f
            )
            ms += stepMs
        }
        drawLine(
            color = ChipBorder,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = 1f
        )
        val path = Path()
        val count = envelope.size
        if (count > 1) {
            path.moveTo(0f, middle)
            for (index in 0 until count) {
                val x = size.width * index / (count - 1).toFloat()
                val y = middle - (envelope[index] / safe) * (middle - 4f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        drawPath(path, GrapeCyan, style = Stroke(width = 2f))
        // Карточка фона-подписи: горизонт сетки.
        drawLine(
            color = GrapeViolet.copy(alpha = 0.5f),
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 2f
        )
    }
}

private const val SAMPLE_RATE = 44_100
