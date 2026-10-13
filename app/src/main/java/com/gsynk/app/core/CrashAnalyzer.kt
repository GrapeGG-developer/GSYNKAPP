package com.gsynk.app.core

/**
 * Разбор отчётов о сбоях: превращает текст трассировки в короткую подпись
 * («что сломалось и где»), чтобы одинаковые падения считались вместе, а не
 * лежали десятком неотличимых файлов.
 *
 * Чистые функции без Android — покрыты тестами.
 */
object CrashAnalyzer {

    /** Одна группа одинаковых падений. */
    data class Group(
        val signature: String,
        val count: Int,
        val lastStamp: String,
        val files: List<String>
    ) {
        val label: String get() = if (count == 1) "1 раз" else "$count раза"
    }

    private val typeLine = Regex("""^\s*([\w.$]+(?:Exception|Error|Throwable))\b[: ]?(.*)$""", RegexOption.MULTILINE)
    private val frame = Regex("""\bat ([\w.$]+)\(([A-Za-z0-9_]+\.(?:kt|java))(?::(\d+))?\)""")
    private val digits = Regex("""\d+""")

    /**
     * Подпись сбоя: тип исключения, суть в паре слов и первое место в коде GSYNK.
     *
     * Числа в сообщении заменяются на «N» — иначе «таймаут 1500» и «таймаут 2000»
     * считались бы разными сбоями, хотя это одно и то же.
     */
    fun signature(text: String): String {
        val head = typeLine.find(text)?.let { match ->
            val type = match.groupValues[1].substringAfterLast('.')
            val reason = match.groupValues[2].trim().let { line ->
                if (line.isEmpty()) "" else ": " + mask(line).take(70)
            }
            type + reason
        } ?: "Неизвестный сбой"

        val place = frame.findAll(text)
            .map { it.groupValues[1] to it.groupValues[2] to it.groupValues[3] }
            .firstOrNull { (classAndFile, _) -> classAndFile.first.startsWith("com.gsynk.app") }
            ?.let { (classAndFile, line) ->
                val (className, file) = classAndFile
                // «com.gsynk.app.core.DeviceStats.snapshot» → «DeviceStats.snapshot»
                val short = className.split('.').takeLast(2).joinToString(".")
                val where = if (line.isEmpty()) file else "$file:$line"
                "$short → $where"
            }

        return if (place == null) head else "$head · $place"
    }

    /** Прячет длинные числа: они в подписи не нужны. */
    private fun mask(line: String): String = digits.replace(line) { match ->
        if (match.value.length >= 4) "N" else match.value
    }

    /** Группирует отчёты (имя файла + текст) по подписи, частые — первыми. */
    fun group(reports: List<Pair<String, String>>): List<Group> =
        reports.groupBy { (_, text) -> signature(text) }
            .map { (signature, items) ->
                val ordered = items.sortedByDescending { (name, _) -> name }
                Group(
                    signature = signature,
                    count = items.size,
                    lastStamp = stamp(ordered.first().first),
                    files = ordered.map { it.first }
                )
            }
            .sortedWith(compareByDescending<Group> { it.count }.thenByDescending { it.lastStamp })

    /** Из «crash-20261013-142530.txt» делает «13.10.2026 14:25». */
    fun stamp(fileName: String): String {
        val parts = fileName.substringBeforeLast('.').split('-')
        val day = parts.getOrNull(1) ?: return ""
        val time = parts.getOrNull(2)?.take(6) ?: return ""
        if (day.length != 8 || time.length != 6 || !day.all { it.isDigit() }) return ""
        return "${day.substring(6, 8)}.${day.substring(4, 6)}.${day.substring(0, 4)} " +
            "${time.substring(0, 2)}:${time.substring(2, 4)}"
    }

    /** Короткая строка «на что смотреть в первую очередь» — идёт в шапку отчёта. */
    fun verdict(groups: List<Group>): String = when {
        groups.isEmpty() -> "Сбоев не зафиксировано"
        groups.first().count >= 3 -> "Повторяющийся сбой: ${groups.first().signature}"
        else -> "Сбой: ${groups.first().signature}"
    }
}
