package fit.silent.core.protocol

import fit.silent.core.model.AlwaysOnSettings
import fit.silent.core.model.FitnessRecordId
import fit.silent.core.model.HeartRateSettings
import fit.silent.core.model.LauncherItem
import fit.silent.core.model.LauncherList
import fit.silent.core.model.NowPlaying
import fit.silent.core.model.RaiseToWakeSettings
import fit.silent.core.model.ReminderKind
import fit.silent.core.model.ReminderSettings
import fit.silent.core.model.ScheduleSettings
import fit.silent.core.model.ScreenSettings
import fit.silent.core.model.SleepModeSettings
import fit.silent.core.model.SpO2Settings
import fit.silent.core.model.StressSettings
import fit.silent.core.model.UserProfile
import fit.silent.core.model.WatchAlarm
import fit.silent.core.model.WatchNotificationSettings
import fit.silent.core.model.WorkoutDetection
import java.util.TimeZone

/** SA.SEWear.Type */
object WearType {
    const val DEVICE = 2
    const val SYSTEM_TIME = 3
    const val USER_SETTINGS = 4
    const val MICRO = 8
    const val NOTIFICATION = 9
    const val MUSIC = 10
    const val SETTING_MENU = 11
    const val WATCH_FACE = 14
    const val WEATHER = 15
}

/** SA.SEWear oneof field numbers. */
object WearField {
    const val TYPE = 1
    const val ID = 2
    const val DEVICE = 5
    const val SYSTEM_TIME = 6
    const val USER_SETTINGS = 7
    const val MICRO = 11
    const val NOTIFICATION = 12
    const val MUSIC = 13
    const val SETTING_MENU = 14
    const val WATCH_FACE = 17
    const val WEATHER = 18
    const val ERROR = 100
}

object DeviceId { const val INFO = 0; const val BATTERY = 1 }

object UserSettingId {
    const val SET_LANGUAGE = 0; const val GET_LANGUAGES = 1; const val SET_UNIT = 2; const val SET_TEMPERATURE = 3
    const val SET_PROFILE = 4; const val GET_PROFILE = 6
}

object MicroId {
    const val WEAR_FIND_PHONE = 0; const val FIND_WEAR = 1; const val BASIC_DATA_REQUEST = 4; const val BASIC_DATA_REPORT = 5
    const val SET_WIDGETS = 6; const val GET_WIDGETS = 7; const val SET_APPS = 8; const val GET_APPS = 9
    const val PHONE_FIND_PHONE = 10; const val SET_QUICK = 11; const val GET_QUICK = 12
}

object NotificationId {
    const val SYSTEM = 2; const val APP = 3; const val CALL_STATE = 4; const val GET_REPLIES = 5; const val SET_REPLIES = 6; const val REPLY_MESSAGE = 7
}

object MusicId { const val REQUEST_STATE = 0; const val COMMAND = 1; const val QUIT = 2; const val INFO = 3 }

/** SESettingmenu_SettingmenuID: each setting has GET (n), SET (n+1), watch-initiated REQUEST (n+2). */
object MenuId {
    const val WRIST = 0; const val HEART_RATE = 6; const val SEDENTARY = 9; const val ALARMS = 12; const val MEDICATION = 15
    const val DRINK = 18; const val DND = 21; const val VIBRATION = 24; const val POWER_SAVING = 27; const val COVER = 30
    const val AOD = 33; const val SCREEN = 39; const val REM = 43; const val WORKOUT = 46; const val MEALS = 55; const val WASH = 58
    const val SLEEP_MODE = 73; const val STRESS = 76; const val NOTIFY = 79; const val SPO2 = 82
    const val FILE_SEND_STATUS = 136; const val DEVICE_FILE_STATUS = 137; const val FITNESS_IDS = 138; const val DELETE_FITNESS = 139

