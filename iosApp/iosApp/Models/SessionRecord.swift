import Foundation

struct CopEllipseRecord: Codable {
    var centerX: Double
    var centerY: Double
    var semiMajor: Double
    var semiMinor: Double
    var angleRad: Double
}

/// Informe de una sesión de estabilometría, persistible con Codable.
struct SessionRecord: Codable, Identifiable {
    let id: UUID
    let timestamp: Date
    let deviceName: String
    let pointCount: Int
    let durationSeconds: Double
    let swayPathLengthMm: Double
    let ellipseAreaMm2: Double
    let meanVelocityMmS: Double
    let rangeXmm: Double
    let rmsXmm: Double
    let rangeYmm: Double
    let rmsYmm: Double
    let s1Avg: Double
    let s2Avg: Double
    let s3Avg: Double
    let s1Max: Double
    let s2Max: Double
    let s3Max: Double
    let s1Min: Double
    let s2Min: Double
    let s3Min: Double
    let totalAvgLoad: Double
    let meanFreqXHz: Double?
    let meanFreqYHz: Double?
    let xs: [Double]
    let ys: [Double]
    let ts: [Int64]
    let ellipse: CopEllipseRecord?
}

extension SessionRecord {
    init(
        snapshot: SessionMetricsSnapshot,
        ellipse: CopEllipse?,
        deviceName: String,
        timestamp: Date,
        xs: [Double],
        ys: [Double],
        ts: [Int64]
    ) {
        id = UUID()
        self.timestamp = timestamp
        self.deviceName = deviceName
        pointCount = Int(snapshot.pointCount)
        durationSeconds = snapshot.durationSeconds
        swayPathLengthMm = snapshot.swayPathLengthMm
        ellipseAreaMm2 = snapshot.ellipseAreaMm2
        meanVelocityMmS = snapshot.meanVelocityMmS
        rangeXmm = snapshot.rangeXmm
        rmsXmm = snapshot.rmsXmm
        rangeYmm = snapshot.rangeYmm
        rmsYmm = snapshot.rmsYmm
        s1Avg = snapshot.s1Avg
        s2Avg = snapshot.s2Avg
        s3Avg = snapshot.s3Avg
        s1Max = snapshot.s1Max
        s2Max = snapshot.s2Max
        s3Max = snapshot.s3Max
        s1Min = snapshot.s1Min
        s2Min = snapshot.s2Min
        s3Min = snapshot.s3Min
        totalAvgLoad = snapshot.totalAvgLoad
        meanFreqXHz = snapshot.meanFreqXHz.isNaN ? nil : snapshot.meanFreqXHz
        meanFreqYHz = snapshot.meanFreqYHz.isNaN ? nil : snapshot.meanFreqYHz
        self.xs = xs
        self.ys = ys
        self.ts = ts
        self.ellipse = ellipse.map {
            CopEllipseRecord(
                centerX: $0.centerX,
                centerY: $0.centerY,
                semiMajor: $0.semiMajor,
                semiMinor: $0.semiMinor,
                angleRad: $0.angleRad
            )
        }
    }

    var dateText: String {
        let f = DateFormatter()
        f.dateFormat = "dd/MM/yyyy HH:mm"
        return f.string(from: timestamp)
    }
}
