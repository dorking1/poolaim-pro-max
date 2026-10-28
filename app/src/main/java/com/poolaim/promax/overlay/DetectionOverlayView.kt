package com.poolaim.promax.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Transparent diagnostic layer for candidate circles; it does not touch the game. */
class DetectionOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        style = Paint.Style.FILL
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var radii = FloatArray(0)
    private var confidence = FloatArray(0)
    private var families = emptyArray<String>()
    private var trackIds = LongArray(0)
    private var trackAges = IntArray(0)
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun updateCandidates(x: FloatArray?, y: FloatArray?, r: FloatArray?,
        confidenceValues: FloatArray?, colorFamilies: Array<String>?, ids: LongArray?, ages: IntArray?,
        frameWidth: Int, frameHeight: Int) {
        xs = x ?: FloatArray(0)
        ys = y ?: FloatArray(0)
        radii = r ?: FloatArray(0)
        confidence = confidenceValues ?: FloatArray(0)
        families = colorFamilies ?: emptyArray()
        trackIds = ids ?: LongArray(0)
        trackAges = ages ?: IntArray(0)
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
            val x = xs[i] * scaleX
            val y = ys[i] * scaleY
            val radius = radii[i] * scaleR
            if (!x.isFinite() || !y.isFinite() || !radius.isFinite() || radius <= 0f) continue
            val score = confidence.getOrNull(i) ?: 0f
            val family = families.getOrNull(i) ?: "UNKNOWN"
            paint.color = if (score < 0.25f) Color.YELLOW else when (family) {
                "WHITE_OR_CUE" -> Color.WHITE
                "YELLOW" -> Color.YELLOW
                "ORANGE" -> 0xFFFF9800.toInt()
                "RED" -> Color.RED
                "PURPLE" -> 0xFFCE93D8.toInt()
                "BLUE" -> 0xFF40C4FF.toInt()
                "GREEN" -> 0xFF69F0AE.toInt()
                else -> Color.CYAN
            }
            canvas.drawCircle(x, y, radius, paint)
            canvas.drawCircle(x, y, 2.5f, paint)
            val familyLabel = if (family == "UNKNOWN") "?" else family.replace("_OR_CUE", "").take(3)
            val id = trackIds.getOrNull(i)?.toString() ?: "?"
            val age = trackAges.getOrNull(i) ?: 1
            canvas.drawText("$id:$familyLabel", x + radius + 3f, y - radius, textPaint)
            if (age > 1) canvas.drawText("stable $age", x + radius + 3f, y + radius + textPaint.textSize, textPaint)
        }
    }
}
