package com.stabilar.core

import com.stabilar.core.parser.CalibrationData
import com.stabilar.core.parser.CopPoint
import com.stabilar.core.parser.FootXFrameParser
import com.stabilar.core.parser.ParseResult

/**
 * Fachada para la interoperabilidad con Swift/ObjC: expone un único resultado
 * plano por frame y los cálculos de peso/COP ya resueltos, evitando navegar la
 * jerarquía sellada de [ParseResult] desde el lado iOS. Vive en iosMain, por lo
 * que no forma parte del core compartido con la app Android.
 */
object FootXBridge {

    fun parseFrame(data: ByteArray): LiveFrame = when (val r = FootXFrameParser.parseFrame(data)) {
        is ParseResult.Sensors -> LiveFrame(
            isSensor = true,
            s1 = r.s1,
            s2 = r.s2,
            s3 = r.s3
        )

        is ParseResult.Battery -> LiveFrame(
            isBattery = true,
            soh = r.soh,
            soc = r.soc,
            voltageMv = r.voltageMv
        )

        is ParseResult.Calibration -> LiveFrame(
            isCalibration = true,
            calFirst = r.first.toDouble(),
            calSecond = r.second.toDouble()
        )

        is ParseResult.CorruptFrame -> LiveFrame(isCorrupt = true)
        is ParseResult.NotAFrame -> LiveFrame()
    }

    fun rawToForceKg(raw: Double): Double = FootXFrameParser.rawToForceKg(raw)

    fun weightKg(s1: Double, s2: Double, s3: Double, calibration: CalibrationData?): Double =
        FootXFrameParser.weightKg(s1, s2, s3, calibration)

    fun toCopPoint(
        s1: Double,
        s2: Double,
        s3: Double,
        calibration: CalibrationData?,
        timestamp: Long
    ): CopPoint = FootXFrameParser.toCalibratedCopPoint(s1, s2, s3, calibration, timestamp)
}

data class LiveFrame(
    val isSensor: Boolean = false,
    val s1: Double = 0.0,
    val s2: Double = 0.0,
    val s3: Double = 0.0,
    val isBattery: Boolean = false,
    val soh: Double = 0.0,
    val soc: Double = 0.0,
    val voltageMv: Double = 0.0,
    val isCalibration: Boolean = false,
    val calFirst: Double = 0.0,
    val calSecond: Double = 0.0,
    val isCorrupt: Boolean = false
)
