package fit.silent.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space

/** Shown before sending the user to Android's notification access screen (Google Play prominent-disclosure rule). */
@Composable
fun NotificationAccessDisclosure(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val graph = LocalGraph.current
    Card(modifier) {
        Txt("Notification access", Nd.type.subheading, color = Nd.colors.textDisplay)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "SilentFit reads the notifications on this phone, including the app name, title and text, and who is calling. " +
                "It sends them only to your paired watch over Bluetooth so they appear on your wrist, and reads the media " +
                "session so the watch can control music. Notification content is not stored and never leaves the phone " +
                "except to your watch.",
            Nd.type.bodySm, color = Nd.colors.textSecondary,
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Agree and continue", { graph.phone.openNotificationAccess() })
            if (onDismiss != null) NdButton("Not now", onDismiss, style = ButtonStyle.GHOST)
        }
    }
}
