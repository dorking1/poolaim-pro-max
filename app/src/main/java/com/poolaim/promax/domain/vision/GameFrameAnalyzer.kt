package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Composes circle proposals, HSV color estimates, and temporal stabilization.
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
        val proposals = circleProposer.detect(frameBgr)
        val hsv = Mat()
        Imgproc.cvtColor(frameBgr, hsv, Imgproc.COLOR_BGR2HSV)
        val rawCandidates = try {
            proposals.mapNotNull { proposal ->
                val tableCenter = SuppliedGameTableProfile.toTableCoordinates(
                    Point2(proposal.centerX, proposal.centerY), width, height,
                )
                if (tableCenter.x !in 0.0..SuppliedGameTableProfile.bounds.width ||
                    tableCenter.y !in 0.0..SuppliedGameTableProfile.bounds.height
                ) return@mapNotNull null

                val color = colorHeuristic.estimateHsv(hsv, proposal.centerX, proposal.centerY, proposal.radius)
                DetectedBallCandidate(
                    centerScreen = Point2(proposal.centerX, proposal.centerY),
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
        val candidates = tracks.mapNotNull { track ->
            val nearest = rawCandidates.minByOrNull {
                hypot(it.centerScreen.x - track.center.x, it.centerScreen.y - track.center.y)
            } ?: return@mapNotNull null
            // Keep the stable, smoothed track center but preserve current frame's color estimate.
            val smoothedScreen = track.center
            val tableCenter = SuppliedGameTableProfile.toTableCoordinates(smoothedScreen, width, height)
            nearest.copy(
                centerScreen = smoothedScreen,
                centerTable = tableCenter,
                radiusScreenPixels = track.radius,
                trackId = track.id,
                ageFrames = track.ageFrames,
            )
        }.distinctBy { it.trackId }

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
