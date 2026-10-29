package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Triangle
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * A [Triangle]'s density and closeness, measured on a wide surface so the longer side is the width.
 * The default triangle here has a 400px base and a 300px height, so its apex sits 200px above the
 * centroid, its base 100px below it, and its inradius is about 107px.
 */
class TriangleEnvelopeTest {

    private val envelope = MorpheusGraphEnvelope(seed = 1)

    @Test
    fun `a triangle is dense inside and empty past its rim`() {
        val layer = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f))

        densityAt(layer, CENTER_X, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X + 80f, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y + 110f) shouldBe FLOOR
        densityAt(layer, CENTER_X, CENTER_Y - 210f) shouldBe FLOOR
        densityAt(layer, CENTER_X + 200f, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `the apex points up at rotation 0 and the base is the width`() {
        val layer = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f))

        (densityAt(layer, CENTER_X, CENTER_Y - 180f) > FLOOR) shouldBe true
        densityAt(layer, CENTER_X + 100f, CENTER_Y + 60f) shouldBe 1f
        densityAt(layer, CENTER_X + 150f, CENTER_Y - 90f) shouldBe FLOOR
    }

    @Test
    fun `x and y place the centroid`() {
        val layer = layerOf(Triangle(x = 0.2f, y = 0.6f, cornerRadius = 0f))

        densityAt(layer, 0.2f * WIDTH, 0.6f * HEIGHT) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `rotation turns the triangle clockwise around its centroid`() {
        val upsideDown = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f, rotation = 180f))
        val pointingRight = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f, rotation = 90f))

        (densityAt(upsideDown, CENTER_X, CENTER_Y + 180f) > FLOOR) shouldBe true
        densityAt(upsideDown, CENTER_X, CENTER_Y - 180f) shouldBe FLOOR
        densityAt(pointingRight, CENTER_X + 160f, CENTER_Y) shouldBe 1f
        densityAt(pointingRight, CENTER_X - 160f, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `a corner radius cuts the apex off`() {
        val hard = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f))
        val rounded = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0.05f))

        (densityAt(hard, CENTER_X, CENTER_Y - 195f) > FLOOR) shouldBe true
        densityAt(rounded, CENTER_X, CENTER_Y - 195f) shouldBe FLOOR
        (densityAt(rounded, CENTER_X, CENTER_Y - 150f) > FLOOR) shouldBe true
    }

    @Test
    fun `the corner radius is held to the inradius, which leaves the incircle`() {
        val layer = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 5f))
        val incenterY = CENTER_Y - 7f

        densityAt(layer, CENTER_X, incenterY) shouldBe 1f
        (densityAt(layer, CENTER_X + 100f, incenterY) > FLOOR) shouldBe true
        densityAt(layer, CENTER_X + 112f, incenterY) shouldBe FLOOR
        densityAt(layer, CENTER_X, CENTER_Y - 195f) shouldBe FLOOR
    }

    @Test
    fun `the plateau sets how much of the inradius is held at full density`() {
        val hard = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f, plateau = 0.95f))
        val soft = layerOf(Triangle(width = 0.4f, height = 0.3f, cornerRadius = 0f, plateau = 0f))
        val nearBase = CENTER_Y + 80f

        densityAt(hard, CENTER_X, nearBase) shouldBe 1f
        (densityAt(soft, CENTER_X, nearBase) < 1f) shouldBe true
    }

    @Test
    fun `sizes are held to a minimum and the plateau to a maximum`() {
        val sliver = layerOf(Triangle(width = 0f, height = 0f, cornerRadius = 0f))
        val flat = layerOf(Triangle(plateau = 3f))

        densityAt(sliver, CENTER_X + 20f, CENTER_Y) shouldBe FLOOR
        densityAt(flat, CENTER_X, CENTER_Y) shouldBe 1f
    }

    @Test
    fun `closeness is full at the centroid and falls off with distance from it`() {
        val layer = layerOf(Triangle())

        envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X, CENTER_Y) shouldBe 1f
        val nearby = envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X + 100f, CENTER_Y)
        val farAway = envelope.closenessAt(layer, WIDTH, HEIGHT, 0f, 0f)
        (nearby < 1f) shouldBe true
        (farAway < nearby) shouldBe true
    }

    private fun layerOf(triangle: Triangle) = MorpheusGraphLayerSpec(shapes = listOf(triangle), envelopeFloor = FLOOR)

    private fun densityAt(layer: MorpheusGraphLayerSpec, x: Float, y: Float) = envelope.densityAt(layer, WIDTH, HEIGHT, x, y)

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 500f
        const val CENTER_X = WIDTH / 2f
        const val CENTER_Y = HEIGHT / 2f
        const val FLOOR = 0.03f
    }
}