    fun set(get: Int) = get + 1
    fun isWatchChange(id: Int) = id in WATCH_CHANGE_IDS
    private val WATCH_CHANGE_IDS = setOf(2, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 41, 45, 48, 57, 60, 75, 78, 81, 84)
    /** Maps a watch-initiated REQUEST id back to its GET id. */
    fun getFor(request: Int) = request - 2
}

/** SESettingMenu oneof field numbers. */
object MenuField {
    const val WRIST = 1; const val HEART_RATE = 3; const val SEDENTARY = 4; const val ALARMS = 5; const val MEDICATION = 6; const val DRINK = 7
    const val DND = 8; const val VIBRATION = 9; const val POWER_SAVING = 10; const val COVER = 11; const val AOD = 12; const val SCREEN = 14
    const val REM = 15; const val WORKOUT = 16; const val MEALS = 21; const val WASH = 22; const val SLEEP_MODE = 27; const val STRESS = 28
    const val NOTIFY = 29; const val SPO2 = 30
    const val FILE_SEND_STATUS = 56; const val DEVICE_FILE_STATUS = 57; const val FITNESS_IDS = 58; const val FITNESS_ID_LIST = 59; const val DELETE_FITNESS = 60
}

object FaceId {
    const val LIST = 0; const val SET = 1; const val REMOVE = 2; const val PREPARE = 4; const val INSTALL_RESULT = 5
    const val SUPPORT_DATA = 10; const val EDIT = 11; const val BG_RESULT = 12; const val WATCH_CHANGED = 13
}

object PrivateId { const val HELLO = 1; const val USER_ID = 2; const val BIND_REQUEST = 3; const val BIND_CONFIRM = 4 }

/** Large-file categories (frame type byte for event 2, SELog_File_TYPE_ID). */
object FileType { const val OTA = 4; const val WATCH_FACE = 5; const val SPORT = 7; const val ALBUM_BACKGROUND = 14; const val DAILY = 15 }

/** [wearType] is -1 for private (connection) commands; [id] is the wear id or private command id. */
class BerryCommand(val event: Int, val type: Int, val payload: ByteArray, val label: String, val wearType: Int = -1, val id: Int = -1) {
    companion object {
        fun wear(wearType: Int, id: Int, field: Int = 0, body: ProtoWriter? = null, label: String): BerryCommand {
            val w = ProtoWriter().uint(WearField.TYPE, wearType).uint(WearField.ID, id)
            if (body != null) w.message(field, body)
            return BerryCommand(BerryFrame.EVENT_PROTOBUF, BerryFrame.TYPE_PROTOBUF, w.bytes, label, wearType, id)
        }

        fun private(cmdId: Int, data: ByteArray = ByteArray(0), label: String) =
            BerryCommand(BerryFrame.EVENT_PRIVATE, BerryFrame.TYPE_PRIVATE, BerryFrame.privatePayload(cmdId, data), label, -1, cmdId)
    }
}

/**
 * Which commands may reach the watch.
 *
 * [PROVEN] commands were captured from the official NoiseFit app talking to this watch on firmware 1.0.39
 * (including a fresh pairing after a factory reset), or are read-only requests that earlier SilentFit builds
 * sent many times without issue. Everything else — every settings write, profile/unit/language writes,
 * launcher edits, album faces — has a field layout that was reconstructed rather than observed, so it is
 * [EXPERIMENTAL] and only sent when the user explicitly enables experimental watch controls.
 */
object CommandPolicy {
    enum class Tier { PROVEN, EXPERIMENTAL }

    private val PROVEN_PRIVATE = setOf(PrivateId.HELLO, PrivateId.USER_ID, PrivateId.BIND_REQUEST, PrivateId.BIND_CONFIRM)

