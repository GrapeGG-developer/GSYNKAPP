package com.gsynk.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Ловит сработавший будильник и запускает службу применения правила. */
class ApplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RuleScheduler.ACTION_FIRE) return
        val ruleId = intent.getStringExtra(RuleScheduler.EXTRA_RULE_ID) ?: return
        ApplyService.start(context.applicationContext, ruleId)
    }
}
