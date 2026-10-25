import Foundation

#if SKIP
/// Android transport: Classic Bluetooth SPP via `SppConnection.kt`.
public final class SppTransport: WatchTransport {
    public let kind = "Bluetooth Classic (SPP)"
    public var onReceive: (([UInt8]) -> Void)? = nil
    public var onStateChange: ((TransportState, String) -> Void)? = nil
    private var connection: SppConnection? = nil

    public init() {
    }

    public func knownWatches() -> [DiscoveredWatch] {
        let context = ProcessInfo.processInfo.androidContext
        var out: [DiscoveredWatch] = []
        for entry in SppConnection.bondedDevices(context) {
            let name = entry.substringBeforeLast("|")
            let address = entry.substringAfterLast("|")
            out.append(DiscoveredWatch(name: name, address: address, transport: kind))
        }
        return out
    }

    public func connect(address: String) {
        connection?.disconnect()
        let link = SppConnection(onBytes: { bytes in
            self.onReceive?(ByteUtils.toBytes(Data(platformValue: bytes)))
        }, onState: { state, message in
            self.onStateChange?(TransportState(rawValue: state) ?? TransportState.failed, message)
        })
        connection = link
        link.connect(ProcessInfo.processInfo.androidContext, address)
    }

    public func disconnect() {
        connection?.disconnect()
        connection = nil
        onStateChange?(TransportState.disconnected, "Disconnected")
    }

    public func send(_ bytes: [UInt8]) -> Bool {
        guard let link = connection else { return false }
        return link.write(ByteUtils.toData(bytes).kotlin())
    }
}
#endif
