package com.gsynk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.R
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.Eggs
import com.gsynk.app.core.Skin
import com.gsynk.app.core.Xmb
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Единое приложение «Настройки GREAL UI».
 *
 * Вместо прежних трёх плиток (эффекты, формы и дизайн в общих настройках)
 * всё оформление GSYNK собрано на одной живой 3D-сцене: пресеты, материал и
 * GPU-аврора, формы и активные углы, сферы-плитки, акцент и отклик.
 */
@Composable
fun GrealSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val skin by Skin.values.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profile = remember { DeviceProfile.detect(context) }
    var materialSwitches by remember { mutableIntStateOf(0) }

    GrapeScreen(
        title = "Настройки GREAL UI",
        subtitle = "GREAL XMB: категории, фокус, материал, волны и отклик · " + Skin.label(skin.level),
        onBack = onBack
    ) {
        GrealSettingsPreview(skin = skin, tileScale = settings.tileScale)

        Spacer(Modifier.height(14.dp))
        SectionTitle("Сцена: уровень 3D и готовые наборы")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(0, 1, 2).forEach { level ->
                val allowed = Skin.effectiveLevel(profile, level)
                SortChip(Skin.label(level), skin.level == level) {
                    Skin.save(context, skin.copy(level = allowed))
                }
            }
        }
        Text(
            text = if (profile.isFull) {
                "Телефон тянет полную GREAL-сцену: GPU-аврору, глубокие тени и сильные 3D-повороты."
            } else {
                "Телефон слабее: полный уровень ограничен, чтобы сохранить плавность."
            },
            color = TextDim,
            fontSize = Gioui.Type.caption
        )
        Spacer(Modifier.height(9.dp))
        Skin.PRESETS.forEach { (_, title, values) ->
            SurfaceCard(borderColor = if (skin == values) GrapeVioletSoft.copy(alpha = 0.55f) else CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GrealOrbFace(
                        modifier = Modifier.size(44.dp),
                        accent = UiTweaks.accentStart,
                        iconRes = R.drawable.ic_tune,
                        seed = title.hashCode(),
                        active = skin == values,
                        iconScale = 0.48f
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "материал " + values.prgOpacity + "% · свет " + values.prgGlow + "% · блики " + values.prgGlare +
                                "% · движение " + values.prgMotion + "% · формы " + values.card + "/" + values.toggle,
                            color = TextDim,
                            fontSize = Gioui.Type.caption
                        )
                    }
                    SortChip("взять", skin == values) {
                        Skin.save(context, Skin.effectiveValues(profile, values))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("Материал, свет и GPU-аврора")
        GrealValueGroup("Плотность материала", skin.prgOpacity, listOf(10, 20, 30, 38, 55, 75, 90), "%") { value ->
            Skin.save(context, skin.copy(prgOpacity = value))
            materialSwitches++
            if (materialSwitches >= 3) Eggs.unlock(context, "glass3")
        }
        GrealValueGroup("Свет по контуру", skin.prgGlow, listOf(0, 15, 35, 55, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(prgGlow = value))
        }
        GrealValueGroup("Реальные блики по краям", skin.prgGlare, listOf(0, 15, 35, 50, 65, 85, 100), "%") { value ->
            Skin.save(context, skin.copy(prgGlare = value))
        }
        GrealValueGroup("Движение света и авроры", skin.prgMotion, listOf(40, 60, 100, 140, 200), "%") { value ->
            Skin.save(context, skin.copy(prgMotion = value))
        }
        GrealValueGroup("Мягкость фона под панелями", skin.blur, listOf(0, 4, 8, 12, 18, 26), " dp") { value ->
            Skin.save(context, skin.copy(blur = value))
        }
        GrealValueGroup("Объёмные лучи сцены", skin.rays, listOf(0, 2, 4, 6, 8, 12), "") { value ->
            Skin.save(context, skin.copy(rays = value))
        }
        GrealValueGroup("Фокус сцены", skin.focus, listOf(0, 25, 50, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(focus = value))
        }
        GrealValueGroup("Скорость авроры", skin.speed, listOf(60, 100, 140, 200), "%") { value ->
            Skin.save(context, skin.copy(speed = value))
        }
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            SortChip("фактура материала", skin.grid) { Skin.save(context, skin.copy(grid = true)) }
            SortChip("гладкий материал", !skin.grid) { Skin.save(context, skin.copy(grid = false)) }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Формы, бесшовные края и активные углы")
        GrealFormsPreview(skin)
        GrealValueGroup("Карточки-функции", skin.tile, listOf(0, 3, 6, 9, 10), " dp") { value ->
            Skin.save(context, skin.copy(tile = value))
        }
        GrealValueGroup("Карточки", skin.card, listOf(2, 6, 9, 13, 14), " dp") { value ->
            Skin.save(context, skin.copy(card = value))
        }
        GrealValueGroup("Кнопки", skin.button, listOf(0, 4, 8, 11, 12), " dp") { value ->
            Skin.save(context, skin.copy(button = value))
        }
        GrealValueGroup("Переключатели", skin.toggle, listOf(2, 6, 10, 13, 14), " dp") { value ->
            Skin.save(context, skin.copy(toggle = value))
        }
        GrealValueGroup("Листы и панели", skin.sheet, listOf(4, 8, 12, 17, 18), " dp") { value ->
            Skin.save(context, skin.copy(sheet = value))
        }
        GrealValueGroup("Капсулы", skin.capsule, listOf(8, 14, 18, 21, 22), " dp") { value ->
            Skin.save(context, skin.copy(capsule = value))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Канон GREAL",
                onClick = { Skin.save(context, skin.copy(tile = 9, card = 13, sheet = 17, button = 11, toggle = 13, capsule = 21)) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Максимально мягко",
                onClick = {
                    Skin.save(context, skin.copy(tile = 10, card = 14, sheet = 18, button = 12, toggle = 14, capsule = 22))
                    Eggs.unlock(context, "keyboard")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Сферы-плитки, акцент и отклик")
        SurfaceCard(borderColor = CardBorder) {
            Text("Акцент GREAL", color = TextSecondary, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                UiTweaks.ACCENTS.forEachIndexed { index, (name, _) ->
                    SortChip(label = name, selected = settings.accent == index) { vm.setAccent(index) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Переливание авроры", color = TextSecondary, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                listOf("спокойно", "обычно", "быстро").forEachIndexed { index, name ->
                    SortChip(label = name, selected = settings.backgroundSpeed == index) { vm.setBackgroundSpeed(index) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Размер сфер-плиток", color = TextSecondary, fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            ChipFlow {
                listOf("мелкие", "обычные", "крупные").forEachIndexed { index, name ->
                    SortChip(label = name, selected = settings.tileScale == index + 1) { vm.setTileScale(index + 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Долгое нажатие на сферу закрепляет её в «моё». Описание функции живёт в шапке открытого окна.",
                color = TextDim,
                fontSize = Gioui.Type.caption
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingRow(
            title = "Живой статус сфер",
            subtitle = "светящаяся точка активности на сфере-плитке",
            checked = settings.tileCounters
        ) { vm.setTileCounters(it) }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Отклик вибрацией",
            subtitle = "касание сферы подтверждается короткой вибрацией",
            checked = settings.haptics
        ) { vm.setHaptics(it) }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Щелчок при нажатии",
            subtitle = "короткий тон вместо тишины",
            checked = settings.clickSound
        ) { vm.setClickSound(it) }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Плотные панели",
            subtitle = "меньше воздуха между карточками",
            checked = settings.compactList
        ) { vm.setCompactList(it) }

        Spacer(Modifier.height(16.dp))
        SectionTitle("XMB: категории, фокус и волна")
        Text(
            text = Xmb.VERSION,
            color = TextSecondary,
            fontSize = Gioui.Type.caption,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), UiTweaks.accentStart.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.24f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
        ) {
            GrealTexture(Modifier.matchParentSize(), accent = UiTweaks.accentStart, seed = 5810, density = 0.28f)
            XmbWaveField(Modifier.matchParentSize(), accent = UiTweaks.accentStart)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("связь", "система", "забава").forEachIndexed { index, label ->
                    val focused = index == 1
                    Column(
                        Modifier
                            .width(86.dp)
                            .graphicsLayer {
                                val scale = Xmb.categoryScale(index - 1)
                                scaleX = scale
                                scaleY = scale
                                alpha = Xmb.categoryAlpha(index - 1)
                                cameraDistance = 8f * density
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            if (focused) {
                                GrealInfiniteOrbit(
                                    Modifier.matchParentSize(),
                                    accent = UiTweaks.accentStart,
                                    seed = 5810 + index,
                                    active = true
                                )
                            }
                            GrealOrbFace(
                                modifier = Modifier.size(if (focused) 42.dp else 36.dp),
                                accent = UiTweaks.accentStart,
                                iconRes = R.drawable.ic_tune,
                                seed = 5810 + index,
                                active = focused,
                                iconScale = 0.48f
                            )
                        }
                        Text(label, color = if (focused) TextPrimary else TextDim, fontSize = 9.sp)
                    }
                }
            }
            GrealActiveCorners(Modifier.matchParentSize(), accent = UiTweaks.accentStart, strength = 0.24f)
        }
        Spacer(Modifier.height(9.dp))
        GrealValueGroup("Скорость волны XMB", skin.speed, listOf(60, 100, 140, 200), "%") { value ->
            Skin.save(context, skin.copy(speed = value))
        }
        GrealValueGroup("Глубина фокуса XMB", skin.focus, listOf(0, 25, 50, 75, 100), "%") { value ->
            Skin.save(context, skin.copy(focus = value))
        }
        Text(
            "В «Пуске» категории листаются влево/вправо, функции — вверх/вниз, а GREAL отвечает за материал, свет и объём.",
            color = TextDim,
            fontSize = Gioui.Type.caption,
            lineHeight = 13.sp
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Бесконечные плитки и антистресс")
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    GrealInfiniteOrbit(
                        modifier = Modifier.matchParentSize(),
                        accent = UiTweaks.accentStart,
                        seed = 5720,
                        active = true
                    )
                    GrealOrbFace(
                        modifier = Modifier.size(44.dp),
                        accent = UiTweaks.accentStart,
                        iconRes = R.drawable.ic_tune,
                        seed = 5720,
                        active = true,
                        iconScale = 0.48f
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Антистресс GREAL", color = TextPrimary, fontSize = Gioui.Type.body, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Отдельное приложение, куда заходят просто покрутить бесконечные 3D-плитки, полопать сферы или оставить световую рябь.",
                        color = TextDim,
                        fontSize = Gioui.Type.caption,
                        lineHeight = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            SecondaryButton(
                text = "Открыть и покрутить",
                onClick = { Router.page("antistress") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            text = "Вернуть сцену «GREAL XMB»",
            onClick = {
                val defaults = Skin.PRESETS.firstOrNull { it.first == "gioui" }?.third ?: Skin.Values()
                Skin.save(context, Skin.effectiveValues(profile, defaults))
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GrealSettingsPreview(skin: Skin.Values, tileScale: Int) {
    val phase = rememberPrgPhase(5710)
    val wave = sin(phase * 2f * PI.toFloat())
    val accent = UiTweaks.accentStart
    val orbSize = when (tileScale.coerceIn(1, 3)) {
        1 -> 62.dp
        3 -> 82.dp
        else -> 72.dp
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(258.dp)
            .clip(Gioui.Shapes.card)
            .border(1.dp, CardBorder, Gioui.Shapes.card)
    ) {
        GrealAuroraField(Modifier.fillMaxSize(), speedScale = skin.speed / 100f)
        GrealDepthBackdrop(Modifier.fillMaxSize(), scrollPixels = phase * 900f)

        // Дальняя и ближняя стеклянные плиты: сцена читается объёмно без линий и кассет.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = (-34).dp, y = 18.dp)
                .size(width = 230.dp, height = 132.dp)
                .graphicsLayer {
                    rotationX = -16f + wave * 4f
                    rotationY = 18f + cos(phase * 2f * PI.toFloat()) * 5f
                    rotationZ = -4f
                    cameraDistance = 9f * density
                    shadowElevation = 24f
                    ambientShadowColor = accent.copy(alpha = 0.32f)
                    spotShadowColor = accent.copy(alpha = 0.40f)
                }
                .clip(RoundedCornerShape(skin.sheet.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.14f), accent.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.22f))
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = 30.dp, y = (-8).dp)
                .size(width = 252.dp, height = 150.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.72f)
                    rotationX = -11f - wave * 3.5f
                    rotationY = -13f + wave * 5f
                    rotationZ = 2.5f
                    cameraDistance = 8.5f * density
                    shadowElevation = 34f
                    ambientShadowColor = accent.copy(alpha = 0.36f)
                    spotShadowColor = Color(0xFF22D3EE).copy(alpha = 0.36f)
                }
                .clip(RoundedCornerShape(skin.card.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.17f), accent.copy(alpha = 0.30f), Color(0xFF03110E).copy(alpha = 0.28f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(skin.card.dp))
        ) {
            GrealTexture(Modifier.matchParentSize(), accent = accent, seed = 5710, density = skin.prgOpacity / 100f)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text("GREAL UI живьём", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "материал " + skin.prgOpacity + "% · свет " + skin.prgGlow + "% · блики " + skin.prgGlare +
                        "% · 3D " + Skin.label(skin.level),
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = Gioui.Type.caption
                )
            }
            GrealActiveCorners(Modifier.matchParentSize(), accent = accent, strength = 0.48f)
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GrealOrbFace(
                modifier = Modifier
                    .size(orbSize)
                    .graphicsLayer {
                        rotationX = wave * 9f
                        rotationY = cos(phase * 2f * PI.toFloat()) * 12f
                        cameraDistance = 8f * density
                        shadowElevation = 22f
                    },
                accent = accent,
                iconRes = R.drawable.ic_tune,
                seed = 5711,
                active = true,
                iconScale = 0.50f
            )
            Text("сфера", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        GrealOrbFace(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-18).dp, y = (-18).dp)
                .size(54.dp)
                .graphicsLayer {
                    rotationX = -wave * 8f
                    rotationY = -cos(phase * 2f * PI.toFloat()) * 10f
                    cameraDistance = 8f * density
                    shadowElevation = 18f
                },
            accent = Color(0xFF22D3EE),
            iconRes = R.drawable.ic_brightness,
            seed = 5712,
            active = false,
            iconScale = 0.48f
        )
    }
}

@Composable
private fun GrealFormsPreview(skin: Skin.Values) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .graphicsLayer {
                rotationX = -7f
                rotationY = 5f
                cameraDistance = 10f * density
                shadowElevation = 20f
            }
            .clip(Gioui.Shapes.card)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Gioui.Palette.Purple.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.20f))
                )
            )
            .border(1.dp, CardBorder, Gioui.Shapes.card),
        contentAlignment = Alignment.Center
    ) {
        GrealTexture(Modifier.matchParentSize(), accent = Gioui.Palette.Purple, seed = 5713, density = 0.32f)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GrealFormSample("плитка", skin.tile.dp, Gioui.Palette.Purple, Modifier.size(58.dp))
            GrealFormSample("карта", skin.card.dp, Gioui.Palette.Teal, Modifier.size(58.dp))
            GrealFormSample("кнопка", skin.button.dp, Gioui.Palette.Amber, Modifier.size(width = 62.dp, height = 40.dp))
            GrealFormSample("капсула", skin.capsule.dp, Gioui.Palette.Cobalt, Modifier.size(width = 62.dp, height = 40.dp))
        }
        GrealActiveCorners(Modifier.matchParentSize(), accent = Gioui.Palette.Purple, strength = 0.28f)
    }
    Spacer(Modifier.height(7.dp))
    Text(
        "Формы применяются ко всему GSYNK сразу: панелям, карточкам, кнопкам, листам и активным углам.",
        color = TextDim,
        fontSize = Gioui.Type.caption,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GrealFormSample(label: String, radius: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .graphicsLayer {
                    rotationX = -12f
                    rotationY = 10f
                    cameraDistance = 9f * density
                    shadowElevation = 16f
                    ambientShadowColor = color.copy(alpha = 0.34f)
                    spotShadowColor = color.copy(alpha = 0.42f)
                }
                .clip(RoundedCornerShape(radius))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.34f), color.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.22f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(radius))
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun GrealValueGroup(
    title: String,
    current: Int,
    values: List<Int>,
    suffix: String,
    onPick: (Int) -> Unit
) {
    Spacer(Modifier.height(11.dp))
    SectionTitle(title + " — " + current + suffix)
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        values.forEach { value ->
            SortChip(value.toString() + suffix, current == value) { onPick(value) }
        }
    }
}
