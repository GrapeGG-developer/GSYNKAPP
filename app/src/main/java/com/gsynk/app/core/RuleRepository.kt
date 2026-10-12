package com.gsynk.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "gsynk_store")

/**
 * Локальное хранилище правил и настроек (DataStore + JSON).
 *
 * Совместимость: правила из версий 1.0/1.1 читаются этим же кодеком —
 * новые поля (точные даты, интервал, положение айленда) имеют значения по умолчанию,
 * поэтому при обновлении приложения ничего не теряется.
 */
class RuleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val rulesKey = stringPreferencesKey("rules")
    private val settingsKey = stringPreferencesKey("settings")

    val rules: Flow<List<Rule>> = appContext.dataStore.data.map { prefs ->
        val raw = prefs[rulesKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(ListSerializer(Rule.serializer()), raw) }
            .getOrElse { emptyList() }
    }

    val settings: Flow<AppSettings> = appContext.dataStore.data.map { prefs ->
        val raw = prefs[settingsKey] ?: return@map AppSettings()
        runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrElse { AppSettings() }
    }

    suspend fun currentRules(): List<Rule> = rules.first()

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveRules(list: List<Rule>) {
        // значения всё равно проверяем ещё раз: в хранилище не должен попасть мусор
        val clean = list.map { rule ->
            rule.copy(
                hour = rule.hour.coerceIn(0, 23),
                minute = rule.minute.coerceIn(0, 59),
                brightness = rule.brightness.coerceIn(0, 100),
                volume = rule.volume.coerceIn(0, 100),
                days = rule.days.filter { it in 1..7 }.distinct().sorted(),
                dates = rule.dates
                    .filter { runCatching { LocalDate.parse(it) }.isSuccess }
                    .distinct()
                    .sorted(),
                intervalHours = rule.intervalHours.coerceIn(0, 23),
                intervalMinutes = rule.intervalMinutes.coerceIn(0, 59),
                intervalStartHour = rule.intervalStartHour.coerceIn(0, 23),
                intervalStartMinute = rule.intervalStartMinute.coerceIn(0, 59)
            )
        }
        appContext.dataStore.edit { prefs ->
            prefs[rulesKey] = json.encodeToString(ListSerializer(Rule.serializer()), clean)
        }
    }

    suspend fun saveSettings(value: AppSettings) {
        appContext.dataStore.edit { prefs ->
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), value)
        }
    }

    /** Создаёт правило, если в хранилище ещё нет ни одного. */
    fun newRule(): Rule = Rule(id = UUID.randomUUID().toString())
}
