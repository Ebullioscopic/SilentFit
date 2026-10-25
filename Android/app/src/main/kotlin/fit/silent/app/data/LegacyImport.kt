package fit.silent.app.data

import android.content.Context
import android.util.Log
import fit.silent.core.model.Sex
import fit.silent.core.model.UserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.SecureRandom

/** Reads the preferences written by the earlier Skip-based build so pairing survives the upgrade. */
object LegacyImport {
    fun migrate(context: Context, current: AppSettings): AppSettings {
        val legacy = File(context.filesDir, "preferences.json")
        if (!legacy.exists() || current.pairedWatch != null) return current
        return try {
            val root = Json.parseToJsonElement(legacy.readText()).jsonObject
            val watch = root["pairedWatch"]?.jsonObject
            val p = root["profile"]?.jsonObject
            fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
            current.copy(
                pairedWatch = watch?.let {
                    val address = it["address"]?.jsonPrimitive?.content ?: return@let null
                    PairedWatch(it["name"]?.jsonPrimitive?.content ?: address, address)
                },
                accountId = root["accountId"]?.jsonPrimitive?.content ?: current.accountId,
                deleteAfterSync = root["deleteAfterSync"]?.jsonPrimitive?.booleanOrNull ?: current.deleteAfterSync,
                onboarded = watch != null,
                profile = if (p == null) current.profile else UserProfile(
                    name = p["name"]?.jsonPrimitive?.content ?: "",
                    heightCm = p.int("heightCm") ?: 170,
                    weightKg = p["weightKg"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 65f,
                    age = p.int("age") ?: 30,
                    sex = Sex.entries.getOrElse(p.int("sex") ?: 0) { Sex.UNSPECIFIED },
                    stepGoal = p.int("stepGoal") ?: 8000,
                    calorieGoal = p.int("calorieGoal") ?: 400,
                    distanceGoalMeters = p.int("distanceGoalMeters") ?: 5000,
                    sleepGoalHours = p.int("sleepGoalHours") ?: 8,
                    metric = p["metric"]?.jsonPrimitive?.booleanOrNull ?: true,
                ),
            )
        } catch (e: Exception) {
            Log.w("LegacyImport", "Old preferences ignored", e)
            current
        }
    }

    fun newAccountId(): String {
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
