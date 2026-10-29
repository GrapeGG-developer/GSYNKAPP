package com.gsynk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Страница новостей релиза: каждая тема — карточка с иллюстрацией и списком по пунктам. */
@Composable
fun NewsScreen(onBack: () -> Unit) {
    GrapeScreen(
        title = "новости",
        subtitle = "GSYNK " + ReleaseNews.VERSION + " · по пунктам",
        onBack = onBack
    ) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.grapescript_logo),
                    contentDescription = "Логотип Grape Script",
                    modifier = Modifier.width(118.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("GSYNK " + ReleaseNews.VERSION, color = TextPrimary, fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
                    Text(
                        "Горизонтальный запуск, Metro-«Пуск», автоматизации жизни и 42 шифра.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ReleaseNews.items.forEachIndexed { index, item ->
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NewsArt(kind = item.iconKey, index = index)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "пункт " + (index + 1).toString(),
                            color = GrapeCyan,
                            fontSize = Gioui.Type.caption,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(item.title, color = TextPrimary, fontSize = Gioui.Type.groupHeader, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(item.body, color = TextPrimary, fontSize = Gioui.Type.body)
                Spacer(Modifier.height(8.dp))
                item.points.forEach { point ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = GrapeCyan, fontSize = Gioui.Type.body, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(point, color = TextDim, fontSize = Gioui.Type.caption)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NewsArt(kind: String, index: Int) {
    val first = Gioui.Palette.tiles[index % Gioui.Palette.tiles.size]
    val second = Gioui.Palette.tiles[(index + 5) % Gioui.Palette.tiles.size]
    Box(
        Modifier
            .size(86.dp)
            .clip(skinShape("card"))
            .background(Brush.linearGradient(listOf(first, second))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(68.dp)) {
            val w = size.width
            val h = size.height
            val white = Color.White.copy(alpha = 0.92f)
            when (kind) {
                "tiles" -> {
                    val cells = listOf(0 to 0, 1 to 0, 0 to 1, 2 to 1, 1 to 2, 2 to 2)
                    cells.forEach { (x, y) ->
                        drawRoundRect(
                            color = white.copy(alpha = if ((x + y) % 2 == 0) 0.9f else 0.5f),
                            topLeft = Offset(w * 0.13f + x * w * 0.25f, h * 0.13f + y * h * 0.25f),
                            size = Size(w * 0.19f, h * 0.19f),
                            cornerRadius = CornerRadius(w * 0.045f, h * 0.045f)
                        )
                    }
                }
                "cipher" -> {
                    drawArc(
                        color = white,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.28f, h * 0.13f),
                        size = Size(w * 0.44f, h * 0.44f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f, cap = StrokeCap.Round)
                    )
                    drawRoundRect(white, Offset(w * 0.18f, h * 0.38f), Size(w * 0.64f, h * 0.46f), CornerRadius(w * 0.08f, h * 0.08f))
                    drawCircle(Color.Black.copy(alpha = 0.35f), w * 0.07f, Offset(w * 0.5f, h * 0.58f))
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(w * 0.5f, h * 0.58f), Offset(w * 0.5f, h * 0.73f), w * 0.07f, StrokeCap.Round)
                }
                "anon" -> {
                    for (ring in 0..3) {
                        drawCircle(
                            color = white.copy(alpha = 0.92f - ring * 0.17f),
                            radius = w * (0.12f + ring * 0.1f),
                            center = Offset(w * 0.5f, h * 0.5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.055f)
                        )
                    }
                    drawCircle(white, w * 0.055f, Offset(w * 0.5f, h * 0.5f))
                }
                "schedule" -> {
                    drawCircle(white, w * 0.34f, Offset(w * 0.5f, h * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f))
                    drawLine(white, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.28f), w * 0.07f, StrokeCap.Round)
                    drawLine(white, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.68f, h * 0.58f), w * 0.07f, StrokeCap.Round)
                    drawCircle(white, w * 0.045f, Offset(w * 0.5f, h * 0.5f))
                }
                else -> {
                    val points = List(6) { i ->
                        val angle = (PI * 2.0 * i / 6.0).toFloat()
                        Offset(w * 0.5f + cos(angle) * w * 0.29f, h * 0.5f + sin(angle) * h * 0.29f)
                    }
                    points.forEachIndexed { i, point ->
                        drawLine(white.copy(alpha = 0.55f), Offset(w * 0.5f, h * 0.5f), point, w * 0.035f, StrokeCap.Round)
                        drawCircle(white, w * (if (i % 2 == 0) 0.075f else 0.052f), point)
                    }
                    drawCircle(white, w * 0.1f, Offset(w * 0.5f, h * 0.5f))
                }
            }
        }
    }
}
