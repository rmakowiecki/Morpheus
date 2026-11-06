@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import kotlin.test.Test

/**
 * Times the settles at a morph's start: both ends of the "Pills to ribbons" showcase and, on the front
 * layer, the comet transit mesh, all settled in one frame. The gallery's half-second averages dilute that
 * spike, so this measures it alone. The front layer is timed again without comets, so its two ends alone
 * show too, and the transit mesh as the difference. Also replays the showcase's loop as the gallery runs it.
 * The "Comet playground" gets the same rows for each of its three morphs: its denser front mesh and larger
 * comets show changes to comet and morph code that the showcase is too light to show.
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

    @Test
    fun `settling the playground's morph meshes`() = benchmark(this::class, "playground morph meshes") {
        for ((leg, morph) in playgroundLegs) {
            for (layer in GalleryLoop.morphLayers(morph.copy(fraction = MID_FRACTION))) timeMorphMesh(layer, "", prefix = "$leg, ")
        }
    }

    /**
     * Times building the comets of the showcase's front layer at the morph's start, which [timeMorphMesh]
     * leaves out: a rebuilt mesh reuses the cached comets.
     */
    @Test
    fun `building the showcase's comet track`() = benchmark(this::class, "comet track build") {
        timeCometTracks(listOf("forward" to MorpheusGraphMorph(heroPills, heroRibbons, MID_FRACTION), "reverse" to MorpheusGraphMorph(heroRibbons, heroPills, MID_FRACTION)))
    }

    /** The same for each of the playground's three morphs. */
    @Test
    fun `building the playground's comet tracks`() = benchmark(this::class, "playground comet track build") {
        timeCometTracks(playgroundLegs.map { (leg, morph) -> leg to morph.copy(fraction = MID_FRACTION) })
    }

    /** Times building the front layer's comets for each of [morphs] on the recipe's canvas, by name. */
    private fun timeCometTracks(morphs: List<Pair<String, MorpheusGraphMorph>>) {
        val canvas = BenchmarkCanvas.Recipe
        for ((name, morph) in morphs) {
            val layer = GalleryLoop.morphLayers(morph).first { it.slotFromFront == 0 }
            val field = LayerField(layer.spec, layer.seed, canvas.referenceSize, canvas.pxPerDp)
            field.shape = layer.shape
            field.advance(0f, canvas.width, canvas.height)
            val shape = layer.shape as LayerShape.Morph
            val nanos = timeCalls(WARM_UP_RUNS, MEASURED_BATCHES) { field.rebuildCometTrack(shape, canvas.width, canvas.height) }
            println(
                "slot 0, $name (density ${layer.spec.density.toInt()}, ${field.cometCount} comets): " +
                    "median ${nanos[nanos.size / 2].millis()} ms, fastest ${nanos.first().millis()} ms per call over $MEASURED_BATCHES batches",
            )
        }
    }

    private fun MorpheusGraphSpec.withoutFrontComets() = copy(layers = layers.dropLast(1) + layers.last().copy(comets = MorpheusGraphComets(strength = 0f)))

    /**
     * Times rebuilding [layer]'s morph meshes on the recipe's canvas; [label] marks a variant in the printed
     * line, and [prefix] starts it.
     */
    private fun timeMorphMesh(layer: MorphLayer, label: String, prefix: String = "") {
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
            "${prefix}slot ${layer.slotFromFront} (density ${layer.spec.density.toInt()}$label, $meshes): " +
                "median ${nanos[nanos.size / 2].millis()} ms, fastest ${nanos.first().millis()} ms per call over $MEASURED_BATCHES batches",
        )
    }

    /**
     * The showcase's loop as the gallery's benchmark recipe runs it, through [GalleryLoop] on the recipe's
     * canvas. Prints each `advance` phase, every layer summed, on the frame where a morph starts or ends and
     * as a mean over the half second from there, the window the gallery's readout averages over; then the mean
     * over the whole loop, which compares with the gallery's readout over a loop. Medians over the loops.
     */
    @Test
    fun `the showcase's loop as the gallery runs it`() = benchmark(this::class, "showcase loop") {
        replayLoop("showcase loop", List(SHOWCASE_LOOP_FRAMES) { frame -> showcaseMorphAt(frame * SHOWCASE_FRAME_SECONDS) }, listOf("forward", "way back"))
    }

    /** The same for the playground's loop, its three morphs in turn, on the same canvas. */
    @Test
    fun `the playground's loop as the gallery runs it`() = benchmark(this::class, "playground loop") {
        replayLoop("playground loop", List(PLAYGROUND_LOOP_FRAMES) { frame -> playgroundMorphAt(frame * SHOWCASE_FRAME_SECONDS) }, playgroundLegs.map { it.first })
    }

    /** Replays [morphs], one a frame, and prints [name]'s rows; [legs] names each morph of the loop in order. */
    private fun replayLoop(name: String, morphs: List<MorpheusGraphMorph>, legs: List<String>) {
        val loop = GalleryLoop()
        val events = loopEvents(morphs, legs)
        val spikes = HashMap<String, MutableList<LongArray>>()
        val windows = HashMap<String, MutableList<LongArray>>()
        val wholeLoops = mutableListOf<LongArray>()
        repeat(WARM_UP_LOOPS + MEASURED_LOOPS) { loopIndex ->
            val frames = List(morphs.size) { frame -> loop.frame(morphs[frame]) }
            if (loopIndex < WARM_UP_LOOPS) return@repeat
            wholeLoops += frames.meanFrame()
            for ((event, frame) in events) {
                spikes.getOrPut(event) { mutableListOf() } += frames[frame]
                windows.getOrPut(event) { mutableListOf() } += frames.subList(frame, minOf(frame + WINDOW_FRAMES, morphs.size)).meanFrame()
            }
        }
        for (event in spikes.keys.sorted()) {
            println("$name, $event: frame ${spikes.getValue(event).medianPhases()}; half second from there, per frame ${windows.getValue(event).medianPhases()}")
        }
        println("$name, whole loop: per frame ${wholeLoops.medianPhases()}")
    }

    /**
     * The frames where each of the loop's morphs leaves its start or reaches its end, numbered in loop order and
     * named by [legs], one name per morph in the order the loop runs them.
     */
    private fun loopEvents(morphs: List<MorpheusGraphMorph>, legs: List<String>): Map<String, Int> {
        val frames = 1 until morphs.size
        val starts = frames.filter { morphs[it - 1].fraction == 0f && morphs[it].fraction > 0f }
        val ends = frames.filter { morphs[it - 1].fraction < 1f && morphs[it].fraction == 1f }
        require(starts.size == legs.size && ends.size == legs.size) { "Expected ${legs.size} morphs, found ${starts.size} starts and ${ends.size} ends" }
        return legs.flatMapIndexed { index, leg -> listOf("${index * 2 + 1} $leg start" to starts[index], "${index * 2 + 2} $leg end" to ends[index]) }.toMap()
    }

    private companion object {
        const val MID_FRACTION = 0.5f
        const val END_SETTLES = 2
        const val WARM_UP_RUNS = 200
        const val MEASURED_BATCHES = 200
        const val WINDOW_FRAMES = 30
        const val WARM_UP_LOOPS = 1
        const val MEASURED_LOOPS = 7
    }
}
