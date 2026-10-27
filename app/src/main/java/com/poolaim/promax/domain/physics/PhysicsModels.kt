package com.poolaim.promax.domain.physics

import com.poolaim.promax.domain.model.BallModel

/**
 * 2D vector used throughout the shot-calculation engine.
 * All positions are in warped canonical table space (units = pixels of the
 * 800-wide warp; distances therefore carry physical meaning via the
 * standard 2:1 table ratio).
 */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)

    val length: Float get() = kotlin.math.sqrt(x * x + y * y)
    val normalized: Vec2 get() = if (length < 1e-6f) Vec2.ZERO else this / length

    fun dot(o: Vec2) = x * o.x + y * o.y

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}

/**
 * Standard 2:1 pool table geometry in warped canonical space.
 * Width = [playWidth]; height = half of it. Six pockets at standard
 * positions (4 corners + 2 side-middle).
 */
data class TableGeometry(
    val width: Float,
    val height: Float,
) {
    val pockets: List<Vec2> = listOf(
        Vec2(0f, 0f), Vec2(width / 2f, 0f), Vec2(width, 0f),
        Vec2(0f, height), Vec2(width / 2f, height), Vec2(width, height),
    )

    val cornerPocketRadius: Float get() = width * 0.024f
    val sidePocketRadius: Float get() = width * 0.021f

    fun containsPoint(p: Vec2, margin: Float = 0f): Boolean =
        p.x >= margin && p.x <= width - margin &&
            p.y >= margin && p.y <= height - margin
}

/** Type of a candidate shot. */
enum class ShotKind { DIRECT, BANK, KICK }

/**
 * One scored shot option for a single player ball.
 *
 * Geometric fit score (0..1): pure geometry quality (cut angle, pocket
 * distance, obstruction). Difficulty level: human-readable ranking derived
 * from the fit score — NOT a statistical success probability (model is not
 * calibrated with experimental data, per project decision).
 */
data class ShotCandidate(
    val targetBall: BallModel,
    val pocket: Vec2,
    val kind: ShotKind,
    /** Where the cue ball must strike (ghost-ball contact point). */
    val ghostPoint: Vec2,
    /** Cut angle in degrees: 0 = straight shot, 90 = impossible. */
    val cutAngleDeg: Float,
    /** Distance cue -> ghost point. */
    val cueTravel: Float,
    /** Distance target -> pocket. */
    val targetTravel: Float,
    /** 0..1 geometric fit score (higher = better geometry). */
    val fitScore: Float,
    /** Difficulty ranking derived from fitScore. */
    val difficulty: Difficulty,
    /** Balls blocking the cue line (cue -> ghost). */
    val blockersOnCuePath: List<BallModel>,
    /** Balls blocking the target line (target -> pocket). */
    val blockersOnTargetPath: List<BallModel>,
)

enum class Difficulty { EASY, MEDIUM, HARD, VERY_HARD;
    companion object {
        fun fromFit(score: Float): Difficulty = when {
            score >= 0.75f -> EASY
            score >= 0.50f -> MEDIUM
            score >= 0.30f -> HARD
            else -> VERY_HARD
        }
    }
}

/**
 * Complete shot recommendation for the current table state.
 */
data class ShotPlan(
    val cueBall: BallModel,
    val candidates: List<ShotCandidate>,
) {
    val bestShot: ShotCandidate? get() = candidates.maxByOrNull { it.fitScore }
}
