package com.gsynk.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameStore by preferencesDataStore(name = "gsynk_game")

/** Рекорды мини-игр. */
class GameScores(context: Context) {

    private val appContext = context.applicationContext
    private val bestReaction = intPreferencesKey("best_reaction_ms")
    private val bestSnake = intPreferencesKey("best_snake")
    private val duelWins = intPreferencesKey("duel_wins")

    val reaction: Flow<Int> = appContext.gameStore.data.map { it[bestReaction] ?: 0 }
    val snake: Flow<Int> = appContext.gameStore.data.map { it[bestSnake] ?: 0 }
    val wins: Flow<Int> = appContext.gameStore.data.map { it[duelWins] ?: 0 }

    suspend fun saveReaction(ms: Int) {
        appContext.gameStore.edit { prefs ->
            val current = prefs[bestReaction] ?: 0
            if (current == 0 || ms < current) prefs[bestReaction] = ms
        }
    }

    suspend fun saveSnake(score: Int) {
        appContext.gameStore.edit { prefs ->
            val current = prefs[bestSnake] ?: 0
            if (score > current) prefs[bestSnake] = score
        }
    }
}
