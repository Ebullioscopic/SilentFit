package fit.silent.core

import fit.silent.core.watchface.Anchor
import fit.silent.core.watchface.Background
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceCompiler
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceRasterizer
import fit.silent.core.watchface.FaceRecord
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.GlyphStrip
import fit.silent.core.watchface.Par
import fit.silent.core.watchface.Raster
import fit.silent.core.watchface.WatchFaceFile
import fit.silent.core.watchface.colorHex
import fit.silent.core.watchface.parseColor
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchFaceTest {
    /** What the watch would display: RGB565-quantised, alpha kept only for alpha images. */
    private fun quantise(p: Int, alpha: Boolean): Int {
        val a = p ushr 24
        var r = (p shr 16) and 0xFF; var g = (p shr 8) and 0xFF; var b = p and 0xFF
        if (!alpha) { r = (r * a * 0x8081) ushr 23; g = (g * a * 0x8081) ushr 23; b = (b * a * 0x8081) ushr 23 }
        r = r shr 3; g = g shr 2; b = b shr 3
        val out = ((r shl 3) or (r shr 2) shl 16) or ((g shl 2) or (g shr 4) shl 8) or ((b shl 3) or (b shr 2))
        return ((if (alpha) a else 0xFF) shl 24) or out
    }

    private fun roundTrip(pixels: IntArray, w: Int, h: Int, alpha: Boolean, tile: Int) {
        val encoded = Par.encode(pixels, w, h, alpha, tile)
        val (image, size) = Par.decode(encoded)
        assertEquals(encoded.size, size)
        assertEquals(w, image.width)
        assertEquals(h, image.height)
        assertContentEquals(IntArray(pixels.size) { quantise(pixels[it], alpha) }, image.pixels)
    }

    @Test fun parRoundTripsNoiseGradientsAndRuns() {
        val rnd = Random(7)
        val w = 61; val h = 37
        roundTrip(IntArray(w * h) { 0xFF000000.toInt() or rnd.nextInt(0xFFFFFF) }, w, h, false, 0)
        roundTrip(IntArray(w * h) { i -> 0xFF000000.toInt() or ((i % w) * 4 shl 16) or ((i / w) * 6 shl 8) }, w, h, false, 0)
        roundTrip(IntArray(w * h) { i -> if ((i / 9) % 2 == 0) 0xFF101010.toInt() else 0xFFFFFFFF.toInt() }, w, h, false, 0)
    }

    @Test fun parTiledAlphaRoundTrip() {
        val w = 240; val h = 280
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (y in 130..150 && x in 120..220) (((x - 120) * 2 + 55) shl 24) or 0xE0E0E0 else 0 }
        roundTrip(px, w, h, true, Par.TILE)
    }

    @Test fun parUsesNarrowOffsetTableForSmallImages() {
        val encoded = Par.encode(IntArray(10 * 10) { 0xFF000000.toInt() }, 10, 10)
        assertEquals(1, encoded[9].toInt())
        assertEquals('P'.code.toByte(), encoded[0])
    }

    @Test fun watchFaceFileRoundTrip() {
        val img = Par.encode(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2)
        val digits = Par.encode(IntArray(6 * 60) { 0xFF00FF00.toInt() }, 6, 60)
        val records = listOf(
            FaceRecord(2, 0x10, 1, 120, 140, 0, compact = true, anchor = Anchor.CENTER),
            FaceRecord(2, 0x11, 1, 0, 0, 0, compact = true, anchor = Anchor.CENTER),
            FaceRecord(4, 0x41, 10, 38, 8, 1),
            FaceRecord(4, 0xFF, 10, -64, 263, 1, name = "NUMBER2", anchor = Anchor.RIGHT),
            FaceRecord(1, 0x05, 1, 120, 140, 0, compact = true, pivotX = 120, pivotY = 140, anchor = Anchor.CENTER),
        )
        val bytes = WatchFaceFile(240, 280, records, listOf(img, digits)).encode()
        val parsed = WatchFaceFile.decode(bytes)
        assertEquals(records, parsed.records)
        assertContentEquals(bytes, parsed.encode())
        assertEquals(bytes.size.toLong(), fit.silent.core.protocol.Bytes.u32le(bytes, 0x30))
    }

    /** Byte-exact regression against a vendor face when one is available locally (never committed). */
    @Test fun vendorSampleReencodesIdentically() {
        for (file in officialSamples()) {
            val original = file.readBytes()
            val face = WatchFaceFile.decode(original)
            assertContentEquals(original, face.encode(), file.name)
            for (img in face.images) {
                val (image, size) = Par.decode(img)
                assertEquals(img.size, size)
                val reencoded = Par.encode(image.pixels, image.width, image.height, image.hasAlpha, image.tile)
                assertContentEquals(img, reencoded)
            }
        }
    }

    @Test fun specJsonRoundTripAndColors() {
        val spec = FaceSpec(
            name = "Dots", background = Background(color = "#000000", pattern = "dots"),
            elements = listOf(FaceElement("h", "number", 20, 40, bind = Binding.HOUR.key, font = "doto", size = 72f)),
        )
        val parsed = FaceSpec.fromJson(spec.toJson())
        assertEquals(spec, parsed)
        assertTrue(spec.toJson().contains("\"format\": \"silentfit.face\""))
        assertEquals(0xFFD71921.toInt(), parseColor("#D71921"))
        assertEquals(0xFFFFFFFF.toInt(), parseColor("#fff"))
        assertEquals("#D71921", colorHex(0xFFD71921.toInt()))
    }

    @Test fun compilerProducesOrderedRecords() {
        val raster = object : FaceRasterizer {
            override fun background(spec: FaceSpec) = Raster(spec.width, spec.height, IntArray(spec.width * spec.height) { 0xFF000000.toInt() })
            override fun glyphs(spec: FaceSpec, element: FaceElement, frames: List<String>) =
                GlyphStrip(8, 12, frames.size, IntArray(8 * 12 * frames.size) { 0xFFFFFFFF.toInt() })
            override fun hand(spec: FaceSpec, element: FaceElement) = Raster(spec.width, spec.height, IntArray(spec.width * spec.height))
            override fun preview(spec: FaceSpec, width: Int, height: Int) = Raster(width, height, IntArray(width * height) { 0xFF000000.toInt() })
        }
        val spec = FaceSpec(
            elements = listOf(
                FaceElement("min", "hand", bind = Binding.MINUTE_HAND.key),
                FaceElement("hr", "number", 30, 250, bind = Binding.HEART_RATE.key),
                FaceElement("bat", "number", 150, 250, w = 40, bind = Binding.BATTERY.key, align = "right"),
                FaceElement("hour", "hand", bind = Binding.HOUR_HAND.key),
            ),
        )
        val compiled = FaceCompiler(raster).compile(spec)
        val parsed = WatchFaceFile.decode(compiled.bytes)
        val kinds = parsed.records.map { it.type to (it.name.ifEmpty { "%02X".format(it.dataId) }) }
        assertEquals(listOf(2 to "10", 2 to "11", 4 to "N_DATA4", 4 to "NUMBER4", 4 to "NUMBER2", 1 to "05", 1 to "06"), kinds)
        val battery = parsed.records.first { it.name == "NUMBER2" }
        assertEquals(Anchor.RIGHT, battery.anchor)
        assertEquals(-(240 - 190), battery.x)
        assertTrue(compiled.warnings.isEmpty(), compiled.warnings.toString())
    }
}
