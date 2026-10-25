package fit.silent.app.watch

import android.os.Handler
import android.os.HandlerThread
import fit.silent.core.fitness.DemoData
import fit.silent.core.protocol.BerryFrame
import fit.silent.core.protocol.BerryStreamParser
import fit.silent.core.protocol.ByteSink
import fit.silent.core.protocol.DeviceId
import fit.silent.core.protocol.FaceId
import fit.silent.core.protocol.InboundKind
import fit.silent.core.protocol.MenuField
import fit.silent.core.protocol.MenuId
import fit.silent.core.protocol.MicroId
import fit.silent.core.protocol.PrivateId
import fit.silent.core.protocol.ProtoWriter
import fit.silent.core.protocol.Responses
import fit.silent.core.protocol.WearField
import fit.silent.core.protocol.WearMessage
import fit.silent.core.protocol.WearType

/** A simulated NF 2 for demo mode and UI development: answers like the real watch. */
class DemoLink : WatchLink {
    private val thread = HandlerThread("SilentFit-Demo").apply { start() }
    private val handler = Handler(thread.looper)
    private val parser = BerryStreamParser()
    @Volatile private var listener: LinkListener? = null
    private var serial = 0x40
    private val faces = mutableListOf("1001" to "Classic", "1002" to "Pulse", "9000001" to "Dot Matrix")
    private var current = "1001"
    private var uploadRemaining = 0
    private var uploadId = ""

    override fun open(address: String, listener: LinkListener) {
        this.listener = listener
        handler.post { listener.onState(LinkState.CONNECTING, "Connecting") }
        handler.postDelayed({ listener.onState(LinkState.CONNECTED, "Connected") }, 400)
    }

    override fun write(bytes: ByteArray): Boolean {
        val copy = bytes.copyOf()
        handler.post { receive(copy) }
        return listener != null
    }

    override fun close() {
        val l = listener ?: return
        listener = null
        handler.post { l.onState(LinkState.DISCONNECTED, "Disconnected") }
    }

    private fun emit(bytes: ByteArray, delay: Long = 30) {
        handler.postDelayed({ listener?.onBytes(bytes, bytes.size) }, delay)
    }

    private fun receive(bytes: ByteArray) {
        if (uploadRemaining > 0 && !bytes.copyOfRange(0, minOf(4, bytes.size)).contentEquals(BerryFrame.HEAD_APP)) {
            consumeUpload(bytes.size)
            return
        }
        for (unit in parser.feed(bytes)) {
            if (unit.kind != InboundKind.PACKET) continue
            emit(BerryFrame.ack(unit.serial), 5)
            when (unit.event) {
                BerryFrame.EVENT_PRIVATE -> private(Responses.privateReply(unit.payload).cmdId)
                BerryFrame.EVENT_LARGE_FILE -> { uploadRemaining = (fit.silent.core.protocol.Bytes.u32le(unit.payload, 0) + 12).toInt(); consumeUpload(unit.payload.size) }
                else -> WearMessage.parse(unit.payload)?.let { wear(it) }
            }
        }
    }

    private fun consumeUpload(n: Int) {
        val before = uploadRemaining
        uploadRemaining -= n
        if ((before / 4096) != (uploadRemaining.coerceAtLeast(0) / 4096)) emit(BerryFrame.ack(serial++ and 0xFF), 2)
        if (uploadRemaining <= 0) {
            uploadRemaining = 0
            faces.removeAll { it.first == uploadId }
            faces += uploadId to "Custom"
            reply(WearType.WATCH_FACE, FaceId.INSTALL_RESULT, WearField.WATCH_FACE, ProtoWriter().message(7, ProtoWriter().string(1, uploadId).uint(2, 2)), 600)
        }
    }

    private fun private(cmd: Int) {
        val data = when (cmd) {
            PrivateId.HELLO -> fit.silent.core.protocol.Bytes.of(0x7C, 0xE6, 0, 0, 1, 0, 0, 0, 0, 7, 0x1C, 0, 0)
            PrivateId.USER_ID -> fit.silent.core.protocol.Bytes.of(0x7C, 0xE6, 0x27, 0, 1, 0x9D, 0xCD, 0xEA, 0xE0, 0x5A, 0x48, 0, 0, 0, 0, 0, 0, 0, 0xC0)
            else -> ByteArray(1)
        }
        emit(BerryFrame.encode(serial++ and 0xFF, BerryFrame.EVENT_PRIVATE, BerryFrame.TYPE_PRIVATE, BerryFrame.privatePayload(cmd, data)), 120)
    }

    private fun reply(type: Int, id: Int, field: Int, body: ProtoWriter?, delay: Long = 60) {
        val w = ProtoWriter().uint(WearField.TYPE, type).uint(WearField.ID, id)
        if (body != null) w.message(field, body)
        emit(BerryFrame.encode(serial++ and 0xFF, BerryFrame.EVENT_PROTOBUF, BerryFrame.TYPE_PROTOBUF, w.bytes), delay)
    }

    private fun replyError(m: WearMessage) {
        val w = ProtoWriter().uint(WearField.TYPE, m.type).uint(WearField.ID, m.id).uint(WearField.ERROR, 3)
        emit(BerryFrame.encode(serial++ and 0xFF, BerryFrame.EVENT_PROTOBUF, BerryFrame.TYPE_PROTOBUF, w.bytes))
    }

