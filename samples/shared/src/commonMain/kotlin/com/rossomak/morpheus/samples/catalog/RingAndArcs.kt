@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

/** One layer with a thin ring inside a thick one, and a half and a quarter arc beside them. */
private val ringAndArcsSpec = MorpheusGraphSpec(
    seed = 21,
    layers = listOf(
        shapeSampleLayer.copy(
            shapes = listOf(
                Ring(x = 0.28f, y = 0.5f, radius = 0.3f, thickness = 0.12f),
                Ring(x = 0.28f, y = 0.5f, radius = 0.15f, thickness = 0.04f),
                Ring(x = 0.7f, y = 0.3f, radius = 0.17f, thickness = 0.07f, startAngle = 180f, sweep = 180f),
                Ring(x = 0.72f, y = 0.7f, radius = 0.17f, thickness = 0.04f, startAngle = 0f, sweep = 90f),
            ),
        ),
    ),
)

/** A [Ring] is a circle, or an arc when its `sweep` is under a full turn: dense along its curve, with a round cap at each end of an arc. */
@Composable
fun RingAndArcsSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = ringAndArcsSpec) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec))
}

private const val RINGS_AND_ARCS = "Rings and arcs"

internal val ringAndArcsSample = Sample(
    id = "ring-and-arcs",
    title = "Ring and arcs",
    description = "A thin ring inside a thick one, beside a half and a quarter arc with round caps.",
    category = SampleCategory.Catalog,
    specs = listOf(TunableSpec(RINGS_AND_ARCS, ringAndArcsSpec)),
    content = { specs -> RingAndArcsSample(spec = specs[RINGS_AND_ARCS]) },
)
