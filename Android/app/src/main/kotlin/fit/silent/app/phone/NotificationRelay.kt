package fit.silent.app.phone

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import fit.silent.app.SilentFitApp
import fit.silent.core.model.NowPlaying
import fit.silent.core.model.PlaybackState as WatchPlayback

/**
 * Forwards phone notifications and the active media session to the watch.
 * Granted by the user in Settings › Notifications › Device & app notifications.
 */
class NotificationRelay : NotificationListenerService() {
    private val recent = LinkedHashMap<String, Int>()
    private var sessions: MediaSessionManager? = null
    private val phone get() = (application as SilentFitApp).graph.phone

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { bind(it?.firstOrNull()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia(phone)
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia(phone)
        override fun onSessionDestroyed() = bind(null)
    }

    override fun onListenerConnected() {
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val me = ComponentName(this, NotificationRelay::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsListener, me)
            sessions = manager
            bind(manager.getActiveSessions(me).firstOrNull())
        } catch (e: SecurityException) {
            Log.w(TAG, "Media sessions unavailable", e)
        }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsListener)
        bind(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val category = n.category.orEmpty()
        val isCall = category == Notification.CATEGORY_CALL
        if (sbn.isOngoing && !isCall) return
        if (category == Notification.CATEGORY_TRANSPORT || category == Notification.CATEGORY_PROGRESS || category == Notification.CATEGORY_SERVICE) return
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val fingerprint = (title + '\u0000' + text).hashCode()
        synchronized(recent) {
            if (recent[sbn.key] == fingerprint) return
            recent[sbn.key] = fingerprint
            if (recent.size > 200) recent.remove(recent.keys.first())
        }
        phone.posted(sbn.packageName, phone.appLabel(sbn.packageName), title, text, sbn.key, category)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(recent) { recent.remove(sbn.key) }
        phone.removed(sbn.notification?.category.orEmpty())
    }

    private fun bind(controller: MediaController?) {
        phone.mediaController?.unregisterCallback(controllerCallback)
        phone.mediaController = controller
        controller?.registerCallback(controllerCallback)
        publishMedia(phone)
    }

    companion object {
        private const val TAG = "SilentFitRelay"

        fun publishMedia(phone: PhoneServices) {
            val controller = phone.mediaController
            val metadata = controller?.metadata
            val playback = controller?.playbackState
            val state = when (playback?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> WatchPlayback.PLAYING
                PlaybackState.STATE_PAUSED -> WatchPlayback.PAUSED
                PlaybackState.STATE_STOPPED -> WatchPlayback.STOPPED
                else -> if (controller == null) WatchPlayback.NOTHING_PLAYING else WatchPlayback.PAUSED
            }
            val (volume, max) = phone.volume()
            phone.publish(
                NowPlaying(
                    state = state,
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    player = controller?.packageName.orEmpty(),
                    volume = volume,
                    maxVolume = max,
                    durationSeconds = ((metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) / 1000).toInt(),
                    positionSeconds = ((playback?.position ?: 0L) / 1000).toInt(),
                ),
            )
        }
    }
}
