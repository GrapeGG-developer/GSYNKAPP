package com.gsynk.app.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * «Шифраторы» GSYNK (GIOUI / 5.0): один универсальный набор обратимых преобразований текста.
 *
 * Три семьи:
 *  * популярные (Base64, HEX, URL, морзе, Цезарь, XOR по ключу);
 *  * символьные (двоичный, 6/7, 4/2, G/S, только G, натуральные 1–9, эмодзи, виноград/банан);
 *  * уникальные GSYNK (GSYNK-32, GS-85, «Виноградная лоза», тяжёлый «GS-Замок» AES-GCM с PBKDF2).
 *
 * Всё — чистая Kotlin/Java без разрешений и без современных API выше Android 11:
 * одинаково работает и на Poco X3 Pro, и на флагманах. Честно: кодировки (Base64, двоичный,
 * винограды) не прячут смысл от того, кто знает схему, настоящую секретность даёт «GS-Замок».
 */
object Ciphers {

    enum class Family(val title: String) {
        POPULAR("популярные"),
        SYMBOLS("символы"),
        GSYNK("уникальные GSYNK")
    }

    data class Info(
        val id: String,
        val title: String,
        val family: Family,
        val about: String,
        val needsKey: Boolean = false,
        val heavy: Boolean = false
    )

    val ALL: List<Info> = listOf(
        Info("base64", "Base64", Family.POPULAR, "стандарт передачи: 3 байта → 4 символа"),
        Info("hex", "HEX", Family.POPULAR, "байты шестнадцатеричными цифрами"),
        Info("url", "URL-кодировка", Family.POPULAR, "безопасные символы и %HEX для остальных"),
        Info("morse", "Азбука Морзе", Family.POPULAR, "точки и тире: латиница и цифры"),
        Info("caesar", "Цезарь +7", Family.POPULAR, "сдвиг букв русского и английского алфавитов"),
        Info("xor", "XOR с ключом", Family.POPULAR, "простое перемешивание байтов по парольной фразе", needsKey = true),

        Info("bin", "Двоичный код", Family.SYMBOLS, "каждый байт восемью битами 0 и 1"),
        Info("d67", "Цифры 6 и 7", Family.SYMBOLS, "двоичный код цифрами: 6 — ноль, 7 — единица"),
        Info("d42", "Цифры 4 и 2", Family.SYMBOLS, "двоичный код цифрами: 4 — ноль, 2 — единица"),
        Info("gs", "Буквы G и S", Family.SYMBOLS, "фирменный двоичный: G — ноль, S — единица"),
        Info("gonly", "Только буква G", Family.SYMBOLS, "одна буква на всё: G — ноль, GG — единица (тяжёлый и длинный)", heavy = true),
        Info("nat9", "Натуральные 1–9", Family.SYMBOLS, "без нуля: каждый байт тремя цифрами от 1 до 9"),
        Info("emoji", "Эмодзи 😡 и 😏", Family.SYMBOLS, "двоичный код лицами: первое — ноль, второе — единица"),
        Info("fruit", "Виноград и банан", Family.SYMBOLS, "фирменный фруктовый: банан — ноль, виноград — единица"),

        Info("gs32", "GSYNK-32", Family.GSYNK, "свой компактный алфавит из 32 знаков, начало GSYNK"),
        Info("g85", "GS-85 компактный", Family.GSYNK, "плотная упаковка: 4 байта в 5 печатных символов"),
        Info("vine", "Виноградная лоза", Family.GSYNK, "красивый: каждые полбайта — виноградное слово"),
        Info("lock", "GS-Замок (AES)", Family.GSYNK, "строгий и тяжёлый: AES-GCM, ключ из пароля через PBKDF2 (120 000 кругов)", needsKey = true, heavy = true)
    )

    fun byId(id: String): Info = ALL.firstOrNull { it.id == id } ?: ALL[0]

    sealed class Outcome {
        data class Ok(val text: String, val cipherId: String) : Outcome()
        data class Err(val message: String) : Outcome()
    }

    const val MAX_CHARS = 50_000

    // ——————————————————— главный вход ———————————————————

