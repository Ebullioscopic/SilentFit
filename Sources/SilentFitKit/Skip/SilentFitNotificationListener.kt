package silent.fit.kit

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Forwards phone notifications (apps, calls, SMS) and the active media session to the watch.
 * Enabled by the user in Settings › Notifications › Device & app notifications.
 */
class SilentFitNotificationListener : NotificationListenerService() {
    private val recent = HashMap<String, Int>()
    private var sessionManager: MediaSessionManager? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        bindController(controllers?.firstOrNull())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, SilentFitNotificationListener::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            sessionManager = manager
            bindController(manager.getActiveSessions(component).firstOrNull())
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions unavailable", e)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        bindController(null)
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val category = n.category ?: ""
        val isCall = category == Notification.CATEGORY_CALL
        if (sbn.isOngoing && !isCall) return
        if (category == Notification.CATEGORY_TRANSPORT || category == Notification.CATEGORY_PROGRESS) return
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim() ?: ""
        if (title.isEmpty() && text.isEmpty()) return
        val fingerprint = (title + "\u0000" + text).hashCode()
        synchronized(recent) {
            if (recent[sbn.key] == fingerprint) return
            recent[sbn.key] = fingerprint
            if (recent.size > 200) recent.clear()
        }
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        PhoneBridge.notificationPosted?.invoke(sbn.packageName, appName, title, text, sbn.key, category)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(recent) { recent.remove(sbn.key) }
        val category = sbn.notification?.category ?: ""
        PhoneBridge.notificationRemoved?.invoke(sbn.packageName, sbn.key, category)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia(this@SilentFitNotificationListener)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia(this@SilentFitNotificationListener)
        override fun onSessionDestroyed() = bindController(null)
    }

    private fun bindController(controller: MediaController?) {
        PhoneBridge.mediaController?.unregisterCallback(controllerCallback)
        PhoneBridge.mediaController = controller
        controller?.registerCallback(controllerCallback)
        publishMedia(this)
    }

    companion object {
        private const val TAG = "SilentFitListener"
        @Volatile var instance: SilentFitNotificationListener? = null

        /** Pushes the current media state; PlaybackState values map to SEPlayerInfo.SEState. */
        fun publishMedia(context: Context) {
            val controller = PhoneBridge.mediaController
            val metadata = controller?.metadata
            val playback = controller?.playbackState
            val state = when (playback?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> 2
                PlaybackState.STATE_PAUSED -> 3
                PlaybackState.STATE_STOPPED -> 4
                else -> if (controller == null) 5 else 3
            }
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val duration = ((metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) / 1000L).toInt()
            val position = ((playback?.position ?: 0L) / 1000L).toInt()
            val player = controller?.packageName ?: ""
            PhoneBridge.mediaChanged?.invoke(state, title, artist, player, PhoneBridge.volume(context), PhoneBridge.maxVolume(context), duration, position)
        }
    }
}
