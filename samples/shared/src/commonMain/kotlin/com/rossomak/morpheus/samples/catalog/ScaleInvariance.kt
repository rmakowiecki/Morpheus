@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.PortraitCanvas
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TileSize
import com.rossomak.morpheus.samples.TunableSpec

private const val BASELINE = "Baseline"

/** One spec on an app bar, a card and a hero area: the mesh is as fine on each. */
@Composable
fun ScaleInvarianceSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.baseline) {
    SampleGrid(
        modifier = modifier,
        columns = 1,
        tiles = listOf(
            SampleTile("App bar · 64 dp", TileSize.FixedHeight(64.dp)) { SampleGraph(spec) },
            SampleTile("Card · 120 dp", TileSize.FixedHeight(120.dp)) { SampleGraph(spec) },
            SampleTile("Hero") { SampleGraph(spec) },
        ),
    )
}

internal val scaleInvarianceSample = Sample(
    id = "scale-invariance",
    title = "Scale invariance",
    description = "One spec on a 64 dp app bar, a 120 dp card and a hero area: the mesh is equally fine on every surface.",
    category = SampleCategory.BasicShapes,
    recipe = CaptureRecipe(canvasSize = PortraitCanvas),
    specs = listOf(TunableSpec(BASELINE, MorpheusGraphPresets.baseline)),
    content = { specs -> ScaleInvarianceSample(spec = specs[BASELINE]) },
)
