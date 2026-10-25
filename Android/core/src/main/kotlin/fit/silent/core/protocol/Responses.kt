package fit.silent.core.protocol

import fit.silent.core.model.AlwaysOnSettings
import fit.silent.core.model.AodStyle
import fit.silent.core.model.BatteryStatus
import fit.silent.core.model.ChargeState
import fit.silent.core.model.HeartRateSettings
import fit.silent.core.model.InstalledFace
import fit.silent.core.model.LauncherItem
import fit.silent.core.model.LiveData
import fit.silent.core.model.RaiseToWakeSettings
import fit.silent.core.model.ReminderSettings
import fit.silent.core.model.ScheduleSettings
import fit.silent.core.model.ScreenSettings
import fit.silent.core.model.Sensitivity
import fit.silent.core.model.SleepModeSettings
import fit.silent.core.model.SpO2Settings
import fit.silent.core.model.StressSettings
import fit.silent.core.model.WakeMode
import fit.silent.core.model.WatchAlarm
import fit.silent.core.model.WatchInfo
import fit.silent.core.model.WatchNotificationSettings
import fit.silent.core.model.WorkoutDetection

/** A parsed SA.SEWear envelope. */
class WearMessage(val type: Int, val id: Int, val errorCode: Int, val root: ProtoMessage) {
    val isError get() = errorCode != 0
    fun body(field: Int): ProtoMessage? = root.message(field)

    companion object {
        fun parse(payload: ByteArray): WearMessage? {
            val root = ProtoMessage.parse(payload) ?: return null
            val type = root.int(WearField.TYPE) ?: return null
            val error = root.message(WearField.ERROR)?.int(1) ?: root.int(WearField.ERROR) ?: 0
            return WearMessage(type, root.int(WearField.ID) ?: 0, error, root)
        }
    }
}

class PrivateReply(val cmdId: Int, val data: ByteArray)
class ProtocolInfo(val deviceType: Int, val protocolVersion: String, val platformType: Int)
class BindInfo(val deviceType: Int, val firmware: String, val mac: String, val isBound: Boolean, val isSameUser: Boolean)
class PrepareReply(val id: String, val status: Int, val compressMode: Int, val expectedSlice: Int, val screenRadius: Int)
class InstallResult(val id: String, val code: Int) { val success get() = code == 2 || code == 3 }
class EditResponse(val id: String, val code: Int, val expectedSlice: Int) { val ok get() = code == 0 || code == 1 }

object Responses {
    /**
     * Private replies: cmdId u16 + length u16 + data. The watch's length field can be shorter than the
     * bytes it actually sends (bind info), so every remaining byte is kept like the vendor SDK does.
     */
    fun privateReply(payload: ByteArray) = PrivateReply(Bytes.u16le(payload, 0), payload.copyOfRange(minOf(4, payload.size), payload.size))

    fun protocolInfo(d: ByteArray) = ProtocolInfo(
        Bytes.u16le(d, 0), "${Bytes.u8(d, 4)}.${Bytes.u8(d, 3)}.${Bytes.u8(d, 2)}", Bytes.u8(d, 9),
    )

    fun bindInfo(d: ByteArray): BindInfo {
        val mac = (10 downTo 5).joinToString(":") { "%02X".format(Bytes.u8(d, it)) }
        val flags = Bytes.u8(d, 18 + Bytes.u8(d, 17))
        return BindInfo(Bytes.u16le(d, 0), "${Bytes.u8(d, 4)}.${Bytes.u8(d, 3)}.${Bytes.u8(d, 2)}", mac, flags and 0x40 != 0, flags and 0x80 != 0)
    }

    fun battery(m: ProtoMessage) = BatteryStatus(m.int(1) ?: 0, ChargeState.of(m.int(2) ?: 0))

    fun deviceInfo(m: ProtoMessage, base: WatchInfo) = base.copy(
        firmware = m.string(1) ?: base.firmware,
        model = m.string(2) ?: base.model,
        serial = m.string(4) ?: base.serial,
    )

    fun watchFaces(face: ProtoMessage): List<InstalledFace> {
        val list = face.message(1) ?: return emptyList()
        return list.messages(1).map { item ->
            val id = item.string(1).orEmpty()
            val name = item.string(2).orEmpty()
            InstalledFace(
                id = id,
                name = name.ifEmpty { id },
                isCurrent = item.bool(3) ?: false,
                canRemove = item.bool(4) ?: false,
                versionCode = item.long(5) ?: 0,
                canEdit = item.bool(6) ?: false,
                backgroundColor = item.string(7),
                style = item.string(9),
                dataSlots = item.ints(10),
                styleColorIndex = item.int(15),
            )
        }
    }

    fun faceSupportData(face: ProtoMessage): List<Int> = face.message(11)?.ints(1) ?: emptyList()

