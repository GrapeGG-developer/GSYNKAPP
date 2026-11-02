package com.gsynk.app.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GSYNK-AI на телефоне: загрузка пакетов .gsai (их собирает программа GSYNK-AI на ПК)
 * и запуск маленького трансформера прямо на устройстве, без интернета.
 *
 * Токенизатор, порядок весов и математика один-в-один повторяют Python-ядро GSYNK-AI,
 * поэтому модель отвечает на телефоне так же, как в проверке на ПК.
 */
object GsaiTags {
    const val PAD = 0
    const val UNK = 1
    const val USER = 2
    const val BOT = 3
    const val END = 4
    const val BOS = 5
    const val EOS = 6
    val SPECIALS = listOf("<pad>", "<unk>", "[USER]", "[BOT]", "[END]", "[BOS]", "[EOS]")
}

/** Словесный токенизатор: зеркало gsai/tokenizer.py. */
class GsaiTokenizer(val vocab: List<String>) {
    init {
        require(vocab.take(GsaiTags.SPECIALS.size) == GsaiTags.SPECIALS) {
            "словарь должен начинаться со специальных токенов GSYNK-AI"
        }
    }

    private val stoi: Map<String, Int> = vocab.withIndex().associate { it.value to it.index }
    val vocabSize: Int get() = vocab.size

    fun encode(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val lowered = text.lowercase(Locale.ROOT)
        val matches = TOKEN_REGEX.findAll(lowered)
        val out = ArrayList<Int>()
        for (m in matches) out += stoi[m.value] ?: GsaiTags.UNK
        return out.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        var capitalizeNext = true
        var prev = ""
        for (idx in ids) {
            when (idx) {
                GsaiTags.PAD, GsaiTags.USER, GsaiTags.BOT, GsaiTags.BOS -> continue
                GsaiTags.END, GsaiTags.EOS -> break
            }
            var tok = vocab.getOrNull(idx) ?: continue
            if (tok == "<unk>") tok = "…"
            if (tok.isEmpty()) continue
            if (capitalizeNext && tok[0].isLetter()) {
                tok = tok.replaceFirstChar { it.uppercaseChar() }
                capitalizeNext = false
            }
            if (sb.isNotEmpty() && tok !in NO_SPACE_BEFORE && prev !in NO_SPACE_AFTER) sb.append(' ')
            sb.append(tok)
            prev = tok
            if (tok == "." || tok == "!" || tok == "?") capitalizeNext = true
        }
        return sb.toString().trim()
    }

    companion object {
        private val TOKEN_REGEX = Regex("[a-zа-яё0-9]+|[^\\sa-zа-яё0-9]", RegexOption.IGNORE_CASE)
        private val NO_SPACE_BEFORE = setOf(".", ",", "!", "?", ";", ":", "…", "%", ")", "»")
        private val NO_SPACE_AFTER = setOf("«", "(")
    }
}

@Serializable
data class GsaiArchitecture(
    @SerialName("vocab_size") val vocabSize: Int,
    @SerialName("block_size") val blockSize: Int,
    @SerialName("n_layer") val nLayer: Int,
    @SerialName("n_head") val nHead: Int,
    @SerialName("n_embd") val nEmbd: Int
)

@Serializable
data class GsaiMeta(
    val format: String = "",
    val name: String = "Модель",
    val icon: String = "🍇",
    val description: String = "",
    val version: String = "1.0.0",
    val architecture: GsaiArchitecture,
    val params: Long = 0,
    val temperature: Float = 0.8f,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("max_new_tokens") val maxNewTokens: Int = 90,
    val steps: Int = 0,
    val loss: Float = 0f,
    @SerialName("weights_sha256") val weightsSha256: String = ""
)

@Serializable
data class GsaiServerModel(
    val id: String,
    val name: String = "",
    val icon: String = "🍇",
    val description: String = "",
    val version: String = "1.0.0",
    val params: Long = 0,
    val size: Long = 0,
    val sha256: String = "",
    val url: String = "",
    val steps: Int = 0,
    val loss: Float = 0f
)

@Serializable
data class GsaiIndex(val models: List<GsaiServerModel> = emptyList())

