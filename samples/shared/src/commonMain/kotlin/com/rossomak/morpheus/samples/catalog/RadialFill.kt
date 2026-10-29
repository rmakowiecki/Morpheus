@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.rossomak.morpheus.GradientStop
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
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
    description = "A radial gradient centered off the middle, on the corner bloom.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(CORNER_BLOOM, radialFillSpec)),
    content = { specs -> RadialFillSample(spec = specs[CORNER_BLOOM]) },
)
