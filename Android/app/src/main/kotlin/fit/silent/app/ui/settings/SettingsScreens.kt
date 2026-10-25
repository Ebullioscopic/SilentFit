package fit.silent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.BuildConfig
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Card
import fit.silent.app.ui.components.FormPage
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NavRow
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedControl
import fit.silent.app.ui.components.StatRow
import fit.silent.app.ui.components.Stepper
import fit.silent.app.ui.components.SwitchRow
import fit.silent.app.ui.components.TextInput
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.ui.theme.ThemeMode
import fit.silent.core.model.Sex
import fit.silent.core.model.WearHand

@Composable
fun SettingsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmExperimental by remember { mutableStateOf(false) }
    val missing = graph.phone.missingPermissions()

    Page {
        item { BackHeader("Settings", { nav.popBackStack() }) }
        item {
            Section("You") {
                NavRow("Profile & goals", subtitle = "${settings.profile.stepGoal} steps · ${settings.profile.sleepGoalHours} h sleep", icon = R.drawable.ic_user) { nav.navigate("settings/profile") }
            }
        }
        item {
            Section("Appearance") {
                SegmentedControl(ThemeMode.entries, settings.themeMode, { m -> graph.settings.update { it.copy(themeMode = m) } }, label = { it.name.lowercase() })
            }
        }
        item {
            Section("Connection") {
                SwitchRow("Stay connected in background", settings.keepConnected, { on ->
                    graph.settings.update { it.copy(keepConnected = on) }
                    if (on) (context.applicationContext as SilentFitApp).ensureLink()
                }, subtitle = "Needed for notifications and calls on the watch")
                SwitchRow("Reconnect automatically", settings.autoReconnect, { v -> graph.settings.update { it.copy(autoReconnect = v) } })
                Label("Background sync", Modifier.padding(top = Space.md))
                Spacer(Modifier.height(Space.sm))
                SegmentedControl(listOf(30, 60, 120, 0), settings.syncIntervalMinutes, { v -> graph.settings.update { it.copy(syncIntervalMinutes = v) } }, label = { if (it == 0) "Manual" else if (it < 60) "${it}m" else "${it / 60}h" })
                Txt("Longer intervals save watch and phone battery; the app always syncs when opened.", Nd.type.caption, Modifier.padding(top = Space.sm), Nd.colors.textDisabled)
                NavRow("Battery optimisation", subtitle = "Let SilentFit run unrestricted if the link drops overnight", icon = R.drawable.ic_battery) { graph.phone.openBatterySettings() }
            }
        }
        item {
            Section("Permissions") {
                StatRow("Notification access", if (graph.phone.notificationAccessGranted) "Granted" else "Off", valueColor = if (graph.phone.notificationAccessGranted) Nd.colors.success else Nd.colors.warning, onClick = { graph.phone.openNotificationAccess() })
                StatRow("Runtime permissions", if (missing.isEmpty()) "Granted" else "${missing.size} missing", valueColor = if (missing.isEmpty()) Nd.colors.success else Nd.colors.warning, onClick = { graph.phone.openAppSettings() })
            }
        }
        item {
            Section("Data") {
                SwitchRow("Free watch storage after sync", settings.deleteAfterSync, { v -> graph.settings.update { it.copy(deleteAfterSync = v) } }, subtitle = "Removes synced records from the watch — other apps will no longer see them")
                SwitchRow("Demo mode", settings.demoMode, { graph.session.setDemoMode(it) }, subtitle = "A simulated watch with sample data")
                if (!confirmClear) NavRow("Clear health history", icon = R.drawable.ic_trash) { confirmClear = true }
                else Row(Modifier.fillMaxWidth().padding(vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    NdButton("Cancel", { confirmClear = false }, style = ButtonStyle.GHOST)
                    NdButton("Clear", { graph.health.clear(); confirmClear = false }, style = ButtonStyle.DESTRUCTIVE)
                }
            }
        }
        item {
            Section("Watch safety") {
                SwitchRow("Experimental watch controls", settings.experimentalWatchControls, { on ->
                    if (on) confirmExperimental = true else graph.settings.update { it.copy(experimentalWatchControls = false) }
                }, subtitle = "Lets SilentFit change watch settings, profile, units and the app menu")
                if (confirmExperimental) Card(Modifier.padding(vertical = Space.md)) {
                    Txt(
                        "These commands were reconstructed from the protocol, not observed from the official app on this watch. " +
                            "A malformed command could make the watch restart or need a factory reset. Only continue if you accept that risk.",
                        Nd.type.bodySm, color = Nd.colors.textSecondary,
                    )
                    Spacer(Modifier.height(Space.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        NdButton("Enable", { graph.settings.update { it.copy(experimentalWatchControls = true) }; confirmExperimental = false }, style = ButtonStyle.DESTRUCTIVE)
                        NdButton("Cancel", { confirmExperimental = false }, style = ButtonStyle.GHOST)
                    }
                }
                Txt("Off: SilentFit only sends commands seen from NoiseFit on this watch — sync, time, battery, notifications, music, find watch and verified faces.", Nd.type.caption, Modifier.padding(top = Space.sm), Nd.colors.textDisabled)
            }
        }
        item {
            Section("Support") {
                NavRow("Connection log", icon = R.drawable.ic_scan_line) { nav.navigate("settings/log") }
                NavRow("About & licences", value = BuildConfig.VERSION_NAME, icon = R.drawable.ic_info) { nav.navigate("settings/about") }
            }
        }
    }
}

@Composable
fun ProfileScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var p by remember { mutableStateOf(settings.profile) }
    var saved by remember { mutableStateOf(false) }
    fun set(next: fit.silent.core.model.UserProfile) { p = next; saved = false }

    FormPage {
        BackHeader("Profile & goals", { nav.popBackStack() })
        TextInput(p.name, { set(p.copy(name = it.take(20))) }, "Name on watch")
        Section("Body") {
            SegmentedControl(Sex.entries, p.sex, { set(p.copy(sex = it)) }, label = { if (it == Sex.UNSPECIFIED) "Other" else it.name.lowercase() })
            Stepper("Age", p.age, { set(p.copy(age = it)) }, 10..100)
            Stepper("Height", p.heightCm, { set(p.copy(heightCm = it)) }, 100..230, format = { "$it cm" })
            Stepper("Weight", p.weightKg.toInt(), { set(p.copy(weightKg = it.toFloat())) }, 30..200, format = { "$it kg" })
            Label("Watch on", Modifier.padding(top = Space.md))
            Spacer(Modifier.height(Space.sm))
            SegmentedControl(listOf(WearHand.LEFT, WearHand.RIGHT), if (p.wearHand == WearHand.RIGHT) WearHand.RIGHT else WearHand.LEFT, { set(p.copy(wearHand = it)) }, label = { it.name.lowercase() })
        }
        Section("Daily goals") {
            Stepper("Steps", p.stepGoal, { set(p.copy(stepGoal = it)) }, 1000..40000, step = 500, format = { "%,d".format(it) })
            Stepper("Calories", p.calorieGoal, { set(p.copy(calorieGoal = it)) }, 100..2000, step = 50, format = { "$it kcal" })
            Stepper("Active", p.activeMinutesGoal, { set(p.copy(activeMinutesGoal = it)) }, 10..180, step = 5, format = { "$it min" })
            Stepper("Standing", p.standingGoal, { set(p.copy(standingGoal = it)) }, 1..16, format = { "$it h" })
            Stepper("Sleep", p.sleepGoalHours, { set(p.copy(sleepGoalHours = it)) }, 4..12, format = { "$it h" })
            Stepper("Water", p.waterGoalMl, { set(p.copy(waterGoalMl = it)) }, 500..5000, step = 250, format = { "$it ml" })
        }
        Section("Units") {
            SegmentedControl(listOf(true, false), p.metric, { set(p.copy(metric = it)) }, label = { if (it) "Metric" else "Imperial" })
            Spacer(Modifier.height(Space.sm))
            SegmentedControl(listOf(true, false), p.celsius, { set(p.copy(celsius = it)) }, label = { if (it) "°C" else "°F" })
        }
        Spacer(Modifier.height(Space.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            NdButton("Save", { graph.session.updateProfile(p); saved = true })
            if (saved) InlineStatus(if (settings.experimentalWatchControls) "Saved" else "Saved on this phone")
        }
    }
}

@Composable
fun LogScreen(nav: NavController) {
    val graph = LocalGraph.current
    val log by graph.session.log.collectAsStateWithLifecycle()
    Page {
        item {
            BackHeader("Connection log", { nav.popBackStack() }) {
                NdButton("Clear", { graph.session.clearLog() }, style = ButtonStyle.GHOST)
            }
        }
        if (log.isEmpty()) item { Txt("Nothing logged yet.", Nd.type.bodySm, color = Nd.colors.textDisabled) }
        items(log.size) { i ->
            val line = log[log.size - 1 - i]
            Txt(line, Nd.type.caption, Modifier.padding(vertical = 2.dp), if ("error" in line || "failed" in line) Nd.colors.accent else Nd.colors.textSecondary)
        }
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var showAccount by remember { mutableStateOf(false) }
    FormPage {
        BackHeader("About", { nav.popBackStack() })
        Column(Modifier.padding(vertical = Space.xl)) {
            Txt("SilentFit", Nd.type.displayLg, color = Nd.colors.textDisplay)
            Label("Version ${BuildConfig.VERSION_NAME}")
        }
        Txt(
            "An open, private companion for Noise watches. Everything stays on this phone: SilentFit has no internet permission, " +
                "no account and no analytics.",
            Nd.type.body,
        )
        Section("Account ID") {
            Txt("The watch remembers the ID it was paired with. Reusing the NoiseFit ID avoids resetting the watch.", Nd.type.bodySm, color = Nd.colors.textSecondary)
            Spacer(Modifier.height(Space.sm))
            if (showAccount) {
                var id by remember { mutableStateOf(settings.accountId) }
                TextInput(id, { id = it.trim().take(100) }, "Account ID", mono = true)
                NdButton("Save", { graph.settings.update { it.copy(accountId = id) }; showAccount = false }, style = ButtonStyle.SECONDARY)
            } else NdButton("Show / edit", { showAccount = true }, style = ButtonStyle.GHOST)
        }
        Section("Licences") {
            Card {
                Txt("Doto, Space Grotesk, Space Mono", Nd.type.bodySm, color = Nd.colors.textDisplay)
                Txt("SIL Open Font License 1.1 — fonts/OFL-*.txt", Nd.type.caption, color = Nd.colors.textSecondary)
                Spacer(Modifier.height(Space.md))
                Txt("Lucide icons", Nd.type.bodySm, color = Nd.colors.textDisplay)
                Txt("ISC License © Lucide Contributors", Nd.type.caption, color = Nd.colors.textSecondary)
                Spacer(Modifier.height(Space.md))
                Txt("Design language", Nd.type.bodySm, color = Nd.colors.textDisplay)
                Txt("Inspired by Nothing's design system; not affiliated with Nothing or Noise.", Nd.type.caption, color = Nd.colors.textSecondary)
            }
        }
    }
}
