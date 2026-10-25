package fit.silent.app.data

import fit.silent.app.ui.theme.ThemeMode
import fit.silent.core.model.UserProfile
import kotlinx.serialization.Serializable

@Serializable
data class PairedWatch(val name: String, val address: String)

@Serializable
data class AppSettings(
    val pairedWatch: PairedWatch? = null,
    /** Account id presented to the watch during binding; reuse the NoiseFit id to avoid a factory reset. */
    val accountId: String = "",
    val profile: UserProfile = UserProfile(),
    val onboarded: Boolean = false,
    val demoMode: Boolean = false,
    val autoReconnect: Boolean = true,
    val keepConnected: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val deleteAfterSync: Boolean = false,
    val forwardNotifications: Boolean = true,
    val forwardCalls: Boolean = true,
    val forwardMessages: Boolean = true,
    val mutedPackages: Set<String> = emptySet(),
    val quietWhileScreenOn: Boolean = false,
    val quickReplies: List<String> = listOf("On my way", "Can't talk now", "Call you later", "OK", "Thanks!"),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Sends commands whose field layout was not observed from the official app (core CommandPolicy). Off by default. */
    val experimentalWatchControls: Boolean = false,
    /** A face that was just made current; reverted automatically if the watch drops the link soon after. */
    val faceTrial: FaceTrial? = null,
    val lastSync: Long = 0,
)

@Serializable
data class FaceTrial(val faceId: String, val previousFaceId: String, val startedAt: Long)