    private fun wear(m: WearMessage) {
        when (m.type) {
            WearType.DEVICE -> when (m.id) {
                DeviceId.BATTERY -> reply(m.type, m.id, WearField.DEVICE, ProtoWriter().message(2, ProtoWriter().uint(1, 76).uint(2, 2)))
                DeviceId.INFO -> reply(
                    m.type, m.id, WearField.DEVICE,
                    ProtoWriter().message(1, ProtoWriter().string(1, "1.0.39").string(2, "NF 2").string(3, "48:5A:00:00:00:00").string(4, "DEMO0001")
                        .message(5, ProtoWriter().uint(1, 76).uint(2, 2))),
                )
            }
            WearType.WATCH_FACE -> when (m.id) {
                FaceId.LIST -> {
                    val list = ProtoWriter()
                    faces.forEach { (id, name) -> list.message(1, ProtoWriter().string(1, id).string(2, name).bool(3, id == current).bool(4, id != "1001")) }
                    reply(m.type, m.id, WearField.WATCH_FACE, ProtoWriter().message(1, list))
                }
                FaceId.SET -> { current = m.body(WearField.WATCH_FACE)?.string(2) ?: current; reply(m.type, m.id, WearField.WATCH_FACE, ProtoWriter().bool(4, true)) }
                FaceId.REMOVE -> { val id = m.body(WearField.WATCH_FACE)?.string(2); faces.removeAll { it.first == id }; reply(m.type, m.id, WearField.WATCH_FACE, ProtoWriter().bool(4, true)) }
                FaceId.PREPARE -> {
                    uploadId = m.body(WearField.WATCH_FACE)?.message(6)?.string(1).orEmpty()
                    reply(m.type, m.id, WearField.WATCH_FACE, ProtoWriter().message(9, ProtoWriter().string(1, uploadId).uint(2, 0).uint(3, 0).uint(4, 4096).uint(6, 28)))
                }
            }
            WearType.MICRO -> if (m.id == MicroId.BASIC_DATA_REQUEST) {
                val today = DemoData.records(1).first()
                val steps = fit.silent.core.protocol.Bytes.u32le(today.bytes, 9).toInt()
                reply(m.type, MicroId.BASIC_DATA_REPORT, WearField.MICRO, ProtoWriter().message(4, ProtoWriter().uint(1, steps).uint(2, steps / 25).uint(3, steps * 78 / 100).uint(4, 74).uint(5, 98).uint(13, 31)))
            }
            WearType.SETTING_MENU -> when (m.id) {
                MenuId.FITNESS_IDS -> {
                    val list = ProtoWriter()
                    DemoData.records(7).forEach { list.bytes(1, it.id.raw) }
                    reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.FITNESS_ID_LIST, ProtoWriter().message(1, list)))
                }
                MenuId.FILE_SEND_STATUS -> sendFitnessFile()
                MenuId.HEART_RATE -> reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.HEART_RATE, ProtoWriter().uint(1, 0).uint(2, 5).bool(3, true).uint(4, 130)))
                MenuId.SPO2 -> reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.SPO2, ProtoWriter().uint(1, 0).uint(2, 30)))
                MenuId.STRESS -> reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.STRESS, ProtoWriter().bool(1, true).bool(2, false)))
                MenuId.DND -> reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.DND, ProtoWriter().bool(1, false).message(2, ProtoWriter().uint(1, 22).uint(2, 0)).message(3, ProtoWriter().uint(1, 7).uint(2, 0))))
                MenuId.ALARMS -> reply(m.type, m.id, WearField.SETTING_MENU, ProtoWriter().message(MenuField.ALARMS, ProtoWriter()))
                else -> if (m.root.fields.none { it.number == WearField.SETTING_MENU }) replyError(m)
            }
            WearType.NOTIFICATION -> if (m.id == 5) {
                val list = ProtoWriter()
                listOf("On my way", "Call you later").forEach { list.message(1, ProtoWriter().string(1, it)) }
                reply(m.type, m.id, WearField.NOTIFICATION, ProtoWriter().message(5, list))
            }
        }
    }

    private fun sendFitnessFile() {
        val file = DemoData.file(DemoData.records(7))
        val header = file.copyOfRange(0, minOf(file.size, 1000))
        emit(ByteSink().bytes(BerryFrame.HEAD_FILE).u8(serial++ and 0xFF).u8(1).u8(15).u16(header.size).bytes(header).toByteArray(), 200)
        var at = header.size
        var index = 1
        while (at < file.size) {
            val end = minOf(file.size, at + 1000)
            emit(ByteSink().u8(0x22).u8(0x48).u8(0x5A).u16(end - at).u8(index++).bytes(file, at, end).toByteArray(), 220L + index * 10)
            at = end
        }
        handler.postDelayed({
            reply(WearType.SETTING_MENU, MenuId.DEVICE_FILE_STATUS, WearField.SETTING_MENU, ProtoWriter().message(MenuField.DEVICE_FILE_STATUS, ProtoWriter().uint(1, 15).uint(2, 1)))
        }, 400L + index * 10)
    }
}
