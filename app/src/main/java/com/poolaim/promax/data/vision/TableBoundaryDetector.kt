package com.poolaim.promax.data.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.content.Context
import com.poolaim.promax.domain.model.TableModel
import org.opencv.android.OpenCVLoader
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
 * Detects the pool table boundary (green felt region) in a captured photo.
 *
 * Strategy:
 * 1. Downscale for speed, blur to suppress noise.
 * 2. HSV threshold on "green felt" hue range.
 * 3. Morphological close to merge the felt into one blob.
 * 4. Find the largest contour; require it to cover >= [MIN_AREA_FRACTION]
 *    of the image, otherwise report failure so the UI can offer manual
 *    calibration.
 * 5. Fit a rotated rect around the blob and derive the four corners in
 *    original-image pixel coordinates.
 */
class TableBoundaryDetector(private val context: Context) {

    init {
        // Loads the native library once; safe to call repeatedly.
        OpenCVLoader.initLocal()
    }

    companion object {
        private const val MIN_AREA_FRACTION = 0.08  // felt must span >= 8% of photo
        private const val WORK_SIZE = 640.0         // working resolution for analysis
        private const val GREEN_HUE_LOW = 35.0      // HSV H range (OpenCV: 0-180)
        private const val GREEN_HUE_HIGH = 85.0
        private const val GREEN_SAT_MIN = 40.0
        private const val GREEN_VAL_MIN = 40.0
    }

    /** Result of table detection. */
    sealed interface DetectionResult {
        data class Success(val table: TableModel) : DetectionResult
        data object Failure : DetectionResult
    }

    fun detect(uri: Uri): DetectionResult {
        val bitmap = decodeScaled(uri) ?: return DetectionResult.Failure
        val scale = bitmap.width / WORK_SIZE
        val work = Bitmap.createScaledBitmap(
            bitmap,
            WORK_SIZE.toInt(),
            (bitmap.height / scale).toInt().coerceAtLeast(1),
            true,
        )

        val mat = Mat().also {
            val px = IntArray(work.width * work.height)
            work.getPixels(px, 0, work.width, 0, 0, work.width, work.height)
            it.put(0, 0, px.toIntArray().map { v ->
                byteArrayOf(
                    (v and 0xFF).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(),
                )
            }.fold(byteArrayOf()) { acc, b -> acc + b })
        }

        // --- Pipeline ---
        val blurred = Mat()
        Imgproc.GaussianBlur(mat, blurred, Size(9.0, 9.0), 0.0)

        val hsv = Mat()
        Imgproc.cvtColor(blurred, hsv, Imgproc.COLOR_RGB2HSV)

        val mask = Mat()
        Core.inRange(
            hsv,
            Scalar(GREEN_HUE_LOW, GREEN_SAT_MIN, GREEN_VAL_MIN),
            Scalar(GREEN_HUE_HIGH, 255.0, 255.0),
            mask,
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
        )
        if (contours.isEmpty()) return DetectionResult.Failure

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return DetectionResult.Failure
        val areaFraction = Imgproc.contourArea(largest) / (work.width() * work.height()).toDouble()
        if (areaFraction < MIN_AREA_FRACTION) return DetectionResult.Failure

        val rect = Imgproc.minAreaRect(MatOfPoint2f(*largest.toArray()))
        val corners2f = arrayOfNulls<Point>(4)
        val points = Mat()
        Imgproc.boxPoints(rect, points)
        for (i in 0 until 4) {
            val p = Point(points.get(i, 0)[0] * scale, points.get(i, 1)[0] * scale)
            corners2f[i] = p
        }

        // Order corners: TL, TR, BR, BL (independent of rotation)
        val ordered = orderCorners(listOfNotNull(*corners2f))

        val bounds = RectF(
            ordered.minOf { it.first },
            ordered.minOf { it.second },
            ordered.maxOf { it.first },
            ordered.maxOf { it.second },
        )

        // Confidence: how rectangular the blob is (area ratio of blob vs fitted rect)
        val rectArea = rect.size.area()
        val confidence = (Imgproc.contourArea(largest) / rectArea).toFloat().coerceIn(0f, 1f)

        return DetectionResult.Success(
            TableModel(
                bounds = bounds,
                corners = ordered,
                confidence = confidence,
                manuallyCalibrated = false,
            ),
        )
    }

    // ---- helpers ----

    private fun orderCorners(points: List<Point>): List<Pair<Float, Float>> {
        val sum = points.map { it.x + it.y }
        val diff = points.map { it.y - it.x }

        val tl = points[sum.indexOf(sum.min())]
        val br = points[sum.indexOf(sum.max())]
        val tr = points[diff.indexOf(diff.min())]
        val bl = points[diff.indexOf(diff.max())]

        return listOf(
            Pair(tl.x.toFloat(), tl.y.toFloat()),
            Pair(tr.x.toFloat(), tr.y.toFloat()),
            Pair(br.x.toFloat(), br.y.toFloat()),
            Pair(bl.x.toFloat(), bl.y.toFloat()),
        )
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 2  // full resolution not needed for boundary detection
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}
