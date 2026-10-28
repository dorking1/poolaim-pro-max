package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Composes circle proposals, HSV color estimates, table calibration, and temporal stabilization.
 * The caller owns the input Mat and must initialize OpenCV.
 * Results remain visual candidates, not guaranteed ground-truth detections.
 */
class GameFrameAnalyzer(
    private val circleProposer: GameBallCircleProposer = GameBallCircleProposer(),
    private val colorHeuristic: BallColorHeuristic = BallColorHeuristic(),
    private val trackSmoother: BallTrackSmoother = BallTrackSmoother(),
) {
    data class DetectedBallCandidate(
        val centerScreen: Point2,
        val centerTable: Point2,
        val radiusScreenPixels: Double,
        val color: BallColorHeuristic.Estimate,
        val trackId: Long = -1L,
        val ageFrames: Int = 1,
    )

    data class FrameAnalysis(
        val frameWidth: Int,
        val frameHeight: Int,
        val candidates: List<DetectedBallCandidate>,
        val isReferenceAspectRatio: Boolean,
    )

    /** Reset temporal identities when capture stops or a new game/table begins. */
    fun resetTracking() = trackSmoother.reset()

    fun analyze(frameBgr: Mat): FrameAnalysis {
        require(!frameBgr.empty()) { "Input frame must not be empty" }
        require(frameBgr.channels() == 3) { "Expected a BGR screenshot frame" }

        val width = frameBgr.cols()
        val height = frameBgr.rows()
        val calibration = TableCalibration.fromReferenceFrame(width, height)
        val proposals = circleProposer.detect(frameBgr)
        val hsv = Mat()
        Imgproc.cvtColor(frameBgr, hsv, Imgproc.COLOR_BGR2HSV)
        val rawCandidates = try {
            proposals.mapNotNull { proposal ->
                val screenCenter = Point2(proposal.centerX, proposal.centerY)
                val normalized = calibration.screenToTable(screenCenter)
                if (normalized.x !in 0.0..1.0 || normalized.y !in 0.0..1.0) {
                    return@mapNotNull null
                }

                val tableCenter = Point2(
                    normalized.x * calibration.clothWidth,
                    normalized.y * calibration.clothHeight,
                )
                val color = colorHeuristic.estimateHsv(
                    hsv, proposal.centerX, proposal.centerY, proposal.radius,
                )
                DetectedBallCandidate(
                    centerScreen = screenCenter,
                    centerTable = tableCenter,
                    radiusScreenPixels = proposal.radius,
                    color = color,
                )
            }
        } finally {
            hsv.release()
        }

        val tracks = trackSmoother.update(rawCandidates.map {
            BallTrackSmoother.Observation(
                center = it.centerScreen,
                radius = it.radiusScreenPixels,
                colorFamily = it.color.family.name,
                confidence = it.color.confidence.coerceIn(0.0, 1.0),
            )
        })

        // Associate only tracks observed in this frame. A one-to-one gated match
        // prevents several tracks from inheriting the same nearest raw candidate.
        val availableRaw = rawCandidates.indices.toMutableSet()
        val currentTracks = tracks.filter { it.missedFrames == 0 }
        val matches = currentTracks.flatMap { track ->
            rawCandidates.indices.mapNotNull { rawIndex ->
                val candidate = rawCandidates[rawIndex]
                val distance = hypot(
                    candidate.centerScreen.x - track.center.x,
                    candidate.centerScreen.y - track.center.y,
                )
                val gate = maxOf(width.toDouble() / SuppliedGameTableProfile.REFERENCE_WIDTH * 34.0,
                    track.radius * 1.5)
                if (distance <= gate) Triple(distance / track.radius.coerceAtLeast(1.0), track, rawIndex)
                else null
            }
        }.sortedBy { it.first }

        val trackToRaw = linkedMapOf<Long, Int>()
        val usedTracks = mutableSetOf<Long>()
        for ((_, track, rawIndex) in matches) {
            if (track.id in usedTracks || rawIndex !in availableRaw) continue
            trackToRaw[track.id] = rawIndex
            usedTracks += track.id
            availableRaw.remove(rawIndex)
        }

        val candidates = currentTracks.mapNotNull { track ->
            val rawIndex = trackToRaw[track.id] ?: return@mapNotNull null
            val raw = rawCandidates[rawIndex]
            val normalized = calibration.screenToTable(track.center)
            if (normalized.x !in 0.0..1.0 || normalized.y !in 0.0..1.0) return@mapNotNull null
            raw.copy(
                centerScreen = track.center,
                centerTable = Point2(
                    normalized.x * calibration.clothWidth,
                    normalized.y * calibration.clothHeight,
                ),
                radiusScreenPixels = track.radius,
                trackId = track.id,
                ageFrames = track.ageFrames,
            )
        }

        val frameAspect = width.toDouble() / height.toDouble()
        val referenceAspect = SuppliedGameTableProfile.REFERENCE_WIDTH / SuppliedGameTableProfile.REFERENCE_HEIGHT
        return FrameAnalysis(
            frameWidth = width,
            frameHeight = height,
            candidates = candidates,
            isReferenceAspectRatio = abs(frameAspect - referenceAspect) < 0.015,
        )
    }
}
