@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.EdgeRule
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

/**
 * One uniform layer, the same in every tile but for its edge rule and its reach; they share a seed, so the nodes start out where they do in the others.
 * [EdgeRule.Proximity] gets a shorter reach (see [edgeRuleSpec]), because at the triangulations' reach it would link nearly every pair into a hairball.
 */
private val edgeRuleLayer = MorpheusGraphLayerSpec(
    density = 28f,
    jitter = 0.5f,
    driftAmplitude = 0.7f,
    driftSpeed = 1.6f,
    edgeKeepChance = 1f,
    lengthFalloff = 0.6f,
    nodeSizeDp = 2.2f,
)

private fun edgeRuleSpec(rule: EdgeRule, maxEdgeFactor: Float) =
    MorpheusGraphSpec(seed = 5, layers = listOf(edgeRuleLayer.copy(edgeRule = rule, maxEdgeFactor = maxEdgeFactor)))

private val proximitySpec = edgeRuleSpec(EdgeRule.Proximity, maxEdgeFactor = 1.45f)
private val fixedTriangulationSpec = edgeRuleSpec(EdgeRule.FixedTriangulation, maxEdgeFactor = 2.2f)
private val livingDelaunaySpec = edgeRuleSpec(EdgeRule.LivingDelaunay, maxEdgeFactor = 2.2f)

private const val PROXIMITY = "Proximity"
private const val FIXED_TRIANGULATION = "Fixed triangulation"
private const val LIVING_DELAUNAY = "Living Delaunay"

/**
 * The same nodes joined three ways: [EdgeRule.Proximity] links every pair within reach, [EdgeRule.FixedTriangulation]
 * triangulates once and keeps those edges, and [EdgeRule.LivingDelaunay] triangulates again every frame, so edges
 * flip, fading out and in, as nodes drift.
 */
@Composable
fun EdgeRulesSample(
    modifier: Modifier = Modifier,
    proximity: MorpheusGraphSpec = proximitySpec,
    fixedTriangulation: MorpheusGraphSpec = fixedTriangulationSpec,
    livingDelaunay: MorpheusGraphSpec = livingDelaunaySpec,
) {
    SampleGrid(
        modifier = modifier,
        columns = 3,
        tiles = listOf(
            SampleTile(PROXIMITY) { SampleGraph(proximity) },
            SampleTile(FIXED_TRIANGULATION) { SampleGraph(fixedTriangulation) },
            SampleTile(LIVING_DELAUNAY) { SampleGraph(livingDelaunay) },
        ),
    )
}

internal val edgeRulesSample = Sample(
    id = "edge-rules",
    title = "Edge rules",
    description = "The same nodes joined by proximity, by a fixed triangulation and by a Delaunay triangulation redone every frame.",
    category = SampleCategory.BasicShapes,
    specs = listOf(
        TunableSpec(PROXIMITY, proximitySpec),
        TunableSpec(FIXED_TRIANGULATION, fixedTriangulationSpec),
        TunableSpec(LIVING_DELAUNAY, livingDelaunaySpec),
    ),
    content = { specs ->
        EdgeRulesSample(
            proximity = specs[PROXIMITY],
            fixedTriangulation = specs[FIXED_TRIANGULATION],
            livingDelaunay = specs[LIVING_DELAUNAY],
        )
    },
)
