import SwiftUI

struct SessionView: View {
    @ObservedObject var ble: BleManager
    @ObservedObject var store: SessionStore

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                statusBar
                if ble.isConnected {
                    liveSection
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

    // MARK: - Datos en vivo + grabación

    private var liveSection: some View {
        VStack(spacing: 20) {
            Text(ble.connectedName ?? "")
                .font(.headline)
            HStack(spacing: 24) {
                MetricCard(title: "Peso", value: String(format: "%.1f", ble.weightKg), unit: "kg")
                MetricCard(title: "COP X", value: String(format: "%.1f", ble.cop.x), unit: "mm")
                MetricCard(title: "COP Y", value: String(format: "%.1f", ble.cop.y), unit: "mm")
            }
            if ble.isRecording {
                recordingControls
            } else {
                Button {
                    ble.startRecording()
                } label: {
                    Label("Iniciar sesión", systemImage: "record.circle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
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

    private var recordingControls: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Circle()
                    .fill(Color.red)
                    .frame(width: 10, height: 10)
                Text(String(format: "Grabando %.0f s", ble.recordElapsed))
                    .font(.subheadline)
                    .bold()
            }
            Button("Finalizar sesión", role: .destructive) {
                if let record = ble.stopRecording() {
                    store.add(record)
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

#Preview {
    SessionView(ble: BleManager(), store: SessionStore())
}
