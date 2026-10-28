package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import kotlin.math.abs

/**
 * User-adjustable rectangular table calibration in screenshot pixel coordinates.
 *
 * The six pockets are expressed in normalized cloth coordinates (0..1), allowing
 * calibration to scale across capture resolutions. This model intentionally
 * supports a rectangular, axis-aligned cloth only; it does not claim perspective
 * correction. A future four-corner homography can replace this mapper.
 */
data class TableCalibration(
    val frameWidth: Int,
    val frameHeight: Int,
    val clothLeft: Double,
    val clothTop: Double,
    val clothRight: Double,
    val clothBottom: Double,
    val pockets: List<NormalizedPocket> = defaultPockets(),
) {
    data class NormalizedPocket(val id: String, val x: Double, val y: Double, val radius: Double) {
        init {
            require(id.isNotBlank())
            require(x in -0.15..1.15 && y in -0.15..1.15) { "Pocket coordinate outside calibration range" }
            require(radius > 0.0 && radius.isFinite())
        }
    }

    val clothWidth get() = clothRight - clothLeft
    val clothHeight get() = clothBottom - clothTop
    val isValid get() = frameWidth > 0 && frameHeight > 0 &&
        clothLeft.isFinite() && clothTop.isFinite() && clothRight.isFinite() && clothBottom.isFinite() &&
        clothLeft >= 0 && clothTop >= 0 && clothRight <= frameWidth && clothBottom <= frameHeight &&
        clothWidth >= frameWidth * 0.2 && clothHeight >= frameHeight * 0.15 &&
        clothRight > clothLeft && clothBottom > clothTop && pockets.size == 6

    init {
        require(frameWidth > 0 && frameHeight > 0)
        require(clothLeft.isFinite() && clothTop.isFinite() && clothRight.isFinite() && clothBottom.isFinite())
        require(clothRight > clothLeft && clothBottom > clothTop)
        require(pockets.size == 6) { "Expected six pocket calibration points" }
    }

    fun screenToTable(point: Point2): Point2 {
        require(isValid) { "Table calibration is invalid" }
        return Point2(
            (point.x - clothLeft) / clothWidth,
            (point.y - clothTop) / clothHeight,
        )
    }

    fun tableToScreen(point: Point2): Point2 {
        require(isValid) { "Table calibration is invalid" }
        return Point2(clothLeft + point.x * clothWidth, clothTop + point.y * clothHeight)
    }

    fun pocketCenters(): List<CalibratedPocket> {
        require(isValid) { "Table calibration is invalid" }
        return pockets.map {
            CalibratedPocket(
                id = it.id,
                center = Point2(it.x * clothWidth + clothLeft, it.y * clothHeight + clothTop),
                radiusPixels = it.radius * minOf(clothWidth, clothHeight),
            )
        }
    }

    fun rescaled(newWidth: Int, newHeight: Int): TableCalibration {
        require(newWidth > 0 && newHeight > 0)
        val sx = newWidth.toDouble() / frameWidth
        val sy = newHeight.toDouble() / frameHeight
        return copy(
            frameWidth = newWidth,
            frameHeight = newHeight,
            clothLeft = clothLeft * sx,
            clothTop = clothTop * sy,
            clothRight = clothRight * sx,
            clothBottom = clothBottom * sy,
        )
    }

    companion object {
        fun fromReferenceFrame(
            frameWidth: Int,
            frameHeight: Int,
            left: Double = SuppliedGameTableProfile.CLOTH_LEFT,
            top: Double = SuppliedGameTableProfile.CLOTH_TOP,
            right: Double = SuppliedGameTableProfile.CLOTH_RIGHT,
            bottom: Double = SuppliedGameTableProfile.CLOTH_BOTTOM,
        ): TableCalibration {
            require(frameWidth > 0 && frameHeight > 0)
            val sx = frameWidth / SuppliedGameTableProfile.REFERENCE_WIDTH
            val sy = frameHeight / SuppliedGameTableProfile.REFERENCE_HEIGHT
            return TableCalibration(frameWidth, frameHeight, left * sx, top * sy, right * sx, bottom * sy)
        }

        fun defaultPockets() = listOf(
            NormalizedPocket("top_left", 0.0, 0.0, 0.05),
            NormalizedPocket("top_middle", 0.5, -0.025, 0.045),
            NormalizedPocket("top_right", 1.0, 0.0, 0.05),
            NormalizedPocket("bottom_left", 0.0, 1.0, 0.05),
            NormalizedPocket("bottom_middle", 0.5, 1.025, 0.045),
            NormalizedPocket("bottom_right", 1.0, 1.0, 0.05),
        )
    }
}

data class CalibratedPocket(val id: String, val center: Point2, val radiusPixels: Double)
