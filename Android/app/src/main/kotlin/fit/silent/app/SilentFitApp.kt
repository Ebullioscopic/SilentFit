package fit.silent.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import fit.silent.app.data.AppSettings
import fit.silent.app.data.FaceLibrary
import fit.silent.app.data.HealthRepository
import fit.silent.app.data.JsonStore
import fit.silent.app.data.LegacyImport
import fit.silent.app.facestudio.AndroidFaceRasterizer
import fit.silent.app.phone.PhoneServices
import fit.silent.app.watch.WatchService
import fit.silent.app.watch.WatchSession
import fit.silent.app.widget.WidgetUpdater
import fit.silent.core.watchface.FaceCompiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Process-wide dependencies; created once in [SilentFitApp]. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = JsonStore(File(context.filesDir, "settings.json"), AppSettings.serializer(), { LegacyImport.migrate(context, AppSettings()) }, scope)
    val health = HealthRepository(context.filesDir, scope)
    val phone = PhoneServices(context)
    val session = WatchSession(context, settings, health, phone, scope)
    val faces = FaceLibrary(context.filesDir)
    val rasterizer = AndroidFaceRasterizer(context)
    val compiler = FaceCompiler(rasterizer)
}

class SilentFitApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        PhoneServices.ensureChannels(this)
        WidgetUpdater.observe(this, graph)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                graph.session.setUiVisible(true)
                ensureLink()
            }

            override fun onStop(owner: LifecycleOwner) {
                graph.session.setUiVisible(false)
            }
        })
    }

    /** Starts the foreground link service when a watch is paired and the user wants it connected. */
    fun ensureLink() {
        val s = graph.settings.value
        if (s.pairedWatch == null) return
        if (s.keepConnected && !s.demoMode) WatchService.start(this) else graph.session.connect()
    }
}
