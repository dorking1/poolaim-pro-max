package com.poolaim.promax.domain.vision

import com.poolaim.promax.domain.physics.v1.Point2
import com.poolaim.promax.domain.physics.v1.PoolPocket
import com.poolaim.promax.domain.physics.v1.TableBounds

/**
 * Reference profile for the user's supplied 8-ball game screenshots.
 *
 * The reference coordinates are estimates, not a substitute for runtime
 * calibration. Coordinates are scaled from the 1536x720 reference frame so
 * screenshots captured at a different resolution can use the same profile.
 */
object SuppliedGameTableProfile {
    const val REFERENCE_WIDTH = 1536.0
    const val REFERENCE_HEIGHT = 720.0

    const val CLOTH_LEFT = 304.0
    const val CLOTH_TOP = 161.0
    const val CLOTH_RIGHT = 1232.0
    const val CLOTH_BOTTOM = 625.0

    val bounds = TableBounds(CLOTH_RIGHT - CLOTH_LEFT, CLOTH_BOTTOM - CLOTH_TOP)

    /**
     * Converts screen pixels into table-local pixels. This scales for a
     * different screenshot resolution, but assumes the game layout preserves
     * the reference aspect ratio and has no extra crop/letterboxing.
     */
    fun toTableCoordinates(screenPoint: Point2, frameWidth: Int, frameHeight: Int): Point2 {
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive" }
        val referenceX = screenPoint.x * REFERENCE_WIDTH / frameWidth
        val referenceY = screenPoint.y * REFERENCE_HEIGHT / frameHeight
        return Point2(referenceX - CLOTH_LEFT, referenceY - CLOTH_TOP)
    }

    /** Converts table-local coordinates to screen pixels for the given frame. */
    fun toScreenCoordinates(tablePoint: Point2, frameWidth: Int, frameHeight: Int): Point2 {
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive" }
        val referenceX = tablePoint.x + CLOTH_LEFT
        val referenceY = tablePoint.y + CLOTH_TOP
        return Point2(
            referenceX * frameWidth / REFERENCE_WIDTH,
            referenceY * frameHeight / REFERENCE_HEIGHT,
        )
    }

    /** Pocket centers in table-local coordinates; refine after visual calibration. */
    val pockets: List<PoolPocket> = listOf(
        PoolPocket("top_left", Point2(0.0, 0.0), 24.0),
        PoolPocket("top_middle", Point2(bounds.width / 2.0, -12.0), 22.0),
        PoolPocket("top_right", Point2(bounds.width, 0.0), 24.0),
        PoolPocket("bottom_right", Point2(bounds.width, bounds.height), 24.0),
        PoolPocket("bottom_middle", Point2(bounds.width / 2.0, bounds.height + 12.0), 22.0),
        PoolPocket("bottom_left", Point2(0.0, bounds.height), 24.0),
    )
}
