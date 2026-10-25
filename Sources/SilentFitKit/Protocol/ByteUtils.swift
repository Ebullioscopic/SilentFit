import Foundation

public enum Delay {
    public static func ms(_ milliseconds: Int) async {
        try? await Task.sleep(nanoseconds: UInt64(milliseconds) * UInt64(1_000_000))
    }
}

/// Little-endian byte helpers shared by the Berry framing and fitness decoders.
///
/// Bytes are carried as `[UInt8]`; all arithmetic goes through `Int`/`Int64` because
/// Kotlin's unsigned types do not support implicit arithmetic when transpiled by Skip.
public enum ByteUtils {
    public static func u8(_ bytes: [UInt8], _ offset: Int) -> Int {
        guard offset >= 0 && offset < bytes.count else { return 0 }
        return Int(bytes[offset])
    }

    public static func u16le(_ bytes: [UInt8], _ offset: Int) -> Int {
        return u8(bytes, offset) | (u8(bytes, offset + 1) << 8)
    }

    public static func u32le(_ bytes: [UInt8], _ offset: Int) -> Int64 {
        let b0 = Int64(u8(bytes, offset))
        let b1 = Int64(u8(bytes, offset + 1)) << 8
        let b2 = Int64(u8(bytes, offset + 2)) << 16
        let b3 = Int64(u8(bytes, offset + 3)) << 24
        return b0 | b1 | b2 | b3
    }

    public static func le16(_ value: Int) -> [UInt8] {
        return [UInt8(value & 0xFF), UInt8((value >> 8) & 0xFF)]
    }

    public static func le32(_ value: Int) -> [UInt8] {
        return [UInt8(value & 0xFF), UInt8((value >> 8) & 0xFF), UInt8((value >> 16) & 0xFF), UInt8((value >> 24) & 0xFF)]
    }

    /// Byte array from integer literals (keeps unsigned conversions explicit for Kotlin).
    public static func bytes(_ values: [Int]) -> [UInt8] {
        var out: [UInt8] = []
        for v in values {
            out.append(UInt8(v & 0xFF))
        }
        return out
    }

    public static func zeros(_ count: Int) -> [UInt8] {
        var out: [UInt8] = []
        var i = 0
        while i < count {
            out.append(UInt8(0))
            i += 1
        }
        return out
    }

    public static func slice(_ bytes: [UInt8], _ from: Int, _ to: Int) -> [UInt8] {
        let start = max(0, min(from, bytes.count))
        let end = max(start, min(to, bytes.count))
        var out: [UInt8] = []
        var i = start
        while i < end {
            out.append(bytes[i])
            i += 1
        }
        return out
    }

    /// Index of `pattern` in `bytes` at or after `from`, or -1.
    public static func indexOf(_ pattern: [UInt8], in bytes: [UInt8], from: Int = 0) -> Int {
        if pattern.isEmpty || bytes.count < pattern.count { return -1 }
        var i = max(0, from)
        while i <= bytes.count - pattern.count {
            var match = true
            var j = 0
            while j < pattern.count {
                if bytes[i + j] != pattern[j] {
                    match = false
                    break
                }
                j += 1
            }
            if match { return i }
            i += 1
        }
        return -1
    }

    public static func startsWith(_ bytes: [UInt8], _ prefix: [UInt8]) -> Bool {
        if bytes.count < prefix.count { return false }
        var i = 0
        while i < prefix.count {
            if bytes[i] != prefix[i] { return false }
            i += 1
        }
        return true
    }

    static let hexDigits: [String] = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"]

    public static func hex(_ bytes: [UInt8]) -> String {
        var parts: [String] = []
        for b in bytes {
            let v = Int(b)
            parts.append(hexDigits[v >> 4] + hexDigits[v & 0x0F])
        }
        return parts.joined(separator: " ")
    }

    public static func fromHex(_ text: String) -> [UInt8] {
        var out: [UInt8] = []
        var high = -1
        for ch in text {
            guard let v = hexDigits.firstIndex(of: String(ch).uppercased()) else { continue }
            if high < 0 {
                high = v
            } else {
                out.append(UInt8((high << 4) | v))
                high = -1
            }
        }
        return out
    }

    public static func utf8(_ text: String) -> [UInt8] {
        guard let data = text.data(using: String.Encoding.utf8) else { return [] }
        return toBytes(data)
    }

    public static func string(_ bytes: [UInt8]) -> String {
        return String(data: Data(bytes), encoding: String.Encoding.utf8) ?? ""
    }

    public static func toBytes(_ data: Data) -> [UInt8] {
        #if SKIP
        return data.bytes
        #else
        return [UInt8](data)
        #endif
    }

    public static func toData(_ bytes: [UInt8]) -> Data {
        return Data(bytes)
    }
}
