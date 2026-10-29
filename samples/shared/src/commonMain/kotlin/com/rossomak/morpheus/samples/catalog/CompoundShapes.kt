@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.ShapePrimitive.Triangle
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

/** One layer with a ring between a triangle and a rectangle, each crossing the ring's rim. */
private val compoundShapesSpec = MorpheusGraphSpec(
    seed = 33,
    layers = listOf(
        shapeSampleLayer.copy(
            shapes = listOf(
                Ring(x = 0.5f, y = 0.5f, radius = 0.2f, thickness = 0.06f),
                Rect(x = 0.7f, y = 0.5f, width = 0.28f, height = 0.2f, rotation = -15f, cornerRadius = 0f),
                Triangle(x = 0.3f, y = 0.5f, width = 0.3f, height = 0.26f, cornerRadius = 0.02f),
            ),
        ),
    ),
)

/** A layer takes the strongest of its shapes at each point, so a [Ring], a [Rect] and a [Triangle] overlap into one union. */
@Composable
fun CompoundShapesSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = compoundShapesSpec) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec))
}

private const val COMPOUND_SHAPES = "Compound shapes"

internal val compoundShapesSample = Sample(
    id = "compound-shapes",
    title = "Compound shapes",
    description = "A triangle and a rectangle each crossing a ring, merged in one layer.",
    category = SampleCategory.Catalog,
    specs = listOf(TunableSpec(COMPOUND_SHAPES, compoundShapesSpec)),
    content = { specs -> CompoundShapesSample(spec = specs[COMPOUND_SHAPES]) },
)
