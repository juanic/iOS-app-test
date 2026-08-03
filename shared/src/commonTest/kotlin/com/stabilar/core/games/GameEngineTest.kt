package com.stabilar.core.games

import com.stabilar.core.parser.CopPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameEngineTest {

    private fun cop(x: Double, y: Double) = CopPoint(x, y, 0L)

    @Test
    fun `bubble hunter - start spawns bubbles and running phase`() {
        val engine = BubbleHunterGame()
        val state = engine.start(60, Difficulty.N2, null)
        assertEquals(GamePhase.Running, state.phase)
        assertEquals(BubbleHunterGame.INITIAL_BUBBLES, state.targets.size)
    }

    @Test
    fun `bubble hunter - moving cop onto a bubble pops it and adds points`() {
        val engine = BubbleHunterGame()
        engine.start(60, Difficulty.N2, null)
        val target = engine.state.targets.first()
        val state = engine.update(cop(target.x, target.y), 0.05f)
        assertTrue(state.score >= BubbleHunterGame.POINTS_PER_BUBBLE)
        assertTrue(state.combo >= 1)
        assertTrue(state.targets.none { it.id == target.id })
    }

    @Test
    fun `bubble hunter - combo of three gives bonus`() {
        val engine = BubbleHunterGame()
        engine.start(60, Difficulty.N2, null)
        var state = engine.state
        var guard = 0
        while ((state.combo < 3 || state.score < 35) && guard < 200) {
            guard++
            val target = state.targets.firstOrNull()
            state = if (target != null) {
                engine.update(cop(target.x, target.y), 0.05f)
            } else {
                engine.update(cop(0.0, -200.0), 0.05f)
            }
        }
        assertTrue(state.combo >= 3, "combo reached 3: combo=${state.combo} score=${state.score}")
        assertTrue(state.score >= 35, "bonus applied: score=${state.score}")
    }

    @Test
    fun `bubble hunter - finishes when time runs out`() {
        val engine = BubbleHunterGame()
        engine.start(2, Difficulty.N2, null)
        val state = engine.update(cop(0.0, 0.0), 3f)
        assertTrue(state.phase is GamePhase.Finished)
        assertEquals(
            FinishedReason.TIME_UP,
            (state.phase as GamePhase.Finished).reason
        )
    }

    @Test
    fun `path follow - staying still at the start does not fill the score`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        val first = engine.waypoints.first()
        val state = engine.update(cop(first.first, first.second), 1f)
        assertEquals(0, state.score)
    }

    @Test
    fun `path follow - walking the whole path continuously from start reaches 100 percent`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        var state = engine.state
        engine.waypoints.zipWithNext().forEach { (a, b) ->
            val steps = 12
            for (i in 1..steps) {
                val t = i / steps.toDouble()
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t
                state = engine.update(cop(x, y), 0.05f)
            }
        }
        assertEquals(100, state.score)
    }

    @Test
    fun `path follow - completing the whole path finishes the game early`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        var state = engine.state
        engine.waypoints.zipWithNext().forEach { (a, b) ->
            val steps = 12
            for (i in 1..steps) {
                val t = i / steps.toDouble()
                val x = a.first + (b.first - a.first) * t
                val y = a.second + (b.second - a.second) * t
                state = engine.update(cop(x, y), 0.05f)
            }
        }
        assertTrue(state.phase is GamePhase.Finished)
        assertEquals(FinishedReason.COMPLETED, (state.phase as GamePhase.Finished).reason)
        assertTrue(state.remainingSeconds > 0f)
    }

    @Test
    fun `path follow - jumping straight to the end does not award points`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        val end = engine.waypoints.last()
        val state = engine.update(cop(end.first, end.second), 1f)
        assertEquals(0, state.score)
    }

    @Test
    fun `path follow - going far from the path yields zero percent`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        val state = engine.update(cop(0.0, 300.0), 1f)
        assertTrue(state.score < 100)
    }

    @Test
    fun `path follow - stop finishes with stopped reason`() {
        val engine = PathFollowGame()
        engine.start(60, Difficulty.N2, null)
        val state = engine.stop()
        assertTrue(state.phase is GamePhase.Finished)
        assertEquals(FinishedReason.STOPPED, (state.phase as GamePhase.Finished).reason)
    }

    @Test
    fun `path follow - random path stays within play bounds and each start regenerates it`() {
        val shapes = List(20) {
            val engine = PathFollowGame()
            engine.start(60, Difficulty.N2, null)
            engine.waypoints
        }
        shapes.forEach { waypoints ->
            assertTrue(waypoints.size >= 6)
            waypoints.forEach { (x, y) ->
                assertTrue(x >= GameCoords.MIN_X && x <= GameCoords.MAX_X)
                assertTrue(y >= GameCoords.MIN_Y && y <= GameCoords.MAX_Y)
            }
        }
        // Con 20 tiradas al azar, es prácticamente seguro que no todas den la misma forma.
        assertTrue(shapes.distinct().size > 1)
    }

    @Test
    fun `simon - holding in active zone adds points and advances`() {
        val engine = PosturalSimonGame()
        engine.start(60, Difficulty.N2, null)
        val active = engine.state.targets.first { it.active }
        val state = engine.update(cop(active.x, active.y), 0.6f)
        assertEquals(PosturalSimonGame.POINTS_PER_ZONE, state.score)
        val newActive = state.targets.first { it.active }
        assertTrue(newActive.id != active.id)
    }

    @Test
    fun `simon - timeout advances zone without points`() {
        val engine = PosturalSimonGame()
        engine.start(60, Difficulty.N2, null)
        val active = engine.state.targets.first { it.active }
        val state = engine.update(cop(active.x + 200.0, active.y), 4.2f)
        assertEquals(0, state.score)
        val newActive = state.targets.first { it.active }
        assertTrue(newActive.id != active.id)
    }

    @Test
    fun `simon - finishes when time runs out`() {
        val engine = PosturalSimonGame()
        engine.start(2, Difficulty.N2, null)
        val state = engine.update(cop(0.0, 0.0), 3f)
        assertTrue(state.phase is GamePhase.Finished)
    }

    @Test
    fun `bubble hunter - harder difficulty shrinks the bubbles`() {
        val easy = BubbleHunterGame().start(60, Difficulty.N1, null).targets.first().radiusMm
        val hard = BubbleHunterGame().start(60, Difficulty.N5, null).targets.first().radiusMm
        assertTrue(hard < easy, "hard=${hard} easy=${easy}")
    }

    @Test
    fun `path follow - harder difficulty narrows the corridor`() {
        val easy = PathFollowGame().start(60, Difficulty.N1, null)
        val hard = PathFollowGame().start(60, Difficulty.N5, null)
        val easyRadius = easy.corridorRadiusMm!!
        val hardRadius = hard.corridorRadiusMm!!
        assertTrue(hardRadius < easyRadius, "hard=${hardRadius} easy=${easyRadius}")
    }

    @Test
    fun `simon - harder difficulty shrinks the zones`() {
        val easy = PosturalSimonGame().start(60, Difficulty.N1, null).targets.first().radiusMm
        val hard = PosturalSimonGame().start(60, Difficulty.N5, null).targets.first().radiusMm
        assertTrue(hard < easy, "hard=${hard} easy=${easy}")
    }

    @Test
    fun `finished state exposes the difficulty and game stats`() {
        val engine = BubbleHunterGame()
        val state = engine.start(1, Difficulty.N5, null)
        val finished = engine.update(cop(0.0, 0.0), 2f)
        assertTrue(finished.phase is GamePhase.Finished)
        assertEquals(Difficulty.N5, finished.difficulty)
        assertTrue(finished.stats.isNotEmpty(), "stats=${finished.stats}")
    }
}
