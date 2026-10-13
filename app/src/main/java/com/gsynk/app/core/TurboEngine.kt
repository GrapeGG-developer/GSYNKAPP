package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Состояние нагрузки: сюда пишет вычислитель, отсюда читают экран и фон.
 *
 * Фон подписан на [active] и [progress]: пока нагрузка идёт, градиент циклично
 * переливается, а скорость переливания задаёт нейросеть.
 */
object TurboState {

    /** Нагрузка включена. */
    val active = MutableStateFlow(false)

    /** Достигнутая скорость счёта на процессоре, миллиардов операций в секунду. */
    val cpuGflops = MutableStateFlow(0f)

    /** Кадров в секунду, которые вытягивает видеоускоритель. */
    val gpuFps = MutableStateFlow(0f)

    /** Индекс процветания: сколько всего операций посчитано. */
    val totalOperations = MutableStateFlow(0L)

    /** Выход нейросети: 0 спокойный, 1 энергичный, 2 ночной. */
    val aiMode = MutableStateFlow(RealtimeAi.MODE_CALM)
    val aiConfidence = MutableStateFlow(0f)
    val aiLoss = MutableStateFlow(0f)

    /** Фаза переливания фона, 0..1 — её крутит ИИ. */
    val progress = MutableStateFlow(0f)

    /** Температура батареи в градусах, чтобы не перегреть телефон. */
    val batteryTemp = MutableStateFlow(0f)

    fun reset() {
        cpuGflops.value = 0f
        gpuFps.value = 0f
        totalOperations.value = 0L
        progress.value = 0f
    }
}

/**
 * Вычислитель «турбо»: грузит процессор на всех ядрах, крутит нейросеть и
 * подсказывает фону, как переливаться.
 *
 * Считает честно: перемножение матриц в float на каждом ядре, замер за секунду,
 * оценка в миллиардах операций. Плюс каждые 200 мс — прогон и шаг обучения сети.
 * Видеоускоритель грузит экранный шейдер (см. `GpuField`), его кадры попадают
 * сюда через [TurboState.gpuFps].
 */
class TurboEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var workers: Job? = null
    private var brain: Job? = null

    private val ai = RealtimeAi()
    private var frame = 0L
    private var heatStop = false

    val isRunning: Boolean get() = TurboState.active.value

    companion object {
        /** Порог защиты: выше этой температуры нагрузку выключаем сами. */
        const val HEAT_LIMIT_C = 45f
    }

    fun start() {
        if (TurboState.active.value) return
        heatStop = false
        TurboState.active.value = true
        TurboState.reset()
        AppLog.event("Турбо", "нагрузка включена: процессор и видеоускоритель")

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        workers = scope.launch {
            val jobs = (0 until cores).map { index ->
                launch(Dispatchers.Default) { hammer(index) }
            }
            jobs.forEach { it.join() }
        }

        brain = scope.launch {
            val calendar = Calendar.getInstance()
            while (isActive && TurboState.active.value) {
                val snapshot = runCatching { DeviceStats.snapshot(context) }.getOrNull()
                val memory = Runtime.getRuntime()
                val heapUsed = (memory.totalMemory() - memory.freeMemory()).toFloat() /
                    memory.maxMemory().toFloat()
                calendar.timeInMillis = System.currentTimeMillis()

                val features = floatArrayOf(
                    (TurboState.cpuGflops.value / 40f).coerceIn(0f, 1f),
                    (TurboState.gpuFps.value / 120f).coerceIn(0f, 1f),
                    ((snapshot?.batteryTempC?.toFloat() ?: 30f) / 60f).coerceIn(0f, 1f),
                    ((snapshot?.batteryPercent ?: 100).toFloat() / 100f).coerceIn(0f, 1f),
                    calendar.get(Calendar.HOUR_OF_DAY) / 24f,
                    heapUsed.coerceIn(0f, 1f),
                    (SystemClock.elapsedRealtime() / 3_600_000f / 24f).coerceIn(0f, 1f),
                    (snapshot?.cores ?: cores).toFloat() / 12f
                )

                val prediction = ai.learn(features, snapshot?.batteryPercent ?: 100)
                var best = 0
                var bestValue = prediction[0]
                for (i in prediction.indices) {
                    if (prediction[i] > bestValue) {
                        bestValue = prediction[i]
                        best = i
                    }
                }
                TurboState.aiMode.value = best
                TurboState.aiConfidence.value = prediction[best]
                TurboState.aiLoss.value = ai.lastLoss
                val temperature = snapshot?.batteryTempC?.toFloat() ?: 0f
                TurboState.batteryTemp.value = temperature

                // защита от перегрева: обещано в интерфейсе и должно работать
                if (temperature >= HEAT_LIMIT_C) {
                    AppLog.warn(
                        "Турбо",
                        "батарея нагрелась до ${temperature.toInt()} °C — выключаю нагрузку"
                    )
                    heatStop = true
                    break
                }

                // переливание фона: скорость зависит от режима ИИ,
                // поэтому картинка меняется вместе с поведением сети
                val speed = when (best) {
                    RealtimeAi.MODE_ACTIVE -> 0.055f
                    RealtimeAi.MODE_NIGHT -> 0.012f
                    else -> 0.030f
                }
                TurboState.progress.value = (TurboState.progress.value + speed) % 1f

                if (frame % 45L == 0L) {
                    val sure = (prediction[best] * 100).toInt()
                    val speedText = "%.1f".format(TurboState.cpuGflops.value)
                    AppLog.event(
                        "Турбо",
                        "режим ${RealtimeAi.modeName(best)} (уверенность $sure проц.), " +
                            "$speedText млрд оп/с, ${TurboState.gpuFps.value.toInt()} кадров/с"
                    )
                }
                frame++
                delay(200L)
            }
            if (heatStop) stop()
        }
    }

    fun stop() {
        if (!TurboState.active.value) return
        TurboState.active.value = false
        workers?.cancel()
        brain?.cancel()
        workers = null
        brain = null
        TurboState.reset()
        AppLog.event("Турбо", "нагрузка выключена, сеть обучена на ${ai.steps} прогонах")
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /** Фоновый счёт: перемножение матриц 64×64 во float. */
    private suspend fun hammer(index: Int) = withContext(Dispatchers.Default) {
        val size = 64
        val left = FloatArray(size * size) { (it % 17) * 0.5f + index }
        val right = FloatArray(size * size) { (it % 13) * 0.7f + index }
        val result = FloatArray(size * size)
        var operations = 0L
        var elapsed = 0f

        while (isActive && TurboState.active.value) {
            val started = System.nanoTime()
            for (row in 0 until size) {
                val rowOffset = row * size
                for (column in 0 until size) {
                    var sum = 0f
                    for (k in 0 until size) sum += left[rowOffset + k] * right[k * size + column]
                    result[rowOffset + column] = sum
                }
            }
            operations += 2L * size * size * size
            elapsed += (System.nanoTime() - started) / 1_000_000_000f

            if (elapsed >= 0.5f) {
                val gflops: Float = operations.toFloat() / elapsed / 1_000_000_000f
                TurboState.cpuGflops.value = kotlin.math.max(TurboState.cpuGflops.value, gflops)
                TurboState.totalOperations.value += operations
                operations = 0L
                elapsed = 0f
                // небольшая пауза, чтобы система не убила приложение как «зависшее»
                delay(2L)
            }
        }
    }
}

/** Короткая сводка о железе: пригодится и на экране нагрузки, и в отчётах. */
object ChipInfo {

    fun model(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else {
            null
        }
    }.getOrNull() ?: runCatching {
        Build.HARDWARE.replace('_', ' ').takeIf { it.isNotBlank() }
    }.getOrNull() ?: "не определён"

    fun vendor(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else {
            null
        }
    }.getOrNull() ?: Build.MANUFACTURER

    fun summary(context: Context): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val temp = battery?.let { BatteryTemp.read(it) } ?: 0f
        return "${vendor()} ${model()} · ядер: $cores · батарея ${temp.toInt()}°C"
    }
}

/** Температура батареи в градусах Цельсия. */
object BatteryTemp {
    fun read(intent: Intent): Float = runCatching {
        val tenths = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
        tenths / 10f
    }.getOrDefault(0f)
}