    private val PROVEN_WEAR: Map<Int, Set<Int>> = mapOf(
        WearType.DEVICE to setOf(DeviceId.INFO, DeviceId.BATTERY),
        WearType.SYSTEM_TIME to setOf(0),
        WearType.MICRO to setOf(MicroId.FIND_WEAR, MicroId.BASIC_DATA_REQUEST),
        WearType.NOTIFICATION to setOf(NotificationId.SYSTEM, NotificationId.APP, NotificationId.CALL_STATE, NotificationId.GET_REPLIES),
        WearType.MUSIC to setOf(MusicId.INFO),
        WearType.SETTING_MENU to setOf(MenuId.FILE_SEND_STATUS, MenuId.FITNESS_IDS, MenuId.DELETE_FITNESS) + SETTING_GETS,
        // Face list, switch, remove and the PREPARE → upload → result install flow (validated with the official face).
        WearType.WATCH_FACE to setOf(FaceId.LIST, FaceId.SET, FaceId.REMOVE, FaceId.PREPARE),
    )

    /** Read-only settings GETs the watch answered in earlier sessions. */
    val SETTING_GETS: Set<Int>
        get() = setOf(
            MenuId.HEART_RATE, MenuId.SPO2, MenuId.STRESS, MenuId.DND, MenuId.WRIST, MenuId.SEDENTARY, MenuId.DRINK,
            MenuId.MEDICATION, MenuId.MEALS, MenuId.WASH, MenuId.ALARMS, MenuId.VIBRATION, MenuId.POWER_SAVING, MenuId.COVER,
            MenuId.SCREEN, MenuId.AOD, MenuId.SLEEP_MODE, MenuId.NOTIFY, MenuId.WORKOUT, MenuId.REM,
        )

    fun tier(c: BerryCommand): Tier {
        val proven = when (c.event) {
            BerryFrame.EVENT_PRIVATE -> c.id in PROVEN_PRIVATE
            BerryFrame.EVENT_PROTOBUF -> PROVEN_WEAR[c.wearType]?.contains(c.id) == true
            else -> false
        }
        return if (proven) Tier.PROVEN else Tier.EXPERIMENTAL
    }

    fun isProven(c: BerryCommand) = tier(c) == Tier.PROVEN
}

/** Builders for every command SilentFit sends. Field layouts follow docs/PROTOCOL.md. */
object Commands {
    // -------------------------------------------------------------- connection

    fun hello() = BerryCommand.private(PrivateId.HELLO, label = "hello")

    /** `[len][userId] 0xBB [len][phoneName] [len][systemVersion]` */
    fun userId(userId: String, phoneName: String, systemVersion: String): BerryCommand {
        val uid = userId.take(100).toByteArray()
        val name = phoneName.take(60).toByteArray()
        val ver = systemVersion.take(50).toByteArray()
        val data = ByteSink().u8(uid.size).bytes(uid).u8(0xBB).u8(name.size).bytes(name).u8(ver.size).bytes(ver).toByteArray()
        return BerryCommand.private(PrivateId.USER_ID, data, "userId")
    }

    fun bindRequest() = BerryCommand.private(PrivateId.BIND_REQUEST, label = "bindRequest")

    fun bindConfirm(ok: Boolean) = BerryCommand.private(PrivateId.BIND_CONFIRM, Bytes.of(if (ok) 1 else 0), "bindConfirm")

    // -------------------------------------------------------------- device

    fun deviceInfo() = BerryCommand.wear(WearType.DEVICE, DeviceId.INFO, label = "deviceInfo")

    fun battery() = BerryCommand.wear(WearType.DEVICE, DeviceId.BATTERY, label = "battery")

    /** UTC seconds plus the zone offset in quarter hours. */
    fun setTime(epochMillis: Long, zone: TimeZone): BerryCommand {
        val quarters = zone.getOffset(epochMillis) / 900_000
        val set = ProtoWriter().uint64(1, epochMillis / 1000).sint(2, quarters)
        return BerryCommand.wear(WearType.SYSTEM_TIME, 0, WearField.SYSTEM_TIME, ProtoWriter().message(1, set), "setTime")
    }

    // -------------------------------------------------------------- micro functions

    fun findWatch() = BerryCommand.wear(WearType.MICRO, MicroId.FIND_WEAR, label = "findWatch")

