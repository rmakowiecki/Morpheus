package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.LinearEasing
import com.rossomak.morpheus.samples.MorphTiming
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/** Just inside the end of the third 2 s leg, where the cycle would wrap. */
private const val LOOP_END = 6.0 - 0.001
private const val TOLERANCE = 1e-3f

class RetargetStepTest {
    // Three legs of 2 s: 0.5 s of hold, a 1 s morph, then 0.5 s of hold.
    private val timing = MorphTiming(hold = 1.seconds, morph = 1.seconds)

    private fun stepAt(seconds: Double) = retargetStepAt(seconds.seconds, timing, LinearEasing)

    @Test
    fun `the first leg morphs from the top right to the middle left`() {
        stepAt(0.0).target shouldBe RetargetTarget.MiddleLeft
        stepAt(0.0).fraction shouldBe 0f
        stepAt(1.0).fraction shouldBe (0.5f plusOrMinus TOLERANCE)
        stepAt(1.0).phase shouldBe RetargetPhase.ToMiddleLeft
        stepAt(1.9).fraction shouldBe 1f
    }

    @Test
    fun `the second leg heads back to the top right, then is cut and heads for the bottom right`() {
        // The leg's morph runs from 2.5 s to 3.5 s and is cut 40% in, at 2.9 s.
        stepAt(2.7).target shouldBe RetargetTarget.TopRight
        stepAt(2.7).fraction shouldBe (0.2f plusOrMinus TOLERANCE)
        stepAt(2.7).phase shouldBe RetargetPhase.BackToTopRight
        stepAt(3.0).target shouldBe RetargetTarget.BottomRight
        stepAt(3.0).phase shouldBe RetargetPhase.Interrupted
    }

    @Test
    fun `the morph toward the bottom right starts from nothing and arrives within the leg`() {
        stepAt(2.9).fraction shouldBe (0f plusOrMinus TOLERANCE)
        stepAt(3.2).fraction shouldBe (0.5f plusOrMinus TOLERANCE)
        stepAt(3.9).fraction shouldBe 1f
    }

    @Test
    fun `the last leg closes the loop from the bottom right to the top right`() {
        stepAt(4.5).target shouldBe RetargetTarget.TopRight
        stepAt(4.5).fraction shouldBe 0f
        stepAt(4.7).phase shouldBe RetargetPhase.Closing
        stepAt(LOOP_END).fraction shouldBe 1f
    }

    @Test
    fun `a phase starts with its morph, so the hold before a morph still belongs to the phase before`() {
        stepAt(0.0).phase shouldBe RetargetPhase.Closing
        stepAt(2.0).phase shouldBe RetargetPhase.ToMiddleLeft
        stepAt(4.0).phase shouldBe RetargetPhase.Interrupted
        stepAt(LOOP_END).phase shouldBe RetargetPhase.Closing
    }
}
