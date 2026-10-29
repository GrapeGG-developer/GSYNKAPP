package com.gsynk.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.Ciphers
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * «Шифраторы» GIOUI: один универсальный экран на все шифры.
 *
 * Два режима (зашифровать/расшифровать), одно поле ввода, выбор шифра рядом чипами
 * по семьям — и автоопределение: если вставленный текст похож на чей-то шифр,
 * приложение само называет его и предлагает расшифровать именно им. Никаких
 * разрешений и сети: всё считает сам телефон, работает и на Poco X3 Pro.
 */
@Composable
fun CipherScreen(
    onBack: () -> Unit,
    initialCipher: String = "base64",
    initialDecrypt: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var encryptMode by remember { mutableStateOf(!initialDecrypt) }
    var selectedId by remember { mutableStateOf(initialCipher) }
    var input by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var autoDetect by remember { mutableStateOf(true) }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf(false) }
    var smartChain by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastMillis by remember { mutableStateOf(-1L) }
    var usedCipherTitle by remember { mutableStateOf("") }
    var detected by remember { mutableStateOf<Pair<Ciphers.Info, String>?>(null) }
    var tlsPair by remember { mutableStateOf<Ciphers.TlsKeyPair?>(null) }

    val selected = Ciphers.byId(selectedId)

    // Автоопределение — с маленькой паузой, чтобы не дёргать декодер на каждой букве.
    LaunchedEffect(input) {
        if (input.trim().length < 4) {
            detected = null
            return@LaunchedEffect
        }
        delay(250)
        val snapshot = input
        val found = withContext(Dispatchers.Default) { Ciphers.detect(snapshot) }
        if (snapshot == input) detected = found
    }

    fun run(forceCipherId: String? = null, forceDecrypt: Boolean = false, forceSmart: Boolean = false) {
        if (busy) return
        val decrypting = forceDecrypt || !encryptMode
        val effectiveId = forceCipherId
            ?: if (decrypting && autoDetect) (detected?.first?.id ?: selectedId) else selectedId
        busy = true
        status = ""
        smartChain = ""
        val useSmart = decrypting && (forceSmart || (autoDetect && forceCipherId == null))
        scope.launch {
            val started = System.nanoTime()
            if (useSmart) {
                val layered = withContext(Dispatchers.Default) { Ciphers.decodeLayers(input, keyText, maxLayers = 16) }
                lastMillis = (System.nanoTime() - started) / 1_000_000
                when (layered) {
                    is Ciphers.LayerOutcome.Ok -> {
                        output = layered.text
                        usedCipherTitle = layered.steps.lastOrNull()?.title ?: "умная расшифровка"
                        smartChain = layered.steps.joinToString(" → ") { it.title }
                        statusError = false
                        status = if (layered.steps.size <= 1) {
                            "Умная расшифровка: снят слой «" + usedCipherTitle + "»"
                        } else {
                            "Умная расшифровка: снято слоёв — " + layered.steps.size + ", добрались до исходного текста"
                        }
                    }
                    is Ciphers.LayerOutcome.Err -> {
                        output = ""
                        smartChain = layered.steps.joinToString(" → ") { it.title }
                        statusError = true
                        status = layered.message
                    }
                }
                busy = false
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                if (decrypting) {
                    Ciphers.decode(effectiveId, input, keyText)
                } else {
                    Ciphers.encode(effectiveId, input, keyText)
                }
            }
            lastMillis = (System.nanoTime() - started) / 1_000_000
            when (result) {
                is Ciphers.Outcome.Ok -> {
                    output = result.text
                    usedCipherTitle = Ciphers.byId(result.cipherId).title
                    statusError = false
                    status = if (decrypting && effectiveId != selectedId) {
                        "Автоопределение сработало: расшифровано шифром «" + usedCipherTitle + "»"
                    } else if (decrypting) {
                        "Расшифровано: «" + usedCipherTitle + "»"
                    } else {
                        "Зашифровано: «" + usedCipherTitle + "»"
                    }
                }
                is Ciphers.Outcome.Err -> {
                    output = ""
                    statusError = true
                    status = result.message
                }
            }
            busy = false
        }
    }

    GrapeScreen(
        title = "шифраторы",
        subtitle = "один экран · " + Ciphers.ALL.size + " шифров · умные слои",
        onBack = onBack
    ) {
        // ——— режим ———
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagChip(text = "зашифровать", selected = encryptMode) { encryptMode = true }
            TagChip(text = "расшифровать", selected = !encryptMode) { encryptMode = false }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (busy) "считаю…" else "готово",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }
        Spacer(Modifier.height(10.dp))

        // ——— автоопределение увидело шифр ———
        detected?.let { (info, why) ->
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Похоже на шифр: " + info.title,
                    color = GrapeGreen,
                    fontSize = Gioui.Type.body,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = why + ". Можно ничего не выбирать — расшифрую этим шифром.",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Расшифровать один слой «" + info.title + "»",
                    onClick = {
                        encryptMode = false
                        selectedId = info.id
                        run(forceCipherId = info.id, forceDecrypt = true)
                    },
                    icon = Icons.Filled.Refresh
                )
                Spacer(Modifier.height(6.dp))
                SecondaryButton(
                    text = "Снять все слои подряд",
                    onClick = {
                        encryptMode = false
                        autoDetect = true
                        run(forceDecrypt = true, forceSmart = true)
                    },
                    icon = Icons.Filled.Refresh
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ——— выбор шифра по семьям ———
        Ciphers.Family.entries.forEach { family ->
            SectionTitle(family.title)
            ChipFlow(Modifier.fillMaxWidth()) {
                Ciphers.ALL.filter { it.family == family }.forEach { info ->
                    TagChip(text = info.title, selected = selectedId == info.id) {
                        selectedId = info.id
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text(
                text = selected.title,
                color = TextPrimary,
                fontSize = Gioui.Type.groupHeader,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = selected.about, color = TextDim, fontSize = Gioui.Type.caption)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip(text = "работает на любом телефоне, и на Poco X3 Pro")
                if (selected.heavy) TagChip(text = "тяжёлый: нагружает процессор")
                if (selected.needsKey) TagChip(text = "нужен ключ")
            }
        }
        Spacer(Modifier.height(10.dp))

        // ——— ключ, когда он действительно нужен ———
        val keyCipher = when {
            selected.needsKey -> selected
            detected?.first?.needsKey == true -> detected!!.first
            else -> null
        }
        if (keyCipher != null) {
            val keyLabel = when {
                keyCipher.id == "gstls" && encryptMode -> "Открытый ключ получателя GSTLS-PUB.…"
                keyCipher.id == "gstls" -> "Ваш закрытый ключ GSTLS-PRIV.… (никому не показывать)"
                keyCipher.id == "lock" -> "Пароль замка (не сохраняется)"
                keyCipher.id == "ganonym" -> "Пароль GANONYM (не сохраняется)"
                else -> "Ключ (фраза)"
            }
            GrapeTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = keyLabel,
                modifier = Modifier.fillMaxWidth(),
                masked = !(keyCipher.id == "gstls" && encryptMode)
            )
            if (keyCipher.id == "gstls") {
                Spacer(Modifier.height(8.dp))
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Как устроен GS-TLS",
                        color = TextPrimary,
                        fontSize = Gioui.Type.body,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Получатель один раз создаёт пару: открытый ключ можно отправить кому угодно, закрытый остаётся только у него. Каждое сообщение получает новый эфемерный ECDH-ключ, поэтому одинаковый текст каждый раз выглядит иначе.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = if (tlsPair == null) "Создать мою пару ключей GS-TLS" else "Создать другую пару ключей",
                        onClick = {
                            scope.launch {
                                val pair = withContext(Dispatchers.Default) { Ciphers.generateTlsKeyPair() }
                                tlsPair = pair
                                keyText = if (encryptMode) pair.publicKey else pair.privateKey
                                statusError = false
                                status = "Пара GS-TLS создана на телефоне. Открытый можно передавать, закрытый сохрани в тайном месте."
                            }
                        }
                    )
                    tlsPair?.let { pair ->
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(text = pair.publicKey, color = GrapeCyan, fontSize = Gioui.Type.caption, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            text = "закрытый: " + pair.privateKey.take(28) + "… (полностью только кнопкой копирования)",
                            color = GrapeAmber,
                            fontSize = Gioui.Type.caption
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(text = "Копир. открытый", onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("GS-TLS открытый ключ", pair.publicKey))
                            })
                            SecondaryButton(text = "Копир. закрытый", onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("GS-TLS ЗАКРЫТЫЙ ключ", pair.privateKey))
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ——— единственное поле ввода ———
        GrapeTextField(
            value = input,
            onValueChange = { if (it.length <= Ciphers.MAX_CHARS) input = it },
            label = if (encryptMode) "Текст, который закрываем" else "Шифр, который открываем",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = input.length.toString() + " символов",
                color = TextDim,
                fontSize = Gioui.Type.caption,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(text = "Пример", onClick = { input = "Привет, GSYNK! Виноград созрел в 2026 году." })
            SecondaryButton(text = "Очистить", onClick = { input = ""; output = ""; status = "" })
        }
        Spacer(Modifier.height(10.dp))

        if (!encryptMode) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Автоопределение шифра", color = TextPrimary, fontSize = Gioui.Type.body)
                    Text(
                        text = "Вставлен шифр — приложение само поймёт слои и снимет повторные шифрования подряд",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
                Switch(checked = autoDetect, onCheckedChange = { autoDetect = it })
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Умно снять все слои сразу",
                onClick = { run(forceDecrypt = true, forceSmart = true) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Refresh
            )
            Spacer(Modifier.height(10.dp))
        }

        PrimaryButton(
            text = if (busy) "Считаю на этом телефоне…" else if (encryptMode) "Зашифровать" else "Расшифровать",
            onClick = { run() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && input.isNotEmpty(),
            icon = Icons.Filled.Lock
        )

        if (status.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = status,
                color = if (statusError) GrapeRed else GrapeGreen,
                fontSize = Gioui.Type.caption
            )
        }
        if (smartChain.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text(text = "цепочка снятия слоёв", color = GrapeCyan, fontSize = Gioui.Type.caption, fontWeight = FontWeight.SemiBold)
                Text(text = smartChain, color = TextPrimary, fontSize = Gioui.Type.caption)
            }
        }

        // ——— результат ———
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "результат · " + usedCipherTitle,
                    color = GrapeCyan,
                    fontSize = Gioui.Type.caption,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = output,
                        color = TextPrimary,
                        fontSize = Gioui.Type.body,
                        fontFamily = if (encryptMode) FontFamily.Monospace else FontFamily.Default
                    )
                }
                Spacer(Modifier.height(8.dp))
                ThinDivider()
                InfoRow("Было символов", input.length.toString())
                InfoRow("Стало символов", output.length.toString())
                if (input.isNotEmpty()) {
                    InfoRow(
                        "Размер вырос в",
                        String.format(Locale.US, "%.2f", output.length.toFloat() / input.length.toFloat()) + " раза"
                    )
                }
                if (lastMillis >= 0) {
                    InfoRow("Считал телефон", lastMillis.toString() + " мс", accent = GrapeAmber)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = "Копировать",
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("GSYNK шифр", output))
                            statusError = false
                            status = "Скопировано в буфер обмена"
                        }
                    )
                    SecondaryButton(
                        text = "Поделиться",
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, AnnotatedString(output).text)
                            }
                            context.startActivity(Intent.createChooser(send, "Отправить шифр"))
                        },
                        icon = Icons.Filled.Share
                    )
                    SecondaryButton(
                        text = "Результат → во ввод",
                        onClick = {
                            input = output
                            output = ""
                            encryptMode = !encryptMode
                            status = "Поменял местами: теперь обратная операция"
                            statusError = false
                        },
                        icon = Icons.Filled.Refresh
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Text(
                text = "Честно про силу",
                color = TextPrimary,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Кодировки (Base64, поросята, двоичный, байт-код и процессор) прячут текст только от случайных " +
                    "глаз: кто знает схему — прочитает. Настоящую секретность дают «GS-Замок», GANONYM и GS-TLS. " +
                    "GS-TLS не делает телефон HTTPS-сервером: это тот же класс защиты внутри текстового пакета — " +
                    "ECDH P-256, HKDF и AES-GCM. Ключи и пароли нигде не хранятся, в сеть ничего не уходит.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
