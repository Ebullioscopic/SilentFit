package fit.silent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchInfo(
    val name: String = "",
    val address: String = "",
    val firmware: String = "",
    val model: String = "",
    val serial: String = "",
    val deviceType: Int = 0,
    val platformType: Int = 0,
)

enum class ChargeState { UNKNOWN, CHARGING, NOT_CHARGING, FULL;
    companion object { fun of(v: Int) = entries.getOrElse(v) { UNKNOWN } }
}

@Serializable
data class BatteryStatus(val level: Int, val state: ChargeState)

/** An entry from the watch's installed face list (SAWatchFaceItem). */
data class InstalledFace(
    val id: String,
    val name: String,
    val isCurrent: Boolean,
    val canRemove: Boolean,
    val canEdit: Boolean = false,
    val versionCode: Long = 0,
    val backgroundColor: String? = null,
    val style: String? = null,
    val styleColorIndex: Int? = null,
    val dataSlots: List<Int> = emptyList(),
)

/** Real-time totals pushed by the watch (SEBasicData). */
@Serializable
data class LiveData(
    val steps: Int = 0,
    val calories: Int = 0,
    val distanceMeters: Int = 0,
    val heartRate: Int = 0,
    val spo2: Int = 0,
    val stress: Int = 0,
    val standingHours: Int = 0,
    val activeMinutes: Int = 0,
    val sleepMinutes: Int = 0,
    val at: Long = 0,
)

enum class PlaybackState(val wire: Int) { NONE(0), NO_PERMISSION(1), PLAYING(2), PAUSED(3), STOPPED(4), NOTHING_PLAYING(5) }

data class NowPlaying(
    val state: PlaybackState = PlaybackState.NOTHING_PLAYING,
    val title: String = "",
    val artist: String = "",
    val player: String = "",
    val volume: Int = 0,
    val maxVolume: Int = 16,
    val durationSeconds: Int = 0,
    val positionSeconds: Int = 0,
)

/** SEPlayerControlCommand values sent by the watch. */
enum class MediaCommand(val wire: Int) { PLAY(1), PAUSE(2), PREVIOUS(3), NEXT(4), VOLUME_UP(5), VOLUME_DOWN(6);
    companion object { fun of(v: Int) = entries.firstOrNull { it.wire == v } }
}

// ---------------------------------------------------------------- user & settings

enum class Sex { UNSPECIFIED, MALE, FEMALE }

enum class WearHand { NOT_SET, LEFT, RIGHT }

@Serializable
data class UserProfile(
    val name: String = "",
    val heightCm: Int = 170,
    val weightKg: Float = 65f,
    val age: Int = 30,
    val sex: Sex = Sex.UNSPECIFIED,
    val stepGoal: Int = 8000,
    val calorieGoal: Int = 400,
    val distanceGoalMeters: Int = 5000,
    val sleepGoalHours: Int = 8,
    val activeMinutesGoal: Int = 30,
    val standingGoal: Int = 12,
    val waterGoalMl: Int = 2000,
    val wearHand: WearHand = WearHand.LEFT,
    val metric: Boolean = true,
    val celsius: Boolean = true,
)

@Serializable
data class HeartRateSettings(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 5,
    val highAlertEnabled: Boolean = false,
    val highAlertBpm: Int = 120,
    val lowAlertBpm: Int = 0,
    val smartMode: Boolean = false,
)

@Serializable
data class SpO2Settings(val enabled: Boolean = true, val intervalMinutes: Int = 10)

@Serializable
data class StressSettings(val enabled: Boolean = true, val relaxReminder: Boolean = false)

/** A switch with a daily window, minutes since midnight. */
@Serializable
data class ScheduleSettings(val enabled: Boolean = false, val startMinutes: Int = 22 * 60, val endMinutes: Int = 7 * 60, val smart: Boolean = false)

enum class WakeMode { ALL_DAY, SCHEDULED, OFF, SMART }

enum class Sensitivity { LOW, MEDIUM, HIGH }

@Serializable
data class RaiseToWakeSettings(
    val mode: WakeMode = WakeMode.ALL_DAY,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 22 * 60,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
)

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val startMinutes: Int = 9 * 60,
    val endMinutes: Int = 21 * 60,
    val intervalMinutes: Int = 60,
    val skipLunch: Boolean = true,
)

enum class ReminderKind(val label: String) { SEDENTARY("Move"), DRINK_WATER("Hydrate"), MEDICATION("Medication"), MEALS("Meals"), WASH_HANDS("Wash hands") }

@Serializable
data class WatchAlarm(
    val index: Int = 0,
    val minutes: Int = 7 * 60,
    /** Bit 0 = Monday … bit 6 = Sunday; 0 = once. */
    val weekdayMask: Int = 0x1F,
    val enabled: Boolean = true,
    val label: String = "Alarm",
)

