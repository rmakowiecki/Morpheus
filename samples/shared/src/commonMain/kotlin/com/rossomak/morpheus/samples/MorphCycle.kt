package com.rossomak.morpheus.samples

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
            val legTiming = inputs.timing
            val time = inputs.time
            val leg = legTiming.leg
            val index = (time / leg).toInt().coerceIn(0, legSpecs.size - 1)
            val from = legSpecs[index]
            val to = legSpecs[(index + 1) % legSpecs.size]
            // Each leg starts its seek from the spec it holds, so a new leg or a tuned spec restarts cleanly.
            if (seeking != (from to to)) {
                state.snapTo(from)
                seeking = from to to
            }
            val linear = ((time - leg * index - legTiming.hold / 2) / legTiming.morph).toFloat().coerceIn(0f, 1f)
            state.seekTo(to, inputs.easings[index].transform(linear))
        }
    }
}

/** What [MorphCycle] re-seeks on, so a change to any of it moves the morph even while the cycle is paused. */
private data class CycleInputs(val specs: List<MorpheusGraphSpec>, val easings: List<Easing>, val timing: MorphTiming, val time: Duration)

/** How long one trip around [legs] legs takes: the period to give [rememberCycleTime] for a [MorphCycle] of that many specs. */
internal fun MorphTiming.cyclePeriod(legs: Int): Duration = leg * legs
