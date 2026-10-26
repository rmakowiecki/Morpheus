@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.test.Test

/**
 * Times the settles at a morph's start: both ends of the "Pills to ribbons" showcase and, on the front
 * layer, the comet transit mesh, all settled in one frame. The gallery's half-second averages dilute that
 * spike, so this measures it alone. The front layer is timed again without comets, so its two ends alone
 * show too, and the transit mesh as the difference. Also replays the showcase's loop as the gallery runs it.
 * Printed, not asserted; runs on [benchmark], see `docs/benchmark.md`.
 */
class SettleBenchmark {

    @Test
    fun `settling the showcase's morph meshes`() = benchmark(this::class, "morph meshes") {
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, fraction = MID_FRACTION)
        for (layer in GalleryLoop.morphLayers(morph)) timeMorphMesh(layer, "")
        val withoutComets = MorpheusGraphMorph(heroPills.withoutFrontComets(), heroRibbons.withoutFrontComets(), fraction = MID_FRACTION)
        timeMorphMesh(GalleryLoop.morphLayers(withoutComets).first { it.slotFromFront == 0 }, ", without comets")
    }

    private fun MorpheusGraphSpec.withoutFrontComets() = copy(layers = layers.dropLast(1) + layers.last().copy(cometStrength = 0f))

    /** Times rebuilding [layer]'s morph meshes on the recipe's canvas; [label] marks a variant in the printed line. */
    private fun timeMorphMesh(layer: MorphLayer, label: String) {
        val canvas = BenchmarkCanvas.Recipe
        val field = LayerField(layer.spec, layer.seed, canvas.referenceSize, canvas.pxPerDp)
        field.shape = layer.shape
        field.advance(0f, canvas.width, canvas.height)
        val shape = layer.shape as LayerShape.Morph
        val nanos = timeCalls(WARM_UP_RUNS, MEASURED_BATCHES) { field.rebuildMorphMesh(shape, canvas.width, canvas.height) }
        val settlesBefore = field.settleCount
        field.rebuildMorphMesh(shape, canvas.width, canvas.height)
        val meshes = if (field.settleCount - settlesBefore > END_SETTLES) "two ends and the transit mesh" else "two ends"
        println(
            "slot ${layer.slotFromFront} (density ${layer.spec.density.toInt()}$label, $meshes): " +
                "median ${nanos[nanos.size / 2].millis()} ms, fastest ${nanos.first().millis()} ms per call over $MEASURED_BATCHES batches",
        )
    }

    /**
     * The showcase's loop as the gallery's benchmark recipe runs it, through [GalleryLoop] on the recipe's
     * canvas. Prints each `advance` phase, every layer summed, on the frame where the morph starts or ends and
     * as a mean over the half second from there, the window the gallery's readout averages over; then the mean
     * over the whole loop, which compares with the gallery's readout over a loop. Medians over the loops.
     */
    @Test
    fun `the showcase's loop as the gallery runs it`() = benchmark(this::class, "showcase loop") {
        val loop = GalleryLoop()
        val fractions = FloatArray(LOOP_FRAMES) { frame -> fractionAt(frame * FRAME_SECONDS) }
        val spikes = HashMap<String, MutableList<LongArray>>()
        val windows = HashMap<String, MutableList<LongArray>>()
        val wholeLoops = mutableListOf<LongArray>()
        repeat(WARM_UP_LOOPS + MEASURED_LOOPS) { loopIndex ->
            val frames = List(LOOP_FRAMES) { frame -> loop.frame(MorpheusGraphMorph(heroPills, heroRibbons, fractions[frame])) }
            if (loopIndex < WARM_UP_LOOPS) return@repeat
            wholeLoops += frames.meanFrame()
            for ((event, frame) in loopEvents(fractions)) {
                spikes.getOrPut(event) { mutableListOf() } += frames[frame]
                windows.getOrPut(event) { mutableListOf() } += frames.subList(frame, minOf(frame + WINDOW_FRAMES, LOOP_FRAMES)).meanFrame()
            }
        }
        for (event in spikes.keys.sorted()) {
            println("showcase loop, $event: frame ${spikes.getValue(event).medianPhases()}; half second from there, per frame ${windows.getValue(event).medianPhases()}")
        }
        println("showcase loop, whole loop: per frame ${wholeLoops.medianPhases()}")
    }

    /** The frames where the morph leaves or reaches either end, as the gallery's showcase seeks it back and forth. */
    private fun loopEvents(fractions: FloatArray): Map<String, Int> {
        val frames = 1 until fractions.size
        return mapOf(
            "1 forward start" to frames.first { fractions[it - 1] == 0f && fractions[it] > 0f },
            "2 forward end" to frames.first { fractions[it - 1] < 1f && fractions[it] == 1f },
            "3 reverse start" to frames.first { fractions[it - 1] == 1f && fractions[it] < 1f },
            "4 reverse end" to frames.first { fractions[it - 1] > 0f && fractions[it] == 0f },
        )
    }

    /** The showcase sample's own fraction at [time] into its loop: hold, morph, hold, then the same mirrored. */
    private fun fractionAt(time: Float): Float {
        val forward = if (time <= HALF_LOOP_SECONDS) time else HALF_LOOP_SECONDS * 2 - time
        return FastOutSlowInEasing.transform(((forward - HOLD_SECONDS) / MORPH_SECONDS).coerceIn(0f, 1f))
    }

    private companion object {
        const val MID_FRACTION = 0.5f
        const val END_SETTLES = 2
        const val WARM_UP_RUNS = 200
        const val MEASURED_BATCHES = 200

        /** The showcase sample's loop at 60 fps: hold the pills, morph, hold the ribbons, then back. */
        const val FRAME_SECONDS = 1f / 60f
        const val HOLD_SECONDS = 0.75f
        const val MORPH_SECONDS = 3.5f
        const val HALF_LOOP_SECONDS = HOLD_SECONDS + MORPH_SECONDS + HOLD_SECONDS
        const val LOOP_FRAMES = 600
        const val WINDOW_FRAMES = 30
        const val WARM_UP_LOOPS = 1
        const val MEASURED_LOOPS = 7
    }
}
