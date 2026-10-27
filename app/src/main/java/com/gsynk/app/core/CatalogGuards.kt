package com.gsynk.app.core

/**
 * Проверки каталога, которые должны выполняться всегда.
 *
 * Каталог — это одна таблица на всё приложение: если в ней появится два одинаковых
 * маршрута, человек нажмёт плитку и попадёт не туда. Такие ошибки ловим тестом,
 * а не глазами на телефоне.
 */
object CatalogGuards {

    /** Все маршруты собраны правильно: иначе плитка просто ничего не откроет. */
    fun everyRouteIsWellFormed(routes: List<String>): Boolean = routes.all { routeIsWellFormed(it) }

    /** Одна и та же функция не должна появляться дважды под одним именем. */
    fun everyIdIsUnique(ids: List<String>): Boolean = ids.size == ids.distinct().size

    /** Маршрут разбирается на вид и имя — иначе он просто не откроется. */
    fun routeIsWellFormed(route: String): Boolean {
        val index = route.indexOf(':')
        if (index <= 0 || index == route.length - 1) return false
        val kind = route.substring(0, index)
        return kind in setOf("panel", "page", "tool", "flag", "lab", "sys")
    }

    /**
     * У маршрута есть получатель: имя маршрута встречается в исходниках обработчиков.
     *
     * Проверка нарочно простая и честная: она смотрит исходный текст (как это делает
     * `UiGuardTest`), а не догадывается. Смысл — не пустить в каталог плитку-тупик:
     * человек нажимает и попадает в никуда.
     */
    fun routeHasReceiver(route: String, sources: List<String>): Boolean {
        val name = route.substringAfter(':', missingDelimiterValue = route)
        if (name.isBlank()) return false
        val quoted = "\"" + name + "\""
        return sources.any { it.contains(quoted) }
    }

    /** Все ли маршруты находят свой обработчик. */
    fun everyRouteHasReceiver(routes: List<String>, sources: List<String>): Boolean =
        routes.all { routeHasReceiver(it, sources) }

    /** Размер плитки должен быть одним из четырёх Gioui. */
    fun knownSizes(): List<String> = listOf("1x1", "2x2", "4x2", "2x4", "4x4")
}
