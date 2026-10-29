@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.ShapePrimitive.Triangle
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

private fun shapeSpec(shape: ShapePrimitive) = MorpheusGraphSpec(seed = 9, layers = listOf(shapeSampleLayer.copy(shapes = listOf(shape))))

private val ribbonSpec = shapeSpec(Ribbon(center = 0.5f, width = 0.35f, curve = 0.15f))
private val bloomSpec = shapeSpec(Bloom(x = 0.5f, y = 0.5f, reach = 0.5f))
private val pillSpec = shapeSpec(Pill(x = 0.5f, y = 0.5f, length = 0.75f, thickness = 0.25f, rotation = 45f))
private val ringSpec = shapeSpec(Ring(x = 0.5f, y = 0.5f, radius = 0.28f, thickness = 0.1f))
private val rectSpec = shapeSpec(Rect(x = 0.5f, y = 0.5f, width = 0.5f, height = 0.32f, rotation = 15f, cornerRadius = 0.08f))
private val triangleSpec = shapeSpec(Triangle(x = 0.5f, y = 0.5f, width = 0.5f, height = 0.43f, cornerRadius = 0.04f))

private const val RIBBON = "Ribbon"
private const val BLOOM = "Bloom"
private const val PILL = "Pill"
private const val RING = "Ring"
private const val RECT = "Rect"
private const val TRIANGLE = "Triangle"

/**
 * The shapes that decide where nodes sit: a [Ribbon] is a meandering band, a [Bloom] thins out from a center, a
 * [Pill] is a capsule, a [Ring] is a circle or an arc, a [Rect] is a rectangle with rounded corners and a [Triangle]
 * is an isosceles triangle. A layer combines up to a few of them by taking the strongest at each point.
 */
@Composable
fun ShapePrimitivesSample(
    modifier: Modifier = Modifier,
    ribbon: MorpheusGraphSpec = ribbonSpec,
    bloom: MorpheusGraphSpec = bloomSpec,
    pill: MorpheusGraphSpec = pillSpec,
    ring: MorpheusGraphSpec = ringSpec,
    rect: MorpheusGraphSpec = rectSpec,
    triangle: MorpheusGraphSpec = triangleSpec,
) {
    SampleGrid(
        modifier = modifier,
        columns = 3,
        tiles = listOf(
            SampleTile(RIBBON) { Box(Modifier.fillMaxSize().morpheusGraphBackground(ribbon)) },
            SampleTile(BLOOM) { Box(Modifier.fillMaxSize().morpheusGraphBackground(bloom)) },
            SampleTile(PILL) { Box(Modifier.fillMaxSize().morpheusGraphBackground(pill)) },
            SampleTile(RING) { Box(Modifier.fillMaxSize().morpheusGraphBackground(ring)) },
            SampleTile(RECT) { Box(Modifier.fillMaxSize().morpheusGraphBackground(rect)) },
            SampleTile(TRIANGLE) { Box(Modifier.fillMaxSize().morpheusGraphBackground(triangle)) },
        ),
    )
}

internal val shapePrimitivesSample = Sample(
    id = "shape-primitives",
    title = "Shape primitives",
    description = "A ribbon, a bloom, a pill, a ring, a rectangle and a triangle: the shapes that decide where a layer's nodes sit.",
    category = SampleCategory.Catalog,
    specs = listOf(
        TunableSpec(RIBBON, ribbonSpec),
        TunableSpec(BLOOM, bloomSpec),
        TunableSpec(PILL, pillSpec),
        TunableSpec(RING, ringSpec),
        TunableSpec(RECT, rectSpec),
        TunableSpec(TRIANGLE, triangleSpec),
    ),
    content = { specs ->
        ShapePrimitivesSample(
            ribbon = specs[RIBBON],
            bloom = specs[BLOOM],
            pill = specs[PILL],
            ring = specs[RING],
            rect = specs[RECT],
            triangle = specs[TRIANGLE],
        )
    },
)
