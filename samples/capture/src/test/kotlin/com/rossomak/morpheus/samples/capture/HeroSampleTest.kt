package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.isSameMorphFamily
import com.rossomak.morpheus.samples.showcase.heroPills
import com.rossomak.morpheus.samples.showcase.heroRibbons
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HeroSampleTest {
    @Test
    fun `the pills and the ribbons are one morph family, so the hero reshapes in place instead of dissolving`() {
        heroPills.isSameMorphFamily(heroRibbons) shouldBe true
    }
}
