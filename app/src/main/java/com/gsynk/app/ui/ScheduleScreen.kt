package com.gsynk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleSort
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Отдельный экран категории «расписание»: на главном остаются только сводка и живые плитки. */
@Composable
fun ScheduleScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Rule) -> Unit
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sortedRules = remember(rules, settings.ruleSort) { RuleSort.apply(rules, settings.ruleSort) }
    val comingRuleId = remember(rules) { nextRuleId(rules) }
    val coming = remember(rules) { nextOccurrence(rules) }

    GrapeScreen(
        title = "расписание",
        subtitle = "яркость и громкость по дням и времени",
        onBack = onBack
    ) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionTitle("автопилот")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Применять правила автоматически", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                    Text(
                        text = coming?.let { "ближайшее изменение через " + humanDelta(ZonedDateTime.now(), it) } ?: "правил пока нет",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
                Switch(checked = settings.autopilot, onCheckedChange = { vm.setAutopilot(it) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Уведомления", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (settings.notifications) "напоминать о правилах и событиях" else "тишина",
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
                Switch(checked = settings.notifications, onCheckedChange = { vm.setNotifications(it) })
            }
            ThinDivider()
            InfoRow("Активно правил", rules.count { it.enabled }.toString() + " из " + rules.size)
            InfoRow("Ближайшее", coming?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) ?: "—")
        }
        Spacer(Modifier.height(12.dp))

        SectionTitle("правила")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RuleSort.ALL.forEach { mode ->
                SortChip(
                    label = RuleSort.label(mode),
                    selected = settings.ruleSort == mode
                ) { vm.setRuleSort(mode) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (sortedRules.isEmpty()) {
            EmptyState(onAdd = onCreate)
        } else {
            sortedRules.forEach { rule ->
                RuleCard(
                    rule = rule,
                    isNext = rule.id == comingRuleId,
                    onEdit = { onEdit(rule) },
                    onApply = { vm.applyNow(rule) },
                    onDuplicate = { vm.duplicate(rule) },
                    onToggle = { vm.setEnabled(rule, it) },
                    onDelete = { vm.delete(rule) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        AddRuleTile(modifier = Modifier.fillMaxWidth(), onClick = onCreate)
        Spacer(Modifier.height(10.dp))
        Text(
            "Стартовый список правил пуст. Каждое правило задаёт дни, время, системную яркость и громкость отдельными процентами.",
            color = TextDim,
            fontSize = Gioui.Type.caption
        )
    }
}
