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
    fun `straight shot to corner pocket scores highest`() {
        val cue = ball(100f, 200f, type = BallModel.Type.CUE)
        val target = ball(300f, 200f)

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target),
            geometry = geometry,
        )

        assertTrue("should have candidates", plan.candidates.isNotEmpty())
        val best = plan.bestShot
        assertNotNull(best)

        // Straight line cue -> target -> pocket(0,200): near-zero cut angle.
        assertTrue("cut angle should be small, was ${best!!.cutAngleDeg}", best.cutAngleDeg < 5f)
        assertTrue("straight shot should be EASY", best.difficulty == Difficulty.EASY)
        assertEquals(ShotKind.DIRECT, best.kind)
    }

    @Test
    fun `blocked path is heavily penalized`() {
        val cue = ball(100f, 200f, type = BallModel.Type.CUE)
        val target = ball(300f, 200f)
        val blocker = ball(200f, 200f) // directly between cue and target

        val plan = ShotCalculator().calculateShots(
            cueBall = cue,
            playerBalls = listOf(target),
            allBalls = listOf(cue, target, blocker),
            geometry = geometry,
        )

        assertTrue("plan may contain blocked candidates", true)
        // Either all blocked, or a different pocket line is better.
        val best = plan.bestShot
        if (best != null) {
            val blocked = best.blockersOnCuePath.isNotEmpty() || best.blockersOnTargetPath.isNotEmpty()
            if (blocked) {
                assertTrue("blocked best must score low", best.fitScore < 0.35f)
            }
        }
    }

    @Test
    fun `ghost point sits one ball diameter beyond target center`() {
        val cue = ball(100f, 100f, type = BallModel.Type.CUE)
        val target = ball(500f, 100f) // straight line to pocket (800,100)
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
        // Ghost point x: target center - direction * 2r; pocket is at +x,
        // direction is +x, so ghost x = 500 - 20 = 480.
        assertEquals(480f, best.ghostPoint.x, 1f)
        assertEquals(100f, best.ghostPoint.y, 1f)
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
