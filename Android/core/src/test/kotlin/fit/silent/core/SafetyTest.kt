package fit.silent.core

import fit.silent.core.model.FitnessRecordId
import fit.silent.core.model.HeartRateSettings
import fit.silent.core.model.LauncherList
import fit.silent.core.model.UserProfile
import fit.silent.core.protocol.BerryCommand
import fit.silent.core.protocol.BerryFrame
import fit.silent.core.protocol.CommandPolicy
import fit.silent.core.protocol.Commands
import fit.silent.core.protocol.FileType
import fit.silent.core.protocol.MenuId
import fit.silent.core.watchface.Anchor
import fit.silent.core.watchface.Binding
import fit.silent.core.watchface.FaceCompiler
import fit.silent.core.watchface.FaceElement
import fit.silent.core.watchface.FaceRasterizer
import fit.silent.core.watchface.FaceRecord
import fit.silent.core.watchface.FaceSafety
import fit.silent.core.watchface.FaceSpec
import fit.silent.core.watchface.GlyphStrip
import fit.silent.core.watchface.Par
import fit.silent.core.watchface.Raster
import fit.silent.core.watchface.WatchFaceFile
import java.io.File
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards that keep SilentFit from sending anything the NF 2 has not been seen to accept. */
class SafetyTest {
    private fun hex(s: String) = s.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
    private fun frame(serial: Int, c: BerryCommand) = BerryFrame.encode(serial, c.event, c.type, c.payload)

    // ------------------------------------------------------------ commands

    /** Frames captured from NoiseFit pairing with this watch (firmware 1.0.39) after a factory reset. */
    @Test fun provenCommandsMatchOfficialAppBytes() {
        assertContentEquals(hex("66 66 48 5A 00 03 02 04 00 01 00 00 00"), frame(0, Commands.hello()))
        assertContentEquals(hex("66 66 48 5A 02 03 02 04 00 03 00 00 00"), frame(2, Commands.bindRequest()))
        assertContentEquals(hex("66 66 48 5A 03 03 02 05 00 04 00 01 00 01"), frame(3, Commands.bindConfirm(true)))
        assertContentEquals(hex("66 66 48 5A 04 01 03 04 00 08 02 10 01"), frame(4, Commands.battery()))
        assertContentEquals(hex("66 66 48 5A 05 01 03 04 00 08 02 10 00"), frame(5, Commands.deviceInfo()))
        assertContentEquals(
            hex("66 66 48 5A 06 01 03 10 00 08 03 10 00 32 0A 0A 08 08 A7 C7 F8 D6 06 10 2C"),
            frame(6, Commands.setTime(1792943015219, TimeZone.getTimeZone("Asia/Kolkata"))),
        )
        assertContentEquals(hex("66 66 48 5A 08 01 03 0C 00 08 0B 10 8A 01 72 05 D2 03 02 08 0F"), frame(8, Commands.fitnessIdList(FileType.DAILY)))
        assertContentEquals(hex("66 66 48 5A 31 01 03 04 00 08 09 10 05"), frame(0x31, Commands.quickReplies()))
        assertContentEquals(hex("66 66 48 5A 32 01 03 08 00 08 09 10 04 62 02 20 01"), frame(0x32, Commands.callState(1)))
        assertContentEquals(hex("66 66 48 5A 5D 01 03 04 00 08 08 10 01"), frame(0x5D, Commands.findWatch()))
    }

