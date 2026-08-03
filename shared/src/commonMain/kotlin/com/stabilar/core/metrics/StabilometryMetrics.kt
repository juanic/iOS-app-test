package com.stabilar.core.metrics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

import com.stabilar.core.parser.CopPoint
import com.stabilar.core.parser.SensorSample
private const val CHI2_95_2DOF = 5.991

data class SessionMetrics(
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
    val meanFreqXHz: Double?,
    val meanFreqYHz: Double?
)

data class SessionReport(
    val timestamp: Long,
    val deviceName: String,
    val metrics: SessionMetrics,
    val points: List<CopPoint>
) {
    companion object
}

object StabilometryMetrics {

    fun compute(
        points: List<CopPoint>,
        samples: List<SensorSample>,
        durationSeconds: Double
    ): SessionMetrics {
        val pathLength = swayPathLength(points)
        val ellipseArea = confidenceEllipseArea(points)
        val duration = if (durationSeconds > 0) durationSeconds else
            if (points.size >= 2) (points.last().timestamp - points.first().timestamp) / 1000.0 else 0.0

        val (rangeX, rmsX) = axisStats(points) { it.x }
        val (rangeY, rmsY) = axisStats(points) { it.y }

        val loads = loadStats(samples)

        return SessionMetrics(
            pointCount = points.size,
            durationSeconds = duration,
            swayPathLengthMm = pathLength,
            ellipseAreaMm2 = ellipseArea,
            meanVelocityMmS = if (duration > 0) pathLength / duration else 0.0,
            rangeXmm = rangeX,
            rmsXmm = rmsX,
            rangeYmm = rangeY,
            rmsYmm = rmsY,
            s1Avg = loads.s1Avg,
            s2Avg = loads.s2Avg,
            s3Avg = loads.s3Avg,
            s1Max = loads.s1Max,
            s2Max = loads.s2Max,
            s3Max = loads.s3Max,
            s1Min = loads.s1Min,
            s2Min = loads.s2Min,
            s3Min = loads.s3Min,
            totalAvgLoad = loads.s1Avg + loads.s2Avg + loads.s3Avg,
            meanFreqXHz = if (points.size >= 4) meanFrequency(points.map { it.x }.toDoubleArray(), duration) else null,
            meanFreqYHz = if (points.size >= 4) meanFrequency(points.map { it.y }.toDoubleArray(), duration) else null
        )
    }

    fun swayPathLength(points: List<CopPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    fun confidenceEllipseArea(points: List<CopPoint>): Double {
        if (points.size < 2) return 0.0
        val mx = points.map { it.x }.average()
        val my = points.map { it.y }.average()
        val n = points.size

        var covXX = 0.0
        var covYY = 0.0
        var covXY = 0.0
        for (p in points) {
            val dx = p.x - mx
            val dy = p.y - my
            covXX += dx * dx
            covYY += dy * dy
            covXY += dx * dy
        }
        covXX /= n
        covYY /= n
        covXY /= n

        val trace = covXX + covYY
        val det = covXX * covYY - covXY * covXY
        val disc = sqrt((trace * trace - 4 * det).coerceAtLeast(0.0))
        val lambda1 = (trace + disc) / 2.0
        val lambda2 = (trace - disc) / 2.0
        return PI * CHI2_95_2DOF * sqrt(lambda1 * lambda2)
    }

    fun axisStats(points: List<CopPoint>, selector: (CopPoint) -> Double): Pair<Double, Double> {
        if (points.isEmpty()) return 0.0 to 0.0
        val values = points.map(selector)
        val mean = values.average()
        val range = values.max() - values.min()
        val rms = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        return range to rms
    }

    fun loadStats(samples: List<SensorSample>): SensorLoadStats {
        if (samples.isEmpty()) {
            return SensorLoadStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val s1 = samples.map { it.s1 }
        val s2 = samples.map { it.s2 }
        val s3 = samples.map { it.s3 }
        return SensorLoadStats(
            s1Avg = s1.average(),
            s2Avg = s2.average(),
            s3Avg = s3.average(),
            s1Max = s1.max(),
            s2Max = s2.max(),
            s3Max = s3.max(),
            s1Min = s1.min(),
            s2Min = s2.min(),
            s3Min = s3.min()
        )
    }

    fun meanFrequency(values: DoubleArray, durationSeconds: Double): Double? {
        val n = values.size
        if (n < 4 || durationSeconds <= 0) return null
        val size = nextPowerOfTwo(n)
        val sampleRate = n / durationSeconds
        val signal = Array(size) { i -> Complex(if (i < n) values[i] else 0.0, 0.0) }
        val spectrum = fft(signal)
        val half = size / 2
        var weighted = 0.0
        var total = 0.0
        for (k in 1 until half) {
            val p = spectrum[k].re * spectrum[k].re + spectrum[k].im * spectrum[k].im
            val f = k * sampleRate / size
            weighted += f * p
            total += p
        }
        return if (total > 0) weighted / total else null
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    private data class Complex(val re: Double, val im: Double)

    private fun fft(a: Array<Complex>): Array<Complex> {
        val n = a.size
        if (n <= 1) return a
        val half = n / 2
        val even = Array(half) { a[it * 2] }
        val odd = Array(half) { a[it * 2 + 1] }
        val fe = fft(even)
        val fo = fft(odd)
        val out = Array(n) { Complex(0.0, 0.0) }
        for (k in 0 until half) {
            val angle = -2 * PI * k / n
            val c = cos(angle)
            val s = sin(angle)
            val re = fo[k].re * c - fo[k].im * s
            val im = fo[k].re * s + fo[k].im * c
            out[k] = Complex(fe[k].re + re, fe[k].im + im)
            out[k + half] = Complex(fe[k].re - re, fe[k].im - im)
        }
        return out
    }
}

data class SensorLoadStats(
    val s1Avg: Double,
    val s2Avg: Double,
    val s3Avg: Double,
    val s1Max: Double,
    val s2Max: Double,
    val s3Max: Double,
    val s1Min: Double,
    val s2Min: Double,
    val s3Min: Double
)
