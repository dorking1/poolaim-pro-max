package com.poolaim.promax.domain.vision

import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * OpenCV Hough-circle proposal generator for the supplied game's cloth region.
 *
 * This only proposes circular objects. It does not classify the cue ball,
 * identify numbers/stripes, or guarantee that a proposal is a ball. Callers
 * should render proposals for correction and track confidence over frames.
 * OpenCV must be initialized by the app before this class is used.
 */
class GameBallCircleProposer(
    private val param1: Double = 110.0,
    private val param2: Double = 18.0,
    private val minRadiusAtReferenceSize: Double = 7.0,
    private val maxRadiusAtReferenceSize: Double = 19.0,
) {
    data class Candidate(
        /** Center in original screenshot pixel coordinates. */
        val centerX: Double,
        val centerY: Double,
        /** Radius in original screenshot pixels. */
        val radius: Double,
    )

    init {
        require(param1 > 0.0 && param2 > 0.0)
        require(minRadiusAtReferenceSize > 0.0)
        require(maxRadiusAtReferenceSize >= minRadiusAtReferenceSize)
    }

    fun detect(frame: Mat): List<Candidate> {
        require(!frame.empty()) { "Input frame must not be empty" }
        require(frame.cols() > 0 && frame.rows() > 0) { "Invalid frame dimensions" }

        val scaleX = frame.cols().toDouble() / SuppliedGameTableProfile.REFERENCE_WIDTH
        val scaleY = frame.rows().toDouble() / SuppliedGameTableProfile.REFERENCE_HEIGHT
        val left = (SuppliedGameTableProfile.CLOTH_LEFT * scaleX).roundToInt().coerceIn(0, frame.cols() - 1)
        val top = (SuppliedGameTableProfile.CLOTH_TOP * scaleY).roundToInt().coerceIn(0, frame.rows() - 1)
        val right = (SuppliedGameTableProfile.CLOTH_RIGHT * scaleX).roundToInt().coerceIn(left + 1, frame.cols())
        val bottom = (SuppliedGameTableProfile.CLOTH_BOTTOM * scaleY).roundToInt().coerceIn(top + 1, frame.rows())
        val roi = Rect(left, top, right - left, bottom - top)

        val crop = frame.submat(roi)
        val gray = Mat()
        val blurred = Mat()
        val circles = Mat()
        try {
            when (crop.channels()) {
                1 -> crop.copyTo(gray)
                3 -> Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
                4 -> Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGRA2GRAY)
                else -> throw IllegalArgumentException("Unsupported frame channel count: ${crop.channels()}")
            }
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 1.2)

            val radiusScale = (scaleX + scaleY) / 2.0
            val minRadius = (minRadiusAtReferenceSize * radiusScale).roundToInt().coerceAtLeast(2)
            val maxRadius = (maxRadiusAtReferenceSize * radiusScale).roundToInt().coerceAtLeast(minRadius)
            val minDistance = (minRadius * 1.6).coerceAtLeast(4.0)

            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT,
                1.2,
                minDistance,
                param1,
                param2,
                minRadius,
                maxRadius,
            )

            val results = ArrayList<Candidate>(circles.cols())
            for (i in 0 until circles.cols()) {
                val circle = circles.get(0, i) ?: continue
                if (circle.size < 3) continue
                val x = circle[0] + left
                val y = circle[1] + top
                val radius = circle[2]
                if (x in left.toDouble()..right.toDouble() &&
                    y in top.toDouble()..bottom.toDouble() &&
                    radius > 0.0
                ) {
                    results += Candidate(x, y, radius)
                }
            }
            return results
        } finally {
            crop.release()
            gray.release()
            blurred.release()
            circles.release()
        }
    }
}
