package com.poolaim.promax.domain.physics.v1

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Deterministic, platform-independent shot geometry in normalized table units.
 * Table bounds are [0,width] x [0,height]. This module is geometry only;
 * scores are not calibrated probabilities of making a shot.
 */
data class Point2(val x: Double, val y: Double) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Point2(x * scale, y * scale)
    fun length(): Double = hypot(x, y)
    fun distanceTo(other: Point2): Double = (this - other).length()
    fun normalized(): Point2 {
        val length = length()
        return if (length <= EPSILON) Point2(0.0, 0.0) else this * (1.0 / length)
    }
    fun dot(other: Point2): Double = x * other.x + y * other.y

    companion object { const val EPSILON = 1e-9 }
}

data class PoolBall(val id: String, val center: Point2, val radius: Double)
data class PoolPocket(val id: String, val center: Point2, val radius: Double)

enum class PathKind { DIRECT, BANK_TOP, BANK_BOTTOM, BANK_LEFT, BANK_RIGHT }

data class PlannedShot(
    val targetBallId: String,
    val pocketId: String,
    val kind: PathKind,
    val cueContactPoint: Point2,
    val bankPoint: Point2?,
    val cuePathLength: Double,
    val objectPathLength: Double,
    val cutAngleDegrees: Double,
    val isCuePathClear: Boolean,
    val isObjectPathClear: Boolean,
    /** Geometric ranking only, not a success probability. */
    val geometryScore: Double,
)

data class TableBounds(val width: Double, val height: Double) {
    init {
        require(width > 0.0 && height > 0.0) { "Table dimensions must be positive" }
    }
}

/**
 * Generates direct and one-cushion bank candidates with ghost-ball geometry.
 * Caller must provide confirmed/calibrated pocket centers and consistent ball radii.
 */
class ShotGeometryEngine {
    fun plan(
        cueBall: PoolBall,
        objectBalls: List<PoolBall>,
        pockets: List<PoolPocket>,
        bounds: TableBounds,
        includeBanks: Boolean = true,
    ): List<PlannedShot> {
        require(cueBall.radius > 0.0) { "Cue ball radius must be positive" }
        val balls = (listOf(cueBall) + objectBalls).distinctBy { it.id }
        if (objectBalls.isEmpty() || pockets.isEmpty()) return emptyList()
        val radius = balls.map { it.radius }.sorted()[balls.size / 2]
        val shots = mutableListOf<PlannedShot>()

        for (target in objectBalls) {
            for (pocket in pockets) {
                addCandidate(
                    shots, cueBall, target, pocket, pocket.center, null,
                    PathKind.DIRECT, balls, radius, bounds,
                )
                if (includeBanks) {
                    // Reflect pocket over each cushion. The ray to the reflected pocket
                    // intersects the cushion at the physical bank point.
                    val reflected = listOf(
                        Triple(Point2(pocket.center.x, -pocket.center.y), PathKind.BANK_TOP, 0),
                        Triple(Point2(pocket.center.x, 2 * bounds.height - pocket.center.y), PathKind.BANK_BOTTOM, 1),
                        Triple(Point2(-pocket.center.x, pocket.center.y), PathKind.BANK_LEFT, 2),
                        Triple(Point2(2 * bounds.width - pocket.center.x, pocket.center.y), PathKind.BANK_RIGHT, 3),
                    )
                    for ((imagePocket, kind, side) in reflected) {
                        val direction = imagePocket - target.center
                        if (direction.length() <= Point2.EPSILON) continue
                        val t = when (side) {
                            0 -> if (direction.y < 0) -target.center.y / direction.y else Double.NaN
                            1 -> if (direction.y > 0) (bounds.height - target.center.y) / direction.y else Double.NaN
                            2 -> if (direction.x < 0) -target.center.x / direction.x else Double.NaN
                            else -> if (direction.x > 0) (bounds.width - target.center.x) / direction.x else Double.NaN
                        }
                        if (!t.isFinite() || t <= 0.0 || t >= 1.0) continue
                        val bank = target.center + direction * t
                        if (bank.x !in 0.0..bounds.width || bank.y !in 0.0..bounds.height) continue
                        addCandidate(
                            shots, cueBall, target, pocket, bank, bank,
                            kind, balls, radius, bounds,
                        )
                    }
                }
            }
        }
        return shots.sortedByDescending { it.geometryScore }
    }

    private fun addCandidate(
        out: MutableList<PlannedShot>,
        cue: PoolBall,
        target: PoolBall,
        pocket: PoolPocket,
        objectAim: Point2,
        bankPoint: Point2?,
        kind: PathKind,
        allBalls: List<PoolBall>,
        radius: Double,
        bounds: TableBounds,
    ) {
        val targetDirection = (objectAim - target.center).normalized()
        if (targetDirection.length() <= Point2.EPSILON) return
        val ghost = target.center - targetDirection * (2.0 * radius)
        if (ghost.x !in radius..(bounds.width - radius) ||
            ghost.y !in radius..(bounds.height - radius)) return

        val cueDirection = (ghost - cue.center).normalized()
        if (cueDirection.length() <= Point2.EPSILON) return
        val angle = Math.toDegrees(acos(cueDirection.dot(targetDirection).coerceIn(-1.0, 1.0)))
        if (angle > 88.0) return

        val blockers = allBalls.filter { it.id != cue.id && it.id != target.id }
        val cueClear = isPathClear(cue.center, ghost, radius, blockers)
        val objectClear = isPathClear(target.center, objectAim, radius, blockers)
        if (!cueClear || !objectClear) return

        val cueLength = cue.center.distanceTo(ghost)
        val objectLength = target.center.distanceTo(objectAim)
        val diagonal = hypot(bounds.width, bounds.height)
        val angleQuality = (1.0 - angle / 90.0).coerceIn(0.0, 1.0)
        val distanceQuality = (1.0 - (cueLength + objectLength) / (2.0 * diagonal)).coerceIn(0.0, 1.0)
        val bankPenalty = if (kind == PathKind.DIRECT) 0.0 else 0.12
        val score = (0.65 * angleQuality + 0.35 * distanceQuality - bankPenalty).coerceIn(0.0, 1.0)

        out += PlannedShot(
            targetBallId = target.id,
            pocketId = pocket.id,
            kind = kind,
            cueContactPoint = ghost,
            bankPoint = bankPoint,
            cuePathLength = cueLength,
            objectPathLength = objectLength,
            cutAngleDegrees = angle,
            isCuePathClear = cueClear,
            isObjectPathClear = objectClear,
            geometryScore = score,
        )
    }

    private fun isPathClear(
        from: Point2,
        to: Point2,
        movingRadius: Double,
        obstacles: List<PoolBall>,
    ): Boolean {
        val segment = to - from
        val lengthSquared = segment.dot(segment)
        if (lengthSquared <= Point2.EPSILON) return true
        return obstacles.none { ball ->
            val offset = ball.center - from
            val t = (offset.dot(segment) / lengthSquared).coerceIn(0.0, 1.0)
            val nearest = from + segment * t
            nearest.distanceTo(ball.center) < movingRadius + ball.radius
        }
    }
}
