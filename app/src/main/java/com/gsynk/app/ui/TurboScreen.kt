package com.gsynk.app.ui

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.ChipInfo
import com.gsynk.app.core.RealtimeAi
import com.gsynk.app.core.TurboState
import com.gsynk.app.ui.theme.GrapeAmber
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TileBrush

/**
 * Экран нагрузки: телефон считает по-настоящему.
 *
 * Процессор грузится перемножением матриц на всех ядрах, видеоускоритель рисует
 * поле шейдером, а отдельная нейросеть работает в это же время — и переливание
 * фона в приложении идёт от её ответов, а не от таймера.
 */
@Composable
fun TurboScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val active by TurboState.active.collectAsState()
    val gflops by TurboState.cpuGflops.collectAsState()
    val fps by TurboState.gpuFps.collectAsState()
    val operations by TurboState.totalOperations.collectAsState()
    val mode by TurboState.aiMode.collectAsState()
    val confidence by TurboState.aiConfidence.collectAsState()
    val loss by TurboState.aiLoss.collectAsState()
    val temp by TurboState.batteryTemp.collectAsState()

    DisposableEffect(Unit) {
        onDispose { /* нагрузка продолжает идти, пока её не выключат вручную */ }
    }

    GrapeScreen(
        title = "Нагрузка",
        subtitle = ChipInfo.summary(context),
        onBack = onBack
    ) {
        // ————— поле на видеоускорителе —————
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            GpuField(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                iterations = 48,
                reportFps = true,
                speed = if (active) 1f else 0.35f
            )
            Text(
                text = if (active) "шейдер считает каждый пиксель" else "поле спит · нажмите «Включить»",
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .padding(10.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        PrimaryButton(
            text = if (active) "Выключить нагрузку" else "Включить максимальную нагрузку",
            onClick = { vm.toggleTurbo() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Нагрузка честная: все ядра процессора считают матрицы, видеоускоритель " +
                "рисует шейдер, нейросеть учится на ходу. Ничего не скачивается и не уходит в сеть. " +
                "Телефон при этом греется — при 45 °C нагрузка выключится сама.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(14.dp))
        SectionTitle("Что происходит сейчас")
        Spacer(Modifier.height(9.dp))

        SurfaceCard(borderColor = if (active) GrapeGreen.copy(alpha = 0.5f) else TextDim.copy(alpha = 0.3f)) {
            InfoRow("Состояние", if (active) "считает" else "простаивает")
            InfoRow("Процессор", "%.1f млрд операций в секунду".format(gflops))
            InfoRow("Ядер задействовано", Runtime.getRuntime().availableProcessors().toString())
            InfoRow("Видеоускоритель", "${fps.toInt()} кадров в секунду")
            InfoRow("Всего посчитано", "${operations / 1_000_000} млн операций")
            InfoRow("Температура батареи", "%.1f °C".format(temp))
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Нейросеть на телефоне")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.45f)) {
            Text(
                text = "режим: ${RealtimeAi.modeName(mode)}",
                color = if (active) GrapeCyan else TextDim,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("Уверенность", "${(confidence * 100).toInt()}%")
            InfoRow("Ошибка обучения", "%.4f".format(loss))
            InfoRow("Слои", "8 → 24 → 16 → 3")
            InfoRow("Операций за прогон", "${RealtimeAi().operationsPerRun}")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сеть никуда не отправляет данные: она живёт в памяти приложения и " +
                    "дообучается по вашим привычкам — нагрузке, памяти, времени суток. " +
                    "От её ответа зависит переливание фона: спокойный режим — медленное, " +
                    "энергичный — быстрое, ночной — почти без движения.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Железо")
        Spacer(Modifier.height(9.dp))
        SurfaceCard {
            InfoRow("Платформа", "${ChipInfo.vendor()} ${ChipInfo.model()}")
            InfoRow("Ядер", Runtime.getRuntime().availableProcessors().toString())
            InfoRow("Память приложения", "${Runtime.getRuntime().maxMemory() / 1024 / 1024} МБ доступно")
            InfoRow("Режим поля", if (active) "максимальный" else "спокойный")
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SortChip("Сбросить счётчики", selected = false) { TurboState.reset() }
            SortChip("Проверить железо", selected = false) { }
        }
        Spacer(Modifier.height(24.dp))
    }
}
