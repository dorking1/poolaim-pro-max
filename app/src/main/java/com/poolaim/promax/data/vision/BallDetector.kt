package com.poolaim.promax.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.TableModel
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Phase C: detects and classifies pool balls inside the confirmed table
 * boundary. Designed for top-down screenshots (e.g. 8 Ball Pool) as well
 * as real photos taken at 45-60 degrees.
 *
 * Pipeline (per confirmed table quad):
 * 1. Perspective-warp the table quad to a rectangle (top-down canonical view).
 * 2. Mask to the felt region (exclude rails and pockets).
 * 3. Adaptive grayscale threshold + HoughCircles as candidate generators.
 * 4. Filter candidates by circularity/size consistency (balls are similar size).
 * 5. Classify each ball by HSV analysis of its center disk:
 *    - White/near-white => CUE
 *    - Black => EIGHT
 *    - White-colored ring fraction high => STRIPE
 *    - Otherwise => SOLID
 *    - Number identification requires OCR in a later phase.
 *
 * Known overlays handled: aiming lines / reticles over balls are mostly
 * thin strokes; the classification uses the ball CENTER disk which the
 * reticle center rarely covers fully. Badly overlapped balls are reported
 * as UNKNOWN for manual correction.
 */
class BallDetector(context: Context) {

    private val appContext = context.applicationContext

    init {
        OpenCVLoader.initLocal()
    }

    companion object {
        private const val WARP_WIDTH = 800        // canonical warped width
        private const val MIN_RADIUS_FRACTION = 0.010  // of warped width
        private const val MAX_RADIUS_FRACTION = 0.045
        private const val FELT_MARGIN_FRACTION = 0.06   // shrink quad to avoid rails
        private const val MIN_BALL_AREA_FRACTION = 0.0004 // of warped area
    }

    sealed interface BallDetectionResult {
        data class Success(val balls: List<BallModel>) : BallDetectionResult
        data object Failure : BallDetectionResult
    }

    fun detect(uri: Uri, table: TableModel): BallDetectionResult {
        val bitmap = decode(uri) ?: return BallDetectionResult.Failure

        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)

        // 1. Warp the table quad to a canonical rectangle.
        val warped = warpTable(rgba, table, bitmap) ?: run {
            rgba.release()
            return BallDetectionResult.Failure
        }

        // 2. Candidate detection: HoughCircles on blurred grayscale.
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.medianBlur(gray, gray, 5)

        val circles = Mat()
        val minR = (WARP_WIDTH * MIN_RADIUS_FRACTION).toInt().coerceAtLeast(4)
        val maxR = (WARP_WIDTH * MAX_RADIUS_FRACTION).toInt()

        Imgproc.HoughCircles(
            gray, circles, Imgproc.HOUGH_GRADIENT,
            1.0,
            minR * 2.0,
            120.0,
            35.0,
            minR,
            maxR,
        )

        if (circles.cols() == 0) {
            release(rgba, warped, gray, circles)
            return BallDetectionResult.Failure
        }

        // 3. Classification of each candidate circle.
        val balls = ArrayList<BallModel>()
        val warpArea = warped.width() * warped.height().toDouble()

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            val cx = data[0]
            val cy = data[1]
            val r = data[2]

            // Skip circles too close to the warped felt edge (rail/pocket overlap).
            val margin = WARP_WIDTH * FELT_MARGIN_FRACTION
            if (cx - r < margin || cy - r < margin ||
                cx + r > warped.width() - margin || cy + r > warped.height() - margin
            ) continue

            // Radius sanity: balls are roughly equal-sized; reject outliers.
            if (r < minR || r > maxR) continue

