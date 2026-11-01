package com.rossomak.morpheus.samples.catalog

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration.Companion.seconds

private const val RIBBON = "Ribbon"
private const val CORNER_BLOOM = "Corner bloom"
private const val RIBBON_MESH = "Ribbon mesh"
private const val LIVING_DELAUNAY = "Living Delaunay"
private const val RIBBON_TO_CORNER_BLOOM = "$RIBBON → $CORNER_BLOOM"
private const val CORNER_BLOOM_TO_RIBBON = "$CORNER_BLOOM → $RIBBON"
private const val RIBBON_MESH_TO_LIVING_DELAUNAY = "$RIBBON_MESH → $LIVING_DELAUNAY"
private const val LIVING_DELAUNAY_TO_RIBBON_MESH = "$LIVING_DELAUNAY → $RIBBON_MESH"

private val ribbon = MorpheusGraphPresets.MorphFamily.ribbon.forMorphSample().withoutComets()
private val cornerBloom = MorpheusGraphPresets.MorphFamily.cornerBloom.forMorphSample().withoutComets()
private val ribbonMesh = MorpheusGraphPresets.ribbonMesh.forMorphSample().withoutComets()

/** The preset's ribbon sits where the ribbon mesh's does, so move it to the other side to make the dissolve visible. */
private val livingDelaunay = MorpheusGraphPresets.livingDelaunay.forMorphSample().withoutComets().let { spec ->
    spec.copy(layers = spec.layers.map { it.copy(shapes = listOf(Ribbon(center = 0.25f, width = 0.32f, tilt = -10f))) })
}

private val MorphFamiliesTiming = MorphTiming(hold = 1.seconds, morph = 2.seconds)

/**
 * A morph within one family beside a morph between two, on one cycle. The family's specs share a mesh, so its
 * nodes stay in place while the shape changes; the others don't, so one mesh dissolves into the other.
 */
@Composable
fun MorphFamiliesSample(
    modifier: Modifier = Modifier,
    family: List<MorpheusGraphSpec> = listOf(ribbon, cornerBloom),
    familyEasings: List<Easing> = family.map { FastOutSlowInEasing },
    dissolve: List<MorpheusGraphSpec> = listOf(ribbonMesh, livingDelaunay),
    dissolveEasings: List<Easing> = dissolve.map { FastOutSlowInEasing },
    timing: MorphTiming = MorphFamiliesTiming,
) {
    require(dissolve.size == family.size) { "Both tiles share one cycle, so they need the same number of specs: ${family.size} and ${dissolve.size}" }
    val cycleTime = rememberCycleTime(period = timing.cyclePeriod(legs = family.size))
    SampleGrid(
        modifier = modifier,
        columns = 2,
        tiles = listOf(
            SampleTile("Same family") { SampleMorph(family, familyEasings, timing, cycleTime) },
            SampleTile("Cross-family dissolve") { SampleMorph(dissolve, dissolveEasings, timing, cycleTime) },
        ),
    )
}

internal val morphFamiliesSample = Sample(
    id = "morph-families",
    title = "Morph families",
    description = "A ribbon morphing in place into a corner bloom, beside one mesh dissolving into another.",
    category = SampleCategory.Morphs,
    specs = listOf(
        TunableSpec(RIBBON, ribbon),
        TunableSpec(CORNER_BLOOM, cornerBloom),
        TunableSpec(RIBBON_MESH, ribbonMesh),
        TunableSpec(LIVING_DELAUNAY, livingDelaunay),
    ),
    morphs = listOf(
        TunableMorph(RIBBON_TO_CORNER_BLOOM),
        TunableMorph(CORNER_BLOOM_TO_RIBBON),
        TunableMorph(RIBBON_MESH_TO_LIVING_DELAUNAY),
        TunableMorph(LIVING_DELAUNAY_TO_RIBBON_MESH),
    ),
    timing = MorphFamiliesTiming,
    content = { specs ->
        MorphFamiliesSample(
            family = listOf(specs[RIBBON], specs[CORNER_BLOOM]),
            familyEasings = specs.easings(RIBBON_TO_CORNER_BLOOM, CORNER_BLOOM_TO_RIBBON),
            dissolve = listOf(specs[RIBBON_MESH], specs[LIVING_DELAUNAY]),
            dissolveEasings = specs.easings(RIBBON_MESH_TO_LIVING_DELAUNAY, LIVING_DELAUNAY_TO_RIBBON_MESH),
            timing = specs.timing,
        )
    },
)
