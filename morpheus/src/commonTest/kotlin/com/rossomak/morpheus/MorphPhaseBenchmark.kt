@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Times every leg of each sample with comets frame by frame, as the sample plays it at 60 fps, through [GalleryLoop]
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

    @Test
    fun `the facing arcs' morph phases`() = benchmark(this::class, "facing arcs") {
        report(
            "facing arcs",
            leg("left to right", facingArcsLeft, facingArcsRight, FACING_ARCS_MORPH_SECONDS, LinearEasing),
            leg("right to left", facingArcsRight, facingArcsLeft, FACING_ARCS_MORPH_SECONDS, LinearEasing),
        )
    }

    @Test
    fun `the comets sample's morph phases`() = benchmark(this::class, "comets") {
        report(
            "comets",
            leg("centre disc to corner pair", cometsCentreDisc, cometsCornerPair, COMETS_MORPH_SECONDS, FastOutSlowInEasing),
            leg("corner pair to centre disc", cometsCornerPair, cometsCentreDisc, COMETS_MORPH_SECONDS, FastOutSlowInEasing),
        )
    }

    /** Its second leg is cut half way, and the rest of its morph time goes from the screen as it is to the bottom centre. */
    @Test
    fun `the retarget sample's morph phases`() = benchmark(this::class, "retarget") {
        val cut = MorpheusGraphMorph(retargetTopLeft, retargetTopRight, RETARGET_CUT)
        report(
            "retarget",
            leg("top right to top left", retargetTopRight, retargetTopLeft, RETARGET_MORPH_SECONDS, LinearEasing),
            leg("top left to top right, cut half way", retargetTopLeft, retargetTopRight, RETARGET_MORPH_SECONDS, LinearEasing, until = RETARGET_CUT),
            leg(
                "interrupted: now to bottom centre",
                lerp(retargetTopLeft, retargetTopRight, RETARGET_CUT),
                retargetBottomCentre,
                RETARGET_MORPH_SECONDS * (1f - RETARGET_CUT),
                LinearEasing,
                origin = cut,
                prelude = listOf(cut),
            ),
            leg("bottom centre to top right", retargetBottomCentre, retargetTopRight, RETARGET_MORPH_SECONDS, LinearEasing),
        )
    }

    /** A leg of a sample's loop: the frames played untimed before it, on fresh fields, and its morph's frames. */
    private class Leg(val name: String, val prelude: List<MorpheusGraphMorph>, val morphs: List<MorpheusGraphMorph>)

    /**
     * The morph from [from] to [to] over [seconds] through [easing], up to [until] of its time, after [prelude] and
     * a frame of its start; [origin] is the morph it interrupts.
     */
    @Suppress("LongParameterList") // The name, the two ends, the timing and easing, the cut and the interrupted morph.
    private fun leg(
        name: String,
        from: MorpheusGraphSpec,
        to: MorpheusGraphSpec,
        seconds: Float,
        easing: Easing,
        until: Float = 1f,
        origin: MorpheusGraphMorph? = null,
        prelude: List<MorpheusGraphMorph> = emptyList(),
    ): Leg {
        val start = MorpheusGraphMorph(from, to, 0f, origin)
        val frames = seconds * FPS
        return Leg(name, prelude + start, List((frames * until).roundToInt()) { frame -> start.copy(fraction = easing.transform((frame + 1) / frames)) })
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
        leg.prelude.forEach { loop.frame(it) }
        val frames = leg.morphs.map { loop.frame(it) }
        val slowest = (1 until frames.size).maxBy { frames[it].sum() }
        return LegRun(frames, frames[slowest], leg.morphs[slowest].fraction)
    }

    private class LegRun(val frames: List<LongArray>, val slowest: LongArray, val slowestFraction: Float)

    private companion object {
        const val FPS = 60f
        const val HERO_MORPH_SECONDS = 2.5f
        const val PLAYGROUND_MORPH_SECONDS = 1.5f
        const val FACING_ARCS_MORPH_SECONDS = 1f
        const val COMETS_MORPH_SECONDS = 2.25f
        const val RETARGET_MORPH_SECONDS = 0.5f
        const val RETARGET_CUT = 0.5f
        const val FIFTHS = 5
        const val FRACTION_DIGITS = 100f
        const val WARM_UP_RUNS = 1
        const val MEASURED_RUNS = 7
    }
}
