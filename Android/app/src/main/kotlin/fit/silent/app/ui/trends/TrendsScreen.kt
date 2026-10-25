package fit.silent.app.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.R
import fit.silent.app.data.HealthRepository
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.Routes
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.BarChart
import fit.silent.app.ui.components.CircleButton
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.Hero
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.PageTitle
import fit.silent.app.ui.components.SampleDots
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedControl
import fit.silent.app.ui.components.StatRow
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.core.model.HealthDay
import fit.silent.core.model.SleepStage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Metric(val key: String, val title: String, val unit: String) {
    STEPS("steps", "Steps", "steps"),
    HEART("heart", "Heart rate", "bpm"),
    SLEEP("sleep", "Sleep", "h"),
    CALORIES("calories", "Calories", "kcal"),
    SPO2("spo2", "Blood oxygen", "%"),
    STRESS("stress", "Stress", ""),
    ACTIVE("active", "Active minutes", "min"),
    STANDING("standing", "Standing", "h");

    /** Daily summary value for bar charts. */
    fun value(d: HealthDay?): Int = when (this) {
        STEPS -> d?.steps ?: 0
        HEART -> d?.heartRate?.average ?: 0
        SLEEP -> d?.sleep?.totalMinutes ?: 0
        CALORIES -> d?.calories ?: 0
        SPO2 -> d?.spo2?.average ?: 0
        STRESS -> d?.stress?.average ?: 0
        ACTIVE -> d?.activeMinutes ?: 0
        STANDING -> d?.standingHourCount ?: 0
    }

    fun display(v: Int) = when (this) { SLEEP -> Format.duration(v); STEPS, CALORIES -> Format.int(v); else -> if (v > 0) "$v" else "--" }

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: STEPS }
}

private enum class Period(val days: Int, val label: String) { WEEK(7, "Week"), MONTH(30, "Month") }

@Composable
fun TrendsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val archive by graph.health.archive.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    val today = LocalDate.now()
    val dates = remember(period) { (period.days - 1 downTo 0).map { today.minusDays(it.toLong()) } }

    Page {
        item { PageTitle("Trends", eyebrow = "History") }
        item { SegmentedControl(Period.entries, period, { period = it }, label = { it.label }) }
        if (archive.days.isEmpty()) {
            item { EmptyState("No history yet", "Health data appears here after your first sync.") }
        } else {
            Metric.entries.forEach { metric ->
                item(metric.key) {
                    val values = dates.map { metric.value(archive.days[HealthRepository.key(it)]) }
                    val present = values.filter { it > 0 }
                    val avg = if (present.isEmpty()) 0 else present.sum() / present.size
                    Section(metric.title, trailing = { Txt("AVG ${metric.display(avg)}", Nd.type.label, color = Nd.colors.textSecondary) }) {
                        Box(Modifier.fillMaxWidth().semantics { contentDescription = "${metric.title}, ${period.label} average ${metric.display(avg)}" }) {
                            BarChart(values, height = 64.dp, highlight = values.lastIndex, color = Nd.colors.textPrimary, axis = false)
                        }
                        StatRow("Today", metric.display(values.last()), unit = metric.unit.takeIf { metric != Metric.SLEEP }, onClick = { nav.navigate(Routes.trend(metric.key)) })
                    }
                }
            }
        }
    }
}

