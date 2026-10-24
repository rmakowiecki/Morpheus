package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Uniform

// The "Pills to ribbons" showcase's two ends, copied from samples/shared's showcase/Hero.kt (the library
// can't depend on the samples). Fills are left out: they never touch the mesh.

internal val heroFront = MorpheusGraphLayerSpec(
    density = 95f,
    jitter = 0.4f,
    driftAmplitude = 0.3f,
    envelopeFloor = 0.01f,
    maxEdgeFactor = 2.4f,
    minAngleDegrees = 14f,
    edgeKeepChance = 0.92f,
    strokeWidthDp = 0.6f,
    edgeAlpha = 0.6f,
    nodeSizeDp = 1.6f,
    nodeSizeVariance = 0.4f,
    hotNodeShare = 0.12f,
    glowRadiusDp = 10f,
    glowStrength = 0.4f,
    cometStrength = 0.5f,
    cometSpark = 0.6f,
)

/** [MorpheusGraphPresets.defaultBackLayer] at density 16 and alpha 0.3, spelled out. */
internal val heroGhost = MorpheusGraphLayerSpec(
    shapes = listOf(Uniform()),
    density = 16f,
    jitter = 0.45f,
    driftAmplitude = 0.35f,
    driftSpeed = 0.5f,
    envelopeFloor = 0.05f,
    waveAmplitude = 0.02f,
    waveSpeed = 0.15f,
    maxEdgeFactor = 2.6f,
    edgeKeepChance = 0.8f,
    strokeWidthDp = 1f,
    edgeAlpha = 0.5f,
    lengthFalloff = 0.3f,
    nodeSizeDp = 4f,
    nodeSizeVariance = 0.2f,
    alpha = 0.3f,
)

internal val heroPills = MorpheusGraphSpec(
    seed = 23,
    layers = listOf(
        heroGhost.copy(waveAmplitude = 0.06f, waveSpeed = 0.4f),
        heroFront.copy(
            shapes = listOf(
                Pill(x = 0.25f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = 55f),
                Pill(x = 0.75f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = -55f),
            ),
        ),
    ),
)

internal val heroRibbons = heroPills.copy(
    layers = listOf(
        heroGhost,
        heroFront.copy(
            shapes = listOf(
                Ribbon(center = 0.5f, width = 0.15f, curve = 0.18f, tilt = 25f),
                Ribbon(center = 0.5f, width = 0.15f, curve = 0.18f, tilt = -25f),
            ),
            waveAmplitude = 0.06f,
            waveSpeed = 0.45f,
        ),
    ),
)
