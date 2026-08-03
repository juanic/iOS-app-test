package com.stabilar.core.games

import kotlin.math.pow
import kotlin.math.round
import kotlin.time.TimeSource

/**
 * Formatea [value] con [decimals] decimales como texto, de forma
 * determinística en todas las plataformas (JVM, iOS, JS). Sustituye al
 * `String.format("%.Nf")` de la JVM, que no existe en Kotlin common.
 */
internal fun formatNumber(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals).toLong()
    val scaled = round(kotlin.math.abs(value) * factor).toLong()
    val intPart = scaled / factor
    val sign = if (value < 0) "-" else ""
    val fracPart = (scaled % factor).toString().padStart(decimals, '0')
    return if (decimals > 0) "$sign$intPart.$fracPart" else "$sign$intPart"
}

/**
 * Semilla de tiempo para [kotlin.random.Random], disponible en Kotlin common.
 * Sustituye al `System.nanoTime()` de la JVM.
 */
internal fun timeSeed(): Long = TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
