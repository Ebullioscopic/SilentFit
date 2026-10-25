package fit.silent.app

import fit.silent.app.facestudio.Presets
import java.io.File
import kotlin.test.Test

/** Writes the built-in presets as JSON for the website (`gradle :app:testDebugUnitTest -PexportPresets=<file>`). */
class ExportPresetsTest {
    @Test fun exportPresets() {
        val target = System.getenv("SILENTFIT_EXPORT_PRESETS") ?: return
        val body = Presets.all.joinToString(",\n") { p ->
            val spec = p.spec.copy(name = p.title).toJson().trim()
            "  \"${p.key}\": {\"category\": \"${if (p.isAnalogue) "analogue" else "digital"}\", \"description\": ${kotlinx.serialization.json.JsonPrimitive(p.description)}, \"spec\": $spec}"
        }
        File(target).writeText("{\n$body\n}\n")
    }
}
