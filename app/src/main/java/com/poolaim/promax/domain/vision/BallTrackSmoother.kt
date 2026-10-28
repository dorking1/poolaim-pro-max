package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import kotlin.math.hypot

/**
 * Lightweight temporal stabilizer for frame-by-frame circle proposals.
 *
 * It associates nearby proposals across frames and smooths their centers/radii.
 * Track IDs are temporary visual tracks, not numbered billiard-ball identities.
 * Call reset() when capture stops or the table orientation changes.
 */
class BallTrackSmoother(
    private val maxCenterDisplacement: Double = 34.0,
    private val centerSmoothing: Double = 0.62,
    private val radiusSmoothing: Double = 0.55,
    private val maxMissedFrames: Int = 3,
) {
    data class Observation(
        val center: Point2,
        val radius: Double,
        val colorFamily: String = "UNKNOWN",
        val confidence: Double = 0.0,
    ) {
        init {
            require(radius > 0.0 && radius.isFinite()) { "Radius must be finite and positive" }
            require(center.x.isFinite() && center.y.isFinite()) { "Center must be finite" }
            require(confidence in 0.0..1.0) { "Confidence must be in [0, 1]" }
        }
    }

    data class Track(
        val id: Long,
        val center: Point2,
        val radius: Double,
        val colorFamily: String,
        val confidence: Double,
        val ageFrames: Int,
        val missedFrames: Int,
    )

    private data class MutableTrack(
        var id: Long,
        var center: Point2,
        var radius: Double,
        var colorFamily: String,
        var confidence: Double,
        var ageFrames: Int = 1,
        var missedFrames: Int = 0,
    )

    private val tracks = mutableListOf<MutableTrack>()
    private var nextId = 1L

    init {
        require(maxCenterDisplacement > 0.0 && maxCenterDisplacement.isFinite())
        require(centerSmoothing in 0.0..1.0)
        require(radiusSmoothing in 0.0..1.0)
        require(maxMissedFrames >= 0)
    }

    @Synchronized
    fun update(observations: List<Observation>): List<Track> {
        val unmatched = tracks.indices.toMutableSet()
        val matchedObservations = mutableSetOf<Int>()

        // Greedy nearest-neighbor association; use distance normalized by radius to
        // avoid assigning a distant proposal just because a frame has few candidates.
        val pairs = mutableListOf<Triple<Double, Int, Int>>()
        for (oi in observations.indices) {
            val observation = observations[oi]
            for (ti in tracks.indices) {
                val track = tracks[ti]
                val distance = hypot(
                    observation.center.x - track.center.x,
                    observation.center.y - track.center.y,
                )
                val gate = maxCenterDisplacement + track.missedFrames * maxCenterDisplacement * 0.5
                if (distance <= gate) {
                    val scale = (observation.radius + track.radius).coerceAtLeast(1.0)
                    pairs += Triple(distance / scale, oi, ti)
                }
            }
        }

        for ((_, oi, ti) in pairs.sortedBy { it.first }) {
            if (oi in matchedObservations || ti !in unmatched) continue
            val observation = observations[oi]
            val track = tracks[ti]
            track.center = Point2(
                track.center.x * (1.0 - centerSmoothing) + observation.center.x * centerSmoothing,
                track.center.y * (1.0 - centerSmoothing) + observation.center.y * centerSmoothing,
            )
            track.radius = track.radius * (1.0 - radiusSmoothing) + observation.radius * radiusSmoothing
            if (observation.confidence >= track.confidence || track.colorFamily == "UNKNOWN") {
                track.colorFamily = observation.colorFamily
            }
            track.confidence = track.confidence * 0.35 + observation.confidence * 0.65
            track.ageFrames += 1
            track.missedFrames = 0
            matchedObservations += oi
            unmatched.remove(ti)
        }

        for (ti in unmatched) tracks[ti].missedFrames += 1
        tracks.removeAll { it.missedFrames > maxMissedFrames }

        for (oi in observations.indices) {
            if (oi in matchedObservations) continue
            val o = observations[oi]
            tracks += MutableTrack(
                id = nextId++,
                center = o.center,
                radius = o.radius,
                colorFamily = o.colorFamily,
                confidence = o.confidence,
            )
        }

        return tracks.map {
            Track(it.id, it.center, it.radius, it.colorFamily, it.confidence, it.ageFrames, it.missedFrames)
        }
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        nextId = 1L
    }
}
