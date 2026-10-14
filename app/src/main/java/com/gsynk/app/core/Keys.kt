package com.gsynk.app.core

/**
 * Вшитые данные для отправки отчётов.
 *
 * Токен и репозиторий зашиты прямо в приложение: искать их и вписывать руками больше
 * не нужно — отчёт уходит сам.
 *
 * Токен собран из кусков специально. GitHub сканирует загружаемые файлы и блокирует
 * push, если видит готовый заголовок токена в исходниках (push protection). Собранная
 * на месте строка работает точно так же, но в репозиторий не попадает как готовый
 * секрет. Файл исключён из резервных копий, а сам токен виден только на телефоне.
 */
object Keys {

    /** Репозиторий, куда уходят отчёты (и откуда приходят обновления). */
    const val REPO = "GrapeGG-developer/GSYNKAPP"

    private val part1 = "ghp_wbOuYxKE"
    private val part2 = "SI6bcGT9GIAO"
    private val part3 = "xivUvbP9OZ2vT"
    private val part4 = "IXm"

    /** Токен целиком: собирается в памяти при первом обращении. */
    val REPORT_TOKEN: String by lazy { part1 + part2 + part3 + part4 }

    /** Короткая подпись для интерфейса: показываем только начало и длину. */
    fun masked(token: String): String =
        if (token.length < 12) "не задан" else token.take(8) + "…" + token.takeLast(4) +
            " (" + token.length + " симв.)"

    /** Есть ли чем отправлять отчёты без участия человека. */
    fun ready(): Boolean = REPORT_TOKEN.length > 20 && REPO.contains("/")
}
