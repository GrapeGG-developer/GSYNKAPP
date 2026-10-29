package com.gsynk.app.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.math.BigInteger
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * «Шифраторы» GSYNK (GIOUI / 5.2): один универсальный набор обратимых преобразований текста.
 *
 * Семьи:
 *  * популярные (Base64, HEX, URL, морзе, Цезарь, XOR по ключу);
 *  * символьные (двоичный, 6/7, 4/2, G/S, только G, натуральные 1–9, лица, поросята, виноград/банан);
 *  * машинные представления (байт-код, ассемблер и процессорный дамп);
 *  * уникальные GSYNK и анонимность (GSYNK-32, GS-85, «Лоза», «GS-Замок», GS-TLS и GANONYM).
 *
 * Всё — чистая Kotlin/Java без разрешений и без современных API выше Android 11:
 * одинаково работает и на Poco X3 Pro, и на флагманах. Честно: кодировки (Base64, двоичный,
 * винограды) не прячут смысл от того, кто знает схему, настоящую секретность даёт «GS-Замок».
 */
object Ciphers {

    enum class Family(val title: String) {
        POPULAR("популярные"),
        SYMBOLS("символы"),
        MACHINE("машинный уровень"),
        GSYNK("уникальные GSYNK"),
        ANON("анонимность")
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
        Info("base32", "Base32", Family.POPULAR, "стандартный алфавит A–Z и 2–7: длиннее Base64, зато без похожих символов"),
        Info("base58", "Base58", Family.POPULAR, "биткоин-алфавит без 0, O, I и l; компактно и удобно переписывать"),
        Info("ascii85", "Ascii85", Family.POPULAR, "плотная печатная упаковка: каждые 4 байта — 5 символов"),
        Info("quoted", "Quoted-Printable", Family.POPULAR, "почтовый вид =XX для байтов, пробел — подчёркиванием"),
        Info("rot13", "ROT13", Family.POPULAR, "классический сдвиг английского алфавита на 13"),
        Info("atbash", "Атбаш", Family.POPULAR, "зеркальный алфавит для английского и русского"),
        Info("vigenere", "Виженер", Family.POPULAR, "буквенный шифр по ключевой фразе для русского и английского", needsKey = true),

        Info("bin", "Двоичный код", Family.SYMBOLS, "каждый байт восемью битами 0 и 1"),
        Info("d67", "Цифры 6 и 7", Family.SYMBOLS, "двоичный код цифрами: главная 6 — единица, 7 — ноль"),
        Info("d42", "Цифры 4 и 2", Family.SYMBOLS, "двоичный код цифрами: главная 4 — единица, 2 — ноль"),
        Info("gs", "Буквы G и S", Family.SYMBOLS, "фирменный двоичный: G — ноль, S — единица"),
        Info("gonly", "Только буква G", Family.SYMBOLS, "одна буква на всё: G — ноль, GG — единица (тяжёлый и длинный)", heavy = true),
        Info("nat9", "Натуральные 1–9", Family.SYMBOLS, "без нуля: каждый байт тремя цифрами от 1 до 9"),
        Info("emoji", "Эмодзи 😡 и 😏", Family.SYMBOLS, "двоичный код лицами: первое — ноль, второе — единица"),
        Info("fruit", "Виноград и банан", Family.SYMBOLS, "фирменный фруктовый: банан — ноль, виноград — единица"),
        Info("pig", "Поросята 🐷", Family.SYMBOLS, "двоичный код поросятами: 🐽 — ноль, 🐷 — единица"),
        Info("cats", "Коты 🐱", Family.SYMBOLS, "длинный двоичный код котами: 🐱 — ноль, 😼 — единица", heavy = true),
        Info("moon", "Луны 🌙", Family.SYMBOLS, "компактнее бинарного: каждые полбайта — одна из 16 лун"),
        Info("chess", "Шахматные круги", Family.SYMBOLS, "двоичный код кругами: ⚪ — ноль, ⚫ — единица", heavy = true),
        Info("braille", "Брайль", Family.SYMBOLS, "один байт — один знак Брайля: очень компактная символьная запись"),
        Info("runes", "Руны", Family.SYMBOLS, "каждые полбайта — руна из набора из 16 знаков"),
        Info("dna", "ДНК A/C/G/T", Family.SYMBOLS, "каждый байт — четыре основания ДНК по два бита"),
        Info("decimal", "Десятичные байты", Family.SYMBOLS, "каждый байт обычным числом 0–255 через дефис"),
        Info("octal", "Восьмеричный", Family.SYMBOLS, "каждый байт тремя восьмеричными цифрами"),
        Info("ternary", "Троичный", Family.SYMBOLS, "длинный числовой вид: каждый байт шестью цифрами 0, 1 и 2", heavy = true),
        Info("quaternary", "Четверичный", Family.SYMBOLS, "каждый байт четырьмя цифрами 0–3"),
        Info("roman", "Римские числа", Family.SYMBOLS, "каждый байт римским числом, ноль — N", heavy = true),

        Info("bytecode", "Байт-код", Family.MACHINE, "текст как поток байтов 0x00…0xFF с заголовком GSYNK BYTECODE"),
        Info("asm", "Ассемблер", Family.MACHINE, "каждый байт — команда MOV и вызов emit_byte в x86-стиле", heavy = true),
        Info("cpu", "Процессорный вид", Family.MACHINE, "дамп памяти MEM с адресами, регистрами IP/SP и флагами"),

