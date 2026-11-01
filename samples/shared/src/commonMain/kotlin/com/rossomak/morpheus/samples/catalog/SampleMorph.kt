package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphComets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.MorphCycle
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.SampleSpecs
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration

/** The mesh density of the morph samples, dense enough that a shape's rim reads as a rim. */
private const val MORPH_DENSITY = 40f

/** This spec at [density] with hard-disc blooms, the look of the morph samples. Specs that share a density stay one morph family. */
internal fun MorpheusGraphSpec.forMorphSample(density: Float = MORPH_DENSITY) = copy(
    layers = layers.map { layer ->
        val atDensity = layer.copy(density = density)
        if (layer.shapes.none { it is Bloom }) {
            atDensity
        } else {
            atDensity.copy(fillChance = 1f, shapes = layer.shapes.map { if (it is Bloom) it.copy(plateau = 1f) else it })
        }
    },
)

internal fun MorpheusGraphSpec.withoutComets() = copy(layers = layers.map { it.copy(comets = MorpheusGraphComets(strength = 0f)) })

/** The easings of the morphs called [morphNames], in that order. */
internal fun SampleSpecs.easings(vararg morphNames: String): List<Easing> = morphNames.map { easing(it) }

/** A full-surface graph cycling through [specs] on a possibly shared [cycleTime]. */
@Composable
internal fun SampleMorph(specs: List<MorpheusGraphSpec>, easings: List<Easing>, timing: MorphTiming, cycleTime: State<Duration>, modifier: Modifier = Modifier) {
    val state = rememberMorpheusGraphMorphState(specs.first())
    MorphCycle(state, specs, easings, timing, cycleTime)
    Box(modifier.fillMaxSize().morpheusGraphBackground(state))
}

/** A [SampleMorph] on a cycle of its own. */
@Composable
internal fun SampleMorph(specs: List<MorpheusGraphSpec>, easings: List<Easing>, timing: MorphTiming, modifier: Modifier = Modifier) {
    SampleMorph(specs, easings, timing, rememberCycleTime(period = timing.cyclePeriod(legs = specs.size)), modifier)
}
