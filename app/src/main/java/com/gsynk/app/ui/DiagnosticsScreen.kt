package com.gsynk.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.AppLog
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import com.gsynk.app.ui.theme.TileBrush

/**
 * Диагностика: отчёты о сбоях, журнал событий и состояние приложения.
 *
 * Всё лежит на телефоне. Отправка куда-либо — только по кнопке и только если
 * пользователь сам вставил токен: по умолчанию отчёты никуда не уходят.
 */
@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()

    val analysis = remember(reports) { AppLog.analysis(context) }
    var opened by remember { mutableStateOf<AppLog.ReportItem?>(null) }
    var openedText by remember { mutableStateOf("") }
    var showJournal by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshReports()
        vm.markCrashSeen()
    }

    GrapeScreen(
        title = "Диагностика",
        subtitle = "сбоев за всё время: ${AppLog.crashTotal(context)}",
        onBack = onBack
    ) {
        // ————— состояние приложения —————
        SurfaceCard(borderColor = CardBorder) {
            Text("Состояние", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoRow("Версия", com.gsynk.app.BuildConfig.VERSION_NAME)
            InfoRow("Отчётов на телефоне", reports.size.toString())
            InfoRow("Сбоев за всё время", AppLog.crashTotal(context).toString())
            InfoRow("Модель ИИ", com.gsynk.app.core.LocalLlm.modelFile(context)?.name ?: "не импортирована")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryButton(
                    text = "Собрать отчёт",
                    onClick = { vm.collectReport("Отчёт по кнопке из раздела «Диагностика»") },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Журнал",
                    onClick = { showJournal = !showJournal }
                )
            }
        }

        // ————— анализатор: что именно ломается и как часто —————
        Spacer(Modifier.height(14.dp))
        SectionTitle("Анализ сбоев")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = if (analysis.isEmpty()) CardBorder else GrapeRed) {
            Text(
                text = com.gsynk.app.core.CrashAnalyzer.verdict(analysis),
                color = if (analysis.isEmpty()) TextPrimary else GrapeRed,
                style = MaterialTheme.typography.titleSmall
            )
            if (analysis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                analysis.forEachIndexed { index, group ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .clip(RectangleShape)
                                .background(GrapeRed.copy(alpha = 0.16f))
                                .border(1.dp, GrapeRed.copy(alpha = 0.45f), RectangleShape)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(group.label, color = GrapeRed, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(group.signature, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            if (group.lastStamp.isNotEmpty()) {
                                Text("последний: ${group.lastStamp}", color = TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Одинаковые падения считаются вместе — чинить нужно верхнее.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Приложение ещё ни разу не падало. Если это случится, отчёт и разбор появятся здесь сами.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (showJournal) {
            Spacer(Modifier.height(12.dp))
            SurfaceCard {
                Text("Журнал событий", color = TextSecondary, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                MonoBlock(text = journal.ifBlank { "пока пусто" })
            }
        }

        // ————— отправка —————
        Spacer(Modifier.height(14.dp))
        SectionTitle("Отправка отчётов")
        Spacer(Modifier.height(9.dp))
        SurfaceCard(borderColor = CardBorder) {
            SettingRow(
                title = "Собирать отчёты о сбоях",
                subtitle = "файл на телефоне, никуда не уходит сам",
                checked = settings.reportEnabled,
                onChange = { vm.setReportEnabled(it) }
            )
            SettingRow(
                title = "Отправлять автоматически после сбоя",
                subtitle = "нужны токен и репозиторий ниже",
                checked = settings.reportAutoUpload,
                onChange = { vm.setReportAutoUpload(it) }
            )
            Spacer(Modifier.height(8.dp))
            GrapeTextField(
                value = settings.reportRepo,
                onValueChange = { vm.setReportRepo(it) },
                label = "Репозиторий для отчётов"
            )
            Spacer(Modifier.height(10.dp))
            GrapeTextField(
                value = settings.reportToken,
                onValueChange = { vm.setReportToken(it) },
                label = "Токен GitHub (право записи)",
                masked = !tokenVisible
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Токен хранится только на телефоне и используется лишь для загрузки отчётов " +
                    "в папку crash-reports/. Перед отправкой из текста вырезаются токены, адреса " +
                    "и длинные числа.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryButton(
                    text = if (uploading) "Отправляю…" else "Отправить все",
                    onClick = { vm.uploadReports() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Показать токен",
                    onClick = { tokenVisible = !tokenVisible }
                )
            }
            if (tokenVisible && settings.reportToken.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = settings.reportToken,
                    color = GrapeVioletSoft,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (settings.reportLastUpload > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Последняя отправка: " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US)
                        .format(java.util.Date(settings.reportLastUpload)),
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // ————— список отчётов —————
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Отчёты",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (reports.isNotEmpty()) {
                SortChip("Удалить все", selected = false) { vm.clearReports() }
            }
        }
        Spacer(Modifier.height(9.dp))

        if (reports.isEmpty()) {
            SurfaceCard {
                Text(
                    text = "Отчётов нет — значит и сбоев не было. Если что-то работает неправильно, " +
                        "нажмите «Собрать отчёт»: в него попадут устройство, настройки, доступы " +
                        "и последние события.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        reports.forEach { item ->
            SurfaceCard(borderColor = if (item.isCrash) GrapeRed.copy(alpha = 0.45f) else CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (item.isCrash) "Сбой · ${item.title}" else "Отчёт · ${item.title}",
                            color = if (item.isCrash) GrapeRed else TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${item.time} • ${item.sizeKb} КБ" +
                                if (com.gsynk.app.core.ReportUploader.isUploaded(item.file)) " • отправлен" else "",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    SortChip(
                        label = if (opened?.file == item.file) "Скрыть" else "Открыть",
                        selected = opened?.file == item.file
                    ) {
                        if (opened?.file == item.file) {
                            opened = null
                        } else {
                            opened = item
                            openedText = AppLog.read(item.file)
                        }
                    }
                }

                if (opened?.file == item.file) {
                    Spacer(Modifier.height(10.dp))
                    MonoBlock(text = openedText)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SortChip("Скопировать", selected = false) {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("GSYNK", openedText))
                            vm.showMessage("Отчёт скопирован")
                        }
                        SortChip("Поделиться", selected = false) {
                            shareReport(context, item.file)
                        }
                        SortChip("Отправить", selected = false) { vm.uploadReport(item) }
                        SortChip("Удалить", selected = false) {
                            opened = null
                            vm.deleteReport(item)
                        }
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
        }

        Spacer(Modifier.height(6.dp))
        SurfaceCard {
            Text(
                text = "Что попадает в отчёт: модель телефона, версия Android и GSYNK, память, " +
                    "доступы, настройки (без паролей), счётчик правил и последние 400 событий " +
                    "приложения. Что не попадает: содержимое ваших сообщений, пароли и файлы модели.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Моноширинный блок с текстом отчёта: свой скролл по вертикали и горизонтали. */
@Composable
private fun MonoBlock(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RectangleShape)
            .background(CardBg)
            .border(1.dp, ChipBorder, RectangleShape)
            .padding(10.dp)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = text,
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** Поделиться файлом отчёта через системный «Поделиться». */
private fun shareReport(context: android.content.Context, file: java.io.File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GSYNK: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
    }
}
