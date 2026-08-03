package com.stabilar.core.games

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

import com.stabilar.core.parser.CopPoint
/**
 * Sendero: hay que recorrer un camino de punta a punta, arrancando desde el
 * primer waypoint. El puntaje ya no depende de "estar cerca de cualquier parte
 * del camino" (eso permitía plantarse en un punto y sumar igual, o cruzar el
 * camino sin seguirlo); depende de cuánta distancia se recorrió de forma
 * continua desde el inicio: [frontierLength] solo avanza cuando el COP está
 * dentro del corredor y cerca del punto hasta donde ya se avanzó, no cuando
 * aparece lejos, adelante, en otro tramo del camino.
 *
 * La forma del camino (cantidad de columnas, amplitud, esquina de arranque y
 * sentido) se genera al azar en cada [start], para que no sea siempre el
 * mismo serpenteo.
 */
class PathFollowGame : GameEngine {

    private val rng = Random(timeSeed())

    private var s = GameUiState(type = GameType.PATH_FOLLOW, corridorRadiusMm = CORRIDOR_RADIUS.toFloat())
    private var totalTime = 0f
    private var corridorRadius = CORRIDOR_RADIUS
    private var maxDeviation = 0.0

    /** Distancia (mm) recorrida de forma continua a lo largo del sendero desde el inicio. */
    private var frontierLength = 0.0

    /** Cuánto puede avanzar el progreso por tick sin haber "caminado" ese tramo. */
    private var maxForwardStep = CORRIDOR_RADIUS * FORWARD_STEP_FACTOR

    /** Sendero de la partida actual (waypoints en mm), generado al azar en [start]. */
    var waypoints: List<Pair<Double, Double>> = FALLBACK_WAYPOINTS
        private set

    private var segmentLengths: List<Double> = emptyList()
    private var cumulativeLengths: List<Double> = emptyList()

    /** Longitud total del sendero actual, en mm. */
    var totalLength: Double = 0.0
        private set

    override val type: GameType get() = GameType.PATH_FOLLOW
    override val state: GameUiState get() = s

    override fun start(durationSeconds: Int, difficulty: Difficulty, cop: CopPoint?): GameUiState {
        reset()
        regeneratePath()
        corridorRadius = (CORRIDOR_RADIUS * difficulty.sizeScale).coerceAtLeast(CORRIDOR_RADIUS_MIN)
        maxForwardStep = corridorRadius * FORWARD_STEP_FACTOR
        s = s.copy(
            phase = GamePhase.Running,
            remainingSeconds = durationSeconds.toFloat(),
            cop = cop,
            corridorRadiusMm = corridorRadius.toFloat(),
            difficulty = difficulty,
            message = "Arrancá desde el punto de inicio (verde) y seguí el camino",
            targets = waypoints.mapIndexed { i, wp ->
                GameTarget(
                    id = i,
                    x = wp.first,
                    y = wp.second,
                    radiusMm = corridorRadius,
                    kind = TargetKind.PATH_NODE
                )
            },
            pathProgress = 0f
        )
        return s
    }

    override fun update(cop: CopPoint?, deltaSeconds: Float): GameUiState {
        if (s.phase !is GamePhase.Running) return s
        val dt = deltaSeconds.coerceAtLeast(0f)
        s = s.copy(
            remainingSeconds = (s.remainingSeconds - dt).coerceAtLeast(0f),
            cop = cop
        )

        totalTime += dt
        var nearPath = false
        var cutAhead = false
        var advanced = false
        val copPos = cop
        if (copPos != null) {
            for (i in 0 until waypoints.size - 1) {
                val (distance, arcLength) = distanceAndArcLength(copPos.x, copPos.y, i)
                if (distance > maxDeviation) maxDeviation = distance
                if (distance <= corridorRadius) {
                    nearPath = true
                    if (arcLength <= frontierLength + maxForwardStep) {
                        if (arcLength > frontierLength) {
                            frontierLength = arcLength
                            advanced = true
                        }
                    } else {
                        cutAhead = true
                    }
                }
            }
        }

        val progressFraction = (frontierLength / totalLength).coerceIn(0.0, 1.0)
        s = s.copy(
            score = (progressFraction * 100).roundToInt(),
            pathProgress = progressFraction.toFloat(),
            message = when {
                cop == null -> "Sin señal del COP"
                advanced -> "Siguiendo el sendero"
                nearPath && cutAhead -> "Te adelantaste, volvé al tramo marcado"
                nearPath -> "Quieto en el sendero, seguí avanzando"
                else -> "Fuera del sendero"
            }
        )

        // Si se recorrió todo el sendero antes de que se acabe el tiempo, se
        // termina el juego ahí mismo en vez de dejar seguir corriendo el reloj
        // sin nada más que hacer.
        if (progressFraction >= 1.0) return finish(FinishedReason.COMPLETED)
        if (s.remainingSeconds <= 0f) return finish(FinishedReason.TIME_UP)
        return s
    }

    override fun stop(): GameUiState = finish(FinishedReason.STOPPED)

    override fun reset(): GameUiState {
        totalTime = 0f
        maxDeviation = 0.0
        frontierLength = 0.0
        s = GameUiState(type = type, corridorRadiusMm = CORRIDOR_RADIUS.toFloat())
        return s
    }

    private fun finish(reason: FinishedReason): GameUiState {
        s = s.copy(
            phase = GamePhase.Finished(s.score, reason),
            message = when (reason) {
                FinishedReason.COMPLETED -> "¡Sendero completo!"
                FinishedReason.TIME_UP -> "¡Tiempo agotado!"
                FinishedReason.STOPPED -> "Juego detenido"
            },
            stats = buildStats()
        )
        return s
    }

