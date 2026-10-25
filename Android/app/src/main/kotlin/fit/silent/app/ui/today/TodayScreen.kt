package fit.silent.app.ui.today

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
            Section("By hour") {
                if (today.hourlySteps.any { it > 0 }) {
                    BarChart(today.hourlySteps, height = 96.dp, highlight = java.time.LocalTime.now().hour)
                } else {
                    Txt("Hourly steps appear after the next sync.", Nd.type.bodySm, color = c.textDisabled)
                }
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
