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
    fun `an interrupted morph draws the same with the cache on and off, across the moment it is interrupted`() {
        val family = MorpheusGraphPresets.MorphFamily
        val mismatches = listOf(
            "uniform field to corner bloom, then ribbon" to Triple(family.uniformField, family.cornerBloom, family.ribbon),
            "pills to ribbons, then a bar" to Triple(heroPills, heroRibbons, heroBar),
        ).flatMap { (name, shapes) ->
            val (interrupted, retargeted) = interruptedMorph(shapes.first, shapes.second, shapes.third)
            val frames = (0..4).map { interrupted.copy(fraction = it / 10f) } + forwardAndBack.map { retargeted.copy(fraction = it) }
            compare(name, frames)
        }

        mismatches.shouldBeEmpty()
    }

    @Test
    fun `a morph left part way, forward or back, draws the same with the cache on and off`() {
        val forward = MorpheusGraphMorph(heroPills, heroRibbons, 0f)
        val back = MorpheusGraphMorph(heroRibbons, heroPills, 0f)
        val leftForward = (0..6).map { forward.copy(fraction = it / 10f) }
        val leftBack = (10 downTo 4).map { forward.copy(fraction = it / 10f) }
        val reversedLeft = (0..6).map { back.copy(fraction = it / 10f) }
        val heldOnBar = List(LEFT_FRAMES) { MorpheusGraphMorph(heroBar, heroBar, 0f) }

        val mismatches = listOf("left at 0.6" to leftForward, "scrubbed back to 0.4" to leftBack, "the way back left at 0.6" to reversedLeft)
            .flatMap { (name, frames) -> compare(name, frames + heldOnBar) }

        mismatches.shouldBeEmpty()
    }

    @Test
    fun `seeking back and forth around one morph builds its meshes once`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, 0f)
        val run = Run(cachesMorphs = true)

        listOf(0.5f, 0f, 0.01f, 1f, 0.99f).forEach { run.advance(morph.copy(fraction = it)) }

        run.fields.values.map { it.morphMeshBuildCount } shouldBe List(run.fields.size) { 1 }
    }

    @Test
    fun `either end of a morph reuses the mesh it settled for that end, the mesh a still frame there settles`() {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, 0f)

        val mismatches = listOf(1f, 0f).flatMap { fraction ->
            val run = Run(cachesMorphs = true).also { it.advance(morph.copy(fraction = 0.5f)) }
            val settlesBefore = run.fields.mapValues { it.value.settleCount }
            run.advance(morph.copy(fraction = fraction))
            val still = Run(cachesMorphs = true).also { it.advance(morph.copy(fraction = fraction)) }
            run.fields.flatMap { (key, field) ->
                listOfNotNull("$key at $fraction settled again".takeIf { field.settleCount != settlesBefore.getValue(key) }) +
                    meshDifferences("$key at $fraction", field, still.fields.getValue(key))
            }
        }

        mismatches.shouldBeEmpty()
    }

    @Test
    fun `a retargeted morph resting at its start, a frozen morph with its comets, draws the mesh a still frame there settles`() {
        val (_, retargeted) = interruptedMorph(heroPills, heroRibbons, heroBar)
        val run = Run(cachesMorphs = true).also { it.advance(retargeted.copy(fraction = 0.5f)) }

        run.advance(retargeted.copy(fraction = 0f))
        val still = Run(cachesMorphs = true).also { it.advance(retargeted.copy(fraction = 0f)) }

        run.fields.flatMap { (key, field) -> meshDifferences("$key at 0", field, still.fields.getValue(key)) }.shouldBeEmpty()
    }

    /** The morph from [first] towards [second] interrupted at [INTERRUPT_FRACTION], and the morph from there to [third] that takes over. */
    private fun interruptedMorph(first: MorpheusGraphSpec, second: MorpheusGraphSpec, third: MorpheusGraphSpec): Pair<MorpheusGraphMorph, MorpheusGraphMorph> {
        val interrupted = MorpheusGraphMorph(first, second, INTERRUPT_FRACTION)
        return interrupted to MorpheusGraphMorph(lerp(first, second, INTERRUPT_FRACTION), third, 0f, origin = interrupted)
    }

    /** Every way [field]'s settled mesh differs from [still]'s: its edges, its nodes and what the settle repaired or pruned. */
    private fun meshDifferences(label: String, field: LayerField, still: LayerField): List<String> {
        val mesh = field.topology
        val stillMesh = still.topology
        return listOfNotNull(
            "$label: edges differ from a still frame".takeIf { !mesh.edges.contentEquals(stillMesh.edges) },
            "$label: live nodes differ from a still frame".takeIf { !mesh.alive.contentEquals(stillMesh.alive) },
            "$label: repairs differ from a still frame".takeIf { mesh.repairedEdgeCount != stillMesh.repairedEdgeCount },
            "$label: pruned nodes differ from a still frame".takeIf { field.prunedNodeCount != still.prunedNodeCount },
            "$label: convergence differs from a still frame".takeIf { mesh.converged != stillMesh.converged },
        )
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

        /** A third shape over the showcase's meshes, so a morph can leave both its ends for one neither settled. */
        val heroBar = heroPills.copy(
            layers = listOf(heroGhost, heroFront.copy(shapes = listOf(ShapePrimitive.Pill(x = 0.5f, y = 0.5f, length = 0.6f, thickness = 0.16f, rotation = 0f)))),
        )
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
