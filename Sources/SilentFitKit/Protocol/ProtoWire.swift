import Foundation

/// Minimal protobuf (proto2) wire-format writer, sufficient for the Berry command set.
///
/// Field numbers and types come from the schemas documented in `docs/PROTOCOL.md`.
public final class ProtoWriter {
    public private(set) var bytes: [UInt8] = []

    public init() {
    }

    private func appendVarint(_ value: Int64) {
        // Negative int32/int64 values are encoded as 10-byte two's complement varints.
        var v = value
        var count = 0
        while count < 10 {
            let low = Int(v & 0x7F)
            v = ProtoWriter.ushr(v, 7)
            if v == Int64(0) {
                bytes.append(UInt8(low))
                return
            }
            bytes.append(UInt8(low | 0x80))
            count += 1
        }
    }

    private func appendKey(_ field: Int, _ wireType: Int) {
        appendVarint(Int64((field << 3) | wireType))
    }

    @discardableResult
    public func uint(_ field: Int, _ value: Int) -> ProtoWriter {
        appendKey(field, 0)
        appendVarint(Int64(value))
        return self
    }

    @discardableResult
    public func uint64(_ field: Int, _ value: Int64) -> ProtoWriter {
        appendKey(field, 0)
        appendVarint(value)
        return self
    }

    @discardableResult
    public func sint(_ field: Int, _ value: Int) -> ProtoWriter {
        let zigzag = (Int64(value) << 1) ^ (Int64(value) >> 63)
        appendKey(field, 0)
        appendVarint(zigzag)
        return self
    }

    @discardableResult
    public func bool(_ field: Int, _ value: Bool) -> ProtoWriter {
        return uint(field, value ? 1 : 0)
    }

    @discardableResult
    public func bytes(_ field: Int, _ value: [UInt8]) -> ProtoWriter {
        appendKey(field, 2)
        appendVarint(Int64(value.count))
        bytes.append(contentsOf: value)
        return self
    }

    @discardableResult
    public func string(_ field: Int, _ value: String) -> ProtoWriter {
        return bytes(field, ByteUtils.utf8(value))
    }

    @discardableResult
    public func message(_ field: Int, _ value: ProtoWriter) -> ProtoWriter {
        return bytes(field, value.bytes)
    }

    @discardableResult
    public func float(_ field: Int, _ value: Double) -> ProtoWriter {
        appendKey(field, 5)
        let bits = ProtoWriter.floatBits(value)
        bytes.append(UInt8(bits & 0xFF))
        bytes.append(UInt8((bits >> 8) & 0xFF))
        bytes.append(UInt8((bits >> 16) & 0xFF))
        bytes.append(UInt8((bits >> 24) & 0xFF))
        return self
    }

    /// Logical (unsigned) right shift.
    static func ushr(_ value: Int64, _ count: Int) -> Int64 {
        #if SKIP
        return value.ushr(count)
        #else
        return Int64(bitPattern: UInt64(bitPattern: value) >> UInt64(count))
        #endif
    }

    static func floatBits(_ value: Double) -> Int {
        #if SKIP
        return java.lang.Float.floatToIntBits(value.toFloat())
        #else
        return Int(Float(value).bitPattern)
        #endif
    }
}

/// A single decoded protobuf field.
public struct ProtoField {
    public let number: Int
    public let wireType: Int
    public let varint: Int64
    public let data: [UInt8]
}

struct VarintResult {
    let value: Int64
    let next: Int
}

/// A decoded protobuf message as an ordered list of fields with typed accessors.
public struct ProtoMessage {
    public let fields: [ProtoField]

    public init(fields: [ProtoField]) {
        self.fields = fields
    }

