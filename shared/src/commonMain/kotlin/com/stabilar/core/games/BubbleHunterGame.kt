package com.stabilar.core.games

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

import com.stabilar.core.parser.CopPoint
class BubbleHunterGame : GameEngine {

    private var s = GameUiState(type = GameType.BUBBLE_HUNTER)
    private val rng = Random(timeSeed())
    private val bubbles = mutableListOf<Bubble>()
    private var nextId = 0
    private var spawnTimer = 0f
    private var messageTimer = 0f
    private var elapsed = 0f

    private var baseRadius = BASE_RADIUS
    private var spawnInterval = SPAWN_INTERVAL
    private var bubbleLifetime = BUBBLE_LIFETIME
    private var maxBubbles = MAX_BUBBLES
    private var initialBubbles = INITIAL_BUBBLES
    private var spawnMargin = SPAWN_MARGIN_MM

    private var poppedCount = 0
    private var spawnedCount = 0
    private var maxCombo = 0
    private var reactionSum = 0.0
    private var reactionCount = 0

    private class Bubble(
        val id: Int,
        var x: Double,
        var y: Double,
        var life: Float,
        val maxLife: Float,
        val baseRadius: Double,
        val spawnedAt: Float
    ) {
        val radius: Double
            get() = baseRadius * (life / maxLife).coerceIn(0.15f, 1f)
    }

    override val type: GameType get() = GameType.BUBBLE_HUNTER
    override val state: GameUiState get() = s

    override fun start(durationSeconds: Int, difficulty: Difficulty, cop: CopPoint?): GameUiState {
        reset()
        baseRadius = (BASE_RADIUS * difficulty.sizeScale).coerceAtLeast(14.0)
        spawnInterval = (SPAWN_INTERVAL * difficulty.paceScale).coerceAtLeast(0.35f)
        bubbleLifetime = (BUBBLE_LIFETIME * difficulty.timeScale).coerceAtLeast(1.5f)
        maxBubbles = when (difficulty) {
            Difficulty.N1 -> 3
            Difficulty.N2 -> MAX_BUBBLES
            Difficulty.N3 -> 5
            Difficulty.N4 -> 6
            Difficulty.N5 -> 8
        }
        initialBubbles = if (difficulty == Difficulty.N1) 2 else INITIAL_BUBBLES
        spawnMargin = when (difficulty) {
            Difficulty.N1, Difficulty.N2 -> SPAWN_MARGIN_MM
            Difficulty.N3 -> 24.0
            Difficulty.N4 -> 20.0
            Difficulty.N5 -> 16.0
        }
        s = s.copy(
            phase = GamePhase.Running,
            remainingSeconds = durationSeconds.toFloat(),
            cop = cop,
            difficulty = difficulty,
            message = "¡Explotá las burbujas!"
        )
        repeat(initialBubbles) { spawnBubble() }
        s = s.copy(targets = buildTargets())
        return s
    }

    override fun update(cop: CopPoint?, deltaSeconds: Float): GameUiState {
        if (s.phase !is GamePhase.Running) return s
        val dt = deltaSeconds.coerceAtLeast(0f)
        elapsed += dt
        s = s.copy(
            remainingSeconds = (s.remainingSeconds - dt).coerceAtLeast(0f),
            cop = cop
        )

        if (messageTimer > 0f) {
            messageTimer -= dt
            if (messageTimer <= 0f) s = s.copy(message = null)
        }

        if (s.remainingSeconds <= 0f) return finish(FinishedReason.TIME_UP)

        spawnTimer -= dt
        if (spawnTimer <= 0f && bubbles.size < maxBubbles) {
            spawnBubble()
            spawnTimer = spawnInterval
        }

        val iterator = bubbles.iterator()
        val popped = mutableListOf<PoppedBubble>()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.life -= dt
            if (bubble.life <= 0f) {
                iterator.remove()
                s = s.copy(combo = 0)
                continue
            }
            val copPos = cop ?: continue
            val dx = copPos.x - bubble.x
            val dy = copPos.y - bubble.y
            if (sqrt(dx * dx + dy * dy) <= bubble.radius) {
                iterator.remove()
                val combo = s.combo + 1
                var pointsAwarded = POINTS_PER_BUBBLE
                var score = s.score + POINTS_PER_BUBBLE
                maxCombo = max(maxCombo, combo)
                poppedCount++
                reactionSum += elapsed - bubble.spawnedAt
                reactionCount++
                if (combo % COMBO_SIZE == 0) {
                    score += COMBO_BONUS
                    pointsAwarded += COMBO_BONUS
                    s = s.copy(score = score, combo = combo, message = "¡Combo x3! +$COMBO_BONUS")
                } else {
                    s = s.copy(score = score, combo = combo, message = "+$POINTS_PER_BUBBLE")
                }
                messageTimer = 1.2f
                popped += PoppedBubble(bubble.id, bubble.x, bubble.y, bubble.radius, pointsAwarded)
            }
        }

        s = s.copy(targets = buildTargets(), poppedThisTick = popped)
        return s
    }

    override fun stop(): GameUiState = finish(FinishedReason.STOPPED)

    override fun reset(): GameUiState {
        bubbles.clear()
        nextId = 0
        spawnTimer = 0f
        messageTimer = 0f
        elapsed = 0f
        poppedCount = 0
        spawnedCount = 0
        maxCombo = 0
        reactionSum = 0.0
        reactionCount = 0
        s = GameUiState(type = type)
        return s
    }

    private fun spawnBubble() {
        val x = rng.nextDouble(GameCoords.MIN_X + spawnMargin, GameCoords.MAX_X - spawnMargin)
        val y = rng.nextDouble(GameCoords.MIN_Y + spawnMargin, GameCoords.MAX_Y - spawnMargin)
        bubbles += Bubble(nextId++, x, y, bubbleLifetime, bubbleLifetime, baseRadius, elapsed)
        spawnedCount++
    }

    private fun buildTargets(): List<GameTarget> = bubbles.map { b ->
        GameTarget(
            id = b.id,
            x = b.x,
            y = b.y,
            radiusMm = b.radius,
            kind = TargetKind.BUBBLE,
            progress = (b.life / b.maxLife).coerceIn(0f, 1f)
        )
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
        val accuracy = if (spawnedCount > 0) poppedCount * 100 / spawnedCount else 0
        val avgReaction = if (reactionCount > 0) reactionSum / reactionCount else 0.0
        return listOf(
            GameStat("Burbujas explotadas", poppedCount.toString(), "uds"),
            GameStat("Precisión", "$accuracy", "%"),
            GameStat("Mejor combo", maxCombo.toString(), "x"),
            GameStat("Reacción media", formatNumber(avgReaction, 2), "s")
        )
    }

    companion object {
        const val INITIAL_BUBBLES = 3
        const val MAX_BUBBLES = 4
        const val SPAWN_INTERVAL = 1.2f
        const val BUBBLE_LIFETIME = 7f
        const val BASE_RADIUS = 32.0
        const val SPAWN_MARGIN_MM = 28.0
        const val POINTS_PER_BUBBLE = 10
        const val COMBO_SIZE = 3
        const val COMBO_BONUS = 5
    }
}
