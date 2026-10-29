@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val ANIMATED_BRUSH = "Animated brush"

private val animatedBrushSpec = MorpheusGraphSpec(
    seed = 13,
    layers = listOf(fillSampleLayer.copy(driftAmplitude = 0.6f, driftSpeed = 1.5f)),
)

/**
 * A linear gradient whose ends circle the surface's center, one turn per [SampleStyle.ROTATION_SECONDS]. It is a
 * top-level value because fills are compared with `equals`.
 */
private val rotatingGradientFill = MorpheusGraphFill.Custom { context ->
    val center = Offset(context.size.width / 2f, context.size.height / 2f)
    val angle = context.timeSeconds / SampleStyle.ROTATION_SECONDS * 2f * PI.toFloat()
    val reach = hypot(context.size.width, context.size.height) / 2f
    val arm = Offset(cos(angle), sin(angle)) * reach
    Brush.linearGradient(colors = SampleStyle.spectrum, start = center - arm, end = center + arm)
}

/** A custom fill that reads the renderer's clock, so the brush moves without anything recomposing. */
@Composable
fun AnimatedBrushSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = animatedBrushSpec) {
    SampleGraph(spec, modifier, fill = rotatingGradientFill)
}

internal val animatedBrushSample = Sample(
    id = "animated-brush",
    title = "Animated brush",
    description = "A custom fill: a linear gradient turning around the middle of the surface.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(ANIMATED_BRUSH, animatedBrushSpec)),
    content = { specs -> AnimatedBrushSample(spec = specs[ANIMATED_BRUSH]) },
)
