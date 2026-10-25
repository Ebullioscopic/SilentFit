package silent.fit.kit

import android.Manifest
import android.app.Activity
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
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Process-wide bridge between Android system services and the shared Swift `WatchSession`.
 * Handlers are installed from transpiled Swift; system callbacks may arrive on any thread.
 */
object PhoneBridge {
    private const val TAG = "SilentFitBridge"

    /** (packageName, appName, title, text, key, category) */
    @Volatile var notificationPosted: ((String, String, String, String, String, String) -> Unit)? = null

    /** (packageName, key, category) */
    @Volatile var notificationRemoved: ((String, String, String) -> Unit)? = null

    /** (state, title, artist, player, volume, maxVolume, durationSec, positionSec) */
    @Volatile var mediaChanged: ((Int, String, String, String, Int, Int, Int, Int) -> Unit)? = null

    @Volatile internal var mediaController: MediaController? = null
    private var ringtone: Ringtone? = null

    fun phoneName(): String = Build.MODEL ?: "Android"

    fun systemVersion(): String = Build.VERSION.RELEASE ?: "0"

    fun runtimePermissions(): List<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
        perms.add(Manifest.permission.READ_PHONE_STATE)
        perms.add(Manifest.permission.SEND_SMS)
        return perms
    }

    fun missingPermissions(context: Context): List<String> =
        runtimePermissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun requestPermissions(activity: Activity) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 4201)
        }
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val component = ComponentName(context, SilentFitNotificationListener::class.java).flattenToString()
        return enabled.split(":").any { it == component }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openBluetoothSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // MARK: Media

    /** Watch SEPlayerControlCommand: 1 play, 2 pause, 3 previous, 4 next, 5 volume up, 6 volume down. */
    fun mediaCommand(context: Context, command: Int) {
        val controls = mediaController?.transportControls
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (command) {
            1 -> controls?.play() ?: dispatchMediaKey(audio, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            2 -> controls?.pause() ?: dispatchMediaKey(audio, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            3 -> controls?.skipToPrevious() ?: dispatchMediaKey(audio, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            4 -> controls?.skipToNext() ?: dispatchMediaKey(audio, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            5 -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            6 -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        }
        if (command == 5 || command == 6) {
            SilentFitNotificationListener.publishMedia(context)
        }
    }

    private fun dispatchMediaKey(audio: AudioManager, code: Int) {
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
    }

    fun volume(context: Context): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC)

    fun maxVolume(context: Context): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // MARK: Find phone

    fun startFindPhone(context: Context) {
        stopFindPhone()
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val tone = RingtoneManager.getRingtone(context, uri)
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
            ringtone = tone
        } catch (e: Exception) {
            Log.w(TAG, "Unable to play find-phone tone", e)
        }
        vibrator(context)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    fun stopFindPhone(context: Context? = null) {
        ringtone?.stop()
        ringtone = null
        if (context != null) vibrator(context)?.cancel()
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // MARK: Calls & SMS

    @Suppress("MissingPermission", "DEPRECATION")
    fun answerCall(context: Context): Boolean {
        if (!hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecom.acceptRingingCall()
        return true
    }

    @Suppress("MissingPermission", "DEPRECATION")
    fun endCall(context: Context): Boolean {
        if (!hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) || Build.VERSION.SDK_INT < 28) return false
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecom.endCall()
    }

    @Suppress("MissingPermission")
    fun silenceRinger(context: Context) {
        if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE) && !hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)) return
        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).silenceRinger()
    }

    fun sendSms(context: Context, number: String, text: String): Boolean {
        if (number.isBlank() || !hasPermission(context, Manifest.permission.SEND_SMS)) return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(number, null, text, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "SMS failed", e)
            false
        }
    }

    // MARK: Foreground service

    fun startLinkService(context: Context, title: String) {
        val intent = Intent(context, SilentFitLinkService::class.java).putExtra("title", title)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service not started", e)
        }
    }

    fun stopLinkService(context: Context) {
        context.stopService(Intent(context, SilentFitLinkService::class.java))
    }
}