@Serializable
data class ScreenSettings(
    val brightness: Int = 3,
    val alwaysOn: Boolean = false,
    val onSeconds: Int = 5,
    val doubleTapToWake: Boolean = false,
)

enum class AodStyle { ANALOG, DIGITAL }

/** Information screen display: the always-on clock shown while the screen is dimmed. */
@Serializable
data class AlwaysOnSettings(
    val mode: WakeMode = WakeMode.OFF,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 22 * 60,
    val style: AodStyle = AodStyle.DIGITAL,
)

@Serializable
data class SleepModeSettings(
    val enabled: Boolean = false,
    val startMinutes: Int = 23 * 60,
    val endMinutes: Int = 7 * 60,
    val dimScreen: Boolean = true,
)

@Serializable
data class WatchNotificationSettings(
    val keepScreenOff: Boolean = false,
    val onlyWhenPhoneLocked: Boolean = false,
    val onlyWhenWorn: Boolean = false,
    val delayedReminder: Boolean = false,
)

@Serializable
data class WorkoutDetection(val autoStart: Boolean = false, val autoStop: Boolean = false)

/** One entry of a watch launcher list (SEWidget). */
@Serializable
data class LauncherItem(val function: Int, val enabled: Boolean, val order: Int = 0, val hidden: Boolean = false, val sortable: Boolean = true)

enum class LauncherList { WIDGETS, APPS, QUICK }

/** Everything the watch reported about its configurable features; null = not supported/unknown. */
data class WatchSettings(
    val heartRate: HeartRateSettings? = null,
    val spo2: SpO2Settings? = null,
    val stress: StressSettings? = null,
    val doNotDisturb: ScheduleSettings? = null,
    val raiseToWake: RaiseToWakeSettings? = null,
    val reminders: Map<ReminderKind, ReminderSettings> = emptyMap(),
    val alarms: List<WatchAlarm>? = null,
    val maxAlarms: Int = 5,
    val vibration: Int? = null,
    val powerSaving: Boolean? = null,
    val coverToMute: Boolean? = null,
    val screen: ScreenSettings? = null,
    val alwaysOn: AlwaysOnSettings? = null,
    val sleepMode: SleepModeSettings? = null,
    val notifications: WatchNotificationSettings? = null,
    val workoutDetection: WorkoutDetection? = null,
    val remSleep: Boolean? = null,
    val launcher: Map<LauncherList, List<LauncherItem>> = emptyMap(),
    val languages: List<Int> = emptyList(),
    val language: Int? = null,
)

// ---------------------------------------------------------------- health

/** Identifier of one fitness record stored on the watch (SEBigFile_Fitness_TypeId). */
class FitnessRecordId(
    val raw: ByteArray,
    val year: Int,
    val month: Int,
    val day: Int,
    val functionType: Int,
    val length: Int,
) {
    val dayKey get() = HealthDay.key(year, month, day)
}

enum class SleepStage { AWAKE, LIGHT, DEEP, REM;
    companion object { fun of(v: Int) = entries.getOrElse(v) { AWAKE } }
}

@Serializable
data class SleepSegment(val start: Long, val minutes: Int, val stage: SleepStage)

@Serializable
data class SleepSummary(
    val start: Long = 0,
    val end: Long = 0,
    val totalMinutes: Int = 0,
    val score: Int = 0,
    val awakeMinutes: Int = 0,
    val lightMinutes: Int = 0,
    val deepMinutes: Int = 0,
    val remMinutes: Int = 0,
    val segments: List<SleepSegment> = emptyList(),
)

/** One value per interval from local midnight; 0 = no reading. */
@Serializable
data class SampleSeries(val interval: Int = 0, val values: List<Int> = emptyList()) {
    val hasData get() = values.any { it > 0 }
    val min get() = values.filter { it > 0 }.minOrNull() ?: 0
    val max get() = values.maxOrNull() ?: 0
    val average get() = values.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
    val latest get() = values.lastOrNull { it > 0 } ?: 0
}

@Serializable
data class HealthDay(
    val id: String,
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val calories: Int = 0,
    val hourlySteps: List<Int> = emptyList(),
    val hourlyDistance: List<Int> = emptyList(),
    val hourlyCalories: List<Int> = emptyList(),
    val standingHours: List<Int> = emptyList(),
    val activeMinutesByHour: List<Int> = emptyList(),
    val heartRate: SampleSeries = SampleSeries(),
    val spo2: SampleSeries = SampleSeries(),
    val stress: SampleSeries = SampleSeries(),
    val sleep: SleepSummary? = null,
) {
    val standingHourCount get() = standingHours.count { it > 0 }
    val activeMinutes get() = activeMinutesByHour.sum()

    companion object {
        fun key(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(year, month, day)
    }
}
