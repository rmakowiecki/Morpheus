package com.rossomak.morpheus

import androidx.compose.ui.geometry.Offset
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Comets carry the nodes only a morph's start shows over to the nodes only its end shows: late in a morph
 * their light sits on the end shape, whatever its primitives, a dissolve's comets land on the other mesh's
 * shape, the same morph in the same direction draws the same heads every time, and every platform works
 * out the same heads and peaks. Each direction is planned from where it starts, so a way back needn't mirror
 * the way there.
 */
class MorpheusGraphCometsTest {

    private val canvas = BenchmarkCanvas.Recipe

    @Test
    fun `late in the showcase's morph, both ways, the comets' light sits on the shape the morph ends on`() {
        for ((from, to) in listOf(heroPills to heroRibbons, heroRibbons to heroPills)) {
            val endShown = frontField(from, to, 1f).shownHomes()
            val late = frontField(from, to, LATE_FRACTION)
            val start = frontField(from, to, 0f)
            val end = frontField(from, to, 1f)
            val lit = (0 until late.nodeCount).filter { node ->
                start.nodePresence(node) < PRESENT && end.nodePresence(node) < PRESENT && late.nodePresence(node) >= PRESENT
            }
            val gaps = lit.map { node -> late.gapTo(endShown, node) }.sorted()

            lit.size shouldBeGreaterThan MIN_LATE_LIT
            gaps[gaps.size / 2] shouldBeLessThan MAX_MEDIAN_GAP_CELLS
            gaps.last() shouldBeLessThan MAX_GAP_CELLS
        }
    }

    @Test
    fun `a dissolve's comets land on the shape of the mesh it dissolves into`() {
        val from = MorpheusGraphPresets.MorphFamily.cornerBloom
        val to = MorpheusGraphPresets.MorphFamily.bottomStartBloom.copy(seed = from.seed + 1)
        val width = TALL_WIDTH
        val height = TALL_HEIGHT
        val endLayer = morphLayers(MorpheusGraphMorph(to, to, 0f), GalleryLoop.FILL).single()
        val endShown = LayerField(endLayer.spec, endLayer.seed, height, PX_PER_DP).also { it.advance(0f, width, height) }.shownHomes()

        val sending = morphLayers(MorpheusGraphMorph(from, to, LATE_FRACTION), GalleryLoop.FILL).map { layer ->
            LayerField(layer.spec, layer.seed, height, PX_PER_DP).also { field ->
                field.shape = layer.shape
                field.advance(0f, width, height)
            }
        }.single { it.cometCount > 0 }
        val lit = (0 until sending.nodeCount).filter { node -> sending.nodeCometPeak(node) > 0f && sending.nodePresence(node) >= PRESENT }
        val gaps = lit.map { node -> sending.gapTo(endShown, node, referenceSize = height) }.sorted()

        lit.size shouldBeGreaterThan 0
        gaps[gaps.size / 2] shouldBeLessThan MAX_MEDIAN_GAP_CELLS
        gaps.last() shouldBeLessThan MAX_GAP_CELLS
    }

    @Test
    fun `the same morph in the same direction draws the same heads every time`() {
        for ((from, to) in listOf(heroPills to heroRibbons, heroRibbons to heroPills)) {
            val first = frontField(from.withCometKnobs(), to.withCometKnobs(), 0.5f)
            val second = frontField(from.withCometKnobs(), to.withCometKnobs(), 0.5f)

            first.cometHeads.size shouldBeGreaterThan 0
            first.cometHeads.map { it.startsAt }.distinct().size shouldBeGreaterThan 1
            second.cometHeads shouldBe first.cometHeads
            (0 until second.nodeCount).map(second::nodeCometPeak) shouldBe (0 until first.nodeCount).map(first::nodeCometPeak)
        }
    }

    @Test
    fun `at full stagger the heads travel in windows spread over the morph, each at least a fifth of it`() {
        val heads = frontField(heroPills.withCometKnobs(stagger = 1f), heroRibbons.withCometKnobs(stagger = 1f), 0.5f).cometHeads
        val starts = heads.map { it.startsAt }

        heads.forEach { head -> head.endsAt - head.startsAt shouldBeGreaterThanOrEqual MIN_FULL_STAGGER_WINDOW }
        heads.forEach { head -> head.endsAt shouldBeLessThanOrEqual 1f }
        starts.min() shouldBeGreaterThanOrEqual 0f
        starts.max() - starts.min() shouldBeGreaterThan MIN_FULL_STAGGER_SPREAD
    }

