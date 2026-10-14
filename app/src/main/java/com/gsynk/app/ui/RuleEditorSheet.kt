@file:OptIn(ExperimentalMaterial3Api::class)

package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.Rule
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGradient
import com.gsynk.app.ui.theme.GrapeGradientSoft
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletDeep
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Шторка создания и редактирования правила. */
@Composable
fun RuleEditorSheet(
    initial: Rule?,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: ((Rule) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val base = initial ?: Rule(name = "Новое правило", hour = 8, minute = 0)
    var draft by remember { mutableStateOf(base) }
    var timePickerFor by remember { mutableStateOf<PickerTarget?>(null) }
    var weekGridOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = CardBg,
        scrimColor = Color(0xE60A0514),
        dragHandle = null
    ) {
        Box(Modifier.imePadding()) {
            Column(
                Modifier
                    .padding(horizontal = 18.dp)
                    .padding(top = 10.dp, bottom = 26.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetGrabber() }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (initial == null) "Новое правило" else "Правило",
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light)
                        )
                        Text(
                            text = buildString {
                                append(draft.timeLabel)
                                if (draft.useInterval) append(" • ${draft.intervalLabel}")
                                append(" • ${draft.daysLabel()}")
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                        )
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RectangleShape)
                            .background(CardBgAlt)
                            .border(1.dp, ChipBorder, RectangleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Закрыть",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)) },
                    label = { Text("Название") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GrapeViolet,
                        unfocusedBorderColor = ChipBorder,
                        focusedContainerColor = CardBgAlt,
                        unfocusedContainerColor = CardBgAlt,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = GrapeGreen,
                        focusedLabelColor = GrapeVioletSoft,
                        unfocusedLabelColor = TextDim
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // ————— время —————
                SectionTitle("Время срабатывания")
                Spacer(Modifier.height(8.dp))
                SurfaceCard(corner = 20.dp, onClick = { timePickerFor = PickerTarget.MAIN }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            tint = GrapeVioletSoft,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        GradientText(
                            text = draft.timeLabel,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = Metro.Type.tileLive, fontWeight = FontWeight.Light),
                            brush = GrapeGradientSoft
                        )
                        Spacer(Modifier.weight(1f))
                        Text("▾", color = TextDim, style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ————— дни недели —————
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Дни недели")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "точный выбор",
                        color = GrapeVioletSoft,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal),
                        modifier = Modifier
                            .clip(RectangleShape)
                            .clickable { weekGridOpen = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Rule.DAY_SHORT.forEachIndexed { index, label ->
                        val day = index + 1
                        DayChip(
                            label = label,
                            selected = draft.days.contains(day),
                            modifier = Modifier.weight(1f),
                            onClick = { draft = draft.withDay(day, !draft.days.contains(day)) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagChip("Будни") { draft = draft.copy(days = listOf(1, 2, 3, 4, 5)) }
                    TagChip("Выходные") { draft = draft.copy(days = listOf(6, 7)) }
                    TagChip("Вся неделя") { draft = draft.copy(days = listOf(1, 2, 3, 4, 5, 6, 7)) }
                }

                // ————— точные даты —————
                if (draft.hasDates) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Только эти даты (разово)")
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "очистить",
                            color = GrapeVioletSoft,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal),
                            modifier = Modifier
                                .clip(RectangleShape)
                                .clickable { draft = draft.withDates(emptyList()) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        draft.validDates.chunked(3).forEach { rowDates ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowDates.forEach { date ->
                                    TagChip(
                                        text = "%02d.%02d".format(date.dayOfMonth, date.monthValue),
                                        selected = true,
                                        onClick = { draft = draft.withDate(date, false) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "После срабатывания разовое правило выключится само",
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ————— повтор интервалом —————
                SurfaceCard(corner = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_bolt),
                            contentDescription = null,
                            tint = GrapeGreenSoft,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Повторять в течение дня", color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium))
                            Text(
                                text = if (draft.useInterval) draft.intervalLabel else "выключено",
                                color = TextDim,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                            )
                        }
                        ToggleDot(
                            checked = draft.useInterval,
                            onChange = { draft = draft.copy(useInterval = !draft.useInterval) }
                        )
                    }

                    if (draft.useInterval) {
                        Spacer(Modifier.height(12.dp))
                        SurfaceCard(corner = 16.dp, contentPadding = PaddingValues(12.dp), onClick = { timePickerFor = PickerTarget.INTERVAL_START }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Первый раз в", color = TextSecondary, style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal))
                                Spacer(Modifier.weight(1f))
                                GradientText(
                                    text = draft.intervalStartLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium),
                                    brush = GrapeGradientSoft
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Каждые…", color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 4, 6, 8, 12).forEach { h ->
                                TagChip(
                                    text = "$h ч",
                                    selected = draft.intervalHours == h && draft.intervalMinutes == 0,
                                    onClick = { draft = draft.copy(intervalHours = h, intervalMinutes = 0) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 15, 30, 45).forEach { m ->
                                TagChip(
                                    text = "$m мин",
                                    selected = draft.intervalMinutes == m,
                                    onClick = { draft = draft.copy(intervalMinutes = m) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Правило повторится: ${draft.intervalStartLabel}, далее ${draft.intervalLabel}",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                ValueBlock(
                    icon = ImageVector.vectorResource(R.drawable.ic_brightness),
                    title = "Яркость экрана",
                    value = draft.brightness,
                    enabled = draft.useBrightness,
                    brush = GrapeGradientSoft,
                    accent = GrapeVioletSoft,
                    onEnabledChange = { draft = draft.copy(useBrightness = it) },
                    onValueChange = { draft = draft.copy(brightness = it) }
                )

                Spacer(Modifier.height(12.dp))

                ValueBlock(
                    icon = ImageVector.vectorResource(R.drawable.ic_volume),
                    title = "Громкость медиа",
                    value = draft.volume,
                    enabled = draft.useVolume,
                    brush = GrapeGradient,
                    accent = GrapeGreenSoft,
                    onEnabledChange = { draft = draft.copy(useVolume = it) },
                    onValueChange = { draft = draft.copy(volume = it) }
                )

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            val cleaned = if (draft.isOneTime) {
                                // разовое правило: дни берём из дат, чтобы недельный повтор не сработал лишний раз
                                draft.copy(days = draft.effectiveDays().toList().sorted())
                            } else {
                                draft
                            }
                            onSave(cleaned)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = draft.days.isNotEmpty() || draft.hasDates
                    )
                    if (initial != null && onDelete != null) {
                        SecondaryButton(
                            text = "Удалить",
                            icon = Icons.Filled.Delete,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { onDelete(initial) }
                        )
                    }
                }

                if (draft.days.isEmpty() && !draft.hasDates) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Выберите дни недели или точные даты",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Normal)
                    )
                }
            }
        }
    }

    // ————— выбор времени —————
    timePickerFor?.let { target ->
        val initialHour = if (target == PickerTarget.MAIN) draft.hour else draft.intervalStartHour
        val initialMinute = if (target == PickerTarget.MAIN) draft.minute else draft.intervalStartMinute
        val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePickerFor = null },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(if (target == PickerTarget.MAIN) "Время срабатывания" else "Первый запуск")
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    draft = if (target == PickerTarget.MAIN) {
                        draft.copy(hour = state.hour, minute = state.minute)
                    } else {
                        draft.copy(
                            intervalStartHour = state.hour,
                            intervalStartMinute = state.minute,
                            hour = state.hour,
                            minute = state.minute
                        )
                    }
                    timePickerFor = null
                }) {
                    Text("Готово", color = GrapeGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerFor = null }) {
                    Text("Отмена", color = TextSecondary)
                }
            }
        )
    }

    // ————— точный выбор дней: недели месяца —————
    if (weekGridOpen) {
        WeekPickerDialog(
            initialDates = draft.validDates,
            onDismiss = { weekGridOpen = false },
            onConfirm = { dates ->
                draft = draft.withDates(dates)
                weekGridOpen = false
            }
        )
    }
}

private enum class PickerTarget { MAIN, INTERVAL_START }

/**
 * Точный выбор дней: для каждого дня недели можно отметить «любой», 1-ю, 2-ю, 3-ю,
 * 4-ю или последнюю неделю месяца — это раскрывается в конкретные даты.
 */
@Composable
private fun WeekPickerDialog(
    initialDates: List<LocalDate>,
    onDismiss: () -> Unit,
    onConfirm: (List<LocalDate>) -> Unit
) {
    var selected by remember { mutableStateOf(initialDates.toSet()) }
    var monthOffset by remember { mutableIntStateOf(0) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }

    fun datesOf(day: Int, ordinal: Int): List<LocalDate> {
        val first = month.atDay(1)
        val firstMatch = first.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(day)))
        if (!YearMonth.from(firstMatch).equals(month)) return emptyList()
        val all = generateSequence(firstMatch) { it.plusDays(7) }
            .takeWhile { YearMonth.from(it) == month }
            .toList()
        return when (ordinal) {
            0 -> all                       // любой день недели этого месяца
            -1 -> listOfNotNull(all.lastOrNull())  // последний
            else -> listOfNotNull(all.getOrNull(ordinal - 1))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Точные дни месяца") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // переключатель месяца
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthOffset -= 1 }) { Text("‹", color = GrapeVioletSoft) }
                    Text(
                        text = MONTH_NAMES[month.monthValue - 1] + " " + month.year,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { monthOffset += 1 }) { Text("›", color = GrapeVioletSoft) }
                }

                Rule.DAY_FULL.forEachIndexed { index, dayName ->
                    val day = index + 1
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(dayName, color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal))
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val options = listOf(0 to "все", 1 to "1-я", 2 to "2-я", 3 to "3-я", 4 to "4-я", -1 to "посл.")
                            options.forEach { (ordinal, label) ->
                                val optionsDates = datesOf(day, ordinal)
                                val allSelected = optionsDates.isNotEmpty() && optionsDates.all { it in selected }
                                TagChip(
                                    text = label,
                                    selected = allSelected,
                                    onClick = {
                                        if (optionsDates.isEmpty()) return@TagChip
                                        selected = if (allSelected) {
                                            selected - optionsDates.toSet()
                                        } else {
                                            selected + optionsDates.toSet()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (selected.isEmpty()) {
                        "Ничего не выбрано"
                    } else {
                        "Выбрано: " + selected.sorted().joinToString(", ") {
                            "%02d.%02d".format(it.dayOfMonth, it.monthValue)
                        }
                    },
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Metro.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.sorted()) }) {
                Text("Готово", color = GrapeGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        }
    )
}

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

@Composable
private fun ValueBlock(
    icon: ImageVector,
    title: String,
    value: Int,
    enabled: Boolean,
    brush: androidx.compose.ui.graphics.Brush,
    accent: Color,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit
) {
    SurfaceCard(corner = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            AnimatedPercent(
                percent = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = Metro.Type.body, fontWeight = FontWeight.Medium),
                color = if (enabled) TextPrimary else TextDim
            )
            Spacer(Modifier.width(10.dp))
            ToggleDot(checked = enabled, onChange = { onEnabledChange(!enabled) })
        }
        Spacer(Modifier.height(8.dp))
        NeoSlider(
            value = value,
            onValueChange = onValueChange,
            brush = brush,
            enabled = enabled
        )
    }
}
