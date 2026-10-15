package com.gsynk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.Probe
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.min

/**
 * Проверка глаз: острота по кольцу Ландольта и различение цвета по точкам.
 *
 * Размер знака не выдуман: он считается от расстояния до глаз по правилу «знак — пять
 * угловых минут, прорезь — одна». Экран рисует кольцо в реальных пикселях и честно
 * предупреждает, если физически не может показать такую мелкую строку.
 */
@Composable
fun EyeScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf("острота") }

    GrapeScreen(
        title = "проверка глаз",
        subtitle = "острота по кольцу и различение цвета",
        onBack = onBack
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortChip(label = "острота", selected = tab == "острота") { tab = "острота" }
            SortChip(label = "цвет", selected = tab == "цвет") { tab = "цвет" }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == "острота") AcuityBlock() else ColourBlock()
        Spacer(Modifier.height(20.dp))
    }
}

// ————— острота —————

@Composable
private fun AcuityBlock() {
    val density = LocalDensity.current
    val densityDpi = (density.density * 160f).toInt().coerceAtLeast(120)
    var distanceMm by remember { mutableStateOf(400) }
    var lineIndex by remember { mutableStateOf(0) }
    var seed by remember { mutableStateOf(101) }
    var passed by remember { mutableStateOf(0) }
    var asked by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val acuity = Probe.LINES[lineIndex]
    val directions = remember(seed) { Probe.directions(seed, Probe.LINES.size) }
    val target = directions[lineIndex.coerceIn(0, directions.lastIndex)]
    val sizePx = Probe.optotypePx(acuity, distanceMm.toFloat(), densityDpi)
    val tooSmall = sizePx < 4

    fun restart() {
        seed += 1
        lineIndex = 0
        passed = 0
        asked = 0
        finished = false
        message = ""
    }

    fun answer(dir: Probe.Dir) {
        if (finished) return
        asked++
        if (dir == target) {
            passed = lineIndex + 1
            if (lineIndex >= Probe.LINES.lastIndex) {
                finished = true
                message = "дошли до конца таблицы — лучше 1,5 эта проверка не показывает"
            } else {
                lineIndex++
                message = "верно"
            }
        } else {
            finished = true
            message = "прорезь смотрела " + target.label + " — на этой строке заканчиваем"
        }
    }

    SurfaceCard(borderColor = GrapeCyan.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("расстояние до глаз", color = TextDim, fontSize = Metro.Type.caption)
                Text(
                    text = (distanceMm / 10).toString() + " см",
                    color = TextPrimary,
                    fontSize = Metro.Type.body,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "строка " + Probe.lineLabel(acuity) + " · " +
                    String.format(Locale.US, "%.2f мм", Probe.optotypeMm(acuity, distanceMm.toFloat())),
                color = GrapeCyan,
                fontSize = Metro.Type.caption
            )
        }
        Spacer(Modifier.height(6.dp))
        NeoSlider(
            value = (distanceMm - 150) / 5,
            onValueChange = { distanceMm = 150 + it * 5 },
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(GrapeCyan, GrapeGreen))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Держите телефон ровно на этом расстоянии и не приближайте его, когда строка станет мелкой.",
            color = TextDim,
            fontSize = Metro.Type.caption
        )
    }

    Spacer(Modifier.height(10.dp))
    SurfaceCard(borderColor = ChipBorder) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            LandoltRing(direction = target, sizePx = sizePx)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "куда смотрит прорезь?",
            color = TextPrimary,
            fontSize = Metro.Type.body,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (tooSmall) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Эта строка на экране всего " + sizePx.toString() + " пикселя — телефон её уже не " +
                    "покажет честно. Увеличьте расстояние нельзя, поэтому проверяйте строки крупнее.",
                color = GrapePink,
                fontSize = Metro.Type.caption
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Probe.Dir.LEFT, Probe.Dir.UP).forEach { dir ->
                DirectionButton(dir.label, Modifier.weight(1f)) { answer(dir) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Probe.Dir.DOWN, Probe.Dir.RIGHT).forEach { dir ->
                DirectionButton(dir.label, Modifier.weight(1f)) { answer(dir) }
            }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = if (message == "верно") GrapeGreen else GrapeAmber,
                fontSize = Metro.Type.caption
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
        Text("итог", color = TextDim, fontSize = Metro.Type.caption)
        Text(
            text = if (passed == 0) "пока ничего" else Probe.lineLabel(Probe.LINES[(passed - 1).coerceAtLeast(0)]),
            color = GrapeGreen,
            fontSize = Metro.Type.tileLive,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "самая мелкая строка, которую вы прочли верно. " +
                "На " + (distanceMm / 10).toString() + " см это " +
                String.format(
                    Locale.US, "%.2f мм", Probe.optotypeMm(
                        Probe.LINES[(passed - 1).coerceAtLeast(0)], distanceMm.toFloat()
                    )
                ) + ", ответов " + asked.toString(),
            color = TextDim,
            fontSize = Metro.Type.caption
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("начать заново", { restart() }, Modifier.weight(1f))
            SecondaryButton("продолжить", { message = ""; finished = false }, Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(10.dp))
    SurfaceCard(borderColor = ChipBorder) {
        Text("почему размеры такие", color = GrapeAmber, fontSize = Metro.Type.body)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Острота 1,0 значит, что знак целиком занимает 5 угловых минут: размер посчитан как " +
                "2·d·tg(2,5′). При 40 см строка 1,0 — это кольцо 0,58 мм с прорезью 0,12 мм, то есть " +
                "меньше миллиметра. Поэтому проверка без очков и офтальмолога — только для интереса.",
            color = TextDim,
            fontSize = Metro.Type.caption
        )
        Spacer(Modifier.height(6.dp))
        InfoRow("плотность экрана", densityDpi.toString() + " ppi")
        InfoRow("пикселей в строке 1,0", Probe.optotypePx(1f, distanceMm.toFloat(), densityDpi).toString())
    }
}

