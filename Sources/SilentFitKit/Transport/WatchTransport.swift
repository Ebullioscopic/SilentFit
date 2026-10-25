import Foundation

public enum TransportState: Int {
    case disconnected = 0
    case connecting = 1
    case connected = 2
    case failed = 3
}

/// A watch that the platform already knows about (paired/bonded) or discovered.
public struct DiscoveredWatch: Identifiable, Hashable, Codable {
    public var id: String
    public var name: String
    public var address: String
    public var transport: String

    public init(name: String, address: String, transport: String) {
        self.id = address
        self.name = name
        self.address = address
        self.transport = transport
    }

    /// Noise Berry-protocol watches advertise names like "NF 2_A1B2", "NoiseFit …", "ColorFit …".
    public var looksLikeNoiseWatch: Bool {
        let n = name.lowercased()
        return n.hasPrefix("nf ") || n.hasPrefix("nf_") || n.contains("noise") || n.contains("colorfit") || n.contains("luna")
    }
}

/// Byte-stream link to a watch. Callbacks may arrive on any thread.
public protocol WatchTransport {
    var kind: String { get }
    var onReceive: (([UInt8]) -> Void)? { get set }
    var onStateChange: ((TransportState, String) -> Void)? { get set }
    func knownWatches() -> [DiscoveredWatch]
    func connect(address: String)
    func disconnect()
    func send(_ bytes: [UInt8]) -> Bool
}

public enum TransportFactory {
    /// The best real transport for the current platform.
    public static func platformDefault() -> WatchTransport {
        #if SKIP
        return SppTransport()
        #elseif os(macOS)
        return RfcommTransport()
        #else
        return BleTransport()
        #endif
    }
}
