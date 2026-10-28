package com.poolaim.promax.domain.vision

import org.opencv.core.Mat
import com.poolaim.promax.domain.physics.v1.Point2

/**
 * Composes circle proposals and the HSV color heuristic into one frame result.
 *
 * The caller owns the input Mat and is responsible for initializing OpenCV.
 * Results are proposals, not ground-truth detections: verify them visually
 * before using them to drive aim calculations.
 */
class GameFrameAnalyzer(
    private val circleProposer: GameBallCircleProposer = GameBallCircleProposer(),
    private val colorHeuristic: BallColorHeuristic = BallColorHeuristic(),
) {
    data class DetectedBallCandidate(
        val centerScreen: Point2,
        val centerTable: Point2,
        val radiusScreenPixels: Double,
        val color: BallColorHeuristic.Estimate,
    )

    data class FrameAnalysis(
        val frameWidth: Int,
        val frameHeight: Int,
        val candidates: List<DetectedBallCandidate>,
        val isReferenceAspectRatio: Boolean,
    )

    fun analyze(frameBgr: Mat): FrameAnalysis {
        require(!frameBgr.empty()) { "Input frame must not be empty" }
        require(frameBgr.channels() == 3) { "Expected a BGR screenshot frame" }

        val width = frameBgr.cols()
        val height = frameBgr.rows()
        val proposals = circleProposer.detect(frameBgr)
        val candidates = proposals.mapNotNull { proposal ->
            val tableCenter = SuppliedGameTableProfile.toTableCoordinates(
                Point2(proposal.centerX, proposal.centerY),
                width,
                height,
            )
            // Reject proposals that fall outside the calibrated cloth after conversion.
            if (tableCenter.x !in 0.0..SuppliedGameTableProfile.bounds.width ||
                tableCenter.y !in 0.0..SuppliedGameTableProfile.bounds.height
            ) return@mapNotNull null

            val color = colorHeuristic.estimate(
                frameBgr,
                proposal.centerX,
                proposal.centerY,
                proposal.radius,
            )
            DetectedBallCandidate(
                centerScreen = Point2(proposal.centerX, proposal.centerY),
                centerTable = tableCenter,
                radiusScreenPixels = proposal.radius,
                color = color,
            )
        }

        val frameAspect = width.toDouble() / height.toDouble()
        val referenceAspect =
            SuppliedGameTableProfile.REFERENCE_WIDTH / SuppliedGameTableProfile.REFERENCE_HEIGHT

        return FrameAnalysis(
            frameWidth = width,
            frameHeight = height,
            candidates = candidates,
            isReferenceAspectRatio = kotlin.math.abs(frameAspect - referenceAspect) < 0.015,
        )
    }
}