    @Test fun commandPolicyAllowsOnlyObservedCommands() {
        val id = FitnessRecordId(ByteArray(10), 2026, 10, 25, 1, 10)
        val proven = listOf(
            Commands.hello(), Commands.userId("a", "b", "c"), Commands.bindRequest(), Commands.bindConfirm(true),
            Commands.battery(), Commands.deviceInfo(), Commands.setTime(0, TimeZone.getTimeZone("UTC")),
            Commands.appNotification("a", "b", "c", "d", "e"), Commands.systemNotification(0, "1", "a", "", "b", "c"),
            Commands.callState(1), Commands.quickReplies(), Commands.findWatch(), Commands.liveData(true),
            Commands.fitnessIdList(FileType.DAILY), Commands.requestFitnessFile(FileType.DAILY, listOf(id)), Commands.deleteFitnessIds(FileType.DAILY, listOf(id)),
            Commands.get(MenuId.HEART_RATE), Commands.watchFaces(), Commands.setWatchFace("1"), Commands.removeWatchFace("1"),
            Commands.prepareFaceInstall("1", 100),
        )
        proven.forEach { assertTrue(CommandPolicy.isProven(it), it.label) }
        val experimental = listOf(
            Commands.setUserProfile(UserProfile()), Commands.setUnits(true), Commands.setTemperatureUnit(true), Commands.getLanguages(),
            Commands.setLanguage(1), Commands.setHeartRate(HeartRateSettings()), Commands.setVibration(1), Commands.setPowerSaving(true),
            Commands.getLauncher(LauncherList.APPS), Commands.setLauncher(LauncherList.APPS, emptyList()), Commands.setQuickReplies(listOf("x")),
            Commands.phoneFoundStop(), Commands.faceSupportData(), Commands.editFace("1", true),
        )
        experimental.forEach { assertFalse(CommandPolicy.isProven(it), it.label) }
    }

    // ------------------------------------------------------------ faces

    private val raster = object : FaceRasterizer {
        override fun background(spec: FaceSpec) = Raster(spec.width, spec.height, IntArray(spec.width * spec.height) { 0xFF000000.toInt() })
        override fun glyphs(spec: FaceSpec, element: FaceElement, frames: List<String>, at: Pair<Int, Int>?) =
            GlyphStrip(8, 12, frames.size, IntArray(8 * 12 * frames.size) { 0xFFFFFFFF.toInt() })
        override fun hand(spec: FaceSpec, element: FaceElement) = handAt(spec, pointingRight = true)
        override fun preview(spec: FaceSpec, width: Int, height: Int) = Raster(width, height, IntArray(width * height) { 0xFF000000.toInt() })
    }

    @Test fun vendorFacesPassSafetyCheck() {
        officialSamples().forEach { assertEquals(emptyList(), FaceSafety.check(it.readBytes()), it.name) }
    }

    private fun compileAll(bindings: List<Binding>): ByteArray {
        val elements = bindings.mapIndexed { i, b ->
            val type = when (b.kind.name) { "HAND" -> "hand"; "SPRITE" -> "sprite"; else -> "number" }
            FaceElement("e$i", type, 10 + (i % 4) * 50, 20 + (i / 4) * 40, bind = b.key)
        }
        return FaceCompiler(raster).compile(FaceSpec(elements = elements)).bytes
    }

    private val splitDigits = setOf(Binding.HOUR_TENS, Binding.HOUR_UNITS, Binding.MINUTE_TENS, Binding.MINUTE_UNITS)

    @Test fun compiledFaceWithEveryVerifiedBindingPasses() {
        assertEquals(emptyList(), FaceSafety.check(compileAll(Binding.entries.filter { it.verified && it !in splitDigits })))
        assertEquals(emptyList(), FaceSafety.check(compileAll(Binding.entries.filter { it.verified && it != Binding.HOUR && it != Binding.MINUTE })))
    }

    @Test fun digitalTimeCompilesToOneRecordPerDigit() {
        val bytes = compileAll(listOf(Binding.HOUR, Binding.MINUTE))
        val ids = WatchFaceFile.decode(bytes).records.drop(2).map { it.dataId }
        assertEquals(listOf(0x08, 0x09, 0x0A, 0x0B), ids)
        assertFalse(FaceSafety.isSafe(compileAll(listOf(Binding.HOUR, Binding.HOUR_TENS))), "hour digit placed twice")
    }

