package com.gsynk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.gsynk.app.core.DeviceStats
import com.gsynk.app.core.Morse
import com.gsynk.app.core.Passwords
import com.gsynk.app.core.Sensors
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.GioBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ————————————————————————————————————————————————
//  Пароли и QR-коды
// ————————————————————————————————————————————————

@Composable
fun PasswordToolScreen(onBack: () -> Unit) {
    var length by remember { mutableStateOf(16) }
    var letters by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var password by remember {
        mutableStateOf(Passwords.generate(Passwords.Options(16, true, true, true)))
    }
    val strength = Passwords.strength(password)

    GrapeScreen(title = "Пароль и QR", subtitle = "генератор без интернета", onBack = onBack) {
        SurfaceCard {
            Text(
                text = password,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RectangleShape)
                            .background(
                                when {
                                    index >= strength -> CardBorder
                                    strength <= 1 -> GrapeRed
                                    strength == 2 -> GrapeViolet
                                    else -> GrapeGreen
                                }
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Стойкость: " + listOf("очень слабый", "слабый", "средний", "хороший", "надёжный")[strength],
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(12.dp))
            Text("Длина: $length", color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                listOf(8, 12, 16, 24, 32, 48).forEach { option ->
                    SortChip(label = option.toString(), selected = length == option) { length = option }
                }
            }
            Spacer(Modifier.height(12.dp))
            ChipFlow {
                SortChip("Буквы", letters) { letters = !letters }
                SortChip("Цифры", digits) { digits = !digits }
                SortChip("Символы", symbols) { symbols = !symbols }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryButton(
                    text = "Сгенерировать",
                    onClick = {
                        password = Passwords.generate(Passwords.Options(length, letters, digits, symbols))
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Ещё",
                    onClick = {
                        password = Passwords.generate(Passwords.Options(length, letters, digits, symbols))
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("QR-код пароля")
        Spacer(Modifier.height(9.dp))
        SurfaceCard {
            QrImage(content = password, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Покажите QR — второй телефон считает пароль камерой или через Grape Link.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

/** Рисует QR-код средствами zxing, без интернета и внешних сервисов. */
@Composable
fun QrImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) {
        runCatching {
            val size = 512
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(EncodeHintType.MARGIN to 1)
            )
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix.get(x, y)) 0xFF0B0714.toInt() else 0xFFF6F4FF.toInt()
                }
            }
            android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    Box(
        modifier
            .clip(RectangleShape)
            .background(Color(0xFFF6F4FF))
            .padding(10.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR-код",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            Text("Не удалось построить QR", color = TextDim, style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
        }
    }
}

// ————————————————————————————————————————————————
//  Морзянка фонариком
// ————————————————————————————————————————————————

@Composable
fun MorseToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("SOS") }
    var isSending by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(Morse.encode("SOS").size.toString()) }
    val morse = remember(text) { Morse.toText(text) }

    LaunchedEffect(isSending, text) {
        if (!isSending) return@LaunchedEffect
        // Пасхалка «три тире»: отправка кода с тремя тире подряд (например, SOS — ... --- ...).
        if (morse.trim(' ', '.').startsWith("---") || morse.trim(' ', '.').endsWith("---")) {
            com.gsynk.app.core.Eggs.unlock(context, "morse3")
        }
        val steps = Morse.encode(text)
        for (step in steps) {
            com.gsynk.app.core.AutomationActions.setTorch(context, step.light)
            delay(if (step.units == 1) 160L else 480L)
        }
        com.gsynk.app.core.AutomationActions.setTorch(context, false)
        isSending = false
    }

    // уходим со страницы — гасим фонарик, чтобы он не горел до утра
    DisposableEffect(Unit) {
        onDispose {
            isSending = false
            com.gsynk.app.core.AutomationActions.setTorch(context, false)
        }
    }

    GrapeScreen(title = "Морзянка", subtitle = "передача миганием", onBack = onBack) {
        SurfaceCard {
            Text(
                text = Morse.encode(text.take(12)).joinToString(" ") {
                    when {
                        !it.light -> "·"
                        it.units == 1 -> "•"
                        else -> "—"
                    }
                }.ifBlank { "—" },
                color = GrapeVioletSoft,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(12.dp))
            GrapeTextField(value = text, onValueChange = { text = it.uppercase() }, label = "Текст")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryButton(
                    text = if (isSending) "Стоп" else "Передать",
                    onClick = { isSending = !isSending },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(text = "SOS", onClick = { text = "SOS" })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Фонарик мигает точками и тире: короткая вспышка — точка, длинная — тире.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Металлоискатель (магнитометр)
// ————————————————————————————————————————————————

@Composable
fun MetalDetectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val compass = remember { Sensors.Compass(context) }
    var value by remember { mutableStateOf(0f) }
    var peak by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        compass.start { reading ->
            value = reading
            if (reading > peak) peak = reading
        }
        onDispose { compass.stop() }
    }

    val normalized = (value / 200f).coerceIn(0f, 1f)

    GrapeScreen(title = "Металлоискатель", subtitle = "магнитное поле вокруг", onBack = onBack) {
        SurfaceCard {
            Text(
                text = "${value.roundToInt()} мкТл",
                color = if (normalized > 0.6f) GrapeRed else TextPrimary,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
            )
            Spacer(Modifier.height(10.dp))
            BarIndicator(percent = (normalized * 100).roundToInt(), brush = GioBrush.Cyan)
            Spacer(Modifier.height(14.dp))
            InfoRow("Максимум", "${peak.roundToInt()} мкТл")
            InfoRow("Спокойный фон", "40–60 мкТл")
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Сбросить пик",
                onClick = { peak = 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (!compass.supported) "У этого телефона нет магнитометра" else
                    "Проводите телефоном по стене или коробке: резкий рост — там металл, проводка или магнит.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Уровень (акселерометр)
// ————————————————————————————————————————————————

@Composable
fun LevelToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val level = remember { Sensors.Level(context) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        level.start { x, y ->
            tiltX = x
            tiltY = y
        }
        onDispose { level.stop() }
    }

    val aligned = abs(tiltX) < 1.5f && abs(tiltY) < 1.5f

    GrapeScreen(title = "Уровень", subtitle = "ровно ли стоит", onBack = onBack) {
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(RectangleShape)
                        .background(if (aligned) GioBrush.Green else GioBrush.Ink)
                        .border(1.dp, ChipBorder, RectangleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RectangleShape)
                            .background(if (aligned) GrapeGreenSoft else GrapeViolet)
                            .rotate(tiltX)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    InfoRow("Наклон X", "%.1f°".format(tiltX))
                    InfoRow("Наклон Y", "%.1f°".format(tiltY))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (aligned) "Ровно!" else "Подровняйте поверхность",
                        color = if (aligned) GrapeGreenSoft else GrapeVioletSoft,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Положите телефон экраном вверх: круг смещается, пока наклон не станет нулевым.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Шумомер (микрофон)
// ————————————————————————————————————————————————

@Composable
fun SoundMeterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var db by remember { mutableStateOf(0) }
    var peak by remember { mutableStateOf(0) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var recorderError by remember { mutableStateOf("") }
    var recorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }

    LaunchedEffect(granted) {
        if (!granted) {
            request.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }
        // Пишем во временный файл приложения, а не в /dev/null: часть прошивок
        // отказывается стартовать запись в /dev/null и микрофон молчит.
        val file = java.io.File(context.cacheDir, "gsynk_noise.3gp")
        val fresh = runCatching {
            android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
        recorder = fresh
        recorderError = if (fresh == null) "Микрофон недоступен — возможно, его занял другой сервис" else ""
        while (true) {
            val amplitude = runCatching { fresh?.maxAmplitude ?: 0 }.getOrDefault(0)
            val value = Sensors.amplitudeToDb(amplitude.toDouble())
            db = value
            if (value > peak) peak = value
            delay(180L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            java.io.File(context.cacheDir, "gsynk_noise.3gp").delete()
        }
    }

    val label = when {
        db < 40 -> "тихо"
        db < 60 -> "разговор"
        db < 75 -> "шумно"
        db < 90 -> "очень шумно"
        else -> "опасно громко"
    }

    GrapeScreen(title = "Шумомер", subtitle = "примерный уровень звука", onBack = onBack) {
        SurfaceCard {
            Text(
                text = "$db дБ",
                color = when {
                    db >= 90 -> GrapeRed
                    db >= 75 -> GrapeVioletSoft
                    else -> TextPrimary
                },
                style = MaterialTheme.typography.displaySmall.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
            )
            Spacer(Modifier.height(4.dp))
            Text(label, color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(12.dp))
            BarIndicator(percent = db, brush = GioBrush.Amber)
            Spacer(Modifier.height(14.dp))
            InfoRow("Пик", "$peak дБ")
            InfoRow("Безопасный предел", "до 85 дБ")
            if (recorderError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(recorderError, color = GrapeRed, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Оценка приблизительная: считается по амплитуде микрофона, калибровки нет.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Системный дашборд
// ————————————————————————————————————————————————

@Composable
fun SysDashboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<DeviceStats.Snapshot?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = try {
                withContext(Dispatchers.IO) { DeviceStats.snapshot(context) }
            } catch (error: Throwable) {
                com.gsynk.app.core.AppLog.warn("Stats", "дашборд системы", error)
                snapshot
            }
            delay(1_500L)
        }
    }

    GrapeScreen(title = "Система", subtitle = "показатели в реальном времени", onBack = onBack) {
        val data = snapshot
        SurfaceCard {
            if (data == null) {
                Text("Считываю…", color = TextDim, style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            } else {
                Text(data.model, color = TextPrimary, style = MaterialTheme.typography.titleLarge.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light))
                Text(
                    "Android ${data.androidVersion} • ядер: ${data.cores}",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
                Spacer(Modifier.height(14.dp))
                InfoRow("Память (система)", "${data.ramUsedMb} / ${data.ramTotalMb} МБ")
                BarIndicator(data.ramPercent, GioBrush.Violet)
                Spacer(Modifier.height(12.dp))
                InfoRow(
                    "Накопитель",
                    "%.1f ГБ свободно из %.1f".format(data.storageFreeGb, data.storageTotalGb)
                )
                BarIndicator(100 - data.storageUsedPercent, GioBrush.Cyan)
                Spacer(Modifier.height(12.dp))
                InfoRow(
                            "Батарея",
                            data.batteryPercent.toString() + "% • " +
                                "%.1f".format(data.batteryTempC) + " °C"
                        )
                BarIndicator(data.batteryPercent, GioBrush.Green)
                Spacer(Modifier.height(12.dp))
                InfoRow("Аптайм", "${data.uptimeHours} ч")
            }
        }
    }
}

// ————————————————————————————————————————————————
//  Кодер: base64 / URL / hex / азбука
// ————————————————————————————————————————————————

@Composable
fun EncoderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("GSYNK") }
    var mode by remember { mutableStateOf(0) }

    val output = remember(input, mode) {
        runCatching {
            when (mode) {
                0 -> android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
                1 -> java.net.URLEncoder.encode(input, "UTF-8")
                2 -> input.toByteArray().joinToString(" ") { "%02X".format(it) }
                else -> input.toByteArray().joinToString(" ") { String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0') }
            }
        }.getOrDefault("—")
    }

    GrapeScreen(title = "Кодер", subtitle = "быстрые преобразования", onBack = onBack) {
        SurfaceCard {
            ChipFlow {
                listOf("Base64", "URL", "HEX", "Двоичный").forEachIndexed { index, label ->
                    SortChip(label = label, selected = mode == index) { mode = index }
                }
            }
            Spacer(Modifier.height(12.dp))
            GrapeTextField(value = input, onValueChange = { input = it }, label = "Исходный текст")
            Spacer(Modifier.height(12.dp))
            Text("Результат", color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CardBg)
                    .border(1.dp, ChipBorder, RectangleShape)
                    .padding(12.dp)
            ) {
                Text(output, color = GrapeGreenSoft, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Скопировать",
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GSYNK", output))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ————————————————————————————————————————————————
//  Калькулятор подсетей
// ————————————————————————————————————————————————

@Composable
fun SubnetScreen(onBack: () -> Unit) {
    var ip by remember { mutableStateOf("192.168.1.10") }
    var prefix by remember { mutableStateOf(24) }

    data class Subnet(
        val network: String,
        val broadcast: String,
        val mask: String,
        val hosts: Int,
        val firstHost: String,
        val lastHost: String
    )

    val result = remember(ip, prefix) {
        runCatching {
            val parts = ip.split(".").map { it.toInt() }
            require(parts.size == 4 && parts.all { it in 0..255 })
            val address = parts.fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
            val maskValue = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val network = address and maskValue
            val broadcast = network or (maskValue.inv() and 0xFFFFFFFFL)
            fun render(value: Long): String =
                listOf(24, 16, 8, 0).joinToString(".") { shift -> ((value shr shift) and 0xFF).toString() }
            val hosts = if (prefix >= 31) 0 else (1 shl (32 - prefix)) - 2
            Subnet(
                network = render(network),
                broadcast = render(broadcast),
                mask = render(maskValue),
                hosts = hosts,
                firstHost = render(network + 1),
                lastHost = render(broadcast - 1)
            )
        }.getOrNull()
    }

    GrapeScreen(title = "Подсети", subtitle = "калькулятор IP", onBack = onBack) {
        SurfaceCard {
            GrapeTextField(
                value = ip,
                onValueChange = { ip = it },
                label = "IP-адрес",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            Spacer(Modifier.height(12.dp))
            Text("Маска: /$prefix", color = TextSecondary, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                listOf(8, 16, 22, 24, 25, 26, 27, 28, 30, 31).forEach { value ->
                    SortChip(label = "/$value", selected = prefix == value) { prefix = value }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (result == null) {
                Text("Введите адрес вида 192.168.1.10", color = TextDim, style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            } else {
                InfoRow("Адрес сети", result.network)
                InfoRow("Маска", result.mask)
                InfoRow("Широковещательный", result.broadcast)
                InfoRow("Первый хост", result.firstHost)
                InfoRow("Последний хост", result.lastHost)
                InfoRow("Устройств", result.hosts.toString())
            }
        }
        Spacer(Modifier.height(12.dp))
        SurfaceCard(borderColor = GrapeViolet.copy(alpha = 0.4f)) {
            Text(
                text = "Разведка сети через Wi-Fi требует системных прав (root/Shizuku) — " +
                    "поэтому здесь только честный расчёт адресов.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
    }
}
