package com.gsynk.app.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.gsynk.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Роутер GIOUI.
 *
 * Раньше каждый экран знал про соседей десяток лямбд, и открыть что-то из глубины
 * было нельзя. Теперь есть одна строка-маршрут: любой экран может попросить открыть
 * что угодно, а разбирает просьбу тот, кто умеет. Формат простой:
 *
 *  * `panel:nfc` — панель главного экрана (nfc, coop, rogue, tools, settings, update, games);
 *  * `tool:password` — инструмент из мультитула;
 *  * `flag:turbo` — панель внутри мультитула (turbo, chronicle, chat, screensaver, diagnostics);
 *  * `lab:strobe` — экран лаборатории железа;
 *  * `sys:sound` — системный экран Android;
 *  * `page:everyday` — отдельная страница GIOUI (everyday, easter, ai, effects, surprise, bench).
 */
object Router {

    private val _request = MutableStateFlow<String?>(null)
    val request: StateFlow<String?> = _request.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun go(route: String) {
        if (route.isBlank()) return
        _history.value = (listOf(route) + _history.value).take(12)
        _request.value = route
    }

    fun consume() {
        _request.value = null
    }

    fun panel(name: String) = go("panel:" + name)
    fun tool(id: String) = go("tool:" + id)
    fun flag(id: String) = go("flag:" + id)
    fun lab(id: String) = go("lab:" + id)
    fun page(id: String) = go("page:" + id)
    fun sys(key: String) = go("sys:" + key)

    /** Разбор маршрута: вид и имя. Вид — до двоеточия. */
    fun split(route: String): Pair<String, String> {
        val index = route.indexOf(':')
        if (index <= 0) return "page" to route
        return route.substring(0, index) to route.substring(index + 1)
    }

    fun lastRoutes(limit: Int = 6): List<String> = _history.value.take(limit)
}

/**
 * Каталог всего приложения.
 *
 * Один список — все функции. Плитки раскладывает сетка GIOUI, поэтому нет ни одного
 * под-под-подпункта: любую функцию видно с главного экрана, а категории и сортировка
 * лишь меняют порядок, а не прячут вещи.
 */
object Catalog {

    enum class Group(val title: String, val hint: String) {
        CONNECT("связь", "NFC, Wi-Fi, Bluetooth, файлы"),
        SCREEN("экран", "остров, эффекты, стекло, скругления"),
        HARDWARE("железо", "лаборатория: датчики, катушки, экран"),
        LOAD("нагрузка", "процессор, ускоритель, температура"),
        AI("умное", "локальный ИИ и настоящие сети"),
        CRYPTO("шифраторы", "шифры, коды, пароли и авто-расшифровка"),
        SYSTEM("система", "правила, разрешения, отчёты, обновление"),
        FUN("забава", "игры, каждый день, пасхалки")
    }

    enum class Sort(val title: String) {
        FREQUENT("часто"),
        ALPHABET("а-я"),
        RECENT("недавние"),
        GROUP("категории"),
        FAVORITE("мои"),
        NEW("новое")
    }

    /**
     * Плитка каталога. `hot` — насколько часто вещь нужна (для сортировки «часто»),
     * `tone` — цвет плитки по палитре GIOUI, `fresh` — пометка «новое в GIOUI».
     */
    data class Entry(
        val id: String,
        val title: String,
        val subtitle: String,
        val group: Group,
        val size: Gioui.Size,
        val tone: Int,
        val iconRes: Int,
        val route: String,
        val hot: Int = 50,
        val fresh: Boolean = false,
        val words: List<String> = emptyList()
    ) {
        val color: Color get() = Gioui.Palette.tiles[tone.coerceIn(0, Gioui.Palette.tiles.lastIndex)]

        fun matches(query: String): Boolean {
            if (query.isBlank()) return true
            val q = query.lowercase(Locale.ROOT).trim()
            return title.lowercase(Locale.ROOT).contains(q) ||
                subtitle.lowercase(Locale.ROOT).contains(q) ||
                group.title.contains(q) ||
                words.any { it.contains(q) }
        }
    }

    private const val BOLT = R.drawable.ic_bolt
    private const val TUNE = R.drawable.ic_tune
    private const val SHIELD = R.drawable.ic_shield
    private const val VOLUME = R.drawable.ic_volume
    private const val BRIGHT = R.drawable.ic_brightness
    private const val TIME = R.drawable.ic_schedule
    private const val COPY = R.drawable.ic_copy

