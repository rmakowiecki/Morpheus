@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

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
    description = "One graph in a solid, a linear and a radial fill.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(FILLS, fillsSpec)),
    content = { specs -> FillsSample(spec = specs[FILLS]) },
)
