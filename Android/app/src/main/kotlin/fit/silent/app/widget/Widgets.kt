package fit.silent.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fit.silent.app.AppGraph
import fit.silent.app.MainActivity
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.data.HealthRepository
import fit.silent.app.data.JsonStore
import fit.silent.app.ui.theme.FontAssets
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

@Serializable
data class WidgetSnapshot(
    val steps: Int = 0,
    val stepGoal: Int = 8000,
    val heartRate: Int = 0,
    val sleepMinutes: Int = 0,
    val calories: Int = 0,
    val battery: Int = -1,
    val charging: Boolean = false,
    val connected: Boolean = false,
    val watchName: String = "",
)

/** Keeps widgets in sync with session data — only when the visible numbers actually change. */
object WidgetUpdater {
    @Volatile private var store: JsonStore<WidgetSnapshot>? = null

    fun store(context: Context, graph: AppGraph): JsonStore<WidgetSnapshot> = store ?: synchronized(this) {
        store ?: JsonStore(File(context.filesDir, "widget.json"), WidgetSnapshot.serializer(), { WidgetSnapshot() }, graph.scope, 500).also { store = it }
    }

    @OptIn(FlowPreview::class)
    fun observe(context: Context, graph: AppGraph) {
        val s = graph.session
        graph.scope.launch {
            combine(graph.health.archive, s.live, s.battery, s.connection, graph.settings.flow) { archive, live, battery, connection, settings ->
                val today = archive.days[HealthRepository.key(LocalDate.now())]
                val sleep = archive.days.values.lastOrNull { it.sleep != null }?.sleep
                WidgetSnapshot(
                    steps = maxOf(today?.steps ?: 0, live?.steps ?: 0),
                    stepGoal = settings.profile.stepGoal,
                    heartRate = live?.heartRate?.takeIf { it > 0 } ?: today?.heartRate?.latest ?: 0,
                    sleepMinutes = sleep?.totalMinutes ?: 0,
                    calories = maxOf(today?.calories ?: 0, live?.calories ?: 0),
                    battery = battery?.level ?: -1,
                    charging = battery?.state == fit.silent.core.model.ChargeState.CHARGING,
                    connected = connection.isConnected,
                    watchName = settings.pairedWatch?.name.orEmpty(),
                )
            }.distinctUntilChanged().debounce(2000).collect { snap ->
                val st = store(context, graph)
                if (snap != st.value) {
                    st.set(snap)
                    runCatching {
                        TodayWidget().updateAll(context)
                        WatchWidget().updateAll(context)
                    }
                }
            }
        }
    }
}

private val Black = ColorProvider(Color(0xFF000000))
private val White = ColorProvider(Color(0xFFFFFFFF))
private val Grey = ColorProvider(Color(0xFF999999))
private val Faint = ColorProvider(Color(0xFF222222))
private val Red = ColorProvider(Color(0xFFD71921))

private fun graphOf(context: Context) = (context.applicationContext as SilentFitApp).graph

/** Renders [text] in Doto (dot-matrix) as a bitmap — RemoteViews cannot load custom fonts. */
private fun dotBitmap(context: Context, text: String, sizePx: Float, color: Int = 0xFFFFFFFF.toInt()): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = FontAssets.typeface(context, "doto", 700); textSize = sizePx; this.color = color }
    val fm = paint.fontMetrics
    val bmp = createBitmap(maxOf(1, paint.measureText(text).toInt() + 2), maxOf(1, (fm.descent - fm.ascent).toInt()))
    Canvas(bmp).drawText(text, 1f, -fm.ascent, paint)
    return bmp
}

@Composable
private fun Label(text: String, color: ColorProvider = Grey) =
    Text(text.uppercase(), style = TextStyle(color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace))

@Composable
private fun Segments(fraction: Float, count: Int = 20) {
    Row(GlanceModifier.fillMaxWidth().height(6.dp)) {
        val lit = (fraction.coerceIn(0f, 1f) * count).toInt()
        for (i in 0 until count) {
            Box(GlanceModifier.defaultWeight().height(6.dp).background(if (i < lit) White else Faint)) {}
            if (i < count - 1) Spacer(GlanceModifier.width(2.dp))
        }
    }
}

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetUpdater.store(context, graphOf(context)).value
        val density = context.resources.displayMetrics.density
        provideContent { Content(context, snap, density) }
    }

    @Composable
    private fun Content(context: Context, s: WidgetSnapshot, density: Float) {
        val compact = LocalSize.current.height < 120.dp
        GlanceTheme {
            Column(
                GlanceModifier.fillMaxSize().background(Black).cornerRadius(16.dp).padding(16.dp).clickable(actionStartActivity<MainActivity>()),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("Today", Grey)
                    Spacer(GlanceModifier.defaultWeight())
                    if (!s.connected && s.watchName.isNotEmpty()) Image(ImageProvider(R.drawable.ic_unlink), "Watch disconnected", GlanceModifier.size(12.dp), colorFilter = ColorFilter.tint(Red))
                }
                Spacer(GlanceModifier.height(if (compact) 4.dp else 10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Image(
                        ImageProvider(dotBitmap(context, "%,d".format(s.steps), (if (compact) 28f else 44f) * density)),
                        "${s.steps} steps", GlanceModifier.semantics { contentDescription = "${s.steps} steps" },
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Column { Label("Steps") }
                }
                Spacer(GlanceModifier.height(8.dp))
                Segments(s.steps / s.stepGoal.coerceAtLeast(1).toFloat())
                if (!compact) {
                    Spacer(GlanceModifier.height(12.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        Stat("Heart", if (s.heartRate > 0) "${s.heartRate}" else "--", GlanceModifier.defaultWeight(), if (s.heartRate > 0) Red else Grey)
                        Stat("Sleep", if (s.sleepMinutes > 0) "${s.sleepMinutes / 60}h${"%02d".format(s.sleepMinutes % 60)}" else "--", GlanceModifier.defaultWeight())
                        Stat("Kcal", "${s.calories}", GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String, modifier: GlanceModifier, color: ColorProvider = White) {
        Column(modifier) {
            Label(label)
            Text(value, style = TextStyle(color = color, fontSize = 15.sp, fontFamily = FontFamily.Monospace))
        }
    }
}

class WatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetUpdater.store(context, graphOf(context)).value
        val density = context.resources.displayMetrics.density
        provideContent {
            GlanceTheme {
                Row(
                    GlanceModifier.fillMaxSize().background(Black).cornerRadius(16.dp).padding(horizontal = 16.dp, vertical = 10.dp).clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(GlanceModifier.defaultWeight()) {
                        Label(snap.watchName.ifEmpty { "No watch" })
                        Spacer(GlanceModifier.height(4.dp))
                        Label(if (snap.connected) (if (snap.charging) "Charging" else "Connected") else "Offline", if (snap.connected) White else Red)
                    }
                    val text = if (snap.battery >= 0) "${snap.battery}" else "--"
                    Image(ImageProvider(dotBitmap(context, text, 34f * density)), "Battery $text percent")
                    Spacer(GlanceModifier.width(4.dp))
                    Label("%")
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class WatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchWidget()
}
