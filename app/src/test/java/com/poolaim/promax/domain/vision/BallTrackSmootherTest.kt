package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BallTrackSmootherTest {
    @Test
    fun nearbyCandidatesKeepTrackIdAndSmoothPosition() {
        val smoother = BallTrackSmoother(maxCenterDisplacement = 30.0)
        val first = smoother.update(listOf(
            BallTrackSmoother.Observation(Point2(100.0, 100.0), 10.0, "RED", 0.8),
        )).single()

        val next = smoother.update(listOf(
            BallTrackSmoother.Observation(Point2(104.0, 100.0), 11.0, "RED", 0.9),
        )).single()

        assertEquals(first.id, next.id)
        assertEquals(2, next.ageFrames)
        assertTrue(next.center.x > 100.0 && next.center.x < 104.0)
        assertEquals(0, next.missedFrames)
    }

    @Test
    fun tracksExpireAfterConfiguredMissedFrames() {
        val smoother = BallTrackSmoother(maxMissedFrames = 1)
        smoother.update(listOf(
            BallTrackSmoother.Observation(Point2(50.0, 50.0), 8.0, "BLUE", 0.7),
        ))
        assertEquals(1, smoother.update(emptyList()).size)
        assertTrue(smoother.update(emptyList()).isEmpty())
    }

    @Test
    fun resetClearsTracksAndRestartsIds() {
        val smoother = BallTrackSmoother()
        smoother.update(listOf(
            BallTrackSmoother.Observation(Point2(10.0, 10.0), 5.0),
        ))
        smoother.reset()
        val newTrack = smoother.update(listOf(
            BallTrackSmoother.Observation(Point2(200.0, 150.0), 5.0),
        )).single()
        assertEquals(1L, newTrack.id)
        assertEquals(1, newTrack.ageFrames)
    }
}
