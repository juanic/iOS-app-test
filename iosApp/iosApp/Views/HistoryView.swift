import SwiftUI

struct HistoryView: View {
    @ObservedObject var store: SessionStore

    var body: some View {
        NavigationStack {
            Group {
                if store.sessions.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 44))
                            .foregroundColor(.secondary)
                        Text("Sin sesiones")
                            .font(.headline)
                        Text("Finalizá una sesión en la pestaña Vivo para generar un informe.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(store.sessions) { record in
                            NavigationLink(value: record) {
                                HistoryRow(record: record)
                            }
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                store.remove(store.sessions[index])
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Historial")
            .navigationDestination(for: SessionRecord.self) { record in
                ReportView(record: record)
            }
        }
    }
}

private struct HistoryRow: View {
    let record: SessionRecord

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(record.dateText)
                    .font(.subheadline)
                    .bold()
                Text(record.deviceName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text(String(format: "%.1f s", record.durationSeconds))
                    .font(.subheadline)
                Text("\(record.pointCount) puntos")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}
