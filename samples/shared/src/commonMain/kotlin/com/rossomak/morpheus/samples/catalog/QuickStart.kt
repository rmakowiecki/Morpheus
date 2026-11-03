package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.animateMorpheusGraphMorph
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration.Companion.seconds

private const val GLOW_CONSTELLATION = "Glow constellation"
private const val RIBBON = "Ribbon"
private const val CORNER_BLOOM = "Corner bloom"

private val QuickStartPeriod = 6.seconds
private const val QUICK_START_MORPH_MILLIS = 2_000

/** A graph from a preset, behind a composable's content: the whole of the smallest integration. */
@Composable
fun QuickStartSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.glowConstellation) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec))
}

/**
 * A graph that morphs between two specs whenever its target changes, here every half period. The two
 * presets belong to one morph family, so the nodes stay in place while the shape changes. In an app the
 * target comes from state, such as a selected tab, instead of a clock.
 */
@Composable
fun QuickStartMorphSample(
    modifier: Modifier = Modifier,
    first: MorpheusGraphSpec = MorpheusGraphPresets.MorphFamily.ribbon,
    second: MorpheusGraphSpec = MorpheusGraphPresets.MorphFamily.cornerBloom,
) {
    val cycleTime = rememberCycleTime(period = QuickStartPeriod)
    val onSecond by remember { derivedStateOf { cycleTime.value >= QuickStartPeriod / 2 } }
    val morph = animateMorpheusGraphMorph(target = if (onSecond) second else first, animationSpec = tween(QUICK_START_MORPH_MILLIS))
    Box(modifier.fillMaxSize().morpheusGraphBackground(morph))
}

internal val quickStartSample = Sample(
    id = "quick-start",
    title = "Quick start",
    description = "A graph from a preset behind a composable: one modifier, no assets.",
    category = SampleCategory.StartHere,
    specs = listOf(TunableSpec(GLOW_CONSTELLATION, MorpheusGraphPresets.glowConstellation)),
    content = { specs -> QuickStartSample(spec = specs[GLOW_CONSTELLATION]) },
)

internal val quickStartMorphSample = Sample(
    id = "quick-start-morph",
    title = "Quick start: morph",
    description = "The graph morphs in place from a ribbon into a corner bloom and back, whenever its target spec changes.",
    category = SampleCategory.StartHere,
    specs = listOf(
        TunableSpec(RIBBON, MorpheusGraphPresets.MorphFamily.ribbon),
        TunableSpec(CORNER_BLOOM, MorpheusGraphPresets.MorphFamily.cornerBloom),
    ),
    content = { specs -> QuickStartMorphSample(first = specs[RIBBON], second = specs[CORNER_BLOOM]) },
)