    @Test
    fun `a head's easing inverts exactly enough to map its light back to the morph's fraction`() {
        for (easing in listOf(0f, 0.3f, 1f)) {
            for (step in 0..EASING_STEPS) {
                val travel = step / EASING_STEPS.toFloat()
                travelUneased(travelEased(travel, easing), easing) shouldBe (travel plusOrMinus EASING_TOLERANCE)
                travelEased(1f - travel, easing) shouldBe ((1f - travelEased(travel, easing)) plusOrMinus EASING_TOLERANCE)
            }
        }
        travelEased(0.25f, 0f) shouldBe 0.25f
    }

    /** The same fingerprint on the JVM and in headless Chrome: no platform's float or hash order leaks in. */
    @Test
    fun `the showcase's comets have the same heads and peaks on every platform`() {
        val field = frontField(heroPills.withCometKnobs(), heroRibbons.withCometKnobs(), 0.5f)
        var fingerprint = 17L
        for (head in field.cometHeads) {
            val values = listOf(head.sourceX, head.sourceY, head.targetX, head.targetY, head.sourceRadius, head.targetRadius, head.startsAt, head.endsAt, head.easing)
            for (value in values) fingerprint = fingerprint * 31 + value.toRawBits()
        }
        for (node in 0 until field.nodeCount) fingerprint = fingerprint * 31 + field.nodeCometPeak(node).toRawBits()

        fingerprint.toULong().toString(16) shouldBe SHOWCASE_FINGERPRINT
    }

