@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import com.rossomak.morpheus.MorpheusGraphLayerSpec

/**
 * The layer every shape sample builds on: dense, flat and with nothing outside its shapes, so the rim of a shape
 * is what you see. Samples set `shapes` and leave the rest.
 */
internal val shapeSampleLayer = MorpheusGraphLayerSpec(
    density = 120f,
    jitter = 0.4f,
    driftAmplitude = 0.3f,
    envelopeFloor = 0f,
    maxEdgeFactor = 1.9f,
    minAngleDegrees = 18f,
    edgeKeepChance = 0.95f,
    edgeAlpha = 0.35f,
    nodeSizeDp = 1.4f,
    depthDimming = 0f,
    hotNodeShare = 0f,
)