@Composable
fun TrendDetailScreen(key: String, nav: NavController) {
    val metric = Metric.of(key)
    val graph = LocalGraph.current
    val archive by graph.health.archive.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val day = LocalDate.ofEpochDay(date)
    val d = archive.days[HealthRepository.key(day)]
    val c = Nd.colors

    Page {
        item { BackHeader(metric.title, { nav.popBackStack() }) }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(R.drawable.ic_chevron_left, "Previous day", { date-- })
                Txt(day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())).uppercase(), Nd.type.label, Modifier.weight(1f), c.textPrimary, align = androidx.compose.ui.text.style.TextAlign.Center)
                CircleButton(R.drawable.ic_chevron_right, "Next day", { date++ }, enabled = day < LocalDate.now())
            }
        }
        item {
            Column(Modifier.padding(vertical = Space.xl)) {
                Label(metric.title)
                val v = metric.value(d)
                Hero(if (metric == Metric.SLEEP) Format.duration(v) else metric.display(v), unit = metric.unit.takeIf { metric != Metric.SLEEP && v > 0 }, dotMatrix = metric != Metric.SLEEP)
            }
        }
        item {
            when (metric) {
                Metric.STEPS -> Section("By hour") { BarChart(d?.hourlySteps.orEmpty().ifEmpty { List(24) { 0 } }) }
                Metric.CALORIES -> Section("By hour") { BarChart(d?.hourlyCalories.orEmpty().ifEmpty { List(24) { 0 } }) }
                Metric.ACTIVE -> Section("By hour") { BarChart(d?.activeMinutesByHour.orEmpty().ifEmpty { List(24) { 0 } }) }
                Metric.STANDING -> Section("Hours stood") { BarChart(d?.standingHours.orEmpty().ifEmpty { List(24) { 0 } }) }
                Metric.HEART -> Section("Through the day") {
                    SampleDots(d?.heartRate?.values.orEmpty(), color = c.accent, average = d?.heartRate?.average ?: 0)
                    StatRow("Resting low", d?.heartRate?.min?.takeIf { it > 0 }?.toString() ?: "--", unit = "bpm")
                    StatRow("Peak", d?.heartRate?.max?.takeIf { it > 0 }?.toString() ?: "--", unit = "bpm")
                }
                Metric.SPO2 -> Section("Through the day") {
                    SampleDots(d?.spo2?.values.orEmpty(), floor = 85, average = d?.spo2?.average ?: 0)
                    StatRow("Lowest", d?.spo2?.min?.takeIf { it > 0 }?.toString() ?: "--", unit = "%")
                }
                Metric.STRESS -> Section("Through the day") {
                    SampleDots(d?.stress?.values.orEmpty(), floor = 0, average = d?.stress?.average ?: 0)
                    StatRow("Highest", d?.stress?.max?.takeIf { it > 0 }?.toString() ?: "--")
                }
                Metric.SLEEP -> SleepDetail(d)
            }
        }
        if (d == null) item { EmptyState("No data for this day", "Sync your watch or pick another day.") }
    }
}

@Composable
private fun SleepDetail(d: HealthDay?) {
    val s = d?.sleep ?: return
    val c = Nd.colors
    Section("Stages") {
        val total = s.segments.sumOf { it.minutes }.coerceAtLeast(1)
        // Hypnogram as stacked rows; depth shown with opacity, not colour.
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = "Sleep stages timeline" }) {
            val rowH = size.height / 4
            var x = 0f
            s.segments.forEach { seg ->
                val w = seg.minutes.toFloat() / total * size.width
                val row = when (seg.stage) { SleepStage.AWAKE -> 0; SleepStage.REM -> 1; SleepStage.LIGHT -> 2; SleepStage.DEEP -> 3 }
                val color = when (seg.stage) { SleepStage.AWAKE -> c.accent; SleepStage.REM -> c.textDisplay.copy(alpha = 0.6f); SleepStage.LIGHT -> c.textDisplay.copy(alpha = 0.8f); SleepStage.DEEP -> c.textDisplay }
                drawRect(color, Offset(x, row * rowH + 2), Size((w - 1).coerceAtLeast(1f), rowH - 4))
                x += w
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Awake", "REM", "Light", "Deep").forEach { Label(it, color = c.textDisabled) }
        }
        Spacer(Modifier.height(Space.md))
        StatRow("Score", "${s.score}", unit = "/100")
        StatRow("Deep", Format.duration(s.deepMinutes))
        StatRow("Light", Format.duration(s.lightMinutes))
        StatRow("REM", Format.duration(s.remMinutes))
        StatRow("Awake", Format.duration(s.awakeMinutes), valueColor = if (s.awakeMinutes > 60) c.warning else c.textPrimary)
    }
}
