package fit.silent.app.ui.watch

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Card
import fit.silent.app.ui.components.Chip
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.NavRow
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedControl
import fit.silent.app.ui.components.Stepper
import fit.silent.app.ui.components.SwitchRow
import fit.silent.app.ui.components.TextInput
import fit.silent.app.ui.components.TimeRow
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.core.model.AodStyle
import fit.silent.core.model.LauncherItem
import fit.silent.core.model.LauncherList
import fit.silent.core.model.ReminderKind
import fit.silent.core.model.ReminderSettings
import fit.silent.core.model.Sensitivity
import fit.silent.core.model.WakeMode
import fit.silent.core.model.WatchAlarm

/** Shown when the watch did not answer a setting request. */
@Composable
private fun Unsupported(what: String) = Txt("$what is not available on this watch.", Nd.type.bodySm, Modifier.padding(vertical = Space.md), Nd.colors.textDisabled)

/** Watch-setting writes are reconstructed, not observed from the official app, so they stay off unless opted in. */
@Composable
fun ExperimentalNotice() {
    val settings by LocalGraph.current.settings.flow.collectAsStateWithLifecycle()
    if (settings.experimentalWatchControls) return
    InlineStatus(
        "View only. Changing these from SilentFit is experimental and off for your watch's safety — use NoiseFit, or turn on Settings › Experimental watch controls.",
        Modifier.padding(bottom = Space.md), Nd.colors.warning,
    )
}

@Composable
fun HealthMonitoringScreen(nav: NavController) {
    val session = LocalGraph.current.session
    val ws by session.watchSettings.collectAsStateWithLifecycle()
    Page {
        item { BackHeader("Monitoring", { nav.popBackStack() }) }
        item { ExperimentalNotice() }
        item {
            Section("Heart rate") {
                val hr = ws.heartRate ?: return@Section Unsupported("Heart-rate monitoring")
                SwitchRow("Continuous heart rate", hr.enabled, { session.setHeartRate(hr.copy(enabled = it)) }, subtitle = "Measures through the day and night")
                if (hr.enabled) {
                    Stepper("Interval", hr.intervalMinutes, { session.setHeartRate(hr.copy(intervalMinutes = it)) }, 1..60, step = if (hr.intervalMinutes < 5) 1 else 5, format = { "$it min" })
                    SwitchRow("Smart frequency", hr.smartMode, { session.setHeartRate(hr.copy(smartMode = it)) }, subtitle = "Measure more often during activity")
                }
                SwitchRow("High heart-rate alert", hr.highAlertEnabled, { session.setHeartRate(hr.copy(highAlertEnabled = it)) })
                if (hr.highAlertEnabled) Stepper("Alert above", hr.highAlertBpm, { session.setHeartRate(hr.copy(highAlertBpm = it)) }, 100..200, step = 5, format = { "$it bpm" })
                Stepper("Low alert below", hr.lowAlertBpm, { session.setHeartRate(hr.copy(lowAlertBpm = it)) }, 0..60, step = 5, format = { if (it == 0) "Off" else "$it bpm" })
            }
        }
        item {
            Section("Blood oxygen") {
                val s = ws.spo2 ?: return@Section Unsupported("SpO₂ monitoring")
                SwitchRow("Automatic SpO₂", s.enabled, { session.setSpO2(s.copy(enabled = it)) }, subtitle = "Uses more battery")
                if (s.enabled) Stepper("Interval", s.intervalMinutes, { session.setSpO2(s.copy(intervalMinutes = it)) }, 5..120, step = 5, format = { "$it min" })
            }
        }
        item {
            Section("Stress") {
                val s = ws.stress ?: return@Section Unsupported("Stress tracking")
                SwitchRow("Stress tracking", s.enabled, { session.setStress(s.copy(enabled = it)) })
                SwitchRow("Relax reminder", s.relaxReminder, { session.setStress(s.copy(relaxReminder = it)) }, enabled = s.enabled)
            }
        }
        item {
            Section("Sleep") {
                ws.remSleep?.let { SwitchRow("REM detection", it, { v -> session.setRemSleep(v) }) }
                ws.workoutDetection?.let { w ->
                    SwitchRow("Auto-detect workouts", w.autoStart, { session.setWorkoutDetection(w.copy(autoStart = it)) })
                    SwitchRow("Auto-end workouts", w.autoStop, { session.setWorkoutDetection(w.copy(autoStop = it)) })
                }
                if (ws.remSleep == null && ws.workoutDetection == null) Unsupported("Sleep and workout detection options")
            }
        }
    }
}

