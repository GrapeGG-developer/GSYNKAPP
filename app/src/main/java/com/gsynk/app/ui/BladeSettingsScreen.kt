package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.R
import com.gsynk.app.core.Blade
import com.gsynk.app.core.Skin

/**
 * Единое приложение «Настройки Blade UI».
 *
 * Вместо прежних GREAL/XMB-настроек здесь собраны лезвия, рамка фокуса,
 * световой пробег и материал панелей в стиле Xbox 360 Blade Dashboard.
 */
@Composable
fun BladeSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val skin by Skin.values.collectAsStateWithLifecycle()

    GrapeScreen(
        title = "Настройки Blade UI",
        subtitle = Blade.VERSION,
        onBack = onBack
    ) {
        BladeSettingsPreview()
        Spacer(Modifier.height(12.dp))
        SectionTitle("Лезвия и рамка фокуса")
        SurfaceCard {
            Text(
                "Классический Blade Dashboard: горизонтальные вкладки-лезвия, вертикальный список функций, зелёно-чёрный фон и лаймовая рамка выбранного элемента.",
                color = BladePalette.White,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Текущая система: " + Blade.VERSION,
                color = BladePalette.Lime,
                fontSize = Gioui.Type.caption,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Свет и материал Blade")
        BladeValueGroup("Скорость пробега", skin.speed, listOf(60, 100, 140, 200), "%") { value ->
            Skin.save(context, skin.copy(speed = value))
        }
        BladeValueGroup("Яркость рамки", skin.prgGlow, listOf(0, 15, 35, 55, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(prgGlow = value))
        }
        BladeValueGroup("Плотность лезвий", skin.prgOpacity, listOf(10, 20, 30, 38, 55, 75, 90), "%") { value ->
            Skin.save(context, skin.copy(prgOpacity = value))
        }
        BladeValueGroup("Глубина наклона", skin.focus, listOf(0, 25, 50, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(focus = value))
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Управление")
        SurfaceCard {
            Text(
                "Категории листаются влево/вправо, пункты — вверх/вниз. Есть инерция, пружинное доведение и крупные кнопки ←/→/↑/↓/OK.",
                color = BladePalette.DimWhite,
                fontSize = Gioui.Type.caption,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                listOf("инерция", "плавно", "Blade", "без поиска").forEach { label ->
                    SortChip(label, selected = label == "Blade") { }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Вернуть канон Blade",
            onClick = {
                Skin.save(
                    context,
                    skin.copy(speed = 100, prgGlow = 55, prgOpacity = 38, focus = 50)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BladeSettingsPreview() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF061A0E), BladePalette.Deep, BladePalette.Ink)
                )
            )
            .border(1.dp, BladePalette.Lime.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
    ) {
        BladeBackground(Modifier.matchParentSize())
        BladeFocusFrame(Modifier.matchParentSize(), strength = 0.34f)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(R.drawable.ic_devices, R.drawable.ic_tune, R.drawable.ic_bolt, R.drawable.ic_volume).forEachIndexed { index, icon ->
                val active = index == 1
                BladeSurface(
                    modifier = Modifier.size(width = 64.dp, height = 88.dp),
                    shape = RoundedCornerShape(9.dp),
                    focused = active
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BladeIcon(iconRes = icon, modifier = Modifier.size(34.dp), active = active)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            when (index) {
                                0 -> "связь"
                                1 -> "система"
                                2 -> "нагрузка"
                                else -> "забава"
                            },
                            color = if (active) BladePalette.White else BladePalette.DimWhite,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BladeValueGroup(
    title: String,
    current: Int,
    values: List<Int>,
    suffix: String,
    onPick: (Int) -> Unit
) {
    Spacer(Modifier.height(9.dp))
    SectionTitle(title + " — " + current + suffix)
    Spacer(Modifier.height(5.dp))
    ChipFlow {
        values.forEach { value ->
            SortChip(value.toString() + suffix, current == value) { onPick(value) }
        }
    }
}
