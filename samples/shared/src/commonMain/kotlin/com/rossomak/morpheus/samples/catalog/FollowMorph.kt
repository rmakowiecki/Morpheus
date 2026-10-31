@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.Follow
import com.rossomak.morpheus.MorpheusGraphPresets.MorphFamily
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleLabel
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.rememberWalkPosition
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val SpecNames = listOf("Uniform field", "Ribbon", "Corner bloom", "Centre disc")

private val followSpecs = listOf(MorphFamily.uniformField, MorphFamily.ribbon, MorphFamily.cornerBloom, MorphFamily.centreDisc).map { it.forMorphSample() }

/** The spec each leg of the walk starts at, then the one it ends at: out to the last spec and back to the first. */
private val Walk = listOf(0, 1, 2, 3, 2, 1, 0)

private val WalkMorphNames = Walk.zipWithNext { from, to -> "${SpecNames[from]} → ${SpecNames[to]}" }

private val FollowMorphTiming = MorphTiming(hold = 500.milliseconds, morph = 1500.milliseconds)

/** The background never crosses from one spec to the next faster than this share of a morph, so a quick scrub leaves it behind the position. */
private const val SPEED_LIMIT = 3

/**
 * A morph that follows a position across [specs], here a walk out to the last spec and back. The label gives the
 * position and the spec nearest it; scrub quickly and the background trails behind, catching up at a limited pace.
 */
@Composable
fun FollowMorphSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = followSpecs,
    names: List<String> = SpecNames,
    easings: List<Easing> = WalkMorphNames.map { FastOutSlowInEasing },
    timing: MorphTiming = FollowMorphTiming,
) {
    val state = rememberMorpheusGraphMorphState(specs.first())
    val position = rememberWalkPosition(Walk, easings, timing)
    state.Follow(specs, minMorphDuration = timing.morph / SPEED_LIMIT) { position.value }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().morpheusGraphBackground(state))
        SampleLabel(text = "${position.value.toTenths()} · ${names[position.value.roundToInt()]}", modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}

private fun Float.toTenths(): String = (this * 10).roundToInt().let { "${it / 10}.${it % 10}" }

internal val followMorphSample = Sample(
    id = "follow-morph",
    title = "Follow morph",
    description = "A morph that follows a position across four specs, out to the last and back; scrub fast and the background trails behind.",
    category = SampleCategory.Morphs,
    specs = SpecNames.zip(followSpecs) { name, spec ->
        TunableSpec(name, spec)
    },
    morphs = WalkMorphNames.map { TunableMorph(it) },
    timing = FollowMorphTiming,
    content = { specs ->
        FollowMorphSample(
            specs = SpecNames.map { specs[it] },
            easings = WalkMorphNames.map { specs.easing(it) },
            timing = specs.timing,
        )
    },
)
