@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.morphs

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleMorph
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleStyle
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.easings
import com.rossomak.morpheus.samples.shapeSampleLayer
import com.rossomak.morpheus.samples.withoutComets
import kotlin.time.Duration.Companion.milliseconds
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
    // Both rects sweep the whole morph together, so the frontier widens across the overlap.
    morphGap = -1f,
)

/** A tall rect hugging one side, tilted so the two mirror images overlap in a slanted band across the middle. */
private fun slantedRectSpec(x: Float, rotation: Float, fill: MorpheusGraphFill) = MorpheusGraphSpec(
    seed = 12,
    layers = listOf(fillMorphLayer.copy(shapes = listOf(Rect(x = x, y = 0.5f, width = 1.1f, height = 1.3f, rotation = rotation, cornerRadius = 0.06f)))),
    fill = fill,
).withoutComets()

private val leftSpec = slantedRectSpec(x = 0f, rotation = -110f, fill = MorpheusGraphFill.Solid(SampleStyle.blue))
private val rightSpec = slantedRectSpec(x = 1f, rotation = -70f, fill = MorpheusGraphFill.linear(listOf(SampleStyle.orange, SampleStyle.magenta)))

private val FillMorphTiming = MorphTiming(hold = 1.seconds, morph = 2500.milliseconds)

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
    summary = "A solid rect and a gradient rect swap shape and fill on one mesh.",
    description = "A rect in a solid fill morphs into a rect in a linear gradient, and back. Both are one morph family, so the nodes stay and only presence and fill " +
        "change. Fills morph too, so the color blends across the overlap.",
    section = SampleSection.Morphs,
    // The capture covers both morphs, there and back.
    recipe = CaptureRecipe(duration = FillMorphTiming.cyclePeriod(legs = 2)),
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
