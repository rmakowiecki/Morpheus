@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

/** Three layers, back to front: a faint fog of big dim nodes, a ghost ribbon, and a bloom in the top-end corner in front. */
private val layersSpec = MorpheusGraphSpec(
    seed = 8,
    layers = listOf(
        MorpheusGraphLayerSpec(
            density = 14f,
            driftAmplitude = 0.5f,
            driftSpeed = 0.5f,
            maxEdgeFactor = 2.6f,
            edgeKeepChance = 0.8f,
            strokeWidthDp = 1f,
            edgeAlpha = 0.5f,
            nodeSizeDp = 5f,
            nodeSizeVariance = 0.5f,
            depthDimming = 0.9f,
            alpha = 0.25f,
        ),
        MorpheusGraphLayerSpec(
            shapes = listOf(Ribbon(center = 0.6f, width = 0.5f, curve = 0.15f)),
            density = 12f,
            driftAmplitude = 0.35f,
            driftSpeed = 0.5f,
            waveAmplitude = 0.02f,
            waveSpeed = 0.15f,
            maxEdgeFactor = 2.6f,
            edgeKeepChance = 0.8f,
            strokeWidthDp = 1f,
            nodeSizeDp = 4f,
            alpha = 0.4f,
        ),
        MorpheusGraphLayerSpec(
            shapes = listOf(Bloom(reach = 0.7f)),
            density = 24f,
            envelopeFloor = 0.02f,
            maxEdgeFactor = 2.4f,
            minAngleDegrees = 14f,
            lengthFalloff = 0.6f,
            nodeSizeVariance = 0.4f,
            hotNodeShare = 0.12f,
            glowStrength = 0.3f,
        ),
    ),
)

/** Layers stack back to front, each with its own shape, density, speed and alpha, which is what gives a graph its depth. */
@Composable
fun LayersSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = layersSpec) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec))
}

private const val LAYERS = "Layers"

internal val layersSample = Sample(
    id = "layers",
    title = "Layers",
    description = "A fog of dim nodes, a ghost ribbon and a bloom, stacked back to front.",
    category = SampleCategory.Catalog,
    specs = listOf(TunableSpec(LAYERS, layersSpec)),
    content = { specs -> LayersSample(spec = specs[LAYERS]) },
)
