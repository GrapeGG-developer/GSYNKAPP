package com.gsynk.app

import com.gsynk.app.core.GsaiArchitecture
import com.gsynk.app.core.GsaiChat
import com.gsynk.app.core.GsaiLayer
import com.gsynk.app.core.GsaiMeta
import com.gsynk.app.core.GsaiModel
import com.gsynk.app.core.GsaiPackage
import com.gsynk.app.core.GsaiSession
import com.gsynk.app.core.GsaiTags
import com.gsynk.app.core.GsaiTokenizer
import com.gsynk.app.core.HalfFloat
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GSYNK-AI на телефоне: токенизатор, пакет .gsai, движок и удаление старых ИИ. */
class GsaiTest {

    private val vocab = GsaiTags.SPECIALS + listOf("привет", "мир", "!", "как", "дела", "?")

    @Test
    fun tokenizerMirrorsPython() {
        val tokenizer = GsaiTokenizer(vocab)
        val ids = tokenizer.encode("Привет, мир!")
        // "привет" есть, "," нет в словаре -> UNK, "мир", "!"
        assertArrayEquals(intArrayOf(7, GsaiTags.UNK, 8, 9), ids)
        val text = tokenizer.decode(intArrayOf(7, 8, 9))
        assertEquals("Привет мир!", text)
    }

    @Test
    fun tokenizerRejectsForeignVocab() {
        val bad = runCatching { GsaiTokenizer(listOf("a", "b")) }
        assertTrue(bad.isFailure)
    }

    @Test
    fun halfFloatRoundtrip() {
        listOf(0f, 1f, -1f, 0.5f, -2.25f, 100f, 0.125f).forEach { value ->
            val restored = HalfFloat.toFloat(HalfFloat.fromFloat(value))
            assertTrue("fp16 $value -> $restored", abs(restored - value) <= abs(value) * 0.002f + 1e-4f)
        }
        assertEquals(0f, HalfFloat.toFloat(0), 0f)
        assertTrue(HalfFloat.toFloat(HalfFloat.fromFloat(Float.POSITIVE_INFINITY)).isInfinite())
    }

    private fun tinyModel(headBias: FloatArray): GsaiModel {
        val d = 8
        val arch = GsaiArchitecture(vocabSize = vocab.size, blockSize = 16, nLayer = 1, nHead = 2, nEmbd = d)
        val zeros = { n: Int -> FloatArray(n) }
        val layer = GsaiLayer(
            ln1W = zeros(d), ln1B = zeros(d),
            qkvW = zeros(3 * d * d), qkvB = zeros(3 * d),
            projW = zeros(d * d), projB = zeros(d),
            ln2W = zeros(d), ln2B = zeros(d),
            fcW = zeros(4 * d * d), fcB = zeros(4 * d),
            mlpW = zeros(4 * d * d), mlpB = zeros(d)
        )
        val meta = GsaiMeta(name = "Тест", architecture = arch, format = "gsai-1")
        return GsaiModel(
            meta = meta,
            tokenizer = GsaiTokenizer(vocab),
            tokEmb = zeros(vocab.size * d),
            posEmb = zeros(16 * d),
            layers = listOf(layer),
            lnfW = zeros(d), lnfB = zeros(d),
            headW = zeros(vocab.size * d),
            headB = headBias
        )
    }

    @Test
    fun engineGreedyFollowsHeadBias() {
        val bias = FloatArray(vocab.size)
        bias[8] = 2f // "мир"
        val session = GsaiSession(tinyModel(bias))
        val out = session.generate(intArrayOf(GsaiTags.USER, 7, GsaiTags.BOT), maxNew = 4, temperature = 0f, topK = 0)
        assertArrayEquals(intArrayOf(8, 8, 8, 8), out)
    }

    @Test
    fun engineStopsAtEndToken() {
        val bias = FloatArray(vocab.size)
        bias[GsaiTags.END] = 5f
        val session = GsaiSession(tinyModel(bias))
        val out = session.generate(intArrayOf(GsaiTags.USER, 7, GsaiTags.BOT), maxNew = 10, temperature = 0f, topK = 0)
        assertEquals(0, out.size)
    }

