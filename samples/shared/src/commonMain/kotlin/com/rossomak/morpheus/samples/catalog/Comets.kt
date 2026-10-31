@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphComets
import com.rossomak.morpheus.MorpheusGraphPresets.MorphFamily
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import kotlin.time.Duration.Companion.milliseconds

/** Larger and sparkier than a layer's default, so the streams read at a glance. */
private val sampleComets = MorpheusGraphComets(size = 1.5f, spark = 0.8f)

private const val COMETS_DENSITY = 70f

/** Blooms shrink to this share of their reach, so the nodes have further to travel and the streams are easier to follow. */
private const val BLOOM_REACH_SHARE = 0.65f

private fun MorpheusGraphSpec.withSampleComets() = forMorphSample(COMETS_DENSITY).let { spec ->
    spec.copy(
        layers = spec.layers.map { layer ->
            layer.copy(
                shapes = layer.shapes.map { if (it is Bloom) it.copy(reach = it.reach * BLOOM_REACH_SHARE) else it },
                comets = sampleComets,
            )
        },
    )
}

private val centreDisc = MorphFamily.centreDisc.withSampleComets()
private val cornerPair = MorphFamily.cornerPair.withSampleComets()

private const val CENTRE_DISC = "Centre disc"
private const val CORNER_PAIR = "Corner pair"
private const val CENTRE_DISC_TO_CORNER_PAIR = "$CENTRE_DISC → $CORNER_PAIR"
private const val CORNER_PAIR_TO_CENTRE_DISC = "$CORNER_PAIR → $CENTRE_DISC"

private val CometsTiming = MorphTiming(hold = 750.milliseconds, morph = 2250.milliseconds)

/**
 * A disc in the middle and a bloom in each of two corners morph into each other and back, with the comets turned up.
 * Both are one morph family, so the nodes stay put and the comets carry what each shape leaves to the nodes it gains.
 */
@Composable
fun CometsSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = listOf(centreDisc, cornerPair),
    easings: List<Easing> = specs.map { FastOutSlowInEasing },
    timing: MorphTiming = CometsTiming,
) {
    SampleMorph(specs, easings, timing, modifier)
}

internal val cometsSample = Sample(
    id = "comets",
    title = "Comets",
    description = "A disc and a pair of corner blooms morph into each other with comets carrying the nodes across; " +
        "the gallery-only Comet playground runs them on a denser mesh.",
    category = SampleCategory.Morphs,
    specs = listOf(TunableSpec(CENTRE_DISC, centreDisc), TunableSpec(CORNER_PAIR, cornerPair)),
    morphs = listOf(TunableMorph(CENTRE_DISC_TO_CORNER_PAIR), TunableMorph(CORNER_PAIR_TO_CENTRE_DISC)),
    timing = CometsTiming,
    content = { specs ->
        CometsSample(
            specs = listOf(specs[CENTRE_DISC], specs[CORNER_PAIR]),
            easings = specs.easings(CENTRE_DISC_TO_CORNER_PAIR, CORNER_PAIR_TO_CENTRE_DISC),
            timing = specs.timing,
        )
    },
)
