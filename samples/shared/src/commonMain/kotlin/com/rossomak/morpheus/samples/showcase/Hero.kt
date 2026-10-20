@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.showcase

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val Blue = Color(0xFF2F6BFF)
private val Purple = Color(0xFF9B3DFF)
private val Red = Color(0xFFFF2A3D)
private val NearBlack = Color(0xFF050807)

/** The very dense front mesh both ends share, so its family fields can't diverge and nodes stay put. */
private val heroFront = MorpheusGraphLayerSpec(
    density = 70f,
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
    cometStrength = 0.25f,
    cometSpark = 0.6f,
)

private val heroGhost = MorpheusGraphPresets.defaultBackLayer.copy(density = 16f, alpha = 0.3f)

private const val HERO_SEED = 23

/** Two diagonal pills leaning away from each other at the top, like a V: blue on the left, red on the right. */
val heroPills = MorpheusGraphSpec(
    seed = HERO_SEED,
    layers = listOf(
        // Waves harder behind the pills; wave isn't a morph-family field, so it eases into the ribbons' calmer one.
        heroGhost.copy(waveAmplitude = 0.06f, waveSpeed = 0.4f),
        heroFront.copy(
            shapes = listOf(
                Pill(x = 0.25f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = 55f),
                Pill(x = 0.75f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = -55f),
            ),
        ),
    ),
    fill = MorpheusGraphFill.linear(listOf(Blue, Red)),
)

/**
 * Two meandering ribbons crossing at the center in a loose X, red through purple to blue. A travelling wave
 * keeps them rippling; it isn't a morph-family field, so it builds up only as the pills turn into ribbons.
 */
val heroRibbons = heroPills.copy(
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
    fill = MorpheusGraphFill.linear(listOf(Red, Purple, Blue)),
)

private val Hold = 750.milliseconds
private val Morph = 3500.milliseconds

/** Pills, the morph to the ribbons, and the ribbons: the forward half of the loop, as a boomerang capture records it. */
private val HalfLoop = Hold + Morph + Hold

/**
 * Two pills morph in place into two crossing ribbons and back, on a loop. Both ends are one morph family,
 * so the nodes stay put while the shape and the colors cross over. The cycle follows the frame clock and
 * seeks one morph state back and forth, and a tuned end takes effect from the next frame.
 */
@Composable
fun HeroSample(modifier: Modifier = Modifier, pills: MorpheusGraphSpec = heroPills, ribbons: MorpheusGraphSpec = heroRibbons) {
    val currentPills by rememberUpdatedState(pills)
    val currentRibbons by rememberUpdatedState(ribbons)
    val state = rememberMorpheusGraphMorphState(pills)
    val cycleTime = rememberCycleTime(period = HalfLoop * 2)
    LaunchedEffect(state) {
        var seeking: Pair<MorpheusGraphSpec, MorpheusGraphSpec>? = null
        snapshotFlow { Triple(currentPills, currentRibbons, morphFraction(cycleTime.value)) }.collect { (from, to, fraction) ->
            // A seek keeps the screen it started from, so a tuned end restarts it from the pills.
            if (seeking != (from to to)) {
                state.snapTo(from)
                seeking = from to to
            }
            state.seekTo(to, fraction)
        }
    }
    Box(modifier.fillMaxSize().background(NearBlack).morpheusGraphBackground(state))
}

/** Where the loop is at [time] into it: the forward half, then the same half mirrored. */
private fun morphFraction(time: Duration): Float {
    val forwardTime = if (time <= HalfLoop) time else HalfLoop * 2 - time
    val linear = ((forwardTime - Hold) / Morph).toFloat().coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(linear)
}

private const val PILLS = "Pills"
private const val RIBBONS = "Ribbons"

internal val heroSample = Sample(
    id = "hero",
    title = "Pills to ribbons",
    description = "Two pills morph in place into two crossing ribbons and back, trading colors as they go.",
    category = SampleCategory.Showcase,
    recipe = CaptureRecipe(
        // 880×440 and 720×360 ran over the budget at 30 fps; the duration stays whole.
        canvasSize = DpSize(540.dp, 270.dp),
        duration = HalfLoop,
        stillAt = Duration.ZERO,
        loop = LoopMode.Boomerang,
        themes = CaptureThemes.DarkOnly,
        budgetBytes = 10_000_000,
    ),
    specs = listOf(TunableSpec(PILLS, heroPills), TunableSpec(RIBBONS, heroRibbons)),
    content = { specs -> HeroSample(pills = specs[PILLS], ribbons = specs[RIBBONS]) },
)