    fun phoneFoundStop() =
        BerryCommand.wear(WearType.MICRO, MicroId.PHONE_FIND_PHONE, WearField.MICRO, ProtoWriter().uint(1, 1), "findPhoneStop")

    fun liveData(start: Boolean) =
        BerryCommand.wear(WearType.MICRO, MicroId.BASIC_DATA_REQUEST, WearField.MICRO, ProtoWriter().uint(3, if (start) 0 else 1), "liveData")

    fun getLauncher(list: LauncherList) = BerryCommand.wear(WearType.MICRO, launcherIds(list).first, label = "get-$list")

    fun setLauncher(list: LauncherList, items: List<LauncherItem>): BerryCommand {
        val l = ProtoWriter()
        items.forEachIndexed { i, it ->
            l.message(1, ProtoWriter().uint(1, it.function).bool(2, it.enabled).uint(3, i).bool(4, it.hidden).bool(5, it.sortable))
        }
        val field = when (list) { LauncherList.WIDGETS -> 5; LauncherList.APPS -> 6; LauncherList.QUICK -> 7 }
        return BerryCommand.wear(WearType.MICRO, launcherIds(list).second, WearField.MICRO, ProtoWriter().message(field, l), "set-$list")
    }

    /** (get id, set id) */
    fun launcherIds(list: LauncherList) = when (list) {
        LauncherList.WIDGETS -> MicroId.GET_WIDGETS to MicroId.SET_WIDGETS
        LauncherList.APPS -> MicroId.GET_APPS to MicroId.SET_APPS
        LauncherList.QUICK -> MicroId.GET_QUICK to MicroId.SET_QUICK
    }

    // -------------------------------------------------------------- notifications

    fun appNotification(appName: String, packageName: String, title: String, text: String, key: String): BerryCommand {
        val n = ProtoWriter().string(1, appName.take(40)).string(2, packageName).string(3, title.take(80))
            .string(4, text.take(400)).string(5, title.take(80)).string(6, key)
        return BerryCommand.wear(WearType.NOTIFICATION, NotificationId.APP, WearField.NOTIFICATION, ProtoWriter().message(2, n), "appNotification")
    }

    /** kind: 0 incoming call, 1 missed call, 2 SMS. */
    fun systemNotification(kind: Int, number: String, contact: String, message: String, appName: String, packageName: String): BerryCommand {
        val n = ProtoWriter().uint(1, kind).string(2, number).string(3, contact).string(4, message.take(400)).string(5, appName).string(6, packageName)
        return BerryCommand.wear(WearType.NOTIFICATION, NotificationId.SYSTEM, WearField.NOTIFICATION, ProtoWriter().message(1, n), "systemNotification")
    }

    /** state: 0 answered, 1 hung up, 2 muted. */
    fun callState(state: Int) =
        BerryCommand.wear(WearType.NOTIFICATION, NotificationId.CALL_STATE, WearField.NOTIFICATION, ProtoWriter().uint(4, state), "callState")

    fun quickReplies() = BerryCommand.wear(WearType.NOTIFICATION, NotificationId.GET_REPLIES, label = "quickReplies")

    fun setQuickReplies(replies: List<String>): BerryCommand {
        val list = ProtoWriter()
        replies.forEach { list.message(1, ProtoWriter().string(1, it.take(60))) }
        return BerryCommand.wear(WearType.NOTIFICATION, NotificationId.SET_REPLIES, WearField.NOTIFICATION, ProtoWriter().message(5, list), "setQuickReplies")
    }

    // -------------------------------------------------------------- music

    fun musicInfo(info: NowPlaying): BerryCommand {
        val p = ProtoWriter().uint(1, info.state.wire).string(2, info.title.take(60))
        if (info.player.isNotEmpty()) p.string(3, info.player)
        if (info.artist.isNotEmpty()) p.string(4, info.artist.take(60))
        p.uint(5, info.volume).uint(6, info.maxVolume).uint(7, info.durationSeconds).uint(8, info.positionSeconds).uint(10, 1)
        return BerryCommand.wear(WearType.MUSIC, MusicId.INFO, WearField.MUSIC, ProtoWriter().message(1, p), "musicInfo")
    }

