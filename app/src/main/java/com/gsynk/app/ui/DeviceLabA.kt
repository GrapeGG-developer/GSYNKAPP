package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.Deep
import com.gsynk.app.core.DeviceLab
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.Eggs
import com.gsynk.app.core.Haptics
import com.gsynk.app.core.Sensors
import com.gsynk.app.core.TurboState
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Расширенная лаборатория железа, часть первая: датчики, чип, питание, экран.
 *
 * Всё, что здесь происходит, — чтение настоящих значений: частоты ядер из `/sys`,
 * температуры из тепловых зон, ток зарядки из батарейного сервиса, строки ускорителя
 * через EGL. Ничего не пишем в железо и не трогаем разделы, поэтому лаборатория
 * безопасна так же, как диспетчер задач.
 */

// ————— общие мелочи лаборатории —————

/** Живые значения одного датчика: подписка снимается вместе с экраном. */
@Composable
private fun rememberSensorValues(
    context: Context,
    type: Int,
    delay: Int = SensorManager.SENSOR_DELAY_GAME
): FloatArray? {
    var values by remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(type, delay) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // Системные AON-датчики пропускаем: на Android 16 подписка на них роняет процесс.
        val sensor = manager?.getDefaultSensor(type)?.takeIf { candidate ->
            !DeviceLab.isSystemOnlySensor(type, candidate.name, candidate.stringType)
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                values = event.values.copyOf()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) runCatching { manager?.registerListener(listener, sensor, delay) }
        onDispose { runCatching { manager?.unregisterListener(listener) } }
    }
    return values
}

