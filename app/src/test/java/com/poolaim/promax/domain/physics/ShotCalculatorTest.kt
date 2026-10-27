package com.poolaim.promax.domain.physics

import com.poolaim.promax.domain.model.BallModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for the ShotCalculator (Phase D).
 * Run with: ./gradlew test
 */
class ShotCalculatorTest {

    private val geometry = TableGeometry(width = 800f, height = 400f)

    private fun ball(x: Float, y: Float, r: Float = 10f, type: BallModel.Type = BallModel.Type.SOLID) =
        BallModel(number = null, centerX = x, centerY = y, radius = r, type = type)

    @Test
    fun `straight shot to side pocket scores highest`() {
        val cue = ball(400f, 200f, type = BallModel.Type.CUE)
        val target = ball(400f, 100f)

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target),
            geometry = geometry,
        )

        assertTrue("should have candidates", plan.candidates.isNotEmpty())
        val best = plan.bestShot
        assertNotNull(best)

        // Straight line cue -> target -> the actual side pocket at (400, 0).
        assertEquals(Vec2(400f, 0f), best!!.pocket)
        assertEquals(0f, best.cutAngleDeg, 1e-4f)
        assertTrue("straight shot should be EASY", best.difficulty == Difficulty.EASY)
        assertEquals(ShotKind.DIRECT, best.kind)
    }

    @Test
    fun `blocked path is heavily penalized`() {
        val cue = ball(400f, 300f, type = BallModel.Type.CUE)
        val target = ball(400f, 150f)
        val blocker = ball(400f, 220f) // directly between cue and target

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target, blocker),
            geometry = geometry,
        )

        // Check the obstructed pocket explicitly rather than accepting an empty plan.
        val blockedShot = plan.candidates.single { it.pocket == Vec2(400f, 0f) }
        assertEquals(listOf(blocker), blockedShot.blockersOnCuePath)
        assertTrue(blockedShot.blockersOnTargetPath.isEmpty())
        assertTrue("blocked shot must score low", blockedShot.fitScore < 0.15f)
    }

    @Test
    fun `ghost point sits one ball diameter beyond target center`() {
        val cue = ball(400f, 300f, type = BallModel.Type.CUE)
        val target = ball(400f, 150f) // straight line to the side pocket (400, 0)
        val r = 10f

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target),
            geometry = geometry,
        )

        val best = plan.bestShot!!
        val pocketDist = best.targetTravel
        assertTrue(pocketDist > 0f)
        // The pocket direction is -y, so the ghost sits 2r below the target.
        assertEquals(Vec2(400f, 0f), best.pocket)
        assertEquals(400f, best.ghostPoint.x, 1e-4f)
        assertEquals(target.centerY + 2f * r, best.ghostPoint.y, 1e-4f)
    }

    @Test
    fun `no candidates when cut angle exceeds limit`() {
        val cue = ball(400f, 200f, type = BallModel.Type.CUE)
        // Target directly above, pocket line to the right: ~90 deg cut.
        val target = ball(400f, 50f)

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target),
            geometry = geometry,
        )

        // Some pockets will be implausible; those candidates must be excluded.
        plan.candidates.forEach {
            assertTrue("cut angle over limit excluded", it.cutAngleDeg <= 80f)
        }
    }
}
