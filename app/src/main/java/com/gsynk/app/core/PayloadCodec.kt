package com.gsynk.app.core

import java.util.UUID

/**
 * Компактное кодирование правил для обмена по NFC.
 *
 * Формат строки: `GSYNK1;имя,час,минута,дни,ярк,звук,флаги;...`
 * Флаги — по одному символу: b — яркость, v — звук, e — включено.
 * Идентификаторы не переносятся: на принимающем телефоне правила получают новые,
 * чтобы не столкнуться с уже существующими.
 */
object PayloadCodec {

    private const val VERSION = "GSYNK1"
    private const val MAX_RULES = 8
    private const val MAX_NAME = 18

    fun encodeRules(rules: List<Rule>): String {
        val encoded = rules.take(MAX_RULES).joinToString(";") { rule ->
            val days = rule.days.joinToString("-")
            val flags = buildString {
                if (rule.useBrightness) append('b')
                if (rule.useVolume) append('v')
                if (rule.enabled) append('e')
            }
            listOf(
                rule.name.replace(',', ' ').replace(';', ' ').take(MAX_NAME),
                rule.hour.toString(),
                rule.minute.toString(),
                days.ifBlank { "-" },
                rule.brightness.toString(),
                rule.volume.toString(),
                flags.ifBlank { "-" }
            ).joinToString(",")
        }
        return "$VERSION;$encoded"
    }

    fun decodeRules(payload: String): List<Rule> {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(VERSION)) return emptyList()
        val body = trimmed.removePrefix(VERSION).trimStart(';')
        if (body.isBlank()) return emptyList()
        return body.split(";").mapNotNull { chunk ->
            runCatching {
                val parts = chunk.split(",")
                if (parts.size < 6) return@runCatching null
                val flags = parts.getOrNull(6).orEmpty()
                Rule(
                    id = UUID.randomUUID().toString(),
                    name = parts[0].ifBlank { "Правило из GSYNK" },
                    hour = parts[1].toInt().coerceIn(0, 23),
                    minute = parts[2].toInt().coerceIn(0, 59),
                    days = parts[3].split("-").mapNotNull { it.toIntOrNull() }.filter { it in 1..7 },
                    brightness = parts[4].toInt().coerceIn(0, 100),
                    volume = parts[5].toInt().coerceIn(0, 100),
                    useBrightness = flags.contains('b') || flags == "-",
                    useVolume = flags.contains('v') || flags == "-",
                    enabled = flags.contains('e') || flags == "-"
                )
            }.getOrNull()
        }
    }
}