    // -------------------------------------------------------------- health data

    fun fitnessIdList(fileType: Int) = menu(MenuId.FITNESS_IDS, MenuField.FITNESS_IDS, ProtoWriter().uint(1, fileType), "fitnessIdList")

    fun requestFitnessFile(fileType: Int, ids: List<FitnessRecordId>): BerryCommand {
        val list = ProtoWriter()
        ids.forEach { list.bytes(1, it.raw) }
        val status = ProtoWriter().uint(1, fileType).uint(2, 1).uint(3, ids.sumOf { it.length }).message(4, list)
        return menu(MenuId.FILE_SEND_STATUS, MenuField.FILE_SEND_STATUS, status, "requestFitnessFile")
    }

    fun deleteFitnessIds(fileType: Int, ids: List<FitnessRecordId>): BerryCommand {
        val list = ProtoWriter()
        ids.forEach { list.bytes(1, it.raw) }
        return menu(MenuId.DELETE_FITNESS, MenuField.DELETE_FITNESS, ProtoWriter().uint(1, fileType).message(2, list), "deleteFitnessIds")
    }

    // -------------------------------------------------------------- settings

    fun get(menuId: Int) = BerryCommand.wear(WearType.SETTING_MENU, menuId, label = "get-$menuId")

    private fun menu(id: Int, field: Int, body: ProtoWriter, label: String) =
        BerryCommand.wear(WearType.SETTING_MENU, id, WearField.SETTING_MENU, ProtoWriter().message(field, body), label)

    private fun setMenu(getId: Int, field: Int, body: ProtoWriter, label: String) = menu(MenuId.set(getId), field, body, label)

    private fun time(minutes: Int) = ProtoWriter().uint(1, (minutes / 60) % 24).uint(2, minutes % 60)

    fun setHeartRate(s: HeartRateSettings): BerryCommand {
        val m = ProtoWriter().uint(1, if (s.enabled) 0 else 1).uint(2, s.intervalMinutes).bool(3, s.highAlertEnabled).uint(4, s.highAlertBpm)
        m.uint(7, if (s.smartMode) 1 else 0)
        if (s.lowAlertBpm > 0) m.uint(8, s.lowAlertBpm)
        return setMenu(MenuId.HEART_RATE, MenuField.HEART_RATE, m, "setHeartRate")
    }

    fun setSpO2(s: SpO2Settings) =
        setMenu(MenuId.SPO2, MenuField.SPO2, ProtoWriter().uint(1, if (s.enabled) 0 else 1).uint(2, s.intervalMinutes), "setSpO2")

    fun setStress(s: StressSettings) =
        setMenu(MenuId.STRESS, MenuField.STRESS, ProtoWriter().bool(1, s.enabled).bool(2, s.relaxReminder), "setStress")

    fun setDoNotDisturb(s: ScheduleSettings) = setMenu(
        MenuId.DND, MenuField.DND,
        ProtoWriter().bool(1, s.enabled).message(2, time(s.startMinutes)).message(3, time(s.endMinutes)).bool(4, s.smart), "setDND",
    )

    fun setRaiseToWake(s: RaiseToWakeSettings) = setMenu(
        MenuId.WRIST, MenuField.WRIST,
        ProtoWriter().uint(1, s.mode.ordinal).message(2, time(s.startMinutes)).message(3, time(s.endMinutes)).uint(4, s.sensitivity.ordinal),
        "setRaiseToWake",
    )

