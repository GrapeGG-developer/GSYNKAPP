package com.gsynk.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сторож форматирования строк.
 *
 * 13.10.2026 приложение падало при входе в «Инструменты»: в строке
 * `"${percent}% • %.1f°C".format(temp)` знак «%» стоял рядом с «•», и Java решала,
 * что это спецификатор `%•`. Итог — UnknownFormatConversionException и вылет.
 *
 * Этот тест читает исходники приложения и не даёт такому вернуться: рядом с вызовом
 * `.format(...)` в строке не должно оставаться «%», не входящих в спецификаторы.
 */
class FormatGuardTest {

    /** Допустимые спецификаторы: %, %s, %.1f, %d, %,d, %2$s и так далее. */
    private val specifier = Regex("%(?:%|\\d+\\$)?[,\\-+ 0#(]*\\d*(?:\\.\\d+)?[bBhHsScCdoxXeEfgGaAn%]")

    @Test
    fun sources_haveNoBrokenFormatPatterns() {
        val sources = findSources()
        assertTrue(
            "не нашёл исходники приложения от " + File("").absolutePath,
            sources.isNotEmpty()
        )

        val broken = mutableListOf<String>()
        sources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (!line.contains(".format(")) return@forEachIndexed
                Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(line).forEach { match ->
                    val literal = match.groupValues[1]
                    if (!literal.contains("%")) return@forEach
                    val leftover = specifier.replace(literal, "")
                    if (leftover.contains("%")) {
                        broken.add("${file.name}:${index + 1} → $literal")
                    }
                }
            }
        }

        assertTrue(
            "ломанный формат строки снова в коде: " + broken.joinToString("; "),
            broken.isEmpty()
        )
    }

    @Test
    fun guard_detectsTheRealCrashPattern() {
        // тот самый вид строки, который уронил приложение
        val bad = "\${percent}% • %.1f°C"
        val leftover = specifier.replace(bad, "")
        assertTrue("сторож должен видеть проблему", leftover.contains("%"))
    }

    /** Ищем исходники рядом с модулем — тесты запускаются из каталога `app`. */
    private fun findSources(): List<File> {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../app/src/main/java")
        )
        val root = candidates.firstOrNull { it.isDirectory } ?: return emptyList()
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
