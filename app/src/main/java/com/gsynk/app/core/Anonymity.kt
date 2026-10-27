package com.gsynk.app.core

import java.security.MessageDigest
import kotlin.random.Random

/** Локальные инструменты анонимности: персоны, ники, нейтральные аватары и проверка следов текста. */
object Anonymity {

    data class Persona(
        val nickname: String,
        val bio: String,
        val pronoun: String,
        val style: String,
        val timezone: String,
        val motto: String
    )

    data class TraceFinding(val title: String, val detail: String, val risk: Int)

    enum class AvatarStyle(val label: String) {
        SYMMETRIC("симметрична"),
        BLOCKS("блоки"),
        RINGS("кольца")
    }

    private val roots = listOf(
        "Nova", "Vector", "Ghost", "Pulse", "Vine", "Quantum", "Shadow", "Orbit", "Pixel", "Signal",
        "Mosaic", "Velvet", "Cipher", "Neon", "Amber", "Silent", "Binary", "Lunar", "Cosmos", "Prism",
        "grape", "echo", "byte", "matrix", "Comet", "aurora", "nexus", "zenith", "onyx", "mist"
    )
    private val tails = listOf(
        "walker", "signal", "field", "line", "wave", "runner", "point", "garden", "pulse", "cipher",
        "north", "quiet", "lab", "cell", "byte", "moon", "orbit", "trace", "shift", "code"
    )
    private val accents = listOf("void", "solar", "mono", "quiet", "night", "safe", "grey", "soft", "ultra", "zero")
    private val bios = listOf(
        "Собираю красивые системы и спокойные интерфейсы.",
        "Здесь только идеи, код и эксперименты.",
        "Тихий наблюдатель цифровой среды.",
        "Люблю короткие заметки и длинные плейлисты.",
        "Проект за проектом — без лишнего шума.",
        "Изучаю технологии и делаю необычные инструменты."
    )
    private val styles = listOf("лаконичный", "технический", "дружелюбный", "загадочный", "экспериментальный", "спокойный")
    private val zones = listOf("UTC−5", "UTC−2", "UTC+0", "UTC+1", "UTC+3", "UTC+5", "UTC+8", "UTC+10")
    private val mottos = listOf(
        "Меньше следов — больше смысла.",
        "Сигнал важнее имени.",
        "Каждый байт на своём месте.",
        "Тишина тоже может быть интерфейсом.",
        "Сначала проверяй, потом публикуй.",
        "Новая маска — новый контекст."
    )

    fun nicknames(seed: String, count: Int = 12): List<String> {
        val random = Random(stableSeed(seed.ifBlank { "GSYNK" }))
    val out = LinkedHashSet<String>()
        var guard = 0
        while (out.size < count && guard++ < 200) {
            val candidate = when (random.nextInt(6)) {
                0 -> roots.random(random).lowercase() + "_" + tails.random(random)
                1 -> accents.random(random) + roots.random(random).replaceFirstChar { it.uppercaseChar() }
                2 -> roots.random(random).lowercase() + random.nextInt(10, 990)
                3 -> tails.random(random) + "." + accents.random(random)
                4 -> "x_" + roots.random(random).lowercase() + "_" + random.nextInt(10, 99)
                else -> roots.random(random).lowercase() + tails.random(random).replaceFirstChar { it.uppercaseChar() }
            }
            out += candidate.replace(Regex("[^A-Za-z0-9_.-]"), "")
        }
        return out.toList()
    }

    fun persona(seed: String): Persona {
        val random = Random(stableSeed(seed.ifBlank { "anonymous-persona" }))
        return Persona(
            nickname = nicknames(seed, 12).first(),
            bio = bios.random(random),
            pronoun = listOf("не указано", "они/их", "он/его", "она/её").random(random),
            style = styles.random(random),
            timezone = zones.random(random),
            motto = mottos.random(random)
        )
    }

    fun stableSeed(text: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (digest[i].toLong() and 0xFF)
        return value
    }

