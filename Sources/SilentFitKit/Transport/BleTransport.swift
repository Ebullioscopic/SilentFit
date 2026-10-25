import Foundation

#if os(iOS) && !SKIP
import CoreBluetooth

/// iOS transport: Berry protocol tunnelled over BLE GATT.
///
/// iOS cannot open Classic SPP links to non-MFi accessories, so this only reaches
/// Noise devices that expose the Berry GATT service (0x6666). Classic-only watches
/// such as the NF 2 are not reachable from iOS (see docs/PLATFORM_MAP.md).
public final class BleTransport: NSObject, WatchTransport, CBCentralManagerDelegate, CBPeripheralDelegate {
    public let kind = "Bluetooth LE (GATT)"
    public var onReceive: (([UInt8]) -> Void)? = nil
    public var onStateChange: ((TransportState, String) -> Void)? = nil

    static let service = CBUUID(string: "6666")
    static let notifyCharacteristic = CBUUID(string: "005E")
    static let writeCharacteristic = CBUUID(string: "005F")

    private var central: CBCentralManager!
    private var peripherals: [String: CBPeripheral] = [:]
    private var names: [String: String] = [:]
    private var target: CBPeripheral? = nil
    private var writer: CBCharacteristic? = nil
    private var pendingAddress: String? = nil

    public override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    public func knownWatches() -> [DiscoveredWatch] {
        startScan()
        return peripherals.keys.sorted().map { key in
            DiscoveredWatch(name: names[key] ?? "Watch", address: key, transport: kind)
        }
    }

    private func startScan() {
        guard central.state == .poweredOn, !central.isScanning else { return }
        for p in central.retrieveConnectedPeripherals(withServices: [Self.service]) {
            remember(p, name: p.name)
        }
        central.scanForPeripherals(withServices: [Self.service], options: nil)
    }

    private func remember(_ p: CBPeripheral, name: String?) {
        let key = p.identifier.uuidString
        peripherals[key] = p
        names[key] = name ?? p.name ?? names[key] ?? "Watch"
    }

    public func connect(address: String) {
        disconnect()
        onStateChange?(.connecting, "Connecting")
        if let uuid = UUID(uuidString: address), let p = central.retrievePeripherals(withIdentifiers: [uuid]).first {
            open(p)
        } else {
            pendingAddress = address
            startScan()
        }
    }

    private func open(_ p: CBPeripheral) {
        target = p
        p.delegate = self
        central.connect(p, options: nil)
    }

    public func disconnect() {
        pendingAddress = nil
        if let p = target {
            central.cancelPeripheralConnection(p)
        }
        target = nil
        writer = nil
    }

    public func send(_ bytes: [UInt8]) -> Bool {
        guard let p = target, let c = writer else { return false }
        let mtu = max(20, p.maximumWriteValueLength(for: .withoutResponse))
        var offset = 0
        while offset < bytes.count {
            let end = min(bytes.count, offset + mtu)
            p.writeValue(Data(bytes[offset..<end]), for: c, type: .withoutResponse)
            offset = end
        }
        return true
    }

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            startScan()
        } else if central.state == .unauthorized {
            onStateChange?(.failed, "Bluetooth permission denied")
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        remember(peripheral, name: advertisementData[CBAdvertisementDataLocalNameKey] as? String)
        if let pending = pendingAddress, pending == peripheral.identifier.uuidString {
            pendingAddress = nil
            central.stopScan()
            open(peripheral)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([Self.service])
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        onStateChange?(.failed, error?.localizedDescription ?? "Connection failed")
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        writer = nil
        onStateChange?(.disconnected, error?.localizedDescription ?? "Watch disconnected")
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == Self.service }) else {
            onStateChange?(.failed, "This watch does not expose the Berry BLE service")
            return
        }
        peripheral.discoverCharacteristics([Self.notifyCharacteristic, Self.writeCharacteristic], for: service)
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        for c in service.characteristics ?? [] {
            if c.uuid == Self.notifyCharacteristic {
                peripheral.setNotifyValue(true, for: c)
            } else if c.uuid == Self.writeCharacteristic {
                writer = c
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if characteristic.isNotifying && writer != nil {
            onStateChange?(.connected, "Connected")
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let value = characteristic.value, !value.isEmpty else { return }
        onReceive?([UInt8](value))
    }
}
#endif