    fun setReminder(kind: ReminderKind, s: ReminderSettings): BerryCommand {
        val (id, field) = reminderIds(kind)
        val m = ProtoWriter().bool(1, s.enabled).message(2, time(s.startMinutes)).message(3, time(s.endMinutes)).uint(4, s.intervalMinutes)
        if (kind != ReminderKind.MEDICATION && kind != ReminderKind.MEALS) m.bool(5, s.skipLunch)
        return setMenu(id, field, m, "set-$kind")
    }

    fun reminderIds(kind: ReminderKind) = when (kind) {
        ReminderKind.SEDENTARY -> MenuId.SEDENTARY to MenuField.SEDENTARY
        ReminderKind.DRINK_WATER -> MenuId.DRINK to MenuField.DRINK
        ReminderKind.MEDICATION -> MenuId.MEDICATION to MenuField.MEDICATION
        ReminderKind.MEALS -> MenuId.MEALS to MenuField.MEALS
        ReminderKind.WASH_HANDS -> MenuId.WASH to MenuField.WASH
    }

    fun setAlarms(alarms: List<WatchAlarm>): BerryCommand {
        val list = ProtoWriter()
        alarms.forEachIndexed { i, a ->
            val data = ProtoWriter().message(1, time(a.minutes)).uint(2, a.weekdayMask).bool(3, a.enabled).string(4, a.label.take(20)).uint(5, 0)
            list.message(1, ProtoWriter().uint(1, i).message(2, data))
        }
        return setMenu(MenuId.ALARMS, MenuField.ALARMS, list, "setAlarms")
    }

    /** SEVibrationIntensityMode is a bare enum in the oneof. */
    fun setVibration(level: Int) = BerryCommand.wear(
        WearType.SETTING_MENU, MenuId.set(MenuId.VIBRATION), WearField.SETTING_MENU, ProtoWriter().uint(MenuField.VIBRATION, level.coerceIn(0, 2)), "setVibration",
    )

    fun setPowerSaving(on: Boolean) = setMenu(MenuId.POWER_SAVING, MenuField.POWER_SAVING, ProtoWriter().bool(1, on), "setPowerSaving")

    fun setCoverToMute(on: Boolean) = setMenu(MenuId.COVER, MenuField.COVER, ProtoWriter().bool(1, on), "setCoverToMute")

    fun setRemSleep(on: Boolean) = setMenu(MenuId.REM, MenuField.REM, ProtoWriter().bool(1, on), "setRem")

    fun setScreen(s: ScreenSettings) = setMenu(
        MenuId.SCREEN, MenuField.SCREEN,
        ProtoWriter().uint(1, s.brightness).bool(2, s.alwaysOn).uint(3, s.onSeconds).bool(4, s.doubleTapToWake), "setScreen",
    )

    fun setAlwaysOn(s: AlwaysOnSettings) = setMenu(
        MenuId.AOD, MenuField.AOD,
        ProtoWriter().uint(1, s.mode.ordinal).message(2, time(s.startMinutes)).message(3, time(s.endMinutes)).uint(4, s.style.ordinal), "setAlwaysOn",
    )

    fun setSleepMode(s: SleepModeSettings) = setMenu(
        MenuId.SLEEP_MODE, MenuField.SLEEP_MODE,
        ProtoWriter().bool(1, s.enabled).message(2, time(s.startMinutes)).message(3, time(s.endMinutes)).bool(6, s.dimScreen), "setSleepMode",
    )

    fun setWatchNotifications(s: WatchNotificationSettings) = setMenu(
        MenuId.NOTIFY, MenuField.NOTIFY,
        ProtoWriter().bool(1, s.keepScreenOff).bool(4, s.delayedReminder).bool(5, s.onlyWhenPhoneLocked).bool(6, s.onlyWhenWorn), "setWatchNotifications",
    )

    fun setWorkoutDetection(s: WorkoutDetection) =
        setMenu(MenuId.WORKOUT, MenuField.WORKOUT, ProtoWriter().bool(1, s.autoStart).bool(2, s.autoStop), "setWorkoutDetection")

    // -------------------------------------------------------------- user settings

