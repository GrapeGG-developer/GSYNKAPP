package com.gsynk.app.ui

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsynk.app.core.NfcLink
import com.gsynk.app.ui.theme.GSYNKTheme

class MainActivity : ComponentActivity() {

    /** Нажали уведомление — открываем панель обновления через общий роутер. */
    private fun openUpdateIfAsked(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) != true) return
        com.gsynk.app.ui.Router.go("panel:update")
    }

    companion object {
        /**
         * Уведомление о новой версии приходит из фоновой проверки: этим ключом
         * приложение понимает, что человек нажал именно «обновить».
         */
        const val EXTRA_OPEN_UPDATE = "gsynk_open_update"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdateIfAsked(intent)
        enableEdgeToEdge()
        // Если приложение открыли касанием метки — сразу показываем Grape Link
        PendingNfcPayload.capture(intent)
        // «Каждый день»: серия визитов и связанные с ней пасхалки считаются один раз при запуске.
        com.gsynk.app.ui.checkDailyAchievements(this)
        setContent {
            GSYNKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val vm: MainViewModel = viewModel()
                    // GIOUI: сетка, уровень эффектов и скругления приходят из одного места.
                    ProvideGioui(this) {
                        MainScreen(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openUpdateIfAsked(intent)
        setIntent(intent)
        PendingNfcPayload.capture(intent)
    }
}

/**
 * Мостик между системным касанием метки и экраном Grape Link.
 *
 * Android отдаёт NDEF-сообщение в активность, а не в наш экран, поэтому
 * разобранные данные ждут здесь, пока Compose-экран их заберёт.
 */
object PendingNfcPayload {

    @Volatile
    var payload: NfcLink.Payload? = null
        private set

    fun capture(intent: Intent?) {
        payload = null
        val raw = intent ?: return
        @Suppress("DEPRECATION")
        val ndef = raw.getParcelableExtra<android.nfc.NdefMessage>(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?: return
        payload = NfcLink.parse(ndef)
    }

    fun take(): NfcLink.Payload? {
        val current = payload
        payload = null
        return current
    }
}