    fun prepareReply(face: ProtoMessage): PrepareReply? {
        val r = face.message(9) ?: return null
        return PrepareReply(r.string(1).orEmpty(), r.int(2) ?: 0, r.int(3) ?: 0, r.int(4) ?: 0, r.int(6) ?: 0)
    }

    fun installResult(face: ProtoMessage): InstallResult? = face.message(7)?.let { InstallResult(it.string(1).orEmpty(), it.int(2) ?: 1) }

    fun editResponse(face: ProtoMessage): EditResponse? = face.message(13)?.let { EditResponse(it.string(1).orEmpty(), it.int(2) ?: 4, it.int(4) ?: 0) }

    fun quickReplies(notification: ProtoMessage): List<String> =
        notification.message(5)?.messages(1)?.mapNotNull { it.string(1)?.takeIf { s -> s.isNotEmpty() } } ?: emptyList()

    fun liveData(m: ProtoMessage, now: Long) = LiveData(
        steps = m.int(1) ?: 0, calories = m.int(2) ?: 0, distanceMeters = m.int(3) ?: 0, heartRate = m.int(4) ?: 0,
        spo2 = m.int(5) ?: 0, standingHours = m.int(8) ?: m.int(6) ?: 0, sleepMinutes = m.int(9) ?: 0,
        activeMinutes = m.int(12) ?: 0, stress = m.int(13) ?: 0, at = now,
    )

    fun launcher(list: ProtoMessage): List<LauncherItem> = list.messages(1).map {
        LauncherItem(it.int(1) ?: 0, it.bool(2) ?: true, it.int(3) ?: 0, it.bool(4) ?: false, it.bool(5) ?: true)
    }.sortedBy { it.order }

    private fun minutes(t: ProtoMessage?) = if (t == null) 0 else (t.int(1) ?: 0) * 60 + (t.int(2) ?: 0)

    fun heartRate(m: ProtoMessage) = HeartRateSettings(
        enabled = (m.int(1) ?: 0) == 0, intervalMinutes = m.int(2) ?: 5, highAlertEnabled = m.bool(3) ?: false,
        highAlertBpm = m.int(4) ?: 120, lowAlertBpm = m.int(8) ?: 0, smartMode = (m.int(7) ?: 0) == 1,
    )

    fun spo2(m: ProtoMessage) = SpO2Settings((m.int(1) ?: 0) == 0, m.int(2) ?: 10)

    fun stress(m: ProtoMessage) = StressSettings(m.bool(1) ?: false, m.bool(2) ?: false)

    fun doNotDisturb(m: ProtoMessage) = ScheduleSettings(m.bool(1) ?: false, minutes(m.message(2)), minutes(m.message(3)), m.bool(4) ?: false)

    fun raiseToWake(m: ProtoMessage) = RaiseToWakeSettings(
        WakeMode.entries.getOrElse(m.int(1) ?: 0) { WakeMode.ALL_DAY }, minutes(m.message(2)), minutes(m.message(3)),
        Sensitivity.entries.getOrElse(m.int(4) ?: 1) { Sensitivity.MEDIUM },
    )

    fun reminder(m: ProtoMessage) = ReminderSettings(m.bool(1) ?: false, minutes(m.message(2)), minutes(m.message(3)), m.int(4) ?: 60, m.bool(5) ?: false)

    fun alarms(m: ProtoMessage): List<WatchAlarm> = m.messages(1).mapNotNull { item ->
        val data = item.message(2) ?: return@mapNotNull null
        WatchAlarm(item.int(1) ?: 0, minutes(data.message(1)), data.int(2) ?: 0, data.bool(3) ?: false, data.string(4) ?: "Alarm")
    }

    fun screen(m: ProtoMessage) = ScreenSettings(m.int(1) ?: 3, m.bool(2) ?: false, m.int(3) ?: 5, m.bool(4) ?: false)

    fun alwaysOn(m: ProtoMessage) = AlwaysOnSettings(
        WakeMode.entries.getOrElse(m.int(1) ?: 2) { WakeMode.OFF }, minutes(m.message(2)), minutes(m.message(3)),
        AodStyle.entries.getOrElse(m.int(4) ?: 1) { AodStyle.DIGITAL },
    )

    fun sleepMode(m: ProtoMessage) = SleepModeSettings(m.bool(1) ?: false, minutes(m.message(2)), minutes(m.message(3)), m.bool(6) ?: false)

    fun watchNotifications(m: ProtoMessage) = WatchNotificationSettings(m.bool(1) ?: false, m.bool(5) ?: false, m.bool(6) ?: false, m.bool(4) ?: false)

    fun workoutDetection(m: ProtoMessage) = WorkoutDetection(m.bool(1) ?: false, m.bool(2) ?: false)
}
