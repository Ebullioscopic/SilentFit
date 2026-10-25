package fit.silent.core

import fit.silent.core.watchface.FaceSafety
import fit.silent.core.watchface.WatchFaceFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Faces compiled by the website studio (`SILENTFIT_WEB_FACES=<dir of .bin>`) must pass the app's safety gate. */
class WebStudioFacesTest {
    @Test fun websiteFacesAreSafe() {
        val dir = System.getenv("SILENTFIT_WEB_FACES")?.let(::File) ?: return
        val files = dir.listFiles { f -> f.extension == "bin" }.orEmpty().sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "no .bin files in $dir")
        for (f in files) {
            val bytes = f.readBytes()
            assertEquals(emptyList(), FaceSafety.check(bytes), f.name)
            val decoded = WatchFaceFile.decode(bytes)
            assertTrue(decoded.encode().contentEquals(bytes), "${f.name} re-encodes byte for byte")
        }
        println("checked ${files.size} website faces")
    }
}
