package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableCalibrationTest {
    @Test
    fun screenTableCoordinatesRoundTrip() {
        val calibration = TableCalibration.fromReferenceFrame(1536, 720)
        val point = Point2(760.0, 390.0)
        val normalized = calibration.screenToTable(point)
        val restored = calibration.tableToScreen(normalized)
        assertEquals(point.x, restored.x, 1e-9)
        assertEquals(point.y, restored.y, 1e-9)
    }

    @Test
    fun referenceProfileScalesToCaptureResolution() {
        val calibration = TableCalibration.fromReferenceFrame(768, 360)
        assertTrue(calibration.isValid)
        assertEquals(152.0, calibration.clothLeft, 1e-9)
        assertEquals(464.0, calibration.clothWidth, 1e-9)
        assertEquals(232.0, calibration.clothHeight, 1e-9)
    }

    @Test
    fun sixPocketCentersAreProduced() {
        val calibration = TableCalibration.fromReferenceFrame(1536, 720)
        val pockets = calibration.pocketCenters()
        assertEquals(6, pockets.size)
        assertEquals("top_left", pockets.first().id)
        assertTrue(pockets.all { it.radiusPixels > 0.0 })
    }

    @Test
    fun calibrationCanBeRescaled() {
        val original = TableCalibration.fromReferenceFrame(1536, 720)
        val scaled = original.rescaled(3072, 1440)
        assertEquals(original.clothLeft * 2, scaled.clothLeft, 1e-9)
        assertEquals(original.clothBottom * 2, scaled.clothBottom, 1e-9)
    }
}
