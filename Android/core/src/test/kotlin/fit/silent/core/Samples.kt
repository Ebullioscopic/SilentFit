package fit.silent.core

import java.io.File

/** Official faces for byte-exact regressions; `SILENTFIT_FACE_SAMPLE` lists them separated by ':' (never committed). */
fun officialSamples(): List<File> =
    System.getenv("SILENTFIT_FACE_SAMPLE").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)
