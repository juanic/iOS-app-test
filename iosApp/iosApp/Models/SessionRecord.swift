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
        pointCount: Int,
        durationSeconds: Double,
        swayPathLengthMm: Double,
        ellipseAreaMm2: Double,
        meanVelocityMmS: Double,
        rangeXmm: Double,
        rmsXmm: Double,
        rangeYmm: Double,
        rmsYmm: Double,
        s1Avg: Double,
        s2Avg: Double,
        s3Avg: Double,
        s1Max: Double,
        s2Max: Double,
        s3Max: Double,
        s1Min: Double,
        s2Min: Double,
        s3Min: Double,
        totalAvgLoad: Double,
        meanFreqXHz: Double?,
        meanFreqYHz: Double?,
        deviceName: String,
        timestamp: Date,
        xs: [Double],
        ys: [Double],
        ts: [Int64],
        ellipse: CopEllipseRecord?
    ) {
        id = UUID()
        self.timestamp = timestamp
        self.deviceName = deviceName
        self.pointCount = pointCount
        self.durationSeconds = durationSeconds
        self.swayPathLengthMm = swayPathLengthMm
        self.ellipseAreaMm2 = ellipseAreaMm2
        self.meanVelocityMmS = meanVelocityMmS
        self.rangeXmm = rangeXmm
        self.rmsXmm = rmsXmm
        self.rangeYmm = rangeYmm
        self.rmsYmm = rmsYmm
        self.s1Avg = s1Avg
        self.s2Avg = s2Avg
        self.s3Avg = s3Avg
        self.s1Max = s1Max
        self.s2Max = s2Max
        self.s3Max = s3Max
        self.s1Min = s1Min
        self.s2Min = s2Min
        self.s3Min = s3Min
        self.totalAvgLoad = totalAvgLoad
        self.meanFreqXHz = meanFreqXHz
        self.meanFreqYHz = meanFreqYHz
        self.xs = xs
        self.ys = ys
        self.ts = ts
        self.ellipse = ellipse
    }

    var dateText: String {
        let f = DateFormatter()
        f.dateFormat = "dd/MM/yyyy HH:mm"
        return f.string(from: timestamp)
    }
}
