package com.poolaim.promax.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/**
 * Minimal transparent detection layer.
 *
 * Do not draw IDs, color names, or track ages over the play field: they obscure
 * the table when detections cluster. Only higher-confidence candidates are shown.
 */
class DetectionOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.25f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var radii = FloatArray(0)
    private var confidence = FloatArray(0)
    private var families = emptyArray<String>()
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun updateCandidates(
        x: FloatArray?, y: FloatArray?, r: FloatArray?,
        confidenceValues: FloatArray?, colorFamilies: Array<String>?,
        ids: LongArray?, ages: IntArray?,
        frameWidth: Int, frameHeight: Int,
    ) {
        xs = x ?: FloatArray(0)
        ys = y ?: FloatArray(0)
        radii = r ?: FloatArray(0)
        confidence = confidenceValues ?: FloatArray(0)
        families = colorFamilies ?: emptyArray()
        sourceWidth = frameWidth.coerceAtLeast(1)
        sourceHeight = frameHeight.coerceAtLeast(1)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scaleX = width.toFloat() / sourceWidth
        val scaleY = height.toFloat() / sourceHeight
        val scaleR = min(scaleX, scaleY)
        val n = min(xs.size, min(ys.size, radii.size))
        for (i in 0 until n) {
            val score = confidence.getOrNull(i) ?: 0f
            // Low-confidence Hough proposals were cluttering the entire table.
            if (score < MIN_DRAW_CONFIDENCE) continue

            val x = xs[i] * scaleX
            val y = ys[i] * scaleY
            val radius = radii[i] * scaleR
            if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f) continue

            val color = when (families.getOrNull(i)) {
                "WHITE_OR_CUE" -> Color.WHITE
                "YELLOW" -> 0xFFFFD54F.toInt()
                "ORANGE" -> 0xFFFF9800.toInt()
                "RED" -> 0xFFFF5252.toInt()
                "PURPLE" -> 0xFFE1BEE7.toInt()
                "BLUE" -> 0xFF40C4FF.toInt()
                "GREEN" -> 0xFF69F0AE.toInt()
                else -> 0xFF80DEEA.toInt()
            }
            paint.color = color
            paint.alpha = if (score < 0.55f) 150 else 220
            centerPaint.color = color
            centerPaint.alpha = paint.alpha
            canvas.drawCircle(x, y, radius, paint)
            canvas.drawCircle(x, y, 2f, centerPaint)
        }
    }

    companion object {
        private const val MIN_DRAW_CONFIDENCE = 0.35f
    }
}
