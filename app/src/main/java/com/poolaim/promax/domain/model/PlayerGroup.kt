package com.poolaim.promax.domain.model

/**
 * Player ball group for 8-ball rules.
 */
enum class PlayerGroup {
    SOLIDS,    // balls 1–7
    STRIPES,   // balls 9–15
    EIGHT_BALL,
    CUSTOM;

    fun ballNumbers(): List<Int> = when (this) {
        SOLIDS -> (1..7).toList()
        STRIPES -> (9..15).toList()
        EIGHT_BALL -> listOf(8)
        CUSTOM -> emptyList() // user-defined in later phase
    }
}
