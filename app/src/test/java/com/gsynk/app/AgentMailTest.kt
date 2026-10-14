package com.gsynk.app

import com.gsynk.app.core.AgentMail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переписка с агентом: проверяем всё, что можно проверить без интернета.
 *
 * Сеть в тестах не трогаем — вместо неё разбираем настоящие ответы GitHub (куски
 * их JSON лежат прямо здесь) и проверяем правила: как отличается своё сообщение от
 * ответа агента, как чистится текст, как считаются непрочитанные.
 */
class AgentMailTest {

    private val comments = """
        [
          {
            "id": 1001,
            "body": "[app] привет, на x3 pro не открывается фаза частот",
            "created_at": "2026-10-14T15:10:00Z",
            "user": {"login": "GrapeGG-developer"}
          },
          {
            "id": 1002,
            "body": "Принял: сделаю запасной путь через /proc, если /sys закрыт.",
            "created_at": "2026-10-14T16:41:00Z",
            "user": {"login": "GrapeGG-developer"}
          },
          {
            "id": 1003,
            "body": "[app] ок, жду",
            "created_at": "2026-10-14T16:55:00Z"
          }
        ]
    """.trimIndent()

    // ————— свои против ответов агента —————

    @Test
    fun messagesFromTheAppAreMarked() {
        assertTrue(AgentMail.isMine("[app] проверка связи"))
        assertTrue(AgentMail.isMine("   [app] пробелы впереди"))
        assertFalse(AgentMail.isMine("Принял, сделаю"))
        assertFalse(AgentMail.isMine(""))
    }

    @Test
    fun markIsRemovedFromTheText() {
        assertEquals("проверка связи", AgentMail.stripMark("[app] проверка связи"))
        assertEquals("ответ агента", AgentMail.stripMark("ответ агента"))
        assertEquals("", AgentMail.stripMark("[app]"))
    }

    // ————— упаковка перед отправкой —————

    @Test
    fun textIsMarkedAndTrimmedBeforeSending() {
        val packed = AgentMail.pack("  что не работает  ")
        assertTrue(packed.startsWith(AgentMail.APP_MARK))
        assertEquals("что не работает", AgentMail.stripMark(packed))
    }

    @Test
    fun secretsAreCutOutBeforeSending() {
        val packed = AgentMail.pack("вот токен ghp_abcdefghijklmnopqrstuvwx1234 и почта me@example.com")
        assertFalse(packed.contains("ghp_"))
        assertFalse(packed.contains("me@example.com"))
        assertTrue(packed.contains("[токен скрыт]"))
    }

    @Test
    fun longTextIsCutToASendableLength() {
        val packed = AgentMail.pack("а".repeat(5_000))
        assertTrue(packed.length <= 4_100)
        assertTrue(packed.endsWith("…"))
    }

    @Test
    fun tooLongWarnsBeforeSending() {
        assertFalse(AgentMail.tooLong("короткое сообщение"))
        assertTrue(AgentMail.tooLong("а".repeat(4_001)))
    }

    @Test
    fun carriageReturnsAreRemoved() {
        val packed = AgentMail.pack("первая\r\nвторая")
        assertFalse(packed.contains("\r"))
        assertTrue(packed.contains("первая"))
    }

    // ————— разбор ответа GitHub —————

    @Test
    fun commentsAreParsedOldestFirst() {
        val parsed = AgentMail.parseComments(comments)
        assertEquals(3, parsed.size)
        assertEquals(1001L, parsed.first().id)
        assertEquals(1003L, parsed.last().id)
    }

    @Test
    fun sidesAreRecognizedInParsedComments() {
        val parsed = AgentMail.parseComments(comments)
        assertTrue(parsed[0].mine)
        assertFalse(parsed[1].mine)
        assertTrue(parsed[2].mine)
        // Текст показывается уже без служебной метки.
        assertEquals("привет, на x3 pro не открывается фаза частот", parsed[0].text)
        assertEquals("ок, жду", parsed[2].text)
    }

    @Test
    fun brokenJsonDoesNotCrashTheApp() {
        assertTrue(AgentMail.parseComments("не json").isEmpty())
        assertTrue(AgentMail.parseComments("").isEmpty())
        assertTrue(AgentMail.parseComments("{}").isEmpty())
    }

    @Test
    fun issueNumberIsFoundByExactTitle() {
        val payload = """[{"number":7,"title":"другое"},{"number":42,"title":"${AgentMail.THREAD_TITLE}"}]"""
        assertEquals(42, AgentMail.parseIssueNumber(payload))
        assertEquals(0, AgentMail.parseIssueNumber("[]"))
        assertEquals(0, AgentMail.parseIssueNumber("мусор"))
    }

    // ————— непрочитанные —————

    @Test
    fun unseenCountsOnlyAgentAnswers() {
        val parsed = AgentMail.parseComments(comments)
        // Оба ответа агента уже прочитаны (метка = 1003): новых нет.
        assertEquals(0, AgentMail.unseen(parsed, 1003L))
        // Прочитано только первое сообщение: непрочитанных ровно один — ответ агента.
        assertEquals(1, AgentMail.unseen(parsed, 1001L))
        // Ничего не прочитано: единственный ответ агента (свои не считаем).
        assertEquals(1, AgentMail.unseen(parsed, 0L))
    }

    @Test
    fun lastIdIsTheNewestOne() {
        assertEquals(1003L, AgentMail.lastId(AgentMail.parseComments(comments)))
        assertEquals(0L, AgentMail.lastId(emptyList()))
    }

    // ————— время и хвост —————

    @Test
    fun stampIsShortAndLocal() {
        // 18:41 UTC + 3 часа = 21:41 по Москве.
        assertEquals("14.10 21:41", AgentMail.prettyStamp("2026-10-14T18:41:00Z"))
        assertEquals("01.01 03:05", AgentMail.prettyStamp("2026-01-01T00:05:00Z"))
    }

    @Test
    fun brokenStampFallsBackToItself() {
        assertEquals("не дата", AgentMail.prettyStamp("не дата"))
    }

    @Test
    fun tailKeepsTheLastMessages() {
        val many = (1..300).map { AgentMail.Msg(id = it.toLong(), body = "[app] " + it) }
        val cut = AgentMail.tail(many, 200)
        assertEquals(200, cut.size)
        assertEquals(300L, cut.last().id)
        assertEquals(101L, cut.first().id)
    }

    // ————— обсуждение должно существовать в репозитории —————

    @Test
    fun threadTitleIsStable() {
        // Менять заголовок нельзя: по нему приложение находит уже созданное обсуждение.
        assertEquals("GSYNK ↔ агент: переписка", AgentMail.THREAD_TITLE)
    }
}
