package com.rossomak.morpheus.samples.showcase

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.rossomak.morpheus.MorpheusGraphMorphState
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.rememberCycleTime

/**
 * Drives [state] around [specs] in turn on a loop, one leg per spec: each leg holds its spec for half the
 * [timing]'s hold, morphs to the next spec over its morph through its own entry of [easings], then holds the
 * next spec for the other half. The loop starts halfway through the first spec's hold. Each leg is a morph of its own, from its spec to the next, so a way back plans its comets from
 * where it starts instead of replaying the way there. The cycle follows the frame clock, and a tuned spec easing
 * or timing takes effect from the next frame.
 */
@Composable
internal fun MorphCycle(state: MorpheusGraphMorphState, specs: List<MorpheusGraphSpec>, easings: List<Easing>, timing: MorphTiming) {
    require(specs.size == easings.size) { "Each of the ${specs.size} legs needs an easing, got ${easings.size}" }
    val currentSpecs by rememberUpdatedState(specs)
    val currentEasings by rememberUpdatedState(easings)
    val currentTiming by rememberUpdatedState(timing)
    val cycleTime = rememberCycleTime(period = timing.leg * specs.size)
    LaunchedEffect(state) {
        var seeking: Pair<MorpheusGraphSpec, MorpheusGraphSpec>? = null
        snapshotFlow { Triple(currentSpecs, currentEasings, cycleTime.value) }.collect { (legSpecs, legEasings, time) ->
            val leg = currentTiming.leg
            val index = (time / leg).toInt().coerceIn(0, legSpecs.size - 1)
            val from = legSpecs[index]
            val to = legSpecs[(index + 1) % legSpecs.size]
            // Each leg starts its seek from the spec it holds, so a new leg or a tuned spec restarts cleanly.
            if (seeking != (from to to)) {
                state.snapTo(from)
                seeking = from to to
            }
            val linear = ((time - leg * index - currentTiming.hold / 2) / currentTiming.morph).toFloat().coerceIn(0f, 1f)
            state.seekTo(to, legEasings[index].transform(linear))
        }
    }
}
