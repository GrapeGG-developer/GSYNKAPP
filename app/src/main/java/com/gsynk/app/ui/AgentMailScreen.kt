package com.gsynk.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gsynk.app.core.AgentMail
import com.gsynk.app.core.Keys
import com.gsynk.app.ui.theme.CardBorder
import com.gsynk.app.ui.theme.ChipBg
import com.gsynk.app.ui.theme.GrapeCyan
import com.gsynk.app.ui.theme.GrapeGreen
import com.gsynk.app.ui.theme.GrapePink
import com.gsynk.app.ui.theme.GrapeVioletDeep
import com.gsynk.app.ui.theme.GrapeVioletSoft
import com.gsynk.app.ui.theme.TextDim
import com.gsynk.app.ui.theme.TextPrimary
import com.gsynk.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Связь с агентом: переписка прямо в приложении.
 *
 * Мгновенного ответа здесь быть не может и обещать его нечестно: агент не держит
 * включённый сервер. Зато переписка не теряется — она живёт в общем репозитории,
 * приложение пишет туда и подтягивает ответы само, как только они появляются.
 */
@Composable
fun AgentMailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(AgentMail.cached(context)) }
    var status by remember { mutableStateOf("переписка подтянется сама — жди мгновение") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf(AgentMail.threadNumber(context)) }

    fun reload(showAnswer: Boolean = true) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { AgentMail.pull(context) }
            messages = result.messages
            issue = result.issue
            if (result.ok) AgentMail.markSeen(context, AgentMail.lastId(result.messages))
            if (showAnswer) status = result.message
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    GrapeScreen(
        title = "Связь с агентом",
        subtitle = if (issue > 0) "обсуждение №" + issue + " в репозитории" else "переписка через GitHub",
        onBack = onBack
    ) {
        // ————— как это работает —————
        SurfaceCard(borderColor = GrapeVioletSoft.copy(alpha = 0.4f)) {
            Text(
                text = "Пиши как в мессенджере — но ответ придёт не сразу",
                color = TextPrimary,
                fontSize = Metro.Type.body,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Агент не держит включённый сервер: он читает сообщения, когда открывает " +
                    "работу над проектом, и отвечает туда же. Приложение забирает ответ само — " +
                    "на экране «Нагрузка» или при следующем заходе сюда появится капсула «агент ответил».",
                color = TextSecondary,
                fontSize = Metro.Type.body
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠ Переписка идёт через публичный репозиторий, её видно всем. Перед отправкой " +
                    "токены, адреса почты и длинные числа вырезаются санитайзером — но пароли и " +
                    "личные данные лучше не писать вовсе.",
                color = GrapePink,
                fontSize = Metro.Type.caption
            )
            if (Keys.ready() && AgentMail.cached(context).isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Чего не хватает? Что сломалось? Что добавить? Пиши своими словами — " +
                        "агент читает всё.",
                    color = TextDim,
                    fontSize = Metro.Type.caption
                )
            }
        }

        // ————— состояние связи —————
        SurfaceCard(borderColor = CardBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (busy) "обращаюсь к репозиторию…" else status,
                        color = if (status.contains("нет соединения") || status.contains("вернул")) {
                            GrapePink
                        } else {
                            TextSecondary
                        },
                        fontSize = Metro.Type.body
                    )
                    Text(
                        text = "сообщений в переписке: " + messages.size +
                            " · моих: " + messages.count { it.mine },
                        color = TextDim,
                        fontSize = Metro.Type.caption
                    )
                }
                if (issue > 0) {
                    Box(
                        Modifier
                            .clip(skinShape("toggle"))
                            .background(ChipBg)
                            .border(1.dp, CardBorder, skinShape("toggle"))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/" + Keys.REPO + "/issues/" + issue)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("в браузере", color = GrapeCyan, fontSize = Metro.Type.caption)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = if (busy) "обновляю…" else "обновить",
                    onClick = { reload() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "прочитано",
                    onClick = {
                        AgentMail.markSeen(context, AgentMail.lastId(messages))
                        status = "отметил прочитанным"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ————— сама переписка —————
        SectionTitle("переписка")
        Spacer(Modifier.height(4.dp))
        if (messages.isEmpty()) {
            SurfaceCard(borderColor = CardBorder) {
                Text(
                    text = "Пока пусто. Первое сообщение — за тобой: напиши, что не так или чего не хватает.",
                    color = TextSecondary,
                    fontSize = Metro.Type.body
                )
            }
        } else {
            // Показываем хвост: длинную переписку листать целиком незачем.
            messages.takeLast(40).forEach { message ->
                MailBubble(message)
            }
            if (messages.size > 40) {
                Text(
                    text = "Показаны последние 40 сообщений из " + messages.size +
                        " · остальное — по кнопке «в браузере»",
                    color = TextDim,
                    fontSize = Metro.Type.caption
                )
            }
        }

        // ————— написать —————
        Spacer(Modifier.height(10.dp))
        SectionTitle("написать агенту")
        Spacer(Modifier.height(6.dp))
        GrapeTextField(
            value = input,
            onValueChange = { input = it },
            label = "сообщение",
            singleLine = false
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = input.trim().length.toString() + " знаков" + if (AgentMail.tooLong(input)) " — длинновато, сократи" else "",
            color = if (AgentMail.tooLong(input)) GrapePink else TextDim,
            fontSize = Metro.Type.caption
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (busy) "жду связи…" else "отправить",
            enabled = input.isNotBlank() && !busy,
            onClick = {
                val text = input
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { AgentMail.send(context, text) }
                    status = result.second
                    if (result.first) input = ""
                    messages = AgentMail.cached(context)
                    issue = AgentMail.threadNumber(context)
                    busy = false
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Пример хорошего сообщения: «на экране фаз чипа частоты ядер пусто, HyperOS " +
                "не отдаёт /sys — сделай запасной путь через /proc». Чем конкретнее, тем быстрее " +
                "агент поправит дело.",
            color = TextDim,
            fontSize = Metro.Type.caption
        )
    }
}

/** Пузырёк сообщения: свои — справа и цветные, ответы агента — слева. */
@Composable
private fun MailBubble(message: AgentMail.Msg) {
    val mine = message.mine
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(skinShape("card"))
                .background(if (mine) GrapeCyan.copy(alpha = 0.14f) else GrapeVioletDeep.copy(alpha = 0.35f))
                .border(
                    1.dp,
                    if (mine) GrapeCyan.copy(alpha = 0.45f) else GrapeVioletSoft.copy(alpha = 0.35f),
                    skinShape("card")
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (mine) "ты" else "агент",
                color = if (mine) GrapeCyan else GrapeVioletSoft,
                fontSize = Metro.Type.caption,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = Metro.Type.body
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AgentMail.prettyStamp(message.createdAt),
                color = TextDim,
                fontSize = Metro.Type.caption
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Значок ответа агента для главного экрана: видно, что есть новое сообщение. */
@Composable
fun AgentMailBanner(unseen: Int, onClick: () -> Unit) {
    SurfaceCard(
        borderColor = GrapeGreen.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Агент ответил",
                    color = GrapeGreen,
                    fontSize = Metro.Type.body,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "новых сообщений: " + unseen + " · открыть переписку",
                    color = TextSecondary,
                    fontSize = Metro.Type.caption
                )
            }
            Text("→", color = GrapeGreen, fontSize = Metro.Type.body)
        }
    }
}
