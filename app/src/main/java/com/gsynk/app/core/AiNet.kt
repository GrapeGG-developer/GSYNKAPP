package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Настоящие ИИ-сети, без обёрток и без «магии».
 *
 * Все крупные сервисы говорят на одном языке — OpenAI-совместимом `/chat/completions`,
 * поэтому здесь одна дорога: собрать JSON, отправить, достать ответ. Ключ хранится
 * только в настройках телефона и никогда не пишется ни в отчёт, ни в журнал.
 * Если ничего не настроено — работает офлайн-помощник [CommandBot].
 */
object AiNet {

    data class Provider(
        val id: String,
        val title: String,
        val baseUrl: String,
        val model: String,
        val hint: String,
        val needsKey: Boolean
    )

    val PROVIDERS: List<Provider> = listOf(
        Provider(
            "offline", "GSYNK офлайн", "", "", "работает без интернета и без ключа", false
        ),
        Provider(
            "openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini",
            "ключ вида sk-… из platform.openai.com", true
        ),
        Provider(
            "groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile",
            "быстрая сеть, ключ gsk_… из console.groq.com", true
        ),
        Provider(
            "openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free",
            "сотни моделей, есть бесплатные; ключ sk-or-…", true
        ),
        Provider(
            "ollama", "Ollama на компьютере", "http://192.168.1.100:11434/v1", "llama3.2",
            "своя модель в домашней сети, ключ не нужен", false
        ),
        Provider(
            "custom", "свой адрес", "https://example.com/v1", "model-name",
            "любой совместимый сервер: адрес и модель", true
        )
    )

    data class Config(
        val provider: String = "offline",
        val baseUrl: String = "",
        val model: String = "",
        val key: String = "",
        val temperature: Int = 60,
        val maxTokens: Int = 600
    ) {
        val info: Provider get() = PROVIDERS.firstOrNull { it.id == provider } ?: PROVIDERS.first()
        val isOffline: Boolean get() = provider == "offline"
        val address: String get() = baseUrl.ifBlank { info.baseUrl }.trimEnd('/')
        val modelName: String get() = model.ifBlank { info.model }
        val ready: Boolean get() = isOffline || (address.isNotBlank() && modelName.isNotBlank() &&
            (!info.needsKey || key.isNotBlank()))
    }

    private const val PREFS = "gsynk_ai"

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Config(
            provider = prefs.getString("provider", "offline") ?: "offline",
            baseUrl = prefs.getString("base", "") ?: "",
            model = prefs.getString("model", "") ?: "",
            key = prefs.getString("key", "") ?: "",
            temperature = prefs.getInt("temp", 60),
            maxTokens = prefs.getInt("tokens", 600)
        )
    }

    fun save(context: Context, config: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", config.provider)
            .putString("base", config.baseUrl)
            .putString("model", config.model)
            .putString("key", config.key)
            .putInt("temp", config.temperature.coerceIn(0, 100))
            .putInt("tokens", config.maxTokens.coerceIn(64, 4000))
            .apply()
    }

    /** Короткое человеческое описание: что сейчас используется. */
    fun describe(config: Config): String =
        if (config.isOffline) "офлайн-помощник GSYNK" else config.info.title + " · " + config.modelName

    /** Системная подсказка: помощник знает, что он живёт внутри GSYNK. */
    const val SYSTEM = "Ты — GSYNK, помощник внутри Android-приложения. Отвечай коротко, по-русски, " +
        "без выдуманных фактов. Если чего-то не знаешь — скажи прямо."

    /** Один запрос к сети. Возвращает текст ответа или сообщение об ошибке. */
    suspend fun ask(
        config: Config,
        prompt: String,
        history: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        if (config.isOffline) return@withContext "офлайн-режим: включите сеть в настройках ИИ"
        if (!config.ready) return@withContext "не хватает настроек: адрес, модель или ключ"
        runCatching {
            val body = buildJsonObject {
                put("model", JsonPrimitive(config.modelName))
                put("temperature", JsonPrimitive(config.temperature / 100.0))
                put("max_tokens", JsonPrimitive(config.maxTokens))
                put("messages", buildJsonArray {
                    add(message("system", SYSTEM))
                    history.takeLast(8).forEach { (role, text) -> add(message(role, text)) }
                    add(message("user", prompt))
                })
            }.toString()

            val connection = (URL(config.address + "/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (config.key.isNotBlank()) setRequestProperty("Authorization", "Bearer " + config.key)
                setRequestProperty("User-Agent", "GSYNK/" + Build_version())
                // OpenRouter просит указывать источник — иначе часть бесплатных моделей отказывает.
                setRequestProperty("HTTP-Referer", "https://github.com/GrapeGG-developer/GSYNKAPP")
                setRequestProperty("X-Title", "GSYNK")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            connection.disconnect()
            if (code !in 200..299) {
                "сеть ответила " + code + ": " + text.take(180)
            } else {
                extract(text) ?: "сеть ответила, но ответ пустой"
            }
        }.getOrElse { error ->
            "не получилось: " + (error.message ?: error.javaClass.simpleName)
        }
    }

    private fun Build_version(): String = runCatching { com.gsynk.app.BuildConfig.VERSION_NAME }.getOrDefault("4.0")

    private fun message(role: String, text: String): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(role))
        put("content", JsonPrimitive(text))
    }

    /** Разбор ответа: OpenAI-совместимый формат, плюс запасные варианты. */
    fun extract(raw: String): String? {
        val json = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val obj = json as? JsonObject ?: return null
        (obj["choices"] as? JsonArray)?.firstOrNull()?.let { choice ->
            val choiceObj = choice as? JsonObject ?: return@let
            (choiceObj["message"] as? JsonObject)?.get("content")?.jsonPrimitive?.content?.let { return it.trim() }
            choiceObj["text"]?.jsonPrimitive?.content?.let { return it.trim() }
        }
        (obj["content"] as? JsonArray)?.firstOrNull()?.let { part ->
            (part as? JsonObject)?.get("text")?.jsonPrimitive?.content?.let { return it.trim() }
        }
        obj["response"]?.jsonPrimitive?.content?.let { return it.trim() }
        obj["error"]?.let { error ->
            val text = (error as? JsonObject)?.get("message")?.jsonPrimitive?.content
            if (text != null) return "сеть ругается: " + text
        }
        return null
    }

    /** Быстрая проверка связи: спрашиваем у сети один короткий ответ. */
    suspend fun ping(config: Config): String =
        ask(config, "Ответь одним словом: на связи")
}