        Info("gs32", "GSYNK-32", Family.GSYNK, "свой компактный алфавит из 32 знаков, начало GSYNK"),
        Info("g85", "GS-85 компактный", Family.GSYNK, "плотная упаковка: 4 байта в 5 печатных символов"),
        Info("vine", "Виноградная лоза", Family.GSYNK, "красивый: каждые полбайта — виноградное слово"),
        Info("lock", "GS-Замок (AES)", Family.GSYNK, "строгий и тяжёлый: AES-GCM, ключ из пароля через PBKDF2 (120 000 кругов)", needsKey = true, heavy = true),
        Info("gstls", "GS-TLS (уровень HTTPS)", Family.GSYNK, "эфемерный ECDH P-256 + HKDF-SHA256 + AES-256-GCM; шифруется открытым ключом получателя, открывается его закрытым", needsKey = true, heavy = true),

        Info("ganonym", "GANONYM", Family.ANON, "компактный анонимный пакет: сжатие, случайная добивка до ступеней размера, PBKDF2 и AES-256-GCM; без автора, адресата, времени и устройства", needsKey = true, heavy = true)
    )

    fun byId(id: String): Info = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** Пара ключей GS-TLS. Открытый можно передавать всем, закрытый нельзя показывать никому. */
    data class TlsKeyPair(val publicKey: String, val privateKey: String)

    const val TLS_PUBLIC_PREFIX = "GSTLS-PUB."
    const val TLS_PRIVATE_PREFIX = "GSTLS-PRIV."

