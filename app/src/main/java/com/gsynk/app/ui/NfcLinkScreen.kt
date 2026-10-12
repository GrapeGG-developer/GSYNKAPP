package com.gsynk.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.NdefHceService
import com.gsynk.app.core.NfcLink
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Grape Link» — связка двух телефонов касанием.
 *
 * Один телефон держит приём и читает метку, второй работает самой меткой:
 * в Android 16 Beam убрали, поэтому GSYNK отдаёт данные через HCE (см. NdefHceService).
 */
@Composable
fun NfcLinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adapter = remember(context) { runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() }

    var mode by remember { mutableStateOf("receive") }
    var kind by remember { mutableStateOf(NfcLink.KIND_TEXT) }
    var title by remember { mutableStateOf("Привет от GSYNK") }
    var body by remember { mutableStateOf("") }
    var received by remember { mutableStateOf<NfcLink.Payload?>(null) }
    var receiving by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var duel by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }
    val nfcOn = adapter != null && adapter.isEnabled

    // приём: слушаем любые метки, пока экран открыт
    DisposableEffect(activity, receiving) {
        if (activity != null && adapter != null && receiving) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val payload = readTag(tag)
                handler.post {
                    if (payload != null) {
                        received = payload
                        celebrating = true
                        status = "Получено по NFC"
                    } else {
                        status = "Метка есть, но данных GSYNK в ней нет"
                    }
                }
            }
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE
            runCatching { adapter.enableReaderMode(activity, callback, flags, android.os.Bundle()) }
            onDispose { runCatching { adapter.disableReaderMode(activity) } }
        } else {
            onDispose { }
        }
    }

    // передача: телефон работает NDEF-меткой с нашими данными
    DisposableEffect(mode, kind, title, body) {
        if (mode == "send" && body.isNotBlank()) {
            NdefHceService.store(
                context,
                NfcLink.buildMessage(NfcLink.Payload(kind, title, body), context.packageName)
            )
            onDispose { NdefHceService.clear(context) }
        } else {
            onDispose { }
        }
    }

    if (duel) {
        NfcDuel(
            opponent = received?.let { NfcLink.parseGameInvite(it.body) },
            onCancel = { duel = false },
            onStoreResult = { score ->
                mode = "send"
                kind = NfcLink.KIND_GAME
                title = "Дуэль GSYNK"
                body = "плитки-tap;${android.os.Build.MODEL ?: "GSYNK"};$score"
                duel = false
                status = "Результат готов: приложите телефон к сопернику"
            }
        )
        return
    }

    GrapeScreen(title = "Grape Link", onBack = onBack) {
        SurfaceCard(corner = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        adapter == null -> "NFC недоступен"
                        nfcOn -> "NFC включён"
                        else -> "NFC выключен"
                    },
                    color = if (nfcOn) GrapeGreen else TextSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (adapter != null && !adapter.isEnabled) {
                    SmallChip("Включить NFC") {
                        runCatching { context.startActivity(NfcLink.openNfcSettingsIntent()) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Один телефон прикладывается к другому: первый отдаёт данные как метка, " +
                    "второй их читает. Так передаются текст, ссылки и вызовы в игру. " +
                    "Большие файлы по NFC физически не пройдут — для них нужен Wi-Fi Direct.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("Принимаю", selected = mode == "receive") {
                    mode = "receive"
                    receiving = true
                    status = "Жду касания…"
                }
                SmallChip("Передаю", selected = mode == "send") {
                    mode = "send"
                    receiving = false
                    if (NdefHceService.hasPayload(context)) {
                        status = "Готово: приложите телефон к сопернику"
                    } else {
                        status = "Заполните данные ниже"
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (mode == "send") {
            SurfaceCard(corner = 22.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip("Текст", selected = kind == NfcLink.KIND_TEXT) { kind = NfcLink.KIND_TEXT }
                    SmallChip("Ссылка", selected = kind == NfcLink.KIND_URL) { kind = NfcLink.KIND_URL }
                    SmallChip("Визитка", selected = kind == NfcLink.KIND_CONTACT) { kind = NfcLink.KIND_CONTACT }
                }
                Spacer(Modifier.height(12.dp))
                GrapeTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = "Заголовок"
                )
                Spacer(Modifier.height(10.dp))
                GrapeTextField(
                    value = body,
                    onValueChange = { body = it.take(600) },
                    label = when (kind) {
                        NfcLink.KIND_URL -> "Ссылка: https://…"
                        NfcLink.KIND_CONTACT -> "Телефон и имя: +7… Иванов"
                        else -> "Текст для передачи"
                    },
                    singleLine = false
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Готов держать",
                    onClick = { status = "Теперь коснитесь этим телефоном второго" },
                    enabled = body.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Держите экран включённым: телефон отдаёт данные, пока приложение открыто.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            SurfaceCard(corner = 22.dp) {
                Text(
                    text = if (receiving) "Приём включён" else "Включите приём",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Приложите телефон к чужой метке или к телефону с открытой передачей GSYNK.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
                if (!receiving) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Принимаю",
                        onClick = {
                            receiving = true
                            status = "Жду касания…"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Дуэль по касанию",
                    onClick = { duel = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = GrapeVioletSoft, style = MaterialTheme.typography.labelMedium)
        }

        val data = received
        if (data != null) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(corner = 22.dp) {
                Text("Получено", color = GrapeGreen, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(data.title.ifBlank { "Без заголовка" }, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(data.body, color = TextSecondary, style = MaterialTheme.typography.bodySmall)

                val invite = if (data.kind == NfcLink.KIND_GAME) NfcLink.parseGameInvite(data.body) else null
                if (invite != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Вызов от «${invite.from}» • результат соперника: ${invite.seed}",
                        color = GrapeGreenSoft,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Ответить вызовом",
                        onClick = { duel = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SecondaryButton(text = "Назад", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }

    if (celebrating) {
        LiquidGlassLink(onDone = { celebrating = false })
    }
}

// ————————————————————————————————————————————————
//  Мини-дуэль: кто быстрее нащёлкает 20 плиток
// ————————————————————————————————————————————————

@Composable
private fun NfcDuel(
    opponent: NfcLink.GameInvite?,
    onCancel: () -> Unit,
    onStoreResult: (Long) -> Unit
) {
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var result by remember { mutableLongStateOf(0L) }

    GrapeScreen(title = "Дуэль по касанию", onBack = onCancel) {
        SurfaceCard(corner = 22.dp) {
            Text(
                text = if (opponent != null) {
                    "Соперник: ${opponent.from} • ${opponent.seed} мс"
                } else {
                    "Соревнование без соперника: сыграйте и передайте свой результат касанием."
                },
                color = if (opponent != null) GrapeGreenSoft else TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Правило: быстрее всех нажать 20 плиток. Время пойдёт с первого касания.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(14.dp))

        if (!started) {
            PrimaryButton(
                text = "Начать",
                onClick = {
                    started = true
                    finished = false
                    hits = 0
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!finished) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(20) { index ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (index % 3 == 0) GrapeViolet else GrapeGreen)
                            .clickable {
                                if (hits == 0) startedAt = System.currentTimeMillis()
                                hits += 1
                                if (hits >= 20) {
                                    result = System.currentTimeMillis() - startedAt
                                    finished = true
                                }
                            }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Нажато: $hits / 20", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        } else {
            SurfaceCard(corner = 22.dp) {
                Text("Ваше время: $result мс", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                val opponentScore = opponent?.seed ?: 0L
                if (opponentScore > 0) {
                    Spacer(Modifier.height(6.dp))
                    val win = result < opponentScore
                    Text(
                        text = if (win) "Победа! Соперник: $opponentScore мс" else "В этот раз быстрее был соперник: $opponentScore мс",
                        color = if (win) GrapeGreen else TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Передать результат касанием",
                    onClick = { onStoreResult(result) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Заново", onClick = {
                    started = false
                    finished = false
                    hits = 0
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Чтение NDEF-сообщения с метки или с чужого телефона. */
private fun readTag(tag: Tag): NfcLink.Payload? {
    val ndef = runCatching { android.nfc.tech.Ndef.get(tag) }.getOrNull() ?: return null
    runCatching { ndef.cachedNdefMessage }.getOrNull()?.let { cached ->
        NfcLink.parse(cached)?.let { return it }
    }
    return runCatching {
        ndef.connect()
        val message: NdefMessage? = ndef.ndefMessage
        ndef.close()
        NfcLink.parse(message)
    }.getOrNull()
}

/**
 * Анимация соединения: жидкое стекло — цветные потоки света расходятся волнами
 * от точки касания и медленно растворяются.
 */
@Composable
fun LiquidGlassLink(onDone: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "liquid")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "flow"
    )
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1800)
        closing = true
        delay(400)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE60A0518))
            .clickable { closing = true },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.85f

            repeat(3) { index ->
                val phase = (flow + index * 0.33f) % 1f
                val radius = maxR * phase
                val alpha = ((1f - phase) * 0.55f).coerceIn(0f, 1f)
                val hueShift = index * 90f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.hsv((265f + hueShift + phase * 40f) % 360f, 0.85f, 1f).copy(alpha = alpha * 0.55f),
                            Color.hsv((140f + hueShift) % 360f, 0.75f, 0.95f).copy(alpha = alpha * 0.30f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    ),
                    radius = radius.coerceAtLeast(1f),
                    center = center
                )
            }

            val ringRadius = size.minDimension * 0.18f * (1f + flow * 0.4f)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f - flow * 0.25f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )

            repeat(9) { i ->
                val angle = (i / 9f) * 6.28f + flow * 6.28f
                val r = size.minDimension * 0.42f
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = 5f + (i % 3) * 3f,
                    center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Связь установлена",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (closing) "завершаю…" else "Grape Link",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) GrapeViolet else CardBgAlt)
            .border(1.dp, if (selected) Color.Transparent else ChipBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF120720) else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Находит Activity по цепочке ContextWrapper — нужна для reader-mode. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
