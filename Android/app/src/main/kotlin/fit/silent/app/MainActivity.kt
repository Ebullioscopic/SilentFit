package fit.silent.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.silent.app.phone.PhoneServices
import fit.silent.app.ui.AppRoot
import fit.silent.app.ui.ImportRequests
import fit.silent.app.ui.theme.NdTheme

class MainActivity : ComponentActivity() {
    private val graph get() = (application as SilentFitApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            val settings by graph.settings.flow.collectAsStateWithLifecycle()
            NdTheme(settings.themeMode) { AppRoot(graph) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            PhoneServices.ACTION_FOUND_PHONE -> graph.session.stopFindingPhone()
            Intent.ACTION_VIEW -> intent.data?.let { ImportRequests.offer(it) }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { ImportRequests.offer(it) }
        }
    }
}
