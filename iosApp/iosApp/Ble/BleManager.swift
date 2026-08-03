import Combine
import CoreBluetooth
import Shared

/// Cliente BLE del servicio NUS. El parseo de tramas, la conversión a fuerza/kg
/// y el cálculo del COP los hace el framework Kotlin `Shared` (com.stabilar.core)
/// a través de la fachada `FootXBridge`.
final class BleManager: NSObject, ObservableObject {
    @Published var stateText = "Bluetooth no disponible"
    @Published var isScanning = false
    @Published var isConnected = false
    @Published var devices: [CBPeripheral] = []
    @Published var connectedName: String?
    @Published var weightKg: Double = 0
    @Published var cop: (x: Double, y: Double) = (0, 0)
    @Published var sensors: (Double, Double, Double) = (0, 0, 0)

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var rxCharacteristic: CBCharacteristic?
    private var txCharacteristic: CBCharacteristic?
    private var calibration: CalibrationData?
    private var calParts: [Double] = []

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        guard central.state == .poweredOn else {
            stateText = "Bluetooth no disponible"
            return
        }
        devices = []
        isScanning = true
        stateText = "Escaneando plataformas..."
        central.scanForPeripherals(
            withServices: [NusConstants.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }

    func stopScan() {
        isScanning = false
        central.stopScan()
    }

    func connect(to device: CBPeripheral) {
        stopScan()
        stateText = "Conectando..."
        peripheral = device
        device.delegate = self
        central.connect(device)
    }

    func disconnect() {
        if let peripheral {
            central.cancelPeripheralConnection(peripheral)
        }
    }

    // MARK: - Envío de comandos

    private func send(_ command: String) {
        guard let peripheral, let rxCharacteristic else { return }
        let data = Data(command.utf8)
        let mtu = peripheral.maximumWriteValueLength(for: .withResponse)
        if data.count <= mtu {
            peripheral.writeValue(data, for: rxCharacteristic, type: .withResponse)
        } else {
            var offset = 0
            while offset < data.count {
                let end = min(offset + mtu, data.count)
                peripheral.writeValue(data.subdata(in: offset..<end), for: rxCharacteristic, type: .withResponse)
                offset = end
            }
        }
    }

    // MARK: - Procesamiento de tramas (delega en el core Kotlin)

    private func handleFrame(_ data: Data) {
        let bytes = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            bytes.set(index: Int32(index), value: byte)
        }
        let frame = FootXBridge.shared.parseFrame(data: bytes)

        if frame.isSensor {
            let s1 = FootXBridge.shared.rawToForceKg(raw: frame.s1)
            let s2 = FootXBridge.shared.rawToForceKg(raw: frame.s2)
            let s3 = FootXBridge.shared.rawToForceKg(raw: frame.s3)
            sensors = (s1, s2, s3)
            weightKg = FootXBridge.shared.weightKg(
                s1: frame.s1, s2: frame.s2, s3: frame.s3,
                calibration: calibration
            )
            let point = FootXBridge.shared.toCopPoint(
                s1: frame.s1, s2: frame.s2, s3: frame.s3,
                calibration: calibration, timestamp: 0
            )
            cop = (point.x, point.y)
        } else if frame.isCalibration {
            // CAL_VALUE envía 3 tramas: (eigen1,eigen2), (eigen3,eigen4), (m,b)
            calParts.append(frame.calFirst)
            calParts.append(frame.calSecond)
            if calParts.count >= 6 {
                calibration = CalibrationData(
                    eigen1: calParts[0], eigen2: calParts[1],
                    eigen3: calParts[2], eigen4: calParts[3],
                    m: calParts[4], b: calParts[5]
                )
                calParts = []
            }
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension BleManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn: stateText = isConnected ? "Conectado" : "Listo"
        case .poweredOff: stateText = "Bluetooth apagado"
        case .unauthorized: stateText = "Bluetooth sin permiso"
        default: stateText = "Bluetooth no disponible"
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        if !devices.contains(where: { $0.identifier == peripheral.identifier }) {
            devices.append(peripheral)
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnected = true
        connectedName = peripheral.name ?? "Plataforma FootX"
        stateText = "Conectado, descubriendo servicios..."
        peripheral.discoverServices([NusConstants.serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        isConnected = false
        connectedName = nil
        rxCharacteristic = nil
        txCharacteristic = nil
        calibration = nil
        calParts = []
        stateText = "Desconectado"
    }
}

// MARK: - CBPeripheralDelegate

extension BleManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            stateText = "Error de servicio: \(error.localizedDescription)"
            return
        }
        guard let service = peripheral.services?.first(where: { $0.uuid == NusConstants.serviceUUID }) else {
            stateText = "Servicio NUS no encontrado"
            return
        }
        peripheral.discoverCharacteristics([NusConstants.rxUUID, NusConstants.txUUID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        for characteristic in service.characteristics ?? [] {
            if characteristic.uuid == NusConstants.rxUUID {
                rxCharacteristic = characteristic
            } else if characteristic.uuid == NusConstants.txUUID {
                txCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
        if rxCharacteristic != nil {
            stateText = "Conectado"
            send("CAL_VALUE")
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if let error {
            stateText = "Notificación fallida: \(error.localizedDescription)"
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let value = characteristic.value, !value.isEmpty else { return }
        handleFrame(value)
    }
}
