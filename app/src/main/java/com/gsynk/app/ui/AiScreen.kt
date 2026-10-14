package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AiNet
import com.gsynk.app.core.CommandBot
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * «Умный помощник» GSUI V4 — настоящие сети плюс офлайн.
 *
 * Три режима в одном экране: офлайн-помощник GSYNK (команды телефону, без интернета),
 * любая OpenAI-совместимая сеть (OpenAI, Groq, OpenRouter, своя) и домашняя Ollama.
 * Ключ ходит только отсюда в сеть и лежит в настройках телефона — в отчёты он не попадает.
 */
@Composable
fun AiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(AiNet.load(context)) }
    var address by remember { mutableStateOf(config.baseUrl) }
    var model by remember { mutableStateOf(config.model) }
    var key by remember { mutableStateOf(config.key) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<Pair<String, Boolean>>() } // текст → это ответ сети?
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun persist(updated: AiNet.Config) {
        config = updated
        AiNet.save(context, updated)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        lines.add(text to false)
        busy = true
        scope.launch {
            val answer = if (config.isOffline) {
                val reply = CommandBot.ask(context, text)
                reply.text + if (reply.toggles.isNotBlank()) " (" + reply.toggles + ")" else ""
            } else {
                AiNet.ask(
                    config = config,
                    prompt = text,
                    history = lines.dropLast(1).takeLast(8).map { (t, isAnswer) ->
                        (if (isAnswer) "assistant" else "user") to t
                    }
                )
            }
            lines.add(answer to true)
            busy = false
        }
    }

    GrapeScreen(title = "Умный помощник", subtitle = AiNet.describe(config), onBack = onBack) {
        // ————— выбор сети —————
        SectionTitle("Откуда берётся ум")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            AiNet.PROVIDERS.forEach { provider ->
                SortChip(label = provider.title, selected = config.provider == provider.id) {
                    val updated = config.copy(
                        provider = provider.id,
                        baseUrl = provider.baseUrl,
                        model = provider.model
                    )
                    address = provider.baseUrl
                    model = provider.model
                    persist(updated)
                    lines.add("выбрано: " + provider.title + " — " + provider.hint to true)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!config.isOffline) {
            SurfaceCard(borderColor = CardBorder) {
                GrapeTextField(
                    value = address,
                    onValueChange = { address = it; persist(config.copy(baseUrl = it)) },
                    label = "Адрес сети (https://…/v1)"
                )
                Spacer(Modifier.height(8.dp))
                GrapeTextField(
                    value = model,
                    onValueChange = { model = it; persist(config.copy(model = it)) },
                    label = "Модель"
                )
                Spacer(Modifier.height(8.dp))
                GrapeTextField(
                    value = key,
                    onValueChange = { key = it; persist(config.copy(key = it)) },
                    label = "Ключ (хранится на телефоне)",
                    masked = true
                )
                Spacer(Modifier.height(10.dp))
                Text("Насколько смело отвечать:", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(20 to "аккуратно", 60 to "обычно", 90 to "смело").forEach { (value, title) ->
                        SortChip(title, config.temperature == value) {
                            persist(config.copy(temperature = value))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SortChip("Проверить связь", false) {
                        busy = true
                        scope.launch {
                            val answer = AiNet.ping(config)
                            lines.add(answer to true)
                            busy = false
                        }
                    }
                    SortChip("Офлайн", config.isOffline) {
                        persist(config.copy(provider = "offline"))
                    }
                }
            }
        } else {
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
                Text("Офлайн-помощник", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Работает без интернета: понимает команды телефону — яркость, громкость, " +
                        "фонарик, тишина, открытие настроек, звонки, поиск. Например: " +
                        CommandBot.examples.take(4).joinToString(", ") + ".",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Разговор")
        Spacer(Modifier.height(7.dp))
        Box(Modifier.height(320.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text(
                            text = "Скажи что-нибудь. Если включена сеть — отвечает модель, " +
                                "если нет — помощник GSYNK.",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }
                itemsIndexed(lines) { _, line ->
                    val (text, isAnswer) = line
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(Metro.Shapes.card)
                            .background(if (isAnswer) CardBgAlt else Metro.Palette.Cobalt.copy(alpha = 0.30f))
                            .border(1.dp, if (isAnswer) ChipBorder else GrapeVioletSoft.copy(alpha = 0.4f), Metro.Shapes.card)
                            .padding(11.dp)
                    ) {
                        Text(
                            text = text,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        GrapeTextField(
            value = input,
            onValueChange = { input = it },
            label = if (busy) "Ждём ответ…" else "Сообщение",
            keyboardType = KeyboardType.Text
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (busy) "Думает" else "Отправить",
                onClick = { send() },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Очистить",
                onClick = { lines.clear() },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
