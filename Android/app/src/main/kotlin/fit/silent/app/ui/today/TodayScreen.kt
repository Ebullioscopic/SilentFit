package fit.silent.app.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import fit.silent.app.data.TodayLayout
import fit.silent.app.ui.components.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.data.HealthRepository
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.Routes
import fit.silent.app.ui.components.BarChart
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Card
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.Gauge
import fit.silent.app.ui.components.Hero
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedBar
import fit.silent.app.ui.components.SegmentedSpinner
import fit.silent.app.ui.components.StatRow
import fit.silent.app.ui.components.StatusDot
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.core.model.HealthDay
import fit.silent.core.model.SleepSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(nav: NavController) {
    val graph = LocalGraph.current
    val archive by graph.health.archive.collectAsStateWithLifecycle()
    val live by graph.session.live.collectAsStateWithLifecycle()
    val connection by graph.session.connection.collectAsStateWithLifecycle()
    val battery by graph.session.battery.collectAsStateWithLifecycle()
    val sync by graph.session.sync.collectAsStateWithLifecycle()
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val findingPhone by graph.session.findingPhone.collectAsStateWithLifecycle()
    val c = Nd.colors
    val profile = settings.profile

    val stored = archive.days[HealthRepository.key(LocalDate.now())] ?: HealthDay(HealthRepository.key(LocalDate.now()))
    // The watch finalises today's record late; live totals are ahead during the day.
    val today = remember(stored, live) {
        live?.let { l -> stored.copy(steps = maxOf(stored.steps, l.steps), calories = maxOf(stored.calories, l.calories), distanceMeters = maxOf(stored.distanceMeters, l.distanceMeters)) } ?: stored
    }
    val lastSleep: SleepSummary? = remember(archive) { archive.days.values.lastOrNull { it.sleep != null }?.sleep }
    val heartRate = live?.heartRate?.takeIf { it > 0 } ?: today.heartRate.latest
    val spo2 = live?.spo2?.takeIf { it > 0 } ?: today.spo2.latest
    val stress = live?.stress?.takeIf { it > 0 } ?: today.stress.latest

    Page {
        item {
            Row(Modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMM", Locale.getDefault())))
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (connection.isConnected) c.success else if (connection.isBusy) c.warning else c.textDisabled)
                        Spacer(Modifier.width(Space.sm))
                        Txt(
                            when {
                                settings.pairedWatch == null -> "No watch paired"
                                connection.isConnected -> "${settings.pairedWatch?.name} · ${battery?.level?.let { "$it%" } ?: "--"}"
                                else -> connection.message
                            },
                            Nd.type.caption, color = c.textSecondary, maxLines = 1,
                        )
                    }
                }
                CircleButton(if (settings.todayLayout == TodayLayout.DIALS) R.drawable.ic_type else R.drawable.ic_gauge,
                    if (settings.todayLayout == TodayLayout.DIALS) "Show as text" else "Show as dials", {
                        graph.settings.update { it.copy(todayLayout = if (it.todayLayout == TodayLayout.DIALS) TodayLayout.TEXT else TodayLayout.DIALS) }
                    })
                Spacer(Modifier.width(Space.sm))
                CircleButton(R.drawable.ic_settings, "Settings", { nav.navigate(Routes.SETTINGS) })
            }
        }
        if (findingPhone) item {
            Card(Modifier.padding(bottom = Space.md)) {
                Txt("Your watch is looking for this phone", Nd.type.body, color = c.textDisplay)
                Spacer(Modifier.height(Space.md))
                NdButton("I found it", { graph.session.stopFindingPhone() }, style = ButtonStyle.DESTRUCTIVE)
            }
        }
        if (settings.todayLayout == TodayLayout.DIALS) {
            item {
                ActivityRings(
                    steps = today.steps, stepGoal = profile.stepGoal,
                    calories = today.calories, calorieGoal = profile.calorieGoal,
                    active = maxOf(today.activeMinutes, live?.activeMinutes ?: 0), activeGoal = profile.activeMinutesGoal,
                    onOpen = { nav.navigate(Routes.trend(it)) },
                )
            }
            item {
                Section("Vitals") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniDial("Heart", if (heartRate > 0) "$heartRate" else "--", "bpm", heartRate / 180f, R.drawable.ic_heart, if (heartRate > 120) c.accent else c.textDisplay) { nav.navigate(Routes.trend("heart")) }
                        MiniDial("SpO₂", if (spo2 > 0) "$spo2" else "--", "%", ((spo2 - 80) / 20f).coerceIn(0f, 1f), R.drawable.ic_droplet, if (spo2 in 1..93) c.warning else c.textDisplay) { nav.navigate(Routes.trend("spo2")) }
                        MiniDial("Stress", if (stress > 0) "$stress" else "--", "", stress / 100f, R.drawable.ic_brain, if (stress > 60) c.warning else c.textDisplay) { nav.navigate(Routes.trend("stress")) }
                    }
                }
            }
            item {
                Section("Body & watch") {
                    val standing = maxOf(today.standingHourCount, live?.standingHours ?: 0)
                    val (dist, unit) = Format.distance(today.distanceMeters, profile.metric)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniDial("Standing", "$standing", "/ ${profile.standingGoal} h", standing / profile.standingGoal.coerceAtLeast(1).toFloat(), R.drawable.ic_activity) { nav.navigate(Routes.trend("standing")) }
                        MiniDial("Distance", dist, unit, (today.distanceMeters / 8000f).coerceIn(0f, 1f), R.drawable.ic_footprints) { nav.navigate(Routes.trend("steps")) }
                        val level = battery?.level
                        MiniDial("Watch", level?.toString() ?: "--", "%", (level ?: 0) / 100f, R.drawable.ic_battery, if (level != null && level <= 20) c.accent else c.textDisplay) { nav.navigate(Routes.WATCH) }
                    }
                }
            }
        } else {
        item {
            Column(Modifier.fillMaxWidth().padding(top = Space.xl).semantics(mergeDescendants = true) { heading() }) {
                Label("Steps")
                Hero(Format.int(today.steps), unit = "/ ${Format.int(profile.stepGoal)}")
            }
            Spacer(Modifier.height(Space.md))
            SegmentedBar(today.steps / profile.stepGoal.coerceAtLeast(1).toFloat(), height = 14.dp)
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth()) {
                Txt(Format.percent(today.steps / profile.stepGoal.coerceAtLeast(1).toFloat()) + " of goal", Nd.type.caption, Modifier.weight(1f), c.textSecondary)
                val (dist, unit) = Format.distance(today.distanceMeters, profile.metric)
                Txt("$dist $unit", Nd.type.caption, color = c.textSecondary)
            }
        }
        item {
            Section("Vitals") {
                StatRow("Heart rate", if (heartRate > 0) "$heartRate" else "--", unit = "bpm", valueColor = if (heartRate > 120) c.accent else c.textPrimary, onClick = { nav.navigate(Routes.trend("heart")) })
                StatRow("Blood oxygen", if (spo2 > 0) "$spo2" else "--", unit = "%", valueColor = if (spo2 in 1..93) c.warning else c.textPrimary, onClick = { nav.navigate(Routes.trend("spo2")) })
                StatRow("Stress", if (stress > 0) "$stress" else "--", valueColor = if (stress > 60) c.warning else c.textPrimary, onClick = { nav.navigate(Routes.trend("stress")) })
            }
        }
        item {
            Section("Activity") {
                StatRow("Calories", Format.int(today.calories), unit = "kcal", onClick = { nav.navigate(Routes.trend("calories")) })
                StatRow("Active", "${maxOf(today.activeMinutes, live?.activeMinutes ?: 0)}", unit = "min", onClick = { nav.navigate(Routes.trend("active")) })
                StatRow("Standing", "${maxOf(today.standingHourCount, live?.standingHours ?: 0)} / ${profile.standingGoal}", unit = "h", onClick = { nav.navigate(Routes.trend("standing")) })
            }
        }
        }
        item {
            Section("By hour") {
                if (today.hourlySteps.any { it > 0 }) {
                    BarChart(today.hourlySteps, height = 96.dp, highlight = java.time.LocalTime.now().hour)
                } else {
                    Txt("Hourly steps appear after the next sync.", Nd.type.bodySm, color = c.textDisabled)
                }
            }
        }
        item {
            Section("Last sleep") {
                if (lastSleep == null) {
                    Txt("Wear your watch to bed to see sleep here.", Nd.type.bodySm, color = c.textDisabled)
                } else {
                    Card(onClick = { nav.navigate(Routes.trend("sleep")) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Gauge(lastSleep.score / 100f, size = 96.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Txt("${lastSleep.score}", Nd.type.dataMd, color = c.textDisplay)
                                    Label("Score")
                                }
                            }
                            Spacer(Modifier.width(Space.lg))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                Txt(Format.duration(lastSleep.totalMinutes), Nd.type.heading, color = c.textDisplay)
                                Txt("Deep ${Format.duration(lastSleep.deepMinutes)}", Nd.type.caption, color = c.textSecondary)
                                Txt("Light ${Format.duration(lastSleep.lightMinutes)}", Nd.type.caption, color = c.textSecondary)
                                Txt("REM ${Format.duration(lastSleep.remMinutes)}", Nd.type.caption, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = Space.xxl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (sync.running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SegmentedSpinner(size = 14.dp)
                            Spacer(Modifier.width(Space.sm))
                            InlineStatus(sync.message)
                        }
                    } else {
                        Label("Synced ${Format.ago(settings.lastSync)}", color = c.textDisabled)
                        if (sync.message.isNotEmpty() && sync.message != "Synced" && sync.message != "Up to date") InlineStatus(sync.message)
                    }
                }
                NdButton("Sync", { graph.session.syncHealth() }, style = ButtonStyle.SECONDARY, enabled = connection.isConnected && !sync.running, icon = R.drawable.ic_refresh_cw)
            }
        }
    }
}

