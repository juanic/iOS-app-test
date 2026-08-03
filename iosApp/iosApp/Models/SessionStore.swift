import Foundation
import Combine

/// Historial de sesiones persistido en JSON dentro de Application Support.
final class SessionStore: ObservableObject {
    @Published var sessions: [SessionRecord] = []
    @Published var lastReport: SessionRecord?

    private let fileURL: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        fileURL = dir.appendingPathComponent("sessions.json")
        encoder.outputFormatting = [.prettyPrinted]
        encoder.dateEncodingStrategy = .millisecondsSince1970
        decoder.dateDecodingStrategy = .millisecondsSince1970
        load()
    }

    func add(_ record: SessionRecord) {
        sessions.insert(record, at: 0)
        lastReport = record
        save()
    }

    func remove(_ record: SessionRecord) {
        sessions.removeAll { $0.id == record.id }
        save()
    }

    func clearLastReport() {
        lastReport = nil
    }

    private func save() {
        try? encoder.encode(sessions).write(to: fileURL)
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        sessions = (try? decoder.decode([SessionRecord].self, from: data)) ?? []
    }
}
