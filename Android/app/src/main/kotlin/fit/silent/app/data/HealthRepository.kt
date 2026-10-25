package fit.silent.app.data

import fit.silent.core.model.HealthDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

@Serializable
data class HealthArchive(val days: Map<String, HealthDay> = emptyMap())

/** Day-keyed health history merged from watch syncs; kept for [RETENTION_DAYS]. */
class HealthRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonStore(File(dir, "health.json"), HealthArchive.serializer(), { HealthArchive() }, scope, debounceMs = 2000)
    val archive: StateFlow<HealthArchive> = store.flow

    fun day(date: LocalDate): HealthDay = archive.value.days[key(date)] ?: HealthDay(key(date))

    fun merge(updated: Map<String, HealthDay>) = store.update { current ->
        val cutoff = key(LocalDate.now().minusDays(RETENTION_DAYS))
        HealthArchive((current.days + updated).filterKeys { it >= cutoff }.toSortedMap())
    }

    fun clear() = store.set(HealthArchive())

    companion object {
        const val RETENTION_DAYS = 400L
        fun key(date: LocalDate) = HealthDay.key(date.year, date.monthValue, date.dayOfMonth)
    }
}
