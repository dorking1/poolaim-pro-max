package com.poolaim.promax.domain.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the physics primitives (Phase G).
 */
class PhysicsModelsTest {

    @Test
    fun `vec2 arithmetic is correct`() {
        val a = Vec2(3f, 4f)
        assertEquals(5f, a.length, 1e-4f)
        assertEquals(Vec2(6f, 8f), a * 2f)
        assertEquals(Vec2(1.5f, 2f), a / 2f)
        assertEquals(Vec2(1f, 1f), a - Vec2(2f, 3f))
        assertEquals(Vec2(5f, 7f), a + Vec2(2f, 3f))
        assertEquals(11f, a.dot(Vec2(1f, 2f)), 1e-4f)
        assertEquals(Vec2(0.6f, 0.8f), a.normalized)
    }

    @Test
    fun `zero vector normalizes safely`() {
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalized)
    }

    @Test
    fun `table geometry has six standard pockets`() {
        val g = TableGeometry(800f, 400f)
        assertEquals(6, g.pockets.size)
        // Corners + middle sides.
        assertEquals(Vec2(0f, 0f), g.pockets[0])
        assertEquals(Vec2(400f, 0f), g.pockets[1])
        assertEquals(Vec2(800f, 0f), g.pockets[2])
        assertEquals(Vec2(0f, 400f), g.pockets[3])
        assertEquals(Vec2(400f, 400f), g.pockets[4])
        assertEquals(Vec2(800f, 400f), g.pockets[5])
    }

    @Test
    fun `containsPoint respects margins`() {
        val g = TableGeometry(800f, 400f)
        assertTrue(g.containsPoint(Vec2(400f, 200f)))
        assertTrue(!g.containsPoint(Vec2(-1f, 200f)))
        assertTrue(!g.containsPoint(Vec2(400f, 500f)))
    }

    @Test
    fun `difficulty thresholds map correctly`() {
        assertEquals(Difficulty.EASY, Difficulty.fromFit(0.9f))
        assertEquals(Difficulty.MEDIUM, Difficulty.fromFit(0.6f))
        assertEquals(Difficulty.HARD, Difficulty.fromFit(0.35f))
        assertEquals(Difficulty.VERY_HARD, Difficulty.fromFit(0.1f))
    }
}
