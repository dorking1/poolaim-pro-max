package com.poolaim.promax.domain.physics.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotGeometryEngineTest {
    private val engine = ShotGeometryEngine()
    private val bounds = TableBounds(width = 1.0, height = 0.5)

    @Test
    fun alignedDirectShotProducesNearZeroCutAngle() {
        val cue = PoolBall("cue", Point2(0.20, 0.25), 0.02)
        val target = PoolBall("target", Point2(0.50, 0.25), 0.02)
        val pocket = PoolPocket("right-middle", Point2(0.90, 0.25), 0.04)

        val shots = engine.plan(cue, listOf(target), listOf(pocket), bounds, includeBanks = false)

        assertTrue("Expected at least one direct shot", shots.isNotEmpty())
        assertEquals(PathKind.DIRECT, shots.first().kind)
        assertTrue("Straight shot should have near-zero cut angle", shots.first().cutAngleDegrees < 0.1)
        assertTrue(shots.first().isCuePathClear)
        assertTrue(shots.first().isObjectPathClear)
    }

    @Test
    fun blockerOnCuePathRejectsDirectShot() {
        val cue = PoolBall("cue", Point2(0.20, 0.25), 0.02)
        val target = PoolBall("target", Point2(0.60, 0.25), 0.02)
        val blocker = PoolBall("blocker", Point2(0.38, 0.25), 0.02)
        val pocket = PoolPocket("right-middle", Point2(0.90, 0.25), 0.04)

        val shots = engine.plan(
            cue, listOf(target, blocker), listOf(pocket), bounds, includeBanks = false,
        )

        assertTrue("A ball blocking the cue path must prevent this shot", shots.isEmpty())
    }

    @Test
    fun noTargetsReturnsNoShots() {
        val cue = PoolBall("cue", Point2(0.20, 0.25), 0.02)
        val pocket = PoolPocket("right-middle", Point2(0.90, 0.25), 0.04)
        assertTrue(engine.plan(cue, emptyList(), listOf(pocket), bounds).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTableDimensionsAreRejected() {
        TableBounds(width = 0.0, height = 0.5)
    }
}