    @Test
    fun packageRoundtripFromZipBytes() {
        val arch = GsaiArchitecture(vocabSize = vocab.size, blockSize = 16, nLayer = 1, nHead = 2, nEmbd = 8)
        val d = arch.nEmbd
        val v = arch.vocabSize
        val perLayer = d + d + 3 * d * d + 3 * d + d * d + d + d + d + 4 * d * d + 4 * d + 4 * d * d + d
        val total = v * d + arch.blockSize * d + arch.nLayer * perLayer + d + d + v * d + v
        val floats = FloatArray(total)
        floats[total - v + GsaiTags.END] = 3f // head.bias[END] — генерация сразу стоп
        val weights = ByteArrayOutputStream()
        floats.forEach { f ->
            val bits = HalfFloat.fromFloat(f)
            weights.write(bits and 0xFF)
            weights.write((bits ushr 8) and 0xFF)
        }
        val weightsBytes = weights.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(weightsBytes).joinToString("") { "%02x".format(it) }
        val metaJson = """
            {"format":"gsai-1","name":"ПакетТест","icon":"🍇","description":"","version":"1.0.0",
             "architecture":{"vocab_size":$v,"block_size":16,"n_layer":1,"n_head":2,"n_embd":8},
             "params":$total,"temperature":0.8,"top_k":40,"max_new_tokens":10,
             "steps":1,"loss":1.5,"weights_sha256":"$sha"}
        """.trimIndent()
        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(ZipEntry("model.json")); zip.write(metaJson.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("vocab.json")); zip.write(Json.encodeToString(vocab).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("weights.bin")); zip.write(weightsBytes); zip.closeEntry()
        }
        val model = GsaiPackage.load(zipBytes.toByteArray())
        assertEquals("ПакетТест", model.meta.name)
        val out = GsaiSession(model).generate(intArrayOf(GsaiTags.USER, 7, GsaiTags.BOT), 5, 0f, 0, Random(1))
        assertEquals(0, out.size)
        // повреждённый пакет ловится по контрольной сумме: веса те же, sha в метаданных чужая
        val badMeta = metaJson.replace(sha, "0".repeat(64))
        val badZip = ByteArrayOutputStream()
        ZipOutputStream(badZip).use { zip ->
            zip.putNextEntry(ZipEntry("model.json")); zip.write(badMeta.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("vocab.json")); zip.write(Json.encodeToString(vocab).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("weights.bin")); zip.write(weightsBytes); zip.closeEntry()
        }
        val bad = runCatching { GsaiPackage.load(badZip.toByteArray()) }
        assertTrue(bad.isFailure)
    }

    @Test
    fun chatPromptUsesDialogueTags() {
        val tokenizer = GsaiTokenizer(vocab)
        val prompt = GsaiChat.buildPrompt(tokenizer, listOf("привет" to "мир"), "как дела?", blockSize = 256)
        assertEquals(GsaiTags.USER, prompt[0])
        assertEquals(GsaiTags.BOT, prompt[prompt.lastIndex])
        assertTrue(prompt.contains(GsaiTags.END))
        assertTrue(prompt.count { it == GsaiTags.USER } == 2)
    }

    @Test
    fun oldAiRemovedCommandBotKept() {
        val main = File("src/main/java/com/gsynk/app")
        assertFalse("AiNet должен быть удалён", File(main, "core/AiNet.kt").exists())
        assertFalse("RealtimeAi должен быть удалён", File(main, "core/RealtimeAi.kt").exists())
        assertFalse("AiScreen должен быть удалён", File(main, "ui/AiScreen.kt").exists())
        assertTrue("командный ИИ остаётся отдельно", File(main, "core/CommandBot.kt").exists())
        assertTrue("чат командного бота остаётся", File(main, "ui/ChatScreen.kt").exists())
        val catalog = File(main, "ui/Catalog.kt").readText()
        assertFalse(catalog.contains("page:ai"))
        assertTrue(catalog.contains("\"gsai\""))
        assertTrue(catalog.contains("page:gsai"))
        assertTrue(catalog.contains("командный ии"))
        val router = File(main, "ui/GiouiScreens.kt").readText()
        assertTrue(router.contains("\"gsai\" -> { GsaiModelsScreen(onBack); return true }"))
        assertFalse(router.contains("AiScreen"))
    }
}
