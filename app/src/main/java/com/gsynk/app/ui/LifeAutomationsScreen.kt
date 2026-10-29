package com.gsynk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.LifeAutomations
import com.gsynk.app.core.Rule
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary

@Composable
fun LifeAutomationsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val totalSpecs = remember { LifeAutomations.ALL.sumOf { it.specs.size } }

    GrapeScreen(
        title = "автоматизации жизни",
        subtitle = LifeAutomations.ALL.size.toString() + " наборов · " + totalSpecs + " правил",
        onBack = onBack
    ) {
        SurfaceCard(Modifier.fillMaxWidth(), borderColor = GrapeGreen.copy(alpha = 0.45f)) {
            Text("Школа и дом — как вы просили", color = TextPrimary, fontSize = Gioui.Type.groupHeader, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "В 07:00 школьный набор делает экран 28%, громкость 0% и включает «не беспокоить», " +
                    "чтобы телефон не светился и не звучал на виду. Дома в 15:00 — 85% яркости и 80% звука для YouTube. " +
                    "Следующее правило само сменяет предыдущее, отдельное время окончания не нужно. Кнопка в карточке может сразу применить последнюю наступившую сцену.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Открыть расписание", onClick = onOpenSchedule, modifier = Modifier.weight(1f))
                SecondaryButton(
                    text = if (settings.autopilot) "Автопилот включён" else "Включить автопилот",
                    onClick = { vm.setAutopilot(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("готовые наборы")
        Spacer(Modifier.height(8.dp))

        LifeAutomations.ALL.forEach { pack ->
            LifeAutomationPackCard(
                pack = pack,
                installed = LifeAutomations.isInstalled(rules, pack),
                enabled = LifeAutomations.isEnabled(rules, pack),
                onInstall = { vm.installLifeAutomation(pack.id) },
                onToggle = { vm.setLifeAutomationEnabled(pack.id, it) },
                onApplyNow = { vm.applyLifeAutomationNow(pack.id) }
            )
            Spacer(Modifier.height(10.dp))
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text("Как подстроить под себя", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Набор создаёт обычные правила. После включения откройте расписание и поменяйте любое время, дни, " +
                    "яркость или громкость под свои уроки. Повторное включение набора обновит его правила и вернёт исходные значения; ручные правила не тронутся.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }
    }
}

@Composable
private fun LifeAutomationPackCard(
    pack: LifeAutomations.Pack,
    installed: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onApplyNow: () -> Unit
) {
    SurfaceCard(
        Modifier.fillMaxWidth(),
        borderColor = if (enabled) GrapeGreen.copy(alpha = 0.55f) else CardBorder
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(pack.emoji, fontSize = Gioui.Type.tileLive)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pack.title, color = TextPrimary, fontSize = Gioui.Type.groupHeader, fontWeight = FontWeight.SemiBold)
                Text(pack.subtitle, color = TextSecondary, fontSize = Gioui.Type.caption)
            }
            TagChip(text = when {
                enabled -> "работает"
                installed -> "выключен"
                else -> pack.specs.size.toString() + " правил"
            })
        }
        Spacer(Modifier.height(10.dp))
        pack.specs.forEach { spec ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "%02d:%02d".format(spec.hour, spec.minute),
                    color = GrapeGreen,
                    fontSize = Gioui.Type.caption,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(46.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(spec.name, color = TextPrimary, fontSize = Gioui.Type.caption, fontWeight = FontWeight.Medium)
                    Text(
                        spec.daysLabel() + " · яркость " + spec.brightness + "% · звук " + spec.volume + "%" +
                            when (spec.silentMode) {
                                1 -> " · не беспокоить"
                                0 -> " · звук разрешён"
                                else -> ""
                            },
                        color = TextDim,
                        fontSize = Gioui.Type.caption
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!installed) {
                PrimaryButton(text = "Включить набор и автопилот", onClick = onInstall, modifier = Modifier.weight(1f))
            } else if (enabled) {
                SecondaryButton(text = "Выключить набор", onClick = { onToggle(false) }, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Обновить правила", onClick = onInstall, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton(text = "Включить снова", onClick = { onToggle(true) }, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Сбросить набор", onClick = onInstall, modifier = Modifier.weight(1f))
            }
        }
        if (installed) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Применить текущую сцену сейчас",
                onClick = onApplyNow,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun LifeAutomations.Spec.daysLabel(): String = Rule(days = days).daysLabel()
