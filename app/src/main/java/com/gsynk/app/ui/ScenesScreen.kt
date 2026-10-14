package com.gsynk.app.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TileBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сцены — автоматизации «одной кнопкой».
 *
 * Каждая сцена сразу выставляет яркость, громкость и режим «не беспокоить»,
 * без создания правил и расписания. Это то, что нужно нажать прямо сейчас.
 */
private data class Scene(
    val id: String,
    val title: String,
    val subtitle: String,
    val brightness: Int,
    val volume: Int,
    val dnd: Boolean,
    val icon: Int,
    val brush: androidx.compose.ui.graphics.Brush
)

private val SCENES = listOf(
    Scene(
        id = "night",
        title = "Ночь",
        subtitle = "минимум света и звука",
        brightness = 8,
        volume = 0,
        dnd = true,
        icon = R.drawable.ic_schedule,
        brush = TileBrush.Violet
    ),
    Scene(
        id = "study",
        title = "Учёба",
        subtitle = "тихо и не ярко",
        brightness = 45,
        volume = 20,
        dnd = true,
        icon = R.drawable.ic_shield,
        brush = TileBrush.Blue
    ),
    Scene(
        id = "street",
        title = "Улица",
        subtitle = "максимум яркости",
        brightness = 100,
        volume = 80,
        dnd = false,
        icon = R.drawable.ic_brightness,
        brush = TileBrush.Amber
    ),
    Scene(
        id = "movie",
        title = "Кино",
        subtitle = "мягкий свет, громкий звук",
        brightness = 15,
        volume = 65,
        dnd = true,
        icon = R.drawable.ic_volume,
        brush = TileBrush.Pink
    )
)

@Composable
fun ScenesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    fun apply(scene: Scene) {
        scope.launch {
            val brightnessOk = withContext(Dispatchers.IO) {
                RuleEngine.setBrightnessPercent(context, scene.brightness)
            }
            val volumeOk = withContext(Dispatchers.IO) {
                RuleEngine.setVolumePercent(context, scene.volume)
            }
            val dndOk = withContext(Dispatchers.IO) { AutomationActions.setDnd(context, scene.dnd) }
            status = buildString {
                append("«").append(scene.title).append("»: ")
                append(if (brightnessOk) "яркость ${scene.brightness}% " else "нет доступа к яркости ")
                append(if (volumeOk) "• звук ${scene.volume}% " else "• звук не изменён ")
                append(if (dndOk) (if (scene.dnd) "• тишина" else "• звук включён") else "• нет доступа к тишине")
            }
        }
    }

    GrapeScreen(title = "Сцены", subtitle = "автоматизации одним касанием", onBack = onBack) {
        Text(
            text = "Сцена — это набор настроек, который применяется мгновенно. " +
                "Отличается от правил тем, что не ждёт времени: нажали и получили.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(14.dp))

        SCENES.forEach { scene ->
            LiveTile(
                title = scene.title,
                subtitle = scene.subtitle,
                brush = scene.brush,
                iconRes = scene.icon,
                liveText = "яркость ${scene.brightness}% • звук ${scene.volume}%" +
                    if (scene.dnd) " • тишина" else "",
                height = 96.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { apply(scene) }
            )
            Spacer(Modifier.height(11.dp))
        }

        Spacer(Modifier.height(4.dp))
        SectionTitle("Поиск телефона")
        Spacer(Modifier.height(9.dp))
        LiveTile(
            title = "Найти телефон",
            subtitle = "громкая сирена и вибрация",
            brush = TileBrush.Red,
            iconRes = R.drawable.ic_bolt,
            liveText = "Позвать",
            height = 96.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    AutomationActions.startSiren()
                    AutomationActions.vibrate(context, 600L)
                }
                status = "Сирена включена — откройте «Инструменты», чтобы выключить"
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            SurfaceCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Выполнено", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                        Text(status, color = com.gsynk.app.ui.theme.TextPrimary, style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
                    }
                }
            }
        }
    }
}
