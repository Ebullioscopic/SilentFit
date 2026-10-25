import Foundation

#if os(macOS) && !SKIP
import IOBluetooth

/// macOS transport: Classic Bluetooth RFCOMM (SPP) through IOBluetooth.
///
/// The watch must be paired with this Mac in System Settings › Bluetooth.
public final class RfcommTransport: NSObject, WatchTransport, IOBluetoothRFCOMMChannelDelegate {
    public let kind = "Bluetooth Classic (RFCOMM)"
    public var onReceive: (([UInt8]) -> Void)? = nil
    public var onStateChange: ((TransportState, String) -> Void)? = nil
    private var channel: IOBluetoothRFCOMMChannel? = nil
    private var device: IOBluetoothDevice? = nil

    public override init() {
        super.init()
    }

    public func knownWatches() -> [DiscoveredWatch] {
        guard let paired = IOBluetoothDevice.pairedDevices() else { return [] }
        var out: [DiscoveredWatch] = []
        for item in paired {
            guard let d = item as? IOBluetoothDevice, let address = d.addressString else { continue }
            let normalized = address.replacingOccurrences(of: "-", with: ":").uppercased()
            out.append(DiscoveredWatch(name: d.name ?? normalized, address: normalized, transport: kind))
        }
        return out
    }

    public func connect(address: String) {
        disconnect()
        onStateChange?(.connecting, "Connecting")
        DispatchQueue.main.async {
            self.open(address: address)
        }
    }

    private func open(address: String) {
        guard let d = IOBluetoothDevice(addressString: address) else {
            onStateChange?(.failed, "Unknown Bluetooth address")
            return
        }
        device = d
        var channelID: BluetoothRFCOMMChannelID = 1
        let sppUUID = IOBluetoothSDPUUID(uuid16: 0x1101)
        if d.performSDPQuery(nil) == kIOReturnSuccess, let record = d.getServiceRecord(for: sppUUID) {
            var found: BluetoothRFCOMMChannelID = 0
            if record.getRFCOMMChannelID(&found) == kIOReturnSuccess && found != 0 {
                channelID = found
            }
        }
        var opened: IOBluetoothRFCOMMChannel? = nil
        let status = d.openRFCOMMChannelAsync(&opened, withChannelID: channelID, delegate: self)
        if status != kIOReturnSuccess {
            onStateChange?(.failed, "Could not open RFCOMM channel (\(status)). Is the watch paired with this Mac and NoiseFit closed on your phone?")
            return
        }
        channel = opened
    }

    public func disconnect() {
        if let c = channel {
            c.setDelegate(nil)
            c.close()
        }
        channel = nil
        if let d = device, d.isConnected() {
            d.closeConnection()
        }
        device = nil
    }

    public func send(_ bytes: [UInt8]) -> Bool {
        guard let c = channel, c.isOpen() else { return false }
        let mtu = max(Int(c.getMTU()), 64)
        var offset = 0
        var ok = true
        while offset < bytes.count && ok {
            var chunk = Array(bytes[offset..<min(bytes.count, offset + mtu)])
            let length = UInt16(chunk.count)
            ok = chunk.withUnsafeMutableBytes { buffer in
                c.writeSync(buffer.baseAddress, length: length) == kIOReturnSuccess
            }
            offset += mtu
        }
        return ok
    }

    public func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        if error == kIOReturnSuccess {
            onStateChange?(.connected, "Connected")
        } else {
            onStateChange?(.failed, "RFCOMM open failed (\(error))")
        }
    }

    public func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        guard let pointer = dataPointer, dataLength > 0 else { return }
        let buffer = UnsafeRawBufferPointer(start: pointer, count: dataLength)
        onReceive?(Array(buffer))
    }

    public func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        channel = nil
        onStateChange?(.disconnected, "Watch disconnected")
    }
}
#endif
