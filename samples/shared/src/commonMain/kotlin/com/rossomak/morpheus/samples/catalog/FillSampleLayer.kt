@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import com.rossomak.morpheus.MorpheusGraphLayerSpec

/** A dense, even field with nothing but its fill to look at. */
internal val fillSampleLayer: MorpheusGraphLayerSpec = shapeSampleLayer.copy(
    density = 90f,
    strokeWidthDp = 1f,
    edgeAlpha = 0.5f,
    nodeSizeDp = 1.8f,
)