@Composable
fun DisplayScreen(nav: NavController) {
    val session = LocalGraph.current.session
    val ws by session.watchSettings.collectAsStateWithLifecycle()
    Page {
        item { BackHeader("Display & wake", { nav.popBackStack() }) }
        item { ExperimentalNotice() }
        item {
            Section("Screen") {
                val s = ws.screen
                if (s == null) Unsupported("Screen settings") else {
                    Stepper("Brightness", s.brightness, { session.setScreen(s.copy(brightness = it)) }, 1..5)
                    Stepper("Screen-on time", s.onSeconds, { session.setScreen(s.copy(onSeconds = it)) }, 3..30, format = { "${it}s" })
                    SwitchRow("Double-tap to wake", s.doubleTapToWake, { session.setScreen(s.copy(doubleTapToWake = it)) })
                    SwitchRow("Keep screen on", s.alwaysOn, { session.setScreen(s.copy(alwaysOn = it)) }, subtitle = "Drains the battery quickly")
                }
                ws.coverToMute?.let { SwitchRow("Cover to turn off", it, { v -> session.setCoverToMute(v) }, subtitle = "Palm over the screen dims it") }
                ws.powerSaving?.let { SwitchRow("Power saving", it, { v -> session.setPowerSaving(v) }) }
                ws.vibration?.let { v ->
                    Spacer(Modifier.height(Space.md))
                    fit.silent.app.ui.components.Label("Vibration")
                    Spacer(Modifier.height(Space.sm))
                    SegmentedControl(listOf(0, 1, 2), v, { session.setVibration(it) }, label = { listOf("Light", "Medium", "Strong")[it] })
                }
            }
        }
        item {
            Section("Raise to wake") {
                val s = ws.raiseToWake ?: return@Section Unsupported("Raise to wake")
                SegmentedControl(listOf(WakeMode.ALL_DAY, WakeMode.SCHEDULED, WakeMode.OFF), s.mode, { session.setRaiseToWake(s.copy(mode = it)) }, label = { mode(it) })
                if (s.mode == WakeMode.SCHEDULED) {
                    TimeRow("From", s.startMinutes, { session.setRaiseToWake(s.copy(startMinutes = it)) })
                    TimeRow("Until", s.endMinutes, { session.setRaiseToWake(s.copy(endMinutes = it)) })
                }
                Spacer(Modifier.height(Space.md))
                SegmentedControl(Sensitivity.entries, s.sensitivity, { session.setRaiseToWake(s.copy(sensitivity = it)) }, label = { it.name.lowercase() })
            }
        }
        item {
            Section("Always-on clock") {
                val s = ws.alwaysOn ?: return@Section Unsupported("Always-on display")
                SegmentedControl(listOf(WakeMode.ALL_DAY, WakeMode.SCHEDULED, WakeMode.OFF), s.mode, { session.setAlwaysOn(s.copy(mode = it)) }, label = { mode(it) })
                if (s.mode == WakeMode.SCHEDULED) {
                    TimeRow("From", s.startMinutes, { session.setAlwaysOn(s.copy(startMinutes = it)) })
                    TimeRow("Until", s.endMinutes, { session.setAlwaysOn(s.copy(endMinutes = it)) })
                }
                Spacer(Modifier.height(Space.md))
                SegmentedControl(AodStyle.entries, s.style, { session.setAlwaysOn(s.copy(style = it)) }, label = { it.name.lowercase() })
            }
        }
        item {
            Section("Do not disturb") {
                val s = ws.doNotDisturb ?: return@Section Unsupported("Do not disturb")
                SwitchRow("Scheduled", s.enabled, { session.setDoNotDisturb(s.copy(enabled = it)) })
                TimeRow("From", s.startMinutes, { session.setDoNotDisturb(s.copy(startMinutes = it)) }, enabled = s.enabled)
                TimeRow("Until", s.endMinutes, { session.setDoNotDisturb(s.copy(endMinutes = it)) }, enabled = s.enabled)
                SwitchRow("Smart", s.smart, { session.setDoNotDisturb(s.copy(smart = it)) }, subtitle = "Also silence while you sleep", enabled = s.enabled)
            }
        }
        item {
            Section("Sleep mode") {
                val s = ws.sleepMode ?: return@Section Unsupported("Sleep mode")
                SwitchRow("Sleep mode", s.enabled, { session.setSleepMode(s.copy(enabled = it)) }, subtitle = "Dims the screen and mutes wrist raise at night")
                TimeRow("From", s.startMinutes, { session.setSleepMode(s.copy(startMinutes = it)) }, enabled = s.enabled)
                TimeRow("Until", s.endMinutes, { session.setSleepMode(s.copy(endMinutes = it)) }, enabled = s.enabled)
            }
        }
    }
}

