package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test

/**
 * A morph's cache changes when the work is done, never what is drawn: every frame of a morph, stepped forward
 * and back, interrupted, or left part way, draws the same with [LayerField.cachesMorphs] on as with it off, and
 * seeking around one morph builds its meshes once.
 */
class MorphCacheTest {

    @Test
    fun `a morph stepped forward and back draws the same with the cache on and off`() {
        val uniformToBloom = MorpheusGraphMorph(MorpheusGraphPresets.MorphFamily.uniformField, MorpheusGraphPresets.MorphFamily.cornerBloom, 0f)
        val pillsToRibbons = MorpheusGraphMorph(heroPills, heroRibbons, 0f)

        val mismatches = listOf("uniform field to corner bloom" to uniformToBloom, "pills to ribbons" to pillsToRibbons).flatMap { (name, morph) ->
            compare(name, forwardAndBack.map { morph.copy(fraction = it) })
        }

        mismatches.shouldBeEmpty()
    }

    @Test
    fun `an interrupted morph draws the same with the cache on and off`() {
        val first = MorpheusGraphPresets.MorphFamily.uniformField
        val second = MorpheusGraphPresets.MorphFamily.cornerBloom
        val interrupted = MorpheusGraphMorph(first, second, INTERRUPT_FRACTION)
        val retargeted = MorpheusGraphMorph(lerp(first, second, INTERRUPT_FRACTION), MorpheusGraphPresets.MorphFamily.ribbon, 0f, origin = interrupted)

        compare("interrupted", forwardAndBack.map { retargeted.copy(fraction = it) }).shouldBeEmpty()
    }

    @Test
    fun `a morph left part way draws the same with the cache on and off`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, 0f)
        val leftPartWay = (0..6).map { morph.copy(fraction = it / 10f) } + List(LEFT_FRAMES) { MorpheusGraphMorph(heroPills, heroPills, 0f) }

        compare("left at 0.6", leftPartWay).shouldBeEmpty()
    }

    @Test
    fun `seeking back and forth around one morph builds its meshes once`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, 0f)
        val run = Run(cachesMorphs = true)

        listOf(0.5f, 0f, 0.01f, 1f, 0.99f).forEach { run.advance(morph.copy(fraction = it)) }

        run.fields.values.map { it.morphMeshBuildCount } shouldBe List(run.fields.size) { 1 }
    }

    /** Every difference between [frames] drawn with the cache on and with it off: presences, hot weights and edges. */
    private fun compare(name: String, frames: List<MorpheusGraphMorph>): List<String> {
        val cached = Run(cachesMorphs = true)
        val evaluated = Run(cachesMorphs = false)
        return frames.flatMapIndexed { frame, morph ->
            cached.advance(morph)
            evaluated.advance(morph)
            cached.fields.flatMap { (key, field) -> differences("$name frame $frame layer $key", field, evaluated.fields.getValue(key), cached.timeSeconds) }
        }
    }

    private fun differences(label: String, cached: LayerField, evaluated: LayerField, timeSeconds: Float): List<String> {
        val nodes = (0 until cached.nodeCount).filter { node ->
            !close(cached.nodePresence(node), evaluated.nodePresence(node)) || !close(cached.nodeHotWeight(node), evaluated.nodeHotWeight(node))
        }
        val cachedEdges = (0 until cached.drawnEdgeCount).map { cached.drawnEdgeKey(it) to cached.drawnEdgeAlpha(it, timeSeconds) }
        val evaluatedEdges = (0 until evaluated.drawnEdgeCount).map { evaluated.drawnEdgeKey(it) to evaluated.drawnEdgeAlpha(it, timeSeconds) }
        val edgesMatch = cachedEdges.size == evaluatedEdges.size &&
            cachedEdges.zip(evaluatedEdges).all { (first, second) -> first.first == second.first && close(first.second, second.second) }
        return listOfNotNull(
            "$label: ${nodes.size} nodes differ, first ${nodes.firstOrNull()}".takeIf { nodes.isNotEmpty() },
            "$label: drawn edges differ".takeIf { !edgesMatch },
        )
    }

    private fun close(first: Float, second: Float): Boolean = abs(first - second) <= TOLERANCE

    /** One [LayerField] per mesh, kept across frames the way the modifier keeps them, with the cache on or off. */
    private class Run(private val cachesMorphs: Boolean) {
        val fields = LinkedHashMap<String, LayerField>()
        var timeSeconds = 0f
            private set

        fun advance(morph: MorpheusGraphMorph) {
            timeSeconds += FRAME_SECONDS
            for (layer in morphLayers(morph, TestFill)) {
                val key = with(layer.spec) { "${layer.seed}/$density/$jitter/$edgeRule/$strictTriangles" }
                val field = fields.getOrPut(key) { LayerField(layer.spec, layer.seed, max(WIDTH, HEIGHT), PX_PER_DP).also { it.cachesMorphs = cachesMorphs } }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(timeSeconds, WIDTH, HEIGHT)
            }
        }
    }

    private companion object {
        val TestFill = MorpheusGraphFill.Solid(Color.White)
        val forwardAndBack = (0..10).map { it / 10f }.let { forward -> forward + forward.reversed() }
        const val PX_PER_DP = 2f
        const val WIDTH = 1280f
        const val HEIGHT = 720f
        const val FRAME_SECONDS = 1f / 60f
        const val INTERRUPT_FRACTION = 0.4f
        const val LEFT_FRAMES = 40
        const val TOLERANCE = 1e-5f
    }
}
