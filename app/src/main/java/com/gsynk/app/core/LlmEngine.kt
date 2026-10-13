package com.gsynk.app.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Локальная языковая модель на устройстве (Gemma через MediaPipe LLM Inference).
 *
 * Это не заглушка: модель реально исполняется на телефоне, без интернета, аккаунтов
 * и лимитов. Файл модели (.task/.bin, обычно 0.3–2.5 ГБ) пользователь импортирует
 * один раз — в приложении лежит только движок.
 */
object LlmEngine {

    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    /** Системная подсказка: держим ответы короткими и по-русски. */
    private const val SYSTEM_PROMPT =
        "Ты встроенный ассистент GSYNK. Отвечай по-русски, кратко и по делу, без лишних вступлений.\n"

    val isLoaded: Boolean get() = engine != null

    fun loadedModelName(): String? = loadedPath?.substringAfterLast('/')

    /** Загружает модель в память. Возвращает текст ошибки или null при успехе. */
    suspend fun load(context: Context, file: File, threads: Int = 0): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (loadedPath == file.absolutePath && engine != null) return@runCatching null
            close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(1024)
                .setMaxTopK(40)
                .build()
            val created = LlmInference.createFromOptions(context.applicationContext, options)
            engine = created
            loadedPath = file.absolutePath
            null as String?
        }.getOrElse { error ->
            "Не удалось загрузить модель: " + (error.message ?: error.javaClass.simpleName)
        }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    /** Короткий вопрос-ответ: удобно для кнопок и подсказок. */
    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val model = engine ?: return@withContext "Модель не загружена — импортируйте файл .task в чате."
        runCatching { model.generateResponse(SYSTEM_PROMPT + prompt).trim() }
            .getOrElse { "Ошибка модели: " + (it.message ?: it.javaClass.simpleName) }
    }

    /**
     * Потоковая генерация: [onPart] получает накопленный текст по мере готовности —
     * так ответ появляется на экране сразу, а не целиком в конце.
     */
    suspend fun askStreaming(prompt: String, onPart: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val model = engine ?: return@withContext "Модель не загружена — импортируйте файл .task в чате."
        runCatching {
            val future = model.generateResponseAsync(
                SYSTEM_PROMPT + prompt,
                ProgressListener<String> { partial, _ ->
                    if (partial.isNotEmpty()) onPart(partial)
                }
            )
            future.get().orEmpty().trim().ifEmpty { "…" }
        }.getOrElse { "Ошибка модели: " + (it.message ?: it.javaClass.simpleName) }
    }

    /** Сколько токенов занимает текст — показываем в чате рядом с лимитом. */
    fun countTokens(text: String): Int = runCatching { engine?.sizeInTokens(text) ?: 0 }.getOrDefault(0)
}