private fun mode(m: WakeMode) = when (m) { WakeMode.ALL_DAY -> "Always"; WakeMode.SCHEDULED -> "Scheduled"; WakeMode.OFF -> "Off"; WakeMode.SMART -> "Smart" }

@Composable
fun RemindersScreen(nav: NavController) {
    val session = LocalGraph.current.session
    val ws by session.watchSettings.collectAsStateWithLifecycle()
    Page {
        item { BackHeader("Reminders", { nav.popBackStack() }) }
        item { ExperimentalNotice() }
        ReminderKind.entries.forEach { kind ->
            item(kind.name) {
                Section(kind.label) {
                    val r = ws.reminders[kind] ?: return@Section Unsupported("${kind.label} reminders")
                    ReminderEditor(r) { session.setReminder(kind, it) }
                }
            }
        }
    }
}

@Composable
private fun ReminderEditor(r: ReminderSettings, onChange: (ReminderSettings) -> Unit) {
    SwitchRow("Remind me", r.enabled, { onChange(r.copy(enabled = it)) })
    if (!r.enabled) return
    Stepper("Every", r.intervalMinutes, { onChange(r.copy(intervalMinutes = it)) }, 15..240, step = 15, format = { Format.duration(it) })
    TimeRow("From", r.startMinutes, { onChange(r.copy(startMinutes = it)) })
    TimeRow("Until", r.endMinutes, { onChange(r.copy(endMinutes = it)) })
    SwitchRow("Skip lunch hour", r.skipLunch, { onChange(r.copy(skipLunch = it)) })
}

