@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Times the shape and alpha-settle phases and the whole `advance` per frame with [LayerField.cachesStillFrames] off and
 * on, the two sides interleaved. Printed, not asserted; runs on [benchmark], see `docs/benchmark.md`.
 */
class StillFrameBenchmark {

    @Test
    fun `the showcase's start held still`() = benchmark(this::class, "showcase start") { timeStill("showcase start held still", showcaseMorphAt(0f)) }

    @Test
    fun `the playground's start held still`() = benchmark(this::class, "playground start") { timeStill("playground start held still", playgroundMorphAt(0f)) }

    @Test
    fun `the showcase held part way`() = benchmark(this::class, "showcase held at 0.5") {
        timeStill("showcase held at 0.5", MorpheusGraphMorph(heroPills, heroRibbons, HELD_FRACTION))
    }

    @Test
    fun `baseline at density 20`() = benchmark(this::class, "baseline 20") { timeStill("baseline at density 20", GalleryLoop.still(baselineAt(DENSITY_LOW))) }

    @Test
    fun `baseline at density 48`() = benchmark(this::class, "baseline 48") { timeStill("baseline at density 48", GalleryLoop.still(baselineAt(DENSITY_MEDIUM))) }

    @Test
    fun `baseline at density 96`() = benchmark(this::class, "baseline 96") { timeStill("baseline at density 96", GalleryLoop.still(baselineAt(DENSITY_HIGH))) }

    @Test
    fun `living Delaunay`() = benchmark(this::class, "living Delaunay") { timeStill("living Delaunay", GalleryLoop.still(MorpheusGraphPresets.livingDelaunay)) }

    @Test
    fun `the showcase's loop`() = benchmark(this::class, "showcase loop") {
        timeSides("showcase loop", List(SHOWCASE_LOOP_FRAMES) { frame -> showcaseMorphAt(frame * SHOWCASE_FRAME_SECONDS) })
    }

    @Test
    fun `the playground's loop`() = benchmark(this::class, "playground loop") {
        timeSides("playground loop", List(PLAYGROUND_LOOP_FRAMES) { frame -> playgroundMorphAt(frame * SHOWCASE_FRAME_SECONDS) })
    }

    private fun timeStill(name: String, morph: MorpheusGraphMorph) = timeSides(name, List(STILL_LOOP_FRAMES) { morph })

    /**
     * Replays [morphs], one a frame, as a loop on each side, each side first every other loop so a drift in the machine's
     * speed reaches both, and prints each side's mean per frame, the median over the loops.
     */
    private fun timeSides(name: String, morphs: List<MorpheusGraphMorph>) {
        val sides = listOf(Side(cachesStillFrames = false), Side(cachesStillFrames = true))
        val shares = CacheShares()
        repeat(WARM_UP_LOOPS + MEASURED_LOOPS) { loopIndex ->
            for (side in if (loopIndex % 2 == 0) sides else sides.reversed()) {
                val nanos = LongArray(Step.entries.size)
                for (morph in morphs) {
                    val phases = side.loop.frame(morph) { fields ->
                        if (side.cachesStillFrames) shares.count(fields, sampled = loopIndex == WARM_UP_LOOPS)
                    }
                    nanos[Step.Shape.ordinal] += phases[GalleryLoop.ADVANCE_PHASES.indexOf(FramePhase.Shape)]
                    nanos[Step.AlphaSettle.ordinal] += phases[GalleryLoop.ADVANCE_PHASES.indexOf(FramePhase.AlphaSettle)]
                    nanos[Step.Advance.ordinal] += phases.sum()
                }
                if (loopIndex >= WARM_UP_LOOPS) Step.entries.forEach { step -> side.stepLoops[step.ordinal] += nanos[step.ordinal] / morphs.size }
            }
        }
        val timings = sides.joinToString("; ") { side ->
            side.name + " " + Step.entries.joinToString { step -> "${step.label} ${side.stepLoops[step.ordinal].median().millis()} ms" }
        }
        println(
            "$name, per frame, median of $MEASURED_LOOPS loops of ${morphs.size} frames: $timings; " +
                "with the cache, ${shares.outsidePercent()}% of the pool outside the settle list, presences kept in ${shares.keptPercent()}% of layer frames",
        )
    }

    private class Side(val cachesStillFrames: Boolean) {
        val name = if (cachesStillFrames) "on" else "off"
        val loop = GalleryLoop(cachesStillFrames = cachesStillFrames)
        val stepLoops = Step.entries.map { ArrayList<Long>() }
    }

    /** The share of the pool outside the settle list, and of layer frames that kept their presences, over the sampled frames. */
    private class CacheShares {
        private val hitsSeen = HashMap<LayerField, Int>()
        private var relevant = 0L
        private var pool = 0L
        private var kept = 0
        private var layerFrames = 0

        fun count(fields: List<LayerField>, sampled: Boolean) {
            for (field in fields) {
                val hit = field.stillFrameCacheHits > (hitsSeen.put(field, field.stillFrameCacheHits) ?: 0)
                if (!sampled) continue
                relevant += field.relevantNodeCount
                pool += field.nodeCount
                if (hit) kept++
                layerFrames++
            }
        }

        fun outsidePercent(): Double = percent(1.0 - relevant.toDouble() / pool)

        fun keptPercent(): Double = percent(kept.toDouble() / layerFrames)

        private fun percent(share: Double): Double = (share * PERCENT * TENTHS).roundToInt() / TENTHS
    }

    private enum class Step(val label: String) {
        Shape("shape"),
        AlphaSettle("alpha settle"),
        Advance("advance"),
    }

    private companion object {
        const val HELD_FRACTION = 0.5f
        const val DENSITY_LOW = 20f
        const val DENSITY_MEDIUM = 48f
        const val DENSITY_HIGH = 96f

        /** A still case's loop, as in [AlphaSettleBenchmark]: two seconds at 60 frames a second. */
        const val STILL_LOOP_FRAMES = 120
        const val WARM_UP_LOOPS = 1
        const val MEASURED_LOOPS = 5
        const val PERCENT = 100.0
        const val TENTHS = 10.0
    }
}
