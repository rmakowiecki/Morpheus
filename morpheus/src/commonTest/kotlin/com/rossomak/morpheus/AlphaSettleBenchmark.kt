@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Times the alpha settle per frame, every layer summed, in three steps: [ReferenceAlphaSettle], the same `withConstants`,
 * and [LayerField]'s own. Frames replay through [GalleryLoop] on the recipe's canvas; the references settle each frame's
 * fields after their `advance`, outside the field's own timing. Every bias favours the references: they time `settle`
 * alone, where the field's lap also rebuilds its incident edges and relevant nodes, and they read node depths from an
 * array. The field's own settle always runs first, inside `advance`, then the two references in a fixed order; the
 * order is not alternated; running first, the field's own step is the one a cold cache would penalize, if any is.
 * Printed, not asserted; runs on [benchmark], see `docs/benchmark.md`.
 */
class AlphaSettleBenchmark {

    @Test
    fun `the alpha settle over the showcase's loop`() = benchmark(this::class, "showcase loop") {
        timeSteps("showcase loop", List(SHOWCASE_LOOP_FRAMES) { frame -> showcaseMorphAt(frame * SHOWCASE_FRAME_SECONDS) })
    }

    @Test
    fun `the alpha settle over the playground's loop`() = benchmark(this::class, "playground loop") {
        timeSteps("playground loop", List(PLAYGROUND_LOOP_FRAMES) { frame -> playgroundMorphAt(frame * SHOWCASE_FRAME_SECONDS) })
    }

    @Test
    fun `the alpha settle on baseline at density 20`() = benchmark(this::class, "baseline 20") { timeStill("baseline at density 20", baselineAt(DENSITY_LOW)) }

    @Test
    fun `the alpha settle on baseline at density 48`() = benchmark(this::class, "baseline 48") { timeStill("baseline at density 48", baselineAt(DENSITY_MEDIUM)) }

    @Test
    fun `the alpha settle on baseline at density 96`() = benchmark(this::class, "baseline 96") { timeStill("baseline at density 96", baselineAt(DENSITY_HIGH)) }

    @Test
    fun `the alpha settle on living Delaunay`() = benchmark(this::class, "living Delaunay") { timeStill("living Delaunay", MorpheusGraphPresets.livingDelaunay) }

    private fun baselineAt(density: Float) = MorpheusGraphPresets.baseline.let { spec -> spec.copy(layers = spec.layers.map { it.copy(density = density) }) }

    private fun timeStill(name: String, spec: MorpheusGraphSpec) {
        val still = GalleryLoop.still(spec)
        timeSteps(name, List(STILL_LOOP_FRAMES) { still })
    }

    /** Replays [morphs], one a frame, as a loop, and prints each step's mean per frame, the median over the loops. */
    private fun timeSteps(name: String, morphs: List<MorpheusGraphMorph>) {
        val loop = GalleryLoop()
        val references = HashMap<LayerField, ReferenceAlphaSettle>()
        val withConstants = HashMap<LayerField, ReferenceAlphaSettle>()
        val stepLoops = Step.entries.map { ArrayList<Long>() }
        repeat(WARM_UP_LOOPS + MEASURED_LOOPS) { loopIndex ->
            val nanos = LongArray(Step.entries.size)
            for (morph in morphs) {
                val phases = loop.frame(morph) { fields ->
                    for (field in fields) {
                        nanos[Step.Reference.ordinal] += timeSettle(references.getOrPut(field) { ReferenceAlphaSettle() }, field)
                        nanos[Step.PerNodeConstants.ordinal] += timeSettle(withConstants.getOrPut(field) { ReferenceAlphaSettle(withConstants = true) }, field)
                    }
                }
                nanos[Step.FrontierRounds.ordinal] += phases[GalleryLoop.ADVANCE_PHASES.indexOf(FramePhase.AlphaSettle)]
            }
            if (loopIndex >= WARM_UP_LOOPS) Step.entries.forEach { step -> stepLoops[step.ordinal] += nanos[step.ordinal] / morphs.size }
        }
        val steps = Step.entries.joinToString { step -> "${step.label} ${stepLoops[step.ordinal].sorted()[MEASURED_LOOPS / 2].millis()} ms" }
        println("$name, alpha settle per frame, median of $MEASURED_LOOPS loops of ${morphs.size} frames: $steps")
    }

    private fun timeSettle(reference: ReferenceAlphaSettle, field: LayerField): Long {
        reference.load(field)
        val started = TimeSource.Monotonic.markNow()
        reference.settle()
        return started.elapsedNow().inWholeNanoseconds
    }

    private enum class Step(val label: String) {
        Reference("reference"),
        PerNodeConstants("per-node constants"),
        FrontierRounds("frontier rounds"),
    }

    private companion object {
        const val DENSITY_LOW = 20f
        const val DENSITY_MEDIUM = 48f
        const val DENSITY_HIGH = 96f

        /**
         * A still case's loop: two seconds at 60 frames a second. Every frame is the same still, so a longer loop only
         * adds time; at density 96, ten seconds kept the wasm run past Karma's 600 s limit.
         */
        const val STILL_LOOP_FRAMES = 120
        const val WARM_UP_LOOPS = 1
        const val MEASURED_LOOPS = 5
    }
}
