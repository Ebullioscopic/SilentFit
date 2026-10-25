package fit.silent.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fit.silent.app.AppGraph
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.faces.FaceCodeScreen
import fit.silent.app.ui.faces.FaceEditorScreen
import fit.silent.app.ui.faces.FacesScreen
import fit.silent.app.ui.onboarding.OnboardingScreen
import fit.silent.app.ui.settings.AboutScreen
import fit.silent.app.ui.settings.LogScreen
import fit.silent.app.ui.settings.ProfileScreen
import fit.silent.app.ui.settings.SettingsScreen
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.today.TodayScreen
import fit.silent.app.ui.trends.TrendDetailScreen
import fit.silent.app.ui.trends.TrendsScreen
import fit.silent.app.ui.watch.AlarmsScreen
import fit.silent.app.ui.watch.DisplayScreen
import fit.silent.app.ui.watch.HealthMonitoringScreen
import fit.silent.app.ui.watch.LauncherScreen
import fit.silent.app.ui.watch.NotificationsScreen
import fit.silent.app.ui.watch.QuickRepliesScreen
import fit.silent.app.ui.watch.RemindersScreen
import fit.silent.app.ui.watch.WatchScreen
import kotlinx.coroutines.flow.MutableStateFlow

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph") }

/** Files opened or shared into the app, consumed by the Faces screen. */
object ImportRequests {
    val pending = MutableStateFlow<Uri?>(null)
    fun offer(uri: Uri) { pending.value = uri }
}

object Routes {
    const val TODAY = "today"
    const val TRENDS = "trends"
    const val FACES = "faces"
    const val WATCH = "watch"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    fun trend(metric: String) = "trend/$metric"
    fun face(id: String) = "face/$id"
    fun faceCode(id: String) = "face-code/$id"
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(Tab(Routes.TODAY, "Today"), Tab(Routes.TRENDS, "Trends"), Tab(Routes.FACES, "Faces"), Tab(Routes.WATCH, "Watch"))

@Composable
fun AppRoot(graph: AppGraph) {
    CompositionLocalProvider(LocalGraph provides graph) {
        val nav = rememberNavController()
        val settings by graph.settings.flow.collectAsStateWithLifecycle()
        val pendingImport by ImportRequests.pending.collectAsStateWithLifecycle()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        LaunchedEffect(pendingImport) { if (pendingImport != null && route != Routes.FACES) nav.navigate(Routes.FACES) { launchSingleTop = true } }

        Column(Modifier.fillMaxSize().background(Nd.colors.black)) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    nav,
                    startDestination = if (settings.onboarded) Routes.TODAY else Routes.ONBOARDING,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(150)) },
                ) {
                    composable(Routes.ONBOARDING, enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                        OnboardingScreen(onDone = { nav.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
                    }
                    composable(Routes.TODAY) { TodayScreen(nav) }
                    composable(Routes.TRENDS) { TrendsScreen(nav) }
                    composable("trend/{metric}", listOf(navArgument("metric") { type = NavType.StringType })) {
                        TrendDetailScreen(it.arguments?.getString("metric").orEmpty(), nav)
                    }
                    composable(Routes.FACES) { FacesScreen(nav) }
                    composable("face/{id}", listOf(navArgument("id") { type = NavType.StringType })) { FaceEditorScreen(it.arguments?.getString("id").orEmpty(), nav) }
                    composable("face-code/{id}", listOf(navArgument("id") { type = NavType.StringType })) { FaceCodeScreen(it.arguments?.getString("id").orEmpty(), nav) }
                    composable(Routes.WATCH) { WatchScreen(nav) }
                    composable("watch/health") { HealthMonitoringScreen(nav) }
                    composable("watch/display") { DisplayScreen(nav) }
                    composable("watch/reminders") { RemindersScreen(nav) }
                    composable("watch/alarms") { AlarmsScreen(nav) }
                    composable("watch/launcher") { LauncherScreen(nav) }
                    composable("watch/replies") { QuickRepliesScreen(nav) }
                    composable("watch/notifications") { NotificationsScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable("settings/profile") { ProfileScreen(nav) }
                    composable("settings/log") { LogScreen(nav) }
                    composable("settings/about") { AboutScreen(nav) }
                    composable("pair") { OnboardingScreen(onDone = { nav.popBackStack() }, pairOnly = true) }
                }
            }
            if (route in tabs.map { it.route }) BottomBar(nav, route)
        }
    }
}

/** `[ TODAY ]  TRENDS  FACES  WATCH` — Space Mono caps, active item bracketed with a dot. */
@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    val c = Nd.colors
    Column(Modifier.fillMaxWidth().background(c.black).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val active = route == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) {
                            if (!active) nav.navigate(tab.route) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .semantics { selected = active }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Txt(if (active) "[ ${tab.label.uppercase()} ]" else tab.label.uppercase(), Nd.type.label, color = if (active) c.textDisplay else c.textDisabled, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) c.accent else c.black))
                }
            }
        }
    }
}
