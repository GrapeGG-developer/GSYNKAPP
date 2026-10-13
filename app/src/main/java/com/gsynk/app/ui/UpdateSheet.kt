package com.gsynk.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.BuildConfig
import com.gsynk.app.core.UpdateChecker
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush

/**
 * Панель обновлений: сама выезжает, когда вышла новая версия, и показывает
 * прогресс скачивания. Пользователю не нужно искать кнопку «проверить».
 */
@Composable
fun UpdateSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val update by vm.update.collectAsStateWithLifecycle()
    val downloading by vm.updateDownloading.collectAsStateWithLifecycle()
    val progress by vm.updateProgress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (update == null) vm.checkUpdate(silent = true)
    }

    GrapeScreen(title = "Обновления", subtitle = "версия ${BuildConfig.VERSION_NAME}", onBack = onDismiss) {
        SurfaceCard(borderColor = if (update?.updateAvailable == true) GrapeGreen.copy(alpha = 0.5f) else CardBorder) {
            when {
                update == null -> {
                    Text("Проверяю наличие новой версии…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                update!!.updateAvailable -> {
                    Text(
                        text = "Вышла версия ${update!!.latestTag}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Установлено: ${BuildConfig.VERSION_NAME}",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(14.dp))

                    if (downloading) {
                        Text("Скачиваю: $progress%", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        BarIndicator(percent = progress, brush = TileBrush.Green)
                    } else {
                        PrimaryButton(
                            text = "Скачать и обновить",
                            onClick = { vm.downloadAndInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Установка «поверх» сохранит все правила, настройки и игры — " +
                                "подпись та же, данные не теряются.",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                else -> {
                    Text("Обновлений нет", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(update!!.message, color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Автопроверка")
        Spacer(Modifier.height(9.dp))
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (settings.updateAuto) "Проверять автоматически" else "Проверять вручную",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Не чаще одного раза в 6 часов, при запуске приложения",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                ToggleDot(settings.updateAuto) { vm.setUpdateAuto(!settings.updateAuto) }
            }
            Spacer(Modifier.height(12.dp))
            GrapeTextField(
                value = settings.updateRepo,
                onValueChange = { vm.setUpdateRepo(it) },
                label = "Репозиторий обновлений"
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(
                    text = "Проверить сейчас",
                    onClick = { vm.checkUpdate() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Открыть GitHub",
                    onClick = { UpdateChecker.openRepo(context, settings.updateRepo) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SurfaceCard {
            Text("Что нового", color = TextSecondary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = update?.notes?.takeIf { it.isNotBlank() }
                    ?: "2.1 — живые плитки и сортировка, компактный остров с новыми позициями, " +
                    "локальная модель с чатом, новый Grape Link, сцены и «хакерский набор» инструментов.",
                color = TextDim,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Компактный переключатель-точка: меньше и аккуратнее системного Switch. */
@Composable
fun ToggleDot(checked: Boolean, onChange: () -> Unit) {
    Row(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) TileBrush.Green else TileBrush.Ink)
            .border(1.dp, if (checked) GrapeGreen else ChipBorder, RoundedCornerShape(50))
            .clickable { onChange() }
            .padding(3.dp),
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(22.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) GrapeGreenSoft else CardBg)
        )
    }
}
