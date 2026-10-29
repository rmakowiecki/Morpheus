@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

/** A ribbon whose nodes ride a travelling wave and whose edges pulse down to their floor and back. */
private val waveAndPulseSpec = MorpheusGraphSpec(
    seed = 10,
    layers = listOf(
        MorpheusGraphLayerSpec(
            shapes = listOf(Ribbon(center = 0.5f, width = 0.4f, curve = 0.1f)),
            density = 26f,
            jitter = 0.4f,
            driftAmplitude = 0.3f,
            waveAmplitude = 0.1f,
            waveSpeed = 0.5f,
            maxEdgeFactor = 2.4f,
            minAngleDegrees = 14f,
            edgeKeepChance = 0.92f,
            edgeAlpha = 0.6f,
            pulseDepth = 1f,
            nodeSizeDp = 2f,
        ),
    ),
)

/** A wave travelling through the nodes' rest positions, and the pulse that dims and brightens the edges. */
@Composable
fun WaveAndPulseSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = waveAndPulseSpec) {
    SampleGraph(spec, modifier)
}

private const val WAVE_AND_PULSE = "Wave and pulse"

internal val waveAndPulseSample = Sample(
    id = "wave-and-pulse",
    title = "Wave and pulse",
    description = "A ribbon rippling with a travelling wave while its edges pulse.",
    category = SampleCategory.BasicShapes,
    specs = listOf(TunableSpec(WAVE_AND_PULSE, waveAndPulseSpec)),
    content = { specs -> WaveAndPulseSample(spec = specs[WAVE_AND_PULSE]) },
)
