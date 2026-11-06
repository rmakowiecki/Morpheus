package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.max
import kotlin.test.Test

/**
 * The still-frame cache changes when the work is done, never what is drawn: every frame draws bit for bit the same
 * with [LayerField.cachesStillFrames] on as with it off, still or morphing, across tuner edits and resizes, under
 * every edge rule. Positions are compared over the settle list only, since with the cache on no other node is
 * positioned.
 */
class StillFrameCacheTest {

    @Test
    fun `still frames draw the same with the cache on and off`() {
        val frames = still(heroPills) + still(playgroundFrame) + still(heroRibbons)

        compare(frames).shouldBeEmpty()
    }

    @Test
    fun `a morph running, held part way and interrupted draws the same with the cache on and off`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, 0f)
        val held = List(HELD_FRAMES) { Frame(morph.copy(fraction = HELD_FRACTION)) }
        val interrupted = MorpheusGraphMorph(heroPills, heroRibbons, INTERRUPT_FRACTION)
        val retargeted = MorpheusGraphMorph(lerp(heroPills, heroRibbons, INTERRUPT_FRACTION), playgroundCross, 0f, origin = interrupted)
        val interruptedFrames = (0..4).map { Frame(interrupted.copy(fraction = it / 10f)) } + (0..10).map { Frame(retargeted.copy(fraction = it / 10f)) }

        compare(still(heroPills) + running(heroPills, heroRibbons) + held + still(heroRibbons) + interruptedFrames).shouldBeEmpty()
    }

    @Test
    fun `a tuner edit and a resize draw the same with the cache on and off`() {
        val tuned = heroPills.copy(layers = heroPills.layers.map { it.copy(hotNodeShare = it.hotNodeShare + TUNED_SHARE, fillChance = TUNED_FILL_CHANCE) })
        val resized = still(heroPills).map { it.copy(width = RESIZED_WIDTH, height = RESIZED_HEIGHT) }

        compare(still(heroPills) + still(tuned) + still(heroPills) + resized + still(heroPills)).shouldBeEmpty()
    }

    @Test
    fun `the Proximity and LivingDelaunay rules draw the same with the cache on and off, still and morphing`() {
        val baseline = MorpheusGraphPresets.baseline
        val tuned = tunedFill(baseline)
        val living = MorpheusGraphPresets.livingDelaunay
        val frames = still(baseline) + still(tuned) + still(baseline).map { it.copy(width = RESIZED_WIDTH, height = RESIZED_HEIGHT) } +
            still(living) + running(baseline, tuned) + running(living, tunedFill(living))

        compare(frames).shouldBeEmpty()
    }

    @Test
    fun `a still frame and a morph held part way compute no presences and position only the settle list`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, HELD_FRACTION)
        val cases = listOf("hero still" to GalleryLoop.still(heroPills), "held mid-morph" to morph, "baseline still" to GalleryLoop.still(MorpheusGraphPresets.baseline))
        for ((name, frame) in cases) {
            val run = Run(cachesStillFrames = true)
            run.advance(Frame(frame))
            val missesBefore = run.fields.mapValues { it.value.stillFrameCacheMisses }
            run.advance(Frame(frame))
            run.fields.forEach { (key, field) ->
                withClue("$name, layer $key") {
                    field.stillFrameCacheMisses shouldBe missesBefore.getValue(key)
                    field.stillFrameCacheHits shouldBeGreaterThan 0
                    field.presencesComputed shouldBe 0
                    field.shapeDensitiesEvaluated shouldBe 0
                    field.positionsComputed shouldBe field.relevantNodeCount
                }
            }
        }
    }

    private fun still(spec: MorpheusGraphSpec): List<Frame> = List(STILL_FRAMES) { Frame(GalleryLoop.still(spec)) }

    /** A morph from [from] to [to] run end to end in tenths. */
    private fun running(from: MorpheusGraphSpec, to: MorpheusGraphSpec): List<Frame> = (0..10).map { Frame(MorpheusGraphMorph(from, to, it / 10f)) }

    private fun tunedFill(spec: MorpheusGraphSpec) = spec.copy(layers = spec.layers.map { it.copy(fillChance = TUNED_FILL_CHANCE) })

    /** Every difference between [frames] drawn with the cache on and with it off. */
    private fun compare(frames: List<Frame>): List<String> {
        val cached = Run(cachesStillFrames = true)
        val evaluated = Run(cachesStillFrames = false)
        return frames.flatMapIndexed { index, frame ->
            cached.advance(frame)
            evaluated.advance(frame)
            cached.fields.flatMap { (key, field) -> differences("frame $index layer $key", field, evaluated.fields.getValue(key)) }
        }.take(MISMATCHES_SHOWN)
    }

    private fun differences(label: String, cached: LayerField, evaluated: LayerField): List<String> {
        val nodes = (0 until cached.nodeCount).filter { node ->
            differ(cached.nodePresence(node), evaluated.nodePresence(node)) ||
                differ(cached.nodeHotWeight(node), evaluated.nodeHotWeight(node)) ||
                differ(cached.nodeAlpha(node), evaluated.nodeAlpha(node)) ||
                differ(cached.nodeGlowFactor(node), evaluated.nodeGlowFactor(node)) ||
                differ(cached.brightestEdgeAlpha(node), evaluated.brightestEdgeAlpha(node)) ||
                differ(cached.secondEdgeAlpha(node), evaluated.secondEdgeAlpha(node)) ||
                cached.visibleEdgeCount(node) != evaluated.visibleEdgeCount(node)
        }
        val listsMatch = cached.relevantNodeCount == evaluated.relevantNodeCount &&
            (0 until cached.relevantNodeCount).all { cached.relevantNode(it) == evaluated.relevantNode(it) }
        val positions = (0 until cached.relevantNodeCount).map { cached.relevantNode(it) }.filter { node ->
            differ(cached.nodePosition(node).x, evaluated.nodePosition(node).x) ||
                differ(cached.nodePosition(node).y, evaluated.nodePosition(node).y)
        }
        val edgesMatch = cached.drawnEdgeCount == evaluated.drawnEdgeCount &&
            (0 until cached.drawnEdgeCount).all { edge ->
                cached.drawnEdgeKey(edge) == evaluated.drawnEdgeKey(edge) &&
                    !differ(cached.drawnEdgeFade(edge), evaluated.drawnEdgeFade(edge)) &&
                    !differ(cached.settledEdgeAlpha(edge), evaluated.settledEdgeAlpha(edge))
            }
        return listOfNotNull(
            "$label: ${nodes.size} nodes differ, first ${nodes.firstOrNull()}".takeIf { nodes.isNotEmpty() },
            "$label: settle lists differ".takeIf { !listsMatch },
            "$label: ${positions.size} positions in the settle list differ, first ${positions.firstOrNull()}".takeIf { positions.isNotEmpty() },
            "$label: drawn edges differ".takeIf { !edgesMatch },
            "$label: visible nodes differ".takeIf { cached.visibleNodeCount != evaluated.visibleNodeCount },
            "$label: pruned nodes differ".takeIf { cached.prunedNodeCount != evaluated.prunedNodeCount },
            "$label: mesh membership changes differ".takeIf { cached.meshMembershipChanges != evaluated.meshMembershipChanges },
        )
    }

    private fun differ(cached: Float, evaluated: Float): Boolean = cached.toRawBits() != evaluated.toRawBits()

    /** One frame: the morph to draw, on a [width] × [height] surface. */
    private data class Frame(val morph: MorpheusGraphMorph, val width: Float = WIDTH, val height: Float = HEIGHT)

    /** One [LayerField] per mesh, kept across frames the way the modifier keeps them, with the cache on or off. */
    private class Run(private val cachesStillFrames: Boolean) {
        val fields = LinkedHashMap<String, LayerField>()
        private var timeSeconds = 0f

        fun advance(frame: Frame) {
            timeSeconds += FRAME_SECONDS
            for (layer in morphLayers(frame.morph, TestFill)) {
                val key = with(layer.spec) { "${layer.seed}/$density/$jitter/$edgeRule/$strictTriangles" }
                val field = fields.getOrPut(key) {
                    LayerField(layer.spec, layer.seed, max(WIDTH, HEIGHT), PX_PER_DP).also { it.cachesStillFrames = cachesStillFrames }
                }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(timeSeconds, frame.width, frame.height)
            }
        }
    }

    private companion object {
        val TestFill = MorpheusGraphFill.Solid(Color.White)
        const val PX_PER_DP = 2f
        const val WIDTH = 1280f
        const val HEIGHT = 720f
        const val RESIZED_WIDTH = 1400f
        const val RESIZED_HEIGHT = 640f
        const val FRAME_SECONDS = 1f / 60f
        const val STILL_FRAMES = 4
        const val HELD_FRAMES = 4
        const val HELD_FRACTION = 0.5f
        const val INTERRUPT_FRACTION = 0.4f
        const val TUNED_SHARE = 0.1f
        const val TUNED_FILL_CHANCE = 0.7f
    }
}
