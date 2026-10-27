package com.gsynk.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.Permissions
import com.gsynk.app.ui.theme.CardBg
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapeRed
import com.gsynk.app.ui.theme.GrapeViolet
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Обязательная панель разрешений.
 *
 * Пока не выданы все доступы, панель занимает весь экран приложения и не
 * закрывается: кнопка «назад» перехвачена, смахивать нечего — это не шторка.
 * Как только список закрыт полностью, панель уходит сама.
 */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(requiredItems(context)) }
    var askedAtLeastOnce by remember { mutableStateOf(false) }
    var ticks by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) items = requiredItems(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // пока панель на экране — проверяем состояние раз в секунду
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            items = requiredItems(context)
            ticks++
        }
    }

    // «назад» не закрывает панель: пока разрешения не выданы, выхода из неё нет
    BackHandler(enabled = true) { /* намеренно ничего */ }

    val granted = items.count { it.second }
    val total = items.size
    if (total == 0 || granted == total) {
        // всё включено — панель исчезает сама
        LaunchedEffect(Unit) {
            AppLog.event("Разрешения", "все доступы выданы ($granted из $total)")
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(CardBg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Панель разрешений полноэкранная: без этих отступов заголовок уезжал
                // под строку состояния, а кнопки — под панель навигации.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 26.dp)
        ) {
            Text(
                text = "разрешения",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = Gioui.Type.tileLive, fontWeight = FontWeight.Light),
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "GSYNK не может работать наполовину: правила, связь, отчёты и игры " +
                    "включаются только с полным набором доступов. Панель закроется сама, " +
                    "как только список станет зелёным.",
                color = TextDim,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
            )

            Spacer(Modifier.height(16.dp))

            // полоса прогресса: строго прямоугольная
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .border(1.dp, CardBorder, RectangleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(granted.toFloat() / total)
                            .height(10.dp)
                            .background(if (granted == total) GrapeGreen else GrapeViolet)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("$granted / $total", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium))
            }

            Spacer(Modifier.height(18.dp))

            items.forEach { (item, isGranted) ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isGranted) GrapeGreen else GrapeRed, RectangleShape)
                        .padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp))
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isGranted) "включено" else "нужно включить",
                                color = if (isGranted) GrapeGreen else GrapeRed,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = hint(item.id),
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                        )
                        if (!isGranted) {
                            Spacer(Modifier.height(9.dp))
                            PrimaryButton(
                                text = "Открыть системный экран",
                                onClick = {
                                    askedAtLeastOnce = true
                                    AppLog.event("Разрешения", "открываю экран: ${item.title}")
                                    Permissions.open(context, item.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RectangleShape)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "проверка №$ticks",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Список обновляется сам. Вернулись из настроек — состояние " +
                            "пересчитается автоматически.",
                        color = TextDim,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = Gioui.Type.body, fontWeight = FontWeight.Normal)
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Проверить снова",
                        onClick = { items = requiredItems(context) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (askedAtLeastOnce) {
                    "Если системный экран не открылся — разрешение выдаётся вручную в настройках приложения."
                } else {
                    "Кнопка «назад» здесь не работает: сначала доступы."
                },
                color = TextDim,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = Gioui.Type.caption, fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Подсказка, как включить доступ именно на этой прошивке. */
private fun hint(id: String): String = when (id) {
    Permissions.ID_EXACT -> "Настройки → Приложения → GSYNK → Будильники и напоминания: «Разрешить»."
    Permissions.ID_WRITE -> "Настройки → Приложения → GSYNK → Изменение системных настроек: «Разрешить»."
    Permissions.ID_BATTERY -> "Настройки → Батарея → GSYNK: «Без ограничений»."
    Permissions.ID_NOTIF -> "Настройки → Уведомления → GSYNK: «Разрешить уведомления»."
    Permissions.ID_OVERLAY -> "Настройки → Приложения → GSYNK → Другие разрешения → «Поверх других окон»."
    Permissions.ID_MIC -> "Запрос появится при первом открытии шумомера, либо: Настройки → Приложения → GSYNK → Микрофон."
    Permissions.ID_AUTOSTART -> "HyperOS: Настройки → Приложения → GSYNK → Автозапуск: «Разрешить»."
    else -> "Откройте системный экран и включите доступ."
}

/** Разрешения, без которых панель не закроется: все доступные на этом устройстве. */
private fun requiredItems(context: Context): List<Pair<Permissions.Item, Boolean>> {
    val base = Permissions.snapshot(context).items
        .map { it to it.granted }
        .toMutableList()
    if (Permissions.autostartAvailable(context)) {
        val granted = Permissions.autostartEnabled(context)
        base.add(Permissions.Item(Permissions.ID_AUTOSTART, "Автозапуск", granted) to granted)
    }
    return base
}
