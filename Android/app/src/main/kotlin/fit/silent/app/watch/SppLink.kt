package fit.silent.app.watch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.UUID

enum class LinkState { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

interface LinkListener {
    /** Called on the link's reader thread, in arrival order. */
    fun onBytes(bytes: ByteArray, count: Int)
    fun onState(state: LinkState, message: String)
}

interface WatchLink {
    fun open(address: String, listener: LinkListener)
    fun write(bytes: ByteArray): Boolean
    fun close()
}

/**
 * Classic Bluetooth SPP (RFCOMM) link used by Berry/Bluetrum Noise watches.
 * The reader thread blocks in `read()`, so an idle connection costs no CPU.
 */
class SppLink(private val context: Context) : WatchLink {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var closing = false
    private val writeLock = Any()

    @SuppressLint("MissingPermission")
    override fun open(address: String, listener: LinkListener) {
        close()
        closing = false
        listener.onState(LinkState.CONNECTING, "Connecting")
        Thread({
            var opened: BluetoothSocket? = null
            try {
                val adapter = adapter(context) ?: throw IOException("Bluetooth is not available")
                if (!adapter.isEnabled) throw IOException("Bluetooth is off")
                adapter.cancelDiscovery()
                opened = connect(adapter.getRemoteDevice(address))
                socket = opened
                listener.onState(LinkState.CONNECTED, "Connected")
                val input = opened.inputStream
                val buffer = ByteArray(8192)
                while (!closing) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) listener.onBytes(buffer, n)
                }
                if (!closing) listener.onState(LinkState.DISCONNECTED, "Watch disconnected")
            } catch (e: Exception) {
                if (!closing) {
                    Log.i(TAG, "SPP link ended: ${e.message}")
                    listener.onState(LinkState.FAILED, e.message ?: "Connection failed")
                }
            } finally {
                runCatching { opened?.close() }
                if (socket === opened) socket = null
            }
        }, "SilentFit-SPP").apply { isDaemon = true }.start()
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val primary = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            primary.connect()
            return primary
        } catch (first: IOException) {
            runCatching { primary.close() }
            // Some firmwares do not publish an SDP record; channel 1 is the conventional SPP channel.
            val fallback = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
            try {
                fallback.connect()
                return fallback
            } catch (_: IOException) {
                runCatching { fallback.close() }
                throw first
            }
        }
    }

    override fun write(bytes: ByteArray): Boolean {
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

    override fun close() {
        closing = true
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    companion object {
        private const val TAG = "SilentFitSPP"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        fun adapter(context: Context): BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

        data class Bonded(val name: String, val address: String) {
            val looksLikeWatch get() = Regex("(?i)noise|nf ?\\d|colorfit|watch|fit").containsMatchIn(name)
        }

        @SuppressLint("MissingPermission")
        fun bonded(context: Context): List<Bonded> = try {
            adapter(context)?.bondedDevices.orEmpty().map { Bonded(it.name ?: it.address, it.address) }
                .sortedWith(compareByDescending<Bonded> { it.looksLikeWatch }.thenBy { it.name })
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
