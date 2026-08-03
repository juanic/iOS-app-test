package com.stabilar.core.metrics

import com.stabilar.core.parser.CopPoint
import com.stabilar.core.parser.SensorSample

/**
 * Instantánea plana de [SessionMetrics] para interop con Swift: todas las
 * propiedades son no-nulas (las frecuencias sin valor se exportan como NaN).
 */
data class SessionMetricsSnapshot(
    val pointCount: Int,
    val durationSeconds: Double,
    val swayPathLengthMm: Double,
    val ellipseAreaMm2: Double,
    val meanVelocityMmS: Double,
    val rangeXmm: Double,
    val rmsXmm: Double,
    val rangeYmm: Double,
    val rmsYmm: Double,
    val s1Avg: Double,
    val s2Avg: Double,
    val s3Avg: Double,
    val s1Max: Double,
    val s2Max: Double,
    val s3Max: Double,
    val s1Min: Double,
    val s2Min: Double,
    val s3Min: Double,
    val totalAvgLoad: Double,
    val meanFreqXHz: Double,
    val meanFreqYHz: Double
)

/**
 * Acumula muestras de COP + carga durante una sesión y delega el cálculo de
 * métricas y la elipse 95% en [StabilometryMetrics] y [CopEllipseCalculator].
 * Vive en commonMain: es testable en JVM y consumible desde Swift con
 * argumentos primitivos (sin colecciones Kotlin).
 */
class SessionRecorder {

    private val points = mutableListOf<CopPoint>()
    private val samples = mutableListOf<SensorSample>()

    fun reset() {
        points.clear()
        samples.clear()
    }

    fun addSample(x: Double, y: Double, timestampMs: Long, s1: Double, s2: Double, s3: Double) {
        points += CopPoint(x = x, y = y, timestamp = timestampMs)
        samples += SensorSample(s1 = s1, s2 = s2, s3 = s3, timestamp = timestampMs)
    }

    fun count(): Int = points.size

    fun compute(durationSeconds: Double): SessionMetricsSnapshot {
        val m = StabilometryMetrics.compute(points, samples, durationSeconds)
        return SessionMetricsSnapshot(
            pointCount = m.pointCount,
            durationSeconds = m.durationSeconds,
            swayPathLengthMm = m.swayPathLengthMm,
            ellipseAreaMm2 = m.ellipseAreaMm2,
            meanVelocityMmS = m.meanVelocityMmS,
            rangeXmm = m.rangeXmm,
            rmsXmm = m.rmsXmm,
            rangeYmm = m.rangeYmm,
            rmsYmm = m.rmsYmm,
            s1Avg = m.s1Avg,
            s2Avg = m.s2Avg,
            s3Avg = m.s3Avg,
            s1Max = m.s1Max,
            s2Max = m.s2Max,
            s3Max = m.s3Max,
            s1Min = m.s1Min,
            s2Min = m.s2Min,
            s3Min = m.s3Min,
            totalAvgLoad = m.totalAvgLoad,
            meanFreqXHz = m.meanFreqXHz ?: Double.NaN,
            meanFreqYHz = m.meanFreqYHz ?: Double.NaN
        )
    }

    fun ellipse(): CopEllipse? = CopEllipseCalculator.compute(points)

    fun xs(): DoubleArray = points.map { it.x }.toDoubleArray()

    fun ys(): DoubleArray = points.map { it.y }.toDoubleArray()

    fun ts(): LongArray = points.map { it.timestamp }.toLongArray()
}
