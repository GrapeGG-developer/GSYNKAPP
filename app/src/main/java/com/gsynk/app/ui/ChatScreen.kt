package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.CommandBot
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush

/**
 * GrapeBot без языковой модели: простой помощник, который понимает команды.
 *
 * Раньше здесь нужно было импортировать файл Gemma (.task) и ждать загрузку нейросети.
 * Теперь ничего скачивать не надо: бот разбирает фразу по ключевым словам и делает
 * дело — яркость, громкость, фонарик, «не беспокоить», открытие системных экранов,
 * звонок, поиск в браузере. Всё офлайн, ответ мгновенный.
 */
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log = remember { mutableStateListOf<Pair<String, Boolean>>() } // текст → это бот?
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        log += "Привет. Я GrapeBot — понимаю команды и делаю их сразу." to true
        log += "Например: «яркость 40», «фонарик вкл», «открой статистику батареи»." to true
    }

    fun send() {
        val line = input.trim()
        if (line.isBlank()) return
        log += line to false
        input = ""
        val reply = runCatching { CommandBot.ask(context, line) }
            .getOrElse { CommandBot.Reply("Не вышло: " + (it.message ?: "ошибка")) }
        log += reply.text to true
        reply.intent?.let { intent ->
            runCatching {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        AppLog.event("GrapeBot", "команда: " + line.take(40))
    }

    GrapeScreen(
        title = "GrapeBot",
        subtitle = "помощник на командах · без модели и интернета",
        onBack = onBack
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(log) { _, item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (item.second) Arrangement.Start else Arrangement.End
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.88f)
                            .clip(RectangleShape)
                            .background(if (item.second) CardBg else GrapeGreen.copy(alpha = 0.18f))
                            .border(1.dp, if (item.second) ChipBorder else GrapeGreen.copy(alpha = 0.5f), RectangleShape)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = item.first,
                            color = if (item.second) TextPrimary else GrapeGreen,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }

        LaunchedEffect(log.size) {
            if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
        }

        Spacer(Modifier.height(12.dp))
        GrapeTextField(
            value = input,
            onValueChange = { input = it },
            label = "Напишите команду",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = "Выполнить · ${input.ifBlank { "…" }}",
            onClick = { send() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Что понимает бот", color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            CommandBot.examples.forEach { example ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(6.dp)
                            .background(GrapeCyan)
                    ) { }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = example,
                        color = TextDim,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal),
                        modifier = Modifier.clip(RectangleShape).clickable {
                            input = example
                            send()
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RectangleShape)
                .background(TileBrush.Violet)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Никаких загрузок моделей — всё считается на телефоне сразу",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