    /** Only the fields the earlier, crash-free build sent; the extra SEUserProfile fields are untested on the NF 2. */
    fun setUserProfile(p: UserProfile): BerryCommand {
        val profile = ProtoWriter().uint(1, p.heightCm).float(2, p.weightKg).uint(4, p.age).uint(5, p.sex.ordinal)
            .uint(7, p.calorieGoal).uint(8, p.stepGoal).uint(9, p.distanceGoalMeters).uint(12, p.sleepGoalHours)
            .bool(14, true).bool(15, true)
        return BerryCommand.wear(WearType.USER_SETTINGS, UserSettingId.SET_PROFILE, WearField.USER_SETTINGS, ProtoWriter().message(5, profile), "setUserProfile")
    }

    fun setUnits(metric: Boolean) =
        BerryCommand.wear(WearType.USER_SETTINGS, UserSettingId.SET_UNIT, WearField.USER_SETTINGS, ProtoWriter().uint(3, if (metric) 0 else 1), "setUnits")

    fun setTemperatureUnit(celsius: Boolean) =
        BerryCommand.wear(WearType.USER_SETTINGS, UserSettingId.SET_TEMPERATURE, WearField.USER_SETTINGS, ProtoWriter().uint(4, if (celsius) 0 else 1), "setTemperature")

    fun getLanguages() = BerryCommand.wear(WearType.USER_SETTINGS, UserSettingId.GET_LANGUAGES, label = "getLanguages")

    fun setLanguage(id: Int) =
        BerryCommand.wear(WearType.USER_SETTINGS, UserSettingId.SET_LANGUAGE, WearField.USER_SETTINGS, ProtoWriter().uint(1, id), "setLanguage")

    // -------------------------------------------------------------- watch faces

    fun watchFaces() = BerryCommand.wear(WearType.WATCH_FACE, FaceId.LIST, label = "watchFaces")

    fun faceSupportData() = BerryCommand.wear(WearType.WATCH_FACE, FaceId.SUPPORT_DATA, label = "faceSupportData")

    fun setWatchFace(id: String) =
        BerryCommand.wear(WearType.WATCH_FACE, FaceId.SET, WearField.WATCH_FACE, ProtoWriter().string(2, id), "setWatchFace")

    fun removeWatchFace(id: String) =
        BerryCommand.wear(WearType.WATCH_FACE, FaceId.REMOVE, WearField.WATCH_FACE, ProtoWriter().string(2, id), "removeWatchFace")

    /** SAPrepareInfo { id, size, watch_bg_size, style } — asks the watch to accept a face file. */
    fun prepareFaceInstall(id: String, size: Int, backgroundSize: Int = 0, style: String = ""): BerryCommand {
        val info = ProtoWriter().string(1, id).uint(2, size).uint(3, backgroundSize).string(7, style)
        return BerryCommand.wear(WearType.WATCH_FACE, FaceId.PREPARE, WearField.WATCH_FACE, ProtoWriter().message(6, info), "prepareFace")
    }

    /** SAEditRequest for album/editable faces; background sizes refer to the PAR file sent next. */
    fun editFace(
        id: String,
        setCurrent: Boolean,
        backgroundMd5: String? = null,
        backgroundSize: Int = 0,
        backgroundColor: String? = null,
        style: String? = null,
        styleColorIndex: Int? = null,
        dataSlots: List<Int>? = null,
    ): BerryCommand {
        val r = ProtoWriter().string(1, id).bool(2, setCurrent)
        if (backgroundColor != null) r.string(3, backgroundColor)
        if (backgroundMd5 != null) r.string(4, backgroundMd5).uint(5, backgroundSize)
        if (style != null) r.string(6, style)
        dataSlots?.forEach { r.uint(7, it) }
        if (styleColorIndex != null) r.uint(14, styleColorIndex)
        return BerryCommand.wear(WearType.WATCH_FACE, FaceId.EDIT, WearField.WATCH_FACE, ProtoWriter().message(12, r), "editFace")
    }
}