/** float16 ↔ float32: веса в пакете хранятся в половинной точности. */
internal object HalfFloat {
    fun toFloat(bits: Int): Float {
        val sign = (bits ushr 15) and 1
        val exponent = (bits ushr 10) and 0x1F
        val fraction = bits and 0x3FF
        val value: Float = when {
            exponent == 0 -> {
                if (fraction == 0) 0f
                else {
                    var f = fraction.toFloat()
                    var e = -14
                    while (f >= 1024f) {
                        f /= 2f; e++
                    }
                    (f / 1024f) * pow2(e)
                }
            }
            exponent == 0x1F -> if (fraction == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> (1f + fraction / 1024f) * pow2(exponent - 15)
        }
        return if (sign == 1) -value else value
    }

    fun fromFloat(value: Float): Int {
        val bits = value.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        val fraction = bits and 0x7FFFFF
        return when {
            value.isNaN() -> sign or 0x7E00
            value == Float.POSITIVE_INFINITY || value == Float.NEGATIVE_INFINITY -> sign or 0x7C00
            exponent >= 0x1F -> sign or 0x7C00
            exponent <= 0 -> {
                if (exponent < -10) sign
                else {
                    val m = fraction or 0x800000
                    val shift = 14 - exponent
                    sign or ((m + (1 shl (shift - 1))) ushr shift)
                }
            }
            else -> sign or (exponent shl 10) or ((fraction + 0x1000) ushr 13)
        }
    }

    private fun pow2(e: Int): Float {
        var r = 1f
        if (e >= 0) repeat(e) { r *= 2f } else repeat(-e) { r /= 2f }
        return r
    }
}

class GsaiLayer(
    val ln1W: FloatArray, val ln1B: FloatArray,
    val qkvW: FloatArray, val qkvB: FloatArray,
    val projW: FloatArray, val projB: FloatArray,
    val ln2W: FloatArray, val ln2B: FloatArray,
    val fcW: FloatArray, val fcB: FloatArray,
    val mlpW: FloatArray, val mlpB: FloatArray
)

class GsaiModel(
    val meta: GsaiMeta,
    val tokenizer: GsaiTokenizer,
    val tokEmb: FloatArray,
    val posEmb: FloatArray,
    val layers: List<GsaiLayer>,
    val lnfW: FloatArray, val lnfB: FloatArray,
    val headW: FloatArray, val headB: FloatArray
) {
    val d: Int = meta.architecture.nEmbd
    val blockSize: Int = meta.architecture.blockSize
    val vocabSize: Int = meta.architecture.vocabSize
}

object GsaiPackage {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseMeta(bytes: ByteArray): GsaiMeta? = runCatching {
        readEntry(bytes, "model.json")?.let { json.decodeFromString(GsaiMeta.serializer(), it.decodeToString()) }
    }.getOrNull()

    fun load(bytes: ByteArray): GsaiModel {
        val metaBytes = readEntry(bytes, "model.json") ?: error("в пакете нет model.json")
        val meta = json.decodeFromString(GsaiMeta.serializer(), metaBytes.decodeToString())
        require(meta.format == "gsai-1") { "неизвестный формат пакета: нужен gsai-1" }
        val vocabBytes = readEntry(bytes, "vocab.json") ?: error("в пакете нет vocab.json")
        val vocab = json.decodeFromString<List<String>>(vocabBytes.decodeToString())
        val weightsBin = readEntry(bytes, "weights.bin") ?: error("в пакете нет weights.bin")
        if (meta.weightsSha256.isNotEmpty()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(weightsBin)
            val hex = digest.joinToString("") { "%02x".format(it) }
            require(hex == meta.weightsSha256) { "weights.bin повреждён: контрольная сумма не сошлась" }
        }
        val arch = meta.architecture
        require(vocab.size == arch.vocabSize) { "словарь не совпадает с архитектурой" }
        val d = arch.nEmbd
        val v = arch.vocabSize
        val t = arch.blockSize
        val cursor = HalfCursor(weightsBin)
        val tokEmb = cursor.take(v * d)
        val posEmb = cursor.take(t * d)
        val layers = ArrayList<GsaiLayer>(arch.nLayer)
        repeat(arch.nLayer) {
            layers += GsaiLayer(
                ln1W = cursor.take(d), ln1B = cursor.take(d),
                qkvW = cursor.take(3 * d * d), qkvB = cursor.take(3 * d),
                projW = cursor.take(d * d), projB = cursor.take(d),
                ln2W = cursor.take(d), ln2B = cursor.take(d),
                fcW = cursor.take(4 * d * d), fcB = cursor.take(4 * d),
                mlpW = cursor.take(d * 4 * d), mlpB = cursor.take(d)
            )
        }
        val lnfW = cursor.take(d)
        val lnfB = cursor.take(d)
        val headW = cursor.take(v * d)
        val headB = cursor.take(v)
        require(cursor.exhausted()) { "размер весов не совпал с архитектурой" }
        return GsaiModel(meta, GsaiTokenizer(vocab), tokEmb, posEmb, layers, lnfW, lnfB, headW, headB)
    }