    fun avatarCells(seed: String, style: AvatarStyle): List<Boolean> {
        val random = Random(stableSeed(seed + ":" + style.name))
        val cells = MutableList(64) { false }
        when (style) {
            AvatarStyle.SYMMETRIC -> {
                for (y in 0 until 8) for (x in 0 until 4) {
                    val on = random.nextFloat() < 0.46f
                    cells[y * 8 + x] = on
                    cells[y * 8 + (7 - x)] = on
                }
            }
            AvatarStyle.BLOCKS -> {
                for (blockY in 0 until 4) for (blockX in 0 until 4) {
                    val on = random.nextFloat() < 0.52f
                    for (dy in 0..1) for (dx in 0..1) cells[(blockY * 2 + dy) * 8 + blockX * 2 + dx] = on
                }
            }
            AvatarStyle.RINGS -> {
                val rings = List(4) { random.nextFloat() < 0.58f }
                for (y in 0 until 8) for (x in 0 until 8) {
                    val ring = minOf(x, y, 7 - x, 7 - y)
                    cells[y * 8 + x] = rings[ring] xor ((x + y) % 5 == 0 && random.nextFloat() < 0.28f)
                }
            }
        }
        return cells
    }

    fun avatarPalette(seed: String): Triple<Int, Int, Int> {
        val random = Random(stableSeed("palette:" + seed))
        val palettes = listOf(
            Triple(0xFF17122B.toInt(), 0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt()),
            Triple(0xFF071A1A.toInt(), 0xFF2DD4BF.toInt(), 0xFFA3E635.toInt()),
            Triple(0xFF201016.toInt(), 0xFFF472B6.toInt(), 0xFFFBBF24.toInt()),
            Triple(0xFF111827.toInt(), 0xFF60A5FA.toInt(), 0xFFC084FC.toInt()),
            Triple(0xFF171717.toInt(), 0xFFE5E7EB.toInt(), 0xFF38BDF8.toInt())
        )
        return palettes[random.nextInt(palettes.size)]
    }

    fun inspectText(input: String): List<TraceFinding> {
        if (input.isBlank()) return listOf(TraceFinding("Текст пустой", "Вставь заметку, описание профиля или сообщение — проверка работает только локально.", 0))
        val findings = ArrayList<TraceFinding>()
        if (Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").containsMatchIn(input)) {
            findings += TraceFinding("Адрес почты", "В тексте есть e-mail. Для отдельной персоны лучше использовать отдельный адрес.", 3)
        }
        if (Regex("(?:\\+\\d[\\d ()-]{8,}\\d|8[\\d ()-]{9,}\\d)").containsMatchIn(input)) {
            findings += TraceFinding("Возможный телефон", "Найдена длинная телефоноподобная последовательность цифр.", 3)
        }
        if (Regex("https?://|www\\.|t\\.me/|vk\\.com/|github\\.com/").containsMatchIn(input)) {
            findings += TraceFinding("Внешние ссылки", "Ссылки связывают персону с другими площадками и могут раскрывать старый ник.", 2)
        }
        if (Regex("@[A-Za-z0-9_]{3,}").containsMatchIn(input)) {
            findings += TraceFinding("Упоминание ника", "Есть @-упоминание: проверь, не ведёт ли оно к основной личности.", 2)
        }
        if (Regex("\\b(19|20)\\d{2}[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\\d|3[01])\\b").containsMatchIn(input)) {
            findings += TraceFinding("Точная дата", "Полная дата может быть датой рождения, события или создания старого аккаунта.", 1)
        }
        if (Regex("(?i)\\b(город|живу в|учусь в|работаю в|мой адрес|моя школа|мой университет)\\b").containsMatchIn(input)) {
            findings += TraceFinding("Контекст места или организации", "Фразы о месте, учёбе или работе резко сужают круг поиска.", 2)
        }
        val words = input.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length > 3 }
        val repeated = words.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).filter { it.value >= 4 }
        if (repeated.isNotEmpty()) {
            findings += TraceFinding(
                "Повторяющийся стиль",
                "Частые слова: " + repeated.joinToString { "«" + it.key + "» ×" + it.value } + ". Устойчивые обороты могут связывать тексты разных аккаунтов.",
                1
            )
        }
        if (input.length > 2_000) {
            findings += TraceFinding("Большой объём", "Длинный текст даёт больше стилистических признаков. Для коротких публикаций отдельная персона устойчивее.", 1)
        }
        if (findings.isEmpty()) {
            findings += TraceFinding("Явных прямых следов нет", "Не найдены почта, телефон, ссылки и явные упоминания. Это не гарантия анонимности: время публикаций и сеть здесь не проверяются.", 0)
        }
        return findings
    }
}