    fun encode(id: String, plain: String, key: String = ""): Outcome {
        if (plain.length > MAX_CHARS) return Outcome.Err("Слишком длинный текст: предел " + MAX_CHARS + " символов, чтобы телефон не завис на символьных шифрах.")
        if (plain.isEmpty()) return Outcome.Ok("", id)
        val bytes = plain.toByteArray(Charsets.UTF_8)
        return try {
            val out: String = when (id) {
                "base64" -> Base64.getEncoder().encodeToString(bytes)
                "hex" -> bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                "url" -> urlEncode(plain)
                "morse" -> morseEncode(plain)
                "caesar" -> caesarShift(plain, 7)
                "xor" -> "XOR1." + Base64.getUrlEncoder().withoutPadding().encodeToString(xorBytes(bytes, key))
                "bin" -> bitEncode(bytes, '0', '1')
                "d67" -> bitEncode(bytes, '6', '7')
                "d42" -> bitEncode(bytes, '4', '2')
                "gs" -> bitEncode(bytes, 'G', 'S')
                "gonly" -> gOnlyEncode(bytes)
                "nat9" -> nat9Encode(bytes)
                "emoji" -> emojiEncode(bytes, EMOJI_ZERO, EMOJI_ONE)
                "fruit" -> emojiEncode(bytes, BANANA, GRAPE)
                "gs32" -> "GS32:" + baseNEncode(withLength(bytes), GS32)
                "g85" -> "G85:" + g85Encode(withLength(bytes))
                "vine" -> vineEncode(bytes)
                "lock" -> lockEncode(bytes, key)
                else -> return Outcome.Err("Неизвестный шифр: " + id)
            }
            Outcome.Ok(out, id)
        } catch (e: CipherProblem) {
            Outcome.Err(e.message ?: "Не получилось зашифровать.")
        } catch (e: Exception) {
            Outcome.Err("Ошибка шифрования: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    fun decode(id: String, coded: String, key: String = ""): Outcome {
        val input = coded.trim()
        if (input.isEmpty()) return Outcome.Ok("", id)
        return try {
            val out: String = when (id) {
                "base64" -> strictUtf8(Base64.getMimeDecoder().decode(input))
                "hex" -> strictUtf8(hexDecode(input))
                "url" -> urlDecode(input)
                "morse" -> morseDecode(input)
                "caesar" -> caesarShift(input, -7)
                "xor" -> strictUtf8(xorBytes(Base64.getUrlDecoder().decode(removePrefixOnce(input, "XOR1.")), key))
                "bin" -> strictUtf8(bitDecode(input, setOf('0'), setOf('1')))
                "d67" -> strictUtf8(bitDecode(input, setOf('6'), setOf('7')))
                "d42" -> strictUtf8(bitDecode(input, setOf('4'), setOf('2')))
                "gs" -> strictUtf8(bitDecode(input, setOf('G', 'g'), setOf('S', 's')))
                "gonly" -> strictUtf8(gOnlyDecode(input))
                "nat9" -> strictUtf8(nat9Decode(input))
                "emoji" -> strictUtf8(emojiDecode(input, EMOJI_ZERO, EMOJI_ONE))
                "fruit" -> strictUtf8(emojiDecode(input, BANANA, GRAPE))
                "gs32" -> strictUtf8(withoutLength(baseNDecode(removePrefixOnce(input, "GS32:"), GS32)))
                "g85" -> strictUtf8(withoutLength(g85Decode(removePrefixOnce(input, "G85:"))))
                "vine" -> strictUtf8(vineDecode(input))
                "lock" -> strictUtf8(lockDecode(input, key))
                else -> return Outcome.Err("Неизвестный шифр: " + id)
            }
            Outcome.Ok(out, id)
        } catch (e: CipherProblem) {
            Outcome.Err(e.message ?: "Не получилось расшифровать этим шифром.")
        } catch (e: Exception) {
            Outcome.Err("Похоже, это не шифр «" + byId(id).title + "»: " + (e.message ?: "данные повреждены"))
        }
    }

    /**
     * Автоопределение: пользователь вставил шифр — система сама понимает, чем это закодировано.
     * Порядок от самого однозначного к менее очевидному; каждая догадка проверяется настоящим
     * декодированием в читаемый UTF-8. Возвращает шифр и человеческую причину.
     */
    fun detect(coded: String): Pair<Info, String>? {
        val input = coded.trim()
        if (input.length < 2) return null
        fun ok(id: String, why: String): Pair<Info, String>? {
            val r = decode(id, input)
            return if (r is Outcome.Ok && r.text.isNotEmpty() && r.text.all { !it.isISOControl() || it == '\n' || it == '\t' }) {
                byId(id) to why
            } else null
        }
        // префиксы уникальных — однозначно
        if (input.startsWith("GSLOCK1.")) return byId("lock") to "начало GSLOCK1. — «GS-Замок», нужен пароль"
        if (input.startsWith("GS32:")) return ok("gs32", "начало GS32: — родной алфавит GSYNK") ?: byId("gs32") to "начало GS32:"
        if (input.startsWith("G85:")) return ok("g85", "начало G85: — компактная упаковка") ?: byId("g85") to "начало G85:"
        if (input.startsWith("XOR1.")) return byId("xor") to "начало XOR1. — нужен ключ"
        // слова лозы
        val words = input.split('-', ' ', '\n').filter { it.isNotBlank() }
        if (words.size >= 4 && words.all { it.lowercase() in VINE }) return ok("vine", "все слова — из виноградного набора «Лозы»")
        // эмодзи и фрукты
        if (input.contains(EMOJI_ZERO) || input.contains(EMOJI_ONE)) return ok("emoji", "состоит из лиц 😡 и 😏")
        if (input.contains(GRAPE) || input.contains(BANANA)) return ok("fruit", "состоит из винограда и бананов")
        val compact = input.filterNot { it.isWhitespace() }
        // только G/S и только G
        if (compact.length >= 8 && compact.all { it == 'G' || it == 'S' || it == 'g' || it == 's' } && compact.any { it == 'S' || it == 's' }) return ok("gs", "только буквы G и S")
        if (compact.length >= 8 && input.all { it == 'G' || it.isWhitespace() }) return ok("gonly", "только буква G и пробелы")
        // цифровые пары раньше натуральных: «6 и 7» ведь тоже цифры 1–9
        if (compact.length >= 16 && compact.all { it == '6' || it == '7' }) return ok("d67", "только цифры 6 и 7")
        if (compact.length >= 16 && compact.all { it == '4' || it == '2' }) return ok("d42", "только цифры 4 и 2")
        if (compact.length >= 16 && compact.all { it == '0' || it == '1' } && compact.length % 8 == 0) return ok("bin", "только 0 и 1, длина кратна восьми")
        // морзе
        if (input.length >= 5 && input.all { it == '.' || it == '-' || it == '/' || it.isWhitespace() } && input.contains('.') && input.contains('-')) return ok("morse", "точки, тире и пробелы — азбука Морзе")
        // URL
        if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(input)) return ok("url", "видно процентные коды %XX")
        // натуральные 1–9: тройки цифр, нуля нет
        if (compact.length >= 6 && compact.length % 3 == 0 && compact.all { it in '1'..'9' }) ok("nat9", "цифры 1–9 без нуля тройками")?.let { return it }
        // hex
        if (compact.length >= 4 && compact.length % 2 == 0 && compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) ok("hex", "чётная длина и только шестнадцатеричные цифры")?.let { return it }
        // base64 — в самом конце, он слишком похож на обычный текст
        if (compact.length >= 8 && compact.length % 4 == 0 && compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            ok("base64", "похоже на Base64 и честно распаковывается в текст")?.let { return it }
        }
        return null
    }

    // ——————————————————— помощники ———————————————————

    private class CipherProblem(message: String) : Exception(message)

    private fun removePrefixOnce(s: String, prefix: String): String =
        if (s.startsWith(prefix)) s.substring(prefix.length) else s

    private fun strictUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            throw CipherProblem("внутри не читаемый текст UTF-8 — вероятно, выбран не тот шифр или данные с ошибкой")
        }
    }

    private fun bitsOf(bytes: ByteArray): List<Int> {
        val out = ArrayList<Int>(bytes.size * 8)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            for (shift in 7 downTo 0) out += (v shr shift) and 1
        }
        return out
    }

    private fun bytesOf(bits: List<Int>): ByteArray {
        if (bits.size % 8 != 0) throw CipherProblem("количество символов (" + bits.size + ") не делится на 8 — кусок шифра потерян")
        val out = ByteArray(bits.size / 8)
        for (i in out.indices) {
            var v = 0
            for (k in 0..7) v = (v shl 1) or bits[i * 8 + k]
            out[i] = v.toByte()
        }
        return out
    }

    private fun bitEncode(bytes: ByteArray, zero: Char, one: Char): String {
        val sb = StringBuilder()
        bitsOf(bytes).chunked(8).forEachIndexed { index, chunk ->
            if (index > 0) sb.append(' ')
            chunk.forEach { sb.append(if (it == 1) one else zero) }
        }
        return sb.toString()
    }

    private fun bitDecode(input: String, zeros: Set<Char>, ones: Set<Char>): ByteArray {
        val bits = ArrayList<Int>()
        for (c in input) {
            when {
                c.isWhitespace() -> Unit
                c in zeros -> bits += 0
                c in ones -> bits += 1
                else -> throw CipherProblem("встретился лишний символ «" + c + "»")
            }
        }
        if (bits.isEmpty()) throw CipherProblem("пустой шифр")
        return bytesOf(bits)
    }

    // Только G: бит 0 — один G, бит 1 — два GG, биты разделены пробелами.
    private fun gOnlyEncode(bytes: ByteArray): String =
        bitsOf(bytes).joinToString(" ") { if (it == 1) "GG" else "G" }

    private fun gOnlyDecode(input: String): ByteArray {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) throw CipherProblem("пустой шифр")
        val bits = tokens.map { token ->
            if (token.any { it != 'G' } || token.length > 2) throw CipherProblem("в шифре «только G» живут лишь куски G и GG, а тут «" + token.take(6) + "»")
            token.length - 1
        }
        return bytesOf(bits)
    }

    private fun nat9Encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            var v = b.toInt() and 0xFF
            val d3 = v % 9; v /= 9
            val d2 = v % 9; v /= 9
            val d1 = v % 9
            sb.append('1' + d1).append('1' + d2).append('1' + d3)
        }
        // читаемость: пробел каждые 12 цифр (4 байта)
        return sb.toString().chunked(12).joinToString(" ")
    }

    private fun nat9Decode(input: String): ByteArray {
        val digits = input.filterNot { it.isWhitespace() }
        if (digits.isEmpty() || digits.length % 3 != 0) throw CipherProblem("цифр должно быть по три на байт (сейчас " + digits.length + ")")
        if (digits.any { it !in '1'..'9' }) throw CipherProblem("в натуральном шифре нет нуля и букв")
        val out = ByteArray(digits.length / 3)
        for (i in out.indices) {
            val v = (digits[i * 3] - '1') * 81 + (digits[i * 3 + 1] - '1') * 9 + (digits[i * 3 + 2] - '1')
            if (v > 255) throw CipherProblem("тройка № " + (i + 1) + " больше байта")
            out[i] = v.toByte()
        }
        return out
    }

    private const val EMOJI_ZERO = "\uD83D\uDE21" // 😡
    private const val EMOJI_ONE = "\uD83D\uDE0F"  // 😏
    private const val BANANA = "\uD83C\uDF4C"     // 🍌
    private const val GRAPE = "\uD83C\uDF47"      // 🍇

    private fun emojiEncode(bytes: ByteArray, zero: String, one: String): String {
        val sb = StringBuilder()
        bitsOf(bytes).chunked(32).forEachIndexed { index, chunk ->
            if (index > 0) sb.append(' ')
            chunk.forEach { sb.append(if (it == 1) one else zero) }
        }
        return sb.toString()
    }

    private fun emojiDecode(input: String, zero: String, one: String): ByteArray {
        val bits = ArrayList<Int>()
        var i = 0
        while (i < input.length) {
            when {
                input[i].isWhitespace() -> i++
                input.startsWith(zero, i) -> { bits += 0; i += zero.length }
                input.startsWith(one, i) -> { bits += 1; i += one.length }
                else -> throw CipherProblem("встретился чужой символ на позиции " + (i + 1))
            }
        }
        if (bits.isEmpty()) throw CipherProblem("пустой шифр")
        return bytesOf(bits)
    }

    private fun hexDecode(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.length % 2 != 0) throw CipherProblem("у HEX чётная длина — символ потерялся")
        return ByteArray(clean.length / 2) { index ->
            val hi = Character.digit(clean[index * 2], 16)
            val lo = Character.digit(clean[index * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw CipherProblem("лишняя буква в HEX на месте " + (index * 2 + 1))
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun urlEncode(plain: String): String {
        val sb = StringBuilder()
        for (b in plain.toByteArray(Charsets.UTF_8)) {
            val v = b.toInt() and 0xFF
            // только настоящий ASCII-набор: байт кириллицы как Char выглядит «буквой» и всё ломает
            val safe = v in 0x30..0x39 || v in 0x41..0x5A || v in 0x61..0x7A ||
                v == 0x2D || v == 0x5F || v == 0x2E || v == 0x7E
            if (safe) sb.append(v.toChar())
            else sb.append('%').append("%02X".format(v))
        }
        return sb.toString()
    }

    private fun urlDecode(input: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < input.length) {
            when (val c = input[i]) {
                '%' -> {
                    if (i + 2 >= input.length) throw CipherProblem("после % нужны две цифры")
                    val hi = Character.digit(input[i + 1], 16)
                    val lo = Character.digit(input[i + 2], 16)
                    if (hi < 0 || lo < 0) throw CipherProblem("битый код % после позиции " + (i + 1))
                    bytes += ((hi shl 4) or lo).toByte()
                    i += 3
                }
                '+' -> { bytes += ' '.code.toByte(); i++ }
                else -> { c.toString().toByteArray(Charsets.UTF_8).forEach { bytes += it }; i++ }
            }
        }
        return strictUtf8(bytes.toByteArray())
    }

    private val MORSE_ENCODE: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '!' to "-.-.--"
    )
    private val MORSE_DECODE: Map<String, Char> = MORSE_ENCODE.entries.associate { it.value to it.key }

    private fun morseEncode(plain: String): String {
        val words = plain.uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" / ") { word ->
            word.map { c -> MORSE_ENCODE[c] ?: throw CipherProblem("морзе умеет только латиницу и цифры; символ «" + c + "» не передать — выбери любой двоичный шифр, он унесёт и русский, и эмодзи") }
                .joinToString(" ")
        }
    }

    private fun morseDecode(input: String): String {
        return input.trim().split('/').joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { code ->
                (MORSE_DECODE[code] ?: throw CipherProblem("неизвестный знак морзе: " + code)).toString()
            }
        }.trim()
    }

    private const val EN_LOW = "abcdefghijklmnopqrstuvwxyz"
    private const val EN_UP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val RU_LOW = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    private const val RU_UP = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"

    private fun caesarShift(text: String, shift: Int): String {
        fun move(c: Char, alphabet: String): Char {
            val index = alphabet.indexOf(c)
            if (index < 0) return c
            val n = alphabet.length
            return alphabet[((index + shift) % n + n) % n]
        }
        return buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        in EN_LOW -> move(c, EN_LOW)
                        in EN_UP -> move(c, EN_UP)
                        in RU_LOW -> move(c, RU_LOW)
                        in RU_UP -> move(c, RU_UP)
                        else -> c
                    }
                )
            }
        }
    }

    private fun xorBytes(data: ByteArray, key: String): ByteArray {
        if (key.isBlank()) throw CipherProblem("для этого шифра нужен ключ (любая фраза); тот же ключ попросит расшифровка")
        val kb = key.toByteArray(Charsets.UTF_8)
        return ByteArray(data.size) { i -> (data[i].toInt() xor (kb[i % kb.size].toInt() and 0xFF)).toByte() }
    }

    // 4 байта длины + данные: хвостовая добивка битами не теряет последний кусок.
    private fun withLength(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size + 4)
        out[0] = (bytes.size ushr 24).toByte()
        out[1] = (bytes.size ushr 16).toByte()
        out[2] = (bytes.size ushr 8).toByte()
        out[3] = bytes.size.toByte()
        bytes.copyInto(out, 4)
        return out
    }

    private fun withoutLength(bytes: ByteArray): ByteArray {
        if (bytes.size < 4) throw CipherProblem("шифр слишком короткий: нет даже заголовка длины")
        val len = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        if (len < 0 || len > bytes.size - 4) throw CipherProblem("заголовок длины повреждён — кусок шифра потерян")
        return bytes.copyOfRange(4, 4 + len)
    }

    private const val GS32 = "GSYNK23456789ABCDEFHJLMPQRTUVWXZ"

    private fun baseNEncode(data: ByteArray, alphabet: String): String {
        val radix = alphabet.length
        var bitsPerChar = 0
        var probe = 1
        while (probe < radix) { probe = probe shl 1; bitsPerChar++ }
        val sb = StringBuilder()
        var buffer = 0
        var liveBits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            liveBits += 8
            while (liveBits >= bitsPerChar) {
                liveBits -= bitsPerChar
                sb.append(alphabet[(buffer shr liveBits) and (radix - 1)])
            }
        }
        if (liveBits > 0) sb.append(alphabet[(buffer shl (bitsPerChar - liveBits)) and (radix - 1)])
        return sb.toString()
    }

    private fun baseNDecode(input: String, alphabet: String): ByteArray {
        val radix = alphabet.length
        var bitsPerChar = 0
        var probe = 1
        while (probe < radix) { probe = probe shl 1; bitsPerChar++ }
        val out = ArrayList<Byte>()
        var buffer = 0
        var liveBits = 0
        for (c in input.trim().uppercase()) {
            if (c.isWhitespace()) continue
            val value = alphabet.indexOf(c)
            if (value < 0) throw CipherProblem("символ «" + c + "» не из алфавита GSYNK-32")
            buffer = (buffer shl bitsPerChar) or value
            liveBits += bitsPerChar
            while (liveBits >= 8) {
                liveBits -= 8
                out += ((buffer shr liveBits) and 0xFF).toByte()
            }
        }
        return out.toByteArray()
    }

    // GS-85: каждый блок из 4 байт как число -> 5 символов по основанию 85 ('!'..'u').
    private fun g85Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            var value = 0L
            for (k in 0..3) {
                value = value * 256 + (if (i + k < data.size) data[i + k].toInt() and 0xFF else 0)
            }
            val chars = CharArray(5)
            for (k in 4 downTo 0) {
                chars[k] = ('!'.code + (value % 85).toInt()).toChar()
                value /= 85
            }
            sb.append(chars)
            i += 4
        }
        return sb.toString()
    }

    private fun g85Decode(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.length % 5 != 0) throw CipherProblem("в GS-85 символов по пять на блок; кусок потерян")
        val out = ArrayList<Byte>(clean.length / 5 * 4)
        var i = 0
        while (i < clean.length) {
            var value = 0L
            for (k in 0..4) {
                val digit = clean[i + k].code - '!'.code
                if (digit !in 0..84) throw CipherProblem("символ «" + clean[i + k] + "» не из диапазона GS-85")
                value = value * 85 + digit
            }
            for (k in 3 downTo 0) out += ((value shr (8 * k)) and 0xFF).toByte()
            i += 5
        }
        return out.toByteArray()
    }

    private val VINE = listOf(
        "лоза", "гроздь", "ягода", "сок", "лист", "вино", "сад", "кисть",
        "мускат", "рислинг", "шардоне", "мерло", "каберне", "изабелла", "дамский", "тайфи"
    )

    private fun vineEncode(bytes: ByteArray): String {
        val words = ArrayList<String>(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            words += VINE[v shr 4]
            words += VINE[v and 0x0F]
        }
        return words.joinToString("-")
    }

    private fun vineDecode(input: String): ByteArray {
        val words = input.lowercase().split('-', ' ', '\n').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size % 2 != 0) throw CipherProblem("в «Лозе» слов чётное число — по два на байт")
        val out = ByteArray(words.size / 2)
        for (i in out.indices) {
            val hi = VINE.indexOf(words[i * 2])
            val lo = VINE.indexOf(words[i * 2 + 1])
            if (hi < 0) throw CipherProblem("слово «" + words[i * 2] + "» не из виноградного набора")
            if (lo < 0) throw CipherProblem("слово «" + words[i * 2 + 1] + "» не из виноградного набора")
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    // ————— GS-Замок: пароль -> PBKDF2(120k, SHA-256) -> AES-256-GCM. Тяжёлый нарочно. —————
    private const val LOCK_ITERATIONS = 120_000

    private fun lockKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, LOCK_ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    private fun lockEncode(data: ByteArray, password: String): String {
        if (password.isBlank()) throw CipherProblem("«GS-Замок» без пароля не закрывается — придумай пароль и запомни: без него текст не вернуть")
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, lockKey(password, salt), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(data)
        return "GSLOCK1." + Base64.getUrlEncoder().withoutPadding().encodeToString(salt + iv + sealed)
    }

    private fun lockDecode(input: String, password: String): ByteArray {
        if (password.isBlank()) throw CipherProblem("это «GS-Замок» — введи тот же пароль, которым закрывали")
        val raw = try {
            Base64.getUrlDecoder().decode(removePrefixOnce(input, "GSLOCK1."))
        } catch (e: Exception) {
            throw CipherProblem("тело замка повреждено (не Base64)")
        }
        if (raw.size < 16 + 12 + 16) throw CipherProblem("замок слишком короткий — кусок потерян")
        val salt = raw.copyOfRange(0, 16)
        val iv = raw.copyOfRange(16, 28)
        val sealed = raw.copyOfRange(28, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, lockKey(password, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(sealed)
        } catch (e: Exception) {
            throw CipherProblem("замок не открылся: пароль не тот или текст изменили хоть на символ")
        }
    }
}
