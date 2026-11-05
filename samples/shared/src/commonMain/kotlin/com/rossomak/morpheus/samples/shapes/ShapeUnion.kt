@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.shapes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.ShapePrimitive.Triangle
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.shapeSampleLayer

/**
 * One layer with a big ring between a triangle and a rectangle, each crossing the ring's rim, a small ring on top
 * of it and an arc along its bottom. Every shape has a hard edge.
 */
private val shapeUnionSpec = MorpheusGraphSpec(
    seed = 33,
    layers = listOf(
        shapeSampleLayer.copy(
            shapes = listOf(
                Ring(x = 0.5f, y = 0.56f, radius = 0.2f, thickness = 0.06f, plateau = 0.95f),
                Rect(x = 0.68f, y = 0.59f, width = 0.25f, height = 0.2f, rotation = -1f, cornerRadius = 0f, plateau = 0.95f),
                Triangle(x = 0.28f, y = 0.5f, width = 0.3f, height = 0.26f, rotation = -32f, cornerRadius = 0f, plateau = 0.95f),
                Ring(x = 0.49f, y = 0.22f, radius = 0.1f, thickness = 0.03f, plateau = 0.95f),
                Ring(x = 0.43f, y = 0.86f, radius = 0.11f, thickness = 0.06f, startAngle = 145f, sweep = 181f, plateau = 0.95f),
            ),
        ),
    ),
)

/**
 * A layer takes the strongest of its shapes at each point, so two [Ring]s, an arc (a [Ring] whose sweep is under a
 * full turn), a [Rect] and a [Triangle] overlap into one union, drawn as one mesh over the layer's cell grid.
 */
@Composable
fun ShapeUnionSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = shapeUnionSpec) {
    SampleGraph(spec, modifier)
}

private const val SHAPE_UNION = "Shape union"

internal val shapeUnionSample = Sample(
    id = "shape-union",
    title = "Shape union",
    summary = "Two rings, an arc, a rect and a triangle merged into one mesh.",
    description = "One layer holds two rings, an arc, a rect and a triangle, all with hard edges. " +
        "An arc is a partial ring: a ring whose sweep stops short of a full turn. " +
        "At each point the layer takes the strongest of its shapes, so overlaps merge into one union. " +
        "All five share the layer's cell grid, so they draw as one mesh.",
    section = SampleSection.Shapes,
    specs = listOf(TunableSpec(SHAPE_UNION, shapeUnionSpec)),
    content = { specs -> ShapeUnionSample(spec = specs[SHAPE_UNION]) },
)