@Composable
fun AlarmsScreen(nav: NavController) {
    val session = LocalGraph.current.session
    val ws by session.watchSettings.collectAsStateWithLifecycle()
    val alarms = ws.alarms
    var editing by remember { mutableStateOf<Int?>(null) }
    Page {
        item {
            BackHeader("Alarms", { nav.popBackStack() }) {
                if (alarms != null && alarms.size < ws.maxAlarms) CircleButton(R.drawable.ic_plus, "Add alarm", {
                    session.setAlarms(alarms + WatchAlarm(index = alarms.size))
                    editing = alarms.size
                })
            }
        }
        if (alarms == null) { item { Unsupported("Alarms") }; return@Page }
        item { ExperimentalNotice() }
        if (alarms.isEmpty()) item { EmptyState("No alarms", "Silent vibrating alarms wake you without waking anyone else.") }
        alarms.forEachIndexed { i, a ->
            item("alarm$i") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Txt(Format.clock(a.minutes), Nd.type.dataLg, color = if (a.enabled) Nd.colors.textDisplay else Nd.colors.textDisabled)
                            Txt("${a.label} · ${Format.weekdays(a.weekdayMask)}", Nd.type.caption, color = Nd.colors.textSecondary)
                        }
                        CircleButton(R.drawable.ic_sliders_horizontal, "Edit alarm", { editing = if (editing == i) null else i })
                        Spacer(Modifier.width(Space.sm))
                        Box(
                            Modifier
                                .clickable(role = Role.Switch) { session.setAlarms(alarms.mapIndexed { j, x -> if (j == i) x.copy(enabled = !x.enabled) else x }) }
                                .semantics { stateDescription = if (a.enabled) "On" else "Off" }
                                .padding(Space.sm),
                        ) { fit.silent.app.ui.components.NdSwitch(a.enabled) }
                    }
                    if (editing == i) AlarmEditor(a, { updated -> session.setAlarms(alarms.mapIndexed { j, x -> if (j == i) updated else x }) }, {
                        session.setAlarms(alarms.filterIndexed { j, _ -> j != i }.mapIndexed { j, x -> x.copy(index = j) })
                        editing = null
                    })
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun AlarmEditor(a: WatchAlarm, onChange: (WatchAlarm) -> Unit, onDelete: () -> Unit) {
    var label by remember(a) { mutableStateOf(a.label) }
    Column(Modifier.padding(bottom = Space.md)) {
        TimeRow("Time", a.minutes, { onChange(a.copy(minutes = it)) })
        Spacer(Modifier.height(Space.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEachIndexed { d, name ->
                Chip(name, a.weekdayMask and (1 shl d) != 0, { onChange(a.copy(weekdayMask = a.weekdayMask xor (1 shl d))) })
            }
        }
        TextInput(label, { label = it.take(20) }, "Label")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Save", { onChange(a.copy(label = label.ifBlank { "Alarm" })) }, style = ButtonStyle.SECONDARY)
            NdButton("Delete", onDelete, style = ButtonStyle.DESTRUCTIVE)
        }
    }
}

@Composable
fun LauncherScreen(nav: NavController) {
    val session = LocalGraph.current.session
    val ws by session.watchSettings.collectAsStateWithLifecycle()
    var list by remember { mutableStateOf(ws.launcher.keys.firstOrNull() ?: LauncherList.APPS) }
    val items = remember(ws.launcher, list) { mutableStateListOf<LauncherItem>().apply { addAll(ws.launcher[list].orEmpty()) } }
    Page {
        item { BackHeader("Apps & widgets", { nav.popBackStack() }) }
        item { ExperimentalNotice() }
        item {
            SegmentedControl(ws.launcher.keys.toList().ifEmpty { listOf(LauncherList.APPS) }, list, { list = it }, label = { when (it) { LauncherList.APPS -> "Apps"; LauncherList.WIDGETS -> "Widgets"; LauncherList.QUICK -> "Quick" } })
            Txt("Reorder with the arrows; hidden items stay installed but leave the watch menu.", Nd.type.bodySm, Modifier.padding(vertical = Space.md), Nd.colors.textSecondary)
        }
        items.forEachIndexed { i, it ->
            item("${list.name}-${it.function}") {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Txt(launcherName(list, it.function), Nd.type.body, Modifier.weight(1f), if (it.enabled) Nd.colors.textPrimary else Nd.colors.textDisabled)
                    CircleButton(R.drawable.ic_chevron_up, "Move up", { if (i > 0 && it.sortable) { items.add(i - 1, items.removeAt(i)) } }, Modifier.padding(end = Space.xs), enabled = i > 0 && it.sortable)
                    CircleButton(R.drawable.ic_chevron_down, "Move down", { if (i < items.lastIndex && it.sortable) { items.add(i + 1, items.removeAt(i)) } }, Modifier.padding(end = Space.xs), enabled = i < items.lastIndex && it.sortable)
                    CircleButton(if (it.enabled) R.drawable.ic_eye else R.drawable.ic_eye_off, if (it.enabled) "Hide" else "Show", { items[i] = it.copy(enabled = !it.enabled) })
                }
                Divider()
            }
        }
        item {
            Spacer(Modifier.height(Space.lg))
            NdButton("Apply to watch", { session.setLauncher(list, items.toList()) })
        }
    }
}

private fun launcherName(list: LauncherList, f: Int): String {
    val names = listOf(
        "Workouts", "Workout records", "Activity", "Heart rate", "Sleep", "SpO₂", "Alarm", "Reminders", "Stopwatch", "Timer", "Music",
        "Weather", "Breathe", "Notifications", "Find phone", "Settings", "Cycle tracking", "Stress", "World clock", "Stocks", "Air pressure",
        "Compass", "ECG", "Temperature", "Phone", "Contacts", "Favourite contacts", "Camera remote", if (list == LauncherList.APPS) "Dial pad" else "Alipay",
        if (list == LauncherList.APPS) "Call log" else "QR code", if (list == LauncherList.APPS) "Flashlight" else "Dial pad",
    )
    return names.getOrElse(f) { "Function $f" }
}