/** Three segmented concentric rings (steps, calories, active minutes) with the step count as the hero. */
@Composable
private fun ActivityRings(steps: Int, stepGoal: Int, calories: Int, calorieGoal: Int, active: Int, activeGoal: Int, onOpen: (String) -> Unit) {
    val c = Nd.colors
    val rings = listOf(
        Triple("steps", steps / stepGoal.coerceAtLeast(1).toFloat(), 1f),
        Triple("calories", calories / calorieGoal.coerceAtLeast(1).toFloat(), 0.62f),
        Triple("active", active / activeGoal.coerceAtLeast(1).toFloat(), 0.36f),
    )
    Row(Modifier.fillMaxWidth().padding(top = Space.lg), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(176.dp).clip(RoundedCornerShape(88.dp)).clickable(role = Role.Button) { onOpen("steps") }
                .semantics { contentDescription = "$steps of $stepGoal steps, $calories kcal, $active active minutes" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(176.dp)) {
                val stroke = 11.dp.toPx()
                val gap = 5.dp.toPx()
                val segments = 40
                rings.forEachIndexed { i, (_, fraction, alpha) ->
                    val inset = stroke / 2 + i * (stroke + gap)
                    val box = Size(size.width - inset * 2, size.height - inset * 2)
                    val lit = (fraction.coerceIn(0f, 1f) * segments).toInt()
                    val over = fraction > 1f
                    for (s in 0 until segments) {
                        val color = when {
                            s < lit -> (if (over && i == 0) c.accent else c.textDisplay).copy(alpha = alpha)
                            else -> c.borderVisible
                        }
                        drawArc(color, -90f + s * 9f + 0.8f, 9f - 1.6f, false, Offset(inset, inset), box, style = Stroke(stroke))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(Format.int(steps), Nd.type.dataMd, color = c.textDisplay)
                Label("Steps")
            }
        }
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            RingLegend("Steps", Format.int(steps), "/ ${Format.int(stepGoal)}", 1f) { onOpen("steps") }
            RingLegend("Calories", Format.int(calories), "/ $calorieGoal kcal", 0.62f) { onOpen("calories") }
            RingLegend("Active", "$active", "/ $activeGoal min", 0.36f) { onOpen("active") }
        }
    }
}

