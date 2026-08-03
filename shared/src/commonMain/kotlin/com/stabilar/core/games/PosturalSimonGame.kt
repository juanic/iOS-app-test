package com.stabilar.core.games

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

import com.stabilar.core.parser.CopPoint
class PosturalSimonGame : GameEngine {

    private var s = GameUiState(type = GameType.POSTURAL_SIMON)
    private val rng = Random(timeSeed())
    private var activeZoneId = ZONES[0].id
    private var holdProgress = 0f
    private var zoneTimer = 0f

    private var zoneRadius = ZONE_RADIUS
    private var holdSeconds = HOLD_SECONDS
    private var zoneTimeout = ZONE_TIMEOUT

    private var zonesCompleted = 0
    private var maxCombo = 0
    private var timeouts = 0
    private var zoneTimeSum = 0f
    private var zoneTimeCount = 0

    private data class ZoneSpec(val id: Int, val x: Double, val y: Double, val name: String)

    override val type: GameType get() = GameType.POSTURAL_SIMON
    override val state: GameUiState get() = s

    override fun start(durationSeconds: Int, difficulty: Difficulty, cop: CopPoint?): GameUiState {
        reset()
        zoneRadius = (ZONE_RADIUS * difficulty.sizeScale).coerceAtLeast(22.0)
        holdSeconds = (HOLD_SECONDS * difficulty.timeScale).coerceAtLeast(0.25f)
        zoneTimeout = (ZONE_TIMEOUT * difficulty.timeScale).coerceAtLeast(1.5f)
        activeZoneId = ZONES[rng.nextInt(ZONES.size)].id
        s = s.copy(
            phase = GamePhase.Running,
            remainingSeconds = durationSeconds.toFloat(),
            cop = cop,
            difficulty = difficulty,
            targets = buildTargets(),
            message = "Mové el COP a ${currentZone().name}"
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
        if (s.remainingSeconds <= 0f) return finish(FinishedReason.TIME_UP)

        zoneTimer += dt
        val inside = cop != null && distance(cop.x, cop.y, currentZone()) <= zoneRadius
        if (inside) {
            holdProgress += dt
            if (holdProgress >= holdSeconds) {
                zonesCompleted++
                zoneTimeSum += zoneTimer
                zoneTimeCount++
                val combo = s.combo + 1
                maxCombo = max(maxCombo, combo)
                s = s.copy(score = s.score + POINTS_PER_ZONE, combo = combo)
                advance("¡Bien! Ahora ${currentZone().name}")
            }
        } else {
            holdProgress = 0f
        }

        if (zoneTimer >= zoneTimeout) {
            timeouts++
            s = s.copy(combo = 0)
            advance("Muy lento. Ahora ${currentZone().name}")
        }

        s = s.copy(targets = buildTargets())
        return s
    }

    override fun stop(): GameUiState = finish(FinishedReason.STOPPED)

    override fun reset(): GameUiState {
        activeZoneId = ZONES[0].id
        holdProgress = 0f
        zoneTimer = 0f
        zonesCompleted = 0
        maxCombo = 0
        timeouts = 0
        zoneTimeSum = 0f
        zoneTimeCount = 0
        s = GameUiState(type = type)
        return s
    }

    private fun currentZone(): ZoneSpec = ZONES[activeZoneId]

    private fun advance(message: String) {
        val candidates = ZONES.filter { it.id != activeZoneId }
        activeZoneId = candidates[rng.nextInt(candidates.size)].id
        holdProgress = 0f
        zoneTimer = 0f
        s = s.copy(targets = buildTargets(), message = message)
    }

    private fun buildTargets(): List<GameTarget> = ZONES.map { z ->
        GameTarget(
            id = z.id,
            x = z.x,
            y = z.y,
            radiusMm = zoneRadius,
            kind = TargetKind.ZONE,
            active = z.id == activeZoneId,
            progress = if (z.id == activeZoneId) (holdProgress / holdSeconds).coerceIn(0f, 1f) else 0f
        )
    }

    private fun distance(x: Double, y: Double, zone: ZoneSpec): Double {
        val dx = x - zone.x
        val dy = y - zone.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun finish(reason: FinishedReason): GameUiState {
        s = s.copy(
            phase = GamePhase.Finished(s.score, reason),
            message = if (reason == FinishedReason.TIME_UP) "¡Tiempo agotado!" else "Juego detenido",
            stats = buildStats()
        )
        return s
    }

    private fun buildStats(): List<GameStat> {
        val avgZone = if (zoneTimeCount > 0) zoneTimeSum / zoneTimeCount else 0f
        return listOf(
            GameStat("Zonas completadas", zonesCompleted.toString(), "uds"),
            GameStat("Mejor racha", maxCombo.toString(), "x"),
            GameStat("Tiempo medio por zona", formatNumber(avgZone.toDouble(), 2), "s"),
            GameStat("Cambios por tiempo", timeouts.toString(), "uds")
        )
    }

    companion object {
        private val ZONES = listOf(
            ZoneSpec(0, 0.0, 10.0, "Centro"),
            ZoneSpec(1, 0.0, 55.0, "Adelante"),
            ZoneSpec(2, 0.0, -45.0, "Atrás"),
            ZoneSpec(3, -65.0, 10.0, "Izquierda"),
            ZoneSpec(4, 65.0, 10.0, "Derecha")
        )

        const val ZONE_RADIUS = 50.0
        const val HOLD_SECONDS = 0.5f
        const val ZONE_TIMEOUT = 4f
        const val POINTS_PER_ZONE = 10
    }
}
