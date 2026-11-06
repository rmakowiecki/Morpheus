@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.fills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleStyle
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.fillSampleLayer

private const val FILLS = "Fills"

private val fillsSpec = MorpheusGraphSpec(seed = 12, layers = listOf(fillSampleLayer))

private val solidFill = MorpheusGraphFill.Solid(SampleStyle.blue)
private val linearFill = MorpheusGraphFill.linear(listOf(SampleStyle.violet, SampleStyle.magenta))
private val radialFill = MorpheusGraphFill.radial(listOf(SampleStyle.amber, SampleStyle.teal), radius = 0.6f)

/** The same graph in a solid, a linear and an inherited radial fill. */
@Composable
fun FillsSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = fillsSpec) {
    SampleGrid(
        modifier = modifier,
        columns = 3,
        tiles = listOf(
            SampleTile("Solid") { SampleGraph(spec.copy(fill = solidFill)) },
            SampleTile("Linear") { SampleGraph(spec.copy(fill = linearFill)) },
            SampleTile("Radial") {
                CompositionLocalProvider(LocalMorpheusGraphFill provides radialFill) { SampleGraph(spec) }
            },
        ),
    )
}

internal val fillsSample = Sample(
    id = "fills",
    title = "Fills",
    summary = "One graph in a solid, a linear and a radial fill.",
    description = "The same graph three times: in a solid fill, a linear gradient and a radial gradient. The solid and linear fills are set on the spec. The radial " +
        "fill comes from the composition, and a fill set on the spec would win over it. A layer's own fill wins over both.",
    section = SampleSection.Fills,
    specs = listOf(TunableSpec(FILLS, fillsSpec)),
    content = { specs -> FillsSample(spec = specs[FILLS]) },
)
