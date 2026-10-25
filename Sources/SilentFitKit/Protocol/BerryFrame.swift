import Foundation

/// Berry link-layer framing (see docs/PROTOCOL.md §2).
///
/// ```
/// 66 66 48 5A | serial | event | type | len (u16 LE) | payload   app → watch request / watch → app response
/// 88 88 48 5A | serial | event | type | len (u16 LE) | payload   watch → app request
/// 99 99 48 5A | serial | error | 00 | 00                          acknowledgement (8 bytes)
/// 11 11 48 5A | serial | event | type | len (u16 LE) | payload   large-file header (fitness/sport/log)
/// 22|33|44 48 5A | len (u16 LE) | index | payload                large-file continuation chunk
/// ```
public enum BerryFrame {
    public static let headAppRequest: [UInt8] = ByteUtils.bytes([0x66, 0x66, 0x48, 0x5A])
    public static let headDeviceRequest: [UInt8] = ByteUtils.bytes([0x88, 0x88, 0x48, 0x5A])
    public static let headAck: [UInt8] = ByteUtils.bytes([0x99, 0x99, 0x48, 0x5A])
    public static let headLargeFile: [UInt8] = ByteUtils.bytes([0x11, 0x11, 0x48, 0x5A])
    static let chunkMarkers: [Int] = [0x22, 0x33, 0x44, 0x55, 0x66, 0x77]

    public static let eventProtobuf = 1
    public static let eventPrivate = 3
    public static let typeProtobuf = 3
    public static let typePrivate = 2

    public static func encode(serial: Int, event: Int, type: Int, payload: [UInt8]) -> [UInt8] {
        var out = headAppRequest
        out.append(UInt8(serial & 0xFF))
        out.append(UInt8(event & 0xFF))
        out.append(UInt8(type & 0xFF))
        out.append(contentsOf: ByteUtils.le16(payload.count))
        out.append(contentsOf: payload)
        return out
    }

    public static func ack(serial: Int) -> [UInt8] {
        var out = headAck
        out.append(UInt8(serial & 0xFF))
        out.append(contentsOf: ByteUtils.zeros(3))
        return out
    }

    /// Private command body: cmdId (u16 LE) + length (u16 LE) + data.
    public static func privatePayload(cmdId: Int, data: [UInt8]) -> [UInt8] {
        var out = ByteUtils.le16(cmdId)
        out.append(contentsOf: ByteUtils.le16(data.count))
        out.append(contentsOf: data)
        return out
    }
}

public enum BerryInboundKind {
    case ack
    case packet
    case largeFileProgress
    case largeFile
}

/// A unit decoded from the inbound byte stream.
public struct BerryInbound {
    public let kind: BerryInboundKind
    public let fromDevice: Bool
    public let serial: Int
    public let event: Int
    public let type: Int
    public let payload: [UInt8]
    public let received: Int
    public let total: Int
}

/// Reassembles Berry frames from arbitrarily fragmented transport reads.
public final class BerryStreamParser {
    private var buffer: [UInt8] = []
    private var fileType = 0
    private var fileTotal: Int64 = -1
    private var fileData: [UInt8] = []

    public init() {
    }

    public var isReceivingLargeFile: Bool {
        return fileTotal >= 0
    }

    public func reset() {
        buffer = []
        fileTotal = -1
        fileData = []
    }

    public func feed(_ bytes: [UInt8]) -> [BerryInbound] {
        buffer.append(contentsOf: bytes)
        var out: [BerryInbound] = []
        while !buffer.isEmpty {
            let consumed = step(&out)
            if consumed < 0 {
                break
            }
            buffer = ByteUtils.slice(buffer, consumed, buffer.count)
        }
        return out
    }

    /// Returns the number of bytes consumed, 0 to retry, or -1 when more bytes are needed.
    private func step(_ out: inout [BerryInbound]) -> Int {
        if ByteUtils.startsWith(buffer, BerryFrame.headAck) {
            if buffer.count < 8 { return -1 }
            out.append(BerryInbound(kind: .ack, fromDevice: true, serial: Int(buffer[4]), event: Int(buffer[5]), type: 0, payload: [], received: 0, total: 0))
            return 8
        }
        let isAppHead = ByteUtils.startsWith(buffer, BerryFrame.headAppRequest)
        let isDeviceHead = ByteUtils.startsWith(buffer, BerryFrame.headDeviceRequest)
        let isFileHead = ByteUtils.startsWith(buffer, BerryFrame.headLargeFile)
        if isAppHead || isDeviceHead || isFileHead {
            if buffer.count < 9 { return -1 }
            let length = ByteUtils.u16le(buffer, 7)
            if buffer.count < 9 + length { return -1 }
            let payload = ByteUtils.slice(buffer, 9, 9 + length)
            if isFileHead {
                fileType = Int(buffer[6])
                fileTotal = ByteUtils.u32le(payload, 0)
                fileData = payload
                emitFileState(&out)
            } else {
                out.append(BerryInbound(kind: .packet, fromDevice: isDeviceHead, serial: Int(buffer[4]), event: Int(buffer[5]), type: Int(buffer[6]), payload: payload, received: 0, total: 0))
            }
            return 9 + length
        }
        if buffer.count >= 3 && Int(buffer[1]) == 0x48 && Int(buffer[2]) == 0x5A && BerryFrame.chunkMarkers.contains(Int(buffer[0])) {
            if buffer.count < 6 { return -1 }
            let length = ByteUtils.u16le(buffer, 3)
            if buffer.count < 6 + length { return -1 }
            let marker = Int(buffer[0])
            // 0x22 daily, 0x33 sport, 0x44 log; 0x55/0x66/0x77 are voice streams we do not consume.
            if fileTotal >= 0 && (marker == 0x22 || marker == 0x33 || marker == 0x44) {
                fileData.append(contentsOf: ByteUtils.slice(buffer, 6, 6 + length))
                emitFileState(&out)
            }
            return 6 + length
        }
        // Unknown bytes: resynchronise on the next recognised header.
        var next = buffer.count
        let heads = [BerryFrame.headAck, BerryFrame.headAppRequest, BerryFrame.headDeviceRequest, BerryFrame.headLargeFile]
        for head in heads {
            let idx = ByteUtils.indexOf(head, in: buffer, from: 1)
            if idx > 0 && idx < next { next = idx }
        }
        if next == buffer.count {
            // Keep a possible partial header at the tail.
            let drop = buffer.count - 3
            return drop > 0 ? drop : -1
        }
        return next
    }

    private func emitFileState(_ out: inout [BerryInbound]) {
        let received = max(0, fileData.count - 12)
        let total = Int(fileTotal)
        if received >= total {
            out.append(BerryInbound(kind: .largeFile, fromDevice: true, serial: 0, event: 0, type: fileType, payload: fileData, received: received, total: total))
            fileTotal = -1
            fileData = []
        } else {
            out.append(BerryInbound(kind: .largeFileProgress, fromDevice: true, serial: 0, event: 0, type: fileType, payload: [], received: received, total: total))
        }
    }
}
