package com.poolaim.promax.domain.model

/**
 * A single detected ball on the table.
 */
data class BallModel(
    val number: Int?,          // null for cue ball or unrecognized
    val centerX: Float,        // image pixel space
    val centerY: Float,
    val radius: Float,
    val type: Type,
) {
    enum class Type { CUE, SOLID, STRIPE, EIGHT, UNKNOWN }
}
