package silent.fit.kit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.UUID

/**
 * Classic Bluetooth SPP (RFCOMM) link used by Berry-protocol Noise watches.
 * States: 0 disconnected, 1 connecting, 2 connected, 3 failed.
 */
class SppConnection(
    private val onBytes: (ByteArray) -> Unit,
    private val onState: (Int, String) -> Unit
) {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var closing = false
    private var readerThread: Thread? = null
    private val writeLock = Any()

    @SuppressLint("MissingPermission")
    fun connect(context: Context, address: String) {
        disconnect()
        closing = false
        onState(1, "Connecting")
        val thread = Thread {
            var opened: BluetoothSocket? = null
            try {
                val adapter = adapter(context) ?: throw IOException("Bluetooth is not available")
                if (!adapter.isEnabled) throw IOException("Bluetooth is turned off")
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                opened = openSocket(device)
                socket = opened
                onState(2, "Connected")
                val input = opened.inputStream
                val buffer = ByteArray(4096)
                while (!closing) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) onBytes(buffer.copyOf(count))
                }
                if (!closing) onState(0, "Watch disconnected")
            } catch (e: Exception) {
                Log.w(TAG, "SPP failure", e)
                if (!closing) onState(3, e.message ?: "Connection failed")
            } finally {
                try { opened?.close() } catch (_: IOException) { }
                if (socket === opened) socket = null
            }
        }
        thread.name = "SilentFit-SPP"
        readerThread = thread
        thread.start()
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        val primary = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            primary.connect()
            return primary
        } catch (first: IOException) {
            try { primary.close() } catch (_: IOException) { }
            // Some firmwares do not publish an SDP record; channel 1 is the conventional SPP channel.
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val fallback = method.invoke(device, 1) as BluetoothSocket
            try {
                fallback.connect()
                return fallback
            } catch (second: IOException) {
                try { fallback.close() } catch (_: IOException) { }
                throw first
            }
        }
    }

    fun write(bytes: ByteArray): Boolean {
        val s = socket ?: return false
        return try {
            synchronized(writeLock) {
                s.outputStream.write(bytes)
                s.outputStream.flush()
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "SPP write failed", e)
            false
        }
    }

    fun disconnect() {
        closing = true
        val s = socket
        socket = null
        try { s?.close() } catch (_: IOException) { }
        readerThread = null
    }

    companion object {
        private const val TAG = "SilentFitSPP"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        fun adapter(context: Context): BluetoothAdapter? {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter
        }

        /** Bonded devices as "name|address" strings. */
        @SuppressLint("MissingPermission")
        fun bondedDevices(context: Context): List<String> {
            return try {
                val adapter = adapter(context) ?: return emptyList()
                adapter.bondedDevices.map { "${it.name ?: it.address}|${it.address}" }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission", e)
                emptyList()
            }
        }
    }
}
