package com.poolaim.promax.domain.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Fast HSV color-family heuristic. Estimates are visual hints only: highlights,
 * stripes, shadows and game effects can change the dominant color.
 */
class BallColorHeuristic {
    enum class ColorFamily { WHITE_OR_CUE, YELLOW, ORANGE, RED, PURPLE, BLUE, GREEN, UNKNOWN }

    data class Estimate(
        val family: ColorFamily,
        val meanHue: Double,
        val meanSaturation: Double,
        val meanValue: Double,
        val confidence: Double,
    )

    /** Convenience API for a single estimate; frame analysis should use estimateHsv for speed. */
    fun estimate(frameBgr: Mat, centerX: Double, centerY: Double, radius: Double): Estimate {
        require(!frameBgr.empty()) { "Input frame must not be empty" }
        require(frameBgr.channels() == 3) { "Expected a BGR frame" }
        val hsv = Mat()
        return try {
            Imgproc.cvtColor(frameBgr, hsv, Imgproc.COLOR_BGR2HSV)
            estimateHsv(hsv, centerX, centerY, radius)
        } finally {
            hsv.release()
        }
    }

    /** Reuses one HSV conversion for all balls in a frame, reducing per-frame work. */
    fun estimateHsv(frameHsv: Mat, centerX: Double, centerY: Double, radius: Double): Estimate {
        require(!frameHsv.empty()) { "Input HSV frame must not be empty" }
        require(frameHsv.channels() == 3) { "Expected an HSV frame" }
        require(radius > 1.0) { "Ball radius must be greater than one pixel" }

        val half = (radius * 0.34).roundToInt().coerceAtLeast(1)
        val cx = centerX.roundToInt()
        val cy = centerY.roundToInt()
        val left = (cx - half).coerceIn(0, frameHsv.cols() - 1)
        val top = (cy - half).coerceIn(0, frameHsv.rows() - 1)
        val right = (cx + half + 1).coerceIn(left + 1, frameHsv.cols())
        val bottom = (cy + half + 1).coerceIn(top + 1, frameHsv.rows())
        val patch = frameHsv.submat(Rect(left, top, right - left, bottom - top))
        try {
            val mean = Core.mean(patch)
            val hue = mean.`val`[0]
            val saturation = mean.`val`[1]
            val value = mean.`val`[2]

            val family = when {
                saturation < 55.0 && value > 150.0 -> ColorFamily.WHITE_OR_CUE
                saturation < 35.0 -> ColorFamily.UNKNOWN
                hue < 12.0 || hue >= 170.0 -> ColorFamily.RED
                hue < 24.0 -> ColorFamily.ORANGE
                hue < 38.0 -> ColorFamily.YELLOW
                hue < 82.0 -> ColorFamily.GREEN
                hue < 132.0 -> ColorFamily.BLUE
                hue < 160.0 -> ColorFamily.PURPLE
                else -> ColorFamily.UNKNOWN
            }

            val confidence = when (family) {
                ColorFamily.UNKNOWN -> 0.0
                ColorFamily.WHITE_OR_CUE -> ((value - 130.0) / 100.0).coerceIn(0.0, 0.75) *
                    (1.0 - saturation / 180.0).coerceIn(0.0, 1.0)
                else -> (saturation / 180.0).coerceIn(0.0, 0.85)
            }
            return Estimate(family, hue, saturation, value, confidence)
        } finally {
            patch.release()
        }
    }
}
