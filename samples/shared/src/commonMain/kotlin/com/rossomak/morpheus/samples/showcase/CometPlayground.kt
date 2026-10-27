@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.showcase

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphComets
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import kotlin.time.Duration.Companion.milliseconds

private val Teal = Color(0xFF1FD1B2)
private val Blue = Color(0xFF2F6BFF)
private val Purple = Color(0xFF9B3DFF)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF2A3D)
private val NearBlack = Color(0xFF050807)

/** The very dense front mesh all three specs share, so they stay one morph family and the nodes stay put. */
private val playgroundFront = MorpheusGraphLayerSpec(
    density = 100f,
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
    comets = MorpheusGraphComets(size = 1.25f, spark = 0.6f),
)

private val playgroundGhost = MorpheusGraphPresets.defaultBackLayer.copy(density = 16f, alpha = 0.3f)

private const val PLAYGROUND_SEED = 41

/** Four pills framing the square, one along each side, the middle left empty. */
val playgroundFrame = MorpheusGraphSpec(
    seed = PLAYGROUND_SEED,
    layers = listOf(
        playgroundGhost,
        playgroundFront.copy(
            shapes = listOf(
                Pill(x = 0.5f, y = 0.09f, length = 0.66f, thickness = 0.1f, rotation = 0f),
                Pill(x = 0.91f, y = 0.5f, length = 0.66f, thickness = 0.1f, rotation = 90f),
                Pill(x = 0.5f, y = 0.91f, length = 0.66f, thickness = 0.1f, rotation = 0f),
                Pill(x = 0.09f, y = 0.5f, length = 0.66f, thickness = 0.1f, rotation = 90f),
            ),
        ),
    ),
    fill = MorpheusGraphFill.linear(listOf(Teal, Blue)),
)

/** Five round blooms, one near each corner and one in the middle, on a strongly waving, drifting mesh. */
val playgroundBlooms = playgroundFrame.copy(
    layers = listOf(
        playgroundGhost.copy(waveAmplitude = 0.06f, waveSpeed = 0.55f),
        playgroundFront.copy(
            shapes = listOf(
                Bloom(x = 0.2f, y = 0.2f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.8f, y = 0.2f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.2f, y = 0.8f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.8f, y = 0.8f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.5f, y = 0.5f, reach = 0.17f, plateau = 0.6f),
            ),
            driftAmplitude = 0.8f,
            driftSpeed = 2.2f,
            waveAmplitude = 0.05f,
            waveSpeed = 0.7f,
        ),
    ),
    fill = MorpheusGraphFill.linear(listOf(Purple, Amber)),
)

/** Two long pills crossing at the center in a "+", each almost touching two opposite sides. */
val playgroundCross = playgroundFrame.copy(
    layers = listOf(
        playgroundGhost,
        playgroundFront.copy(
            shapes = listOf(
                Pill(x = 0.5f, y = 0.5f, length = 0.9f, thickness = 0.13f, rotation = 0f),
                Pill(x = 0.5f, y = 0.5f, length = 0.9f, thickness = 0.13f, rotation = 90f),
            ),
        ),
    ),
    fill = MorpheusGraphFill.linear(listOf(Red, Purple)),
)

private val PlaygroundTiming = MorphTiming(hold = 750.milliseconds, morph = 3500.milliseconds)

/**
 * Frame, blooms and cross morph into each other in turn, on a loop: each leg holds a spec, then morphs to the
 * next through its own easing, on the [timing]. All three are one morph family, so the nodes stay put and the comets carry what
 * each shape leaves.
 */
@Composable
fun CometPlaygroundSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = listOf(playgroundFrame, playgroundBlooms, playgroundCross),
    easings: List<Easing> = specs.map { FastOutSlowInEasing },
    timing: MorphTiming = PlaygroundTiming,
) {
    val state = rememberMorpheusGraphMorphState(specs.first())
    MorphCycle(state, specs, easings, timing)
    Box(modifier.fillMaxSize().background(NearBlack).morpheusGraphBackground(state))
}

private const val FRAME = "Frame"
private const val BLOOMS = "Blooms"
private const val CROSS = "Cross"
private const val FRAME_TO_BLOOMS = "Frame → Blooms"
private const val BLOOMS_TO_CROSS = "Blooms → Cross"
private const val CROSS_TO_FRAME = "Cross → Frame"

internal val cometPlaygroundSample = Sample(
    id = "comet-playground",
    title = "Comet playground",
    description = "Four framing pills, five blooms and a cross morph into each other in turn, to watch the comets.",
    category = SampleCategory.Showcase,
    // The whole three-leg loop, crossfaded over the half of the frame's hold it starts with; 24 fps, as
    // 12.75 s isn't whole frames at 30.
    recipe = CaptureRecipe(
        canvasSize = DpSize(480.dp, 480.dp),
        duration = PlaygroundTiming.leg * 3,
        fps = 24,
        crossfade = PlaygroundTiming.hold / 2,
        themes = CaptureThemes.DarkOnly,
    ),
    specs = listOf(TunableSpec(FRAME, playgroundFrame), TunableSpec(BLOOMS, playgroundBlooms), TunableSpec(CROSS, playgroundCross)),
    morphs = listOf(TunableMorph(FRAME_TO_BLOOMS), TunableMorph(BLOOMS_TO_CROSS), TunableMorph(CROSS_TO_FRAME)),
    timing = PlaygroundTiming,
    galleryOnly = true,
    content = { specs ->
        CometPlaygroundSample(
            specs = listOf(specs[FRAME], specs[BLOOMS], specs[CROSS]),
            easings = listOf(specs.easing(FRAME_TO_BLOOMS), specs.easing(BLOOMS_TO_CROSS), specs.easing(CROSS_TO_FRAME)),
            timing = specs.timing,
        )
    },
)
