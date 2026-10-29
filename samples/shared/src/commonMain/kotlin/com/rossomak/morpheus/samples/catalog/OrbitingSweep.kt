@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

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
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

private const val ORBITING_SWEEP = "Orbiting sweep"
private const val SWEEP_STOPS = 16

/** A thick ring over outliers: enough of them to cover the surface, few enough to leave the ring plain. */
private val orbitingSweepSpec = MorpheusGraphSpec(
    seed = 14,
    layers = listOf(
        fillSampleLayer.copy(
            shapes = listOf(Ring(x = 0.5f, y = 0.5f, radius = 0.3f, thickness = 0.14f)),
            envelopeFloor = 0.12f,
            driftAmplitude = 0.55f,
            driftSpeed = 1.5f,
        ),
    ),
)

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
private val orbitingSweepFill = MorpheusGraphFill.Custom { context ->
    val lap = context.timeSeconds / SampleStyle.ROTATION_SECONDS % 1f
    val stops = Array(SWEEP_STOPS + 1) { index ->
        val position = index / SWEEP_STOPS.toFloat()
        position to loopColor((position - lap + 1f) % 1f)
    }
    Brush.sweepGradient(colorStops = stops, center = Offset(context.size.width / 2f, context.size.height / 2f))
}

@Composable
fun OrbitingSweepSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = orbitingSweepSpec) {
    SampleGraph(spec, modifier, fill = orbitingSweepFill)
}

internal val orbitingSweepSample = Sample(
    id = "orbiting-sweep",
    title = "Orbiting sweep",
    description = "A custom fill: a sweep gradient whose colors travel around a ring.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(ORBITING_SWEEP, orbitingSweepSpec)),
    content = { specs -> OrbitingSweepSample(spec = specs[ORBITING_SWEEP]) },
)
