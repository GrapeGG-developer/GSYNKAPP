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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Если приложение открыли касанием метки — сразу показываем Grape Link
        PendingNfcPayload.capture(intent)
        setContent {
            GSYNKTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val vm: MainViewModel = viewModel()
                    MainScreen(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
