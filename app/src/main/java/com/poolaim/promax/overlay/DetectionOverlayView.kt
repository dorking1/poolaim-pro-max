package com.poolaim.promax.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Transparent, non-touchable visualization of candidate ball circles. */
class DetectionOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(85, 255, 140)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var radii = FloatArray(0)
    private var sourceWidth = 1
    private var sourceHeight = 1

    fun updateCandidates(x: FloatArray?, y: FloatArray?, r: FloatArray?, frameWidth: Int, frameHeight: Int) {
        xs = x ?: FloatArray(0)
        ys = y ?: FloatArray(0)
        radii = r ?: FloatArray(0)
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
            if (x.isFinite() && y.isFinite() && radius.isFinite() && radius > 0f) {
                canvas.drawCircle(x, y, radius, paint)
                canvas.drawCircle(x, y, 2.5f, paint)
            }
        }
    }
}
