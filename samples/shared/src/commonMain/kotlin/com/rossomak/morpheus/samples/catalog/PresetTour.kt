package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.MorphCycle
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleLabel
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration.Companion.seconds

private val PresetNames = listOf(
    "Baseline",
    "Ribbon mesh",
    "Ribbon plus ghost",
    "Glow constellation",
    "Corner bloom",
    "Depth fog",
    "Living Delaunay",
)

private const val HALF_MORPHED = 0.5

private val PresetTourTiming = MorphTiming(hold = 2.seconds, morph = 1.seconds)

/**
 * Every preset in turn, each morphing into the next and the last into the first, on a loop. The presets belong to
 * different morph families, so each morph dissolves one mesh into the other instead of reshaping it. A label
 * names the preset on stage.
 */
@Composable
fun PresetTourSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = MorpheusGraphPresets.all,
    names: List<String> = PresetNames,
    easings: List<Easing> = specs.map { FastOutSlowInEasing },
    timing: MorphTiming = PresetTourTiming,
) {
    val state = rememberMorpheusGraphMorphState(specs.first())
    val cycleTime = rememberCycleTime(period = timing.cyclePeriod(legs = specs.size))
    MorphCycle(state, specs, easings, timing, cycleTime)
    val currentTiming by rememberUpdatedState(timing)
    // A preset is on stage until its morph is half way, then the next one is; derived, so the label recomposes only when it changes.
    val onStage by remember(specs.size) {
        derivedStateOf {
            val leg = currentTiming.leg
            val index = (cycleTime.value / leg).toInt().coerceIn(0, specs.size - 1)
            val morphed = (cycleTime.value - leg * index - currentTiming.hold / 2) / currentTiming.morph
            if (morphed < HALF_MORPHED) index else (index + 1) % specs.size
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().morpheusGraphBackground(state))
        SampleLabel(text = names[onStage], modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
    }
}

private val TourMorphNames = PresetNames.mapIndexed { index, name -> "$name → ${PresetNames[(index + 1) % PresetNames.size]}" }

internal val presetTourSample = Sample(
    id = "preset-tour",
    title = "Preset tour",
    description = "Every preset in turn, each morphing into the next, with the preset on stage named in the corner.",
    category = SampleCategory.BasicShapes,
    specs = PresetNames.zip(MorpheusGraphPresets.all) { name, spec -> TunableSpec(name, spec) },
    morphs = TourMorphNames.map { TunableMorph(it) },
    timing = PresetTourTiming,
    content = { specs ->
        PresetTourSample(
            specs = PresetNames.map { specs[it] },
            easings = TourMorphNames.map { specs.easing(it) },
            timing = specs.timing,
        )
    },
)
