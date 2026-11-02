@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
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

    /** At a gap of -1 both sweeps span the whole morph, so the comets still fly at [LATE_FRACTION]. */
    @Test
    fun `late in the showcase's morph, both ways, the comets' light sits on the shape the morph ends on`() {
        val pills = heroPills.withDefaultTiming().withMorphGap(-1f)
        val ribbons = heroRibbons.withDefaultTiming().withMorphGap(-1f)
        for ((from, to) in listOf(pills to ribbons, ribbons to pills)) {
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

    /** At the default gap of 0 the leaving sweep ends and the arriving one starts half way, the hand-over. */
    @Test
    fun `at full stagger the heads travel in windows spread over the comet zones either side of the hand-over, each a fifth of them`() {
        val heads = frontField(heroPills.withDefaultTiming().withCometKnobs(stagger = 1f), heroRibbons.withDefaultTiming().withCometKnobs(stagger = 1f), 0.5f).cometHeads
        val zone = MorpheusGraphComets().zone
        val window = 2f * zone
        val starts = heads.map { it.startsAt }

        heads.forEach { head -> head.endsAt - head.startsAt shouldBeGreaterThanOrEqual FULL_STAGGER_WINDOW_SHARE * window - FLOAT_STEP }
        heads.forEach { head -> head.startsAt shouldBeGreaterThanOrEqual 0.5f - zone - FLOAT_STEP }
        heads.forEach { head -> head.endsAt shouldBeLessThanOrEqual 0.5f + zone + FLOAT_STEP }
        starts.max() - starts.min() shouldBeGreaterThan MIN_FULL_STAGGER_SPREAD * (1f - FULL_STAGGER_WINDOW_SHARE) * window
    }

    @Test
    fun `a larger comet lead sets each synced head off that much earlier and lands it that much later`() {
        val onTime = frontField(heroPills.withCometLead(0f), heroRibbons.withCometLead(0f), 0.5f).cometHeads
        val early = frontField(heroPills.withCometLead(TEST_LEAD), heroRibbons.withCometLead(TEST_LEAD), 0.5f).cometHeads

        onTime.size shouldBeGreaterThan 0
        early.map { it.anchors } shouldBe onTime.map { it.anchors }
        early.zip(onTime).forEach { (head, onTimeHead) ->
            head.startsAt shouldBe ((onTimeHead.startsAt - TEST_LEAD).coerceAtLeast(0f) plusOrMinus FLOAT_STEP)
            head.endsAt shouldBe ((onTimeHead.endsAt + TEST_LEAD).coerceAtMost(1f) plusOrMinus FLOAT_STEP)
        }
    }

    /** The zone stretches with the sweeps, so it picks the same cells of each shape at any gap. */
    @Test
    fun `a relay's comets leave from and land on the same places whatever its gap`() {
        val anchors = listOf(-1f, -0.5f, 0f, 0.2f).map { gap ->
            frontField(heroPills.withMorphGap(gap), heroRibbons.withMorphGap(gap), 0.5f).cometHeads.map { it.anchors }
        }

        anchors.first().size shouldBeGreaterThan 0
        anchors.forEach { it shouldBe anchors.first() }
    }

    /** At a gap of -1 the sweeps and the zone's window last twice as long as at 0, so every head flies at half the pace. */
    @Test
    fun `a comet's tail is as long on screen whatever its head's pace`() {
        val from = heroPills.withDefaultTiming().withCometKnobs(stagger = 0.5f, easing = 0f)
        val to = heroRibbons.withDefaultTiming().withCometKnobs(stagger = 0.5f, easing = 0f)
        val brisk = frontField(from.withMorphGap(0f), to.withMorphGap(0f), 0.5f)
        val slow = frontField(from.withMorphGap(-1f), to.withMorphGap(-1f), 0.5f)
        val lit = (0 until brisk.nodeCount).filter { brisk.nodeCometDarkFade(it) > 0f }

        lit.size shouldBeGreaterThan 0
        slow.cometHeads.map { it.anchors } shouldBe brisk.cometHeads.map { it.anchors }
        lit.forEach { node -> slow.nodeCometDarkFade(node) shouldBe (2f * brisk.nodeCometDarkFade(node) plusOrMinus FLOAT_STEP) }
    }

    @Test
    fun `only a relay's comet zones, the cells of each shape nearest the other, send and take heads`() {
        val blocks = RelayBlocks()
        val streams = blocks.streams()
        val emitters = streams.emitters!!
        val receivers = streams.receivers!!

        streams.heads.size shouldBeGreaterThan 0
        emitters.count { it } shouldBeLessThan blocks.left.count { it } / 2
        receivers.count { it } shouldBeLessThan blocks.right.count { it } / 2
        streams.heads.forEach { head ->
            emitters[blocks.nodeAt(head.sourceX, head.sourceY)] shouldBe true
            receivers[blocks.nodeAt(head.targetX, head.targetY)] shouldBe true
        }
    }

    @Test
    fun `a hand-over centres each synced head's slices' fades half a fade and its lead inside its flight`() {
        val blocks = RelayBlocks()
        val streams = blocks.streams()
        val handedOver = blocks.switches.copyOf().also(streams::handOver)
        val reach = MORPH_FADE_WINDOW / 2f + MorpheusGraphComets().lead
        val departures = streams.heads.map { it.startsAt + reach }
        val landings = streams.heads.map { it.endsAt - reach }
        val moved = (0 until blocks.count).filter { handedOver[it] != blocks.switches[it] }

        streams.isSynced shouldBe true
        moved.size shouldBeGreaterThan 0
        moved.forEach { node ->
            val centre = switchCentre(handedOver[node])
            if (blocks.left[node]) {
                streams.emitters!![node] shouldBe true
                departures.minOf { abs(it - centre) } shouldBeLessThan FLOAT_STEP
            } else {
                streams.receivers!![node] shouldBe true
                landings.minOf { abs(it - centre) } shouldBeLessThan FLOAT_STEP
            }
        }
    }

    @Test
    fun `staggered heads run free of their slices and hand nothing over`() {
        val blocks = RelayBlocks()
        val streams = blocks.streams(MorpheusGraphComets(stagger = 0.5f))

        streams.heads.size shouldBeGreaterThan 0
        streams.isSynced shouldBe false
        blocks.switches.copyOf().also(streams::handOver).toList() shouldBe blocks.switches.toList()
    }

    @Test
    fun `a relay played backwards sends each synced head back along its path, over the mirrored window`() {
        val forward = RelayBlocks().streams().heads
        val backward = RelayBlocks(reversed = true).streams().heads.map { head ->
            head.copy(
                sourceX = head.targetX,
                sourceY = head.targetY,
                targetX = head.sourceX,
                targetY = head.sourceY,
                sourceRadius = head.targetRadius,
                targetRadius = head.sourceRadius,
                startsAt = 1f - head.endsAt,
                endsAt = 1f - head.startsAt,
                sourceDensity = head.targetDensity,
                targetDensity = head.sourceDensity,
            )
        }

        forward.size shouldBeGreaterThan 0
        backward.map { it.anchors }.sortedWith(anchorOrder) shouldBe forward.map { it.anchors }.sortedWith(anchorOrder)
        val backwardByAnchors = backward.associateBy { it.anchors }
        forward.forEach { head ->
            val mirrored = backwardByAnchors.getValue(head.anchors)
            mirrored.startsAt shouldBe (head.startsAt plusOrMinus FLOAT_STEP)
            mirrored.endsAt shouldBe (head.endsAt plusOrMinus FLOAT_STEP)
            mirrored.sourceRadius shouldBe (head.sourceRadius plusOrMinus FLOAT_STEP)
            mirrored.targetRadius shouldBe (head.targetRadius plusOrMinus FLOAT_STEP)
        }
    }

    /**
     * Two square blocks of nodes a unit apart, with a block's width between them, in a relay at a gap of 0 that sweeps
     * from the left block's far side to the right block's: the left block leaves and the right one arrives, or the
     * other way round, and the switches mirrored, when [reversed].
     */
    private class RelayBlocks(reversed: Boolean = false) {
        private val side = RELAY_BLOCK_SIDE
        val count = side * side * 2
        val homeX = FloatArray(count) { node -> (node % side + if (node < side * side) 0 else side * 2).toFloat() }
        val homeY = FloatArray(count) { node -> (node / side % side).toFloat() }
        val left = BooleanArray(count) { it < side * side }
        val right = BooleanArray(count) { it >= side * side }
        val switches = FloatArray(count) { node ->
            val across = (node % side) / (side - 1f)
            val forward = if (left[node]) across / 2f else 0.5f + across / 2f
            if (reversed) 1f - forward else forward
        }
        private val leaving = if (reversed) right else left
        private val arriving = if (reversed) left else right

        fun streams(comets: MorpheusGraphComets = MorpheusGraphComets()): CometStreams =
            cometStreams(leaving, arriving, BooleanArray(count), homeX, homeY, count, spacing = 1f, comets = comets, seed = 1, switches = switches)

        fun nodeAt(x: Float, y: Float): Int = (0 until count).single { homeX[it] == x && homeY[it] == y }
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
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(pieceSize = 0f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(zone = 1.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(zone = -0.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(tail = -0.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(lead = 1.1f) }
        assertFailsWith<IllegalArgumentException> { MorpheusGraphComets(lead = -1.1f) }
    }

    @Test
    fun `heads per stream interpolate only when both ends set them`() {
        lerp(MorpheusGraphComets(headsPerStream = 2), MorpheusGraphComets(headsPerStream = 5), 0.5f).headsPerStream shouldBe 4
        lerp(MorpheusGraphComets(headsPerStream = 2), MorpheusGraphComets(), 0.5f).headsPerStream shouldBe null
    }

    /** The X is one connected piece, so its comet zone is the cells nearest the pills, which needn't reach all four arms. */
    @Test
    fun `the way back from two crossing ribbons sends comets from both ribbons`() {
        val heads = frontField(heroRibbons, heroPills, 0.5f).cometHeads
        val ribbons = heads.map { head -> (head.sourceX - canvas.width / 2f) * (head.sourceY - canvas.height / 2f) > 0f }.distinct()

        ribbons shouldHaveSize 2
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
            sourceShares[sourceShares.size / 2] shouldBeGreaterThanOrEqual MIN_MEDIAN_DISC_SHOWN_SHARE
            targetShares[targetShares.size / 2] shouldBeGreaterThanOrEqual MIN_MEDIAN_DISC_SHOWN_SHARE
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

    @Test
    fun `a morph drawn without comet overlays never builds the data only the overlays read`() {
        val field = frontField(heroPills, heroRibbons, 0.5f)
        val stats = MorpheusGraphFrameStats()

        field.drawOnce(stats, MorpheusGraphCometDebug.None)
        field.isCometDebugPlanBuilt shouldBe false
        field.drawOnce(stats, MorpheusGraphCometDebug(heads = true, paths = true, targets = true, endDiscs = true, clearance = true))
        field.isCometDebugPlanBuilt shouldBe false
        field.drawOnce(stats, MorpheusGraphCometDebug(pieces = true))
        field.isCometDebugPlanBuilt shouldBe true
    }

    /** Draws this field once on the recipe's canvas, timed through [stats]' probe as the gallery draws it. */
    private fun LayerField.drawOnce(stats: MorpheusGraphFrameStats, cometDebug: MorpheusGraphCometDebug) {
        val bitmap = ImageBitmap(canvas.width.toInt(), canvas.height.toInt())
        CanvasDrawScope().draw(Density(canvas.pxPerDp), LayoutDirection.Ltr, Canvas(bitmap), Size(canvas.width, canvas.height)) {
            stats.probe.startLayer(0, this@drawOnce)
            draw(this, LayerFill.Solid(Color.White), HaloBrushCache(Color.White), 0f, showViolations = false, probe = stats.probe, cometDebug = cometDebug)
            stats.probe.endLayer(this@drawOnce)
        }
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

        /** Late enough that the heads are nearly home, early enough that, at the default gap, none has landed yet. */
        const val LATE_FRACTION = 0.75f

        /** Enough comet-only nodes to take a median gap over; near home a head mostly lights the end shape's own nodes. */
        const val MIN_LATE_LIT = 10

        /** A showcase ribbon is about 8 cells thick; the stand-in comets this replaced lit patches some 16 cells off it. */
        const val MAX_MEDIAN_GAP_CELLS = 3f
        const val MAX_GAP_CELLS = 12f
        const val TALL_WIDTH = 1080f
        const val TALL_HEIGHT = 1900f
        const val PX_PER_DP = 2.75f
        const val SHOWCASE_FINGERPRINT = "80943241079e2060"

        /** At a stagger of 1 a head's window lasts 1 − 0.8 of the zones' window. */
        const val FULL_STAGGER_WINDOW_SHARE = 0.2f
        const val FLOAT_STEP = 1e-3f
        const val TEST_LEAD = 0.05f

        /** The least spread of the heads' start fractions, as a share of the room they can start in. */
        const val MIN_FULL_STAGGER_SPREAD = 0.4f
        const val EASING_STEPS = 64
        const val EASING_TOLERANCE = 1e-5f

        /** A disc on a shape's soft edge can take in a few nodes the envelope just fades out. */
        const val MIN_DISC_SHOWN_SHARE = 0.7f

        /** A comet zone sits at its shape's end, so even a middling disc there can take in a node or two past it. */
        const val MIN_MEDIAN_DISC_SHOWN_SHARE = 0.95f
        const val CAP_BLOCK_SIDE = 120
        const val RELAY_BLOCK_SIDE = 24
        val anchorOrder: Comparator<List<Float>> = compareBy({ it[0] }, { it[1] }, { it[2] }, { it[3] })

        /** `COMET_MAX_HEADS` in MorpheusGraphComets.kt. */
        const val MAX_HEADS = 128
    }
}

/** Every layer's comets with staggered, eased heads of varied sizes, so tests cover the knobs as well as their defaults. */
internal fun MorpheusGraphSpec.withCometKnobs(stagger: Float = 0.5f, easing: Float = 0.5f, sizeDeviation: Float = 0.25f) =
    mapLayers { it.copy(comets = it.comets.copy(stagger = stagger, easing = easing, sizeDeviation = sizeDeviation)) }

/** Every layer's comets with [lead]. */
internal fun MorpheusGraphSpec.withCometLead(lead: Float) = mapLayers { it.copy(comets = it.comets.copy(lead = lead)) }

/** Every layer with [gap] between its leaving and arriving sweeps. */
internal fun MorpheusGraphSpec.withMorphGap(gap: Float) = mapLayers { it.copy(morphGap = gap) }

/** Every layer with the library's default comet zone, tail and lead and morph gap, so a test reads the engine's timing, not a sample's. */
internal fun MorpheusGraphSpec.withDefaultTiming() = mapLayers { layer ->
    val defaults = MorpheusGraphComets()
    layer.copy(comets = layer.comets.copy(zone = defaults.zone, tail = defaults.tail, lead = defaults.lead), morphGap = MorpheusGraphLayerSpec().morphGap)
}

/** A head's two anchors, where it leaves from and lands on. */
private val CometHead.anchors get() = listOf(sourceX, sourceY, targetX, targetY)

/** This spec with every layer put through [transform]. */
internal fun MorpheusGraphSpec.mapLayers(transform: (MorpheusGraphLayerSpec) -> MorpheusGraphLayerSpec) = copy(layers = layers.map(transform))
