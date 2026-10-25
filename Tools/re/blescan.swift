import CoreBluetooth
import Foundation

// Usage: swift blescan.swift [namePrefix] — scans, connects to first match, dumps GATT tree.
let prefix = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "NF"

final class Scanner: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    var central: CBCentralManager!
    var target: CBPeripheral?
    var pendingServices = 0

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        print("state:", central.state.rawValue)
        guard central.state == .poweredOn else { return }
        let connected = central.retrieveConnectedPeripherals(withServices: [
            CBUUID(string: "1800"), CBUUID(string: "180A"), CBUUID(string: "FE78"),
            CBUUID(string: "16186F00-0000-1000-8000-00807F9B34FB"), CBUUID(string: "6666"),
        ])
        for p in connected { print("already-connected:", p.name ?? "?", p.identifier) }
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? ""
        if prefix == "*" {
            let sd = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] ?? [:]
            guard !name.isEmpty || !sd.isEmpty else { return }
            print("adv:", name, peripheral.identifier, advertisementData[CBAdvertisementDataServiceUUIDsKey] ?? "", sd, advertisementData[CBAdvertisementDataManufacturerDataKey] ?? "")
            return
        }
        guard name.hasPrefix(prefix) else { return }
        print("found:", name, peripheral.identifier, "rssi", RSSI)
        for (k, v) in advertisementData { print("  adv", k, "=", v) }
        if target == nil {
            target = peripheral
            central.stopScan()
            central.connect(peripheral)
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("connected; discovering services")
        peripheral.delegate = self
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        print("fail:", error as Any); exit(1)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let services = peripheral.services ?? []
        pendingServices = services.count
        for s in services { peripheral.discoverCharacteristics(nil, for: s) }
        if services.isEmpty { exit(0) }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        print("service", service.uuid.uuidString)
        for c in service.characteristics ?? [] {
            print("   char", c.uuid.uuidString, "props", c.properties.rawValue)
        }
        pendingServices -= 1
        if pendingServices == 0 { exit(0) }
    }
}

let scanner = Scanner()
let timeout = Double(ProcessInfo.processInfo.environment["SCAN_SECONDS"] ?? "25") ?? 25
DispatchQueue.main.asyncAfter(deadline: .now() + timeout) { print("timeout"); exit(2) }
RunLoop.main.run()
