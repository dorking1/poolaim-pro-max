package com.poolaim.promax.domain.model

import android.graphics.RectF

/**
 * Geometric model of the detected pool table within the imported image.
 * Coordinates are in image pixel space.
 */
data class TableModel(
    val bounds: RectF,
    val corners: List<Pair<Float, Float>>,
    val confidence: Float,
    val manuallyCalibrated: Boolean = false,
)
