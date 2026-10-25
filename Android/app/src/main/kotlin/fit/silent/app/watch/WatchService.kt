package fit.silent.app.watch

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fit.silent.app.MainActivity
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.phone.PhoneServices
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the watch link alive while the app is in the background so notifications and calls reach the
 * watch. Battery cost is the idle RFCOMM link: no polling, no wake locks, reconnects driven by
 * adapter/ACL broadcasts with exponential backoff as a fallback.
 */
class WatchService : LifecycleService() {
    private val graph get() = (application as SilentFitApp).graph

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> graph.session.onBluetoothChanged(true)
                    BluetoothAdapter.STATE_OFF -> graph.session.onBluetoothChanged(false)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let {
                    graph.session.onWatchNearby(it.address)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        PhoneServices.ensureChannels(this)
        startInForeground(notification("Connecting", null))
        ContextCompat.registerReceiver(
            this, bluetoothReceiver,
            IntentFilter().apply { addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothDevice.ACTION_ACL_CONNECTED) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            combine(graph.session.connection, graph.session.battery) { c, b ->
                val battery = b?.let { " · ${it.level}%" }.orEmpty()
                (if (c.isConnected) "Connected$battery" else c.message) to graph.settings.value.pairedWatch?.name
            }.distinctUntilChanged().collect { (text, name) ->
                getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, name))
            }
        }
        lifecycleScope.launch {
            graph.settings.flow.collect { s ->
                if (s.pairedWatch == null || !s.keepConnected) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_DISCONNECT) {
            graph.session.disconnect()
            graph.settings.update { it.copy(keepConnected = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        graph.session.connect()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun notification(text: String, watchName: String?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WatchService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, PhoneServices.CHANNEL_LINK)
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(watchName ?: getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(open)
            .addAction(0, "Disconnect", stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val ACTION_DISCONNECT = "fit.silent.app.DISCONNECT"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java)) }
        }
    }
}

/** Restores the background link after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as SilentFitApp).graph
        val s = graph.settings.value
        if (s.pairedWatch != null && s.keepConnected && s.autoReconnect) WatchService.start(context)
    }
}
