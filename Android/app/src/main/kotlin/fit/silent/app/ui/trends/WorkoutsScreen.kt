package fit.silent.app.ui.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.BackHeader
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.EmptyState
import fit.silent.app.ui.components.Format
import fit.silent.app.ui.components.Page
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.core.fitness.Workout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutsScreen(nav: NavController) {
    val graph = LocalGraph.current
    val archive by graph.health.archive.collectAsStateWithLifecycle()
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val workouts = remember(archive) { archive.workouts.values.sortedByDescending { it.start } }
    Page {
        item { BackHeader("Workouts", { nav.popBackStack() }) }
        if (workouts.isEmpty()) item { EmptyState("No workouts yet", "Start a workout on your watch; it appears here after the next sync.") }
        workouts.forEach { w -> item(w.key) { WorkoutRow(w, settings.profile.metric); Divider() } }
    }
}

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

@Composable
fun WorkoutRow(w: Workout, metric: Boolean) {
    val c = Nd.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(w.name, Nd.type.body, color = c.textDisplay)
            Txt(Instant.ofEpochSecond(w.start).atZone(ZoneId.systemDefault()).format(WHEN), Nd.type.caption, color = c.textSecondary)
            val details = buildList {
                if (w.distanceMeters > 0) Format.distance(w.distanceMeters, metric).let { (v, u) -> add("$v $u") }
                if (w.calories > 0) add("${w.calories} kcal")
                if (w.steps > 0) add("${Format.int(w.steps)} steps")
                if (w.avgHeartRate > 0) add("avg ${w.avgHeartRate} bpm")
            }
            if (details.isNotEmpty()) Txt(details.joinToString(" · "), Nd.type.caption, color = c.textSecondary)
        }
        Txt(Format.duration((w.durationSeconds + 30) / 60), Nd.type.dataMd, color = c.textPrimary)
    }
}
