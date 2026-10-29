package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Ring
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/** A [Ring]'s density and closeness, measured on a wide surface so the longer side is the width. */
class RingEnvelopeTest {

    private val envelope = MorpheusGraphEnvelope(seed = 1)

    @Test
    fun `a full ring is dense along its curve, hollow inside and empty past its rim`() {
        val layer = layerOf(Ring(radius = 0.25f, thickness = 0.1f, plateau = 0.8f))

        densityAt(layer, CENTER_X + RADIUS, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X - RADIUS, CENTER_Y) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y + RADIUS) shouldBe 1f
        densityAt(layer, CENTER_X, CENTER_Y) shouldBe FLOOR
        densityAt(layer, CENTER_X + RADIUS + 0.06f * WIDTH, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `the plateau holds full density across the band before the soft edge`() {
        val hard = layerOf(Ring(thickness = 0.1f, plateau = 0.8f))
        val soft = layerOf(Ring(thickness = 0.1f, plateau = 0f))
        val halfwayToRim = CENTER_X + RADIUS + 0.025f * WIDTH

        densityAt(hard, halfwayToRim, CENTER_Y) shouldBe 1f
        densityAt(soft, halfwayToRim, CENTER_Y) shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `an arc runs clockwise from its start angle`() {
        val layer = layerOf(Ring(startAngle = 0f, sweep = 90f))

        densityAt(layer, onCurve(45f).first, onCurve(45f).second) shouldBe 1f
        densityAt(layer, onCurve(90f).first, onCurve(90f).second) shouldBe 1f
        densityAt(layer, onCurve(180f).first, onCurve(180f).second) shouldBe FLOOR
        densityAt(layer, onCurve(270f).first, onCurve(270f).second) shouldBe FLOOR
    }

    @Test
    fun `the start angle turns the arc around the center`() {
        val layer = layerOf(Ring(startAngle = 90f, sweep = 90f))

        densityAt(layer, onCurve(135f).first, onCurve(135f).second) shouldBe 1f
        densityAt(layer, onCurve(45f).first, onCurve(45f).second) shouldBe FLOOR
    }

    @Test
    fun `an arc ends in round caps`() {
        val layer = layerOf(Ring(thickness = 0.1f, startAngle = 0f, sweep = 90f))
        val (startX, startY) = onCurve(0f)
        val (endX, endY) = onCurve(90f)

        // Past the start, counter-clockwise from it, and past the end, clockwise from it.
        densityAt(layer, startX, startY - 0.03f * WIDTH) shouldBe 1f
        densityAt(layer, startX, startY - 0.06f * WIDTH) shouldBe FLOOR
        densityAt(layer, endX - 0.03f * WIDTH, endY) shouldBe 1f
        densityAt(layer, endX - 0.06f * WIDTH, endY) shouldBe FLOOR
    }

    @Test
    fun `a sweep of zero leaves a dot at the start angle`() {
        val layer = layerOf(Ring(thickness = 0.1f, startAngle = 0f, sweep = 0f))
        val (dotX, dotY) = onCurve(0f)

        densityAt(layer, dotX, dotY) shouldBe 1f
        densityAt(layer, dotX, dotY + 0.03f * WIDTH) shouldBe 1f
        densityAt(layer, dotX + 0.03f * WIDTH, dotY) shouldBe 1f
        densityAt(layer, dotX, dotY + 0.06f * WIDTH) shouldBe FLOOR
        densityAt(layer, CENTER_X, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `a sweep past a full turn closes the ring and a negative one leaves a dot`() {
        densityAt(layerOf(Ring(sweep = 900f)), onCurve(270f).first, onCurve(270f).second) shouldBe 1f
        densityAt(layerOf(Ring(sweep = -40f)), onCurve(90f).first, onCurve(90f).second) shouldBe FLOOR
    }

    @Test
    fun `the thickness is held to a minimum and the plateau to a maximum`() {
        val thin = layerOf(Ring(thickness = 0f))
        val flat = layerOf(Ring(plateau = 2f))

        densityAt(thin, CENTER_X + RADIUS, CENTER_Y) shouldBe 1f
        densityAt(thin, CENTER_X + RADIUS + 0.007f * WIDTH, CENTER_Y) shouldBe FLOOR
        densityAt(flat, CENTER_X + RADIUS, CENTER_Y) shouldBe 1f
        densityAt(flat, CENTER_X + RADIUS + 0.06f * WIDTH, CENTER_Y) shouldBe FLOOR
    }

    @Test
    fun `closeness is full on the curve and falls off with distance from it`() {
        val layer = layerOf(Ring())

        envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X + RADIUS, CENTER_Y) shouldBe 1f
        val atCenter = envelope.closenessAt(layer, WIDTH, HEIGHT, CENTER_X, CENTER_Y)
        val atCorner = envelope.closenessAt(layer, WIDTH, HEIGHT, 0f, 0f)
        (atCenter < 1f) shouldBe true
        (atCorner < atCenter) shouldBe true
    }

    @Test
    fun `closeness of an arc is measured from the arc, not the whole circle`() {
        val layer = layerOf(Ring(startAngle = 0f, sweep = 90f))
        val onArc = onCurve(45f)
        val onMissingSide = onCurve(225f)

        envelope.closenessAt(layer, WIDTH, HEIGHT, onArc.first, onArc.second) shouldBe 1f
        (envelope.closenessAt(layer, WIDTH, HEIGHT, onMissingSide.first, onMissingSide.second) < 1f) shouldBe true
    }

    private fun onCurve(degrees: Float): Pair<Float, Float> {
        val radians = degrees * RADIANS_PER_DEGREE
        return CENTER_X + RADIUS * cos(radians) to CENTER_Y + RADIUS * sin(radians)
    }

    private fun layerOf(ring: Ring) = MorpheusGraphLayerSpec(shapes = listOf(ring), envelopeFloor = FLOOR)

    private fun densityAt(layer: MorpheusGraphLayerSpec, x: Float, y: Float) = envelope.densityAt(layer, WIDTH, HEIGHT, x, y)

    private companion object {
        const val WIDTH = 1000f
        const val HEIGHT = 500f
        const val CENTER_X = WIDTH / 2f
        const val CENTER_Y = HEIGHT / 2f
        const val RADIUS = 0.25f * WIDTH
        const val FLOOR = 0.03f
        const val RADIANS_PER_DEGREE = 0.017453292f
    }
}
