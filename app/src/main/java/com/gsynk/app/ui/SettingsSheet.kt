@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.gsynk.app.R
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.IslandPosition
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.ChipBorder
import com.gsynk.app.ui.theme.GrapeGreenSoft
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary

@Composable
fun SettingsSheet(
    settings: AppSettings,
    versionName: String,
    overlayAllowed: Boolean,
    onDismiss: () -> Unit,
    onAutopilot: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onIsland: (Boolean) -> Unit,
    onIslandPosition: (Int) -> Unit,
    onSyncAfterBoot: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onRestoreAutoBrightness: () -> Unit,
    onPreviewIsland: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBg,
        scrimColor = Color(0xE60A0514),
        dragHandle = null
    ) {
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
                    Text("Настройки", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    Text("GSYNK $versionName", color = TextDim, style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardBgAlt)
                        .border(1.dp, ChipBorder, CircleShape)
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

            SwitchRow(
                icon = ImageVector.vectorResource(R.drawable.ic_bolt),
                title = "Автопилот",
                checked = settings.autopilot,
                accent = GrapeGreenSoft,
                onCheckedChange = onAutopilot
            )
            SwitchRow(
                icon = Icons.Filled.Refresh,
                title = "Уведомление при срабатывании",
                checked = settings.notifications,
                accent = GrapeVioletSoft,
                onCheckedChange = onNotifications
            )
            SwitchRow(
                icon = ImageVector.vectorResource(R.drawable.ic_shield),
                title = "Мини-айленд поверх приложений",
                checked = settings.islandEnabled,
                accent = GrapeVioletSoft,
                onCheckedChange = onIsland
            )
            SwitchRow(
                icon = Icons.Filled.Refresh,
                title = "Синхронизация после перезагрузки",
                checked = settings.syncAfterBoot,
                accent = GrapeVioletSoft,
                onCheckedChange = onSyncAfterBoot
            )

            if (settings.islandEnabled) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 33.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagChip(
                        text = "Сверху",
                        selected = settings.islandPosition == IslandPosition.TOP,
                        onClick = { onIslandPosition(IslandPosition.TOP) }
                    )
                    TagChip(
                        text = "По центру",
                        selected = settings.islandPosition == IslandPosition.CENTER,
                        onClick = { onIslandPosition(IslandPosition.CENTER) }
                    )
                    TagChip(
                        text = "Снизу",
                        selected = settings.islandPosition == IslandPosition.BOTTOM,
                        onClick = { onIslandPosition(IslandPosition.BOTTOM) }
                    )
                }
            }

            if (settings.islandEnabled && !overlayAllowed) {
                Spacer(Modifier.height(8.dp))
                SurfaceCard(corner = 18.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = GrapeVioletSoft,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Нужен доступ «Поверх других приложений»",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TagChip(text = "Дать", selected = true, onClick = onOpenOverlay)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Синхронизировать",
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Автояркость",
                    onClick = onRestoreAutoBrightness,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Показать айленд",
                    onClick = onPreviewIsland,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Автозапуск",
                    onClick = onOpenAutostart,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF120720),
                checkedTrackColor = accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color(0xFF2A1E40),
                uncheckedBorderColor = ChipBorder
            )
        )
    }
}