    /** Основной список. Он же — единственный источник плиток. */
    val ALL: List<Entry> = listOf(
        // ——— связь ———
        Entry("nfc", "grape link", "NFC: обмен, связь, игра", Group.CONNECT, Gioui.Size.WIDE, 4, BOLT, "panel:nfc", 100, words = listOf("нфс", "метка", "контакт")),
        Entry("nearby", "gsynk рядом", "Bluetooth и Wi-Fi Direct", Group.CONNECT, Gioui.Size.MEDIUM, 5, TUNE, "tool:nearby", 96, words = listOf("блютуз", "вайфай", "радар")),
        Entry("ble", "ble-радар", "кто рядом по эфиру", Group.CONNECT, Gioui.Size.SMALL, 6, SHIELD, "lab:ble", 70, fresh = true, words = listOf("блиuetooth", "сканирование")),
        Entry("filelink", "передача файлов", "по Wi-Fi, без проводов", Group.CONNECT, Gioui.Size.SMALL, 7, COPY, "tool:nearby", 68, words = listOf("файл", "обмен")),
        Entry("wifiscan", "каналы wi-fi", "загрузка эфира", Group.CONNECT, Gioui.Size.SMALL, 8, TUNE, "lab:wifi", 58, fresh = true, words = listOf("роутер", "сеть")),
        Entry("netinfo", "подсети", "калькулятор адресов", Group.CONNECT, Gioui.Size.SMALL, 9, BOLT, "tool:subnet", 40, words = listOf("ip", "маска")),
        Entry("coop", "игра вдвоём", "grape link по Wi-Fi", Group.CONNECT, Gioui.Size.SMALL, 10, SHIELD, "panel:coop", 62, words = listOf("кооп", "данж")),
        Entry("nfcmark", "метка-визитка", "записать свою", Group.CONNECT, Gioui.Size.SMALL, 11, COPY, "lab:nfcwrite", 45, fresh = true, words = listOf("запись", "тег")),

        // ——— экран ———
        Entry("island", "остров 2.0", "под камерой, живьём", Group.SCREEN, Gioui.Size.MEDIUM, 0, BRIGHT, "page:island", 92, fresh = true, words = listOf("островок", "камера", "дырка")),
        Entry("effects", "эффекты", "стёкла, лучи, размытие", Group.SCREEN, Gioui.Size.MEDIUM, 12, TIME, "page:effects", 88, fresh = true, words = listOf("стекло", "блюр", "blur", "фокус")),
        Entry("radius", "скругления", "у каждого элемента своё", Group.SCREEN, Gioui.Size.SMALL, 13, TUNE, "page:radius", 64, fresh = true, words = listOf("радиус", "углы")),
        Entry("tweaks", "кастомизация", "акцент, отклик, скорость", Group.SCREEN, Gioui.Size.SMALL, 14, VOLUME, "panel:settings", 60, words = listOf("тема", "цвет")),
        Entry("screensaver", "скринсейвер", "полка с часами", Group.SCREEN, Gioui.Size.SMALL, 15, TIME, "flag:screensaver", 42, words = listOf("часы", "полка")),
        Entry("screen", "проверка экрана", "AMOLED, ШИМ, мультитач", Group.SCREEN, Gioui.Size.SMALL, 16, BRIGHT, "lab:screen", 48, words = listOf("пиксель", "битый")),
        Entry("pwm", "шим-микроскоп", "замер мерцания камерой", Group.SCREEN, Gioui.Size.SMALL, 17, BRIGHT, "lab:pwm", 44, fresh = true, words = listOf("мерцание", "истощение")),
        Entry("latency", "латентность экрана", "касание → свет", Group.SCREEN, Gioui.Size.SMALL, 18, TIME, "lab:latency", 30, fresh = true, words = listOf("отклик", "задержка")),
        Entry("echo", "эхо-линейка", "расстояние звуком", Group.HARDWARE, Gioui.Size.MEDIUM, 14, TUNE, "page:echo", 79, fresh = true, words = listOf("эхо", "дальномер", "расстояние", "звук", "рулетка")),
        Entry("eyes", "проверка глаз", "острота и цвет", Group.SCREEN, Gioui.Size.MEDIUM, 7, BRIGHT, "page:eyes", 63, fresh = true, words = listOf("зрение", "острота", "цвет", "дальтонизм", "таблица")),
        Entry("pulse", "пульс камерой", "удары сердца по свету", Group.HARDWARE, Gioui.Size.WIDE, 12, BOLT, "page:pulse", 85, fresh = true, words = listOf("пульс", "сердце", "бпм", "камера", "здоровье")),

        // ——— управление железом (V4p1: фазы чипа) ———
        Entry("folders", "категории", "все функции по группам GIOUI", Group.SYSTEM, Gioui.Size.WIDE, 1, TUNE, "page:folders", 99, fresh = true, words = listOf("категории", "сортировка", "каталог")),
        Entry("cpu", "частоты ядер", "режимы и ступени", Group.HARDWARE, Gioui.Size.MEDIUM, 2, TUNE, "page:cpu", 86, fresh = true, words = listOf("governor", "мгц", "процессор")),
        Entry("battery", "питание изнутри", "ток, ватты, ресурс", Group.HARDWARE, Gioui.Size.MEDIUM, 3, BOLT, "page:battery", 84, fresh = true, words = listOf("батарея", "ток", "износ")),
        Entry("panel", "панель и наклон", "экран и уровень", Group.SCREEN, Gioui.Size.MEDIUM, 4, BRIGHT, "page:display", 74, fresh = true, words = listOf("герцы", "наклон", "экран")),
        Entry("radio", "радио-тракт", "модем, сеть, wi-fi", Group.CONNECT, Gioui.Size.MEDIUM, 5, BOLT, "page:radio", 76, fresh = true, words = listOf("сигнал", "оператор", "канал")),
        Entry("flash", "светодиод вспышки", "уровни и накал", Group.HARDWARE, Gioui.Size.MEDIUM, 6, BRIGHT, "page:camera", 80, fresh = true, words = listOf("вспышка", "фонарь", "светодиод")),
        Entry("motor", "мотор и резонанс", "частота и поле", Group.HARDWARE, Gioui.Size.MEDIUM, 7, VOLUME, "page:motor", 78, fresh = true, words = listOf("вибрация", "резонанс", "катушка")),
        Entry("deepmem", "память и планировщик", "давление и накопитель", Group.LOAD, Gioui.Size.MEDIUM, 8, TUNE, "page:memory", 70, fresh = true, words = listOf("озу", "диск", "очередь")),
        Entry("sensorsdeep", "датчики изнутри", "что есть и что закрыто", Group.HARDWARE, Gioui.Size.MEDIUM, 9, TIME, "page:sensors", 72, fresh = true, words = listOf("сенсоры", "always-on", "закрытые")),

        // ——— железо ———
        Entry("lab", "лаборатория", "всё железо телефона", Group.HARDWARE, Gioui.Size.WIDE, 2, TUNE, "tool:lab", 90, words = listOf("железо", "опыты")),
        Entry("sensors", "датчики", "все, что есть живьём", Group.HARDWARE, Gioui.Size.MEDIUM, 5, TIME, "lab:sensors", 80, words = listOf("сенсоры", "гироскоп")),
        Entry("coils", "катушки", "вибромотор и звук", Group.HARDWARE, Gioui.Size.SMALL, 6, VOLUME, "lab:coils", 66, words = listOf("вибрация", "мотор")),
        Entry("induction", "индукция", "металл, поля, зарядка", Group.HARDWARE, Gioui.Size.SMALL, 7, SHIELD, "lab:metal", 55, words = listOf("магнит", "металлоискатель")),
        Entry("compass", "компас", "азимут и стороны света", Group.HARDWARE, Gioui.Size.SMALL, 8, TUNE, "lab:compass", 52, fresh = true, words = listOf("север", "курс")),
        Entry("baro", "барометр", "давление и высота", Group.HARDWARE, Gioui.Size.SMALL, 9, TIME, "lab:baro", 38, fresh = true, words = listOf("давление", "высота")),
        Entry("charge", "заряд и ток", "вольты, амперы, ватты", Group.HARDWARE, Gioui.Size.SMALL, 10, BOLT, "lab:charge", 58, fresh = true, words = listOf("батарея", "зарядка")),
        Entry("thermal", "термощуп", "тепловые зоны", Group.HARDWARE, Gioui.Size.SMALL, 11, SHIELD, "lab:thermal", 46, fresh = true, words = listOf("температура", "нагрев")),
        Entry("oscope", "осциллограф", "звук на графике", Group.HARDWARE, Gioui.Size.SMALL, 12, VOLUME, "lab:oscope", 50, fresh = true, words = listOf("микрофон", "волна")),
        Entry("sweep", "свип-тон", "частота от и до", Group.HARDWARE, Gioui.Size.SMALL, 13, VOLUME, "lab:sweep", 40, fresh = true, words = listOf("звук", "генератор")),
        Entry("shaker", "акселерограф", "тряска на графике", Group.HARDWARE, Gioui.Size.SMALL, 14, TUNE, "lab:shake", 44, fresh = true, words = listOf("ускорение", "трясёт")),
        Entry("prox", "приближение", "датчик у динамика", Group.HARDWARE, Gioui.Size.SMALL, 15, SHIELD, "lab:prox", 36, fresh = true, words = listOf("экран гаснет")),
        Entry("light", "люксметр", "свет в комнате", Group.HARDWARE, Gioui.Size.SMALL, 16, BRIGHT, "lab:light", 34, fresh = true, words = listOf("освещённость", "люкс")),
        Entry("haptic", "вибро-код", "азбука мотором", Group.HARDWARE, Gioui.Size.SMALL, 17, VOLUME, "lab:haptic", 42, fresh = true, words = listOf("морзе", "вибро")),
        Entry("strobe", "стробоскоп", "мигание экраном", Group.HARDWARE, Gioui.Size.SMALL, 18, BRIGHT, "lab:strobe", 54, words = listOf("мигание", "диско")),
        Entry("ir", "ик-пульт", "телевизор одним касанием", Group.HARDWARE, Gioui.Size.SMALL, 19, BOLT, "lab:ir", 47, words = listOf("инфракрасный", "пульт")),
        Entry("codes", "инженерные коды", "тестовые меню", Group.HARDWARE, Gioui.Size.SMALL, 0, COPY, "lab:codes", 33, words = listOf("коды", "меню")),
        Entry("screentest", "тест фонарика", "вспышка в такт", Group.HARDWARE, Gioui.Size.SMALL, 1, BRIGHT, "lab:torch", 32, words = listOf("фонарь", "вспышка")),

        // ——— нагрузка ———
        Entry("bench", "бенчмарк", "как AnTuTu, только наш", Group.LOAD, Gioui.Size.WIDE, 12, BOLT, "page:bench", 86, fresh = true, words = listOf("тест", "баллы", "antutu")),
        Entry("turbo", "нагрузка", "все ядра сразу", Group.LOAD, Gioui.Size.MEDIUM, 13, BOLT, "flag:turbo", 74, words = listOf("cpu", "процессор", "жар")),
        Entry("gpu", "ускоритель", "кадры шейдера", Group.LOAD, Gioui.Size.SMALL, 14, TIME, "flag:turbo", 56, words = listOf("gpu", "шейдер", "fps")),
        Entry("chronicle", "хроника", "что было за день", Group.LOAD, Gioui.Size.SMALL, 15, TIME, "flag:chronicle", 48, words = listOf("история", "события")),
        Entry("sysinfo", "дашборд", "живое состояние", Group.LOAD, Gioui.Size.SMALL, 16, TUNE, "tool:sysinfo", 64, words = listOf("память", "процессор")),
        Entry("chronicle2", "статистика нагрузки", "график за час", Group.LOAD, Gioui.Size.SMALL, 17, BOLT, "flag:chronicle", 38, words = listOf("график", "нагрузка")),

        // ——— умное ———
        Entry("ai", "умный помощник", "настоящие сети и офлайн", Group.AI, Gioui.Size.MEDIUM, 3, SHIELD, "page:ai", 76, fresh = true, words = listOf("ии", "gpt", "нейросеть")),
        Entry("chat", "локальный ии", "чат без интернета", Group.AI, Gioui.Size.SMALL, 4, SHIELD, "flag:chat", 60, words = listOf("офлайн", "болталка")),
        Entry("daily", "каждый день", "новое при открытии", Group.AI, Gioui.Size.MEDIUM, 5, TIME, "page:everyday", 82, fresh = true, words = listOf("день", "серия", "подарок")),
        Entry("surprise", "рулетка", "случайная функция", Group.AI, Gioui.Size.SMALL, 6, VOLUME, "page:surprise", 66, fresh = true, words = listOf("рандом", "случайно")),
        Entry("easter", "пасхалки", "секреты внутри", Group.AI, Gioui.Size.SMALL, 7, COPY, "page:easter", 58, fresh = true, words = listOf("секрет", "секреты")),

        // ——— шифраторы (GIOUI/5.0) ———
        Entry("ciphers", "шифраторы", "все шифры в одном окне", Group.CRYPTO, Gioui.Size.WIDE, 2, SHIELD, "page:ciphers", 97, fresh = true, words = listOf("шифр", "зашифровать", "расшифровать", "код", "base64", "крипто")),
        Entry("cipherlock", "gs-замок", "AES и пароль", Group.CRYPTO, Gioui.Size.MEDIUM, 7, SHIELD, "page:cipherlock", 90, fresh = true, words = listOf("aes", "пароль", "секрет", "замок")),
        Entry("ciphergrape", "виноградный код", "текст виноградом и бананом", Group.CRYPTO, Gioui.Size.MEDIUM, 4, COPY, "page:ciphergrape", 84, fresh = true, words = listOf("виноград", "банан", "фрукты", "эмодзи")),
        Entry("cipherauto", "авто-расшифровка", "сама поймёт, чем закрыто", Group.CRYPTO, Gioui.Size.SMALL, 5, BOLT, "page:cipherauto", 88, fresh = true, words = listOf("авто", "определить", "узнать шифр", "расшифровать")),

        // ——— система ———
        Entry("rules", "автопилот", "правила по времени", Group.SYSTEM, Gioui.Size.MEDIUM, 8, TUNE, "panel:tools", 88, words = listOf("правила", "расписание")),
        Entry("permissions", "разрешения", "всё, что нужно приложению", Group.SYSTEM, Gioui.Size.SMALL, 9, SHIELD, "panel:settings", 70, words = listOf("права", "доступ")),
        Entry("update", "обновление", "приходит само", Group.SYSTEM, Gioui.Size.SMALL, 10, BOLT, "flag:update", 72, words = listOf("новая версия", "апдейт")),
        Entry("diag", "диагностика", "отчёты и журнал", Group.SYSTEM, Gioui.Size.SMALL, 11, TUNE, "flag:diagnostics", 50, words = listOf("сбои", "логи")),
        Entry("agent", "связь с агентом", "переписка через GitHub", Group.SYSTEM, Gioui.Size.WIDE, 0, COPY, "page:agent", 95, fresh = true, words = listOf("агент", "чат", "связь", "вопрос")),
        Entry("report", "собрать отчёт", "состояние телефона", Group.SYSTEM, Gioui.Size.SMALL, 12, COPY, "sys:report", 46, words = listOf("лог", "жалоба")),
        Entry("syssound", "звук", "системный экран", Group.SYSTEM, Gioui.Size.SMALL, 13, VOLUME, "sys:sound", 30),
        Entry("sysbattery", "батарея", "системный экран", Group.SYSTEM, Gioui.Size.SMALL, 14, BOLT, "sys:battery", 30),
        Entry("syswifi", "wi-fi", "системный экран", Group.SYSTEM, Gioui.Size.SMALL, 15, TUNE, "sys:wifi", 30),
        Entry("sysbt", "bluetooth", "системный экран", Group.SYSTEM, Gioui.Size.SMALL, 16, SHIELD, "sys:bluetooth", 28),
        Entry("sysapps", "приложения", "системный экран", Group.SYSTEM, Gioui.Size.SMALL, 17, COPY, "sys:apps", 24),

        // ——— забава ———
        Entry("games", "игры", "мини-игры и дуэли", Group.FUN, Gioui.Size.MEDIUM, 18, TIME, "panel:games", 68, words = listOf("играть")),
        Entry("rogue", "gtboi", "подземелье вдвоём", Group.FUN, Gioui.Size.SMALL, 19, SHIELD, "panel:rogue", 64, words = listOf("игра", "данжен")),
        Entry("music", "композитор", "музыка из частот", Group.FUN, Gioui.Size.SMALL, 0, VOLUME, "lab:music", 56, words = listOf("мелодия", "ноты")),
        Entry("tone", "камертон", "держу — играет", Group.FUN, Gioui.Size.SMALL, 1, VOLUME, "lab:tone", 40, words = listOf("частота", "нота")),
        Entry("torchlab", "фонарик-строб", "вспышка в такт", Group.FUN, Gioui.Size.SMALL, 2, BRIGHT, "lab:torch", 44, words = listOf("вспышка", "свет")),
        Entry("metal", "металл", "магнитометр", Group.FUN, Gioui.Size.SMALL, 3, SHIELD, "tool:metal", 42, words = listOf("искать", "металлоискатель")),
        Entry("level", "уровень", "наклон в градусах", Group.FUN, Gioui.Size.SMALL, 4, TUNE, "tool:level", 46, words = listOf("горизонт", "ровно")),
        Entry("noise", "шумомер", "уровень в дБ", Group.FUN, Gioui.Size.SMALL, 5, VOLUME, "tool:sound", 44, words = listOf("громко", "децибел")),
        Entry("morse", "морзянка", "мигание фонариком", Group.CRYPTO, Gioui.Size.SMALL, 6, BOLT, "tool:morse", 34, words = listOf("точка", "тире")),
        Entry("password", "пароль и qr", "генератор", Group.CRYPTO, Gioui.Size.SMALL, 7, COPY, "tool:password", 36, words = listOf("код", "qr")),
        Entry("coder", "кодер", "base64, url, hex", Group.CRYPTO, Gioui.Size.SMALL, 8, COPY, "tool:coder", 32, words = listOf("шифр", "кодирование"))
    )

