package com.gsynk.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Совместимость со старыми будильниками: расписание вынесено в GAUTO, поэтому
 * этот приёмник намеренно ничего не делает. Новые будильники GSYNK не ставит.
 */
class ApplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Unit
}
