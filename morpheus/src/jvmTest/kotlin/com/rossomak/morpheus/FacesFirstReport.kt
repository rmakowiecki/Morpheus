package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.test.Test

/**
 * Reports how face-first strict meshes settle (see [MeshWorkspace.settleFacesFirst]): per preset layer and
 * for the showcase, how often they fall back to the repair rounds and how many edges those repair; and how
 * many interior edges the keep chance drops come back. Printed, not asserted; only runs with `MORPHEUS_BENCHMARK` set:
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*FacesFirstReport' -i`
 *
 * It steps like [GoldenTopologyTest], on each [SURFACES] size with the first golden seed.
 */
class FacesFirstReport {

    @Test
    fun `fallback rate and repaired edges per preset and for the showcase`() {
        if (!benchmarksEnabled()) return
        val stills = MorpheusGraphPresets.all.flatMapIndexed { presetIndex, preset ->
            preset.layers.mapIndexed { layerIndex, layer -> "preset $presetIndex/layer $layerIndex (${layer.edgeRule})" to layer }
        } + listOf("pills" to heroPills, "ribbons" to heroRibbons).flatMap { (end, spec) ->
            spec.layers.mapIndexed { layerIndex, layer -> "hero $end/layer $layerIndex" to layer }
        }
        for ((name, layer) in stills) {
            val tally = Tally()
            for ((width, height) in SURFACES) {
                val field = LayerField(layer, SEED, max(width, height), PX_PER_DP)
                for (frame in 0 until STILL_FRAMES) {
                    field.advance(frame * FRAME_SECONDS, width, height)
                    tally.add(field)
                }
                tally.finish(field)
            }
            println("$name: ${tally.describe()}")
        }
        for ((width, height) in SURFACES) {
            val morph = MorpheusGraphMorph(heroPills.copy(seed = SEED), heroRibbons.copy(seed = SEED), fraction = 0f)
            val fields = HashMap<String, LayerField>()
            val tallies = LinkedHashMap<String, Tally>()
            for (frame in 0..MORPH_FRAMES) {
                for (layer in morphLayers(morph.copy(fraction = frame / MORPH_FRAMES.toFloat()), MorpheusGraphFill.Solid(Color.White))) {
                    val mesh = "slot ${layer.slotFromFront}/density ${layer.spec.density.toInt()}"
                    val field = fields.getOrPut(mesh) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                    field.spec = layer.spec
                    field.shape = layer.shape
                    field.advance(frame * FRAME_SECONDS, width, height)
                    tallies.getOrPut(mesh) { Tally() }.add(field)
                }
            }
            for ((mesh, tally) in tallies) {
                tally.finish(fields.getValue(mesh))
                println("hero pills to ribbons ${width.toInt()}x${height.toInt()}/$mesh: ${tally.describe()}")
            }
        }
    }

    @Test
    fun `interior edges the keep chance drops that come back`() {
        if (!benchmarksEnabled()) return
        val layers = MorpheusGraphPresets.all.flatMap { it.layers } + heroPills.layers + heroRibbons.layers
        var dropped = 0
        var restored = 0
        for (layer in layers.filter { it.edgeRule == EdgeRule.FixedTriangulation && it.strictTriangles }) {
            for (seed in GOLDEN_SEEDS) {
                for ((width, height) in SURFACES) {
                    val (droppedHere, restoredHere) = keepChanceDrops(layer, seed, width, height)
                    dropped += droppedHere
                    restored += restoredHere
                }
            }
        }
        val share = if (dropped == 0) "n/a" else "%.1f%%".format(restored * 100.0 / dropped)
        println("keep chance: $restored of $dropped interior edges it dropped came back ($share)")
    }

    /** Interior Delaunay edges the keep chance drops at a fresh still frame, and how many of them the mesh has after all. */
    private fun keepChanceDrops(layer: MorpheusGraphLayerSpec, seed: Int, width: Float, height: Float): Pair<Int, Int> {
        val referenceSize = max(width, height)
        val field = LayerField(layer, seed, referenceSize, PX_PER_DP)
        field.advance(0f, width, height)
        val present = (0 until field.nodeCount).filter { field.nodePresence(it) >= PRESENT_THRESHOLD }.toIntArray()
        val xs = FloatArray(field.nodeCount) { field.nodeHome(it).x }
        val ys = FloatArray(field.nodeCount) { field.nodeHome(it).y }
        val triangles = Delaunay.triangulate(FloatArray(present.size) { xs[present[it]] }, FloatArray(present.size) { ys[present[it]] }, present.size)
        for (corner in triangles.indices) triangles[corner] = present[triangles[corner]]
        val cell = referenceSize / layer.density
        fun filtered(keepChance: Float) = meshEdgeKeys(triangles, xs, ys, layer.minAngleDegrees, layer.maxEdgeFactor * cell, keepChance, seed).toHashSet()
        val sides = HashMap<Long, Int>()
        for (base in triangles.indices step 3) {
            for (side in 0 until 3) sides.merge(edgeKey(triangles[base + side], triangles[base + (side + 1) % 3]), 1, Int::plus)
        }
        val interior = (filtered(keepChance = 1f) - filtered(layer.edgeKeepChance)).filter { sides[it] == 2 }
        val mesh = field.topology.edges.toHashSet()
        return interior.size to interior.count { it in mesh }
    }

    /** One case's settles, fallbacks and repaired edges, over one fresh field at a time. */
    private class Tally {
        private var settles = 0
        private var facesFirst = 0
        private var fallbacks = 0
        private var settledFrames = 0
        private var repairedSum = 0L
        private var fieldSettles = 0

        /** Counts [field]'s repaired edges on a frame that settled it. */
        fun add(field: LayerField) {
            if (field.settleCount == fieldSettles) return
            fieldSettles = field.settleCount
            settledFrames++
            repairedSum += field.repairedEdgeCount
        }

        /** Adds [field]'s settle counts, ready for the next fresh field. */
        fun finish(field: LayerField) {
            settles += field.settleCount
            facesFirst += field.facesFirstSettleCount
            fallbacks += field.facesFirstFallbackCount
            fieldSettles = 0
        }

        fun describe(): String {
            val rate = if (facesFirst == 0) "n/a" else "%.0f%%".format(fallbacks * 100.0 / facesFirst)
            val repaired = if (settledFrames == 0) "n/a" else "%.1f".format(repairedSum.toDouble() / settledFrames)
            return "$settles settles, $facesFirst face first, $fallbacks fell back ($rate); repaired edges $repaired per settled frame"
        }
    }

    private companion object {
        const val SEED = 1
        val GOLDEN_SEEDS = listOf(1, 42, 9_001)
        val SURFACES = listOf(640f to 360f, 360f to 640f)
        const val FRAME_SECONDS = 1f / 30f
        const val STILL_FRAMES = 151
        const val MORPH_FRAMES = 60
        const val PX_PER_DP = 2.75f

        /** LayerField's own threshold for joining the mesh. */
        const val PRESENT_THRESHOLD = 0.05f
    }
}
