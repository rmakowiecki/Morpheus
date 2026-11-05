@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.fills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.rossomak.morpheus.GradientStop
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleStyle
import com.rossomak.morpheus.samples.TunableSpec

private const val CORNER_BLOOM = "Corner bloom"

/** The corner bloom, painted with a radial gradient centered a little inside the bloom's corner. */
private val radialFillSpec = MorpheusGraphPresets.cornerBloom.copy(
    fill = MorpheusGraphFill.Radial(
        stops = listOf(
            GradientStop(0f, SampleStyle.amber),
            GradientStop(0.45f, SampleStyle.magenta),
            GradientStop(1f, SampleStyle.violet),
        ),
        center = Offset(0.85f, 0.2f),
        radius = 0.9f,
    ),
)

@Composable
fun RadialFillSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = radialFillSpec) {
    SampleGraph(spec, modifier)
}

internal val radialFillSample = Sample(
    id = "radial-fill",
    title = "Radial fill",
    summary = "A radial gradient centered off the middle, on the corner bloom.",
    description = "The corner bloom preset in a radial gradient. The gradient's center sits near the bloom's corner, so the color follows the shape, not the surface. " +
        "The radius is a fraction of the surface's longer side.",
    section = SampleSection.Fills,
    specs = listOf(TunableSpec(CORNER_BLOOM, radialFillSpec)),
    content = { specs -> RadialFillSample(spec = specs[CORNER_BLOOM]) },
)
