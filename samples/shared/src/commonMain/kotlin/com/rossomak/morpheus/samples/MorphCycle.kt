package com.rossomak.morpheus.samples

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.rossomak.morpheus.MorpheusGraphMorphState
import com.rossomak.morpheus.MorpheusGraphSpec
import kotlin.time.Duration

/**
 * Drives [state] around [specs] in turn on a loop, one leg per spec: each leg holds its spec for half the
 * [timing]'s hold, morphs to the next spec over its morph through its own entry of [easings], then holds the
 * next spec for the other half. The loop starts halfway through the first spec's hold. Each leg is a morph of
 * its own, from its spec to the next, so a way back plans its comets from where it starts instead of
 * replaying the way there. A tuned spec easing or timing takes effect from the next frame.
 *
 * The cycle follows [cycleTime], which the caller creates with [rememberCycleTime] over [cyclePeriod], so that
 * several states can share one cycle, and one [CycleControl] pauses and scrubs them together.
 */
@Composable
internal fun MorphCycle(state: MorpheusGraphMorphState, specs: List<MorpheusGraphSpec>, easings: List<Easing>, timing: MorphTiming, cycleTime: State<Duration>) {
    require(specs.size == easings.size) { "Each of the ${specs.size} legs needs an easing, got ${easings.size}" }
    val currentSpecs by rememberUpdatedState(specs)
    val currentEasings by rememberUpdatedState(easings)
    val currentTiming by rememberUpdatedState(timing)
    LaunchedEffect(state) {
        var seeking: Pair<MorpheusGraphSpec, MorpheusGraphSpec>? = null
        snapshotFlow { CycleInputs(currentSpecs, currentEasings, currentTiming, cycleTime.value) }.collect { inputs ->
            val legSpecs = inputs.specs
            val leg = cycleLegAt(inputs.time, inputs.timing, inputs.easings)
            val from = legSpecs[leg.index]
            val to = legSpecs[(leg.index + 1) % legSpecs.size]
            // Each leg starts its seek from the spec it holds, so a new leg or a tuned spec restarts cleanly.
            if (seeking != (from to to)) {
                state.snapTo(from)
                seeking = from to to
            }
            state.seekTo(to, leg.fraction)
        }
    }
}

/** The [index] of a cycle's current leg and its eased morph [fraction]. */
internal data class CycleLeg(val index: Int, val fraction: Float)

/** The leg [time] falls in (clamped to the last) and its eased morph fraction; each leg holds half the hold, morphs, then holds half. */
internal fun cycleLegAt(time: Duration, timing: MorphTiming, easings: List<Easing>): CycleLeg {
    val progress = legProgressAt(time, timing, legs = easings.size)
    return CycleLeg(progress.index, easings[progress.index].transform(progress.linear))
}

/** A cycle's current leg and its morph's [linear] progress, 0 to 1, before any easing. */
internal data class LegProgress(val index: Int, val linear: Float)

/** Where [time] falls in a cycle of [legs]; the last leg takes any overrun. */
internal fun legProgressAt(time: Duration, timing: MorphTiming, legs: Int): LegProgress {
    val index = (time / timing.leg).toInt().coerceIn(0, legs - 1)
    val linear = ((time - timing.leg * index - timing.hold / 2) / timing.morph).toFloat().coerceIn(0f, 1f)
    return LegProgress(index, linear)
}

/**
 * The position of a cycle that walks the specs in [walk] order, one leg per step, on a clock of its own: each leg
 * moves it from one index to the next through its entry of [easings] and holds it there for the rest of the leg.
 */
@Composable
internal fun rememberWalkPosition(walk: List<Int>, easings: List<Easing>, timing: MorphTiming): State<Float> {
    require(easings.size == walk.size - 1) { "Each of the ${walk.size - 1} legs needs an easing, got ${easings.size}" }
    val cycleTime = rememberCycleTime(period = timing.cyclePeriod(legs = easings.size))
    val currentEasings by rememberUpdatedState(easings)
    val currentTiming by rememberUpdatedState(timing)
    return remember(walk) { derivedStateOf { walkPosition(cycleTime.value, walk, currentTiming, currentEasings) } }
}

/** Where on [walk] a cycle is at [time], as a position among the specs. */
internal fun walkPosition(time: Duration, walk: List<Int>, timing: MorphTiming, easings: List<Easing>): Float {
    val leg = cycleLegAt(time, timing, easings)
    val from = walk[leg.index]
    return from + (walk[leg.index + 1] - from) * leg.fraction
}

/** What [MorphCycle] re-seeks on, so a change to any of it moves the morph even while the cycle is paused. */
private data class CycleInputs(val specs: List<MorpheusGraphSpec>, val easings: List<Easing>, val timing: MorphTiming, val time: Duration)

/** How long one trip around [legs] legs takes: the period to give [rememberCycleTime] for a [MorphCycle] of that many specs. */
internal fun MorphTiming.cyclePeriod(legs: Int): Duration = leg * legs