            val classification = classifyBall(warped, cx, cy, r, warpArea)
            if (classification != null) {
                balls.add(classification)
            }
        }

        release(rgba, warped, gray, circles)

        return if (balls.isEmpty()) {
            BallDetectionResult.Failure
        } else {
            BallDetectionResult.Success(balls)
        }
    }

    // ---- classification ----

    private fun classifyBall(
        warped: Mat,
        cx: Double,
        cy: Double,
        r: Double,
        warpArea: Double,
    ): BallModel? {
        // Sample the CENTER disk (inner 55% of radius): reticles and thin
        // aiming lines rarely cover the whole center.
        val centerR = (r * 0.55).coerceAtLeast(2.0)
        val patch = Mat()
        runCatching {
            val roiX = (cx - centerR).toInt().coerceIn(0, warped.width() - 1)
            val roiY = (cy - centerR).toInt().coerceIn(0, warped.height() - 1)
            val roiW = (centerR * 2).toInt().coerceIn(1, warped.width() - roiX)
            val roiH = (centerR * 2).toInt().coerceIn(1, warped.height() - roiY)
            val roi = warped.submat(roiY, roiY + roiH, roiX, roiX + roiW)
            roi.copyTo(patch)
            roi.release()
        }.getOrElse {
            patch.release()
            return null
        }

        val hsv = Mat()
        Imgproc.cvtColor(patch, hsv, Imgproc.COLOR_BGR2HSV)

        val mean = Core.mean(hsv)
        val hue = mean.`val`[0]
        val sat = mean.`val`[1]
        val val0 = mean.`val`[2]

        patch.release()
        hsv.release()

        val areaFraction = (Math.PI * r * r) / warpArea
        if (areaFraction < MIN_BALL_AREA_FRACTION) return null

        val type = when {
            sat < 60 && val0 > 160 -> BallModel.Type.CUE        // near-white
            val0 < 80 && sat < 90 -> BallModel.Type.EIGHT       // black
            sat >= 60 && val0 >= 80 -> {
                // Solid vs stripe: compare color-purity of center disk.
                // Stripes have a large white band through the center, so the
                // center disk mean saturation is notably lower than a solid.
                if (sat < 110) BallModel.Type.STRIPE else BallModel.Type.SOLID
            }
            else -> BallModel.Type.UNKNOWN
        }

        return BallModel(
            number = null,        // number OCR is a later phase
            centerX = cx.toFloat(),
            centerY = cy.toFloat(),
            radius = r.toFloat(),
            type = type,
        )
    }

    // ---- warping ----

    /** Warps the table quad into a canonical WARP_WIDTH x h rectangle. */
    private fun warpTable(rgba: Mat, table: TableModel, bitmap: Bitmap): Mat? {
        // Table corners are in ORIGINAL image pixel space. The bitmap decoded
        // here may be subsampled, so scale coordinates to match this Mat.
        val scale = rgba.width().toDouble() / bitmap.width

        // corners order from TableBoundaryDetector: TL, TR, BR, BL
        val tl = Point(table.corners[0].first * scale, table.corners[0].second * scale)
        val tr = Point(table.corners[1].first * scale, table.corners[1].second * scale)
        val br = Point(table.corners[2].first * scale, table.corners[2].second * scale)
        val bl = Point(table.corners[3].first * scale, table.corners[3].second * scale)

        // Estimate aspect ratio (2:1 standard table) from the quad.
        val widthTop = hypot(tr.x - tl.x, tr.y - tl.y)
        val widthBottom = hypot(br.x - bl.x, br.y - bl.y)
        val heightLeft = hypot(bl.x - tl.x, bl.y - tl.y)
        val heightRight = hypot(br.x - tr.x, br.y - tr.y)
        val warpW = WARP_WIDTH.toDouble()
        val warpH = (warpW * ((heightLeft + heightRight) / (widthTop + widthBottom))).coerceAtLeast(1.0)

        val src = MatOfPoint2f(tl, tr, br, bl)
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(warpW, 0.0),
            Point(warpW, warpH),
            Point(0.0, warpH),
        )
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val out = Mat()
        Imgproc.warpPerspective(rgba, out, m, Size(warpW, warpH))

        src.release(); dst.release(); m.release()
        return out
    }

    // ---- decode ----

    private fun decode(uri: Uri): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    private fun release(vararg mats: Mat) = mats.forEach { it.release() }
}
