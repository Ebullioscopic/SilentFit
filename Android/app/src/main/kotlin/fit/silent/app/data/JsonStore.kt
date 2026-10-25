package fit.silent.app.data

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/**
 * A value persisted as JSON with atomic writes. Updates are in-memory immediately and written to disk
 * after [debounceMs] of quiet, so bursts of changes cost one write.
 */
@OptIn(FlowPreview::class)
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    default: () -> T,
    scope: CoroutineScope,
    debounceMs: Long = 1500,
) {
    private val atomic = AtomicFile(file)
    private val state = MutableStateFlow(load() ?: default())
    val flow: StateFlow<T> = state.asStateFlow()
    val value: T get() = state.value

    init {
        state.drop(1).debounce(debounceMs).onEach { write(it) }.launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    fun update(transform: (T) -> T) = state.update(transform)

    fun set(value: T) { state.value = value }

    private fun load(): T? = try {
        if (!file.exists()) null else AppJson.decodeFromString(serializer, atomic.readFully().toString(Charsets.UTF_8))
    } catch (e: Exception) {
        Log.w("JsonStore", "Discarding unreadable ${file.name}", e)
        null
    }

    private fun write(value: T) {
        val out = atomic.startWrite()
        try {
            out.write(AppJson.encodeToString(serializer, value).toByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            Log.w("JsonStore", "Write failed for ${file.name}", e)
        }
    }
}