    fun byId(id: String): Entry? = ALL.firstOrNull { it.id == id }

    /**
     * Отбор и порядок. Сортировка не прячет функции: она только меняет, что вверху.
     */
    fun view(
        query: String = "",
        group: Group? = null,
        sort: Sort = Sort.GROUP,
        favorites: Set<String> = emptySet(),
        recent: List<String> = emptyList()
    ): List<Entry> {
        var list = ALL.filter { it.matches(query) }
        if (group != null) list = list.filter { it.group == group }
        if (sort == Sort.FAVORITE) list = list.filter { it.id in favorites }
        return when (sort) {
            Sort.FREQUENT -> list.sortedByDescending { it.hot }
            Sort.ALPHABET -> list.sortedBy { it.title }
            Sort.RECENT -> list.sortedBy { entry ->
                val index = recent.indexOf(entry.id)
                if (index < 0) 999 else index
            }
            Sort.GROUP -> list.sortedWith(compareBy({ it.group.ordinal }, { -it.hot }))
            Sort.FAVORITE -> list.sortedByDescending { it.hot }
            Sort.NEW -> list.sortedWith(compareBy({ !it.fresh }, { it.group.ordinal }, { -it.hot }))
        }
    }

    /** Полка избранного и недавних: тоже не прячем, а показываем сверху. */
    fun favoritesOrdered(favorites: Set<String>): List<Entry> =
        ALL.filter { it.id in favorites }.sortedByDescending { it.hot }

