package com.gsynk.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.LowLevel
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary

/** Лабораторный низкоуровневый вывод: байты, endian, контрольные суммы и виртуальный CPU. */
@Composable
fun LowLevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("GSYNK 5.1 · байты, регистры и процессор") }
    var mode by remember { mutableStateOf("hex") }
    var status by remember { mutableStateOf("") }

    val bytes = remember(input) { input.toByteArray(Charsets.UTF_8) }
    val output = remember(bytes, mode) {
        when (mode) {
            "bin" -> LowLevel.binaryDump(bytes)
            "endian" -> LowLevel.endianView(bytes)
            "sums" -> LowLevel.checksums(bytes)
            "cpu" -> LowLevel.traceCpu(bytes).joinToString("\n") { step ->
                "IP=%04X  A=%02X B=%02X C=%02X  FLAGS[%s%s]  %s".format(
                    step.ip,
                    step.a,
                    step.b,
                    step.c,
                    if (step.carry) "C" else "-",
                    if (step.zero) "Z" else "-",
                    step.instruction
                )
            }
            else -> LowLevel.hexDump(bytes)
        }
    }

    GrapeScreen(
        title = "низкий уровень",
        subtitle = "hex, биты, endian, хэши и трассировка CPU",
        onBack = onBack
    ) {
        GrapeTextField(
            value = input,
            onValueChange = { if (it.length <= 4_000) input = it },
            label = "Текст для разбора по байтам UTF-8",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow(Modifier.fillMaxWidth()) {
            TagChip("HEX-дамп", selected = mode == "hex") { mode = "hex" }
            TagChip("биты", selected = mode == "bin") { mode = "bin" }
            TagChip("endian BE/LE", selected = mode == "endian") { mode = "endian" }
            TagChip("суммы и хэши", selected = mode == "sums") { mode = "sums" }
            TagChip("регистры CPU", selected = mode == "cpu") { mode = "cpu" }
        }
        Spacer(Modifier.height(12.dp))

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text("сводка", color = GrapeCyan, fontSize = Gioui.Type.caption, fontWeight = FontWeight.SemiBold)
            InfoRow("Символов", input.length.toString())
            InfoRow("Байт UTF-8", bytes.size.toString())
            InfoRow("Бит", (bytes.size * 8).toString())
            InfoRow("Первый байт", if (bytes.isEmpty()) "—" else "0x%02X".format(bytes.first().toInt() and 0xFF))
            InfoRow("Последний байт", if (bytes.isEmpty()) "—" else "0x%02X".format(bytes.last().toInt() and 0xFF))
        }
        Spacer(Modifier.height(10.dp))

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text(
                text = when (mode) {
                    "bin" -> "каждый байт · биты, HEX и десятичное значение"
                    "endian" -> "группы по 4 байта · big-endian и little-endian"
                    "sums" -> "контрольные суммы и хэши"
                    "cpu" -> "виртуальный процессор · IP, A/B/C, флаги переноса и нуля"
                    else -> "hex-дамп · смещение, байты и печатные символы"
                },
                color = GrapeCyan,
                fontSize = Gioui.Type.caption,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = output,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = Gioui.Type.caption
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Копировать вывод",
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("GSYNK low-level", output))
                    status = "Низкоуровневый вывод скопирован"
                }
            )
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = GrapeGreen, fontSize = Gioui.Type.caption)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Трассировка CPU — учебная модель внутри GSYNK: она ничего не запускает в системе, не трогает загрузчик, разделы и прошивку.",
            color = TextDim,
            fontSize = Gioui.Type.caption
        )
    }
}
