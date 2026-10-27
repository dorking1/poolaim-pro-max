package com.poolaim.promax.domain.physics

import com.poolaim.promax.domain.model.BallModel
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shot calculation engine (Phase D).
 *
 * Reuses the classic PoolAim approach preserved from v0.2:
 * - Ghost-ball geometry: the cue must contact the point one ball-radius
 *   beyond the target center, along the target->pocket line.
 * - Cut angle between the cue->ghost line and target->pocket line.
 * - Path obstruction checks against ALL balls (opponent AND own; blocking
 *   own ball still blocks the shot geometrically).
 * - Geometric fit score combining cut angle, distances, and obstruction.
 *   Difficulty labels are derived from the fit score; we deliberately do
 *   NOT report statistical success percentages (uncalibrated model).
 */
class ShotCalculator {

    companion object {
        /** Penalty weight for cut angle in the fit score. */
        private const val W_CUT = 0.45f
        /** Penalty weight for combined travel distance. */
        private const val W_DIST = 0.30f
        /** Penalty weight for obstruction. */
        private const val W_BLOCK = 0.25f

        /** Max sensible cut angle before a direct shot is physically implausible. */
        private const val MAX_CUT_DEG = 80f
    }

    /**
     * Computes all candidate shots for each player ball toward each pocket.
     * Returns them sorted by fit score (best first).
     */
    fun calculateShots(
        cueBall: BallModel,
        playerBalls: List<BallModel>,
        allBalls: List<BallModel>,
        geometry: TableGeometry,
    ): ShotPlan {
        val ballRadius = medianBallRadius(allBalls)

        val candidates = ArrayList<ShotCandidate>()

        for (ball in playerBalls) {
            val ballCenter = Vec2(ball.centerX, ball.centerY)

            for (pocket in geometry.pockets) {
                val targetDir = (pocket - ballCenter).normalized
                val ghost = ballCenter - targetDir * (ballRadius * 2f)

                val cueCenter = Vec2(cueBall.centerX, cueBall.centerY)
                val cueToGhost = ghost - cueCenter

                if (cueToGhost.length < ballRadius) continue // cue too close

                val cutAngle = cutAngleBetween(
                    cueLine = cueToGhost.normalized,
                    targetLine = targetDir,
                )

                if (cutAngle.isNaN()) continue
                if (cutAngle > MAX_CUT_DEG) continue // physically implausible

                // Obstruction checks.
                val cueBlockers = ballsBlockingPath(
                    from = cueCenter,
                    to = ghost,
                    radius = ballRadius,
                    balls = allBalls - cueBall - ball,
                )
                val targetBlockers = ballsBlockingPath(
                    from = ballCenter,
                    to = pocket,
                    radius = ballRadius,
                    balls = allBalls - cueBall - ball,
                )

                val blocked = cueBlockers.isNotEmpty() || targetBlockers.isNotEmpty()

                // Fit score: pure geometry.
                val fit = fitScore(
                    cutAngleDeg = cutAngle,
                    cueTravel = cueToGhost.length,
                    targetTravel = (pocket - ballCenter).length,
                    geometry = geometry,
                    blocked = blocked,
                )

                if (fit <= 0f) continue

                candidates.add(
                    ShotCandidate(
                        targetBall = ball,
                        pocket = pocket,
                        kind = ShotKind.DIRECT,
                        ghostPoint = ghost,
                        cutAngleDeg = cutAngle,
                        cueTravel = cueToGhost.length,
                        targetTravel = (pocket - ballCenter).length,
                        fitScore = fit,
                        difficulty = Difficulty.fromFit(fit),
                        blockersOnCuePath = cueBlockers,
                        blockersOnTargetPath = targetBlockers,
                    ),
                )
            }
        }

        candidates.sortByDescending { it.fitScore }
        return ShotPlan(cueBall = cueBall, candidates = candidates)
    }

    // ---- geometry helpers ----

    /** Cut angle in degrees between the cue direction and the target line. */
    private fun cutAngleBetween(cueLine: Vec2, targetLine: Vec2): Float {
        val dot = cueLine.dot(targetLine).coerceIn(-1f, 1f)
        val rad = acos(dot)
        return Math.toDegrees(rad.toDouble()).toFloat()
    }

    /**
     * Returns balls whose centers come within [radius] of the segment
     * from->to (i.e. they physically block the path of a ball of that radius).
     */
    private fun ballsBlockingPath(
        from: Vec2,
        to: Vec2,
        radius: Float,
        balls: List<BallModel>,
    ): List<BallModel> {
        val segment = to - from
        val segLen = segment.length
        if (segLen < 1e-4f) return emptyList()

        val dir = segment / segLen
        val out = ArrayList<BallModel>()

        for (b in balls) {
            val center = Vec2(b.centerX, b.centerY)
            val rel = center - from
            val t = (rel.dot(dir)).coerceIn(0f, segLen)
            val closest = from + dir * t
            val distToLine = (center - closest).length
            // Blocker must clear TWO radii: its own + traveling ball's.
            if (distToLine < radius * 2f && t in 1e-3f..(segLen - 1e-3f)) {
                out.add(b)
            }
        }
        return out
    }

    /**
     * Geometric fit score 0..1. Higher = better geometry.
     * Factors: cut angle quality, combined travel distance, obstruction.
     */
    private fun fitScore(
        cutAngleDeg: Float,
        cueTravel: Float,
        targetTravel: Float,
        geometry: TableGeometry,
        blocked: Boolean,
    ): Float {
        // 1. Cut angle quality: 0 deg = 1.0, MAX_CUT_DEG+ = 0.
        val cutQuality = (1f - (cutAngleDeg / MAX_CUT_DEG)).coerceIn(0f, 1f)

        // 2. Distance quality: normalize by table diagonal.
        val diagonal = sqrt(
            geometry.width * geometry.width + geometry.height * geometry.height,
        )
        val combined = cueTravel + targetTravel
        val distQuality = (1f - (combined / diagonal).coerceIn(0f, 1f))

        // 3. Obstruction: fully blocked = massive penalty.
        val blockQuality = if (blocked) 0f else 1f

        val score = (W_CUT * cutQuality) + (W_DIST * distQuality) + (W_BLOCK * blockQuality)

        // Blocked shots remain listed but sink below any open shot.
        return if (blocked) score * 0.15f else score
    }

    /** Median radius of all detected balls — robust to detection noise. */
    private fun medianBallRadius(balls: List<BallModel>): Float {
        val radii = balls.map { it.radius }.sorted()
        return if (radii.isEmpty()) 10f else radii[radii.size / 2]
    }

    private operator fun List<BallModel>.minus(exclude: BallModel): List<BallModel> =
        this.filterNot { it === exclude }
}
