package fit.silent.core

import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.Raster
import java.io.File

/** Official faces for byte-exact regressions; `SILENTFIT_FACE_SAMPLE` lists them separated by ':' (never committed). */
fun officialSamples(): List<File> =
    System.getenv("SILENTFIT_FACE_SAMPLE").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)

/** A 4 px wide opaque bar from the screen centre, to 3 o'clock or to 12 o'clock. */
fun handAt(spec: FaceSpec, pointingRight: Boolean): Raster {
    val cx = spec.width / 2; val cy = spec.height / 2
    return Raster(spec.width, spec.height, IntArray(spec.width * spec.height) { i ->
        val x = i % spec.width; val y = i / spec.width
        val on = if (pointingRight) x in cx..cx + 80 && y in cy - 2..cy + 2 else y in cy - 80..cy && x in cx - 2..cx + 2
        if (on) -1 else 0
    })
}
