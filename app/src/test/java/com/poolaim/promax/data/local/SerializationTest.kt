package com.poolaim.promax.data.local

import com.poolaim.promax.domain.model.BallModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the history serialization (Phase G).
 */
class SerializationTest {

    private fun ball(x: Float, y: Float, r: Float, t: BallModel.Type) =
        BallModel(number = null, centerX = x, centerY = y, radius = r, type = t)

    @Test
    fun `balls csv round-trips losslessly`() {
        val balls = listOf(
            ball(10f, 20f, 5f, BallModel.Type.CUE),
            ball(30.5f, 40.25f, 6f, BallModel.Type.SOLID),
            ball(50f, 60f, 7f, BallModel.Type.STRIPE),
        )

        val csv = balls.toCsv()
        val parsed = parseBallsCsv(csv)

        assertEquals(balls.size, parsed.size)
        balls.zip(parsed).forEach { (orig, p) ->
            assertEquals(orig.centerX, p.centerX, 1e-4f)
            assertEquals(orig.centerY, p.centerY, 1e-4f)
            assertEquals(orig.radius, p.radius, 1e-4f)
            assertEquals(orig.type, p.type)
        }
    }

    @Test
    fun `malformed entries are skipped without crashing`() {
        val csv = "10,20,5,CUE;garbage;30,40,6,SOLID;;"
        val parsed = parseBallsCsv(csv)
        assertEquals(2, parsed.size)
        assertEquals(BallModel.Type.CUE, parsed[0].type)
        assertEquals(BallModel.Type.SOLID, parsed[1].type)
    }

    @Test
    fun `empty string yields empty list`() {
        assertTrue(parseBallsCsv("").isEmpty())
    }
}