    fun recentOrdered(recent: List<String>): List<Entry> =
        recent.mapNotNull { byId(it) }.distinctBy { it.id }

    // ————— предпочтения каталога —————
    private const val PREFS = "gsynk_catalog"

    fun loadFavorites(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("favorites", emptySet()) ?: emptySet()

    fun saveFavorites(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet("favorites", ids).apply()
    }

    fun loadRecent(context: Context): List<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("recent", "") ?: "")
            .split('|').filter { it.isNotBlank() }

    fun pushRecent(context: Context, id: String) {
        val list = (listOf(id) + loadRecent(context)).distinct().take(12)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("recent", list.joinToString("|")).apply()
    }

    fun loadSort(context: Context): Sort {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("sort", 3)
        return Sort.entries.getOrElse(raw) { Sort.GROUP }
    }

    fun saveSort(context: Context, sort: Sort) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("sort", sort.ordinal).apply()
    }

    fun loadGroup(context: Context): Group? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("group", -1)
        return Group.entries.getOrNull(raw)
    }

    fun saveGroup(context: Context, group: Group?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("group", group?.ordinal ?: -1).apply()
    }

    /** Открыть плитку и запомнить, что человек ей пользуется. */
    fun open(context: Context, entry: Entry) {
        pushRecent(context, entry.id)
        Router.go(entry.route)
    }
}