    private fun readEntry(bytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private class HalfCursor(private val raw: ByteArray) {
        private var offset = 0
        fun take(count: Int): FloatArray {
            val out = FloatArray(count)
            for (i in 0 until count) {
                val lo = raw[offset].toInt() and 0xFF
                val hi = raw[offset + 1].toInt() and 0xFF
                out[i] = HalfFloat.toFloat(lo or (hi shl 8))
                offset += 2
            }
            return out
        }

        fun exhausted(): Boolean = offset == raw.size
    }
}

/**
 * Инференс с KV-кэшем: каждый новый токен считается одной позицией,
 * поэтому даже средняя модель отвечает на телефоне в реальном времени.
 */
class GsaiSession(val model: GsaiModel) {
    private val d = model.d
    private val heads = model.meta.architecture.nHead
    private val headDim = d / heads
    private val kCache = Array(model.layers.size) { FloatArray(model.blockSize * d) }
    private val vCache = Array(model.layers.size) { FloatArray(model.blockSize * d) }
    private var length = 0
    private val fedTokens = ArrayList<Int>()

    fun reset() {
        length = 0
        fedTokens.clear()
    }

    /** Подаёт токен и возвращает логиты следующего. */
    fun push(token: Int): FloatArray {
        if (length >= model.blockSize) rebuildWithTail()
        fedTokens += token
        val pos = length
        val x = FloatArray(d)
        val tokOff = token.coerceIn(0, model.vocabSize - 1) * d
        for (i in 0 until d) x[i] = model.tokEmb[tokOff + i] + model.posEmb[pos * d + i]
        for (li in model.layers.indices) {
            val layer = model.layers[li]
            val h = layerNorm(x, layer.ln1W, layer.ln1B)
            val qkv = linear(h, layer.qkvW, layer.qkvB, 3 * d, d)
            val cacheOff = pos * d
            for (i in 0 until d) {
                kCache[li][cacheOff + i] = qkv[d + i]
                vCache[li][cacheOff + i] = qkv[2 * d + i]
            }
            val attended = FloatArray(d)
            val scale = 1f / sqrt(headDim.toFloat())
            for (head in 0 until heads) {
                val start = head * headDim
                val scores = FloatArray(pos + 1)
                var maxScore = Float.NEGATIVE_INFINITY
                for (p in 0..pos) {
                    var dot = 0f
                    val kOff = p * d + start
                    for (j in 0 until headDim) dot += qkv[start + j] * kCache[li][kOff + j]
                    val s = dot * scale
                    scores[p] = s
                    if (s > maxScore) maxScore = s
                }
                var sum = 0f
                for (p in 0..pos) {
                    scores[p] = exp(scores[p] - maxScore)
                    sum += scores[p]
                }
                for (p in 0..pos) {
                    val wgt = scores[p] / sum
                    val vOff = p * d + start
                    for (j in 0 until headDim) attended[start + j] += wgt * vCache[li][vOff + j]
                }
            }
            val projected = linear(attended, layer.projW, layer.projB, d, d)
            for (i in 0 until d) x[i] += projected[i]
            val h2 = layerNorm(x, layer.ln2W, layer.ln2B)
            val hidden = linear(h2, layer.fcW, layer.fcB, 4 * d, d)
            for (i in hidden.indices) if (hidden[i] < 0f) hidden[i] = 0f
            val mlp = linear(hidden, layer.mlpW, layer.mlpB, d, 4 * d)
            for (i in 0 until d) x[i] += mlp[i]
        }
        length++
        val normed = layerNorm(x, model.lnfW, model.lnfB)
        return linear(normed, model.headW, model.headB, model.vocabSize, d)
    }

