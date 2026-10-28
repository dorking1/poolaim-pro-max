package com.poolaim.promax.overlay

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * Non-interactive, transparent overlay surface.
 *
 * Raw vision proposals are intentionally not painted on the live table: they are
 * not yet reliable shot solutions and drawing them creates distracting dots.
 * The full-screen view remains NOT_TOUCHABLE in FloatingAimService, so game
 * input passes through. Draw validated cue/target trajectories here only after
 * the shot planner provides a confidence-gated solution.
 */
class DetectionOverlayView(context: Context) : View(context) {
    // Keep the surface transparent and visually empty until a validated shot
    // path is available. Candidate detections are diagnostics, not guidance.
    fun updateCandidates(
        x: FloatArray?, y: FloatArray?, r: FloatArray?,
        confidenceValues: FloatArray?, colorFamilies: Array<String>?,
        ids: LongArray?, ages: IntArray?,
        frameWidth: Int, frameHeight: Int,
    ) {
        // Intentionally do not render raw detections over gameplay.
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Transparent by design: never obscure the table with unverified points.
    }
}
