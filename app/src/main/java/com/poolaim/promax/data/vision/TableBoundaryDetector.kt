package com.poolaim.promax.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import com.poolaim.promax.domain.model.TableModel
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Detects the pool table boundary (felt region) in a captured photo.
 *
 * Pipeline:
 * 1. Decode downscaled, convert to OpenCV Mat via [Utils.bitmapToMat] (BGRA).
 * 2. HSV threshold on the felt hue range. Covers BOTH green felt (H 35-85)
 *    and blue/cyan felt (H 85-105, e.g. 8 Ball Pool style tables).
 * 3. Morphological close/open to merge the felt into one blob.
 * 4. Find the largest contour; require >= [MIN_AREA_FRACTION] of the image,
 *    otherwise report failure so the UI can offer manual calibration.
 * 5. Fit a min-area rect, extract and order the four corners, map back to
 *    original-image coordinates.
 *
 * Limitations (manual calibration fallback exists for these):
 * - Very dark or unusual felt colors fall outside the hue window.
 * - Dark cushions adjacent to bright felt may be merged into the blob.
 */
class TableBoundaryDetector(context: Context) {

    private val appContext = context.applicationContext

    init {
        OpenCVLoader.initLocal()
    }

    companion object {
        private const val MIN_AREA_FRACTION = 0.08  // felt spans >= 8% of the photo
        private const val WORK_WIDTH = 640          // working resolution for analysis
        private const val FELT_HUE_LOW = 35.0      // OpenCV HSV H range: 0-180
        private const val FELT_HUE_HIGH = 105.0     // green through cyan-blue
        private const val FELT_SAT_MIN = 40.0
        private const val FELT_VAL_MIN = 90.0       // skip dark cushions
    }

    sealed interface DetectionResult {
        data class Success(val table: TableModel) : DetectionResult
        data object Failure : DetectionResult
    }

    fun detect(uri: Uri): DetectionResult {
        val bitmap = decodeScaled(uri) ?: return DetectionResult.Failure
        val work = scaleToWorkWidth(bitmap)

        val rgba = Mat()
        Utils.bitmapToMat(work, rgba)

        val hsv = Mat()
        Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_BGR2HSV)

        val mask = Mat()
        Core.inRange(
            hsv,
            Scalar(FELT_HUE_LOW, FELT_SAT_MIN, FELT_VAL_MIN),
            Scalar(FELT_HUE_HIGH, 255.0, 255.0),
            mask,
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
        )
        if (contours.isEmpty()) {
            release(rgba, hsv, mask)
            return DetectionResult.Failure
        }

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) }
        if (largest == null) {
            release(rgba, hsv, mask)
            return DetectionResult.Failure
        }

        val blobArea = Imgproc.contourArea(largest)
        val imageArea = work.width * work.height.toDouble()
        if (blobArea / imageArea < MIN_AREA_FRACTION) {
            release(rgba, hsv, mask)
            return DetectionResult.Failure
        }

        val scale = bitmap.width.toDouble() / work.width

        val rect = Imgproc.minAreaRect(MatOfPoint2f(*largest.toArray()))
        val boxPoints = Mat()
        Imgproc.boxPoints(rect, boxPoints)

        val corners = (0 until 4).map { i ->
            val x = boxPoints.get(i, 0)[0] * scale
            val y = boxPoints.get(i, 1)[0] * scale
            Point(x, y)
        }

        val ordered = orderCorners(corners)
        val bounds = RectF(
            ordered.minOf { it.first },
            ordered.minOf { it.second },
            ordered.maxOf { it.first },
            ordered.maxOf { it.second },
        )

        // Confidence: how rectangular the blob is (blob area vs fitted rect area).
        val rectArea = rect.size.area().coerceAtLeast(1.0)
        val confidence = (blobArea / rectArea).toFloat().coerceIn(0f, 1f)

        release(rgba, hsv, mask)

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
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.y - it.x }

        val tl = points[sums.indexOf(sums.min())]
        val br = points[sums.indexOf(sums.max())]
        val tr = points[diffs.indexOf(diffs.min())]
        val bl = points[diffs.indexOf(diffs.max())]

        return listOf(
            tl.x.toFloat() to tl.y.toFloat(),
            tr.x.toFloat() to tr.y.toFloat(),
            br.x.toFloat() to br.y.toFloat(),
            bl.x.toFloat() to bl.y.toFloat(),
        )
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    private fun scaleToWorkWidth(src: Bitmap): Bitmap {
        if (src.width <= WORK_WIDTH) return src
        val ratio = WORK_WIDTH.toDouble() / src.width
        return Bitmap.createScaledBitmap(
            src,
            WORK_WIDTH,
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun release(vararg mats: Mat) = mats.forEach { it.release() }
}