    private fun rebuildWithTail() {
        val keep = fedTokens.takeLast(model.blockSize / 2)
        reset()
        for (tok in keep) push(tok)
    }

    fun generate(
        prompt: IntArray,
        maxNew: Int,
        temperature: Float,
        topK: Int,
        random: Random = Random.Default
    ): IntArray {
        reset()
        require(prompt.isNotEmpty()) { "пустой запрос" }
        var logits = push(prompt[0])
        for (i in 1 until prompt.size) logits = push(prompt[i])
        val out = ArrayList<Int>()
        repeat(maxNew) {
            val next = sample(logits, temperature, topK, random)
            if (next == GsaiTags.END || next == GsaiTags.EOS) return out.toIntArray()
            out += next
            logits = push(next)
        }
        return out.toIntArray()
    }

    private fun sample(logits: FloatArray, temperature: Float, topK: Int, random: Random): Int {
        if (temperature <= 0.01f) {
            var best = 0
            for (i in logits.indices) if (logits[i] > logits[best]) best = i
            return best
        }
        val scaled = FloatArray(logits.size) { logits[it] / temperature }
        if (topK in 1 until scaled.size) {
            val sorted = scaled.sortedDescending()
            val threshold = sorted[topK - 1]
            for (i in scaled.indices) if (scaled[i] < threshold) scaled[i] = Float.NEGATIVE_INFINITY
        }
        var max = Float.NEGATIVE_INFINITY
        for (v in scaled) if (v > max) max = v
        var sum = 0.0
        val probs = DoubleArray(scaled.size)
        for (i in scaled.indices) {
            val p = if (scaled[i] == Float.NEGATIVE_INFINITY) 0.0 else exp((scaled[i] - max).toDouble())
            probs[i] = p
            sum += p
        }
        var roll = random.nextDouble() * sum
        for (i in probs.indices) {
            roll -= probs[i]
            if (roll <= 0.0) return i
        }
        return probs.indices.maxByOrNull { probs[it] } ?: 0
    }

    private fun layerNorm(x: FloatArray, w: FloatArray, b: FloatArray): FloatArray {
        var mean = 0f
        for (v in x) mean += v
        mean /= x.size
        var variance = 0f
        for (v in x) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= x.size
        val inv = 1f / sqrt(variance + 1e-5f)
        return FloatArray(x.size) { (x[it] - mean) * inv * w[it] + b[it] }
    }

    private fun linear(x: FloatArray, w: FloatArray, b: FloatArray, outDim: Int, inDim: Int): FloatArray {
        val out = FloatArray(outDim)
        for (o in 0 until outDim) {
            var sum = b[o]
            val row = o * inDim
            for (i in 0 until inDim) sum += w[row + i] * x[i]
            out[o] = sum
        }
        return out
    }
}

/** Формат диалога — тот же, на котором модель училась на ПК. */
object GsaiChat {
    fun buildPrompt(
        tokenizer: GsaiTokenizer,
        history: List<Pair<String, String>>,
        userText: String,
        blockSize: Int
    ): IntArray {
        val ids = ArrayList<Int>()
        for ((user, bot) in history) {
            ids += GsaiTags.USER
            ids.addAll(tokenizer.encode(user).toList())
            ids += GsaiTags.BOT
            ids.addAll(tokenizer.encode(bot).toList())
            ids += GsaiTags.END
        }
        ids += GsaiTags.USER
        ids.addAll(tokenizer.encode(userText).toList())
        ids += GsaiTags.BOT
        val trimmed = if (ids.size > blockSize) ids.subList(ids.size - blockSize, ids.size) else ids
        return trimmed.toIntArray()
    }

    fun reply(
        session: GsaiSession,
        history: List<Pair<String, String>>,
        userText: String,
        temperature: Float,
        topK: Int,
        maxNew: Int,
        random: Random = Random.Default
    ): String {
        val model = session.model
        val prompt = buildPrompt(model.tokenizer, history, userText, model.blockSize)
        val out = session.generate(prompt, maxNew, temperature, topK, random)
        val text = model.tokenizer.decode(out)
        return text.ifEmpty { "…" }
    }
}
