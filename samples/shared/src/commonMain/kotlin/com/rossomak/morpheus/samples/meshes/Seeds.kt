package com.rossomak.morpheus.samples.meshes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

private const val RIBBON_MESH = "Ribbon mesh"

private val seeds = 1..4

/** The same ribbon under four seeds, identical but for the seed: the seed alone changes where the nodes fall. */
@Composable
fun SeedsSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.ribbonMesh) {
    SampleGrid(
        modifier = modifier,
        columns = 2,
        tiles = seeds.map { seed -> SampleTile("Seed $seed") { SampleGraph(spec.copy(seed = seed)) } },
    )
}

internal val seedsSample = Sample(
    id = "seeds",
    title = "Seeds",
    summary = "One ribbon spec under seeds 1 to 4, identical but for the seed.",
    description = "The same ribbon spec under seeds 1 to 4; the four setups differ in nothing else. The library is pseudorandom and deterministic: every random choice " +
        "derives from the seed. A seed always draws the same graph. It places every cell's home and sets its drift and roll.",
    section = SampleSection.Meshes,
    specs = listOf(TunableSpec(RIBBON_MESH, MorpheusGraphPresets.ribbonMesh)),
    content = { specs -> SeedsSample(spec = specs[RIBBON_MESH]) },
)