@Composable
private fun RingLegend(label: String, value: String, goal: String, alpha: Float, onClick: () -> Unit) {
    val c = Nd.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).then(Modifier.background(c.textDisplay.copy(alpha = alpha))))
            Spacer(Modifier.width(Space.sm))
            Label(label)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, Nd.type.heading, color = c.textDisplay)
            Spacer(Modifier.width(Space.xs))
            Txt(goal, Nd.type.caption, color = c.textSecondary, maxLines = 1)
        }
    }
}

/** 270° instrument dial with an icon, value and label; tap opens details. */
@Composable
private fun MiniDial(label: String, value: String, unit: String, fraction: Float, @DrawableRes icon: Int, color: Color = Nd.colors.textDisplay, onClick: () -> Unit) {
    val c = Nd.colors
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value $unit" }
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(88.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val box = Size(size.width - stroke, size.height - stroke)
                drawArc(c.borderVisible, 135f, 270f, false, Offset(inset, inset), box, style = Stroke(stroke))
                drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, size = 16.dp, tint = c.textSecondary)
                Txt(value, Nd.type.dataMd, color = if (value == "--") c.textDisabled else c.textDisplay, maxLines = 1)
            }
        }
        Label(label)
        if (unit.isNotEmpty()) Txt(unit, Nd.type.caption, color = c.textDisabled, maxLines = 1)
    }
}
