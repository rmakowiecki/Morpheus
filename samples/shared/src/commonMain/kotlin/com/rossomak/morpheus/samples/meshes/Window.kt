@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.meshes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.PortraitCanvas
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TileSize
import com.rossomak.morpheus.samples.TunableSpec

private const val BASELINE = "Baseline"

/** One spec on an app bar, a card and a hero area: the mesh is as fine on each. */
@Composable
fun WindowSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.baseline) {
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

internal val windowSample = Sample(
    id = "window",
    title = "Window",
    summary = "One spec on an app bar, a card and a hero area, with one cell size.",
    description = "One spec on a 64 dp app bar, a 120 dp card and a hero area. Each layer's grid is built from the window, not from the surface. The cells keep one " +
        "size, so a bigger surface holds more nodes, not bigger ones.",
    section = SampleSection.Meshes,
    recipe = CaptureRecipe(canvasSize = PortraitCanvas),
    specs = listOf(TunableSpec(BASELINE, MorpheusGraphPresets.baseline)),
    content = { specs -> WindowSample(spec = specs[BASELINE]) },
)
