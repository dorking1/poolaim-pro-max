package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SuppliedGameTableProfileTest {
    @Test
    fun referenceClothTopLeftMapsToTableOrigin() {
        val result = SuppliedGameTableProfile.toTableCoordinates(Point2(304.0, 161.0), 1536, 720)
        assertEquals(0.0, result.x, 1e-6)
        assertEquals(0.0, result.y, 1e-6)
    }

    @Test
    fun coordinateMappingScalesWithFrameResolution() {
        val result = SuppliedGameTableProfile.toTableCoordinates(Point2(608.0, 322.0), 3072, 1440)
        assertEquals(0.0, result.x, 1e-6)
        assertEquals(0.0, result.y, 1e-6)
    }

    @Test
    fun tableCoordinatesRoundTripAtReferenceResolution() {
        val original = Point2(400.0, 210.0)
        val screen = SuppliedGameTableProfile.toScreenCoordinates(original, 1536, 720)
        val roundTrip = SuppliedGameTableProfile.toTableCoordinates(screen, 1536, 720)
        assertEquals(original.x, roundTrip.x, 1e-6)
        assertEquals(original.y, roundTrip.y, 1e-6)
    }

    @Test
    fun profileDefinesSixPockets() {
        assertEquals(6, SuppliedGameTableProfile.pockets.size)
    }

    @Test
    fun invalidFrameDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SuppliedGameTableProfile.toTableCoordinates(Point2(0.0, 0.0), 0, 720)
        }
    }
}
