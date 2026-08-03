import CoreBluetooth

/// UUIDs del servicio Nordic UART Service (NUS) del firmware ESP32-S3.
enum NusConstants {
    static let serviceUUID = CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    /// Android -> dispositivo (WRITE)
    static let rxUUID = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    /// dispositivo -> Android (NOTIFY)
    static let txUUID = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
}
