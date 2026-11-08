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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.BuildConfig
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary

/**
 * Настройки: всё, что не относится к самим правилам.
 *
 * Панель открывается на весь экран и уважает вырезы: содержимое не залезает
 * на строку состояния, а кнопка закрытия всегда на виду.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skin by Skin.values.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AnimatedGrapeBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "настройки",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "GSYNK ${BuildConfig.VERSION_NAME} · SCRIPT",
                    color = TextDim,
                    fontSize = Gioui.Type.caption,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(text = "Назад", onClick = onBack)
            }

            // ————— автопилот и уведомления —————
            SettingRow(
                title = "Автопилот",
                subtitle = "правила применяются сами по расписанию",
                checked = settings.autopilot,
                onChange = { vm.setAutopilot(it) }
            )
            SettingRow(
                title = "Уведомления",
                subtitle = "сообщения о сработавших правилах",
                checked = settings.notifications,
                onChange = { vm.setNotifications(it) }
            )
            SettingRow(
                title = "Синхронизация после перезагрузки",
                subtitle = "телефон сам придёт к последнему правилу",
                checked = settings.syncAfterBoot,
                onChange = { vm.setSyncAfterBoot(it) }
            )

            // ————— обновления —————
            Spacer(Modifier.height(16.dp))
            SectionTitle("Обновления")
            Spacer(Modifier.height(8.dp))
            SettingRow(
                title = "Обновлять автоматически",
                subtitle = "APK скачается сам; Android покажет только системное подтверждение установки",
                checked = settings.updateAuto,
                onChange = { vm.setUpdateAuto(it) }
            )
            Spacer(Modifier.height(6.dp))
            GrapeTextField(
                value = settings.updateRepo,
                onValueChange = { vm.setUpdateRepo(it) },
                label = "Репозиторий обновлений"
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Проверить сейчас",
                onClick = { vm.checkUpdate() },
                modifier = Modifier.fillMaxWidth()
            )

            // ————— Script UI живёт отдельным единым приложением —————
            Spacer(Modifier.height(16.dp))
            SectionTitle("Script — дизайн")
            Spacer(Modifier.height(8.dp))
            SurfaceCard(borderColor = CardBorder) {
                Text("GSYNK Script UI", color = ScriptPalette.Lime, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold))
                Text(
                    "постоянная система: типографика, строки, глифы, чёткая палитра и неизменный каркас. Градиент " + skin.prgOpacity +
                        "%, акцент " + skin.prgGlow + "%, фон " + skin.speed + "%, фокус " + skin.focus + "%",
                    color = TextDim,
                    fontSize = Gioui.Type.caption
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Открыть настройки Script UI",
                    onClick = { Router.page("script") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ————— доступы —————
            Spacer(Modifier.height(16.dp))
            SectionTitle("Доступы и приложение")
            Spacer(Modifier.height(8.dp))
            val snapshot = Permissions.snapshot(context)
            snapshot.items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .clickable { Permissions.open(context, item.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = if (item.granted) TextSecondary else TextPrimary,
                        fontSize = Gioui.Type.body,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (item.granted) "выдано" else "выдать →",
                        color = if (item.granted) TextDim else GrapeVioletSoft,
                        fontSize = Gioui.Type.caption
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(
                    text = "Синхронизировать",
                    onClick = { vm.syncToSchedule() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Вернуть автояркость",
                    onClick = { vm.restoreAutoBrightness() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Правил в расписании: " + (vm.rules.collectAsStateWithLifecycle().value.size) +
                    " • вся работа идёт на телефоне, интернет нужен только для проверки обновлений",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
            }
            .clip(skinShape("card"))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.10f), UiTweaks.accentStart.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.14f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), skinShape("card"))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.Normal
            )
            Text(subtitle, color = TextDim, fontSize = Gioui.Type.caption)
        }
        ToggleDot(checked = checked) { onChange(!checked) }
    }
}
