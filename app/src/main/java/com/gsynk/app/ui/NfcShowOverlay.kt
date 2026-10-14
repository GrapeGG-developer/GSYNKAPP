package com.gsynk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.NfcShow
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Слой «показ по касанию»: живёт поверх всего приложения.
 *
 * Второй телефон коснулся — и здесь либо салют во весь экран, либо короткая плашка
 * «фонарик включён». Слой один на всё приложение, поэтому сработает на любом экране.
 */
@Composable
fun NfcShowHost() {
    val context = LocalContext.current
    val request by NfcShow.current.collectAsState()
    val shown = request ?: return

    // команды выполняются сразу, картинка — просто живёт своё время
    LaunchedEffect(shown.id) {
        when (shown.kind) {
            NfcShow.Kind.TORCH -> {
                val ok = AutomationActions.setTorch(context, true)
                AppLog.event("Grape Link", "команда: фонарик " + if (ok) "включён" else "недоступен")
            }

            NfcShow.Kind.VIBRATE -> {
                AutomationActions.vibrate(context, 900L)
                AppLog.event("Grape Link", "команда: вибрация")
            }

            NfcShow.Kind.SIREN -> {
                AutomationActions.startSiren()
                AppLog.event("Grape Link", "команда: сирена на 3 секунды")
                delay(3_000L)
                AutomationActions.stopSiren()
            }

            else -> AutomationActions.vibrate(context, 70L)
        }
    }

    LaunchedEffect(shown.id) {
        delay(NfcShow.durationOf(shown.kind))
        NfcShow.clear()
    }

    var elapsed by remember(shown.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(shown.id) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
            if (elapsed > 8f) break
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (shown.kind) {
            NfcShow.Kind.FIREWORKS -> Canvas(Modifier.fillMaxSize()) { drawFireworks(elapsed) }
            NfcShow.Kind.HEARTS -> Canvas(Modifier.fillMaxSize()) { drawHearts(elapsed) }
            NfcShow.Kind.FLASH -> Canvas(Modifier.fillMaxSize()) { drawFlash(elapsed) }
            NfcShow.Kind.TEXT -> ShowTextCard(text = shown.text, from = shown.from, elapsed = elapsed)
            else -> CommandBanner(kind = shown.kind, from = shown.from)
        }
    }
}

// ————— рисунки —————

private fun DrawScope.drawFireworks(elapsed: Float) {
    val shots = listOf(
        Triple(0.15f, 0.34f, GrapeVioletSoft),
        Triple(1.35f, 0.62f, GrapeCyan),
        Triple(2.55f, 0.30f, GrapeAmber),
        Triple(3.65f, 0.55f, GrapePink)
    )
    shots.forEach { (startAt, x, color) ->
        val local = elapsed - startAt
        if (local < 0f || local > 1.5f) return@forEach
        val progress = (local / 1.2f).coerceIn(0f, 1f)
        val radius = size.minDimension * (0.06f + 0.34f * progress)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val center = Offset(size.width * x, size.height * (0.18f + x * 0.35f))
        val rays = 30
        for (index in 0 until rays) {
            val angle = (index.toFloat() / rays) * 2f * PI.toFloat()
            val wobble = 1f + 0.12f * sin(index * 3.7f + elapsed * 6f)
            val from = center + Offset(cos(angle) * radius * 0.62f * wobble, sin(angle) * radius * 0.62f * wobble)
            val to = center + Offset(cos(angle) * radius * wobble, sin(angle) * radius * wobble)
            drawLine(
                color = color.copy(alpha = alpha * 0.9f),
                start = from,
                end = to,
                strokeWidth = size.minDimension * 0.006f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = color.copy(alpha = alpha * 0.25f),
            radius = radius * 0.5f,
            center = center
        )
    }
}

private fun DrawScope.drawHearts(elapsed: Float) {
    val count = 16
    for (index in 0 until count) {
        val seedX = ((index * 37) % 100) / 100f
        val delay = (index % 8) * 0.35f
        val local = elapsed - delay
        if (local < 0f || local > 4.5f) continue
        val rise = local / 4.5f
        val x = size.width * (0.08f + seedX * 0.84f) + sin(local * 2.2f + index) * size.width * 0.02f
        val y = size.height * (0.95f - rise * 0.9f)
        val scale = size.minDimension * (0.02f + 0.012f * ((index % 3) + 1))
        val alpha = (1f - rise).coerceIn(0f, 1f) * 0.9f
        val color = when (index % 3) {
            0 -> GrapePink
            1 -> GrapeAmber
            else -> GrapeVioletSoft
        }
        drawHeart(center = Offset(x, y), radius = scale, color = color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius * 0.9f)
        cubicTo(
            center.x - radius * 1.6f, center.y - radius * 0.3f,
            center.x - radius * 0.5f, center.y - radius * 1.5f,
            center.x, center.y - radius * 0.5f
        )
        cubicTo(
            center.x + radius * 0.5f, center.y - radius * 1.5f,
            center.x + radius * 1.6f, center.y - radius * 0.3f,
            center.x, center.y + radius * 0.9f
        )
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFlash(elapsed: Float) {
    if (elapsed < 0.45f) {
        val alpha = (1f - elapsed / 0.45f).coerceIn(0f, 1f)
        drawRect(Color.White.copy(alpha = alpha * 0.92f))
        drawRect(GrapeCyan.copy(alpha = alpha * 0.35f))
        return
    }
    val pulse = ((elapsed - 0.45f) % 1.1f) / 1.1f
    val alpha = (1f - pulse) * 0.35f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(GrapeCyan.copy(alpha = alpha), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.4f),
            radius = size.minDimension * (0.3f + pulse * 0.8f)
        )
    )
}

@Composable
private fun ShowTextCard(text: String, from: String, elapsed: Float) {
    val appear = (elapsed / 0.35f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f * appear)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = text.ifBlank { "ПРИВЕТ ОТ GSYNK" },
                color = TextPrimary,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light),
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (from.isNotBlank()) {
                Text(
                    text = "от «$from»",
                    color = GrapeVioletSoft,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                )
            }
        }
    }
}

@Composable
private fun CommandBanner(kind: NfcShow.Kind, from: String) {
    val color = when (kind) {
        NfcShow.Kind.TORCH -> GrapeAmber
        NfcShow.Kind.VIBRATE -> GrapeCyan
        NfcShow.Kind.SIREN -> GrapeRed
        else -> GrapeVioletSoft
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 46.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            Modifier
                .clip(RectangleShape)
                .background(CardBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(color)
            ) { }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Коснулись: " + kind.label.lowercase(), color = color, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                Text(
                    text = if (from.isBlank()) "выполнено на этом телефоне" else "выполнено · от «$from»",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }
    }
}
