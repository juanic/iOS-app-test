package com.stabilar.core.metrics

import kotlin.math.atan2
import kotlin.math.sqrt

import com.stabilar.core.parser.CopPoint
data class CopEllipse(
    val centerX: Double,
    val centerY: Double,
    val semiMajor: Double,
    val semiMinor: Double,
    val angleRad: Double
)

/**
 * Parámetros de la elipse de confianza al 95% sobre la distribución de puntos COP
 * (matriz de covarianza, ejes principales). Solo se usa para dibujar el overlay en
 * el informe; no altera las métricas de [StabilometryMetrics].
 */
object CopEllipseCalculator {

    private const val CHI2_95_2DOF = 5.991

    fun compute(points: List<CopPoint>): CopEllipse? {
        if (points.size < 2) return null
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

        val vx = covXY
        val vy = lambda1 - covXX
        val angle = atan2(vy, vx)

        return CopEllipse(
            centerX = mx,
            centerY = my,
            semiMajor = sqrt(lambda1 * CHI2_95_2DOF),
            semiMinor = sqrt(lambda2 * CHI2_95_2DOF),
            angleRad = angle
        )
    }
}
