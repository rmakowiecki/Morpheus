package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Pill
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt
import kotlin.test.Test

/** A [Pill]'s density and closeness, measured on a wide surface so the longer side is the width. */
class PillEnvelopeTest {

    private val envelope = MorpheusGraphEnvelope(seed = 1)

    @Test
    fun `a pill is dense along its whole axis and empty past its rim`() {
        val layer = layerOf(Pill(length = 0.5f, thickness = 0.1f, plateau = 0.8f))

        densityAt(layer, CENTER_X, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X + 0.2f * WIDTH, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y + 0.06f * WIDTH) shouldBe FLOOR
        densityAt(layer, CENTER_X + 0.26f * WIDTH, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `rotation turns the pill clockwise around its center`() {
        val layer = layerOf(Pill(length = 0.4f, thickness = 0.05f, rotation = 45f))
        val alongAxis = 0.12f * WIDTH / sqrt(2f)

        densityAt(layer, CENTER_X + alongAxis, CENTER_Y + alongAxis) shouldBe 1f
        densityAt(layer, CENTER_X + alongAxis, CENTER_Y - alongAxis) shouldBe FLOOR
    }

    @Test
    fun `a pill shorter than it is thick is a disc`() {
        val layer = layerOf(Pill(length = 0.05f, thickness = 0.2f, plateau = 0.9f))

        densityAt(layer, CENTER_X + 0.08f * WIDTH, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y + 0.08f * WIDTH) shouldBe 1f
        densityAt(layer, CENTER_X + 0.11f * WIDTH, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `closeness is full on the axis and falls off with distance from it`() {
        val layer = layerOf(Pill(length = 0.5f, thickness = 0.1f))

        envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X + 0.1f * WIDTH, CENTER_Y) shouldBe 1f
        val offAxis = envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X, 0f)
        val fartherOff = envelope.closenessAt(layer, WIDTH, HEIGHT, 0f, 0f)
        (offAxis > fartherOff) shouldBe true
    }

    private fun layerOf(pill: Pill) = MorpheusGraphLayerSpec(shapes = listOf(pill), envelopeFloor = FLOOR)

    private fun densityAt(layer: MorpheusGraphLayerSpec, x: Float, y: Float) = envelope.densityAt(layer, WIDTH, HEIGHT, x, y)

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 500f
        const val CENTER_X = WIDTH / 2f
        const val CENTER_Y = HEIGHT / 2f
        const val FLOOR = 0.03f
    }
}