@Composable
fun QuickRepliesScreen(nav: NavController) {
    val graph = LocalGraph.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val fromWatch by graph.session.quickReplies.collectAsStateWithLifecycle()
    val replies = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { replies.clear(); replies.addAll(fromWatch.ifEmpty { settings.quickReplies }) }
    Page {
        item { BackHeader("Quick replies", { nav.popBackStack() }) }
        item { ExperimentalNotice() }
        item { Txt("Send these as SMS replies to calls and messages straight from your watch.", Nd.type.bodySm, Modifier.padding(bottom = Space.md), Nd.colors.textSecondary) }
        replies.forEachIndexed { i, r ->
            item("r$i") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput(r, { replies[i] = it.take(60) }, "Reply ${i + 1}", Modifier.weight(1f))
                    CircleButton(R.drawable.ic_trash, "Remove reply", { replies.removeAt(i) })
                }
            }
        }
        item {
            Row(Modifier.padding(top = Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                NdButton("Add", { if (replies.size < 10) replies.add("") }, style = ButtonStyle.SECONDARY, enabled = replies.size < 10, icon = R.drawable.ic_plus)
                NdButton("Save to watch", { graph.session.saveQuickReplies(replies.filter { it.isNotBlank() }); status = if (settings.experimentalWatchControls) "Saved" else "Saved on phone" })
                status?.let { InlineStatus(it) }
            }
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val ws by graph.session.watchSettings.collectAsStateWithLifecycle()
    val access = graph.phone.notificationAccessGranted
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.filter { it.first != context.packageName }.sortedBy { it.second.lowercase() }
    }
    Page {
        item { BackHeader("Notifications", { nav.popBackStack() }) }
        if (!access) item {
            Card(Modifier.padding(bottom = Space.md)) {
                Txt("Allow notification access so SilentFit can mirror alerts to your watch.", Nd.type.bodySm)
                Spacer(Modifier.height(Space.md))
                NdButton("Open settings", { graph.phone.openNotificationAccess() })
            }
        }
        item {
            Section("Forward") {
                SwitchRow("App notifications", settings.forwardNotifications, { v -> graph.settings.update { it.copy(forwardNotifications = v) } })
                SwitchRow("Calls", settings.forwardCalls, { v -> graph.settings.update { it.copy(forwardCalls = v) } }, subtitle = "Answer or decline from your wrist")
                SwitchRow("Text messages", settings.forwardMessages, { v -> graph.settings.update { it.copy(forwardMessages = v) } })
                SwitchRow("Quiet while using phone", settings.quietWhileScreenOn, { v -> graph.settings.update { it.copy(quietWhileScreenOn = v) } }, subtitle = "Skip alerts when the phone is unlocked")
            }
        }
        ws.notifications?.let { n ->
            item {
                Section("On the watch") {
                    ExperimentalNotice()
                    SwitchRow("Keep screen off", n.keepScreenOff, { graph.session.setWatchNotifications(n.copy(keepScreenOff = it)) }, subtitle = "Vibrate without lighting the display")
                    SwitchRow("Only when worn", n.onlyWhenWorn, { graph.session.setWatchNotifications(n.copy(onlyWhenWorn = it)) })
                    SwitchRow("Only when phone is locked", n.onlyWhenPhoneLocked, { graph.session.setWatchNotifications(n.copy(onlyWhenPhoneLocked = it)) })
                }
            }
        }
        item { Section("Apps") { Txt("Muted apps never reach the watch.", Nd.type.bodySm, color = Nd.colors.textSecondary) } }
        apps.forEach { (pkg, label) ->
            item(pkg) {
                SwitchRow(label, pkg !in settings.mutedPackages, { on -> graph.settings.update { s -> s.copy(mutedPackages = if (on) s.mutedPackages - pkg else s.mutedPackages + pkg) } }, enabled = settings.forwardNotifications)
            }
        }
        item { NavRow("Send a test notification", onClick = { graph.session.sendTestNotification() }) }
    }
}
