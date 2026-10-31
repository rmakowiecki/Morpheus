@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import kotlin.time.Duration.Companion.seconds

private const val LEFT = "Left"
private const val RIGHT = "Right"
private const val LEFT_TO_RIGHT = "$LEFT → $RIGHT"
private const val RIGHT_TO_LEFT = "$RIGHT → $LEFT"

/** Dense, with nodes of several sizes, so the mesh reads as a mesh while it changes shape. */
private val fillMorphLayer = shapeSampleLayer.copy(
    density = 40f,
    strokeWidthDp = 1.5f,
    edgeAlpha = 0.6f,
    nodeSizeDp = 2.4f,
    nodeSizeVariance = 0.4f,
)

/** A tall rect hugging one side, tilted so the two mirror images overlap in a slanted band across the middle. */
private fun slantedRectSpec(x: Float, rotation: Float, fill: MorpheusGraphFill) = MorpheusGraphSpec(
    seed = 12,
    layers = listOf(fillMorphLayer.copy(shapes = listOf(Rect(x = x, y = 0.5f, width = 1.1f, height = 1.3f, rotation = rotation, cornerRadius = 0.06f)))),
    fill = fill,
).withoutComets()

private val leftSpec = slantedRectSpec(x = 0f, rotation = -110f, fill = MorpheusGraphFill.Solid(SampleStyle.blue))
private val rightSpec = slantedRectSpec(x = 1f, rotation = -70f, fill = MorpheusGraphFill.linear(listOf(SampleStyle.orange, SampleStyle.magenta)))

private val FillMorphTiming = MorphTiming(hold = 1.seconds, morph = 4.seconds)

/**
 * A rectangle on the left in a solid fill and one on the right in a linear gradient morph into each other and
 * back on one mesh, so the morph's frontier widens and narrows across the overlap as the fraction moves.
 */
@Composable
fun FillMorphSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = listOf(leftSpec, rightSpec),
    easings: List<Easing> = specs.map { FastOutSlowInEasing },
    timing: MorphTiming = FillMorphTiming,
) {
    SampleMorph(specs, easings, timing, modifier)
}

internal val fillMorphSample = Sample(
    id = "fill-morph",
    title = "Fill morph",
    description = "A solid blue rectangle on the left and a linear orange one on the right swap shape and fill on the same mesh.",
    category = SampleCategory.Morphs,
    specs = listOf(TunableSpec(LEFT, leftSpec), TunableSpec(RIGHT, rightSpec)),
    morphs = listOf(TunableMorph(LEFT_TO_RIGHT), TunableMorph(RIGHT_TO_LEFT)),
    timing = FillMorphTiming,
    content = { specs ->
        FillMorphSample(
            specs = listOf(specs[LEFT], specs[RIGHT]),
            easings = specs.easings(LEFT_TO_RIGHT, RIGHT_TO_LEFT),
            timing = specs.timing,
        )
    },
)
