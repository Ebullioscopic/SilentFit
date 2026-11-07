package fit.silent.app.watch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import fit.silent.app.data.PairedWatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Bluetooth bonding with a watch whose address is known (from its QR code), without leaving the app. */
object WatchBonding {
    sealed interface Step {
        data object Searching : Step
        data class Bonding(val name: String) : Step
        data class Bonded(val watch: PairedWatch) : Step
        data class Failed(val reason: String) : Step
    }

    private const val TIMEOUT_MS = 90_000L

    /** Searches for [address], asks Android to bond with it and completes once bonded. Already-bonded watches finish at once. */
    @SuppressLint("MissingPermission")
    fun bond(context: Context, address: String): Flow<Step> = callbackFlow {
        val adapter = SppLink.adapter(context)
        if (adapter == null || !adapter.isEnabled) {
            send(Step.Failed("Turn on Bluetooth")); close(); awaitClose {}; return@callbackFlow
        }
        val device = try { adapter.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null }
        if (device == null) {
            send(Step.Failed("That code has no valid address")); close(); awaitClose {}; return@callbackFlow
        }
        fun nameOf(d: BluetoothDevice) = d.name?.takeIf { it.isNotBlank() } ?: "NF 2_${address.replace(":", "").takeLast(4)}"
        // Noise names its watches "<model>_<last 4 hex of the address>"; this also catches a QR code that carries
        // a sibling (BLE) address of the same watch.
        val suffix = "_" + address.replace(":", "").takeLast(4)
        fun matches(d: BluetoothDevice?) = d != null && (d.address.equals(address, true) || d.name?.endsWith(suffix, true) == true)
        val bonded = try { adapter.bondedDevices.orEmpty() } catch (_: SecurityException) { emptySet() }
        val known = bonded.firstOrNull { it.address.equals(address, true) } ?: bonded.firstOrNull(::matches)
        if (known != null) {
            send(Step.Bonded(PairedWatch(nameOf(known), known.address))); close(); awaitClose {}; return@callbackFlow
        }

        var bondStarted = false
        var bondTarget = device
        fun startBond(d: BluetoothDevice) {
            if (bondStarted) return
            bondStarted = true
            bondTarget = d
            runCatching { adapter.cancelDiscovery() }
            trySend(Step.Bonding(nameOf(d)))
            if (d.bondState != BluetoothDevice.BOND_BONDING && !d.createBond()) { trySend(Step.Failed("Android refused to pair. Try again")); close() }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val found = intent.device()
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> if (matches(found)) startBond(found!!)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (!bondStarted) startBond(device)
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (found?.address.equals(bondTarget.address, true)) {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                        when {
                            state == BluetoothDevice.BOND_BONDED -> { trySend(Step.Bonded(PairedWatch(nameOf(found!!), found.address))); close() }
                            state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING ->
                                { trySend(Step.Failed("Pairing didn't finish. Keep the watch on its QR screen and try again")); close() }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        try {
            send(Step.Searching)
            // Discovery puts the watch in Android's device cache; bonding an unseen classic device often fails.
            if (!adapter.startDiscovery()) startBond(device)
        } catch (_: SecurityException) {
            send(Step.Failed("Allow Nearby devices to pair")); close()
        }
        val timeout = launch {
            delay(TIMEOUT_MS)
            send(Step.Failed("The watch didn't answer. Keep it close and on its QR screen")); close()
        }
        awaitClose {
            timeout.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { adapter.cancelDiscovery() }
        }
    }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
