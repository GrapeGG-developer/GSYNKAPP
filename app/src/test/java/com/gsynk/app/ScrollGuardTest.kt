package com.gsynk.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сторож вложенной прокрутки.
 *
 * 13.10.2026 приложение падало при открытии сопряжения Grape Link:
 * `IllegalStateException: Vertically scrollable component was measured with an
 * infinity maximum height constraints`. Причина — свой `verticalScroll`
 * внутри `GrapeScreen`, который уже прокручивает содержимое.
 *
 * Правило простое: внутри `GrapeScreen` вертикальную прокрутку заводить нельзя.
 * Исключение — блок с ограниченной высотой (`heightIn`, `height`, `size`, `weight`),
 * как моноширинный блок отчёта в диагностике.
 */
class ScrollGuardTest {

    private fun uiSources(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/gsynk/app/ui"),
            File("app/src/main/java/com/gsynk/app/ui"),
            File("../app/src/main/java/com/gsynk/app/ui")
        )
        val root = candidates.firstOrNull { it.isDirectory } ?: return emptyList()
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Признак того, что высота блока задана явно — тогда прокрутка безопасна. */
    private val bounded = Regex("(heightIn|height\\(|size\\(|weight\\()")

    @Test
    fun screensInsideGrapeScreenDoNotNestScroll() {
        val sources = uiSources()
        assertTrue("не нашёл исходники интерфейса", sources.isNotEmpty())

        val broken = mutableListOf<String>()
        sources.forEach { file ->
            val lines = file.readLines()
            if (lines.none { it.contains("GrapeScreen(") }) return@forEach
            lines.forEachIndexed { index, line ->
                if (!line.contains(".verticalScroll(")) return@forEachIndexed
                val from = (index - 10).coerceAtLeast(0)
                val context = lines.subList(from, index + 1).joinToString("\n")
                if (!bounded.containsMatchIn(context)) {
                    broken.add(file.name + ":" + (index + 1))
                }
            }
        }

        assertTrue(
            "вложенная прокрутка внутри GrapeScreen снова в коде: " + broken.joinToString("; "),
            broken.isEmpty()
        )
    }

    /** Список внутри прокручиваемого экрана тоже обязан иметь свою высоту. */
    @Test
    fun listsInsideGrapeScreenHaveTheirOwnHeight() {
        val broken = mutableListOf<String>()
        uiSources().forEach { file ->
            val lines = file.readLines()
            if (lines.none { it.contains("GrapeScreen(") }) return@forEach
            lines.forEachIndexed { index, line ->
                if (!line.contains("LazyColumn(") && !line.contains("LazyRow(")) return@forEachIndexed
                val from = (index - 8).coerceAtLeast(0)
                val context = lines.subList(from, index + 12).joinToString("\n")
                if (!bounded.containsMatchIn(context)) {
                    broken.add(file.name + ":" + (index + 1))
                }
            }
        }
        assertTrue(
            "список без своей высоты внутри GrapeScreen: " + broken.joinToString("; "),
            broken.isEmpty()
        )
    }

    @Test
    fun guardSeesTheRealCrashPattern() {
        // тот самый вид блока, который уронил сопряжение
        val bad = listOf(
            "    ) {",
            "        Column(",
            "            Modifier",
            "                .fillMaxSize()",
            "                .verticalScroll(rememberScrollState()),",
            "            verticalArrangement = Arrangement.spacedBy(12.dp)",
            "        ) {"
        )
        val index = bad.indexOfFirst { it.contains(".verticalScroll(") }
        val context = bad.subList(0, index + 1).joinToString("\n")
        assertTrue("сторож обязан видеть этот случай", !bounded.containsMatchIn(context))
    }

    @Test
    fun boundedBlocksAreAllowed() {
        val good = listOf(
            "        Box(",
            "            Modifier",
            "                .fillMaxWidth()",
            "                .heightIn(max = 320.dp)",
            "        ) {",
            "            Column(",
            "                Modifier",
            "                    .verticalScroll(rememberScrollState())"
        )
        val index = good.indexOfFirst { it.contains(".verticalScroll(") }
        val context = good.subList(0, index + 1).joinToString("\n")
        assertTrue("блок с ограниченной высотой не должен считаться ошибкой", bounded.containsMatchIn(context))
    }
}
