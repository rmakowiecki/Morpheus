@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphPresets.MorphFamily
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleLabel
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.legProgressAt
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Smaller and softer than the morph family's hard disc, so the retargeted morph reads clearly. */
private const val BLOOM_REACH = 0.14f
private const val BLOOM_PLATEAU = 0.4f

/** One bloom in a morph family of its own, so any two of them morph into each other on one mesh, comets and all. */
private fun bloomSpec(x: Float, y: Float): MorpheusGraphSpec = MorphFamily.cornerBloom.withSampleComets().let { spec ->
    spec.copy(layers = spec.layers.map { it.copy(shapes = listOf(Bloom(x = x, y = y, reach = BLOOM_REACH, plateau = BLOOM_PLATEAU))) })
}

/** A bloom's centre, which is also its ring's, as fractions of the surface. */
internal enum class RetargetTarget(val label: String, val x: Float, val y: Float) {
    TopRight("Top right", x = 0.85f, y = 0.2f),
    MiddleLeft("Middle left", x = 0.15f, y = 0.5f),
    BottomRight("Bottom right", x = 0.85f, y = 0.8f),
    ;

    val spec: MorpheusGraphSpec = bloomSpec(x, y)
}

/** A stretch of the script, under its caption, with the target whose ring is drawn bold. */
internal enum class RetargetPhase(val caption: String, val ring: RetargetTarget) {
    ToMiddleLeft("1 · Top right → Middle left", RetargetTarget.MiddleLeft),
    BackToTopRight("2 · Back to top right…", RetargetTarget.TopRight),
    Interrupted("3 · Interrupted: now → Bottom right", RetargetTarget.BottomRight),
    Closing("4 · Bottom right → Top right", RetargetTarget.TopRight),
}

/** The morph toward the top right is cut this far into its morph time. */
private const val INTERRUPT_AT = 0.4f

private const val RETARGET_LEGS = 3

private val RetargetMorphTiming = MorphTiming(hold = 500.milliseconds, morph = 1500.milliseconds)

/** A moment of the script: the target the morph heads for, its eased fraction, and the phase shown. */
internal data class RetargetStep(val target: RetargetTarget, val fraction: Float, val phase: RetargetPhase)

/**
 * Where the script is at [time]. Leg 0 morphs from the top right to the middle left. Leg 1 starts back toward
 * the top right and is cut at [INTERRUPT_AT] of its morph: the rest of the morph goes to the bottom right
 * instead, from wherever the screen is. Leg 2 closes the loop. Legs hold and ease as in a `MorphCycle`.
 * A phase starts with its morph, so the first half of a hold still belongs to the phase before.
 */
internal fun retargetStepAt(time: Duration, timing: MorphTiming, easing: Easing): RetargetStep {
    val (leg, progress) = legProgressAt(time, timing, RETARGET_LEGS)
    val waiting = progress == 0f
    fun step(target: RetargetTarget, linear: Float, phase: RetargetPhase) = RetargetStep(target, easing.transform(linear), phase)
    return when {
        leg == 0 -> step(RetargetTarget.MiddleLeft, progress, if (waiting) RetargetPhase.Closing else RetargetPhase.ToMiddleLeft)
        leg == 2 -> step(RetargetTarget.TopRight, progress, if (waiting) RetargetPhase.Interrupted else RetargetPhase.Closing)
        progress < INTERRUPT_AT -> step(RetargetTarget.TopRight, progress, if (waiting) RetargetPhase.ToMiddleLeft else RetargetPhase.BackToTopRight)
        else -> step(RetargetTarget.BottomRight, (progress - INTERRUPT_AT) / (1f - INTERRUPT_AT), RetargetPhase.Interrupted)
    }
}

/**
 * A bloom morphs from the top right to the middle left, then sets out back toward the top right, and 40% of the
 * way there is sent to the bottom right instead, from the screen as it is. Driven with `seekTo` from the cycle
 * time. The interruption makes each frame depend on the ones before, so the sample can pause but not scrub,
 * and its timing and easing are fixed.
 */
@Composable
fun RetargetMorphSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = RetargetTarget.entries.map { it.spec },
    timing: MorphTiming = RetargetMorphTiming,
) {
    val state = rememberMorpheusGraphMorphState(specs.first())
    val cycleTime = rememberCycleTime(period = timing.cyclePeriod(legs = RETARGET_LEGS))
    val step by remember(timing) { derivedStateOf { retargetStepAt(cycleTime.value, timing, FastOutSlowInEasing) } }
    LaunchedEffect(state, specs) {
        snapshotFlow { step }.collect { state.seekTo(specs[it.target.ordinal], it.fraction) }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().morpheusGraphBackground(state))
        TargetRings(current = step.phase.ring)
        SampleLabel(text = step.phase.caption, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}

/** A ring at each target, the one the morph is heading for drawn bold. */
@Composable
private fun TargetRings(current: RetargetTarget) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxSize()) {
        RetargetTarget.entries.forEach { target ->
            val isCurrent = target == current
            drawCircle(
                color = if (isCurrent) color else color.copy(alpha = 0.35f),
                radius = 14.dp.toPx(),
                center = Offset(size.width * target.x, size.height * target.y),
                style = Stroke(width = (if (isCurrent) 3.dp else 1.5.dp).toPx()),
            )
        }
    }
}

internal val retargetMorphSample = Sample(
    id = "retarget-morph",
    title = "Retarget morph",
    description = "A new target in the middle of a morph carries the morph on from the screen as it is, not from either end.",
    category = SampleCategory.Morphs,
    specs = RetargetTarget.entries.map { TunableSpec(it.label, it.spec) },
    content = { specs -> RetargetMorphSample(specs = RetargetTarget.entries.map { specs[it.label] }) },
)
