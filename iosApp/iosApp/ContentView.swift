import SwiftUI

struct ContentView: View {
    @StateObject private var ble = BleManager()
    @StateObject private var store = SessionStore()

    var body: some View {
        TabView {
            SessionView(ble: ble, store: store)
                .tabItem {
                    Label("Vivo", systemImage: "waveform.path.ecg")
                }
            HistoryView(store: store)
                .tabItem {
                    Label("Historial", systemImage: "clock.arrow.circlepath")
                }
        }
        .fullScreenCover(item: $store.lastReport) { record in
            NavigationStack {
                ReportView(record: record)
            }
        }
    }
}

#Preview {
    ContentView()
}
