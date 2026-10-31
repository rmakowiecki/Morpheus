package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

private val seeds = 1..4

/** The same ribbon under four seeds: the seed changes where the nodes fall and nothing else. */
@Composable
fun SeedGridSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.ribbonMesh) {
    SampleGrid(
        modifier = modifier,
        columns = 2,
        tiles = seeds.map { seed -> SampleTile("Seed $seed") { SampleGraph(spec.copy(seed = seed)) } },
    )
}

internal val seedGridSample = Sample(
    id = "seed-grid",
    title = "Seed grid",
    description = "The ribbon mesh preset under seeds 1 to 4: the seed alone decides where the nodes fall.",
    category = SampleCategory.BasicShapes,
    specs = listOf(TunableSpec(RIBBON_MESH, MorpheusGraphPresets.ribbonMesh)),
    content = { specs -> SeedGridSample(spec = specs[RIBBON_MESH]) },
)
