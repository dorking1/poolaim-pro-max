package com.poolaim.promax.domain.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Lightweight HSV color-family heuristic for candidate balls.
 * Does not infer printed numbers or solid/stripe group.
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

    fun estimate(frameBgr: Mat, centerX: Double, centerY: Double, radius: Double): Estimate {
        require(!frameBgr.empty()) { "Input frame must not be empty" }
        require(frameBgr.channels() == 3) { "Expected a BGR frame" }
        require(radius > 1.0) { "Ball radius must be greater than one pixel" }

        val half = (radius * 0.42).roundToInt().coerceAtLeast(1)
        val left = (centerX.roundToInt() - half).coerceIn(0, frameBgr.cols() - 1)
        val top = (centerY.roundToInt() - half).coerceIn(0, frameBgr.rows() - 1)
        val right = (centerX.roundToInt() + half + 1).coerceIn(left + 1, frameBgr.cols())
        val bottom = (centerY.roundToInt() + half + 1).coerceIn(top + 1, frameBgr.rows())
        val patch = frameBgr.submat(Rect(left, top, right - left, bottom - top))
        val hsv = Mat()
        try {
            Imgproc.cvtColor(patch, hsv, Imgproc.COLOR_BGR2HSV)
            val mean = Core.mean(hsv)
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
            hsv.release()
        }
    }
}
