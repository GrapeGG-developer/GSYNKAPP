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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.Eggs
import com.gsynk.app.core.Everyday
import com.gsynk.app.core.Haptics
import com.gsynk.app.core.IslandOverlay
import com.gsynk.app.core.Roulette
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.theme.CardBgAlt
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import java.time.LocalDate

/**
 * Страницы GIOUI.
 *
 * Здесь живёт всё, что появилось в четвёртой версии интерфейса: живой остров 2.0,
 * ползунки эффектов и скруглений, «каждый день» с новым содержимым, пасхалки и
 * рулетка случайных функций. Каждая страница — один уровень, без вложенных меню.
 */

/** Остров 2.0: посадка под камеру, живые виджеты, стекло. */
@Composable
fun Island2Screen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skin by Skin.values.collectAsStateWithLifecycle()
    val profile = remember { DeviceProfile.detect(context) }
    val cutout = remember(context) { IslandOverlay.cutoutHeight(context) }

    GrapeScreen(title = "Остров 2.0", subtitle = "вплотную под камерой", onBack = onBack) {
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.45f)) {
            Text("Где садится капсула", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (cutout > 0) {
                    "Камера занимает " + cutout + " px сверху — остров садится ровно под ней, " +
                        "без зазора в сто точек, как было раньше."
                } else {
                    "Выреза под камеру нет: остров садится под строкой состояния."
                },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(10.dp))
            InfoRow("Позиция", com.gsynk.app.core.IslandPosition.label(settings.islandPosition))
            InfoRow("Сдвиг", settings.islandOffsetY.toString() + " точек")
            InfoRow("Скругление", skin.island.toString() + " dp — самое большое в приложении")
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Что показывать")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            com.gsynk.app.core.IslandWidget.ALL.forEach { widget ->
                SortChip(
                    label = com.gsynk.app.core.IslandWidget.label(widget),
                    selected = widget in settings.islandWidgets
                ) { vm.toggleIslandWidget(widget, widget !in settings.islandWidgets) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Посадка")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            SortChip("Вплотную к камере", settings.islandHugCamera) { vm.setIslandHugCamera(true) }
            SortChip("Ниже камеры", !settings.islandHugCamera) { vm.setIslandHugCamera(false) }
        }
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            SortChip("Выше на 2", false) { vm.setIslandOffset((settings.islandOffsetY - 2).coerceIn(-60, 120)) }
            SortChip("Ниже на 2", false) { vm.setIslandOffset((settings.islandOffsetY + 2).coerceIn(-60, 120)) }
            SortChip("Сброс", false) { vm.setIslandOffset(0) }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Скругление острова")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(16, 20, 26, 32, 40).forEach { value ->
                SortChip(value.toString() + " dp", skin.island == value) {
                    Skin.save(context, skin.copy(island = value))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Постоянный остров")
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = if (settings.islandPersistent) "Выключить" else "Включить",
                onClick = { vm.setIslandPersistent(!settings.islandPersistent) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Образец",
                onClick = { vm.fullIslandPreview() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text("Живое стекло", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Капсула не стоит на месте: два оттенка перетекают друг в друга " +
                    "в реальном времени, а не по картинке. На слабом телефоне это " +
                    "выключается вместе с уровнем эффектов.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Эффекты: размытие, стекло, фокус, лучи, сетка — всё живьём. */
@Composable
fun EffectsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val skin by Skin.values.collectAsStateWithLifecycle()
    var glassSwitches by remember { mutableIntStateOf(0) }
    val profile = remember { DeviceProfile.detect(context) }

    GrapeScreen(title = "Эффекты", subtitle = Skin.label(skin.level), onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(Gioui.Shapes.card)
        ) {
            GrainField(
                modifier = Modifier.fillMaxSize(),
                accent = UiTweaks.accentStart,
                speed = skin.speed / 100f
            )
            if (skin.rays > 0) {
                LightRays(modifier = Modifier.fillMaxSize(), rays = skin.rays)
            }
            GlassPanel(modifier = Modifier.fillMaxSize(), kind = glassKind(skin.glass)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("живьём", color = Color.White, fontSize = Gioui.Type.pageSubtitle)
                    Text(
                        text = "стекло: " + Skin.glassTitle(skin.glass) + ", размытие " + skin.blur + " dp, " +
                            "лучей " + skin.rays + ", фон " + skin.speed + "%",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Уровень")
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
            text = if (profile.isFull) "Телефон тянет полный набор." else
                "Телефон слабее: полный набор ограничен, чтобы не терять плавность.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )

        Spacer(Modifier.height(12.dp))
        SectionTitle("Стекло")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            Skin.GLASSES.forEach { (id, title) ->
                SortChip(title, skin.glass == id) {
                    Skin.save(context, skin.copy(glass = id))
                    glassSwitches++
                    // Пасхалка «три стекла»: переключил три раза подряд — открылась.
                    if (glassSwitches >= 3) Eggs.unlock(context, "glass3")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Размытие фона — " + skin.blur + " dp")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(0, 4, 8, 12, 18, 26).forEach { value ->
                SortChip(value.toString(), skin.blur == value) {
                    Skin.save(context, skin.copy(blur = value))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Лучи и трассировка тени — " + skin.rays)
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(0, 2, 4, 6, 8, 12).forEach { value ->
                SortChip(value.toString(), skin.rays == value) {
                    Skin.save(context, skin.copy(rays = value))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Фокус — " + skin.focus + "%")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(0, 25, 50, 75, 100).forEach { value ->
                SortChip(value.toString(), skin.focus == value) {
                    Skin.save(context, skin.copy(focus = value))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Сетка GIOUI и скорость фона")
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            SortChip("сетка 20 px", skin.grid) { Skin.save(context, skin.copy(grid = true)) }
            SortChip("без сетки", !skin.grid) { Skin.save(context, skin.copy(grid = false)) }
        }
        Spacer(Modifier.height(7.dp))
        ChipFlow {
            listOf(60, 100, 140, 200).forEach { value ->
                SortChip(value.toString() + "%", skin.speed == value) {
                    Skin.save(context, skin.copy(speed = value))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("Готовые наборы")
        Spacer(Modifier.height(7.dp))
        Skin.PRESETS.forEach { (_, title, values) ->
            SurfaceCard(borderColor = if (values.glass == skin.glass) GrapeVioletSoft.copy(alpha = 0.5f) else CardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                        Text(
                            text = "стекло " + Skin.glassTitle(values.glass) + " · размытие " + values.blur +
                                " · скругления " + values.card + "/" + values.toggle,
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                    SortChip("взять", skin == values) {
                        Skin.save(context, Skin.effectiveValues(profile, values))
                        }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Скругления GIOUI: единая система, настраивается вокруг канона. */
@Composable
fun RadiusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val skin by Skin.values.collectAsStateWithLifecycle()

    GrapeScreen(title = "Скругления", subtitle = "Единая система GIOUI", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(Gioui.Shapes.card)
                .background(CardBgAlt)
                .border(1.dp, CardBorder, Gioui.Shapes.card),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(skin.tile.dp))
                        .background(Gioui.Palette.Purple)
                )
                Box(
                    Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(Gioui.Shapes.card)
                        .background(Gioui.Palette.Teal)
                )
                Box(
                    Modifier
                        .width(64.dp)
                        .height(40.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(skin.toggle.dp))
                        .background(Gioui.Palette.Amber)
                )
                Box(
                    Modifier
                        .width(64.dp)
                        .height(40.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(skin.island.dp))
                        .background(Gioui.Palette.Cobalt)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Слева направо: карточка-функция, карточка, переключатель, остров. В GIOUI это одна " +
                "система: канон — карточки 24 dp, контролы 16 dp, листы 28 dp, остров — капсула.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )

        Spacer(Modifier.height(14.dp))
        radiusGroup("Карточки-функции", skin.tile, listOf(16, 20, 24, 28)) { value ->
            Skin.save(context, skin.copy(tile = value))
        }
        radiusGroup("Карточки", skin.card, listOf(16, 20, 24, 28)) { value ->
            Skin.save(context, skin.copy(card = value))
        }
        radiusGroup("Кнопки", skin.button, listOf(12, 14, 16, 20)) { value ->
            Skin.save(context, skin.copy(button = value))
        }
        radiusGroup("Переключатели", skin.toggle, listOf(12, 14, 16, 20)) { value ->
            Skin.save(context, skin.copy(toggle = value))
        }
        radiusGroup("Листы и панели", skin.sheet, listOf(20, 24, 28, 32)) { value ->
            Skin.save(context, skin.copy(sheet = value))
        }
        radiusGroup("Остров (капсула)", skin.island, listOf(24, 28, 32, 40)) { value ->
            Skin.save(context, skin.copy(island = value))
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Канон GIOUI",
                onClick = { Skin.save(context, skin.copy(tile = 24, card = 24, sheet = 28, button = 16, toggle = 16, island = 32)) },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Всё скруглённое",
                onClick = {
                    Skin.save(context, skin.copy(tile = 30, card = 30, sheet = 34, button = 20, toggle = 20))
                    // Пасхалка «клавиатура из плиток»: всё скруглено до предела.
                    Eggs.unlock(context, "keyboard")
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun radiusGroup(title: String, current: Int, values: List<Int>, onPick: (Int) -> Unit) {
    Spacer(Modifier.height(10.dp))
    SectionTitle(title + " — " + current + " dp")
    Spacer(Modifier.height(6.dp))
    ChipFlow {
        values.forEach { value ->
            SortChip(value.toString(), current == value) { onPick(value) }
        }
    }
}

/** «Каждый день»: набор дня, серия, подарок. */
@Composable
fun EverydayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val day = remember { Everyday.forDate(LocalDate.now()) }
    var streak by remember { mutableIntStateOf(0) }
    var opened by remember { mutableIntStateOf(0) }
    var taken by remember { mutableStateOf(false) }
    var eggFound by remember { mutableStateOf<String?>(null) }

    // Пасхалка «переворот»: экраном вниз, пока открыт «Каждый день».
    val faceDown = remember { com.gsynk.app.core.EverydayFaceDown.watch(context) { flipped ->
        if (flipped) eggFound = "Пасхалка: " + (Eggs.byId("flip")?.title ?: "переворот")
    } }
    DisposableEffect(Unit) { onDispose { faceDown() } }

    LaunchedEffect(Unit) {
        streak = Everyday.markVisit(context)
        opened = Everyday.openedDays(context)
        taken = Everyday.giftTaken(context)
        AppLog.event("gsui", "Ежедневный набор открыт: день " + day.epochDay + ", серия " + streak)
    }

    GrapeScreen(title = "Каждый день", subtitle = "серия: " + streak + " дн.", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(Gioui.Shapes.card)
                .background(Gioui.Palette.tiles[day.tone % Gioui.Palette.tiles.size])
        ) {
            GlassPanel(modifier = Modifier.fillMaxSize(), kind = Glass.Kind.FROST) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = day.title,
                        color = Color.White,
                        fontSize = Gioui.Type.pageTitle
                    )
                    Column {
                        Text(
                            text = "число дня: " + day.number + " · " + day.word,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                        Text(
                            text = "открыто дней: " + opened + " · " + Everyday.rewardFor(streak),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Факт о железе")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(day.fact, color = TextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Задание дня")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.4f)) {
            Text(day.task, color = TextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Выполнишь — отметь галочкой: счётчик заданий растёт, а серия не рвётся.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            SortChip("сделано", false) {
                Haptics.tick(context, 24L)
                val fresh = Eggs.unlock(context, "daily30")
                AppLog.event("gsui", "Задание дня отмечено выполненным")
                eggFound = if (fresh) "Открыта пасхалка: " + (Eggs.byId("daily30")?.title ?: "") else "Отмечено"
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Подарок")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = if (taken) CardBorder else GrapeVioletSoft.copy(alpha = 0.45f)) {
            Text(
                text = if (taken) "Подарок на сегодня забран" else Everyday.rewardFor(streak),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (taken) "Забрать завтра" else "Забрать",
                onClick = {
                    if (!taken) {
                        Everyday.takeGift(context)
                        taken = true
                        Haptics.tick(context, 40L)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Прикол дня")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = CardBorder) {
            Text(day.joke, color = TextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Случайная пасхалка под рукой")
        Spacer(Modifier.height(6.dp))
        SurfaceCard(borderColor = CardBorder) {
            val total = Eggs.ALL.size
            val foundNow = Eggs.found(context).size
            Text("Найдено " + foundNow + " из " + total, color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Открывай их сама: секретных входов больше, чем кажется. " +
                    "Один из них — семь касаний значка на главной.",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Все пасхалки",
                onClick = { Router.page("easter") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (eggFound != null) {
            Spacer(Modifier.height(8.dp))
            Text(eggFound!!, color = GrapeGreen, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Пасхалки: сетка секретов и подсказки. */
@Composable
fun EasterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val found = remember(reload) { Eggs.found(context) }

    GrapeScreen(
        title = "Пасхалки",
        subtitle = found.size.toString() + " из " + Eggs.ALL.size,
        onBack = onBack
    ) {
        TileRow {
            SortChip("Проверить прогресс", false) { reload++; Haptics.tick(context, 20L) }
            SortChip("Сбросить", false) { Eggs.reset(context); message = "Пасхалки сброшены" }
        }
        Spacer(Modifier.height(10.dp))
        GridOfSecrets(
            items = Eggs.ALL.map { egg ->
                Triple(
                    egg.title,
                    if (egg.id in found) egg.reward else egg.hint,
                    egg.id in found
                )
            }
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = GrapeGreen, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
        }
        // Все секреты найдены: набор кожи, которого нет в обычном списке эффектов.
        if (found.size >= Eggs.ALL.size - 1) {
            Spacer(Modifier.height(12.dp))
            Eggs.unlock(context, "all")
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.5f)) {
                Text("Секретный набор кожи", color = GrapeGreen, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "«Виноградовая ночь»: аква-стекло, размытие 18, лучи 9 и почти капсульные " +
                        "скругления у всего. Такого набора нет в списке эффектов — он только за пасхалки.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Применить секретный набор",
                    onClick = {
                        val profile = DeviceProfile.detect(context)
                        Skin.save(context, Skin.effectiveValues(profile, Eggs.secretSkin()))
                        Haptics.tick(context, 40L)
                        message = "Секретный набор кожи применён"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Подсказки, которые уже открыты")
        Spacer(Modifier.height(6.dp))
        Eggs.ALL.filter { it.id in found }.forEach { egg ->
            SurfaceCard(borderColor = GrapeGreen.copy(alpha = 0.35f)) {
                Text(egg.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
                Text(egg.secret, color = TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
                Text("награда: " + egg.reward, color = GrapeGreen, style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (found.isEmpty()) {
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = "Пока ничего не найдено. Начни со значка винограда на главной: " +
                        "семь касаний подряд откроют первую пасхалку.",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GridOfSecrets(items: List<Triple<String, String, Boolean>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Gioui.gutter)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gioui.gutter)) {
                row.forEach { (title, hint, opened) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(Gioui.Shapes.tile)
                            .background(
                                if (opened) Gioui.Palette.Emerald
                                else Gioui.Palette.Gray.copy(alpha = 0.55f)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.12f), Gioui.Shapes.tile)
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = if (opened) "✓" else "?",
                                color = Color.White,
                                fontSize = Gioui.Type.pageSubtitle
                            )
                            Column {
                                Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal))
                                Text(
                                    hint,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Рулетка: случайная функция из всего приложения. */
@Composable
fun SurpriseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { DeviceProfile.detect(context) }
    var roll by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Catalog.Entry?>(null) }
    var opened by remember { mutableIntStateOf(0) }

    val daily = remember { Everyday.forDate(LocalDate.now()) }

    GrapeScreen(title = "Рулетка", subtitle = "случайная функция приложения", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(Gioui.Shapes.card)
        ) {
            GrainField(Modifier.fillMaxSize(), accent = Gioui.Palette.Magenta, second = Gioui.Palette.Amber, speed = 1.4f)
            GlassPanel(modifier = Modifier.fillMaxSize(), kind = Glass.Kind.AQUA) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = picked?.title ?: "крути",
                        color = Color.White,
                        fontSize = Gioui.Type.pageTitle
                    )
                    Column {
                        Text(
                            text = picked?.subtitle ?: "нажми — выпадет что-нибудь из приложения",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                        Text(
                            text = "открыто раз: " + opened + " · " + daily.word,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Крутить",
            onClick = {
                roll++
                val candidates = Catalog.ALL.filter { !it.fresh || profile.isFull }
                val rare = Catalog.ALL.filter { it.fresh }
                val route = Roulette.pick(
                    candidates = candidates.map { it.route },
                    rare = rare.map { it.route },
                    roll = roll + daily.number
                )
                picked = Catalog.ALL.firstOrNull { it.route == route }
                opened++
                if (opened >= 5) Eggs.unlock(context, "roulette")
                Haptics.tick(context, 30L)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(9.dp))
        SecondaryButton(
            text = "Открыть",
            onClick = {
                picked?.let { Catalog.open(context, it) }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        SectionTitle("Что уже выпадало")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Рулетка не повторяет предыдущую функцию и знает про железо: на слабом телефоне " +
                "тяжёлые вещи не выпадают.",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal).copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
        )
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Экраны GIOUI наружу: роутер отдаёт сюда маршруты `page:…`.
 * Рядом — список идентификаторов: по нему тесты проверяют, что каждая страница
 * действительно разбирается в `when` ниже.
 */

val GiouiPageIds: List<String> = listOf(
    "island", "effects", "radius", "everyday", "easter", "surprise", "ai", "bench",
    "echo", "eyes", "pulse",
    // GIOUI 5.0: шифраторы — один экран, разные стартовые пресеты
    "ciphers", "cipherlock", "ciphergrape", "cipherauto"
)

@Composable
fun GiouiPage(id: String, vm: MainViewModel, onBack: () -> Unit): Boolean {
    when (id) {
        "island" -> { Island2Screen(vm, onBack); return true }
        "effects" -> { EffectsScreen(onBack); return true }
        "radius" -> { RadiusScreen(onBack); return true }
        "everyday" -> { EverydayScreen(onBack); return true }
        "easter" -> { EasterScreen(onBack); return true }
        "surprise" -> { SurpriseScreen(onBack); return true }
        "ai" -> { AiScreen(onBack); return true }
        "bench" -> { BenchScreen(vm, onBack); return true }
        "echo" -> { EchoRulerScreen(onBack); return true }
        "eyes" -> { EyeScreen(onBack); return true }
        "pulse" -> { PulseScreen(onBack); return true }
        "ciphers" -> { CipherScreen(onBack); return true }
        "cipherlock" -> { CipherScreen(onBack, initialCipher = "lock"); return true }
        "ciphergrape" -> { CipherScreen(onBack, initialCipher = "fruit"); return true }
        "cipherauto" -> { CipherScreen(onBack, initialDecrypt = true); return true }
    }
    return false
}

/** Ежедневная проверка достижений: вызывается при старте приложения. */
fun checkDailyAchievements(context: android.content.Context) {
    val streak = Everyday.markVisit(context)
    if (streak >= 30) Eggs.unlock(context, "daily30")
    val found = Eggs.found(context)
    if (found.isNotEmpty()) {
        AppLog.event("gsui", "Пасхалок открыто: " + found.size.toString() + " из " + Eggs.ALL.size)
    }
}

/** Уровень эффектов, применённый к коже: единая точка входа для экранов. */
private fun glassKind(id: String): Glass.Kind = when (id) {
    "matte" -> Glass.Kind.MATTE
    "frost" -> Glass.Kind.FROST
    "aqua" -> Glass.Kind.AQUA
    else -> Glass.Kind.LIQUID
}
