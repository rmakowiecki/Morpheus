package com.rossomak.morpheus

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Reports how abruptly halos go out over the "Pills to ribbons" showcase's loop, as the gallery seeks it: per
 * layer with halos, how many halos drop by more than [HALO_DROP] of a full halo in one frame, the most in any
 * one frame, and what took each one down. A comet's patch once lost its halos together, mostly on the reverse
 * morph, when its sparks' heat or its nodes' presence fell in a frame or two (#43); normal switching, and the
 * odd glow factor the alpha settle drops, stay. Printed, not asserted; only runs with `MORPHEUS_BENCHMARK` set:
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*MorphHaloReport' -i`
 */
class MorphHaloReport {

    @Test
    fun `halos going out in one frame over the showcase loop`() {
        if (!benchmarksEnabled()) return
        val canvas = BenchmarkCanvas.Recipe
        val fields = HashMap<Int, LayerField>()
        val tallies = HashMap<Int, Tally>()
        for (frame in 0 until SHOWCASE_LOOP_FRAMES) {
            val time = frame * SHOWCASE_FRAME_SECONDS
            val morph = MorpheusGraphMorph(heroPills, heroRibbons, showcaseFractionAt(time))
            for (layer in GalleryLoop.morphLayers(morph)) {
                val fullHalo = layer.spec.glowStrength * layer.spec.alpha
                if (fullHalo <= 0f) continue
                val field = fields.getOrPut(layer.slotFromFront) { LayerField(layer.spec, layer.seed, canvas.referenceSize, canvas.pxPerDp) }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(time, canvas.width, canvas.height)
                tallies.getOrPut(layer.slotFromFront) { Tally() }.add(frame, NodeHalos.of(field, fullHalo))
            }
        }
        for ((slot, tally) in tallies.entries.sortedBy { it.key }) println("slot $slot: ${tally.describe()}")
    }

    /** Every node's halo as a share of a full one, and the three things it multiplies, after one [LayerField.advance]. */
    private class NodeHalos(val halo: FloatArray, val heat: FloatArray, val glow: FloatArray, val presence: FloatArray, val switching: BooleanArray) {
        companion object {
            fun of(field: LayerField, fullHalo: Float) = NodeHalos(
                FloatArray(field.nodeCount) { field.nodeHaloAlpha(it) / fullHalo },
                FloatArray(field.nodeCount) { field.nodeHotWeight(it) },
                FloatArray(field.nodeCount) { field.nodeGlowFactor(it) },
                FloatArray(field.nodeCount) { field.nodePresence(it) },
                BooleanArray(field.nodeCount) { field.nodeSwitchesInMorph(it) },
            )
        }
    }

    /** One layer's halo drops over the loop, by frame and by cause. */
    private class Tally {
        private var previous: NodeHalos? = null
        private var largestDrop = 0f
        private val dropsByFrame = HashMap<Int, Int>()
        private val dropsByCause = LinkedHashMap<String, Int>()

        fun add(frame: Int, halos: NodeHalos) {
            val before = previous
            previous = halos
            if (before == null || before.halo.size != halos.halo.size) return
            for (node in halos.halo.indices) {
                val drop = before.halo[node] - halos.halo[node]
                largestDrop = max(largestDrop, drop)
                if (drop <= HALO_DROP) continue
                dropsByFrame[frame] = (dropsByFrame[frame] ?: 0) + 1
                val cause = when {
                    halos.switching[node] -> "switching"
                    before.heat[node] - halos.heat[node] > HALO_DROP -> "heat"
                    before.glow[node] - halos.glow[node] > HALO_DROP -> "glow factor"
                    before.presence[node] - halos.presence[node] > HALO_DROP -> "presence"
                    else -> "mixed"
                }
                dropsByCause[cause] = (dropsByCause[cause] ?: 0) + 1
            }
        }

        fun describe(): String {
            val drops = dropsByFrame.values.sum()
            val causes = dropsByCause.entries.joinToString { (cause, count) -> "$cause $count" }.ifEmpty { "none" }
            return "$drops halo drops over $HALO_DROP in ${dropsByFrame.size} frames, at most ${dropsByFrame.values.maxOrNull() ?: 0} in a frame; " +
                "largest ${(largestDrop * 100).roundToInt() / 100f}; by cause: $causes"
        }
    }

    private companion object {
        /** A halo losing more than this share of a full halo in one frame reads as going out. */
        const val HALO_DROP = 0.15f
    }
}
