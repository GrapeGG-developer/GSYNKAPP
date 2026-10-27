package com.gsynk.app.core

import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32

/** Низкоуровневый вывод байтов и миниатюрный виртуальный процессор для лаборатории GSYNK. */
object LowLevel {

    data class CpuSnapshot(
        val ip: Int,
        val a: Int,
        val b: Int,
        val c: Int,
        val carry: Boolean,
        val zero: Boolean,
        val instruction: String
    )

    fun hexDump(input: ByteArray, offsetWidth: Int = 4): String {
        if (input.isEmpty()) return "0000  -- пусто --"
        return input.toList().chunked(16).mapIndexed { row, chunk ->
            val offset = (row * 16).toString(16).uppercase().padStart(offsetWidth, '0')
            val hex = chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            val ascii = chunk.joinToString("") {
                val value = it.toInt() and 0xFF
                if (value in 32..126) value.toChar().toString() else "·"
            }
            "$offset  ${hex.padEnd(47)}  |$ascii|"
        }.joinToString("\n")
    }

    fun binaryDump(input: ByteArray): String {
        if (input.isEmpty()) return "пусто"
        return input.mapIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            "%04X  %s  0x%02X  %3d".format(index, value.toString(2).padStart(8, '0'), value, value)
        }.joinToString("\n")
    }

    fun endianView(input: ByteArray): String {
        if (input.isEmpty()) return "нет байтов для endian-представления"
        val padded = ByteArray(((input.size + 3) / 4) * 4)
        input.copyInto(padded)
        return padded.toList().chunked(4).mapIndexed { index, group ->
            val be = group.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
            val le = group.reversed().fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
            val bytes = group.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            "%04X  [%s]  BE=%08X (%d)  LE=%08X (%d)".format(index * 4, bytes, be, be, le, le)
        }.joinToString("\n")
    }

    fun checksums(input: ByteArray): String {
        val md5 = MessageDigest.getInstance("MD5").digest(input)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(input)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(input)
        val crc = CRC32().also { it.update(input) }
        val adler = Adler32().also { it.update(input) }
        val xor = input.fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
        val sum8 = input.fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }
        return buildString {
            appendLine("байтов: " + input.size)
            appendLine("бит: " + input.size * 8)
            appendLine("единиц в битах: " + input.sumOf { Integer.bitCount(it.toInt() and 0xFF) })
            appendLine("XOR8: 0x%02X".format(xor))
            appendLine("SUM8: 0x%02X".format(sum8))
            appendLine("CRC32: 0x%08X".format(crc.value))
            appendLine("Adler32: 0x%08X".format(adler.value))
            appendLine("MD5: " + md5.hex())
            appendLine("SHA-1: " + sha1.hex())
            appendLine("SHA-256: " + sha256.hex())
        }.trimEnd()
    }

    /** Детерминированная трасса: каждый байт пропускается через A/B/C, XOR, ADD и циклический сдвиг. */
    fun traceCpu(input: ByteArray, maxSteps: Int = 64): List<CpuSnapshot> {
        if (input.isEmpty()) return listOf(CpuSnapshot(0, 0, 0, 0, carry = false, zero = true, instruction = "HALT ; нет данных"))
        var a = 0
        var b = 0x5A
        var c = 0
        var carry = false
        val out = ArrayList<CpuSnapshot>()
        input.take(maxSteps).forEachIndexed { index, raw ->
            val value = raw.toInt() and 0xFF
            val instruction: String
            when (index % 4) {
                0 -> { a = value; instruction = "LOAD A, [MEM %04X]".format(index) }
                1 -> { b = b xor value; instruction = "XOR B, 0x%02X".format(value) }
                2 -> {
                    val sum = a + value
                    carry = sum > 0xFF
                    a = sum and 0xFF
                    instruction = "ADD A, 0x%02X".format(value)
                }
                else -> {
                    c = ((a shl 1) or (a ushr 7)) and 0xFF
                    instruction = "ROL C, A ; MEM=0x%02X".format(value)
                }
            }
            out += CpuSnapshot(
                ip = index + 1,
                a = a,
                b = b,
                c = c,
                carry = carry,
                zero = a == 0,
                instruction = instruction
            )
        }
        out += CpuSnapshot(input.size, a, b, c, carry, a == 0, "HALT")
        return out
    }

    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
