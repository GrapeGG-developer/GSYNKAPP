package com.gsynk.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.DeviceLab
import com.gsynk.app.core.Haptics
import com.gsynk.app.core.Melody
import com.gsynk.app.core.MelodyStore
import com.gsynk.app.core.Nearby
import com.gsynk.app.core.SineGenerator
import com.gsynk.app.core.ToneSynth
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.ScreenDimmer
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.GioBrush
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Лаборатория: низкоуровневые опыты с железом телефона.
 *
 * Всё, что здесь есть, крутит настоящие железки: панель экрана, вибромотор-катушку,
 * динамик, магнитный датчик, инфракрасный порт и системные экраны, спрятанные от
 * обычного пользователя. Собрано то, чего нет в магазинах приложений, но сделано
 * так, чтобы телефон нельзя было испортить: загрузчик, прошивка и разделы не трогаются.
 */
@Composable
fun LabScreen(onBack: () -> Unit, initial: String? = null) {
    val context = LocalContext.current
    val profile = remember { DeviceProfile.detect(context) }
    var tool by remember { mutableStateOf(initial) }
    var dimmerPercent by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { ScreenDimmer.hide() }
    }

    // Расширенные экраны GIOUI живут в отдельных файлах: сначала спрашиваем у них.
    if (tool != null && LabExtraScreen(tool!!, onBack = { tool = null })) return

    when (tool) {
        "strobe" -> StrobeScreen(onBack = { tool = null })
        "screen" -> ScreenTestScreen(onBack = { tool = null })
        "sensors" -> SensorsScreen(onBack = { tool = null })
        "coils" -> CoilsScreen(onBack = { tool = null })
        "music" -> ComposerScreen(onBack = { tool = null })
        "tone" -> ToneLabScreen(onBack = { tool = null })
        "metal" -> InductionScreen(onBack = { tool = null })
        "torch" -> TorchStrobeScreen(onBack = { tool = null })
        "codes" -> DialCodesScreen(onBack = { tool = null })
        "ir" -> if (DeviceLab.hasIr(context)) {
            IrRemoteScreen(onBack = { tool = null })
        } else {
            GrapeScreen(title = "ИК-пульт", onBack = { tool = null }) {
                SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.5f)) {
                    Text("Инфракрасного излучателя нет", color = GrapeRed, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "На этом телефоне нет ИК-порта. Он есть у Poco X3 Pro и большинства " +
                            "старых Xiaomi — там ПУЛЬТ появится сам.",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
            }
        }
    }
    if (tool != null) return

    GrapeScreen(
        title = "Лаборатория",
        subtitle = "железо телефона без прикрас · ${profile.tierLabel}",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.45f)) {
            Text("Безопасно и возвращается назад", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(DeviceLab.SAFETY_NOTE, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(12.dp))

        TileRow {
            NavTile(
                title = "Стробоскоп",
                subtitle = "мигание экраном, 4 частоты",
                iconRes = com.gsynk.app.R.drawable.ic_bolt,
                brush = GioBrush.Amber,
                modifier = Modifier.weight(1f),
                onClick = { tool = "strobe" }
            )
            NavTile(
                title = "Фонарик-строб",
                subtitle = "вспышка камеры в такт",
                iconRes = com.gsynk.app.R.drawable.ic_brightness,
                brush = GioBrush.Ink,
                modifier = Modifier.weight(1f),
                onClick = { tool = "torch" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Экран",
                subtitle = "AMOLED, ШИМ, мультитач",
                iconRes = com.gsynk.app.R.drawable.ic_tune,
                brush = GioBrush.Violet,
                modifier = Modifier.weight(1f),
                onClick = { tool = "screen" }
            )
            NavTile(
                title = "Датчики",
                subtitle = "все, что есть живьём",
                iconRes = com.gsynk.app.R.drawable.ic_schedule,
                brush = GioBrush.Cyan,
                modifier = Modifier.weight(1f),
                onClick = { tool = "sensors" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "Катушки",
                subtitle = "вибромотор и звук",
                iconRes = com.gsynk.app.R.drawable.ic_volume,
                brush = GioBrush.Pink,
                modifier = Modifier.weight(1f),
                onClick = { tool = "coils" }
            )
            NavTile(
                title = "Индукция",
                subtitle = "металл, поля, зарядка",
                iconRes = com.gsynk.app.R.drawable.ic_shield,
                brush = GioBrush.Blue,
                modifier = Modifier.weight(1f),
                onClick = { tool = "metal" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            // Списки «скрытых экранов Android» и разделов HyperOS убраны по итогам отчёта
            // с телефона: на Android 16 и HyperOS они не открываются и ничего не прячут.
            // Вместо них — то, что действительно работает с железом.
            NavTile(
                title = "Композитор",
                subtitle = "музыка из частот",
                iconRes = com.gsynk.app.R.drawable.ic_volume,
                brush = GioBrush.Green,
                modifier = Modifier.weight(1f),
                onClick = { tool = "music" }
            )
            NavTile(
                title = "Камертон",
                subtitle = "держу — играет частота",
                iconRes = com.gsynk.app.R.drawable.ic_tune,
                brush = GioBrush.Amber,
                modifier = Modifier.weight(1f),
                onClick = { tool = "tone" }
            )
        }
        Spacer(Modifier.height(11.dp))
        TileRow {
            NavTile(
                title = "ИК-пульт",
                subtitle = if (DeviceLab.hasIr(context)) "телевизор одним касанием" else "нет излучателя",
                iconRes = com.gsynk.app.R.drawable.ic_bolt,
                brush = if (DeviceLab.hasIr(context)) GioBrush.Red else GioBrush.Ink,
                modifier = Modifier.weight(1f),
                onClick = { tool = "ir" }
            )
            NavTile(
                title = "Коды",
                subtitle = "инженерные меню и тесты",
                iconRes = com.gsynk.app.R.drawable.ic_copy,
                brush = GioBrush.Ink,
                modifier = Modifier.weight(1f),
                onClick = { tool = "codes" }
            )
        }

        // ————— расширенная лаборатория GIOUI —————
        Spacer(Modifier.height(18.dp))
        SectionTitle("Ещё опыты с железом")
        Spacer(Modifier.height(6.dp))
        LabExtrasGrid(onOpen = { tool = it })

        // ————— затемнение ниже системного минимума: настоящая AMOLED-регулировка —————
        Spacer(Modifier.height(16.dp))
        SectionTitle("AMOLED-затемнение")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Штатная яркость Android не опускается ниже десятой части, а ночью хочется темнее. " +
                "Слой гасит свет поверх всего экрана: чёрные пиксели AMOLED не светятся, поэтому " +
                "телефон ещё и ест меньше.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = if (dimmerPercent > 0) GrapeGreen.copy(alpha = 0.5f) else CardBorder) {
            InfoRow("Затемнение", dimmerPercent.toString() + " проц.", if (dimmerPercent > 0) GrapeGreen else TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 20, 40, 60, 80).forEach { value ->
                    SortChip(
                        label = if (value == 0) "выкл" else value.toString(),
                        selected = dimmerPercent == value
                    ) {
                        dimmerPercent = value
                        ScreenDimmer.apply(context, value)
                        AppLog.event("Лаборатория", "затемнение экрана: $value проц.")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Что открылось для этого телефона", color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            InfoRow("Модель", profile.model)
            InfoRow("Экран", profile.width.toString() + "×" + profile.height)
            InfoRow("Android", profile.androidLabel + " (SDK " + profile.androidSdk + ")")
            InfoRow("Уровень", profile.tierLabel, GrapeVioletSoft)
            InfoRow("ИК-порт", if (profile.hasIr) "есть" else "нет", if (profile.hasIr) GrapeGreen else TextDim)
            InfoRow("NFC", if (profile.hasNfc) "есть" else "нет", if (profile.hasNfc) GrapeGreen else TextDim)
            InfoRow("HyperOS", if (profile.isHyperOs) "да" else "нет", if (profile.isHyperOs) GrapeAmber else TextDim)
            Spacer(Modifier.height(6.dp))
            Text(profile.reason, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Стробоскоп: мигание экраном
// —————————————————————————————————————————————————

@Composable
private fun StrobeScreen(onBack: () -> Unit) {
    val strobeContext = LocalContext.current
    var strobe by remember { mutableStateOf(DeviceLab.strobes[1]) }
    var palette by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    val colors = DeviceLab.strobeColors[palette].second

    LaunchedEffect(running, strobe, palette) {
        if (!running) return@LaunchedEffect
        var last = 0L
        val period = (1000f / DeviceLab.strobeClamp(strobe.hertz)).toLong().coerceAtLeast(30L)
        while (true) {
            withFrameNanos { now ->
                if (now - last >= period * 1_000_000L) {
                    last = now
                    frame++
                }
            }
        }
    }

    if (running) {
        val color = Color(colors[frame % colors.size])
        Box(
            Modifier
                .fillMaxSize()
                .background(color)
                .clickable { running = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "коснитесь, чтобы остановить",
                color = if (color == Color.White) Color.Black else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal),
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }
        return
    }

    GrapeScreen(title = "Стробоскоп", subtitle = "мигание экраном · " + strobe.label, onBack = onBack) {
        SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.5f)) {
            Text("Осторожно", color = GrapeAmber, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Мелькание света может быть неприятно людям с чувствительностью к мерцанию. " +
                    "Не направляйте экран в глаза и не держите вспышки дольше пары минут.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Частота")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DeviceLab.strobes.forEach { option ->
                SurfaceCard(
                    borderColor = if (option == strobe) GrapeAmber.copy(alpha = 0.6f) else CardBorder,
                    onClick = { strobe = option }
                ) {
                    Text(option.label + " · " + option.hertz.roundToInt() + " Гц", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                    Text(option.hint, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("Цвета")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceLab.strobeColors.forEachIndexed { index, (name, _) ->
                SortChip(label = name.substringBefore(" —"), selected = index == palette) { palette = index }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Запустить мигание",
            onClick = {
                running = true
                AppLog.event("Лаборатория", "стробоскоп: " + strobe.label)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Экран: AMOLED, ШИМ, мультитач
// —————————————————————————————————————————————————

@Composable
private fun ScreenTestScreen(onBack: () -> Unit) {
    var test by remember { mutableStateOf<DeviceLab.ScreenTest?>(null) }

    when (test) {
        DeviceLab.ScreenTest.DEAD_PIXELS -> FlatFieldsTest(onExit = { test = null })
        DeviceLab.ScreenTest.BURN_IN -> CheckerTest(onExit = { test = null })
        DeviceLab.ScreenTest.CLEAN -> PixelCleanTest(onExit = { test = null })
        DeviceLab.ScreenTest.GRADIENT -> GradientTest(onExit = { test = null })
        DeviceLab.ScreenTest.PWM -> PwmTest(onExit = { test = null })
        DeviceLab.ScreenTest.TOUCH -> MultiTouchTest(onExit = { test = null })
        DeviceLab.ScreenTest.GHOST -> GhostTest(onExit = { test = null })
        null -> GrapeScreen(
            title = "Проверки экрана",
            subtitle = "то, что обычно ищут в браузере",
            onBack = onBack
        ) {
            DeviceLab.ScreenTest.entries.forEach { option ->
                SurfaceCard(borderColor = CardBorder, onClick = { test = option }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(option.hint, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
                }
                Spacer(Modifier.height(9.dp))
            }
            Text(
                text = "Каждая проверка — на весь экран: коснитесь, чтобы вернуться.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FlatFieldsTest(onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val (name, color) = DeviceLab.flatFields[index]
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(color))
            .clickable {
                if (index + 1 < DeviceLab.flatFields.size) index++ else onExit()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = name + " · далее " + (index + 1) + " / " + DeviceLab.flatFields.size,
            color = if (color == 0xFFFFFFFF.toInt()) Color.Black else Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal),
            modifier = Modifier.padding(bottom = 40.dp)
        )
    }
}

@Composable
private fun CheckerTest(onExit: () -> Unit) {
    var dark by remember { mutableStateOf(true) }
    Canvas(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { dark = !dark }
    ) {
        val cell = size.minDimension / 12f
        for (row in 0 until 24) {
            for (col in 0 until 12) {
                val even = (row + col) % 2 == 0
                val paint = if (dark) (if (even) Color.White else Color.Black) else (if (even) Color(0xFF1A1A1A) else Color.White)
                drawRect(
                    color = paint,
                    topLeft = Offset(col * cell, row * cell),
                    size = androidx.compose.ui.geometry.Size(cell, cell)
                )
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.padding(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SortChip("инвертировать", selected = false) { dark = !dark }
            SortChip("выход", selected = false) { onExit() }
        }
    }
}

@Composable
private fun PixelCleanTest(onExit: () -> Unit) {
    var frame by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(running) {
        while (running) {
            delay(90L)
            frame++
        }
    }
    val palette = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        0xFF00FFFF.toInt(), 0xFFFF00FF.toInt(), 0xFFFFFF00.toInt(), 0xFF000000.toInt()
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(palette[frame % palette.size]))
            .clickable { running = !running },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = if (running) "перебор цветов · коснитесь для паузы" else "пауза · коснитесь для продолжения",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal),
            modifier = Modifier.padding(bottom = 40.dp)
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Row(Modifier.padding(16.dp)) {
            SortChip("выход", selected = false) { onExit() }
        }
    }
}

@Composable
private fun GradientTest(onExit: () -> Unit) {
    Canvas(
        Modifier
            .fillMaxSize()
            .clickable { onExit() }
    ) {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Black, Color(0xFF141021), Color(0xFF2B2140), Color(0xFF3B2F5B), Color.White)
            )
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Color.Black, Color(0xFF7C3AED), Color.White, Color(0xFF22D3EE), Color.Black)
            ),
            topLeft = Offset(0f, size.height * 0.55f),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.12f)
        )
    }
}

@Composable
private fun PwmTest(onExit: () -> Unit) {
    var bars by remember { mutableIntStateOf(8) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExit() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.height / (bars + 2)
            for (index in 0 until bars) {
                val width = size.width * (0.12f + 0.82f * (index.toFloat() / bars))
                drawRect(
                    color = Color.White.copy(alpha = 0.10f + 0.9f * (index.toFloat() / bars)),
                    topLeft = Offset(size.width * 0.05f, step * (index + 1)),
                    size = androidx.compose.ui.geometry.Size(width, step * 0.62f)
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Поставьте системную яркость на минимум: если полосы «дрожат» или рябят — " +
                    "у панели заметное мерцание (ШИМ). Это чувствуют глаза при долгом чтении.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8, 12).forEach { value ->
                    SortChip(label = value.toString() + " полос", selected = bars == value) { bars = value }
                }
                SortChip("выход", selected = false) { onExit() }
            }
        }
    }
}

@Composable
private fun MultiTouchTest(onExit: () -> Unit) {
    val points = remember { mutableStateMapOf<Long, Offset>() }
    var maxTouches by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0714))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        points.clear()
                        event.changes.forEach { change ->
                            if (change.pressed) points[change.id.value.toLong()] = change.position
                            change.consume()
                        }
                        if (points.size > maxTouches) maxTouches = points.size
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            points.forEach { (id, position) ->
                drawCircle(GrapeCyan.copy(alpha = 0.9f), radius = 46f, center = position)
                drawCircle(Color.White, radius = 46f, center = position, style = Stroke(width = 3f))
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 3f, center = position)
                drawContext.canvas.nativeCanvas.drawText(
                    id.toString(),
                    position.x + 52f,
                    position.y,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 34f
                    }
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Одновременно касаний: " + points.size.toString() + " · максимум: $maxTouches",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Положите ладонь: видно, сколько пальцев панель слышит на самом деле и где есть мёртвые зоны.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            SortChip("выход", selected = false) { onExit() }
        }
    }
}

@Composable
private fun GhostTest(onExit: () -> Unit) {
    var position by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            position += 0.004f
            if (position > 1.2f) position = -0.2f
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .clickable { onExit() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(6) { index ->
                val y = size.height * ((position + index * 0.14f) % 1f)
                drawRect(
                    color = Color.White.copy(alpha = 0.06f + 0.05f * index),
                    topLeft = Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(size.width, 26f)
                )
            }
            drawRect(
                color = Color(0xFF808080),
                topLeft = Offset(size.width * 0.1f, size.height * 0.42f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.16f)
            )
        }
        Text(
            text = "Серая плита едет медленно: если за ней тянется светлый шлейф — у панели медленный отклик",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        )
    }
}

// ————————————————————————————————————————————————
//  Датчики: всё, что есть в телефоне, живьём
// —————————————————————————————————————————————————

@Composable
private fun SensorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val values = remember { mutableStateMapOf<Int, String>() }
    val sensors = remember { manager?.getSensorList(Sensor.TYPE_ALL).orEmpty() }

    DisposableEffect(manager) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                values[event.sensor.type] = event.values.joinToString(" ") { value ->
                    String.format("%.2f", value)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // Слушаем только те датчики, на которые обычному приложению дают право.
        // Приватные и «always-on» датчики Android 16 отдают лишь системе: попытка
        // подписки роняет процесс с SecurityException (был такой вылет из отчёта).
        val allowed = sensors.filterNot {
            DeviceLab.isSystemOnlySensor(it.type, it.name, it.stringType.orEmpty())
        }
        val skipped = sensors.size - allowed.size
        if (skipped > 0) {
            AppLog.event(
                "Лаборатория",
                "датчики: пропущено системных — " + skipped + " из " + sensors.size +
                    " (" + sensors.filterNot { it in allowed }.joinToString(", ") { it.name } + ")"
            )
        }
        allowed.forEach { sensor ->
            runCatching { manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        }
        onDispose { runCatching { manager?.unregisterListener(listener) } }
    }

    GrapeScreen(
        title = "Датчики",
        subtitle = "всего найдено: " + sensors.size,
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.5f)) {
            Text("Живые показания", color = GrapeCyan, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Значения обновляются прямо сейчас — датчиков в телефоне больше десятка, " +
                    "и большинство приложений показывают из них три-четыре.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        // ————— системные датчики: показываем, что они есть, но не читаем —————
        val systemOnly = sensors.filter {
            DeviceLab.isSystemOnlySensor(it.type, it.name, it.stringType.orEmpty())
        }
        if (systemOnly.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.45f)) {
                Text(
                    text = "Только для системы: " + systemOnly.size,
                    color = GrapeAmber,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Эти датчики (приватные и «always-on») Android 16 отдаёт только системным " +
                        "службам: подписка из обычного приложения запрещена и раньше роняла GSYNK. " +
                        "Показываем список — читать их нельзя ни одному приложению, и это правильно.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
                Spacer(Modifier.height(8.dp))
                systemOnly.take(12).forEach { sensor ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "• " + sensor.name,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val magnetic = sensors.firstOrNull { it.type == Sensor.TYPE_MAGNETIC_FIELD }
        val pressure = sensors.firstOrNull { it.type == Sensor.TYPE_PRESSURE }
        if (magnetic != null || pressure != null) {
            SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.4f)) {
                magnetic?.let { sensor ->
                    val raw = values[sensor.type] ?: "0 0 0"
                    val parts = raw.split(" ").mapNotNull { it.toFloatOrNull() }
                    if (parts.size >= 3) {
                        val strength = DeviceLab.magneticStrength(parts[0], parts[1], parts[2])
                        InfoRow("Магнитное поле", String.format("%.1f мкТл", strength), GrapeAmber)
                        InfoRow("Азимут", String.format("%.0f град.", DeviceLab.compassHeading(parts[0], parts[1])), GrapeCyan)
                    }
                }
                pressure?.let { sensor ->
                    val hpa = values[sensor.type]?.toFloatOrNull()
                    if (hpa != null) {
                        InfoRow("Давление", String.format("%.1f гПа", hpa), GrapeVioletSoft)
                        InfoRow("Высота", String.format("%.0f м", DeviceLab.altitudeMeters(hpa)), GrapeGreen)
                    }
                }
                val light = sensors.firstOrNull { it.type == Sensor.TYPE_LIGHT }
                light?.let { sensor ->
                    values[sensor.type]?.let { lux -> InfoRow("Освещённость", lux + " лк", GrapeGreen) }
                }
                val proximity = sensors.firstOrNull { it.type == Sensor.TYPE_PROXIMITY }
                proximity?.let { sensor ->
                    values[sensor.type]?.let { value ->
                        InfoRow("Приближение", if (value.startsWith("0")) "закрыт" else "открыт", GrapePink)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        sensors.forEach { sensor ->
            SurfaceCard(borderColor = CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(GrapeCyan)
                    ) { }
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(sensor.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                        Text(
                            text = "тип " + sensor.type + " · " + sensor.vendor + " · " +
                                String.format("%.1f мА", sensor.power) + " · " +
                                when (sensor.reportingMode) {
                                    Sensor.REPORTING_MODE_CONTINUOUS -> "непрерывный"
                                    Sensor.REPORTING_MODE_ON_CHANGE -> "по изменению"
                                    Sensor.REPORTING_MODE_ONE_SHOT -> "однократный"
                                    Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "по событию"
                                    else -> "неизвестно"
                                },
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                        Text(
                            text = values[sensor.type] ?: "нет данных",
                            color = GrapeGreen,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Катушки: вибромотор и звук
// —————————————————————————————————————————————————

@Composable
private fun CoilsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hertz by remember { mutableIntStateOf(8) }
    var toneHz by remember { mutableIntStateOf(440) }
    var sweep by remember { mutableStateOf(false) }

    fun buzz(pattern: LongArray) {
        // Раньше вибрация молчала на HyperOS: система глушит её без явного назначения.
        // Haptics играет как «сигнал» (USAGE_ALARM) и имеет запасные дорожки.
        val ok = Haptics.play(context, pattern, Haptics.Use.ALARM)
        if (!ok) AppLog.warn("Лаборатория", "вибромотор не ответил", null)
    }

    DisposableEffect(Unit) {
        onDispose { AutomationActions.stopSiren() }
    }

    GrapeScreen(
        title = "Катушки",
        subtitle = "вибромотор, динамик, индукция",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapePink.copy(alpha = 0.5f)) {
            Text("Вибромотор — это катушка", color = GrapePink, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Внутри телефона вибрирует грузик на валу, который толкает катушка индуктивности. " +
                    "Меняя форму импульсов, из неё можно выжать щелчок, сердцебиение, мотор или SOS.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceLab.Buzz.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { option ->
                        SurfaceCard(
                            modifier = Modifier.weight(1f),
                            borderColor = CardBorder,
                            onClick = {
                                buzz(DeviceLab.buzzPattern(option))
                                AppLog.event("Лаборатория", "вибро-шаблон: " + option.label)
                            }
                        ) {
                            Text(option.label, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                            Spacer(Modifier.height(2.dp))
                            Text(option.hint, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Ритм на катушке")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Удары в секунду: медленно — постукивание, быстро — жужжание. " +
                "На 16 ударах телефон «поёт» басом через стол.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = if (hertz > 0) GrapePink.copy(alpha = 0.5f) else CardBorder) {
            InfoRow("Частота ударов", hertz.toString() + " в секунду", GrapePink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 8, 12, 16).forEach { value ->
                    SortChip(label = value.toString(), selected = hertz == value) { hertz = value }
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = "Играть 3 секунды",
                onClick = { buzz(DeviceLab.buzzRhythm(hertz, 3)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Динамик как катушка")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Динамик — тоже катушка в магнитном поле. Тон-генератор делает чистые тоны, " +
                "развёртку и ультразвук: проверка на слух и «отпугиватель» для комаров.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(10.dp))
        SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.4f)) {
            InfoRow("Тон", toneHz.toString() + " Гц", GrapeCyan)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 220, 440, 1000, 8000, 17_000).forEach { value ->
                    SortChip(label = if (value >= 1000) (value / 1000).toString() + "к" else value.toString(), selected = toneHz == value) {
                        toneHz = value
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            AutomationActions.playTone(toneHz)
                            AppLog.event("Лаборатория", "тон " + toneHz + " Гц")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Играть 2 секунды", color = GrapeCyan, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            sweep = true
                            AutomationActions.playSweep()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (sweep) "развёртка идёт…" else "Развёртка 20→20к", color = GrapeAmber, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                }
            }
            Spacer(Modifier.height(10.dp))
            ToneHoldPanel(initialHz = toneHz.toDouble()) { hz ->
                AppLog.event("Лаборатория", "удержание: " + hz.toInt() + " Гц")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Осторожно с громкостью: ультразвук на 17 кГц и низкие тоны громко не слушайте — " +
                    "это нагрузка на динамик.",
                color = GrapeRed.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Индукция: металл, поля, беспроводная зарядка
// —————————————————————————————————————————————————

@Composable
private fun InductionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    // Берём датчик, только если он публичный: у Xiaomi бывает «магнитный always-on»,
    // подписка на который валит процесс (SecurityException из registerAonListener).
    val magnetic = remember {
        manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.takeIf {
            !DeviceLab.isSystemOnlySensor(it.type, it.name, it.stringType.orEmpty())
        }
    }
    var strength by remember { mutableFloatStateOf(0f) }
    var baseline by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var calibrated by remember { mutableStateOf(false) }

    DisposableEffect(magnetic) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = DeviceLab.magneticStrength(event.values[0], event.values[1], event.values[2])
                strength = value
                if (calibrated && value > peak) peak = value
            }

            override fun onAccuracyChanged(sensor: Sensor? , accuracy: Int) = Unit
        }
        magnetic?.let { manager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { runCatching { manager?.unregisterListener(listener) } }
    }

    val delta = DeviceLab.metalDelta(strength, baseline)
    GrapeScreen(
        title = "Индукция и поля",
        subtitle = "магнитометр как детектор",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.5f)) {
            Text("Как это работает", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "В телефоне есть датчик магнитного поля на эффекте Холла. Металл рядом меняет " +
                    "поле — это и есть металлоискатель. Беспроводная зарядка, динамик и вибромотор " +
                    "тоже создают поле: поднесите телефон к ним и увидите всплеск.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(borderColor = if (delta > 25f) GrapeGreen.copy(alpha = 0.6f) else CardBorder) {
            InfoRow("Сейчас", String.format("%.1f мкТл", strength), GrapeAmber)
            InfoRow("Фон", String.format("%.1f мкТл", baseline))
            InfoRow("Отклонение", String.format("%.1f мкТл", delta), if (delta > 25f) GrapeGreen else TextSecondary)
            InfoRow("Пик с замера", String.format("%.1f мкТл", peak), GrapePink)
            Spacer(Modifier.height(10.dp))
            BarIndicator(
                percent = (delta / 150f * 100f).roundToInt().coerceIn(0, 100),
                brush = GioBrush.Green
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip("взять фон", selected = false) {
                    baseline = strength
                    peak = strength
                    calibrated = true
                }
                SortChip("сброс", selected = false) {
                    baseline = 0f
                    peak = 0f
                    calibrated = false
                }
            }
            Text(
                text = when {
                    !calibrated -> "Возьмите фон вдали от металла — потом подносите телефон к предметам."
                    delta > 60f -> "Рядом точно металл или магнит: поле сильно изменилось."
                    delta > 25f -> "Что-то металлическое близко."
                    else -> "Пока спокойно — металла рядом нет."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Проверка катушки зарядки и динамика", color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Включите вибро-шаблон «Мотор» и держите телефон — датчик поймает поле катушки " +
                    "вибромотора. То же с беспроводной зарядкой: поднесите к ней телефон и увидите всплеск.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = "Тряхнуть катушкой и замерить",
                onClick = {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createWaveform(DeviceLab.buzzPattern(DeviceLab.Buzz.ENGINE), -1))
                        }
                    }
                    if (!calibrated) {
                        baseline = strength
                        calibrated = true
                    }
                    peak = strength
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Фонарик-стробоскоп
// —————————————————————————————————————————————————

@Composable
private fun TorchStrobeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hertz by remember { mutableIntStateOf(3) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running, hertz) {
        if (!running) return@LaunchedEffect
        var on = false
        while (true) {
            on = !on
            AutomationActions.setTorch(context, on)
            delay((1000L / hertz.coerceIn(1, 10)) / 2)
        }
    }

    DisposableEffect(Unit) {
        onDispose { AutomationActions.setTorch(context, false) }
    }

    GrapeScreen(title = "Фонарик-стробоскоп", subtitle = "$hertz вспышек в секунду", onBack = onBack) {
        SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.5f)) {
            Text(
                text = "Вспышка камеры мигает с выбранной частотой. Так проверяют экран второго телефона " +
                    "на мерцание и делают стробоскоп для съёмки. Долго не держите — вспышка греется.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 5, 8).forEach { value ->
                SortChip(label = value.toString() + " Гц", selected = hertz == value) { hertz = value }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (running) "Остановить" else "Запустить вспышки",
            onClick = { running = !running },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Коды, ИК-пульт
// ————————————————————————————————————————————————

@Composable
private fun DialCodesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    GrapeScreen(title = "Инженерные коды", subtitle = "открываются в звонилке", onBack = onBack) {
        SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
            Text(
                text = "Нажмите код — откроется звонилка с уже набранным номером. Дальше достаточно " +
                    "нажать вызов: это штатные тесты железа, они ничего не меняют.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        DeviceLab.dialCodes.forEach { (code, hint) ->
            SurfaceCard(borderColor = CardBorder, onClick = { DeviceLab.openDialer(context, code) }) {
                Text(code, color = GrapeGreen, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(2.dp))
                Text(hint, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Инфракрасный пульт
// —————————————————————————————————————————————————

@Composable
private fun IrRemoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var brand by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("Наведите телефон на телевизор и жмите кнопки") }

    GrapeScreen(
        title = "ИК-пульт",
        subtitle = "встроенный излучатель",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.5f)) {
            Text("Настоящий пульт в телефоне", color = GrapeRed, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Xiaomi ставит в дешёвые модели инфракрасный излучатель — им управляют техникой. " +
                    "Коды протокола NEC считаются прямо на телефоне, никакой интернет не нужен.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Производитель")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            DeviceLab.tvBrands.forEachIndexed { index, (name, _) ->
                SortChip(label = name, selected = index == brand) { brand = index }
            }
        }
        Spacer(Modifier.height(14.dp))
        val deviceCode = DeviceLab.tvBrands[brand].second
        DeviceLab.tvKeys.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val nec = key.nec ?: return@forEach
                    Box(
                        Modifier
                            .weight(1f)
                            .height(66.dp)
                            .clip(RectangleShape)
                            .background(GioBrush.Ink)
                            .border(1.dp, ChipBorder, RectangleShape)
                            .clickable {
                                val candidate = DeviceLab.Ir.nec(nec.first, nec.second)
                                val direct = DeviceLab.transmit(context, candidate)
                                val text = if (direct) {
                                    key.title + " отправлено"
                                } else {
                                    "Не вышло: " + key.title
                                }
                                log = text
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key.title, color = TextPrimary, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(8.dp))
        SurfaceCard(borderColor = CardBorder) {
            InfoRow("Выбрано", DeviceLab.tvBrands[brand].first, GrapeCyan)
            InfoRow("Адрес кода", "0x" + deviceCode.toString(16).uppercase(), GrapeVioletSoft)
            Spacer(Modifier.height(6.dp))
            Text(log, color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ————————————————————————————————————————————————
//  Тон: кнопка «держу — играет», музыка из частот
// —————————————————————————————————————————————————

/**
 * Кнопка, которая держит частоту, пока палец на экране.
 *
 * Пока держишь — [ToneSynth] тянет непрерывную ноту, а шкала под пальцем меняет её на ходу
 * (переносишь палец вправо — выше). Отпустил — тишина. Рядом «в мотив»: текущая частота
 * уходит в список, из которого потом собирается музыка.
 */
@Composable
fun ToneHoldPanel(initialHz: Double, onHold: (Double) -> Unit = {}) {
    val localContext = LocalContext.current
    var hz by remember { mutableDoubleStateOf(initialHz) }
    val generator = remember { SineGenerator() }
    var holding by remember { mutableStateOf(false) }
    var level by remember { mutableIntStateOf(55) }
    var collected by remember { mutableStateOf(listOf<Double>()) }

    fun stopHold() {
        if (holding) {
            ToneSynth.stop()
            holding = false
        }
    }

    DisposableEffect(Unit) { onDispose { ToneSynth.stop() } }

    SurfaceCard(borderColor = if (holding) GrapeGreen.copy(alpha = 0.6f) else GrapeCyan.copy(alpha = 0.4f)) {
        Text("Держу — играет", color = if (holding) GrapeGreen else GrapeCyan, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (holding) {
                "звучит " + hz.toInt() + " Гц • нота " + Melody.nameOf(hz) + " • тяните палец в сторону, чтобы вести"
            } else {
                "Нажмите и держите: пока палец на кнопке, идёт ровный тон. Двигайте палец по кнопке — " +
                    "частота едет без разрывов."
            },
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RectangleShape)
                .background(if (holding) GrapeGreen.copy(alpha = 0.18f) else CardBg)
                .border(1.dp, if (holding) GrapeGreen else ChipBorder, RectangleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        holding = true
                        ToneSynth.start(hz, level)
                        onHold(hz)
                        var position = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            position = change.position
                            val percent = (position.x / size.width * 100f).coerceIn(0f, 100f)
                            hz = generator.frequencyAtPercent(percent.toInt())
                            ToneSynth.setFrequency(hz)
                        }
                        stopHold()
                        ToneSynth.setFrequency(hz)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (holding) "звучит ${hz.toInt()} Гц" else "ДЕРЖАТЬ",
                color = if (holding) GrapeGreen else TextPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 25, 50).forEach { step ->
                SortChip(label = "+" + step, selected = false) {
                    hz = ToneSynth.clampHz(hz + step)
                    if (holding) ToneSynth.setFrequency(hz)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 25, 50).forEach { step ->
                SortChip(label = "−" + step, selected = false) {
                    hz = ToneSynth.clampHz(hz - step)
                    if (holding) ToneSynth.setFrequency(hz)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoRow("Частота", "${hz.toInt()} Гц • ${Melody.nameOf(hz)}", GrapeCyan)
        InfoRow("Громкость", "$level проц.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 55, 80, 100).forEach { value ->
                SortChip(label = value.toString(), selected = level == value) {
                    level = value
                    ToneSynth.setVolume(value)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .clickable {
                        collected = (collected + hz).takeLast(64)
                        AppLog.event("Лаборатория", "в мотив: " + hz.toInt() + " Гц")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("В мотив (${collected.size})", color = GrapeAmber, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .clickable {
                        if (collected.isNotEmpty()) {
                            ToneSynth.stop()
                            ToneSynth.playSequence(Melody.fromFrequencies(collected, 320L))
                            AppLog.event("Лаборатория", "мотив из " + collected.size + " частот")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Играть мотив", color = GrapeGreen, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .clickable { collected = emptyList() },
                contentAlignment = Alignment.Center
            ) {
                Text("Очистить мотив", color = TextDim, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .clickable {
                        val text = Melody.encode(Melody.fromFrequencies(collected, 320L))
                        runCatching {
                            val manager = localContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            manager?.setPrimaryClip(android.content.ClipData.newPlainText("GSYNK-мотив", text))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Мотив в буфер", color = TextDim, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            }
        }
    }
}

/** Камертон: одна частота, которую удобно «вести» и запоминать. */
@Composable
private fun ToneLabScreen(onBack: () -> Unit) {
    GrapeScreen(title = "Камертон", subtitle = "удержание даёт ровный тон", onBack = onBack) {
        ToneHoldPanel(initialHz = 440.0) { }
        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = GrapeAmber.copy(alpha = 0.4f)) {
            Text("Что это даёт", color = GrapeAmber, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Катушка динамика работает как излучатель: можно проверить слух на высоких " +
                    "частотах, найти резонанс корпуса, «спеть» телефоном или собрать из частот мотив. " +
                    "Держите кнопку — тон идёт ровно, ведите палец — частота едет плавно.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

/** Композитор: пианино из частот, запись, готовые мотивы, сохранение и передача. */
@Composable
private fun ComposerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var octave by remember { mutableIntStateOf(4) }
    var mode by remember { mutableStateOf("мажор") }
    var recording by remember { mutableStateOf(false) }
    var melody by remember { mutableStateOf(listOf<Melody.Note>()) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(MelodyStore.load(context)) }
    var status by remember { mutableStateOf("") }
    val scale = remember(octave, mode) { Melody.scale("C" + octave, mode, 12) }

    DisposableEffect(Unit) { onDispose { ToneSynth.stop() } }

    fun remember4(notes: List<Melody.Note>) {
        melody = (melody + notes).takeLast(96)
    }

    GrapeScreen(title = "Композитор", subtitle = "музыка из частот", onBack = onBack) {
        SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.45f)) {
            Text("Клавиши", color = GrapeGreen, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (recording) "Запись идёт: каждое нажатие попадёт в мотив" else
                    "Нажмите клавишу — играет частота. Включите запись, и из нажатий соберётся мотив.",
                color = if (recording) GrapeRed else TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 4, 5, 6).forEach { value ->
                    SortChip(label = "окт " + value, selected = octave == value) {
                        octave = value
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("мажор", "минор", "пентатоника", "целотон").forEach { value ->
                    SortChip(label = value, selected = mode == value) { mode = value }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scale.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { noteHz ->
                            val now = System.currentTimeMillis()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RectangleShape)
                                    .background(CardBg)
                                    .border(1.dp, ChipBorder, RectangleShape)
                                    .clickable {
                                        ToneSynth.stop()
                                        ToneSynth.playSequence(listOf(Melody.Note(noteHz, 320L)))
                                        if (recording) {
                                            val gap = (now - lastTap).coerceIn(120L, 1_200L)
                                            remember4(listOf(Melody.Note(noteHz, gap)))
                                            lastTap = now
                                        }
                                        AppLog.event("Лаборатория", "нота " + Melody.nameOf(noteHz))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = Melody.nameOf(noteHz),
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(if (recording) GrapeRed.copy(alpha = 0.2f) else CardBg)
                        .border(1.dp, if (recording) GrapeRed else ChipBorder, RectangleShape)
                        .clickable {
                            recording = !recording
                            if (recording) {
                                lastTap = System.currentTimeMillis()
                                status = "Пишу ноты — играйте"
                            } else {
                                status = "Запись остановлена"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (recording) "Стоп запись" else "Запись", color = if (recording) GrapeRed else TextPrimary, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            if (melody.isNotEmpty()) {
                                ToneSynth.stop()
                                ToneSynth.playSequence(melody)
                                AppLog.event("Лаборатория", "играю мотив: " + melody.size + " нот")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Играть мотив", color = GrapeGreen, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                }
            }
            Spacer(Modifier.height(8.dp))
            InfoRow("В мотиве", Melody.summary(melody), GrapeCyan)
            Text(melody.take(24).joinToString(" ") { it.name }, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable { melody = Melody.transpose(melody, 1) },
                    contentAlignment = Alignment.Center
                ) { Text("выше на 1", color = TextDim, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable { melody = Melody.tempo(melody, 125) },
                    contentAlignment = Alignment.Center
                ) { Text("быстрее", color = TextDim, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable { melody = emptyList() },
                    contentAlignment = Alignment.Center
                ) { Text("очистить", color = TextDim, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Готовые мотивы")
        Spacer(Modifier.height(8.dp))
        Melody.PRESETS.keys.forEach { preset ->
            SurfaceCard(borderColor = CardBorder, onClick = {
                val notes = Melody.preset(preset)
                ToneSynth.stop()
                ToneSynth.playSequence(notes)
                melody = notes
                status = "Мотив «" + preset + "» играет"
            }) {
                Text(preset, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                Text(
                    text = Melody.summary(Melody.preset(preset)) + " • нажмите, чтобы послушать",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Мои мотивы")
        Spacer(Modifier.height(8.dp))
        SurfaceCard(borderColor = CardBorder) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название мотива") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            if (melody.isNotEmpty()) {
                                val ok = MelodyStore.save(context, name, Melody.encode(melody))
                                status = if (ok) "Сохранено" else "Не сохранилось — слишком много мотивов"
                                saved = MelodyStore.load(context)
                            } else {
                                status = "Сначала сыграйте ноты"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Сохранить", color = GrapeGreen, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RectangleShape)
                        .background(CardBg)
                        .border(1.dp, ChipBorder, RectangleShape)
                        .clickable {
                            if (melody.isNotEmpty()) {
                                val ok = Nearby.sendProfile(
                                    name.ifBlank { "мотив" },
                                    "MELODY;" + Melody.encode(melody)
                                )
                                status = if (ok) "Мотив ушёл другу по связи" else "Нет связи — сначала подключитесь в «GSYNK рядом»"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Другу по связи", color = GrapeCyan, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
            }
        }

        Spacer(Modifier.height(10.dp))
        if (saved.isEmpty()) {
            Text("Сохранённых мотивов пока нет.", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        } else {
            saved.forEach { (title, encoded) ->
                SurfaceCard(borderColor = CardBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                            Text(
                                text = Melody.summary(Melody.decode(encoded)),
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RectangleShape)
                                .background(CardBg)
                                .border(1.dp, ChipBorder, RectangleShape)
                                .clickable {
                                    ToneSynth.stop()
                                    ToneSynth.playSequence(Melody.decode(encoded))
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = GrapeGreen, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RectangleShape)
                                .background(CardBg)
                                .border(1.dp, ChipBorder, RectangleShape)
                                .clickable {
                                    melody = Melody.decode(encoded)
                                    name = title
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("↑", color = GrapeCyan, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RectangleShape)
                                .background(CardBg)
                                .border(1.dp, ChipBorder, RectangleShape)
                                .clickable {
                                    MelodyStore.remove(context, title)
                                    saved = MelodyStore.load(context)
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", color = GrapeRed, style = MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}


/** Опыты GIOUI: короткий список для плиток — имя маршрута, подпись и цвет. */
private val extrasLab: List<Triple<String, String, Int>> = listOf(
    Triple("cores", "ядра и частоты", 0),
    Triple("thermal", "термощуп", 1),
    Triple("charge", "заряд и ток", 2),
    Triple("gpu", "ускоритель", 3),
    Triple("memory", "память и диск", 4),
    Triple("sensors", "все датчики", 5),
    Triple("shake", "акселерограф", 6),
    Triple("compass", "компас", 7),
    Triple("baro", "барометр", 8),
    Triple("prox", "приближение", 9),
    Triple("light", "люксметр", 10),
    Triple("steps", "шаги", 11),
    Triple("latency", "латентность экрана", 12),
    Triple("multitouch", "мультитач", 13),
    Triple("pwm", "мерцание (ШИМ)", 14),
    Triple("oscope", "осциллограф", 15),
    Triple("spectrum", "спектр звука", 16),
    Triple("sweep", "свип-тон", 17),
    Triple("haptic", "вибро-орган", 18),
    Triple("ble", "ble-радар", 19),
    Triple("wifi", "каналы wi-fi", 0),
    Triple("wifisig", "сигнал wi-fi", 1),
    Triple("usb", "usb и otg", 2),
    Triple("nfcwrite", "метка-визитка", 3)
)

/** Плитки расширенной лаборатории: тот же экран, без углубления в подменю. */
@androidx.compose.runtime.Composable
fun LabExtrasGrid(onOpen: (String) -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.material3.Text(
            text = "24 опыта с железом: от частот ядер до мерцания экрана. Всё только читает " +
                "датчики и ничего не пишет в систему.",
            color = com.gsynk.app.ui.theme.TextDim,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
        extrasLab.chunked(4).forEach { row ->
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Gioui.gutter)
            ) {
                row.forEach { (id, title, tone) ->
                    GioCard(
                        title = title,
                        subtitle = "опыт",
                        size = Gioui.Size.SMALL,
                        color = Gioui.Palette.tiles[tone % Gioui.Palette.tiles.size],
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        onClick = { onOpen(id) }
                    )
                }
                repeat(4 - row.size) {
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
                }
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(Gioui.gutter))
        }
    }
}