    public static func parse(_ bytes: [UInt8]) -> ProtoMessage? {
        var fields: [ProtoField] = []
        var i = 0
        while i < bytes.count {
            guard let key = readVarint(bytes, i) else { return nil }
            i = key.next
            let number = Int(key.value >> 3)
            let wireType = Int(key.value & 0x7)
            if number <= 0 { return nil }
            if wireType == 0 {
                guard let value = readVarint(bytes, i) else { return nil }
                fields.append(ProtoField(number: number, wireType: 0, varint: value.value, data: []))
                i = value.next
            } else if wireType == 2 {
                guard let length = readVarint(bytes, i) else { return nil }
                let after = length.next
                let end = after + Int(length.value)
                if length.value < 0 || end > bytes.count { return nil }
                fields.append(ProtoField(number: number, wireType: 2, varint: 0, data: ByteUtils.slice(bytes, after, end)))
                i = end
            } else if wireType == 5 {
                if i + 4 > bytes.count { return nil }
                fields.append(ProtoField(number: number, wireType: 5, varint: ByteUtils.u32le(bytes, i), data: []))
                i += 4
            } else if wireType == 1 {
                if i + 8 > bytes.count { return nil }
                let low = ByteUtils.u32le(bytes, i)
                let high = ByteUtils.u32le(bytes, i + 4)
                fields.append(ProtoField(number: number, wireType: 1, varint: low | (high << 32), data: []))
                i += 8
            } else {
                return nil
            }
        }
        return ProtoMessage(fields: fields)
    }

    static func readVarint(_ bytes: [UInt8], _ start: Int) -> VarintResult? {
        var result: Int64 = 0
        var shift = 0
        var i = start
        while i < bytes.count && shift < 64 {
            let b = Int(bytes[i])
            result = result | (Int64(b & 0x7F) << shift)
            i += 1
            if (b & 0x80) == 0 {
                return VarintResult(value: result, next: i)
            }
            shift += 7
        }
        return nil
    }

    public func has(_ number: Int) -> Bool {
        return fields.contains { $0.number == number }
    }

    public func int64(_ number: Int) -> Int64? {
        return fields.last { $0.number == number && ($0.wireType == 0 || $0.wireType == 5 || $0.wireType == 1) }?.varint
    }

    public func int(_ number: Int) -> Int? {
        guard let v = int64(number) else { return nil }
        return Int(v)
    }

    public func sint(_ number: Int) -> Int? {
        guard let v = int64(number) else { return nil }
        return Int(ProtoWriter.ushr(v, 1) ^ (0 - (v & 1)))
    }

    public func bool(_ number: Int) -> Bool? {
        guard let v = int64(number) else { return nil }
        return v != Int64(0)
    }

    public func float(_ number: Int) -> Double? {
        guard let bits = int64(number) else { return nil }
        #if SKIP
        return java.lang.Float.intBitsToFloat(bits.toInt()).toDouble()
        #else
        return Double(Float(bitPattern: UInt32(truncatingIfNeeded: bits)))
        #endif
    }

    public func bytes(_ number: Int) -> [UInt8]? {
        return fields.last { $0.number == number && $0.wireType == 2 }?.data
    }

    public func string(_ number: Int) -> String? {
        guard let b = bytes(number) else { return nil }
        return ByteUtils.string(b)
    }

    public func message(_ number: Int) -> ProtoMessage? {
        guard let b = bytes(number) else { return nil }
        return ProtoMessage.parse(b)
    }

    public func messages(_ number: Int) -> [ProtoMessage] {
        var out: [ProtoMessage] = []
        for f in fields {
            if f.number == number && f.wireType == 2, let m = ProtoMessage.parse(f.data) {
                out.append(m)
            }
        }
        return out
    }

    public func strings(_ number: Int) -> [String] {
        var out: [String] = []
        for f in fields {
            if f.number == number && f.wireType == 2 {
                out.append(ByteUtils.string(f.data))
            }
        }
        return out
    }

    /// Repeated varint field, accepting both packed and unpacked encodings.
    public func ints(_ number: Int) -> [Int] {
        var out: [Int] = []
        for f in fields where f.number == number {
            if f.wireType == 0 {
                out.append(Int(f.varint))
            } else if f.wireType == 2 {
                var i = 0
                while i < f.data.count {
                    guard let v = ProtoMessage.readVarint(f.data, i) else { break }
                    out.append(Int(v.value))
                    i = v.next
                }
            }
        }
        return out
    }
}