@Composable
private fun sensorMissing(title: String, note: String, onBack: () -> Unit) {
    GrapeScreen(title = title, subtitle = "датчика нет", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text("Этого датчика в телефоне нет", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(note, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
        }
    }
}

/** Лента значений: рисует последние точки как график, без библиотек. */
@Composable
private fun Strip(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = GrapeCyan,
    baseline: Float? = null,
    low: Float? = null,
    high: Float? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(Metro.Shapes.tile)
            .background(CardBgAlt)
            .border(1.dp, CardBorder, Metro.Shapes.tile)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.size < 2) return@Canvas
            val minValue = low ?: (values.min() - 0.5f)
            val maxValue = high ?: (values.max() + 0.5f)
            val span = (maxValue - minValue).coerceAtLeast(0.001f)
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - ((value - minValue) / span) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            baseline?.let { value ->
                val y = size.height - ((value - minValue) / span) * size.height
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

/** История значений с ограничением длины. */
@Composable
private fun history(limit: Int = 120): MutableList<Float> = remember { mutableStateListOf<Float>() }

private fun MutableList<Float>.push(value: Float, limit: Int = 120) {
    add(value)
    while (size > limit) removeAt(0)
}

// ————— 1. Все датчики с характеристиками —————

@Composable
fun LabSensorGridScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sensors = remember { Deep.sensors(context) }
    var filter by remember { mutableStateOf("все") }
    val shown = remember(filter, sensors) {
        when (filter) {
            "только пробуждение" -> sensors.filter { it.wakeUp }
            "мало энергии" -> sensors.filter { it.powerMa < 0.6f }
            else -> sensors
        }
    }

    GrapeScreen(title = "Все датчики", subtitle = sensors.size.toString() + " штук", onBack = onBack) {
        ChipFlow {
            listOf("все", "только пробуждение", "мало энергии").forEach { title ->
                SortChip(title, filter == title) { filter = title }
            }
        }
        Spacer(Modifier.height(10.dp))
        shown.forEach { sensor ->
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sensor.type, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                        Text(sensor.name, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                    }
                    if (sensor.wakeUp) {
                        Text("будит", color = GrapeGreen, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                    }
                }
                Spacer(Modifier.height(6.dp))
                InfoRow("Производитель", sensor.vendor)
                InfoRow("Диапазон", "+/− " + Deep.round1(sensor.maxRange).toString() + " ед.")
                InfoRow("Шаг", sensor.resolution.toString())
                InfoRow("Энергия", sensor.powerMa.toString() + " мА")
                InfoRow("Самый быстрый", (sensor.minDelayUs / 1000).toString() + " мс")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ————— 2. Ядра процессора —————

@Composable
fun LabCoresScreen(onBack: () -> Unit) {
    var cores by remember { mutableStateOf(Deep.cores()) }
    var tick by remember { mutableIntStateOf(0) }
    val frequencies = remember { Deep.frequencies() }
    val governors = remember { Deep.governors() }
    val context = LocalContext.current
    val profile = remember(context) { DeviceProfile.detect(context) }

    LaunchedEffect(Unit) {
        while (true) {
            cores = Deep.cores()
            tick++
            delay(700)
        }
    }
    val meanMhz = if (cores.isEmpty()) 0 else cores.map { it.currentMhz }.average().toInt()

    GrapeScreen(
        title = "Ядра и частоты",
        subtitle = cores.size.toString() + " ядер · " + meanMhz + " МГц сейчас",
        onBack = onBack
    ) {
        if (cores.isEmpty()) {
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = "Прошивка не отдаёт частоты через /sys — это бывает на новых ядрах " +
                        "с закрытым доступом. Остальное железо всё равно читается.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }
        cores.forEach { core ->
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ядро " + core.index, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (core.online) "в работе" else "спит",
                        color = if (core.online) GrapeGreen else TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(core.currentMhz.toString() + " МГц", color = GrapeCyan, style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
                }
                Spacer(Modifier.height(7.dp))
                BarIndicator(
                    percent = core.loadPercent,
                    brush = Brush.horizontalGradient(
                        listOf(GrapeCyan, GrapeVioletSoft)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("Минимум", core.minMhz.toString() + " МГц")
                InfoRow("Максимум", core.maxMhz.toString() + " МГц")
                InfoRow("Регулятор", core.governor)
            }
            Spacer(Modifier.height(8.dp))
        }
        SurfaceCard(borderColor = CardBorder) {
            Text("Ступени частот", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (frequencies.isEmpty()) "недоступны" else frequencies.joinToString(" · ") + " МГц",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            Text("Регуляторы", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (governors.isEmpty()) "недоступны" else governors.joinToString(" · "),
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Уровень телефона: " + profile.tierLabel + ". Turbо-режим GSYNK греет чип " +
                    "до 45 °C и потом сам останавливается — это защита, а не ограничение.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 3. Тепловые зоны —————

@Composable
fun LabThermalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var zones by remember { mutableStateOf(Deep.zones()) }
    var peak by remember { mutableStateOf(0f) }
    var battery by remember { mutableStateOf(Deep.battery(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            zones = Deep.zones()
            battery = Deep.battery(context)
            val max = (zones.map { it.celsius } + battery.tempC).maxOrNull() ?: 0f
            if (max > peak) peak = max
            if (max >= 45f) Eggs.unlock(context, "thermal45")
            delay(1_500)
        }
    }

    GrapeScreen(
        title = "Термощуп",
        subtitle = "пик: " + Deep.round1(peak).toString() + " °C",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = if (battery.tempC >= 42f) Metro.Palette.Red.copy(alpha = 0.5f) else CardBorder) {
            Text("Батарея", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Text(Deep.round1(battery.tempC).toString() + " °C · " + battery.status, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(8.dp))
            BarIndicator(
                percent = (battery.tempC / 60f * 100).toInt().coerceIn(0, 100),
                brush = Brush.horizontalGradient(
                    listOf(GrapeGreen, Metro.Palette.Amber, Metro.Palette.Red)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        zones.sortedByDescending { it.celsius }.forEach { zone ->
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(zone.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                        Text(zone.name, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                    }
                    Text(Deep.round1(zone.celsius).toString() + " °C", color = GrapeCyan, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Тепловые зоны — это датчики на плате. Прошивка называет их по-своему, " +
                    "поэтому часть подписей переведена вручную: чип, ускоритель, разъём, корпус. " +
                    "45 °C — рубеж, после которого телефон сам сбрасывает частоты.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 4. Зарядка: ток, напряжение, мощность —————

@Composable
fun LabChargeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(Deep.battery(context)) }
    val watts = history()
    val currents = history()
    var peakW by remember { mutableStateOf(0f) }
    var minutes by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Deep.battery(context)
            battery = now
            watts.push(now.watts)
            currents.push(now.currentMa)
            if (now.watts > peakW) peakW = now.watts
            minutes++
            delay(1_000)
        }
    }

    GrapeScreen(
        title = "Заряд и ток",
        subtitle = battery.direction + " · " + Deep.round1(battery.watts).toString() + " Вт",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = if (battery.watts > 10f) GrapeGreen.copy(alpha = 0.45f) else CardBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(battery.percent.toString() + "%", color = TextPrimary, fontSize = Metro.Type.pageTitle)
                    Text(battery.plugged + " · " + battery.status, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Deep.round1(battery.watts).toString() + " Вт", color = GrapeGreen, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                    Text("пик " + Deep.round1(peakW).toString() + " Вт", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoRow("Ток", Deep.round1(battery.currentMa).toString() + " мА")
            InfoRow("Напряжение", battery.voltageMv.toString() + " мВ")
            InfoRow("Температура", Deep.round1(battery.tempC).toString() + " °C")
            InfoRow("Здоровье", battery.health)
            InfoRow("Циклов", if (battery.cycles >= 0) battery.cycles.toString() else "прошивка не сообщает")
            InfoRow("Счётчик заряда", Deep.round1(battery.chargeCounterMah).toString() + " мА·ч")
            InfoRow("Наблюдаем", minutes.toString() + " с")
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Мощность зарядки, Вт")
        Spacer(Modifier.height(6.dp))
        Strip(watts.toList(), color = GrapeGreen, low = 0f, high = maxOf(25f, peakW + 2f))

        Spacer(Modifier.height(10.dp))
        SectionTitle("Ток, мА")
        Spacer(Modifier.height(6.dp))
        Strip(
            currents.toList(),
            color = GrapeCyan,
            low = minOf(-200f, (currents.minOrNull() ?: 0f) - 50f),
            high = maxOf(400f, (currents.maxOrNull() ?: 0f) + 50f),
            baseline = 0f
        )

        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Чем ниже заряд, тем выше ток — так работает быстрая зарядка. Ближе к 80% " +
                    "телефон сам сбрасывает мощность, поэтому ватты падают: это не поломка.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 5. Ускоритель —————

@Composable
fun LabGpuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val gpu = remember { Deep.gpu() }
    val profile = remember { DeviceProfile.detect(context) }
    val canField = remember { gpuFieldAvailable(context) }
    var fps by remember { mutableStateOf(0f) }
    val fpsHistory = history(80)

    LaunchedEffect(Unit) {
        while (true) {
            fps = TurboState.gpuFps.value
            if (fps > 0f) fpsHistory.push(fps, 80)
            delay(500)
        }
    }

    GrapeScreen(title = "Ускоритель", subtitle = profile.model, onBack = onBack) {
        if (canField) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(Metro.Shapes.tile)
                    .border(1.dp, CardBorder, Metro.Shapes.tile)
            ) {
                GpuField(Modifier.fillMaxSize(), reportFps = true, speed = 1.6f)
                Text(
                    text = fps.roundToInt().toString() + " кадр/с",
                    color = Color.White,
                    fontSize = Metro.Type.pageSubtitle,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }
        } else {
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = "Шейдерный стенд доступен на Android 13 и новее — здесь система " +
                        "рисует поле ускорителем, а мы считаем кадры.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        if (fpsHistory.isNotEmpty()) {
            SectionTitle("Кадры в секунду")
            Spacer(Modifier.height(6.dp))
            Strip(fpsHistory.toList(), color = Metro.Palette.Lime, low = 0f, high = 120f)
            Spacer(Modifier.height(10.dp))
        }

        SurfaceCard(borderColor = CardBorder) {
            Text("Что сообщает сам ускоритель", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            if (gpu == null) {
                Text("контекст EGL не создался — строки недоступны", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            } else {
                InfoRow("Производитель", gpu.vendor)
                InfoRow("Ускоритель", gpu.renderer)
                InfoRow("Версия", gpu.version)
                InfoRow("Расширений", gpu.extensions.toString())
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Расширения — это набор возможностей, которые ускоритель умеет напрямую: " +
                    "размытие, анизотропия, вычисления. Чем их больше, тем лучше тянет стекло и лучи.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 6. Акселерограф —————

@Composable
fun LabShakeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val values = rememberSensorValues(context, Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
    val signal = history(150)
    var peak by remember { mutableStateOf(0f) }
    var shakes by remember { mutableStateOf(0) }
    var lastShakeAt by remember { mutableStateOf(0L) }

    LaunchedEffect(values) {
        val v = values ?: return@LaunchedEffect
        val magnitude = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        signal.push(magnitude, 150)
        if (magnitude > peak) peak = magnitude
        val now = System.currentTimeMillis()
        if (magnitude > 18f && now - lastShakeAt > 350L) {
            shakes++
            lastShakeAt = now
            Haptics.tick(context, 18L)
            if (shakes >= 10) Eggs.unlock(context, "shake")
        }
    }

    if (values == null) {
        sensorMissing("Акселерограф", "Без акселерометра телефон не считает ни наклон, ни шаги.", onBack)
        return
    }

    GrapeScreen(
        title = "Акселерограф",
        subtitle = "трясок: " + shakes + " · пик " + Deep.round1(peak).toString() + " g",
        onBack = onBack
    ) {
        val magnitude = kotlin.math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        SurfaceCard(borderColor = CardBorder) {
            Text(Deep.round1(magnitude).toString() + " g", color = TextPrimary, fontSize = Metro.Type.pageTitle)
            Text(
                text = "покой — ровно 1 g: это ускорение свободного падения. Тряска даёт 3–20 g.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        Strip(signal.toList(), color = Metro.Palette.Magenta, low = 0f, high = maxOf(22f, peak + 2f), baseline = 9.81f)
        Spacer(Modifier.height(10.dp))
        InfoRow("По осям", "X " + Deep.round1(values[0]).toString() + " · Y " + Deep.round1(values[1]).toString() + " · Z " + Deep.round1(values[2]))
        InfoRow("Всего трясок", shakes.toString())
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Акселерометр ловит не скорость, а изменение скорости. Именно он решает, " +
                    "что телефон упал: короткий всплеск выше 18 g.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 7. Компас —————

@Composable
fun LabCompassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val compass = remember { Sensors.Compass(context) }
    var heading by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        compass.start { value -> heading = value }
        onDispose { compass.stop() }
    }

    val direction = when (((heading % 360) + 360) % 360) {
        in 337.5f..360f, in 0f..22.5f -> "север"
        in 22.5f..67.5f -> "северо-восток"
        in 67.5f..112.5f -> "восток"
        in 112.5f..157.5f -> "юго-восток"
        in 157.5f..202.5f -> "юг"
        in 202.5f..247.5f -> "юго-запад"
        in 247.5f..292.5f -> "запад"
        else -> "северо-запад"
    }

    GrapeScreen(title = "Компас", subtitle = direction, onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text(heading.roundToInt().toString() + "°", color = TextPrimary, fontSize = Metro.Type.pageTitle)
            Text(
                text = "держи телефон ровно и подальше от магнитов: железо рядом сбивает стрелку",


                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(Metro.Shapes.tile)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 24f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color.White.copy(alpha = 0.10f), radius = radius, center = center)
                drawCircle(Color.White.copy(alpha = 0.18f), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                // Стрелка: север всегда сверху, а вращается картушка — так делают настоящие компасы.
                val angle = Math.toRadians(((heading % 360) + 360) % 360.0)
                val tip = Offset(
                    center.x + (radius * 0.86f * kotlin.math.sin(angle)).toFloat(),
                    center.y - (radius * 0.86f * kotlin.math.cos(angle)).toFloat()
                )
                drawLine(Metro.Palette.Red, center, tip, strokeWidth = 6f)
                drawLine(Color.White.copy(alpha = 0.5f), center, Offset(center.x * 2 - tip.x, center.y * 2 - tip.y), strokeWidth = 4f)
                drawCircle(Color.White, radius = 6f, center = center)
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Компас считает направление по магнитному полю Земли, а не по спутникам. " +
                    "Рядом с динамиком, магнитом или металлическим столом он врёт — отнеси телефон на метр.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 8. Барометр —————

@Composable
fun LabBarometerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val values = rememberSensorValues(context, Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_UI)
    val series = history(120)
    var minim by remember { mutableStateOf(Float.MAX_VALUE) }
    var maxim by remember { mutableStateOf(0f) }

    LaunchedEffect(values) {
        val pressure = values?.firstOrNull() ?: return@LaunchedEffect
        series.push(pressure, 120)
        if (pressure < minim) minim = pressure
        if (pressure > maxim) maxim = pressure
        // За замер разница в 5 гПа — это уже смена погоды.
        if (maxim - minim >= 5f) Eggs.unlock(context, "storm")
    }

    if (values == null) {
        sensorMissing(
            "Барометр",
            "Барометра нет — высоту телефон берёт только по спутникам, и она менее точна.",
            onBack
        )
        return
    }

    GrapeScreen(title = "Барометр", subtitle = "давление и высота", onBack = onBack) {
        val pressure = values.firstOrNull() ?: 1013.25f
        SurfaceCard(borderColor = CardBorder) {
            Text(Deep.round1(pressure).toString() + " гПа", color = TextPrimary, fontSize = Metro.Type.pageTitle)
            Text(
                text = "высота по стандартной атмосфере: " + Deep.round1(DeviceLab.altitudeMeters(pressure)).toString() + " м",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        Strip(series.toList(), color = GrapeCyan, low = minim - 1f, high = maxim + 1f, baseline = 1013.25f)
        Spacer(Modifier.height(10.dp))
        InfoRow("Минимум за замер", Deep.round1(minim).toString() + " гПа")
        InfoRow("Максимум за замер", Deep.round1(maxim).toString() + " гПа")
        InfoRow("Разница высот", Deep.round1(abs(maxim - minim) * 8.3f).toString() + " м")
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "1 гПа разницы — это примерно 8 метров высоты. Подъём на один этаж меняет " +
                    "давление на 0,4 гПа, поэтому барометр чувствует лифт.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 9. Приближение —————

@Composable
fun LabProximityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val values = rememberSensorValues(context, Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_UI)
    var closedCount by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(false) }

    LaunchedEffect(values) {
        val near = values?.firstOrNull() ?: return@LaunchedEffect
        val closed = near < 3f
        if (closed != state) {
            state = closed
            if (closed) {
                closedCount++
                Haptics.tick(context, 16L)
            }
        }
    }

    if (values == null) {
        sensorMissing("Датчик приближения", "Без него экран не гаснет у уха — в звонках это заметно.", onBack)
        return
    }

    GrapeScreen(title = "Датчик приближения", subtitle = if (state) "закрыт" else "открыт", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(Metro.Shapes.tile)
                .background(if (state) Metro.Palette.Emerald.copy(alpha = 0.35f) else CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state) "ЗАКРЫТ" else "ОТКРЫТ",
                    color = if (state) GrapeGreen else TextPrimary,
                    fontSize = Metro.Type.pageTitle
                )
                Text(
                    text = "значение датчика: " + Deep.round1(values.firstOrNull() ?: 0f),
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("Срабатываний", closedCount.toString())
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Закрой верхнюю часть экрана ладонью: датчик у динамика увидит отражение " +
                    "инфракрасного света и переключится. Так телефон узнаёт, что он у уха.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 10. Люксметр —————

@Composable
fun LabLuxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val values = rememberSensorValues(context, Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_UI)
    val series = history(120)
    var minim by remember { mutableStateOf(Float.MAX_VALUE) }
    var maxim by remember { mutableStateOf(0f) }

    LaunchedEffect(values) {
        val lux = values?.firstOrNull() ?: return@LaunchedEffect
        series.push(lux, 120)
        if (lux < minim) minim = lux
        if (lux > maxim) maxim = lux
        // Полная темнота — тоже пасхалка: такое место надо ещё поискать.
        if (lux < 0.5f) Eggs.unlock(context, "dark0")
    }

    if (values == null) {
        sensorMissing("Люксметр", "Датчика света нет: яркостью управляет только рука.", onBack)
        return
    }

    GrapeScreen(title = "Люксметр", subtitle = "свет в комнате", onBack = onBack) {
        val lux = values.firstOrNull() ?: 0f
        SurfaceCard(borderColor = CardBorder) {
            Text(lux.roundToInt().toString() + " лк", color = TextPrimary, fontSize = Metro.Type.pageTitle)
            Text(
                text = when {
                    lux < 5f -> "темно, как ночью в комнате"
                    lux < 50f -> "приглушённый свет"
                    lux < 300f -> "комната с вечерним светом"
                    lux < 1_000f -> "обычная комната днём"
                    lux < 10_000f -> "пасмурная улица"
                    else -> "яркое солнце"
                },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        Strip(series.toList(), color = Metro.Palette.Amber, low = 0f, high = maxOf(500f, maxim))
        Spacer(Modifier.height(10.dp))
        InfoRow("Минимум", minim.roundToInt().toString() + " лк")
        InfoRow("Максимум", maxim.roundToInt().toString() + " лк")
        InfoRow("Отклик автoяркости", if (maxim > 50f) "работает" else "работает, но света мало")
    }
}

// ————— 11. Шаги —————

@Composable
fun LabStepsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val counter = rememberSensorValues(context, Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_UI)
    val detector = rememberSensorValues(context, Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_UI)
    var base by remember { mutableStateOf<Float?>(null) }
    var live by remember { mutableIntStateOf(0) }

    LaunchedEffect(counter) {
        val total = counter?.firstOrNull() ?: return@LaunchedEffect
        if (base == null) base = total
    }
    LaunchedEffect(detector) {
        if (detector != null) {
            live++
            if (live >= 100) Eggs.unlock(context, "steps100")
        }
    }

    val totalNow = counter?.firstOrNull()
    if (totalNow == null && detector == null) {
        sensorMissing("Шаги", "Счётчика шагов нет — телефон не считает пройденное сам.", onBack)
        return
    }

    GrapeScreen(title = "Шаги", subtitle = "счётчик и датчик шага", onBack = onBack) {
        val total = totalNow
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = if (total != null) (total - (base ?: total)).roundToInt().toString() else live.toString(),
                color = TextPrimary,
                fontSize = Metro.Type.pageTitle
            )
            Text(
                text = "шагов с момента открытия экрана · всего с загрузки: " +
                    (total?.roundToInt()?.toString() ?: "нет счётчика"),
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("Шагов за сеанс", live.toString())
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Счётчик шагов работает даже в кармане и живёт в отдельном чипе: он не " +
                    "тратит батарею процессора и обнуляется только при перезагрузке.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 12. Память и хранилище —————

@Composable
fun LabMemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var memory by remember { mutableStateOf(Deep.memory()) }
    var storage by remember { mutableStateOf(Deep.storage(context)) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            memory = Deep.memory()
            storage = Deep.storage(context)
            tick++
            delay(1_500)
        }
    }

    GrapeScreen(title = "Память и диск", subtitle = "использовано " + memory.usedPercent + "%", onBack = onBack) {
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = memory.usedMb.toString() + " из " + memory.totalMb + " МБ",
                color = TextPrimary,
                fontSize = Metro.Type.pageTitle
            )
            Text("оперативная память", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(8.dp))
            BarIndicator(
                percent = memory.usedPercent,
                brush = Brush.horizontalGradient(listOf(GrapeCyan, Metro.Palette.Violet)),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Свободно", memory.freeMb.toString() + " МБ")
            if (memory.swapTotalMb > 0) {
                InfoRow("Подкачка", (memory.swapTotalMb - memory.swapFreeMb).toString() + " / " + memory.swapTotalMb + " МБ")
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(Deep.round1(storage.totalGb).toString() + " ГБ всего", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Text("свободно " + Deep.round1(storage.freeGb).toString() + " ГБ", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(8.dp))
            BarIndicator(
                percent = ((storage.totalGb - storage.freeGb) / storage.totalGb * 100f).roundToInt().coerceIn(0, 100),
                brush = Brush.horizontalGradient(listOf(GrapeGreen, Metro.Palette.Amber)),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Занято приложением", storage.appMb.toString() + " МБ")
            InfoRow("Из них кэш", storage.cacheMb.toString() + " МБ")
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Подкачка (swap) — это кусок диска, который система использует как медленную " +
                    "память. Если её много — телефон начинает подтормаживать при переключении приложений.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 13. Латентность экрана —————

@Composable
fun LabLatencyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var inputMs by remember { mutableStateOf(0f) }
    var totalMs by remember { mutableStateOf(0f) }
    var lastTouch by remember { mutableStateOf(0L) }
    val frameHistory = history(90)

    DisposableEffect(Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val input = metrics.getMetric(android.view.FrameMetrics.INPUT_HANDLING_DURATION) / 1_000_000f
            val total = metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION) / 1_000_000f
            if (input > 0f) inputMs = input
            if (total > 0f) {
                totalMs = total
                frameHistory.push(total, 90)
                // Пасхалка «восемь и три»: кадр ровно в 8,3 мс — это честные 120 Гц.
                if (total in 7.6f..9.0f) Eggs.unlock(context, "latency8")
            }
        }
        // Метрики кадров доступны без прав: система сама отдаёт длительности шагов кадра.
        LatencyBridge.attach(context, handler, listener)
        onDispose { LatencyBridge.detach() }
    }

    GrapeScreen(title = "Латентность экрана", subtitle = "касание → кадр", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(Metro.Shapes.tile)
                .background(if (taps % 2 == 0) Metro.Palette.Cobalt else Metro.Palette.Magenta)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    taps++
                    lastTouch = System.nanoTime()
                    Haptics.tick(context, 12L)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("нажми сюда", color = Color.White, fontSize = Metro.Type.pageSubtitle)
                Text(
                    text = "касаний: " + taps,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(Deep.round1(inputMs).toString() + " мс", color = GrapeCyan, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                    Text("обработка касания", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
                Column(Modifier.weight(1f)) {
                    Text(Deep.round1(totalMs).toString() + " мс", color = GrapeGreen, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                    Text("кадр целиком", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (totalMs in 0.1f..7f) "120 Гц" else if (totalMs < 18f) "60 Гц" else "низко",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium)
                    )
                    Text("по времени кадра", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (frameHistory.isNotEmpty()) {
            Strip(frameHistory.toList(), color = Metro.Palette.Amber, low = 0f, high = 33f, baseline = 8.3f)
            Spacer(Modifier.height(10.dp))
        }
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Эти числа отдаёт сама система через метрики кадров: сколько миллисекунд " +
                    "проходит от касания до готового кадра. 8,3 мс — это честные 120 Гц.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————— 14. Мультитач-поле —————

@Composable
fun LabMultiTouchScreen(onBack: () -> Unit) {
    var points by remember { mutableStateOf(0) }
    var record by remember { mutableIntStateOf(0) }

    GrapeScreen(title = "Мультитач", subtitle = "рекорд: " + record + " пальцев", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(Metro.Shapes.tile)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Metro.Shapes.tile)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            points = pressed
                            if (pressed > record) record = pressed
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(points.toString(), color = TextPrimary, fontSize = Metro.Type.pageTitle)
                Text("пальцев на экране", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Положи сразу несколько пальцев и веди ими: телефон покажет, сколько касаний " +
                    "он различает. Дёшевые матрицы честно держат 5, хорошие — 10.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("Сейчас", points.toString())
        InfoRow("Лучший результат", record.toString())
    }
}

// ————— 15. Мерцание (ШИМ) —————

@Composable
fun LabFlickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<com.gsynk.app.core.Flicker.Report?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hz by remember { mutableIntStateOf(240) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(running, hz) {
        if (!running) return@LaunchedEffect
        // Пасхалка «предел мерцания»: 960 Гц — самая высокая частота в списке.
        if (hz >= 960) Eggs.unlock(context, "flick960")
        var last = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now
            phase = (phase + dt * hz) % 2f
            delay(4)
        }
    }

    GrapeScreen(title = "Мерцание экрана", subtitle = "ШИМ и стробоскоп", onBack = onBack) {
        SectionTitle("Сравнение глазами")
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(Metro.Shapes.tile)
                .background(if (phase < 1f) Metro.Palette.White else Metro.Palette.Gray)
        ) {
            Text(
                text = "полоса мигает на " + hz + " Гц",
                color = if (phase < 1f) Color.Black else Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            listOf(60, 120, 240, 480, 960).forEach { value ->
                SortChip(value.toString() + " Гц", hz == value) { hz = value }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (running) "Стоп" else "Мигать",
                onClick = { running = !running },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Сброс",
                onClick = { hz = 240; running = false },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Полоса мигает по-настоящему: на 240–960 Гц ты увидишь не мигание, а полосы " +
                "или «шлейф» — так работает стробоскопический эффект от ШИМ.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Замер камерой")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "Второй способ — посмотреть на свой экран через камеру: она снимет картинку " +
                    "с фиксированной выдержкой и посчитает частоту мерцания по кадрам.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (busy) "Снимаем…" else "Замерить камерой",
                onClick = {
                    busy = true
                    error = null
                    report = null
                    scope.launch {
                        runCatching { com.gsynk.app.core.Flicker.measure(context) }
                            .onSuccess { report = it }
                            .onFailure { error = it.message ?: "камера не ответила" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        report?.let { r ->
            Spacer(Modifier.height(10.dp))
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
                Text(
                    text = if (r.hz > 0f) Deep.round1(r.hz).toString() + " Гц" else "периодичности не видно",
                    color = TextPrimary,
                    fontSize = Metro.Type.pageSubtitle
                )
                Text(r.note, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                Spacer(Modifier.height(6.dp))
                InfoRow("Кадров снято", r.frames.toString())
                InfoRow("Глубина мерцания", (r.depth * 100f).roundToInt().toString() + "%")
                InfoRow("Длительность", r.millis.toString() + " мс")
                Spacer(Modifier.height(8.dp))
                Strip(r.samples, color = GrapeCyan, low = r.samples.minOrNull() ?: 0f, high = (r.samples.maxOrNull() ?: 1f))
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Metro.Palette.Red, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
        }

        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Как читать результат", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "· ниже 100 Гц — глазам тяжело, некоторые видят полосы;\n" +
                    "· 240–1920 Гц — обычная ШИМ современных AMOLED;\n" +
                    "· выше 2000 Гц — мерцания практически не видно.\n\n" +
                    DeviceLab.SAFETY_NOTE,
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

/** Мост к метрикам кадров: их можно привязать только к живому окну. */
object LatencyBridge {
    private var window: android.view.Window? = null
    private var listener: android.view.Window.OnFrameMetricsAvailableListener? = null

    fun attach(
        context: Context,
        handler: android.os.Handler,
        listener: android.view.Window.OnFrameMetricsAvailableListener
    ) {
        val activity = context as? android.app.Activity ?: return
        this.window = activity.window
        this.listener = listener
        runCatching { activity.window.addOnFrameMetricsAvailableListener(listener, handler) }
    }

    fun detach() {
        runCatching { listener?.let { window?.removeOnFrameMetricsAvailableListener(it) } }
        window = null
        listener = null
    }
}


// ————— единая дверь в расширенную лабораторию —————

/**
 * Открывает расширенный экран лаборатории по короткому имени маршрута.
 * Возвращает true, если такой экран есть: тогда вызывающему ничего делать не нужно.
 * Список намеренно плоский — все опыты лежат в одном месте, без под-под-пунктов.
 */
@Composable
fun LabExtraScreen(id: String, onBack: () -> Unit): Boolean {
    when (id) {
        // датчики и железо
        "sensors" -> { LabSensorGridScreen(onBack); return true }
        "cores" -> { LabCoresScreen(onBack); return true }
        "thermal" -> { LabThermalScreen(onBack); return true }
        "charge" -> { LabChargeScreen(onBack); return true }
        "gpu" -> { LabGpuScreen(onBack); return true }
        "shake" -> { LabShakeScreen(onBack); return true }
        "compass" -> { LabCompassScreen(onBack); return true }
        "baro" -> { LabBarometerScreen(onBack); return true }
        "prox" -> { LabProximityScreen(onBack); return true }
        "light" -> { LabLuxScreen(onBack); return true }
        "steps" -> { LabStepsScreen(onBack); return true }
        "memory" -> { LabMemoryScreen(onBack); return true }
        "latency" -> { LabLatencyScreen(onBack); return true }
        "multitouch" -> { LabMultiTouchScreen(onBack); return true }
        "pwm" -> { LabFlickerScreen(onBack); return true }
        // звук, моторика
        "oscope" -> { LabOscilloscopeScreen(onBack); return true }
        "spectrum" -> { LabSpectrumScreen(onBack); return true }
        "sweep" -> { LabSweepScreen(onBack); return true }
        "haptic" -> { LabHapticScreen(onBack); return true }
        // радио и периферия
        "ble" -> { LabBleRadarScreen(onBack); return true }
        "wifi" -> { LabWifiChannelsScreen(onBack); return true }
        "wifisig" -> { LabWifiSignalScreen(onBack); return true }
        "usb" -> { LabUsbScreen(onBack); return true }
        "nfcwrite" -> { LabNfcCardScreen(onBack); return true }
    }
    return false
}
