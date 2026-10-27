package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeBlue
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary

/**
 * Хроника дня — добавка от разработчика.
 *
 * Приложение и так пишет журнал для разбора сбоев; здесь тот же журнал превращается
 * в понятную ленту: когда сработало правило, когда открывали инструменты, сколько
 * было касаний меткой, когда считала нагрузка и что делала нейросеть.
 *
 * Ничего не отправляется и не синхронизируется: лента строится на телефоне из
 * собственных записей.
 */
@Composable
fun ChronicleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val journal = remember { AppLog.journalTail(context, lines = 600) }
    val entries = remember(journal) { parseJournal(journal) }
    val summary = remember(entries) { summarize(entries) }

    GrapeScreen(
        title = "Хроника дня",
        subtitle = "записей: ${entries.size}",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = CardBorder) {
            Text(
                text = "итог дня",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium),
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SummaryCell(
                    value = summary.rules.toString(),
                    label = "правил",
                    color = GrapeGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryCell(
                    value = summary.tools.toString(),
                    label = "инструменты",
                    color = GrapeBlue,
                    modifier = Modifier.weight(1f)
                )
                SummaryCell(
                    value = summary.games.toString(),
                    label = "игры",
                    color = GrapeViolet,
                    modifier = Modifier.weight(1f)
                )
                SummaryCell(
                    value = summary.turbo.toString(),
                    label = "нагрузка",
                    color = GrapeCyan,
                    modifier = Modifier.weight(1f)
                )
            }
            if (summary.crashes > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "сбоев за это время: ${summary.crashes} — разбор в «Диагностике»",
                    color = GrapeRed,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Лента событий")
        Spacer(Modifier.height(9.dp))

        if (entries.isEmpty()) {
            SurfaceCard {
                Text(
                    text = "Пока пусто. Хроника заполняется сама: как только сработает правило " +
                        "или вы откроете инструмент, запись появится здесь.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                )
            }
        } else {
            SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                entries.take(200).forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        // время
                        Text(
                            text = entry.time,
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
                            modifier = Modifier.width(52.dp)
                        )
                        // цветная метка раздела
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(15.dp)
                                .background(colorFor(entry.tag))
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(
                                text = entry.tag,
                                color = colorFor(entry.tag),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                            )
                            Text(
                                text = entry.message,
                                color = if (entry.isProblem) GrapeRed else TextSecondary,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SurfaceCard {
            Text(
                text = "Хроника строится из журнала приложения. Он же уходит в отчёт, когда " +
                    "что-то ломается, — поэтому по дням видно, что происходило перед сбоем.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCell(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(66.dp)
            .background(ChipBg)
            .border(1.dp, ChipBorder, RectangleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, style = MaterialTheme.typography.titleLarge.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light), fontWeight = FontWeight.Light)
            Text(label, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        }
    }
}

private data class Entry(val time: String, val tag: String, val message: String, val isProblem: Boolean)

private data class Summary(val rules: Int, val tools: Int, val games: Int, val turbo: Int, val crashes: Int)

/** Разбирает строки журнала вида «12:34:56 [Правило] текст». */
private fun parseJournal(journal: String): List<Entry> =
    journal.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val time = trimmed.take(8).takeIf { it.length == 8 && it[2] == ':' } ?: return@mapNotNull null
            val rest = trimmed.removePrefix(time).trim()
            if (!rest.startsWith("[")) return@mapNotNull null
            val closing = rest.indexOf(']')
            if (closing <= 1) return@mapNotNull null
            val tag = rest.substring(1, closing)
            val message = rest.substring(closing + 1).trim()
            Entry(time = time, tag = tag, message = message, isProblem = message.startsWith("!"))
        }
        .toList()
        .reversed()

private fun summarize(entries: List<Entry>): Summary = Summary(
    rules = entries.count { it.tag == "Правило" },
    tools = entries.count { it.tag == "Tools" },
    games = entries.count { it.tag == "Игра" },
    turbo = entries.count { it.tag == "Турбо" },
    crashes = entries.count { it.tag == "Сбой" || it.message.contains("СБОЙ") }
)

private fun colorFor(tag: String): androidx.compose.ui.graphics.Color = when (tag) {
    "Правило" -> GrapeGreen
    "Игра" -> GrapeViolet
    "Турбо" -> GrapeCyan
    "Tools" -> GrapeBlue
    "Разрешения" -> GrapeCyan
    "Reports" -> TextSecondary
    "Сбой" -> GrapeRed
    else -> TextDim
}
