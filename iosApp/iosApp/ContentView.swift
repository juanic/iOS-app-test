import SwiftUI

struct ContentView: View {
    @StateObject private var ble = BleManager()

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                statusBar
                if ble.isConnected {
                    liveDataSection
                } else {
                    scanSection
                    deviceList
                }
            }
            .padding()
            .navigationTitle("StabilAr FootX")
        }
    }

    // MARK: - Estado

    private var statusBar: some View {
        HStack {
            Circle()
                .fill(ble.isConnected ? Color.green : Color.gray)
                .frame(width: 10, height: 10)
            Text(ble.stateText)
                .font(.subheadline)
            Spacer()
            if ble.isScanning {
                ProgressView()
            }
        }
    }

    // MARK: - Escaneo

    private var scanSection: some View {
        Button {
            if ble.isScanning {
                ble.stopScan()
            } else {
                ble.startScan()
            }
        } label: {
            Text(ble.isScanning ? "Detener escaneo" : "Escanear plataforma")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
    }

    private var deviceList: some View {
        List(ble.devices, id: \.identifier) { device in
            Button {
                ble.connect(to: device)
            } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(device.name ?? "Plataforma FootX")
                        Text(device.identifier.uuidString)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundColor(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Datos en vivo

    private var liveDataSection: some View {
        VStack(spacing: 20) {
            Text(ble.connectedName ?? "")
                .font(.headline)
            HStack(spacing: 24) {
                metricCard(title: "Peso", value: String(format: "%.1f", ble.weightKg), unit: "kg")
                metricCard(title: "COP X", value: String(format: "%.1f", ble.cop.x), unit: "mm")
                metricCard(title: "COP Y", value: String(format: "%.1f", ble.cop.y), unit: "mm")
            }
            Button("Desconectar", role: .destructive) {
                ble.disconnect()
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
    }

    private func metricCard(title: String, value: String, unit: String) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.title3)
                .bold()
                .monospacedDigit()
            Text(unit)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(minWidth: 70)
    }
}

#Preview {
    ContentView()
}