    /** Создаёт новую пару P-256 для GS-TLS прямо на телефоне. */
    fun generateTlsKeyPair(): TlsKeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val pair = generator.generateKeyPair()
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return TlsKeyPair(
            publicKey = TLS_PUBLIC_PREFIX + encoder.encodeToString(pair.public.encoded),
            privateKey = TLS_PRIVATE_PREFIX + encoder.encodeToString(pair.private.encoded)
        )
    }

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
                "d67" -> bitEncode(bytes, '7', '6')
                "d42" -> bitEncode(bytes, '2', '4')
                "gs" -> bitEncode(bytes, 'G', 'S')
                "gonly" -> gOnlyEncode(bytes)
                "nat9" -> nat9Encode(bytes)
                "emoji" -> emojiEncode(bytes, EMOJI_ZERO, EMOJI_ONE)
                "fruit" -> emojiEncode(bytes, BANANA, GRAPE)
                "pig" -> emojiEncode(bytes, PIG_NOSE, PIG_FACE)
                "bytecode" -> bytecodeEncode(bytes)
                "asm" -> asmEncode(bytes)
                "cpu" -> cpuEncode(bytes)
                "base32" -> base32Encode(bytes)
                "base58" -> "B58:" + base58Encode(bytes)
                "ascii85" -> "A85:" + ascii85Encode(bytes)
                "quoted" -> "QP:" + quotedPrintableEncode(bytes)
                "rot13" -> rot13(plain)
                "atbash" -> atbash(plain)
                "vigenere" -> vigenere(plain, key, decrypt = false)
                "cats" -> emojiEncode(bytes, CAT_ZERO, CAT_ONE)
                "moon" -> nibbleTokenEncode(bytes, MOON_TOKENS)
                "chess" -> emojiEncode(bytes, CHESS_ZERO, CHESS_ONE)
                "braille" -> brailleEncode(bytes)
                "runes" -> runesEncode(bytes)
                "dna" -> "DNA:" + dnaEncode(bytes)
                "decimal" -> "DEC:" + bytes.joinToString("-") { (it.toInt() and 0xFF).toString() }
                "octal" -> "OCT:" + fixedRadixEncode(bytes, 8, 3)
                "ternary" -> "B3:" + fixedRadixEncode(bytes, 3, 6)
                "quaternary" -> "B4:" + fixedRadixEncode(bytes, 4, 4)
                "roman" -> "ROMAN:" + bytes.joinToString("-") { romanEncode(it.toInt() and 0xFF) }
                "gs32" -> "GS32:" + baseNEncode(withLength(bytes), GS32)
                "g85" -> "G85:" + g85Encode(withLength(bytes))
                "vine" -> vineEncode(bytes)
                "lock" -> lockEncode(bytes, key)
                "gstls" -> tlsEncode(bytes, key)
                "ganonym" -> ganonymEncode(bytes, key)
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
                "d67" -> digitalDecode(input, '7', '6', '6', '7')
                "d42" -> digitalDecode(input, '2', '4', '4', '2')
                "gs" -> strictUtf8(bitDecode(input, setOf('G', 'g'), setOf('S', 's')))
                "gonly" -> strictUtf8(gOnlyDecode(input))
                "nat9" -> strictUtf8(nat9Decode(input))
                "emoji" -> strictUtf8(emojiDecode(input, EMOJI_ZERO, EMOJI_ONE))
                "fruit" -> strictUtf8(emojiDecode(input, BANANA, GRAPE))
                "pig" -> strictUtf8(emojiDecode(input, PIG_NOSE, PIG_FACE))
                "bytecode" -> strictUtf8(bytecodeDecode(input))
                "asm" -> strictUtf8(asmDecode(input))
                "cpu" -> strictUtf8(cpuDecode(input))
                "base32" -> strictUtf8(base32Decode(input))
                "base58" -> strictUtf8(base58Decode(removePrefixOnce(input, "B58:")))
                "ascii85" -> strictUtf8(ascii85Decode(removePrefixOnce(input, "A85:")))
                "quoted" -> strictUtf8(quotedPrintableDecode(removePrefixOnce(input, "QP:")))
                "rot13" -> rot13(input)
                "atbash" -> atbash(input)
                "vigenere" -> vigenere(input, key, decrypt = true)
                "cats" -> strictUtf8(emojiDecode(input, CAT_ZERO, CAT_ONE))
                "moon" -> strictUtf8(nibbleTokenDecode(input, MOON_TOKENS))
                "chess" -> strictUtf8(emojiDecode(input, CHESS_ZERO, CHESS_ONE))
                "braille" -> strictUtf8(brailleDecode(input))
                "runes" -> strictUtf8(runesDecode(input))
                "dna" -> strictUtf8(dnaDecode(removePrefixOnce(input, "DNA:")))
                "decimal" -> strictUtf8(decimalDecode(removePrefixOnce(input, "DEC:")))
                "octal" -> strictUtf8(fixedRadixDecode(removePrefixOnce(input, "OCT:"), 8, 3))
                "ternary" -> strictUtf8(fixedRadixDecode(removePrefixOnce(input, "B3:"), 3, 6))
                "quaternary" -> strictUtf8(fixedRadixDecode(removePrefixOnce(input, "B4:"), 4, 4))
                "roman" -> strictUtf8(romanDecode(removePrefixOnce(input, "ROMAN:")))
                "gs32" -> strictUtf8(withoutLength(baseNDecode(removePrefixOnce(input, "GS32:"), GS32)))
                "g85" -> strictUtf8(withoutLength(g85Decode(removePrefixOnce(input, "G85:"))))
                "vine" -> strictUtf8(vineDecode(input))
                "lock" -> strictUtf8(lockDecode(input, key))
                "gstls" -> strictUtf8(tlsDecode(input, key))
                "ganonym" -> strictUtf8(ganonymDecode(input, key))
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
        if (input.startsWith("GSTLS1.")) return byId("gstls") to "начало GSTLS1. — пакет GS-TLS, нужен закрытый ключ получателя"
        if (input.startsWith("GA1.")) return byId("ganonym") to "начало GA1. — анонимный пакет GANONYM, нужен пароль"
        if (input.startsWith("GSLOCK1.")) return byId("lock") to "начало GSLOCK1. — «GS-Замок», нужен пароль"
        if (input.startsWith("; GSYNK BYTECODE 1")) return ok("bytecode", "заголовок байт-кода и поток 0x-байтов")
        if (input.startsWith("; GSYNK ASM 1")) return ok("asm", "ассемблерный листинг GSYNK ASM")
        if (input.startsWith("; GSYNK CPU DUMP 1")) return ok("cpu", "процессорный дамп памяти GSYNK CPU")
        if (input.startsWith("GS32:")) return ok("gs32", "начало GS32: — родной алфавит GSYNK") ?: byId("gs32") to "начало GS32:"
        if (input.startsWith("G85:")) return ok("g85", "начало G85: — компактная упаковка") ?: byId("g85") to "начало G85:"
        if (input.startsWith("XOR1.")) return byId("xor") to "начало XOR1. — нужен ключ"
        if (input.startsWith("B58:")) return ok("base58", "начало B58: — популярный Base58") ?: byId("base58") to "начало B58:"
        if (input.startsWith("A85:")) return ok("ascii85", "начало A85: — плотный Ascii85") ?: byId("ascii85") to "начало A85:"
        if (input.startsWith("QP:")) return ok("quoted", "начало QP: — Quoted-Printable") ?: byId("quoted") to "начало QP:"
        if (input.startsWith("DNA:")) return ok("dna", "начало DNA: и основания A/C/G/T") ?: byId("dna") to "начало DNA:"
        if (input.startsWith("DEC:")) return ok("decimal", "начало DEC: — байты десятичными числами") ?: byId("decimal") to "начало DEC:"
        if (input.startsWith("OCT:")) return ok("octal", "начало OCT: — байты восьмеричными тройками") ?: byId("octal") to "начало OCT:"
        if (input.startsWith("B3:")) return ok("ternary", "начало B3: — троичные байты") ?: byId("ternary") to "начало B3:"
        if (input.startsWith("B4:")) return ok("quaternary", "начало B4: — четверичные байты") ?: byId("quaternary") to "начало B4:"
        if (input.startsWith("ROMAN:")) return ok("roman", "начало ROMAN: — байты римскими числами") ?: byId("roman") to "начало ROMAN:"
        if (input.any { it.code in 0x2800..0x28FF }) return ok("braille", "состоит из знаков Брайля")
        if (input.any { it in RUNES }) return ok("runes", "состоит из рун")
        if (MOON_TOKENS.any { input.contains(it) }) return ok("moon", "состоит из лунных фаз")
        if (input.contains(CAT_ZERO) || input.contains(CAT_ONE)) return ok("cats", "состоит из котов 🐱 и 😼")
        if (input.contains(CHESS_ZERO) || input.contains(CHESS_ONE)) return ok("chess", "состоит из белых и чёрных кругов")
        // слова лозы
        val words = input.split('-', ' ', '\n').filter { it.isNotBlank() }
        if (words.size >= 4 && words.all { it.lowercase() in VINE }) return ok("vine", "все слова — из виноградного набора «Лозы»")
        // эмодзи и фрукты
        if (input.contains(PIG_NOSE) || input.contains(PIG_FACE)) return ok("pig", "состоит из поросят 🐽 и 🐷")
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
        // Base32 проверяем до Base64: у него узкий алфавит A-Z2-7 и добивка '='.
        val base32Compact = compact.uppercase()
        if (base32Compact.length >= 8 && base32Compact.length % 8 == 0 && base32Compact.all { it in 'A'..'Z' || it in '2'..'7' || it == '=' }) {
            ok("base32", "алфавит A–Z/2–7 и длина кратна восьми")?.let { return it }
        }
        // base64 — в самом конце, он слишком похож на обычный текст
        if (compact.length >= 8 && compact.length % 4 == 0 && compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            ok("base64", "похоже на Base64 и честно распаковывается в текст")?.let { return it }
        }
        return null
    }

    data class LayerStep(val cipherId: String, val title: String, val why: String)

    sealed class LayerOutcome {
        data class Ok(val text: String, val steps: List<LayerStep>) : LayerOutcome()
        data class Err(val message: String, val steps: List<LayerStep> = emptyList()) : LayerOutcome()
    }

    /**
     * Умная многослойная расшифровка: определяет внешний шифр, снимает его,
     * затем снова смотрит на результат. Так сообщение вида
     * Base64(Base64(🐽🐷(...))) открывается целиком, а не по одному слою.
     */
    fun decodeLayers(coded: String, key: String = "", maxLayers: Int = 16): LayerOutcome {
        val input = coded.trim()
        if (input.isEmpty()) return LayerOutcome.Ok("", emptyList())
        val steps = ArrayList<LayerStep>()
        val seen = mutableSetOf(input)
        var current = input
        while (steps.size < maxLayers.coerceIn(1, 32)) {
            val detected = detect(current) ?: break
            val (info, why) = detected
            val decoded = decode(info.id, current, key)
            if (decoded is Outcome.Err) {
                return if (steps.isEmpty()) {
                    LayerOutcome.Err(decoded.message)
                } else {
                    LayerOutcome.Err(
                        "Снял слоёв: " + steps.size + ", следующий «" + info.title + "» не открылся: " + decoded.message,
                        steps.toList()
                    )
                }
            }
            val next = (decoded as Outcome.Ok).text
            if (next.isEmpty() || next == current || next in seen) break
            steps += LayerStep(info.id, info.title, why)
            seen += next
            current = next
        }
        return if (steps.isEmpty()) {
            LayerOutcome.Err("Не узнал ни один слой шифра — попробуйте выбрать шифр вручную.")
        } else {
            LayerOutcome.Ok(current, steps.toList())
        }
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

    /**
     * Цифровые пары 5.1: главная цифра кодирует единицу (6 и 4), вторая — ноль.
     * Старые сообщения 5.0, где порядок был обратным, пробуем прочитать вторым проходом:
     * так исправление порядка не ломает уже отправленные шифры.
     */
    private fun digitalDecode(input: String, zero: Char, one: Char, legacyZero: Char, legacyOne: Char): String {
        val current = bitDecode(input, setOf(zero), setOf(one))
        return try {
            strictUtf8(current)
        } catch (e: CipherProblem) {
            strictUtf8(bitDecode(input, setOf(legacyZero), setOf(legacyOne)))
        }
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
    private const val PIG_NOSE = "\uD83D\uDC3D"   // 🐽
    private const val PIG_FACE = "\uD83D\uDC37"   // 🐷
    private const val CAT_ZERO = "\uD83D\uDC31"   // 🐱
    private const val CAT_ONE = "\uD83D\uDE3C"    // 😼
    private const val CHESS_ZERO = "\u26AA"        // ⚪
    private const val CHESS_ONE = "\u26AB"         // ⚫
    private val MOON_TOKENS = listOf(
        "\uD83C\uDF11", "\uD83C\uDF12", "\uD83C\uDF13", "\uD83C\uDF14",
        "\uD83C\uDF15", "\uD83C\uDF16", "\uD83C\uDF17", "\uD83C\uDF18",
        "\uD83C\uDF19", "\uD83C\uDF1A", "\uD83C\uDF1B", "\uD83C\uDF1C",
        "\uD83C\uDF1D", "\uD83C\uDF1E", "\u2B50", "\uD83C\uDF1F"
    )
    private const val RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊ"

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


    // ————— 5.2: популярные компактные коды, числа и символьные наборы —————

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private fun base32Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var liveBits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            liveBits += 8
            while (liveBits >= 5) {
                liveBits -= 5
                out.append(BASE32_ALPHABET[(buffer shr liveBits) and 0x1F])
            }
        }
        if (liveBits > 0) out.append(BASE32_ALPHABET[(buffer shl (5 - liveBits)) and 0x1F])
        while (out.length % 8 != 0) out.append('=')
        return out.toString()
    }

    private fun base32Decode(input: String): ByteArray {
        val clean = input.uppercase().filterNot { it.isWhitespace() }.trimEnd('=')
        if (clean.isEmpty()) throw CipherProblem("пустой Base32")
        val out = ArrayList<Byte>()
        var buffer = 0
        var liveBits = 0
        for (c in clean) {
            val value = BASE32_ALPHABET.indexOf(c)
            if (value < 0) throw CipherProblem("символ «" + c + "» не из Base32")
            buffer = (buffer shl 5) or value
            liveBits += 5
            if (liveBits >= 8) {
                liveBits -= 8
                out += ((buffer shr liveBits) and 0xFF).toByte()
            }
        }
        return out.toByteArray()
    }

    private fun base58Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val zeros = bytes.takeWhile { it.toInt() == 0 }.size
        if (zeros == bytes.size) return "1".repeat(zeros)
        var value = BigInteger(1, bytes)
        val radix = BigInteger.valueOf(58)
        val chars = ArrayList<Char>()
        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(radix)
            chars += BASE58_ALPHABET[divRem[1].toInt()]
            value = divRem[0]
        }
        return "1".repeat(zeros) + chars.asReversed().joinToString("")
    }

    private fun base58Decode(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) throw CipherProblem("пустой Base58")
        val zeros = clean.takeWhile { it == '1' }.length
        var value = BigInteger.ZERO
        val radix = BigInteger.valueOf(58)
        for (c in clean) {
            val digit = BASE58_ALPHABET.indexOf(c)
            if (digit < 0) throw CipherProblem("символ «" + c + "» не из Base58")
            value = value.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }
        val raw = if (value == BigInteger.ZERO) ByteArray(0) else value.toByteArray().let {
            if (it.isNotEmpty() && it[0].toInt() == 0) it.copyOfRange(1, it.size) else it
        }
        return ByteArray(zeros) + raw
    }

    private fun ascii85Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val count = minOf(4, bytes.size - i)
            var value = 0L
            for (k in 0 until 4) {
                value = value * 256 + if (k < count) (bytes[i + k].toInt() and 0xFF) else 0
            }
            val chars = CharArray(5)
            var rest = value
            for (k in 4 downTo 0) {
                chars[k] = ('!'.code + (rest % 85).toInt()).toChar()
                rest /= 85
            }
            out.append(chars, 0, count + 1)
            i += count
        }
        return out.toString()
    }

    private fun ascii85Decode(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) throw CipherProblem("пустой Ascii85")
        val out = ArrayList<Byte>()
        var i = 0
        while (i < clean.length) {
            val count = minOf(5, clean.length - i)
            if (count == 1) throw CipherProblem("последний блок Ascii85 обрезан")
            var value = 0L
            for (k in 0 until 5) {
                val digit = if (k < count) {
                    val d = clean[i + k].code - '!'.code
                    if (d !in 0..84) throw CipherProblem("символ «" + clean[i + k] + "» не из Ascii85")
                    d
                } else 84
                value = value * 85 + digit
            }
            if (value > 0xFFFF_FFFFL) throw CipherProblem("блок Ascii85 больше четырёх байт")
            val block = ByteArray(4) { index -> ((value shr (8 * (3 - index))) and 0xFF).toByte() }
            repeat(count - 1) { index -> out += block[index] }
            i += count
        }
        return out.toByteArray()
    }

    private fun quotedPrintableEncode(bytes: ByteArray): String {
        val out = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            when {
                v == 0x20 -> out.append('_')
                v in 0x21..0x7E && v != 0x3D && v != 0x5F -> out.append(v.toChar())
                else -> out.append('=').append("%02X".format(v))
            }
        }
        return out.toString()
    }

    private fun quotedPrintableDecode(input: String): ByteArray {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '_' -> { out += 0x20.toByte(); i++ }
                c == '=' -> {
                    if (i + 2 >= input.length) throw CipherProblem("после = нужны две HEX-цифры")
                    val value = input.substring(i + 1, i + 3).toIntOrNull(16)
                        ?: throw CipherProblem("битый код =XX в Quoted-Printable")
                    out += value.toByte()
                    i += 3
                }
                c.code in 0x21..0x7E -> { out += c.code.toByte(); i++ }
                else -> throw CipherProblem("символ «" + c + "» надо было записать как =XX")
            }
        }
        return out.toByteArray()
    }

    private fun rot13(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    else -> c
                }
            )
        }
    }

    private fun mirrorIn(c: Char, alphabet: String): Char {
        val index = alphabet.indexOf(c)
        return if (index < 0) c else alphabet[alphabet.lastIndex - index]
    }

    private fun atbash(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in EN_LOW -> mirrorIn(c, EN_LOW)
                    in EN_UP -> mirrorIn(c, EN_UP)
                    in RU_LOW -> mirrorIn(c, RU_LOW)
                    in RU_UP -> mirrorIn(c, RU_UP)
                    else -> c
                }
            )
        }
    }

    private fun alphabetOf(c: Char): String? = when (c) {
        in EN_LOW -> EN_LOW
        in EN_UP -> EN_UP
        in RU_LOW -> RU_LOW
        in RU_UP -> RU_UP
        else -> null
    }

    private fun vigenere(text: String, key: String, decrypt: Boolean): String {
        val shifts = key.mapNotNull { c ->
            alphabetOf(c)?.indexOf(c)?.takeIf { it >= 0 }
        }
        if (shifts.isEmpty()) throw CipherProblem("для Виженера нужен ключ из русских или английских букв")
        var keyIndex = 0
        return buildString(text.length) {
            for (c in text) {
                val alphabet = alphabetOf(c)
                if (alphabet == null) {
                    append(c)
                } else {
                    val index = alphabet.indexOf(c)
                    val shift = shifts[keyIndex % shifts.size] * if (decrypt) -1 else 1
                    keyIndex++
                    val n = alphabet.length
                    append(alphabet[((index + shift) % n + n) % n])
                }
            }
        }
    }

    private fun nibbleTokenEncode(bytes: ByteArray, tokens: List<String>): String {
        require(tokens.size == 16)
        return bytes.toList().chunked(16).joinToString(" ") { chunk ->
            chunk.joinToString("") { b ->
                val v = b.toInt() and 0xFF
                tokens[v shr 4] + tokens[v and 0x0F]
            }
        }
    }

    private fun nibbleTokenDecode(input: String, tokens: List<String>): ByteArray {
        val ordered = tokens.withIndex().sortedByDescending { it.value.length }
        val nibbles = ArrayList<Int>()
        var i = 0
        while (i < input.length) {
            if (input[i].isWhitespace()) {
                i++
                continue
            }
            val found = ordered.firstOrNull { input.startsWith(it.value, i) }
                ?: throw CipherProblem("чужой символ на позиции " + (i + 1))
            nibbles += found.index
            i += found.value.length
        }
        if (nibbles.isEmpty() || nibbles.size % 2 != 0) throw CipherProblem("полубайтов должно быть чётное число")
        return ByteArray(nibbles.size / 2) { index -> ((nibbles[index * 2] shl 4) or nibbles[index * 2 + 1]).toByte() }
    }

    private fun brailleEncode(bytes: ByteArray): String =
        bytes.toList().chunked(32).joinToString(" ") { chunk ->
            chunk.joinToString("") { (0x2800 + (it.toInt() and 0xFF)).toChar().toString() }
        }

    private fun brailleDecode(input: String): ByteArray {
        val chars = input.filterNot { it.isWhitespace() }
        if (chars.isEmpty() || chars.any { it.code !in 0x2800..0x28FF }) throw CipherProblem("в Брайле допустимы только знаки ⠀–⣿")
        return ByteArray(chars.length) { (chars[it].code - 0x2800).toByte() }
    }

    private fun runesEncode(bytes: ByteArray): String {
        return bytes.toList().chunked(32).joinToString(" ") { chunk ->
            chunk.joinToString("") { b ->
                val v = b.toInt() and 0xFF
                RUNES[v shr 4].toString() + RUNES[v and 0x0F]
            }
        }
    }

    private fun runesDecode(input: String): ByteArray {
        val chars = input.filterNot { it.isWhitespace() }
        if (chars.isEmpty() || chars.length % 2 != 0) throw CipherProblem("рун должно быть чётное число — по две на байт")
        return ByteArray(chars.length / 2) { index ->
            val hi = RUNES.indexOf(chars[index * 2])
            val lo = RUNES.indexOf(chars[index * 2 + 1])
            if (hi < 0 || lo < 0) throw CipherProblem("встретилась чужая руна")
            ((hi shl 4) or lo).toByte()
        }
    }

    private val DNA = charArrayOf('A', 'C', 'G', 'T')

    private fun dnaEncode(bytes: ByteArray): String = buildString(bytes.size * 4) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(DNA[(v shr 6) and 3]).append(DNA[(v shr 4) and 3]).append(DNA[(v shr 2) and 3]).append(DNA[v and 3])
        }
    }

    private fun dnaDecode(input: String): ByteArray {
        val clean = input.uppercase().filterNot { it.isWhitespace() }
        if (clean.isEmpty() || clean.length % 4 != 0) throw CipherProblem("в ДНК-шифре по четыре основания на байт")
        return ByteArray(clean.length / 4) { index ->
            var value = 0
            for (k in 0 until 4) {
                val digit = DNA.indexOf(clean[index * 4 + k])
                if (digit < 0) throw CipherProblem("основание «" + clean[index * 4 + k] + "» не A/C/G/T")
                value = (value shl 2) or digit
            }
            value.toByte()
        }
    }

    private fun decimalDecode(input: String): ByteArray {
        val tokens = input.split('-', ' ', '\n', ',').filter { it.isNotBlank() }
        if (tokens.isEmpty()) throw CipherProblem("нет десятичных байтов")
        return ByteArray(tokens.size) { index ->
            val value = tokens[index].toIntOrNull() ?: throw CipherProblem("«" + tokens[index] + "» — не число")
            if (value !in 0..255) throw CipherProblem("число " + value + " больше байта")
            value.toByte()
        }
    }

    private fun fixedRadixEncode(bytes: ByteArray, radix: Int, digits: Int): String {
        val alphabet = "0123456789"
        return bytes.joinToString(" ") { b ->
            var value = b.toInt() and 0xFF
            val chars = CharArray(digits) { '0' }
            for (index in digits - 1 downTo 0) {
                chars[index] = alphabet[value % radix]
                value /= radix
            }
            chars.concatToString()
        }
    }

    private fun fixedRadixDecode(input: String, radix: Int, digits: Int): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }
        if (clean.isEmpty() || clean.length % digits != 0) throw CipherProblem("нужно по " + digits + " цифр на байт")
        return ByteArray(clean.length / digits) { index ->
            var value = 0
            for (k in 0 until digits) {
                val digit = clean[index * digits + k].digitToIntOrNull()
                    ?: throw CipherProblem("встретилась не цифра")
                if (digit !in 0 until radix) throw CipherProblem("цифра " + digit + " не подходит для основания " + radix)
                value = value * radix + digit
            }
            if (value > 255) throw CipherProblem("группа № " + (index + 1) + " больше байта")
            value.toByte()
        }
    }

    private val ROMAN_TABLE = listOf(
        100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )

    private fun romanEncode(value: Int): String {
        if (value == 0) return "N"
        var rest = value
        val out = StringBuilder()
        for ((amount, symbol) in ROMAN_TABLE) {
            while (rest >= amount) {
                out.append(symbol)
                rest -= amount
            }
        }
        return out.toString()
    }

    private fun romanValue(token: String): Int {
        if (token == "N") return 0
        var index = 0
        var value = 0
        while (index < token.length) {
            val pair = ROMAN_TABLE.firstOrNull { token.startsWith(it.second, index) }
                ?: throw CipherProblem("«" + token + "» — не римское число")
            value += pair.first
            index += pair.second.length
        }
        if (value !in 0..255 || romanEncode(value) != token) throw CipherProblem("«" + token + "» — неканоничное римское число")
        return value
    }

    private fun romanDecode(input: String): ByteArray {
        val tokens = input.uppercase().split('-', ' ', '\n', ',').filter { it.isNotBlank() }
        if (tokens.isEmpty()) throw CipherProblem("нет римских чисел")
        return ByteArray(tokens.size) { romanValue(tokens[it]).toByte() }
    }

    // ————— машинные представления: обратимые текстовые виды тех же байтов —————

    private fun bytecodeEncode(bytes: ByteArray): String = buildString {
        appendLine("; GSYNK BYTECODE 1")
        appendLine("; bytes=" + bytes.size)
        bytes.toList().chunked(16).forEach { chunk ->
            appendLine(chunk.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })
        }
    }.trimEnd()

    private fun bytecodeDecode(input: String): ByteArray {
        val found = Regex("0[xX]([0-9A-Fa-f]{2})").findAll(input).toList()
        if (found.isEmpty()) throw CipherProblem("в байт-коде нет ни одного байта вида 0x7F")
        return ByteArray(found.size) { found[it].groupValues[1].toInt(16).toByte() }
    }

    private fun asmEncode(bytes: ByteArray): String = buildString {
        appendLine("; GSYNK ASM 1")
        appendLine("section .text")
        appendLine("global _start")
        appendLine("_start:")
        val registers = listOf("al", "bl", "cl", "dl")
        bytes.forEachIndexed { index, byte ->
            appendLine("    mov " + registers[index % registers.size] + ", 0x%02X".format(byte.toInt() and 0xFF))
            appendLine("    call emit_byte")
        }
        appendLine("    ret")
    }.trimEnd()

    private fun asmDecode(input: String): ByteArray {
        val found = Regex("(?i)mov\\s+[a-z]{2}\\s*,\\s*0x([0-9a-f]{2})").findAll(input).toList()
        if (found.isEmpty()) throw CipherProblem("в ассемблере нет команд MOV с байтами 0x..")
        return ByteArray(found.size) { found[it].groupValues[1].toInt(16).toByte() }
    }

    private fun cpuEncode(bytes: ByteArray): String = buildString {
        appendLine("; GSYNK CPU DUMP 1")
        appendLine("IP=0000 SP=FFFE FLAGS=----")
        bytes.toList().chunked(8).forEachIndexed { index, chunk ->
            val address = index * 8
            append("MEM %04X: ".format(address))
            append(chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
            appendLine("  ; IP=%04X A=%02X B=%02X".format(
                address + chunk.size,
                chunk.last().toInt() and 0xFF,
                chunk.fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
            ))
        }
        appendLine("HALT")
    }.trimEnd()

    private fun cpuDecode(input: String): ByteArray {
        val out = ArrayList<Byte>()
        input.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore(';').trim()
            if (!line.startsWith("MEM ")) return@forEach
            val afterColon = line.substringAfter(':', "")
            if (afterColon.isBlank()) throw CipherProblem("в процессорном дампе пустая строка MEM")
            afterColon.trim().split(Regex("\\s+")).forEach { token ->
                val value = token.toIntOrNull(16)
                if (token.length != 2 || value == null) throw CipherProblem("в MEM встретился не байт: «" + token + "»")
                out += value.toByte()
            }
        }
        if (out.isEmpty()) throw CipherProblem("в процессорном виде нет строк MEM")
        return out.toByteArray()
    }

    // ————— общие криптопримитивы: HKDF, сжатие и целые в кадрах —————

    private const val MAX_BYTES = 250_000

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        val out = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            out.write(previous)
            counter++
        }
        prk.fill(0)
        return out.toByteArray().copyOf(length)
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)

    private fun deflateRaw(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count > 0) out.write(buffer, 0, count)
                if (out.size() > MAX_BYTES) throw CipherProblem("сжатый пакет слишком большой")
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray {
        if (expectedSize !in 0..MAX_BYTES) throw CipherProblem("размер распаковки за пределами допустимого")
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (e: DataFormatException) {
                    throw CipherProblem("сжатая часть повреждена")
                }
                if (count > 0) {
                    out.write(buffer, 0, count)
                    if (out.size() > expectedSize) throw CipherProblem("распаковка вышла за заявленный размер")
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    throw CipherProblem("сжатая часть оборвана")
                }
            }
            val result = out.toByteArray()
            if (result.size != expectedSize) throw CipherProblem("размер после распаковки не совпал")
            return result
        } finally {
            inflater.end()
        }
    }

    private fun passwordKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    // ————— GS-TLS: ECDH P-256 -> HKDF-SHA256 -> AES-256-GCM —————

    private fun parseTlsPublic(raw: String): PublicKey {
        val text = raw.trim()
        if (!text.startsWith(TLS_PUBLIC_PREFIX)) {
            throw CipherProblem("GS-TLS шифруется открытым ключом получателя GSTLS-PUB.… — создай пару ключей кнопкой ниже и передай отправителю открытый")
        }
        return try {
            val der = Base64.getUrlDecoder().decode(text.removePrefix(TLS_PUBLIC_PREFIX))
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        } catch (e: Exception) {
            throw CipherProblem("открытый ключ GS-TLS повреждён или обрезан")
        }
    }

    private fun parseTlsPrivate(raw: String): PrivateKey {
        val text = raw.trim()
        if (!text.startsWith(TLS_PRIVATE_PREFIX)) {
            throw CipherProblem("GS-TLS открывается закрытым ключом получателя GSTLS-PRIV.… — открытый ключ для расшифровки не подходит")
        }
        return try {
            val der = Base64.getUrlDecoder().decode(text.removePrefix(TLS_PRIVATE_PREFIX))
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
        } catch (e: Exception) {
            throw CipherProblem("закрытый ключ GS-TLS повреждён или обрезан")
        }
    }

    private fun tlsEncode(data: ByteArray, publicKeyText: String): String {
        val recipient = parseTlsPublic(publicKeyText)
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val ephemeral = generator.generateKeyPair()
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(recipient, true)
        val shared = agreement.generateSecret()
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val ephemeralDer = ephemeral.public.encoded
        if (ephemeralDer.size > 0xFFFF) throw CipherProblem("эфемерный ключ неожиданно большой")
        val header = ByteArray(2 + ephemeralDer.size + salt.size + iv.size)
        header[0] = (ephemeralDer.size ushr 8).toByte()
        header[1] = ephemeralDer.size.toByte()
        ephemeralDer.copyInto(header, 2)
        salt.copyInto(header, 2 + ephemeralDer.size)
        iv.copyInto(header, 2 + ephemeralDer.size + salt.size)
        val keyBytes = hkdfSha256(
            ikm = shared,
            salt = salt,
            info = "GSYNK GSTLS1 ECDH P-256 AES-256-GCM".toByteArray(Charsets.UTF_8),
            length = 32
        )
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("GSTLS1".toByteArray(Charsets.UTF_8) + header)
            return "GSTLS1." + Base64.getUrlEncoder().withoutPadding().encodeToString(header + cipher.doFinal(data))
        } finally {
            shared.fill(0)
            keyBytes.fill(0)
        }
    }

    private fun tlsDecode(input: String, privateKeyText: String): ByteArray {
        val recipientPrivate = parseTlsPrivate(privateKeyText)
        val raw = try {
            Base64.getUrlDecoder().decode(removePrefixOnce(input, "GSTLS1."))
        } catch (e: Exception) {
            throw CipherProblem("тело GS-TLS повреждено (не Base64URL)")
        }
        if (raw.size < 2 + 16 + 12 + 16) throw CipherProblem("пакет GS-TLS слишком короткий")
        val keyLength = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
        val headerSize = 2 + keyLength + 16 + 12
        if (keyLength !in 32..1024 || raw.size < headerSize + 16) throw CipherProblem("заголовок GS-TLS повреждён")
        val ephemeralDer = raw.copyOfRange(2, 2 + keyLength)
        val salt = raw.copyOfRange(2 + keyLength, 2 + keyLength + 16)
        val iv = raw.copyOfRange(2 + keyLength + 16, headerSize)
        val sealed = raw.copyOfRange(headerSize, raw.size)
        val ephemeralPublic = try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(ephemeralDer))
        } catch (e: Exception) {
            throw CipherProblem("эфемерный ключ GS-TLS повреждён")
        }
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(recipientPrivate)
        agreement.doPhase(ephemeralPublic, true)
        val shared = agreement.generateSecret()
        val keyBytes = hkdfSha256(
            ikm = shared,
            salt = salt,
            info = "GSYNK GSTLS1 ECDH P-256 AES-256-GCM".toByteArray(Charsets.UTF_8),
            length = 32
        )
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("GSTLS1".toByteArray(Charsets.UTF_8) + raw.copyOfRange(0, headerSize))
            return cipher.doFinal(sealed)
        } catch (e: Exception) {
            throw CipherProblem("GS-TLS не открылся: закрытый ключ не от этого пакета или пакет изменили")
        } finally {
            shared.fill(0)
            keyBytes.fill(0)
        }
    }

    // ————— GANONYM: сжатие + ступени размера + PBKDF2 -> AES-256-GCM без метаданных человека —————

    private const val GANONYM_ITERATIONS = 240_000

    private fun anonymBucket(size: Int): Int {
        var bucket = 128
        while (bucket < size && bucket <= MAX_BYTES) bucket *= 2
        return maxOf(bucket, size)
    }

    private fun ganonymEncode(data: ByteArray, password: String): String {
        if (password.isBlank()) throw CipherProblem("GANONYM без пароля не закрывается")
        if (data.size > MAX_BYTES) throw CipherProblem("GANONYM рассчитан на текст до 250 000 байт")
        val compressed = deflateRaw(data)
        val useCompressed = compressed.size < data.size
        val body = if (useCompressed) compressed else data
        val random = SecureRandom()
        val frame = ByteArray(anonymBucket(9 + body.size)).also { random.nextBytes(it) }
        frame[0] = if (useCompressed) 1 else 0
        writeInt(frame, 1, data.size)
        writeInt(frame, 5, body.size)
        body.copyInto(frame, 9)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, passwordKey(password, salt, GANONYM_ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD("GA1".toByteArray(Charsets.UTF_8))
        return "GA1." + Base64.getUrlEncoder().withoutPadding().encodeToString(salt + iv + cipher.doFinal(frame))
    }

    private fun ganonymDecode(input: String, password: String): ByteArray {
        if (password.isBlank()) throw CipherProblem("это GANONYM — введи пароль, которым закрывали")
        val raw = try {
            Base64.getUrlDecoder().decode(removePrefixOnce(input, "GA1."))
        } catch (e: Exception) {
            throw CipherProblem("тело GANONYM повреждено (не Base64URL)")
        }
        if (raw.size < 16 + 12 + 16 + 9) throw CipherProblem("пакет GANONYM слишком короткий")
        val salt = raw.copyOfRange(0, 16)
        val iv = raw.copyOfRange(16, 28)
        val frame = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, passwordKey(password, salt, GANONYM_ITERATIONS), GCMParameterSpec(128, iv))
            cipher.updateAAD("GA1".toByteArray(Charsets.UTF_8))
            cipher.doFinal(raw.copyOfRange(28, raw.size))
        } catch (e: Exception) {
            throw CipherProblem("GANONYM не открылся: пароль не тот или пакет изменили")
        }
        if (frame.size < 9) throw CipherProblem("внутренний кадр GANONYM повреждён")
        val originalSize = readInt(frame, 1)
        val bodySize = readInt(frame, 5)
        if (originalSize !in 0..MAX_BYTES || bodySize !in 0..(frame.size - 9)) {
            throw CipherProblem("размеры внутри GANONYM повреждены")
        }
        val body = frame.copyOfRange(9, 9 + bodySize)
        return when (frame[0].toInt()) {
            0 -> if (body.size == originalSize) body else throw CipherProblem("размер GANONYM не совпал")
            1 -> inflateRaw(body, originalSize)
            else -> throw CipherProblem("неизвестный вариант кадра GANONYM")
        }
    }

    // ————— GS-Замок: пароль -> PBKDF2(120k, SHA-256) -> AES-256-GCM. Тяжёлый нарочно. —————
    private const val LOCK_ITERATIONS = 120_000

    private fun lockKey(password: String, salt: ByteArray): SecretKeySpec =
        passwordKey(password, salt, LOCK_ITERATIONS)

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
