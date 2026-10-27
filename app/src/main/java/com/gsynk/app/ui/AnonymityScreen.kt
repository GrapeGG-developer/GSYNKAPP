package com.gsynk.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.provider.MediaStore
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.Anonymity
import com.gsynk.app.core.Ciphers
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Категория «анонимность»: персоны, ники, конструктор аватарок, следы текста и GANONYM. */
@Composable
fun AnonymityScreen(onBack: () -> Unit, initialMode: String = "persona") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(initialMode) }
    var seed by remember { mutableStateOf("тихий виноград") }
    var nonce by remember { mutableIntStateOf(0) }
    var styleName by remember { mutableStateOf(Anonymity.AvatarStyle.SYMMETRIC.name) }
    var traceText by remember { mutableStateOf("") }
    var packageText by remember { mutableStateOf("") }
    var packagePassword by remember { mutableStateOf("") }
    var packageOutput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val effectiveSeed = if (nonce == 0) seed else seed + "#" + nonce
    val persona = remember(effectiveSeed) { Anonymity.persona(effectiveSeed) }
    val nicknames = remember(effectiveSeed) { Anonymity.nicknames(effectiveSeed, 12) }
    val style = Anonymity.AvatarStyle.valueOf(styleName)
    val cells = remember(effectiveSeed, style) { Anonymity.avatarCells(effectiveSeed, style) }
    val palette = remember(effectiveSeed) { Anonymity.avatarPalette(effectiveSeed) }
    val findings = remember(traceText) { Anonymity.inspectText(traceText) }

    fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        statusError = false
        status = "Скопировано: " + label
    }

    fun runGanonym(decode: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                if (decode) Ciphers.decode("ganonym", packageText, packagePassword)
                else Ciphers.encode("ganonym", packageText, packagePassword)
            }
            when (result) {
                is Ciphers.Outcome.Ok -> {
                    packageOutput = result.text
                    statusError = false
                    status = if (decode) "GANONYM открыт" else "GANONYM закрыт: метаданных человека в пакете нет"
                }
                is Ciphers.Outcome.Err -> {
                    packageOutput = ""
                    statusError = true
                    status = result.message
                }
            }
            busy = false
        }
    }

    GrapeScreen(
        title = "анонимность",
        subtitle = "персона, аватар, ники и пакет без метаданных",
        onBack = onBack
    ) {
        ChipFlow(Modifier.fillMaxWidth()) {
            TagChip("персона", selected = mode == "persona") { mode = "persona" }
            TagChip("генератор ников", selected = mode == "nick") { mode = "nick" }
            TagChip("конструктор аватарок", selected = mode == "avatar") { mode = "avatar" }
            TagChip("следы текста", selected = mode == "traces") { mode = "traces" }
            TagChip("GANONYM", selected = mode == "ganonym") { mode = "ganonym" }
        }
        Spacer(Modifier.height(12.dp))

        if (mode == "persona" || mode == "nick" || mode == "avatar") {
            GrapeTextField(
                value = seed,
                onValueChange = { seed = it },
                label = "Зерно персоны (любое слово или фраза)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    text = "Другой вариант",
                    onClick = { nonce++ },
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Случайное зерно",
                    onClick = {
                        seed = "gs-" + Random.nextInt(100000, 999999).toString()
                        nonce = 0
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        when (mode) {
            "nick" -> {
                SectionTitle("нейтральные ники · нажми, чтобы скопировать")
                ChipFlow(Modifier.fillMaxWidth()) {
                    nicknames.forEach { nickname ->
                        TagChip(nickname) { copy("Ник GSYNK", nickname) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("Как пользоваться", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ник не должен повторять старые имена, даты рождения, город или любимые устойчивые слова из основной переписки. Одно зерно всегда даёт один и тот же набор — можно восстановить варианты позже.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
            }

            "avatar" -> {
                SectionTitle("конструктор анонимных аватарок")
                ChipFlow(Modifier.fillMaxWidth()) {
                    Anonymity.AvatarStyle.entries.forEach { item ->
                        TagChip(item.label, selected = style == item) { styleName = item.name }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AvatarPreview(seed = effectiveSeed, style = style, cells = cells, palette = palette)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (busy) "Сохраняю…" else "Сохранить PNG",
                        onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { saveAnonymityAvatar(context, effectiveSeed, style) }
                                    }
                                    result.onSuccess { name ->
                                        statusError = false
                                        status = "Аватар сохранён в Pictures/GSYNK: " + name
                                    }.onFailure { error ->
                                        statusError = true
                                        status = "Не сохранилось: " + (error.message ?: "неизвестная ошибка")
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    )
                    SecondaryButton(
                        text = "Копировать зерно",
                        onClick = { copy("Зерно аватарки", effectiveSeed) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("Без лица и фото", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Аватарка рисуется только из зерна: в ней нет камеры, геопозиции, исходного имени файла телефона и EXIF. Для разных сервисов лучше менять и зерно, и стиль.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
            }

            "traces" -> {
                GrapeTextField(
                    value = traceText,
                    onValueChange = { if (it.length <= 20_000) traceText = it },
                    label = "Текст профиля, заметка или сообщение для проверки",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                Spacer(Modifier.height(10.dp))
                findings.forEach { finding ->
                    val accent = when {
                        finding.risk >= 3 -> GrapeRed
                        finding.risk == 2 -> Color(0xFFF59E0B)
                        finding.risk == 1 -> GrapeCyan
                        else -> GrapeGreen
                    }
                    SurfaceCard(Modifier.fillMaxWidth(), borderColor = accent.copy(alpha = 0.5f)) {
                        Text(finding.title, color = accent, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
                        Text(finding.detail, color = TextDim, fontSize = Gioui.Type.caption)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Проверка не видит IP, время публикаций, устройство и связи аккаунтов — это локальная гигиена текста, а не волшебная невидимость.",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
            }

            "ganonym" -> {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("GANONYM", color = GrapeCyan, fontSize = Gioui.Type.groupHeader, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Сжатие + случайная внутренняя добивка до ступеней 128/256/512… + PBKDF2 240 000 кругов + AES-256-GCM. В пакете нет ника, автора, адресата, времени и модели телефона.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
                Spacer(Modifier.height(10.dp))
                GrapeTextField(
                    value = packagePassword,
                    onValueChange = { packagePassword = it },
                    label = "Пароль GANONYM (не сохраняется)",
                    modifier = Modifier.fillMaxWidth(),
                    masked = true
                )
                Spacer(Modifier.height(8.dp))
                GrapeTextField(
                    value = packageText,
                    onValueChange = { if (it.length <= Ciphers.MAX_CHARS) packageText = it },
                    label = "Открытый текст или пакет GA1.…",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (busy) "Считаю…" else "Закрыть анонимно",
                        onClick = { runGanonym(decode = false) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && packageText.isNotEmpty() && packagePassword.isNotEmpty(),
                        icon = Icons.Filled.Lock
                    )
                    SecondaryButton(
                        text = "Открыть GA1",
                        onClick = { runGanonym(decode = true) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && packageText.isNotEmpty() && packagePassword.isNotEmpty()
                    )
                }
                if (packageOutput.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text("результат", color = GrapeCyan, fontSize = Gioui.Type.caption, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(packageOutput, color = TextPrimary, fontSize = Gioui.Type.body)
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Копировать результат", onClick = { copy("GANONYM", packageOutput) })
                    }
                }
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarPreview(seed = effectiveSeed, style = style, cells = cells, palette = palette, sizeDp = 96)
                    Spacer(Modifier.size(14.dp))
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = persona.nickname,
                            color = TextPrimary,
                            fontSize = Gioui.Type.tileLive,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SurfaceCard(Modifier.fillMaxWidth()) {
                    InfoRow("Ник", persona.nickname, accent = GrapeCyan)
                    InfoRow("Местоимение", persona.pronoun)
                    InfoRow("Стиль", persona.style)
                    InfoRow("Часовой пояс", persona.timezone)
                    ThinDivider()
                    Text("Описание", color = TextDim, fontSize = Gioui.Type.caption)
                    Text(persona.bio, color = TextPrimary, fontSize = Gioui.Type.body)
                    Spacer(Modifier.height(6.dp))
                    Text("Девиз", color = TextDim, fontSize = Gioui.Type.caption)
                    Text(persona.motto, color = TextPrimary, fontSize = Gioui.Type.body)
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Копировать всю персону",
                    onClick = {
                        copy(
                            "Анонимная персона",
                            "Ник: " + persona.nickname + "\n" +
                                "Описание: " + persona.bio + "\n" +
                                "Местоимение: " + persona.pronoun + "\n" +
                                "Стиль: " + persona.style + "\n" +
                                "Часовой пояс: " + persona.timezone + "\n" +
                                "Девиз: " + persona.motto + "\n" +
                                "Зерно аватарки: " + effectiveSeed
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = status,
                color = if (statusError) GrapeRed else GrapeGreen,
                fontSize = Gioui.Type.caption
            )
        }
    }
}

@Composable
private fun AvatarPreview(
    seed: String,
    style: Anonymity.AvatarStyle,
    cells: List<Boolean>,
    palette: Triple<Int, Int, Int>,
    sizeDp: Int = 220
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(skinShape("card"))
            .background(Color(palette.first))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        ComposeCanvas(Modifier.size((sizeDp - 24).dp)) {
            val cell = this.size.minDimension / 8f
            drawCircle(
                color = Color(palette.third).copy(alpha = 0.16f),
                radius = this.size.minDimension * 0.48f,
                center = Offset(this.size.width / 2f, this.size.height / 2f)
            )
            cells.forEachIndexed { index, on ->
                if (on) {
                    val x = index % 8
                    val y = index / 8
                    val color = if ((x + y) % 3 == 0) Color(palette.third) else Color(palette.second)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x * cell + cell * 0.08f, y * cell + cell * 0.08f),
                        size = Size(cell * 0.84f, cell * 0.84f),
                        cornerRadius = CornerRadius(cell * 0.22f, cell * 0.22f)
                    )
                }
            }
        }
    }
}

private fun saveAnonymityAvatar(context: Context, seed: String, style: Anonymity.AvatarStyle): String {
    val palette = Anonymity.avatarPalette(seed)
    val cells = Anonymity.avatarCells(seed, style)
    val size = 1024
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = palette.first
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    val margin = 72f
    val cell = (size - margin * 2) / 8f
    cells.forEachIndexed { index, on ->
        if (on) {
            val x = index % 8
            val y = index / 8
            paint.color = if ((x + y) % 3 == 0) palette.third else palette.second
            val left = margin + x * cell + cell * 0.08f
            val top = margin + y * cell + cell * 0.08f
            canvas.drawRoundRect(left, top, left + cell * 0.84f, top + cell * 0.84f, cell * 0.22f, cell * 0.22f, paint)
        }
    }
    val name = "gsynk-anon-" + (Anonymity.stableSeed(seed + style.name) and 0x7fffffff).toString() + ".png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GSYNK")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("галерея не приняла файл")
    try {
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("PNG не записался")
        } ?: error("не открылся поток записи")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    } finally {
        bitmap.recycle()
    }
    return name
}
