package com.gsynk.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.GsaiChat
import com.gsynk.app.core.GsaiModel
import com.gsynk.app.core.GsaiPackage
import com.gsynk.app.core.GsaiServer
import com.gsynk.app.core.GsaiServerModel
import com.gsynk.app.core.GsaiSession
import com.gsynk.app.core.GsaiStore
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GioBrush
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * GSYNK-AI модели: каталог моделей с сервера (их выпускает программа GSYNK-AI на ПК),
 * скачивание и общение с моделью прямо на мощности телефона.
 */
@Composable
fun GsaiModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { GsaiStore(context.applicationContext) }
    var openId by remember { mutableStateOf<String?>(null) }

    val id = openId
    if (id != null) {
        GsaiChatScreen(store = store, modelId = id, onBack = { openId = null })
        return
    }

    var serverModels by remember { mutableStateOf<List<GsaiServerModel>>(emptyList()) }
    var installed by remember { mutableStateOf(store.installed()) }
    var status by remember { mutableStateOf("смотрю сервер GSYNK-AI…") }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var refreshTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        status = "смотрю сервер GSYNK-AI…"
        runCatching { GsaiServer.fetchIndex() }
            .onSuccess {
                serverModels = it
                status = if (it.isEmpty()) {
                    "на сервере пока нет моделей — создайте первую в программе GSYNK-AI на ПК"
                } else {
                    "на сервере моделей: ${it.size}"
                }
                installed = store.installed()
            }
            .onFailure { status = "сервер недоступен: ${it.message}" }
    }

    fun download(entry: GsaiServerModel) {
        if (downloadingId != null) return
        downloadingId = entry.id
        progress = 0f
        scope.launch {
            runCatching {
                val bytes = GsaiServer.download(entry.url) { progress = it }
                withContext(Dispatchers.Default) { store.save(entry, bytes) }
            }.onSuccess {
                installed = store.installed()
                status = "модель «${entry.name}» скачана — можно общаться без интернета"
                AppLog.event("GSYNK-AI", "скачана модель ${entry.name} ${entry.version}")
            }.onFailure {
                status = "не скачалось: ${it.message}"
            }
            downloadingId = null
        }
    }

    val installedById = installed.associateBy { it.id }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("файл не открылся")
                }
                val meta = GsaiPackage.parseMeta(bytes) ?: error("это не пакет GSYNK-AI")
                val sha = GsaiServer.sha256(bytes)
                val slug = meta.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                val entry = GsaiServerModel(
                    id = if (slug.isEmpty()) "local-" + sha.take(12) else slug,
                    name = meta.name, icon = meta.icon, description = meta.description,
                    version = meta.version, params = meta.params, size = bytes.size.toLong(),
                    sha256 = sha, url = "", steps = meta.steps, loss = meta.loss
                )
                withContext(Dispatchers.Default) { store.save(entry, bytes) }
                entry
            }.onSuccess { entry ->
                installed = store.installed()
                status = "модель «${entry.name}» импортирована из файла"
                AppLog.event("GSYNK-AI", "импортирована модель ${entry.name}")
            }.onFailure {
                status = "импорт не удался: ${it.message}"
            }
        }
    }

    GrapeScreen(
        title = "GSYNK-AI модели",
        subtitle = "свои нейросети с ПК · работают прямо на телефоне",
        onBack = onBack
    ) {
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.45f)) {
            Text(
                "Модели создаются и обучаются в программе GSYNK-AI на вашем компьютере, " +
                    "а сюда прилетают с сервера. Скачанная модель общается полностью офлайн: " +
                    "считает процессор телефона.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(status, color = TextDim, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "↻ Обновить каталог сервера", onClick = { refreshTick++ }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("На этом телефоне")
        Spacer(Modifier.height(8.dp))
        if (installed.isEmpty()) {
            Text("пока пусто — скачайте модель ниже", color = TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        installed.forEach { entry ->
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
                Text(
                    "${entry.icon} ${entry.name}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(modelFacts(entry), color = TextDim, style = MaterialTheme.typography.labelMedium)
                if (entry.description.isNotBlank()) {
                    Text(entry.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "💬 Общаться", onClick = { openId = entry.id }, modifier = Modifier.weight(1f))
                    SecondaryButton(
                        text = "Удалить",
                        onClick = {
                            store.delete(entry.id)
                            installed = store.installed()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(6.dp))
        SectionTitle("На сервере GSYNK-AI")
        Spacer(Modifier.height(8.dp))
        if (serverModels.isEmpty()) {
            Text(
                "здесь появятся модели, выпущенные из программы GSYNK-AI на ПК",
                color = TextDim, style = MaterialTheme.typography.bodyMedium
            )
        }
        serverModels.forEach { entry ->
            val local = installedById[entry.id]
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    "${entry.icon} ${entry.name}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(modelFacts(entry), color = TextDim, style = MaterialTheme.typography.labelMedium)
                if (entry.description.isNotBlank()) {
                    Text(entry.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                when {
                    downloadingId == entry.id -> {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "скачиваю… ${(progress * 100).toInt()}%",
                            color = TextDim, style = MaterialTheme.typography.labelMedium
                        )
                    }
                    local == null -> PrimaryButton(
                        text = "⬇ Скачать на телефон",
                        onClick = { download(entry) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    local.version != entry.version || local.sha256 != entry.sha256 -> PrimaryButton(
                        text = "⬆ Обновить до ${entry.version}",
                        onClick = { download(entry) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> Text(
                        "✓ уже на телефоне, версия актуальна",
                        color = GrapeGreen, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun modelFacts(entry: GsaiServerModel): String {
    val params = "%.1f млн параметров".format(entry.params / 1_000_000.0)
    val size = "%.1f МБ".format(entry.size / 1_048_576.0)
    val loss = if (entry.loss > 0f) " · loss %.2f".format(entry.loss) else ""
    return "v${entry.version} · $params · $size$loss"
}

@Composable
private fun GsaiChatScreen(store: GsaiStore, modelId: String, onBack: () -> Unit) {
    var model by remember { mutableStateOf<GsaiModel?>(null) }
    var loadError by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() } // текст → это модель?
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var tempPercent by remember { mutableStateOf(57) } // 0.1 + 57% * 1.4 ≈ 0.9
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(modelId) {
        runCatching { store.load(modelId) }
            .onSuccess { loaded ->
                model = loaded
                tempPercent = ((loaded.meta.temperature - 0.1f) / 1.4f * 100f).toInt().coerceIn(0, 100)
                messages += "Привет! Я ${loaded.meta.name}. Я живу прямо в этом телефоне и работаю без интернета. Напиши мне что-нибудь!" to true
            }
            .onFailure { loadError = it.message ?: "ошибка загрузки" }
    }

    fun send() {
        val current = model ?: return
        val text = input.trim()
        if (text.isBlank() || thinking) return
        input = ""
        messages += text to false
        thinking = true
        val temperature = 0.1f + tempPercent / 100f * 1.4f
        scope.launch {
            val answer = withContext(Dispatchers.Default) {
                runCatching {
                    GsaiChat.reply(
                        GsaiSession(current), history.toList(), text,
                        temperature = temperature,
                        topK = current.meta.topK,
                        maxNew = current.meta.maxNewTokens,
                        random = Random(System.nanoTime())
                    )
                }.getOrElse { "ой, у меня что-то закоротило: ${it.message}" }
            }
            history += text to answer
            messages += answer to true
            thinking = false
        }
    }

    GrapeScreen(
        title = model?.let { "${it.meta.icon} ${it.meta.name}" } ?: "модель",
        subtitle = model?.let {
            "%.1f млн параметров · считает этот телефон".format(it.meta.params / 1_000_000.0)
        } ?: "загружаю модель…",
        onBack = onBack
    ) {
        if (loadError.isNotEmpty()) {
            SurfaceCard(borderColor = GrapeRed.copy(alpha = 0.5f)) {
                Text("Модель не загрузилась: $loadError", color = TextPrimary)
                Text(
                    "Попробуйте удалить её в списке и скачать заново.",
                    color = TextDim, style = MaterialTheme.typography.labelMedium
                )
            }
            return@GrapeScreen
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { _, item ->
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
                        Column {
                            Text(
                                if (item.second) (model?.meta?.name ?: "модель") else "вы",
                                color = TextDim, style = MaterialTheme.typography.labelSmall
                            )
                            Text(item.first, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }
        if (thinking) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("модель думает на процессоре телефона…", color = TextDim, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        GrapeTextField(
            value = input,
            onValueChange = { input = it },
            label = "Напишите модели",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (thinking) "Думает…" else "Отправить",
            onClick = { send() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Креативность: ${"%.2f".format(0.1f + tempPercent / 100f * 1.4f)} (правее — неожиданнее ответы)",
            color = TextSecondary, style = MaterialTheme.typography.labelMedium
        )
        NeoSlider(value = tempPercent, onValueChange = { tempPercent = it }, brush = GioBrush.Violet)
        SecondaryButton(
            text = "Сбросить разговор",
            onClick = {
                history.clear()
                messages.clear()
                model?.let { messages += "Разговор с чистого листа. Привет! Я ${it.meta.name}." to true }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
    }
}
