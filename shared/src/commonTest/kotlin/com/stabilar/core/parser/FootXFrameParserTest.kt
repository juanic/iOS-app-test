package com.stabilar.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FootXFrameParserTest {

    private fun buildFrame(
        type: Int,
        payload: ByteArray,
        counter: Int
    ): ByteArray {
        require(payload.size == 9)
        val frame = ByteArray(16)
        frame[0] = 0xEF.toByte()
        frame[1] = 0xEF.toByte()
        frame[2] = type.toByte()
        payload.copyInto(frame, 3)
        frame[12] = counter.toByte()
        var checksum = 0
        for (i in 2..12) checksum = checksum xor (frame[i].toInt() and 0xFF)
        frame[13] = checksum.toByte()
        frame[14] = 0xFE.toByte()
        frame[15] = 0xFE.toByte()
        return frame
    }

    private fun uint24(value: Int): ByteArray = byteArrayOf(
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun putFloatBE(frame: ByteArray, offset: Int, value: Float) {
        val bits = value.toRawBits()
        frame[offset] = ((bits shr 24) and 0xFF).toByte()
        frame[offset + 1] = ((bits shr 16) and 0xFF).toByte()
        frame[offset + 2] = ((bits shr 8) and 0xFF).toByte()
        frame[offset + 3] = (bits and 0xFF).toByte()
    }

    @Test
    fun parsesSensorFrame() {
        val frame = buildFrame(
            type = 0xAD,
            payload = uint24(100) + uint24(200) + uint24(300),
            counter = 5
        )
        val result = FootXFrameParser.parseFrame(frame)
        assertTrue(result is ParseResult.Sensors)
        result as ParseResult.Sensors
        assertEquals(100.0, result.s1, 0.001)
        assertEquals(200.0, result.s2, 0.001)
        assertEquals(300.0, result.s3, 0.001)
        assertEquals(5, result.counter)
    }

    @Test
    fun rejectsBadChecksum() {
        val frame = buildFrame(0xAD, uint24(1) + uint24(2) + uint24(3), 0)
        frame[13] = (frame[13].toInt() xor 0xFF).toByte()
        assertTrue(FootXFrameParser.parseFrame(frame) is ParseResult.CorruptFrame)
    }

    @Test
    fun rejectsWrongLengthOrHeader() {
        assertTrue(FootXFrameParser.parseFrame(ByteArray(15)) is ParseResult.NotAFrame)
        val frame = buildFrame(0xAD, uint24(1) + uint24(2) + uint24(3), 0)
        frame[0] = 0x00
        assertTrue(FootXFrameParser.parseFrame(frame) is ParseResult.NotAFrame)
    }

    @Test
    fun computesCopFromGeometry() {
        val (x, y) = FootXFrameParser.computeCop(1.0, 1.0, 1.0)
        assertEquals(0.0, x, 0.001)
        assertEquals(-0.0333, y, 0.001)

        val (x2, y2) = FootXFrameParser.computeCop(1.0, 0.0, 0.0)
        assertEquals(-200.0, x2, 0.001)
        assertEquals(-115.5, y2, 0.001)
    }

    @Test
    fun copIsZeroWhenTotalBelowThreshold() {
        val (x, y) = FootXFrameParser.computeCop(0.01, 0.0, 0.0)
        assertEquals(0.0, x, 0.001)
        assertEquals(0.0, y, 0.001)
    }

    @Test
    fun parsesCalibrationFrameBigEndian() {
        val first = 1.5f
        val second = -2.25f
        val frame = ByteArray(16)
        frame[0] = 0xEF.toByte()
        frame[1] = 0xEF.toByte()
        frame[2] = 0xBD.toByte()
        putFloatBE(frame, 3, first)
        frame[7] = 0
        putFloatBE(frame, 8, second)
        frame[12] = 0
        var checksum = 0
        for (i in 2..12) checksum = checksum xor (frame[i].toInt() and 0xFF)
        frame[13] = checksum.toByte()
        frame[14] = 0xFE.toByte()
        frame[15] = 0xFE.toByte()

        val result = FootXFrameParser.parseFrame(frame)
        assertTrue(result is ParseResult.Calibration)
        result as ParseResult.Calibration
        assertEquals(first, result.first, 0.001f)
        assertEquals(second, result.second, 0.001f)
    }

    @Test
    fun calibrationInvalidWhenNaN() {
        val cal = CalibrationData(
            eigen1 = Double.NaN,
            eigen2 = 1.0,
            eigen3 = 0.0,
            eigen4 = 1.0,
            m = 1.0,
            b = 0.0
        )
        assertTrue(!cal.isValid)
    }

    @Test
    fun calibrationValidWhenCorrectionPresent() {
        val cal = CalibrationData(
            eigen1 = 1.0,
            eigen2 = 0.0,
            eigen3 = 0.0,
            eigen4 = 1.0,
            m = 1.05,
            b = 0.2
        )
        assertTrue(cal.isValid)
    }

    @Test
    fun rawToForceIsPositiveAndScaled() {
        val f = FootXFrameParser.rawToForceKg(8388607.0)
        assertTrue(f > 0.0)
        assertTrue(f < 200.0)
    }

    @Test
    fun calibratedCopAppliesEigenMatrix() {
        val cal = CalibrationData(
            eigen1 = 2.0,
            eigen2 = 0.0,
            eigen3 = 0.0,
            eigen4 = 2.0,
            m = 1.0,
            b = 0.0
        )
        val point = FootXFrameParser.toCalibratedCopPoint(1.0, 1.0, 1.0, cal, 0L)
        assertEquals(0.0, point.x, 0.001)
        assertEquals(-0.0666, point.y, 0.001)
    }

    @Test
    fun uncalibratedCopPointIsRaw() {
        val point = FootXFrameParser.toCalibratedCopPoint(1.0, 1.0, 1.0, null, 0L)
        assertEquals(0.0, point.x, 0.001)
        assertEquals(-0.0333, point.y, 0.001)
    }

    @Test
    fun weightUsesCalibrationSlope() {
        val cal = CalibrationData(
            eigen1 = 1.0,
            eigen2 = 0.0,
            eigen3 = 0.0,
            eigen4 = 1.0,
            m = 2.0,
            b = 1.0
        )
        val raw = 1000000.0
        val expected = (FootXFrameParser.rawToForceKg(raw) * 3.0) * 2.0 + 1.0
        assertEquals(expected, FootXFrameParser.weightKg(raw, raw, raw, cal), 0.001)
    }
}