    private fun face(vararg extra: FaceRecord, width: Int = 240, height: Int = 280): ByteArray {
        val bg = Par.encode(IntArray(width * height) { 0xFF000000.toInt() }, width, height)
        val preview = Par.encode(IntArray(164 * 191) { 0xFF000000.toInt() }, 164, 191)
        val digits = Par.encode(IntArray(10 * 120) { 0xFFFFFFFF.toInt() }, 10, 120)
        val records = listOf(
            FaceRecord(2, 0x10, 1, width / 2, height / 2, 0, compact = true, anchor = Anchor.CENTER),
            FaceRecord(2, 0x11, 1, 0, 0, 1, compact = true, anchor = Anchor.CENTER),
        ) + extra
        return WatchFaceFile(width, height, records, listOf(bg, preview, digits)).encode()
    }

    @Test fun rejectsHandsNotDrawnAtThreeOClock() {
        // A hand drawn pointing at 12 made the watch show the wrong time (hands looked stuck).
        val twelve = object : FaceRasterizer by raster {
            override fun hand(spec: FaceSpec, element: FaceElement) = handAt(spec, pointingRight = false)
        }
        val spec = FaceSpec(elements = listOf(FaceElement("m", "hand", bind = Binding.MINUTE_HAND.key)))
        assertFalse(FaceSafety.isSafe(FaceCompiler(twelve).compile(spec).bytes))
        assertEquals(emptyList(), FaceSafety.check(FaceCompiler(raster).compile(spec).bytes))
    }

    @Test fun centredNumbersStoreOffsetFromScreenCentre() {
        // Official sport face: steps centred on the left dial (x≈58) is stored as -62, calories on the right as +62.
        val spec = FaceSpec(elements = listOf(
            FaceElement("s", "number", 14, 120, 88, bind = Binding.STEPS.key, align = "center"),
            FaceElement("k", "number", 138, 120, 88, bind = Binding.CALORIES.key, align = "center"),
            FaceElement("d", "number", 0, 200, 240, bind = Binding.DAY.key, align = "center"),
        ))
        val records = WatchFaceFile.decode(FaceCompiler(raster).compile(spec).bytes).records.drop(2)
        assertEquals(listOf(-62, 62, 0), records.map { it.x })
        assertTrue(records.all { it.anchor == Anchor.CENTER })
    }

    @Test fun minimalFacePasses() {
        assertEquals(emptyList(), FaceSafety.check(face(FaceRecord(4, 0x41, 10, 38, 8, 2))))
    }

    @Test fun rejectsUnprovenRecordIds() {
        // The probe that boot-looped the watch used type-4 ids such as 0x04 and 0x07.
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x04, 10, 38, 8, 2))))
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x07, 10, 38, 8, 2))))
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0xFF, 10, 38, 8, 2, name = "NUMBER5"))))
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x41, 7, 38, 8, 2))))
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x0C, 10, 38, 8, 2))))
    }

    @Test fun rejectsStructuralProblems() {
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x41, 10, 38, 8, 2), FaceRecord(4, 0x41, 10, 80, 8, 2))), "duplicate binding")
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x41, 10, 38, 275, 2))), "off screen")
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0xFF, 10, 38, 8, 2, name = "NUMBER4"))), "heart rate without placeholder")
        assertFalse(FaceSafety.isSafe(face(FaceRecord(4, 0x41, 10, 38, 8, 2), width = 200)), "wrong screen size")
        val ok = face(FaceRecord(4, 0x41, 10, 38, 8, 2))
        assertFalse(FaceSafety.isSafe(ok.copyOf(ok.size - 40)), "truncated")
        assertFalse(FaceSafety.isSafe(ok.copyOf().also { it[0x2E] = 40 }), "record count")
        assertFalse(FaceSafety.isSafe(ok.copyOf().also { it[0x34 + 4 + FaceRecord.SHORT_BODY * 2 + 8 + 4 + 20] = 7 }), "layout tail")
        assertFalse(FaceSafety.isSafe(ByteArray(10)), "not a face")
    }
}