    @Test
    fun `comet settings reject values that can't be drawn`() {
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(strength = -0.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(size = -1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(headSpacing = 0f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(headsPerStream = 0) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(spark = -0.5f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(stagger = 1.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(stagger = -0.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(easing = 1.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(easing = -0.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(sizeDeviation = -0.1f) }
    }

    @Test
    fun `heads per stream interpolate only when both ends set them`() {
        lerp(MorpheusGraphComets(headsPerStream = 2), MorpheusGraphComets(headsPerStream = 5), 0.5f).headsPerStream shouldBe 4
        lerp(MorpheusGraphComets(headsPerStream = 2), MorpheusGraphComets(), 0.5f).headsPerStream shouldBe null
    }

    @Test
    fun `the way back from two crossing ribbons sends comets from each of the four arms of the X`() {
        val heads = frontField(heroRibbons, heroPills, 0.5f).cometHeads
        val quadrants = heads.groupingBy { head -> (head.sourceX < canvas.width / 2f) to (head.sourceY < canvas.height / 2f) }.eachCount()

        quadrants.keys shouldHaveSize 4
    }

    @Test
    fun `a long pill splits into pieces, so each end of it streams to the bloom below that end`() {
        val pill = frontSpec(Pill(x = 0.5f, y = 0.25f, length = 0.9f, thickness = 0.12f, rotation = 0f))
        val blooms = frontSpec(Bloom(x = 0.15f, y = 0.8f, reach = 0.12f, plateau = 0.6f), Bloom(x = 0.85f, y = 0.8f, reach = 0.12f, plateau = 0.6f))
        val middle = canvas.width / 2f

        val heads = frontField(pill, blooms, 0.5f).cometHeads
        val toLeft = heads.filter { it.targetX < middle }
        val toRight = heads.filter { it.targetX >= middle }

        toLeft.size shouldBeGreaterThan 0
        toRight.size shouldBeGreaterThan 0
        toLeft.map { it.sourceX }.sorted()[toLeft.size / 2] shouldBeLessThan middle
        toRight.map { it.sourceX }.sorted()[toRight.size / 2] shouldBeGreaterThan middle
    }

    @Test
    fun `every head's disc leaves from inside the start shape and lands inside the end shape`() {
        val pill = frontSpec(Pill(x = 0.5f, y = 0.25f, length = 0.9f, thickness = 0.12f, rotation = 0f))
        val blooms = frontSpec(Bloom(x = 0.15f, y = 0.8f, reach = 0.12f, plateau = 0.6f), Bloom(x = 0.85f, y = 0.8f, reach = 0.12f, plateau = 0.6f))
        for ((from, to) in listOf(heroPills to heroRibbons, heroRibbons to heroPills, pill to blooms)) {
            val start = frontField(from, to, 0f)
            val end = frontField(from, to, 1f)
            val heads = frontField(from, to, 0.5f).cometHeads
            val sourceShares = heads.map { start.shownShareWithin(Offset(it.sourceX, it.sourceY), it.sourceRadius) }.sorted()
            val targetShares = heads.map { end.shownShareWithin(Offset(it.targetX, it.targetY), it.targetRadius) }.sorted()

            heads.size shouldBeGreaterThan 0
            sourceShares[sourceShares.size / 2] shouldBe 1f
            targetShares[targetShares.size / 2] shouldBe 1f
            sourceShares.first() shouldBeGreaterThanOrEqual MIN_DISC_SHOWN_SHARE
            targetShares.first() shouldBeGreaterThanOrEqual MIN_DISC_SHOWN_SHARE
        }
    }

    @Test
    fun `a morph that wants more heads than the cap shares the cap out, at least one head a stream`() {
        // Two blocks of 120 × 120 nodes a unit apart, far enough apart to match up block to block, and no kept nodes.
        val side = CAP_BLOCK_SIDE
        val count = side * side * 2
        val homeX = FloatArray(count) { node -> (node % side + if (node < side * side) 0 else side * 2).toFloat() }
        val homeY = FloatArray(count) { node -> (node / side % side).toFloat() }
        val leaving = BooleanArray(count) { it < side * side }
        val arriving = BooleanArray(count) { it >= side * side }

        val streams = cometStreams(leaving, arriving, BooleanArray(count), homeX, homeY, count, spacing = 1f, comets = MorpheusGraphComets(), seed = 1)

        streams.streamCount shouldBeGreaterThan 0
        streams.heads.size shouldBeLessThanOrEqual MAX_HEADS
        streams.heads.size shouldBeGreaterThanOrEqual streams.streamCount
    }

    private fun frontSpec(vararg shapes: ShapePrimitive) = heroPills.copy(layers = listOf(heroGhost, heroFront.copy(shapes = shapes.toList())))

    /** The share of this field's nodes within [radius] of [center] that it shows; 1 when none lie there. */
    private fun LayerField.shownShareWithin(center: Offset, radius: Float): Float {
        val inside = (0 until nodeCount).filter { (nodeHome(it) - center).getDistance() <= radius }
        return if (inside.isEmpty()) 1f else inside.count { nodePresence(it) >= PRESENT }.toFloat() / inside.size
    }

    /** The showcase's front layer at [fraction] of the morph from [from] to [to], on the recipe's canvas. */
    private fun frontField(from: MorpheusGraphSpec, to: MorpheusGraphSpec, fraction: Float): LayerField {
        val layer = GalleryLoop.morphLayers(MorpheusGraphMorph(from, to, fraction)).first { it.slotFromFront == 0 }
        return LayerField(layer.spec, layer.seed, canvas.referenceSize, canvas.pxPerDp).also { field ->
            field.shape = layer.shape
            field.advance(0f, canvas.width, canvas.height)
        }
    }

    private fun LayerField.shownHomes(): List<Offset> = (0 until nodeCount).filter { nodePresence(it) >= PRESENT }.map(::nodeHome)

    /** How far [node] lies from the nearest of [homes], in this field's cells on a window [referenceSize] long. */
    private fun LayerField.gapTo(homes: List<Offset>, node: Int, referenceSize: Float = canvas.referenceSize): Float =
        homes.minOf { (nodeHome(node) - it).getDistance() } / (referenceSize / spec.density)

    private companion object {
        const val PRESENT = 0.05f
        const val LATE_FRACTION = 0.85f
        const val MIN_LATE_LIT = 20

        /** A showcase ribbon is about 8 cells thick; the stand-in comets this replaced lit patches some 16 cells off it. */
        const val MAX_MEDIAN_GAP_CELLS = 3f
        const val MAX_GAP_CELLS = 12f
        const val TALL_WIDTH = 1080f
        const val TALL_HEIGHT = 1900f
        const val PX_PER_DP = 2.75f
        const val SHOWCASE_FINGERPRINT = "80943241079e2060"

        /** At a stagger of 1 a window lasts 1 − 0.8 of the morph, give or take a float step. */
        const val MIN_FULL_STAGGER_WINDOW = 0.199f
        const val MIN_FULL_STAGGER_SPREAD = 0.4f
        const val EASING_STEPS = 64
        const val EASING_TOLERANCE = 1e-5f

        /** A disc on a shape's soft edge can take in a few nodes the envelope just fades out. */
        const val MIN_DISC_SHOWN_SHARE = 0.7f
        const val CAP_BLOCK_SIDE = 120

        /** `COMET_MAX_HEADS` in MorpheusGraphComets.kt. */
        const val MAX_HEADS = 128
    }
}

/** Every layer's comets with staggered, eased heads of varied sizes, so tests cover the knobs as well as their defaults. */
internal fun MorpheusGraphSpec.withCometKnobs(stagger: Float = 0.5f, easing: Float = 0.5f, sizeDeviation: Float = 0.25f) =
    copy(layers = layers.map { layer -> layer.copy(comets = layer.comets.copy(stagger = stagger, easing = easing, sizeDeviation = sizeDeviation)) })
