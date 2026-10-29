package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Rect
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt
import kotlin.test.Test

/** A [Rect]'s density and closeness, measured on a wide surface so the longer side is the width. */
class RectEnvelopeTest {

    private val envelope = MorpheusGraphEnvelope(seed = 1)

    @Test
    fun `a rect is dense inside and empty past its rim`() {
        val layer = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = 0f, plateau = 0.8f))

        densityAt(layer, CENTER_X, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X + 0.17f * WIDTH, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y + 0.08f * WIDTH) shouldBe 1f
        densityAt(layer, CENTER_X + 0.21f * WIDTH, CENTER_Y) shouldBe FLOOR
        densityAt(layer, CENTER_X, CENTER_Y + 0.11f * WIDTH) shouldBe FLOOR
    }

    @Test
    fun `density falls off from the rim in, over the share the plateau leaves`() {
        val layer = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = 0f, plateau = 0.8f))

        // The inradius is 100px and the falloff takes the outer 20px, so 10px in is halfway.
        densityAt(layer, CENTER_X + 0.19f * WIDTH, CENTER_Y) shouldBe (0.5f plusOrMinus 0.01f)
        densityAt(layer, CENTER_X, CENTER_Y + 0.09f * WIDTH) shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `a plateau of zero fades from the rim all the way to the center`() {
        val layer = layerOf(Rect(width = 0.2f, height = 0.2f, cornerRadius = 0f, plateau = 0f))

        densityAt(layer, CENTER_X, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X + 0.05f * WIDTH, CENTER_Y) shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `rotation turns the rect clockwise around its center`() {
        val layer = layerOf(Rect(width = 0.4f, height = 0.05f, cornerRadius = 0f, rotation = 45f))
        val alongLength = 0.15f * WIDTH / sqrt(2f)

        densityAt(layer, CENTER_X + alongLength, CENTER_Y + alongLength) shouldBe 1f
        densityAt(layer, CENTER_X + alongLength, CENTER_Y - alongLength) shouldBe FLOOR
    }

    @Test
    fun `a corner radius rounds the corners off`() {
        val hard = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = 0f))
        val rounded = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = 0.05f))
        val nearCornerX = CENTER_X + 0.195f * WIDTH
        val nearCornerY = CENTER_Y + 0.095f * WIDTH

        (densityAt(hard, nearCornerX, nearCornerY) > FLOOR) shouldBe true
        densityAt(rounded, nearCornerX, nearCornerY) shouldBe FLOOR
        densityAt(rounded, CENTER_X + 0.15f * WIDTH, CENTER_Y) shouldBe 1f
    }

    @Test
    fun `the corner radius is held to half the shorter side, so a square turns into a disc`() {
        val disc = layerOf(Rect(width = 0.2f, height = 0.2f, cornerRadius = 5f, plateau = 0.9f))
        val stadium = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = 5f, plateau = 0.9f))

        densityAt(disc, CENTER_X + 0.09f * WIDTH, CENTER_Y) shouldBe 1f
        densityAt(disc, CENTER_X + 0.09f * WIDTH, CENTER_Y + 0.09f * WIDTH) shouldBe FLOOR
        densityAt(stadium, CENTER_X + 0.15f * WIDTH, CENTER_Y) shouldBe 1f
        densityAt(stadium, CENTER_X + 0.195f * WIDTH, CENTER_Y + 0.095f * WIDTH) shouldBe FLOOR
    }

    @Test
    fun `a negative corner radius is a hard corner`() {
        val layer = layerOf(Rect(width = 0.4f, height = 0.2f, cornerRadius = -1f))

        (densityAt(layer, CENTER_X + 0.195f * WIDTH, CENTER_Y + 0.095f * WIDTH) > FLOOR) shouldBe true
    }

    @Test
    fun `sizes are held to a minimum and the plateau to a maximum`() {
        val thin = layerOf(Rect(width = 0f, height = 0.2f, cornerRadius = 0f))
        val flat = layerOf(Rect(plateau = 3f))

        densityAt(thin, CENTER_X, CENTER_Y) shouldBe 1f
        densityAt(thin, CENTER_X + 0.006f * WIDTH, CENTER_Y) shouldBe FLOOR
        densityAt(flat, CENTER_X, CENTER_Y) shouldBe 1f
    }

    @Test
    fun `a square keeps its proportions on a tall surface too`() {
        val layer = layerOf(Rect(width = 0.2f, height = 0.2f, cornerRadius = 0f))

        envelope.densityAt(layer, 500f, 1000f, 250f + 0.07f * 1000f, 500f) shouldBe 1f
        envelope.densityAt(layer, 500f, 1000f, 250f, 500f + 0.07f * 1000f) shouldBe 1f
        envelope.densityAt(layer, 500f, 1000f, 250f + 0.11f * 1000f, 500f) shouldBe FLOOR
    }

    @Test
    fun `closeness is full at the center and falls off with distance from it`() {
        val layer = layerOf(Rect())

        envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X, CENTER_Y) shouldBe 1f
        val nearby = envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X + 0.1f * WIDTH, CENTER_Y)
        val farAway = envelope.closenessAt(layer, WIDTH, HEIGHT, 0f, 0f)
        (nearby < 1f) shouldBe true
        (farAway < nearby) shouldBe true
    }

    private fun layerOf(rect: Rect) = MorpheusGraphLayerSpec(shapes = listOf(rect), envelopeFloor = FLOOR)

    private fun densityAt(layer: MorpheusGraphLayerSpec, x: Float, y: Float) = envelope.densityAt(layer, WIDTH, HEIGHT, x, y)

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 500f
        const val CENTER_X = WIDTH / 2f
        const val CENTER_Y = HEIGHT / 2f
        const val FLOOR = 0.03f
    }
}