    private fun buildStats(): List<GameStat> = listOf(
        GameStat("Sendero recorrido", formatNumber(frontierLength, 0), "mm"),
        GameStat("Sendero total", formatNumber(totalLength, 0), "mm"),
        GameStat("Máx. desvío", formatNumber(maxDeviation, 1), "mm")
    )

    /** Genera un nuevo sendero al azar y recalcula sus longitudes acumuladas. */
    private fun regeneratePath() {
        waypoints = generateWaypoints(rng)
        segmentLengths = waypoints.zipWithNext { a, b ->
            val dx = b.first - a.first
            val dy = b.second - a.second
            sqrt(dx * dx + dy * dy)
        }
        cumulativeLengths = segmentLengths.runningFold(0.0) { acc, l -> acc + l }
        totalLength = cumulativeLengths.last()
    }

    /**
     * Distancia al segmento [i] y la posición del punto más cercano de ese
     * segmento medida como longitud de arco acumulada sobre TODO el sendero
     * (no solo dentro del segmento). Esto es lo que permite detectar si el
     * punto más cercano pertenece a un tramo ya alcanzable o a uno "adelantado".
     */
    private fun distanceAndArcLength(px: Double, py: Double, i: Int): Pair<Double, Double> {
        val a = waypoints[i]
        val b = waypoints[i + 1]
        val dx = b.first - a.first
        val dy = b.second - a.second
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) {
            0.0
        } else {
            (((px - a.first) * dx + (py - a.second) * dy) / lengthSq).coerceIn(0.0, 1.0)
        }
        val cx = a.first + t * dx
        val cy = a.second + t * dy
        val ddx = px - cx
        val ddy = py - cy
        val distance = sqrt(ddx * ddx + ddy * ddy)
        val arcLength = cumulativeLengths[i] + t * segmentLengths[i]
        return distance to arcLength
    }

    /**
     * Genera un sendero en zigzag (serpenteo) con cantidad de columnas,
     * amplitud, esquina de arranque y sentido al azar, para que el camino no
     * sea siempre igual entre partidas. La cantidad de columnas y la
     * separación mínima entre ellas quedan acotadas para que el corredor de
     * columnas vecinas no se superponga ni en la dificultad más fácil.
     */
    private fun generateWaypoints(random: Random): List<Pair<Double, Double>> {
        val columnCount = random.nextInt(MIN_COLUMNS, MAX_COLUMNS + 1)
        val amplitude = random.nextDouble(MIN_AMPLITUDE, MAX_AMPLITUDE)
        val topY = amplitude
        val bottomY = -amplitude
        val xs = randomColumnPositions(columnCount, random)
        val orderedXs = if (random.nextBoolean()) xs else xs.reversed()
        val startAtBottom = random.nextBoolean()

        val points = mutableListOf<Pair<Double, Double>>()
        var atTop = !startAtBottom
        points.add(orderedXs[0] to if (atTop) topY else bottomY)
        for (i in orderedXs.indices) {
            atTop = !atTop
            val y = if (atTop) topY else bottomY
            points.add(orderedXs[i] to y)
            if (i != orderedXs.lastIndex) {
                points.add(orderedXs[i + 1] to y)
            }
        }
        return points
    }

    /** Posiciones de columna repartidas en [X_MIN, X_MAX] con una separación mínima segura. */
    private fun randomColumnPositions(count: Int, random: Random): List<Double> {
        val step = (X_MAX - X_MIN) / (count - 1)
        val maxJitter = ((step - MIN_COLUMN_GAP) / 2.0).coerceAtLeast(0.0)
        return List(count) { i ->
            val base = X_MIN + step * i
            val jitter = if (maxJitter > 0.0) random.nextDouble(-maxJitter, maxJitter) else 0.0
            (base + jitter).coerceIn(X_MIN, X_MAX)
        }
    }

    companion object {
        /** Forma usada antes de que se llame [start] por primera vez (nunca se recorre). */
        val FALLBACK_WAYPOINTS = listOf(
            Pair(-80.0, -40.0),
            Pair(-80.0, 40.0),
            Pair(-30.0, 40.0),
            Pair(-30.0, -40.0),
            Pair(30.0, -40.0),
            Pair(30.0, 40.0),
            Pair(80.0, 40.0),
            Pair(80.0, -40.0)
        )

        // Corredor angostado (antes 30.0 / piso 12.0): la franja ancha se
        // percibía como "zona de relleno" no válida aunque sumara puntos, así
        // que se redujo el radio real para que la tolerancia se sienta acorde
        // a lo que se ve pintado.
        const val CORRIDOR_RADIUS = 18.0
        const val CORRIDOR_RADIUS_MIN = 8.0

        /** Multiplicador del radio del corredor que define cuánto se puede avanzar por tick. */
        private const val FORWARD_STEP_FACTOR = 3.0

        // Rango horizontal usado para las columnas del serpenteo, y rango de
        // amplitud vertical (mismos límites que la forma fija original).
        private const val X_MIN = -80.0
        private const val X_MAX = 80.0
        private const val MIN_AMPLITUDE = 30.0
        private const val MAX_AMPLITUDE = 40.0

        // Cantidad de columnas del zigzag: acotada a 3-4 para que, incluso con
        // el corredor más ancho (dificultad más fácil), las columnas vecinas no
        // terminen con corredores superpuestos.
        private const val MIN_COLUMNS = 3
        private const val MAX_COLUMNS = 4
        private const val MIN_COLUMN_GAP = 44.0
    }
}

