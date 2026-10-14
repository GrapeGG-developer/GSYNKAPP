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

    /** Размер плитки должен быть одним из четырёх Metro. */
    fun knownSizes(): List<String> = listOf("1x1", "2x2", "4x2", "2x4", "4x4")
}