@Composable
private fun DirectionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SurfaceCard(
        modifier = modifier,
        borderColor = GrapeViolet.copy(alpha = 0.4f),
        onClick = onClick
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = Metro.Type.body,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/** Кольцо Ландольта: разрыв ровно в пятую часть знака. */
@Composable
private fun LandoltRing(direction: Probe.Dir, sizePx: Int) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val shown = min(sizePx, min(size.width, size.height).toInt()).coerceAtLeast(3)
        val thickness = (shown / 5f).coerceAtLeast(1.5f)
        val radius = (shown - thickness) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val gapDegrees = 72f
        drawArc(
            color = GrapeViolet,
            startAngle = direction.degrees + gapDegrees / 2f,
            sweepAngle = 360f - gapDegrees,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = thickness)
        )
        // Светящаяся точка в центре помогает зафиксировать взгляд.
        drawCircle(GrapeCyan.copy(alpha = 0.6f), radius = 2f, center = center)
    }
}

// ————— цвет —————

@Composable
private fun ColourBlock() {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(41) }
    var round by remember { mutableStateOf(1) }
    var score by remember { mutableStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var hardMode by remember { mutableStateOf(false) }

    val number = remember(seed) { Probe.numberFor(seed) }
    val plate = remember(seed) { Probe.plate(number, seed * 7 + 3, 300) }
    val figure = remember(plate) { Probe.figureShare(plate) }

    // Цвета подобраны близкими по яркости: цифру видно только по оттенку.
    val insideColour = if (hardMode) Color(0xFF6E8B3D) else Color(0xFFD9714F)
    val outsideColour = if (hardMode) Color(0xFFB9A14C) else Color(0xFFA8B15E)
    val ratio = if (hardMode) {
        Probe.contrast(Triple(110, 139, 61), Triple(185, 161, 76))
    } else {
        Probe.contrast(Triple(217, 113, 79), Triple(168, 177, 94))
    }

    fun check() {
        val given = answer.trim().toIntOrNull()
        shown = true
        result = when {
            given == null -> "цифру разобрать не удалось — это тоже результат"
            given == number -> {
                score++
                "верно: на пластине было " + number.toString()
            }
            else -> "на пластине было " + number.toString() + ", а не " + given.toString()
        }
    }

    fun next() {
        seed += 1
        round++
        answer = ""
        shown = false
        result = ""
    }

    SurfaceCard(borderColor = GrapePink.copy(alpha = 0.45f)) {
        Text("что видно на пластине", color = GrapePink, fontSize = Metro.Type.body)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(250.dp)) {
                drawCircle(Color(0xFF120F1E), radius = size.minDimension / 2f)
                plate.forEach { dot ->
                    drawCircle(
                        color = if (dot.inside) insideColour else outsideColour,
                        radius = dot.radius * size.minDimension,
                        center = Offset(dot.x * size.width, dot.y * size.height)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "пластина " + round.toString() + " · точек " + plate.size.toString(),
                color = TextDim,
                fontSize = Metro.Type.caption,
                modifier = Modifier.weight(1f)
            )
            TagChip(text = if (hardMode) "сложнее" else "обычно", selected = hardMode) {
                hardMode = !hardMode
            }
        }
        Spacer(Modifier.height(8.dp))
        GrapeTextField(
            value = answer,
            onValueChange = { answer = it.filter { ch -> ch.isDigit() }.take(2) },
            label = "какое число видно"
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("проверить", { check() }, Modifier.weight(1f))
            SecondaryButton("следующая", { next() }, Modifier.weight(1f))
        }
        if (shown) {
            Spacer(Modifier.height(8.dp))
            Text(result, color = if (result.startsWith("верно")) GrapeGreen else GrapeAmber, fontSize = Metro.Type.caption)
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("разница яркости внутри и снаружи", String.format(Locale.US, "%.2f×", ratio))
        InfoRow("точек внутри знака", Probe.figureDots(plate).toString())
        InfoRow("доля точек знака", String.format(Locale.US, "%.0f%%", figure * 100f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = Probe.contrastVerdict(ratio) + ". Яркость подобрана близкой специально: цифру видно " +
                "по оттенку, а не по свету.",
            color = TextDim,
            fontSize = Metro.Type.caption
        )
    }

    Spacer(Modifier.height(10.dp))
    SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
        Text("счёт", color = TextDim, fontSize = Metro.Type.caption)
        Text(
            text = score.toString() + " из " + (round - if (shown) 0 else 1).coerceAtLeast(1),
            color = GrapeGreen,
            fontSize = Metro.Type.tileLive,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when {
                score == 0 -> "пока ни одной: если так и дальше, стоит показаться офтальмологу"
                round <= 2 -> "мало данных — сделайте хотя бы пять пластин подряд"
                score * 2 >= round -> "различение цвета выглядит обычным"
                else -> "часть пластин не разобралась: это повод проверить цветовое зрение у врача"
            },
            color = TextDim,
            fontSize = Metro.Type.caption
        )
    }

    Spacer(Modifier.height(10.dp))
    SurfaceCard(borderColor = ChipBorder) {
        Text("честно об этом тесте", color = GrapeAmber, fontSize = Metro.Type.body)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Настоящие пластины Рабкина печатают на бумаге с выверенными чернилами и проверяют " +
                "при дневном свете. Экран светится сам, цвета зависят от матрицы и яркости, поэтому " +
                "здесь можно заметить склонность, но поставить диагноз — нет. Яркость экрана лучше " +
                "поставить на середину и выключить ночной режим.",
            color = TextDim,
            fontSize = Metro.Type.caption
        )
    }
}
