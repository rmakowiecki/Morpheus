@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.fills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleGraph
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.SampleStyle
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.fillSampleLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val LINEAR_GRADIENT = "Linear gradient"
private const val SWEEP_GRADIENT = "Sweep gradient"
private const val SWEEP_STOPS = 16

private val linearGradientSpec = MorpheusGraphSpec(
    seed = 13,
    layers = listOf(fillSampleLayer.copy(driftAmplitude = 0.6f, driftSpeed = 1.5f)),
)

/** A thick ring with every cell inside it active and none outside. */
private val sweepGradientSpec = MorpheusGraphSpec(
    seed = 14,
    layers = listOf(
        fillSampleLayer.copy(
            shapes = listOf(Ring(x = 0.5f, y = 0.5f, radius = 0.3f, thickness = 0.14f)),
            fillChance = 1f,
            envelopeFloor = 0f,
            driftAmplitude = 0.55f,
            driftSpeed = 1.5f,
        ),
    ),
)

/**
 * A linear gradient whose ends circle the surface's center, one turn per [SampleStyle.ROTATION_SECONDS]. It is a
 * top-level value because fills are compared with `equals`.
 */
private val rotatingLinearFill = MorpheusGraphFill.Custom { context ->
    val center = Offset(context.size.width / 2f, context.size.height / 2f)
    val angle = context.timeSeconds / SampleStyle.ROTATION_SECONDS * 2f * PI.toFloat()
    val reach = hypot(context.size.width, context.size.height) / 2f
    val arm = Offset(cos(angle), sin(angle)) * reach
    Brush.linearGradient(colors = SampleStyle.spectrum, start = center - arm, end = center + arm)
}

/** The color at [position], from 0 to 1, of a loop through the spectrum whose last color leads back into the first. */
private fun loopColor(position: Float): Color {
    val colors = SampleStyle.spectrum
    val scaled = position * colors.size
    val index = scaled.toInt()
    return lerp(colors[index % colors.size], colors[(index + 1) % colors.size], scaled - index)
}

/**
 * A sweep gradient around the surface's center. Compose can't start a sweep at an angle, so each frame shifts
 * which color sits at which stop instead; the loop closes on itself, so there is no seam.
 */
private val rotatingSweepFill = MorpheusGraphFill.Custom { context ->
    val lap = context.timeSeconds / SampleStyle.ROTATION_SECONDS % 1f
    val stops = Array(SWEEP_STOPS + 1) { index ->
        val position = index / SWEEP_STOPS.toFloat()
        position to loopColor((position - lap + 1f) % 1f)
    }
    Brush.sweepGradient(colorStops = stops, center = Offset(context.size.width / 2f, context.size.height / 2f))
}

/** Two custom fills that read the renderer's clock, so their brushes turn without anything recomposing. */
@Composable
fun RotatingGradientBrushSample(
    modifier: Modifier = Modifier,
    linearGradient: MorpheusGraphSpec = linearGradientSpec,
    sweepGradient: MorpheusGraphSpec = sweepGradientSpec,
) {
    SampleGrid(
        modifier = modifier,
        columns = 2,
        tiles = listOf(
            SampleTile(LINEAR_GRADIENT) { SampleGraph(linearGradient, fill = rotatingLinearFill) },
            SampleTile(SWEEP_GRADIENT) { SampleGraph(sweepGradient, fill = rotatingSweepFill) },
        ),
    )
}

internal val rotatingGradientBrushSample = Sample(
    id = "rotating-gradient-brush",
    title = "Rotating gradient brush",
    summary = "A linear and a sweep gradient brush, both turning with the clock.",
    description = "Two custom fills whose brush changes every frame: a linear gradient and a sweep gradient, both turning. " +
        "The renderer asks a custom fill for its brush every frame, with the time in seconds. The brush reads the clock itself, so nothing recomposes.",
    section = SampleSection.Fills,
    specs = listOf(TunableSpec(LINEAR_GRADIENT, linearGradientSpec), TunableSpec(SWEEP_GRADIENT, sweepGradientSpec)),
    content = { specs -> RotatingGradientBrushSample(linearGradient = specs[LINEAR_GRADIENT], sweepGradient = specs[SWEEP_GRADIENT]) },
)
