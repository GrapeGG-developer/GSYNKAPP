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
import com.gsynk.app.core.Script
import com.gsynk.app.core.Skin

/**
 * Единое приложение «Настройки Script UI».
 *
 * Постоянная система: палитра, 2D-градиенты, изометричные скругления,
 * плотность строк и скорость фона. Никакого стекла и 3D.
 */
@Composable
fun ScriptSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val skin by Skin.values.collectAsStateWithLifecycle()

    GrapeScreen(
        title = "Настройки Script UI",
        subtitle = Script.VERSION,
        onBack = onBack
    ) {
        ScriptPalettePreview()
        Spacer(Modifier.height(8.dp))
        ScriptSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = ScriptShapes.isoChip,
            focused = true
        ) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(
                    "изометричное скругление · активная строка",
                    color = ScriptPalette.White,
                    fontSize = Gioui.Type.caption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Постоянная система")
        SurfaceCard {
            Text(
                "GSYNK Script UI: нумерованные строки, главы-категории, активная строка и неизменный каркас Monolith. Интерфейс плоский: 2D-градиенты, чёткая палитра, асимметричные скругления.",
                color = ScriptPalette.White,
                fontSize = Gioui.Type.body,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Система: " + Script.VERSION,
                color = ScriptPalette.Accent2,
                fontSize = Gioui.Type.caption,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionTitle("Палитра и градиенты")
        ScriptValueGroup("Плотность градиента", skin.prgOpacity, listOf(10, 20, 30, 38, 55, 75, 90), "%") { value ->
            Skin.save(context, skin.copy(prgOpacity = value))
        }
        ScriptValueGroup("Яркость акцента", skin.prgGlow, listOf(0, 15, 35, 55, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(prgGlow = value))
        }
        ScriptValueGroup("Скорость фона", skin.speed, listOf(60, 100, 140, 200), "%") { value ->
            Skin.save(context, skin.copy(speed = value))
        }
        ScriptValueGroup("Контраст фокуса", skin.focus, listOf(0, 25, 50, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(focus = value))
        }
        ScriptValueGroup("Изометричное скругление", skin.card, listOf(4, 8, 13, 18, 22), " dp") { value ->
            Skin.save(context, skin.copy(card = value))
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Навигация")
        SurfaceCard {
            Text(
                "←/→ меняют главу, ↑/↓ двигают фокус по строкам, OK открывает активную строку, ★ закрепляет её. Структура экрана не меняется: состояние, глава, активная строка, строки, действия.",
                color = ScriptPalette.Muted,
                fontSize = Gioui.Type.caption,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                listOf("script", "monolith", "2d", "без стекла", "без поиска").forEach { label ->
                    SortChip(label, selected = label == "script") { }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Вернуть канон Script",
            onClick = {
                Skin.save(
                    context,
                    skin.copy(prgOpacity = 38, prgGlow = 55, speed = 100, focus = 50, card = 13)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ScriptPalettePreview() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(ScriptShapes.isoPanel)
            .background(
                Brush.linearGradient(
                    listOf(ScriptPalette.Background, ScriptPalette.Surface, ScriptPalette.SurfaceAlt)
                )
            )
            .border(1.dp, ScriptPalette.Accent, ScriptShapes.isoPanel)
    ) {
        ScriptBackground(Modifier.matchParentSize())
        ScriptTexture(Modifier.matchParentSize(), accent = ScriptPalette.Accent2, seed = 601, density = 0.34f)
        ScriptFocusBar(Modifier.matchParentSize(), strength = 0.24f)
        ScriptCorners(Modifier.matchParentSize(), accent = ScriptPalette.Accent3, strength = 0.30f)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "INK" to ScriptPalette.Background,
                "SURF" to ScriptPalette.SurfaceAlt,
                "ACC" to ScriptPalette.Accent,
                "CYAN" to ScriptPalette.Accent2,
                "AMBR" to ScriptPalette.Accent3
            ).forEach { (label, color) ->
                Box(
                    Modifier
                        .size(width = 58.dp, height = 92.dp)
                        .clip(ScriptShapes.isoCard)
                        .background(Brush.verticalGradient(listOf(color, ScriptPalette.Surface)))
                        .border(1.dp, ScriptPalette.Border, ScriptShapes.isoCard),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = ScriptPalette.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 8.dp)
        ) {
            ScriptIcon(iconRes = R.drawable.ic_tune, modifier = Modifier.size(28.dp), active = true)
        }
        ScriptEdgeMark(Modifier.matchParentSize(), accent = ScriptPalette.Accent2, seed = 602)
    }
}

@Composable
private fun ScriptValueGroup(
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
