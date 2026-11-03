@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Times every leg of the showcase and the playground frame by frame, as each sample plays it at 60 fps, through [GalleryLoop]
 * on the recipe's canvas, each `advance` phase every layer summed: the morph's first frame, where its meshes and
 * comets are built; the mean frame over the morph; the slowest frame after the first, with its fraction and the phase
 * that dominates it; and the mean frame in each fifth of the morph, to show when the cost lands. Each leg runs on
 * fresh fields, after a frame of the screen it starts from; medians over the runs. Printed, not asserted; runs on
 * [benchmark], see `docs/benchmark.md`.
 */
class MorphPhaseBenchmark {

    @Test
    fun `the showcase's morph phases`() = benchmark(this::class, "showcase") {
        report(
            "showcase",
            leg("pills to ribbons", heroPills, heroRibbons, HERO_MORPH_SECONDS, FastOutSlowInEasing),
            leg("ribbons to pills", heroRibbons, heroPills, HERO_MORPH_SECONDS, FastOutSlowInEasing),
        )
    }

    @Test
    fun `the playground's morph phases`() = benchmark(this::class, "playground") {
        report("playground", *playgroundLegs.map { (name, morph) -> leg(name, morph.from, morph.to, PLAYGROUND_MORPH_SECONDS, FastOutSlowInEasing) }.toTypedArray())
    }

    /** A leg of a sample's loop: its morph's start, played untimed on fresh fields, and its morph's frames. */
    private class Leg(val name: String, val start: MorpheusGraphMorph, val morphs: List<MorpheusGraphMorph>)

    /** The morph from [from] to [to] over [seconds] through [easing], after a frame of its start. */
    private fun leg(name: String, from: MorpheusGraphSpec, to: MorpheusGraphSpec, seconds: Float, easing: Easing): Leg {
        val start = MorpheusGraphMorph(from, to, 0f)
        val frames = seconds * FPS
        return Leg(name, start, List(frames.roundToInt()) { frame -> start.copy(fraction = easing.transform((frame + 1) / frames)) })
    }

    /** Prints [sample]'s rows, leg by leg. */
    private fun report(sample: String, vararg legs: Leg) {
        for (leg in legs) {
            val runs = List(WARM_UP_RUNS + MEASURED_RUNS) { run(leg) }.drop(WARM_UP_RUNS)
            val slowest = runs.sortedBy { it.slowest.sum() }[runs.size / 2]
            val slowestPhase = GalleryLoop.ADVANCE_PHASES[slowest.slowest.indices.maxBy { slowest.slowest[it] }].name.lowercase()
            println("$sample, ${leg.name}: first frame ${runs.map { it.frames.first() }.medianPhases()}")
            println("$sample, ${leg.name}: per frame ${runs.map { it.frames.meanFrame() }.medianPhases()}")
            println(
                "$sample, ${leg.name}: slowest after the first ${slowest.slowest.sum().millis()} ms at fraction " +
                    "${(slowest.slowestFraction * FRACTION_DIGITS).roundToInt() / FRACTION_DIGITS}, mostly $slowestPhase",
            )
            for (fifth in 0 until FIFTHS) {
                val fifthFrames = runs.map { run -> run.frames.subList(fifth * run.frames.size / FIFTHS, (fifth + 1) * run.frames.size / FIFTHS).meanFrame() }
                println("$sample, ${leg.name}: fifth ${fifth + 1}, per frame ${fifthFrames.medianPhases()}")
            }
        }
    }

    /** One run of [leg] on fresh fields: each morph frame's phases, and the slowest frame after the first. */
    private fun run(leg: Leg): LegRun {
        val loop = GalleryLoop()
        loop.frame(leg.start)
        val frames = leg.morphs.map { loop.frame(it) }
        val slowest = (1 until frames.size).maxBy { frames[it].sum() }
        return LegRun(frames, frames[slowest], leg.morphs[slowest].fraction)
    }

    private class LegRun(val frames: List<LongArray>, val slowest: LongArray, val slowestFraction: Float)

    private companion object {
        const val FPS = 60f
        const val HERO_MORPH_SECONDS = 2.5f
        const val PLAYGROUND_MORPH_SECONDS = 1.5f
        const val FIFTHS = 5
        const val FRACTION_DIGITS = 100f
        const val WARM_UP_RUNS = 1
        const val MEASURED_RUNS = 7
    }
}
