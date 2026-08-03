package com.stabilar.core.metrics

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRecorderTest {

    private fun recordStraightLine(recorder: SessionRecorder, fromX: Double, toX: Double, n: Int) {
        recorder.reset()
        for (i in 0 until n) {
            val x = fromX + (toX - fromX) * i / (n - 1)
            val ts = 1000L + i * 20L
            recorder.addSample(x = x, y = 0.0, timestampMs = ts, s1 = 30.0, s2 = 30.0, s3 = 30.0)
        }
    }

    @Test
    fun countTracksSamples() {
        val recorder = SessionRecorder()
        recorder.addSample(0.0, 0.0, 1000L, 30.0, 30.0, 30.0)
        recorder.addSample(1.0, 1.0, 1020L, 30.0, 30.0, 30.0)
        assertEquals(2, recorder.count())
        recorder.reset()
        assertEquals(0, recorder.count())
    }

    @Test
    fun emptySessionYieldsZeroMetrics() {
        val m = SessionRecorder().compute(durationSeconds = 10.0)
        assertEquals(0, m.pointCount)
        assertEquals(0.0, m.swayPathLengthMm)
        assertEquals(0.0, m.ellipseAreaMm2)
        assertEquals(0.0, m.totalAvgLoad)
    }

    @Test
    fun swayPathLengthIsTheStraightLineDistance() {
        val recorder = SessionRecorder()
        recordStraightLine(recorder, fromX = 0.0, toX = 100.0, n = 100)
        val m = recorder.compute(durationSeconds = 2.0)
        assertEquals(100.0, m.swayPathLengthMm, 0.01)
        // 100 puntos en 20ms -> 2 s de duración reportada por timestamps
        assertEquals(2.0, m.durationSeconds, 0.01)
        assertEquals(50.0, m.swayPathLengthMm / m.durationSeconds, 0.01)
    }

    @Test
    fun ellipseAreaIsPositiveForSpreadOutPoints() {
        val recorder = SessionRecorder()
        recorder.reset()
        // Puntos no colineales: x en ciclo de 7, y en ciclo de 5.
        for (i in 0 until 70) {
            val x = (i % 7) * 10.0
            val y = (i % 5) * 8.0
            recorder.addSample(x, y, 1000L + i * 20L, 30.0, 30.0, 30.0)
        }
        val m = recorder.compute(durationSeconds = 1.0)
        assertTrue(m.ellipseAreaMm2 > 0.0)
        assertTrue(m.rangeXmm >= 60.0)
        assertTrue(m.rmsXmm > 0.0)
    }

    @Test
    fun ellipseNeedsAtLeastTwoPoints() {
        val recorder = SessionRecorder()
        assertNull(recorder.ellipse())
        recorder.addSample(0.0, 0.0, 1000L, 30.0, 30.0, 30.0)
        assertNull(recorder.ellipse())
        recorder.addSample(5.0, 3.0, 1020L, 30.0, 30.0, 30.0)
        val ellipse = assertNotNull(recorder.ellipse())
        assertTrue(ellipse.semiMajor > 0.0)
        // Dos puntos son siempre colineales -> semiMinor ~ 0 hasta añadir el tercero.
        assertEquals(0.0, ellipse.semiMinor, 0.001)
        recorder.addSample(1.0, 4.0, 1040L, 30.0, 30.0, 30.0)
        val wide = assertNotNull(recorder.ellipse())
        assertTrue(wide.semiMinor > 0.0)
    }

    @Test
    fun exportedArraysRoundTripPoints() {
        val recorder = SessionRecorder()
        recordStraightLine(recorder, fromX = 0.0, toX = 50.0, n = 6)
        val xs = recorder.xs()
        val ys = recorder.ys()
        val ts = recorder.ts()
        assertEquals(6, xs.size)
        assertEquals(6, ys.size)
        assertEquals(6, ts.size)
        assertEquals(0.0, xs[0])
        assertEquals(50.0, xs[5])
        assertEquals(1000L, ts[0])
        assertEquals(1100L, ts[5])
    }

    @Test
    fun loadStatsReflectAddedSensors() {
        val recorder = SessionRecorder()
        recorder.addSample(0.0, 0.0, 1000L, 10.0, 20.0, 30.0)
        recorder.addSample(0.0, 0.0, 1020L, 20.0, 30.0, 40.0)
        val m = recorder.compute(durationSeconds = 1.0)
        assertEquals(15.0, m.s1Avg, 0.001)
        assertEquals(40.0, m.s3Max, 0.001)
        assertEquals(10.0, m.s1Min, 0.001)
        assertEquals(75.0, m.totalAvgLoad, 0.001)
    }

    @Test
    fun meanFrequencyNaNWhenTooFewPoints() {
        val recorder = SessionRecorder()
        recorder.addSample(0.0, 0.0, 1000L, 30.0, 30.0, 30.0)
        recorder.addSample(1.0, 0.0, 1020L, 30.0, 30.0, 30.0)
        val m = recorder.compute(durationSeconds = 1.0)
        assertTrue(m.meanFreqXHz.isNaN())
    }

    @Test
    fun loadStatsIgnoresGeometryCollinearity() {
        // Tres sensores con pesos distintos sobre la misma posición geométrica.
        val recorder = SessionRecorder()
        for (i in 0 until 10) {
            recorder.addSample(0.0, 0.0, 1000L + i * 10L, 30.0 + i, 40.0 - i, 50.0)
        }
        val m = recorder.compute(durationSeconds = 1.0)
        assertEquals(34.5, m.s1Avg, 0.001)
        assertEquals(35.5, m.s2Avg, 0.001)
        assertEquals(50.0, m.s3Avg, 0.001)
        // Path de un solo punto -> 0
        assertEquals(0.0, m.swayPathLengthMm, 0.001)
    }

    @Test
    fun resetClearsStateFully() {
        val recorder = SessionRecorder()
        recordStraightLine(recorder, fromX = 0.0, toX = 10.0, n = 10)
        assertTrue(recorder.count() > 0)
        recorder.reset()
        assertEquals(0, recorder.count())
        assertTrue(recorder.xs().isEmpty())
        val m = recorder.compute(durationSeconds = 1.0)
        assertEquals(0, m.pointCount)
        assertNull(recorder.ellipse())
    }

    @Test
    fun ellipseParamsMatchManualComputation() {
        val recorder = SessionRecorder()
        // Puntos perfectamente colineales en diagonal.
        for (i in 0 until 4) {
            val k = i.toDouble()
            recorder.addSample(k, k, 1000L + i * 10L, 30.0, 30.0, 30.0)
        }
        val ellipse = assertNotNull(recorder.ellipse())
        // lambda1 > 0, lambda2 ~ 0 -> semiMinor ~ 0
        assertEquals(0.0, ellipse.semiMinor, 0.001)
        // Datos colineales en diagonal: varianza por eje 1.25 -> lambda1 = 2.5
        val variance = ((0.0 + 1.0 + 4.0 + 9.0) / 4.0 - 1.5 * 1.5)
        val expectedMajor = sqrt(5.991 * 2.0 * variance)
        assertEquals(expectedMajor, ellipse.semiMajor, 0.001)
    }
}
