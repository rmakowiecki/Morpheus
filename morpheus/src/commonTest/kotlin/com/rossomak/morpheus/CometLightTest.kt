package com.rossomak.morpheus

import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test

/**
 * Keeps a comet-lit node's light going on and off gently: each window is narrowed only by its own end of the morph.
 * One shared window once let a node lit just after the morph's start go dark within a few frames mid-morph (#43).
 */
class CometLightTest {

    @Test
    fun `a node lit just after the start still goes dark over a whole window`() {
        cometLight(lightsAt = 0.03f, darkensAt = 0.5f, fraction = 0f) shouldBe 0f
        largestStepBetween(LIGHT_STEADY_FROM, 1f) { cometLight(lightsAt = 0.03f, darkensAt = 0.5f, fraction = it) } shouldBeLessThanOrEqual MAX_STEP
    }

    @Test
    fun `a node going dark just before the end still lights over a whole window`() {
        cometLight(lightsAt = 0.5f, darkensAt = 0.97f, fraction = 1f) shouldBe 0f
        largestStepBetween(0f, 1f - LIGHT_STEADY_FROM) { cometLight(lightsAt = 0.5f, darkensAt = 0.97f, fraction = it) } shouldBeLessThanOrEqual MAX_STEP
    }

    @Test
    fun `a node lit at both ends stays lit`() {
        cometLight(lightsAt = -1f, darkensAt = 2f, fraction = 0f) shouldBe 1f
        cometLight(lightsAt = -1f, darkensAt = 2f, fraction = 1f) shouldBe 1f
    }

    /** The largest change in [light] between two fractions [SHOWCASE_STEP] apart, from [from] to [to]. */
    private fun largestStepBetween(from: Float, to: Float, light: (Float) -> Float): Float {
        var largest = 0f
        var fraction = from
        while (fraction + SHOWCASE_STEP <= to) {
            largest = max(largest, abs(light(fraction + SHOWCASE_STEP) - light(fraction)))
            fraction += SHOWCASE_STEP
        }
        return largest
    }

    private companion object {
        /** The showcase's largest step of the fraction in one frame, at the middle of its eased morph. */
        const val SHOWCASE_STEP = 0.013f

        /** A smoothStep over the full 0.2 window moves at most 1.5 × 0.013 / 0.2 ≈ 0.098 in one such step. */
        const val MAX_STEP = 0.1f

        /** Past the narrowed window at the near end, where the light is steady until the other window. */
        const val LIGHT_STEADY_FROM = 0.1f
    }
}
