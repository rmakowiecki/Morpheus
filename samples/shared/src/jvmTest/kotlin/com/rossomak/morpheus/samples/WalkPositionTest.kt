package com.rossomak.morpheus.samples

import androidx.compose.animation.core.LinearEasing
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private const val LOOP_END = 12.0 - 0.001
private const val TOLERANCE = 1e-6f

class WalkPositionTest {
    // Six legs of 2 s: 0.5 s of hold, a 1 s morph, then 0.5 s of hold.
    private val timing = MorphTiming(hold = 1.seconds, morph = 1.seconds)
    private val easings = List(6) { LinearEasing }
    private val walk = listOf(0, 1, 2, 3, 2, 1, 0)

    private fun positionAt(seconds: Double) = walkPosition(seconds.seconds, walk, timing, easings)

    @Test
    fun `the walk rests on each spec for a hold`() {
        positionAt(0.0) shouldBe 0f
        positionAt(2.0) shouldBe 1f
        positionAt(6.0) shouldBe 3f
        positionAt(8.0) shouldBe 2f
        positionAt(LOOP_END) shouldBe 0f
    }

    @Test
    fun `the walk goes out to the last spec and back`() {
        positionAt(1.0) shouldBe (0.5f plusOrMinus TOLERANCE)
        positionAt(7.0) shouldBe (2.5f plusOrMinus TOLERANCE)
        positionAt(11.0) shouldBe (0.5f plusOrMinus TOLERANCE)
    }

    @Test
    fun `the walk ends where it starts, so the loop has no jump`() {
        positionAt(0.0) shouldBe positionAt(LOOP_END)
    }
}
