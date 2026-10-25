package fit.silent.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.data.PairedWatch
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.FormPage
import fit.silent.app.ui.components.Icon
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.TextInput
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.components.dotGrid
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.watch.SppLink

/** First run: permissions → pick the bonded watch → optional account id. Also used for re-pairing. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, pairOnly: Boolean = false) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(if (pairOnly) 1 else 0) }
    var refresh by remember { mutableIntStateOf(0) }
    var accountId by remember { mutableStateOf(settings.accountId) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val c = Nd.colors

    FormPage {
        Box(Modifier.fillMaxWidth().height(96.dp).padding(top = Space.xl).dotGrid(c.borderVisible, 14.dp, 1.5.dp))
        when (step) {
            0 -> Column {
                Spacer(Modifier.height(Space.xxl))
                Txt("SILENT", Nd.type.displayXl, color = c.textDisplay)
                Txt("FIT", Nd.type.displayXl, color = c.textDisplay)
                Spacer(Modifier.height(Space.md))
                Txt("Your Noise watch, without the cloud. Health data, notifications and custom faces — all on this phone.", Nd.type.body, color = c.textSecondary)
                Section("Permissions") {
                    Perm(R.drawable.ic_bluetooth, "Nearby devices", "Talk to your watch over Bluetooth")
                    Perm(R.drawable.ic_bell, "Notifications", "Show the connection and find-phone alerts")
                    Perm(R.drawable.ic_phone, "Phone & SMS", "Answer calls and send quick replies from the watch")
                }
                Spacer(Modifier.height(Space.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    NdButton("Continue", {
                        permissions.launch(graph.phone.runtimePermissions().toTypedArray())
                        step = 1
                    })
                    NdButton("Try demo", {
                        graph.session.setDemoMode(true)
                        graph.settings.update { it.copy(onboarded = true) }
                        onDone()
                    }, style = ButtonStyle.GHOST)
                }
            }
            else -> Column {
                Spacer(Modifier.height(Space.xl))
                Txt("Choose your watch", Nd.type.displayMd, color = c.textDisplay)
                Spacer(Modifier.height(Space.sm))
                Txt("Pair it in Android's Bluetooth settings first (it appears as \"NF 2_XXXX\"), then pick it here.", Nd.type.bodySm, color = c.textSecondary)
                val devices = remember(refresh) { SppLink.bonded(context) }
                LaunchedEffect(Unit) { refresh++ }
                Section("Paired devices", trailing = { NdButton("Refresh", { refresh++ }, style = ButtonStyle.GHOST) }) {
                    if (!graph.phone.hasBluetoothPermission) {
                        InlineStatus("Nearby devices permission needed", color = c.warning)
                        Spacer(Modifier.height(Space.sm))
                        NdButton("Allow", { permissions.launch(graph.phone.runtimePermissions().toTypedArray()) }, style = ButtonStyle.SECONDARY)
                    } else if (devices.isEmpty()) {
                        Txt("No paired devices.", Nd.type.bodySm, color = c.textDisabled)
                    }
                    devices.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                graph.settings.update { it.copy(accountId = accountId.trim().ifEmpty { it.accountId }, onboarded = true, keepConnected = true, demoMode = false) }
                                graph.session.pair(PairedWatch(d.name, d.address))
                                (context.applicationContext as SilentFitApp).ensureLink()
                                onDone()
                            }.padding(vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(R.drawable.ic_watch, tint = if (d.looksLikeWatch) c.textDisplay else c.textDisabled)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Txt(d.name, Nd.type.body, maxLines = 1)
                                Label(d.address)
                            }
                            if (d.looksLikeWatch) Label("Watch", color = c.textPrimary)
                        }
                        Divider()
                    }
                    Spacer(Modifier.height(Space.md))
                    NdButton("Bluetooth settings", { graph.phone.openBluetoothSettings() }, style = ButtonStyle.SECONDARY, icon = R.drawable.ic_bluetooth)
                }
                Section("Coming from NoiseFit?") {
                    Txt("Enter your NoiseFit account ID to keep the watch's existing pairing. Leave empty to pair fresh — the watch may ask you to confirm.", Nd.type.bodySm, color = c.textSecondary)
                    TextInput(accountId, { accountId = it.trim().take(100) }, "Account ID (optional)", mono = true)
                }
            }
        }
    }
}

@Composable
private fun Perm(icon: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
        Icon(icon, size = 20.dp)
        Spacer(Modifier.width(Space.md))
        Column {
            Txt(title, Nd.type.body)
            Txt(body, Nd.type.bodySm, color = Nd.colors.textSecondary)
        }
    }
    Divider()
}
