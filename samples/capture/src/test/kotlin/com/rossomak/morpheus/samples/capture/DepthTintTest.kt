package com.rossomak.morpheus.samples.capture

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.rossomak.morpheus.samples.catalog.depthTintColor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DepthTintTest {
    @Test
    fun `the back layer is the dim blue-grey and the front layer the bright warm color`() {
        val back = depthTintColor(layerIndex = 0, layerCount = 3)
        val front = depthTintColor(layerIndex = 2, layerCount = 3)
        (back.blue > back.red) shouldBe true
        (front.red > front.blue) shouldBe true
        (front.luminance() > back.luminance()) shouldBe true
    }

    @Test
    fun `a layer between the ends is tinted between them`() {
        val middle = depthTintColor(layerIndex = 1, layerCount = 3)
        middle shouldBe lerp(depthTintColor(0, 3), depthTintColor(2, 3), 0.5f)
        middle shouldNotBe depthTintColor(0, 3)
        middle shouldNotBe depthTintColor(2, 3)
    }

    @Test
    fun `a lone layer is the front`() {
        depthTintColor(layerIndex = 0, layerCount = 1) shouldBe depthTintColor(layerIndex = 2, layerCount = 3)
    }

    @Test
    fun `a layer outside the count is refused`() {
        assertFailsWith<IllegalArgumentException> { depthTintColor(layerIndex = 3, layerCount = 3) }
        assertFailsWith<IllegalArgumentException> { depthTintColor(layerIndex = -1, layerCount = 3) }
    }
}
