package fit.silent.app.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.Routes
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Card
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Hero
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NavRow
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.PageTitle
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedBar
import fit.silent.app.ui.components.SegmentedSpinner
import fit.silent.app.ui.components.StatRow
import fit.silent.app.ui.components.StatusDot
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.watch.LinkPhase
import fit.silent.core.model.ChargeState
import androidx.compose.ui.platform.LocalContext

@Composable
fun WatchScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val connection by graph.session.connection.collectAsStateWithLifecycle()
    val watch by graph.session.watch.collectAsStateWithLifecycle()
    val battery by graph.session.battery.collectAsStateWithLifecycle()
    val ws by graph.session.watchSettings.collectAsStateWithLifecycle()
    val c = Nd.colors

    Page {
        item { PageTitle(settings.pairedWatch?.name ?: "Watch", eyebrow = "Device") }
        if (settings.pairedWatch == null) {
            item {
                EmptyState("No watch paired", "Pair your Noise watch in Android Bluetooth settings, then choose it here.") {
                    NdButton("Choose watch", { nav.navigate("pair") }, icon = R.drawable.ic_bluetooth)
                }
            }
            return@Page
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Label("Battery")
                    Hero(battery?.level?.toString() ?: "--", unit = "%", color = if ((battery?.level ?: 100) <= 15) c.accent else c.textDisplay)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (connection.isConnected) c.success else if (connection.isBusy) c.warning else c.accent)
                        Spacer(Modifier.width(Space.sm))
                        Label(connection.phase.name.lowercase().replaceFirstChar { it.uppercase() }, color = c.textPrimary)
                    }
                    if (battery?.state == ChargeState.CHARGING) Label("Charging")
                }
            }
            Spacer(Modifier.height(Space.md))
            SegmentedBar((battery?.level ?: 0) / 100f, segments = 20, color = if ((battery?.level ?: 100) <= 15) c.accent else c.textDisplay)
            if (!connection.isConnected) {
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (connection.isBusy) { SegmentedSpinner(size = 14.dp); Spacer(Modifier.width(Space.sm)) }
                    InlineStatus(connection.message, color = if (connection.phase == LinkPhase.FAILED) c.accent else c.textSecondary)
                }
            }
        }
        item {
            FlowRow(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (connection.isConnected) {
                    NdButton("Find watch", { graph.session.findWatch() }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_vibrate)
                    NdButton("Sync time", { graph.session.syncTime() }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_clock)
                    NdButton("Test alert", { graph.session.sendTestNotification() }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_bell)
                } else if (!connection.isBusy) {
                    NdButton("Connect", { graph.settings.update { it.copy(keepConnected = true) }; (context.applicationContext as SilentFitApp).ensureLink() }, icon = R.drawable.ic_link)
                    if (connection.phase == LinkPhase.FAILED && connection.message.contains("account")) {
                        NdButton("Pair again", { graph.session.requestRebind() }, style = ButtonStyle.SECONDARY)
                    }
                }
            }
        }
        item {
            Section("Health") {
                NavRow("Monitoring", subtitle = listOfNotNull(
                    ws.heartRate?.let { if (it.enabled) "HR every ${it.intervalMinutes}m" else "HR off" },
                    ws.spo2?.let { if (it.enabled) "SpO₂ on" else "SpO₂ off" },
                ).joinToString(" · ").ifEmpty { null }, icon = R.drawable.ic_heart, enabled = connection.isConnected) { nav.navigate("watch/health") }
                NavRow("Reminders", subtitle = "Move, hydrate, medication", icon = R.drawable.ic_bell, enabled = connection.isConnected) { nav.navigate("watch/reminders") }
                NavRow("Alarms", value = ws.alarms?.count { it.enabled }?.let { "$it on" }, icon = R.drawable.ic_alarm_clock, enabled = connection.isConnected) { nav.navigate("watch/alarms") }
            }
        }
        item {
            Section("Watch") {
                NavRow("Faces", subtitle = "Designs, presets and imports", icon = R.drawable.ic_watch) { nav.navigate(Routes.FACES) }
                NavRow("Display & wake", subtitle = "Brightness, always-on, raise to wake, sleep mode", icon = R.drawable.ic_sun, enabled = connection.isConnected) { nav.navigate("watch/display") }
                NavRow("Apps & widgets", subtitle = "Order and visibility on the watch", icon = R.drawable.ic_layout_grid, enabled = connection.isConnected && ws.launcher.isNotEmpty()) { nav.navigate("watch/launcher") }
            }
        }
        item {
            Section("Phone") {
                NavRow("Notifications", subtitle = "What reaches your wrist", icon = R.drawable.ic_message_square) { nav.navigate("watch/notifications") }
                NavRow("Quick replies", value = settings.quickReplies.size.toString(), icon = R.drawable.ic_type) { nav.navigate("watch/replies") }
            }
        }
        item {
            Section("About this watch") {
                StatRow("Model", watch.model.ifEmpty { watch.deviceType.takeIf { it > 0 }?.toString() ?: "--" })
                StatRow("Firmware", watch.firmware.ifEmpty { "--" })
                StatRow("Address", settings.pairedWatch?.address ?: "--")
                if (watch.serial.isNotEmpty()) StatRow("Serial", watch.serial)
                if (watch.platformType > 0) StatRow("Platform", "Bluetrum · ${watch.platformType}")
            }
        }
        item {
            Card(Modifier.padding(top = Space.xl)) {
                Txt("Connection", Nd.type.subheading, color = c.textDisplay)
                Spacer(Modifier.height(Space.sm))
                Txt("Disconnecting stops notifications and background sync until you connect again.", Nd.type.bodySm, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    NdButton("Disconnect", { graph.settings.update { it.copy(keepConnected = false) }; graph.session.disconnect() }, style = ButtonStyle.SECONDARY, enabled = connection.isConnected || connection.isBusy)
                    NdButton("Forget", { graph.session.forget() }, style = ButtonStyle.DESTRUCTIVE)
                }
            }
        }
    }
}
