package com.gsynk.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.R
import com.gsynk.app.ui.theme.TextDim

/** Прямое приложение быстрых переключателей — без старого промежуточного «Инструменты». */
@Composable
fun QuickActionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val quick: QuickActionsViewModel = viewModel()
    val torch by quick.torch.collectAsStateWithLifecycle()
    val dnd by quick.dnd.collectAsStateWithLifecycle()
    val siren by quick.siren.collectAsStateWithLifecycle()
    val stats by quick.stats.collectAsStateWithLifecycle()
    val message by quick.message.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        quick.startStats()
        onDispose { quick.stopStats() }
    }
    LaunchedEffect(message) {
        message?.let {
            vm.showMessage(it)
            quick.clearMessage()
        }
    }

    GrapeScreen(title = "быстрые переключатели", subtitle = "прямо сейчас, без меню", onBack = onBack) {
        TileRow {
            ToggleTile(
                title = "Фонарик",
                on = torch,
                iconRes = R.drawable.ic_brightness,
                onText = "светит",
                offText = "выключен",
                modifier = Modifier.weight(1f)
            ) { quick.toggleTorch() }
            ToggleTile(
                title = "Тишина",
                on = dnd,
                iconRes = R.drawable.ic_shield,
                onText = "не беспокоить",
                offText = "звук включён",
                modifier = Modifier.weight(1f)
            ) { quick.toggleDnd() }
        }
        Spacer(Modifier.height(10.dp))
        TileRow {
            ToggleTile(
                title = "Сирена",
                on = siren,
                iconRes = R.drawable.ic_volume,
                onText = "громко зовёт",
                offText = "найти телефон",
                modifier = Modifier.weight(1f)
            ) { quick.toggleSiren() }
            LiveTile(
                title = "Вибрация",
                subtitle = "проверка мотора",
                brush = com.gsynk.app.ui.theme.GioBrush.Pink,
                iconRes = R.drawable.ic_bolt,
                liveText = "разок",
                modifier = Modifier.weight(1f)
            ) { quick.vibrate() }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Сейчас в телефоне")
        Spacer(Modifier.height(8.dp))
        SurfaceCard {
            val snapshot = stats
            if (snapshot == null) {
                Text("Считываю показатели…", color = TextDim, fontSize = Gioui.Type.body)
            } else {
                InfoRow("Память", "${snapshot.ramUsedMb} / ${snapshot.ramTotalMb} МБ")
                BarIndicator(percent = snapshot.ramPercent, brush = com.gsynk.app.ui.theme.GioBrush.Violet)
                Spacer(Modifier.height(10.dp))
                InfoRow("Накопитель", "свободно %.1f ГБ".format(snapshot.storageFreeGb))
                BarIndicator(percent = 100 - snapshot.storageUsedPercent, brush = com.gsynk.app.ui.theme.GioBrush.Cyan)
                Spacer(Modifier.height(10.dp))
                InfoRow(
                    "Батарея",
                    snapshot.batteryPercent.toString() + " из 100 • " + "%.1f".format(snapshot.batteryTempC) + "°C"
                )
                BarIndicator(percent = snapshot.batteryPercent, brush = com.gsynk.app.ui.theme.GioBrush.Green)
                Spacer(Modifier.height(10.dp))
                InfoRow("Ядер", snapshot.cores.toString())
                InfoRow("Включён", "${snapshot.uptimeHours} ч")
            }
        }
    }
}
