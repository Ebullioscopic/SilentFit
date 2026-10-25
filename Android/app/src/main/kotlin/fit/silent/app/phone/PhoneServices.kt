package fit.silent.app.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.session.MediaController
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fit.silent.app.MainActivity
import fit.silent.app.R
import fit.silent.core.model.MediaCommand
import fit.silent.core.model.NowPlaying

/** A phone notification classified for the watch. */
data class PhoneNotification(
    val kind: Kind,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val key: String,
) {
    enum class Kind { APP, INCOMING_CALL, MISSED_CALL, SMS }
}

/** Phone-side actions the watch can trigger and phone events the watch should hear about. */
class PhoneServices(private val context: Context) {
    @Volatile var onNotification: ((PhoneNotification) -> Unit)? = null
    @Volatile var onCallEnded: (() -> Unit)? = null
    @Volatile var onNowPlaying: ((NowPlaying) -> Unit)? = null
    @Volatile internal var mediaController: MediaController? = null
    private var ringtone: Ringtone? = null

    val phoneName: String get() = Build.MODEL ?: "Android"
    val systemVersion: String get() = Build.VERSION.RELEASE ?: "0"

    // ---------------------------------------------------------------- permissions

    fun runtimePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_SCAN) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.SEND_SMS)
    }

    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missingPermissions() = runtimePermissions().filterNot { has(it) }

    val hasBluetoothPermission get() = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT)

    val notificationAccessGranted: Boolean get() {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, NotificationRelay::class.java).flattenToString()
        return enabled.split(":").any { it == me }
    }

    fun openNotificationAccess() = start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openAppSettings() = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.fromParts("package", context.packageName, null)))
    fun openBluetoothSettings() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    @SuppressLint("BatteryLife")
    fun openBatterySettings() = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun start(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    // ---------------------------------------------------------------- notifications in

    internal fun posted(pkg: String, app: String, title: String, text: String, key: String, category: String) {
        onNotification?.invoke(PhoneNotification(classify(pkg, title, text, category), pkg, app, title, text, key))
    }

    internal fun removed(category: String) {
        if (category == Notification.CATEGORY_CALL) onCallEnded?.invoke()
    }

    private fun classify(pkg: String, title: String, text: String, category: String): PhoneNotification.Kind {
        val dialer = "dialer" in pkg || "incallui" in pkg || "telecom" in pkg || pkg == "com.android.phone"
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(context).orEmpty()
        return when {
            category == Notification.CATEGORY_CALL -> PhoneNotification.Kind.INCOMING_CALL
            category == Notification.CATEGORY_MISSED_CALL || (dialer && ("missed" in text.lowercase() || "missed" in title.lowercase())) -> PhoneNotification.Kind.MISSED_CALL
            pkg == smsPackage -> PhoneNotification.Kind.SMS
            else -> PhoneNotification.Kind.APP
        }
    }

    fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ---------------------------------------------------------------- media

    fun publish(info: NowPlaying) = onNowPlaying?.invoke(info)

    fun volume(): Pair<Int, Int> {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun media(command: MediaCommand) {
        val controls = mediaController?.transportControls
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun key(code: Int) {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        when (command) {
            MediaCommand.PLAY -> controls?.play() ?: key(KeyEvent.KEYCODE_MEDIA_PLAY)
            MediaCommand.PAUSE -> controls?.pause() ?: key(KeyEvent.KEYCODE_MEDIA_PAUSE)
            MediaCommand.PREVIOUS -> controls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            MediaCommand.NEXT -> controls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)
            MediaCommand.VOLUME_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            MediaCommand.VOLUME_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        }
        if (command == MediaCommand.VOLUME_UP || command == MediaCommand.VOLUME_DOWN) NotificationRelay.publishMedia(this)
    }

    // ---------------------------------------------------------------- calls & SMS

    /** state from the watch: 0 answer, 1 hang up, 2 mute. */
    @SuppressLint("MissingPermission")
    fun callAction(state: Int) {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        runCatching {
            @Suppress("DEPRECATION")
            when (state) {
                0 -> telecom.acceptRingingCall()
                1 -> telecom.endCall()
                else -> telecom.silenceRinger()
            }
        }.onFailure { Log.w(TAG, "Call action failed", it) }
    }

    fun sendSms(number: String, text: String): Boolean {
        if (number.isBlank() || text.isBlank() || !has(Manifest.permission.SEND_SMS)) return false
        return runCatching {
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(number, null, text.take(160), null, null)
            true
        }.getOrElse { Log.w(TAG, "SMS failed", it); false }
    }

    // ---------------------------------------------------------------- find phone

    fun startFindPhone() {
        stopFindPhone()
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                isLooping = true
                play()
            }
        }.onFailure { Log.w(TAG, "Find-phone tone failed", it) }
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        showFindPhoneAlert()
    }

    fun stopFindPhone() {
        ringtone?.stop()
        ringtone = null
        vibrator()?.cancel()
        context.getSystemService(NotificationManager::class.java).cancel(FIND_PHONE_ID)
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun showFindPhoneAlert() {
        if (!has(Manifest.permission.POST_NOTIFICATIONS) && Build.VERSION.SDK_INT >= 33) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).setAction(ACTION_FOUND_PHONE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_smartphone)
            .setContentTitle("Your watch is looking for this phone")
            .setContentText("Tap to stop ringing")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setFullScreenIntent(open, false)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(FIND_PHONE_ID, n)
    }

    companion object {
        private const val TAG = "SilentFitPhone"
        const val CHANNEL_LINK = "link"
        const val CHANNEL_ALERTS = "alerts"
        const val FIND_PHONE_ID = 42
        const val ACTION_FOUND_PHONE = "fit.silent.app.FOUND_PHONE"

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_LINK, context.getString(R.string.channel_link), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.channel_link_description)
                setShowBadge(false)
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_description)
            })
        }
    }
}
