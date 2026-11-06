@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.parameters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec

/** The drift row turns its amplitude and speed up together, and the wave row likewise; the wave row holds the nodes still. */
private val motionRows = listOf(
    ParameterRow(listOf("driftAmplitude", "driftSpeed"), listOf(listOf(0f, 0f), listOf(0.6f, 1.8f), listOf(0.95f, 4f))) { (amplitude, speed) ->
        copy(driftAmplitude = amplitude, driftSpeed = speed)
    },
    ParameterRow(listOf("waveAmplitude", "waveSpeed"), listOf(listOf(0f, 0f), listOf(0.07f, 0.6f), listOf(0.1f, 1.3f))) { (amplitude, speed) ->
        copy(waveAmplitude = amplitude, waveSpeed = speed, driftAmplitude = 0f)
    },
)

/** The disc at three drifts and three waves. */
@Composable
fun MotionSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = parameterDiscSpec) {
    ParameterGrid(spec, motionRows, modifier)
}

internal val motionSample = parameterSample(
    id = "motion",
    title = "Motion",
    summary = "One disc at three drifts and three waves.",
    description = "The rows turn up the same disc's drift and its wave, left to right, amplitude and speed together. " +
        "Drift wanders each node around its home, in cells. " +
        "The wave shifts every home up and down, as a share of the surface's height. " +
        "Its row holds the drift at 0.",
    rows = motionRows,
    spec = parameterDiscSpec,
) { MotionSample(spec = it) }
