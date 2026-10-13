package com.gsynk.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.LocalAssistant
import com.gsynk.app.core.LocalLlm
import com.gsynk.app.core.LlmEngine
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatMessage(val text: String, val fromUser: Boolean, val pending: Boolean = false)

/**
 * Локальный ИИ: чат со своей моделью прямо на телефоне.
 *
 * Если модель не импортирована — работает встроенный ассистент команд
 * («яркость 40», «фонарик вкл»), а кнопка импорта подключает Gemma.
 */
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf(LocalLlm.modelFile(context)?.name ?: "") }
    var modelLoaded by remember { mutableStateOf(LlmEngine.isLoaded) }
    var status by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "Копирую модель…"
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        LocalLlm.import(context, stream, uri.lastPathSegment ?: "model.task")
                    }
                }.getOrNull()
            }
            if (copied == null) {
                status = "Не удалось скопировать файл"
            } else {
                modelName = copied.name
                status = "Загружаю «${copied.name}» в память…"
                val error = LlmEngine.load(context, copied)
                modelLoaded = error == null
                status = error ?: "Модель готова: ${copied.name}"
            }
            busy = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        messages.add(ChatMessage(text, fromUser = true))
        messages.add(ChatMessage("", fromUser = false, pending = true))
        busy = true

        scope.launch {
            val index = messages.lastIndex
            if (modelLoaded) {
                LlmEngine.askStreaming(text) { partial ->
                    messages[index] = ChatMessage(partial, fromUser = false, pending = true)
                }.let { answer ->
                    messages[index] = ChatMessage(answer, fromUser = false)
                }
            } else {
                val answer = LocalAssistant.answer(context, text)
                messages[index] = ChatMessage(answer, fromUser = false)
            }
            busy = false
        }
    }

    GrapeScreen(
        title = "Локальный ИИ",
        subtitle = if (modelLoaded) "модель: $modelName (офлайн)" else "работает встроенный ассистент",
        onBack = onBack
    ) {
        // ————— карточка модели —————
        SurfaceCard(borderColor = if (modelLoaded) GrapeGreen.copy(alpha = 0.5f) else CardBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TileBrush.Violet),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AI", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (modelLoaded) "Модель загружена" else "Локальная модель не подключена",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (modelName.isNotBlank()) modelName else "Gemma .task — файл 0.3–2.5 ГБ, интернет не нужен",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryButton(
                    text = "Импортировать .task",
                    onClick = { importer.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                )
                if (modelName.isNotBlank()) {
                    SecondaryButton(
                        text = "Удалить",
                        onClick = {
                            LocalLlm.delete(context)
                            LlmEngine.close()
                            modelName = ""
                            modelLoaded = false
                            status = "Модель удалена"
                        }
                    )
                }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(status, color = GrapeVioletSoft, style = MaterialTheme.typography.labelSmall)
            }
        }

        // ————— быстрые команды —————
        Spacer(Modifier.height(12.dp))
        Text("Быстрые команды", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("яркость 30", "громкость 0", "фонарик вкл").forEach { phrase ->
                SortChip(label = phrase, selected = false) { input = phrase }
            }
        }

        // ————— чат —————
        Spacer(Modifier.height(14.dp))
        TilePanel {
            if (messages.isEmpty()) {
                Text(
                    text = if (modelLoaded) {
                        "Спросите что угодно — модель считает ответ на телефоне, без сети."
                    } else {
                        "Я понимаю «яркость 40», «громкость 0», «фонарик вкл», «не беспокоить», " +
                            "«сколько правил». Импортируйте Gemma — и смогу отвечать на любые вопросы."
                    },
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    itemsIndexed(messages) { _, message ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            val bubble = if (message.fromUser) TileBrush.Violet else TileBrush.Ink
                            Box(
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bubble)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = if (message.pending && message.text.isEmpty()) "думаю…" else message.text,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ————— ввод —————
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GrapeTextField(
                value = input,
                onValueChange = { input = it },
                label = "Сообщение",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (busy) TileBrush.Ink else TileBrush.Green)
                    .border(1.dp, ChipBorder, RoundedCornerShape(18.dp))
                    .clickable { send() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_bolt),
                    contentDescription = "Отправить",
                    tint = if (busy) TextDim else GrapeGreenSoft,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Всё считается на устройстве: ни один запрос не уходит в интернет",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
