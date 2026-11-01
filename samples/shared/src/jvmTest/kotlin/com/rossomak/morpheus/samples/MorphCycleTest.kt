package com.rossomak.morpheus.samples

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MorphCycleTest {
    // A leg takes 3 s: 0.5 s of hold, a 2 s morph, then 0.5 s of hold.
    private val timing = MorphTiming(hold = 1.seconds, morph = 2.seconds)
    private val linear = listOf(LinearEasing, LinearEasing, LinearEasing)

    @Test
    fun `a leg holds half a hold, morphs, then holds half a hold`() {
        cycleLegAt(0.milliseconds, timing, linear) shouldBe CycleLeg(0, 0f)
        cycleLegAt(500.milliseconds, timing, linear) shouldBe CycleLeg(0, 0f)
        cycleLegAt(1500.milliseconds, timing, linear) shouldBe CycleLeg(0, 0.5f)
        cycleLegAt(2500.milliseconds, timing, linear) shouldBe CycleLeg(0, 1f)
        cycleLegAt(2999.milliseconds, timing, linear) shouldBe CycleLeg(0, 1f)
    }

    @Test
    fun `the next leg starts where the cycle crosses a whole leg`() {
        cycleLegAt(3.seconds, timing, linear) shouldBe CycleLeg(1, 0f)
        cycleLegAt(4500.milliseconds, timing, linear) shouldBe CycleLeg(1, 0.5f)
        cycleLegAt(8.seconds, timing, linear).index shouldBe 2
    }

    @Test
    fun `a time past the last leg stays at its end`() {
        cycleLegAt(9.seconds, timing, linear) shouldBe CycleLeg(2, 1f)
    }

    @Test
    fun `the fraction runs through the easing of its own leg`() {
        val squared = Easing { it * it }
        val easings = listOf(LinearEasing, squared, LinearEasing)
        cycleLegAt(1500.milliseconds, timing, easings).fraction shouldBe 0.5f
        cycleLegAt(4500.milliseconds, timing, easings).fraction shouldBe (0.25f plusOrMinus 1e-6f)
    }
}
