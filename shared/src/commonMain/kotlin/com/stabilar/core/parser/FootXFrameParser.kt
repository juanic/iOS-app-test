package com.stabilar.core.parser

object FootXGeometry {
    const val SENSOR1_X = -200.0
    const val SENSOR1_Y = -115.5
    const val SENSOR2_X = 0.0
    const val SENSOR2_Y = 230.9
    const val SENSOR3_X = 200.0
    const val SENSOR3_Y = -115.5
}

data class CopPoint(
    val x: Double,
    val y: Double,
    val timestamp: Long
)

data class SensorSample(
    val s1: Double,
    val s2: Double,
    val s3: Double,
    val timestamp: Long
)

data class CalibrationData(
    val eigen1: Double,
    val eigen2: Double,
    val eigen3: Double,
    val eigen4: Double,
    val m: Double,
    val b: Double
) {
    val isValid: Boolean
        get() {
            val values = listOf(eigen1, eigen2, eigen3, eigen4, m, b)
            if (values.any { it.isNaN() || it.isInfinite() }) return false
            val eigenHasCorrection =
                eigen1 != 0.0 || eigen2 != 0.0 || eigen3 != 0.0 || eigen4 != 0.0
            return eigenHasCorrection
        }
}

sealed interface ParseResult {
    data class Sensors(
        val s1: Double,
        val s2: Double,
        val s3: Double,
        val counter: Int
    ) : ParseResult

    data class Battery(
        val soh: Double,
        val soc: Double,
        val voltageMv: Double
    ) : ParseResult

    data class Calibration(
        val first: Float,
        val second: Float
    ) : ParseResult

    data object CorruptFrame : ParseResult
    data object NotAFrame : ParseResult
}

object FootXFrameParser {

    private const val FRAME_LENGTH = 16
    private const val HEADER_0 = 0xEF
    private const val HEADER_1 = 0xEF
    private const val FOOTER_0 = 0xFE
    private const val FOOTER_1 = 0xFE
    private const val TYPE_SENSORS = 0xAD
    private const val TYPE_BATTERY = 0xED
    private const val TYPE_CALIBRATION = 0xBD

    private const val VREF = 2.5
    private const val GAIN = 32.0
    private const val SENSOR_SENSITIVITY = 0.000992
    private const val FORCE_SCALE = 1.27

    fun parseFrame(data: ByteArray): ParseResult {
        if (data.size != FRAME_LENGTH) return ParseResult.NotAFrame
        if ((data[0].toInt() and 0xFF) != HEADER_0 ||
            (data[1].toInt() and 0xFF) != HEADER_1 ||
            (data[14].toInt() and 0xFF) != FOOTER_0 ||
            (data[15].toInt() and 0xFF) != FOOTER_1
        ) {
            return ParseResult.NotAFrame
        }

        var checksum = 0
        for (i in 2..12) {
            checksum = checksum xor (data[i].toInt() and 0xFF)
        }
        if (checksum != (data[13].toInt() and 0xFF)) return ParseResult.CorruptFrame

        val counter = data[12].toInt() and 0xFF
        return when (data[2].toInt() and 0xFF) {
            TYPE_SENSORS -> ParseResult.Sensors(
                s1 = readUInt24(data, 3).toDouble(),
                s2 = readUInt24(data, 6).toDouble(),
                s3 = readUInt24(data, 9).toDouble(),
                counter = counter
            )

            TYPE_BATTERY -> ParseResult.Battery(
                soh = readUInt24(data, 3).toDouble(),
                soc = readUInt24(data, 6).toDouble(),
                voltageMv = readUInt24(data, 9).toDouble()
            )

            TYPE_CALIBRATION -> {
                ParseResult.Calibration(readFloatBE(data, 3), readFloatBE(data, 8))
            }

            else -> ParseResult.CorruptFrame
        }
    }

    fun computeCop(s1: Double, s2: Double, s3: Double): Pair<Double, Double> {
        val total = s1 + s2 + s3
        if (total <= 0.1) return 0.0 to 0.0
        val x = (s1 * FootXGeometry.SENSOR1_X + s2 * FootXGeometry.SENSOR2_X + s3 * FootXGeometry.SENSOR3_X) / total
        val y = (s1 * FootXGeometry.SENSOR1_Y + s2 * FootXGeometry.SENSOR2_Y + s3 * FootXGeometry.SENSOR3_Y) / total
        return x to y
    }

    fun toCopPoint(s1: Double, s2: Double, s3: Double, timestamp: Long): CopPoint {
        val (x, y) = computeCop(s1, s2, s3)
        return CopPoint(x, y, timestamp)
    }

    fun toCalibratedCopPoint(
        s1: Double,
        s2: Double,
        s3: Double,
        calibration: CalibrationData?,
        timestamp: Long
    ): CopPoint {
        val (x, y) = computeCop(s1, s2, s3)
        val cal = calibration?.takeIf { it.isValid }
        val cx = if (cal != null) x * cal.eigen1 + y * cal.eigen3 else x
        val cy = if (cal != null) x * cal.eigen2 + y * cal.eigen4 else y
        return CopPoint(cx, cy, timestamp)
    }

    fun rawToForceKg(raw: Double): Double {
        val voltage = raw * 2.0 * (VREF / GAIN) / 16777216.0
        val force = voltage / SENSOR_SENSITIVITY / FORCE_SCALE
        return force.coerceAtLeast(0.0)
    }

    fun weightKg(s1: Double, s2: Double, s3: Double, calibration: CalibrationData?): Double {
        val total = rawToForceKg(s1) + rawToForceKg(s2) + rawToForceKg(s3)
        val cal = calibration?.takeIf { it.isValid }
        return if (cal != null) total * cal.m + cal.b else total
    }

    private fun readUInt24(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)

    private fun readFloatBE(data: ByteArray, offset: Int): Float {
        val bits = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        return Float.fromBits(bits)
    }
}
