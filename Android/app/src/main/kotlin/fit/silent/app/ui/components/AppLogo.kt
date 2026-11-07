package fit.silent.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.silent.app.R
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.theme.Nd
import fit.silent.app.watch.LinkPhase

/** Which mark the app shows: the SF watch at rest, a link while connecting, a pulse once connected, a network while moving data. */
enum class LogoState(@DrawableRes val dark: Int, @DrawableRes val light: Int) {
    IDLE(R.drawable.logo_sf_dark, R.drawable.logo_sf_light),
    LINKING(R.drawable.logo_link_dark, R.drawable.logo_link_light),
    CONNECTED(R.drawable.logo_pulse_dark, R.drawable.logo_pulse_light),
    SYNCING(R.drawable.logo_network_dark, R.drawable.logo_network_light),
}

@Composable
fun AppLogo(state: LogoState, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val dark = Nd.colors.isDark
    Crossfade(state, modifier, animationSpec = tween(400), label = "logo") { s ->
        Image(painterResource(if (dark) s.dark else s.light), contentDescription = "SilentFit", modifier = Modifier.size(size))
    }
}

/** The logo state that follows the watch link. */
@Composable
fun watchLogoState(): LogoState {
    val session = LocalGraph.current.session
    val connection by session.connection.collectAsStateWithLifecycle()
    val sync by session.sync.collectAsStateWithLifecycle()
    val transfer by session.transfer.collectAsStateWithLifecycle()
    return when {
        connection.isConnected && (sync.running || transfer != null) -> LogoState.SYNCING
        connection.isConnected -> LogoState.CONNECTED
        connection.phase == LinkPhase.CONNECTING || connection.phase == LinkPhase.HANDSHAKE || connection.phase == LinkPhase.PAIRING -> LogoState.LINKING
        else -> LogoState.IDLE
    }
}
