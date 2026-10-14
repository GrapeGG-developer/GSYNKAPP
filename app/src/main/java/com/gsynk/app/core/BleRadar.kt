package com.gsynk.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.pow

/**
 * Радар Bluetooth: кто рядом и насколько близко.
 *
 * Сканирует эфир (в том числе BLE) и показывает устройства по силе сигнала — так можно
 * найти свой потерянный трекер или понять, сколько чужих телефонов вокруг. Расстояние
 * считается по уровню сигнала: это оценка, а не измерение, но для «ближе-дальше» её
 * хватает.
 */
object BleRadar {

    data class Found(
        val address: String,
        val name: String,
        val rssi: Int,
        val meters: Double,
        val seen: Int
    ) {
        val label: String get() = name.ifBlank { address }
        /** Полоска силы: 0 — очень далеко, 100 — вплотную. */
        val strength: Int get() = rssiToStrength(rssi)
    }

    private val _devices = MutableStateFlow<List<Found>>(emptyList())
    val devices: StateFlow<List<Found>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    @Volatile private var adapter: BluetoothAdapter? = null
    private val counters = HashMap<String, Int>()

    /** Оценка расстояния по мощности сигнала: 1 метр на -60 дБм, дальше по кубам. */
    fun meters(rssi: Int): Double {
        val measured = 1.0                     // эталонный метр
        val reference = -60.0                  // типичный уровень на метре
        val exponent = 2.2                     // потеря в помещении
        val power = (reference - rssi) / (10.0 * exponent)
        return (measured * 10.0.pow(power)).coerceIn(0.05, 99.0)
    }

    /** Сила сигнала в процентах шкалы — для полоски на экране. */
    fun rssiToStrength(rssi: Int): Int =
        ((rssi + 100) * 100 / 60).coerceIn(0, 100)

    /** Насколько точно устройство опознано: имя есть — хорошо, только адрес — так себе. */
    fun quality(found: Found): String = when {
        found.name.isNotBlank() && found.meters < 2.0 -> "вплотную"
        found.name.isNotBlank() -> "рядом"
        found.seen > 3 -> "устойчиво виден"
        else -> "мелькнуло"
    }

    fun permissionFor(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun canScan(context: Context): Boolean = permissionFor().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (!canScan(context)) return false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val current = manager?.adapter ?: return false
        if (!current.isEnabled) return false
        adapter = current
        counters.clear()
        _devices.value = emptyList()
        _scanning.value = true
        val scanner = current.bluetoothLeScanner
            ?: current.bluetoothLeScannerCompat()
            ?: return false
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { add(it.device.address, runCatching { it.device.name }.getOrNull().orEmpty(), it.rssi) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    add(result.device.address, runCatching { result.device.name }.getOrNull().orEmpty(), result.rssi)
                }
            }
        }
        scanCallback = callback
        return runCatching {
            scanner.startScan(null, settings, callback)
            AppLog.event("Радар", "сканирование эфира запущено")
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        _scanning.value = false
        val callback = scanCallback ?: return
        scanCallback = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        AppLog.event("Радар", "сканирование остановлено")
    }

    @Volatile private var scanCallback: ScanCallback? = null

    private fun add(address: String, name: String, rssi: Int) {
        val seen = (counters[address] ?: 0) + 1
        counters[address] = seen
        val found = Found(address, name, rssi, meters(rssi), seen)
        _devices.update { current ->
            (current.filterNot { it.address == address } + found)
                .sortedWith(compareByDescending<Found> { it.rssi }.thenBy { it.label.lowercase() })
                .take(40)
        }
    }

    fun reset() {
        _devices.value = emptyList()
        counters.clear()
    }

    /** Сколько устройств ближе метра — для уведомления «кто-то рядом». */
    fun veryClose(): Int = _devices.value.count { device -> device.meters < 2.0 && abs(device.rssi) < 75 }

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.bluetoothLeScannerCompat(): android.bluetooth.le.BluetoothLeScanner? =
        runCatching { bluetoothLeScanner }.getOrNull()
}
