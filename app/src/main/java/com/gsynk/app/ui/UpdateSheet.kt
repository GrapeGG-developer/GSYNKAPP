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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.GioBrush

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

    // Панель открылась — значит человек смотрит именно сюда: проверяем сразу и по-настоящему.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.checkUpdate(silent = true, force = true)
    }
    val checking by vm.updateChecking.collectAsStateWithLifecycle()
    val status by vm.updateStatus.collectAsStateWithLifecycle()

    var shared by remember { mutableStateOf(com.gsynk.app.core.UpdateShare.last) }

    GrapeScreen(title = "Обновления", subtitle = "версия ${BuildConfig.VERSION_NAME}", onBack = onDismiss) {
        // ————— версию принесли касанием NFC или связью «GSYNK рядом» —————
        val carried = shared
        if (carried != null) {
            SurfaceCard(borderColor = com.gsynk.app.ui.theme.GrapeCyan.copy(alpha = 0.5f)) {
                androidx.compose.material3.Text(
                    text = "Обновление передано вам",
                    color = com.gsynk.app.ui.theme.GrapeCyan,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = com.gsynk.app.core.UpdateShare.describe(carried),
                    color = com.gsynk.app.ui.theme.TextDim,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                            .background(com.gsynk.app.ui.theme.CardBg)
                            .border(1.dp, com.gsynk.app.ui.theme.ChipBorder, androidx.compose.ui.graphics.RectangleShape)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(carried.url)
                                        )
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            "Скачать APK",
                            color = com.gsynk.app.ui.theme.GrapeGreen,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                            .background(com.gsynk.app.ui.theme.CardBg)
                            .border(1.dp, com.gsynk.app.ui.theme.ChipBorder, androidx.compose.ui.graphics.RectangleShape)
                            .clickable { vm.checkUpdate() },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            "Проверить",
                            color = com.gsynk.app.ui.theme.GrapeCyan,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    text = "Забыть это предложение",
                    color = com.gsynk.app.ui.theme.TextDim,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
                    modifier = Modifier.clickable {
                        com.gsynk.app.core.UpdateShare.clearLast()
                        shared = null
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ————— чем поделиться: передать другу то, что нашли сами —————
        if (update?.updateAvailable == true) {
            SurfaceCard(borderColor = com.gsynk.app.ui.theme.GrapeVioletSoft.copy(alpha = 0.45f)) {
                androidx.compose.material3.Text(
                    text = "Передать обновление другу",
                    color = com.gsynk.app.ui.theme.GrapeVioletSoft,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = "Приложите телефоны (касание NFC) — в метке уедут версия и ссылка на APK, " +
                        "а друг скачает файл сам. Или свяжитесь по Bluetooth/Wi-Fi в разделе «GSYNK рядом».",
                    color = com.gsynk.app.ui.theme.TextDim,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        SurfaceCard(borderColor = if (update?.updateAvailable == true) GrapeGreen.copy(alpha = 0.5f) else CardBorder) {
            // Состояние самой проверки: видно, что приложение не спит.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (checking) "проверяю версию…" else status.ifBlank { "готов к проверке" },
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                    if (update?.unchanged == true) {
                        Text(
                            text = "Ответ GitHub «ничего не изменилось» — лимит обращений не потрачен",
                            color = TextDim,
                            fontSize = Gioui.Type.caption
                        )
                    }
                }
                if (checking) PulseDot(GrapeCyan)
            }
            Spacer(Modifier.height(10.dp))
            when {
                update == null -> {
                    Text("Проверяю наличие новой версии…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                }

                update!!.updateAvailable -> {
                    Text(
                        text = "Вышла версия ${update!!.latestTag}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light).copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Установлено: ${BuildConfig.VERSION_NAME}",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(14.dp))

                    if (downloading) {
                        Text("Скачиваю: $progress%", color = GrapeVioletSoft, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        BarIndicator(percent = progress, brush = GioBrush.Green)
                    } else {
                        PrimaryButton(
                            text = if (update!!.webOnly) "Открыть страницу релиза" else "Скачать и обновить",
                            onClick = { vm.downloadAndInstall() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            SecondaryButton(
                                text = "Напомнить позже",
                                onClick = { vm.postponeUpdate(); onDismiss() },
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                text = "Пропустить версию",
                                onClick = { vm.dismissUpdate(); onDismiss() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Установка «поверх» сохранит все правила, настройки и игры — " +
                                "подпись та же, данные не теряются.",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }

                else -> {
                    Text(
                        text = if (update!!.ok) "Обновлений нет" else "Проверка не прошла",
                        color = if (update!!.ok) TextPrimary else GrapePink,
                        fontSize = Gioui.Type.tileLive,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = update!!.message,
                        color = if (update!!.ok) TextDim else GrapePink,
                        fontSize = Gioui.Type.caption
                    )
                    if (update!!.rateLimitedUntil > 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "GitHub ограничил частоту обращений. Приложение подождёт и " +
                                "проверит само — вручную жать не нужно.",
                            color = TextDim,
                            fontSize = Gioui.Type.caption
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SecondaryButton(
                            text = "Повторить",
                            onClick = { vm.checkUpdate(force = true) },
                            modifier = Modifier.weight(1f)
                        )
                        if (update!!.pageUrl.isNotBlank()) {
                            SecondaryButton(
                                text = "Страница релиза",
                                onClick = { UpdateChecker.open(context, update!!.pageUrl) }
                            )
                        }
                    }
                    if (settings.seenVersion.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Версия " + settings.seenVersion + " отмечена как пропущенная.",
                            color = TextDim,
                            fontSize = Gioui.Type.caption
                        )
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(
                            text = "Вернуть напоминания",
                            onClick = { vm.unskipUpdate() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Раз в минуту, с токеном и меткой ETag — лимит GitHub не тратится",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
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
            Text("Что нового", color = TextSecondary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = update?.notes?.takeIf { it.isNotBlank() }
                    ?: "2.1 — живые плитки и сортировка, компактный остров с новыми позициями, " +
                    "локальная модель с чатом, новый Grape Link, сцены и «хакерский набор» инструментов.",
                color = TextDim,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
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
            .clip(RectangleShape)
            .background(if (checked) GioBrush.Green else GioBrush.Ink)
            .border(1.dp, if (checked) GrapeGreen else ChipBorder, RectangleShape)
            .clickable { onChange() }
            .padding(3.dp),
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // бегунок — квадрат: интерфейс 2.1 полностью острый
        Box(
            Modifier
                .width(22.dp)
                .height(22.dp)
                .background(if (checked) GrapeGreenSoft else CardBg)
        )
    }
}
