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
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.rememberMorpheusGraphMorphState
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.MorphCycle
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.cyclePeriod
import com.rossomak.morpheus.samples.rememberCycleTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val Blue = Color(0xFF2F6BFF)
private val Purple = Color(0xFF9B3DFF)
private val Red = Color(0xFFFF2A3D)
private val NearBlack = Color(0xFF050807)

/** The very dense front mesh both ends share, so its family fields can't diverge and nodes stay put. */
private val heroFront = MorpheusGraphLayerSpec(
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
    comets = MorpheusGraphComets(size = 1.25f, spark = 0.6f),
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
                Ribbon(center = 0.5f, width = 0.25f, curve = 0.18f, tilt = 25f),
                Ribbon(center = 0.5f, width = 0.25f, curve = 0.18f, tilt = -25f),
            ),
            waveAmplitude = 0.06f,
            waveSpeed = 0.45f,
        ),
    ),
    fill = MorpheusGraphFill.linear(listOf(Red, Purple, Blue)),
)

/** Each end rests 1.5 s between morphs; a leg, half a rest, the morph and half a rest, is what a boomerang capture records. */
private val HeroTiming = MorphTiming(hold = 1500.milliseconds, morph = 3500.milliseconds)

/**
 * Two pills morph in place into two crossing ribbons and back, on a loop. Both ends are one morph family,
 * so the nodes stay put while the shape and the colors cross over. The way back is a morph of its own, from
 * the ribbons, with its own easing. A tuned end or easing takes effect from the next frame, and so does a tuned timing.
 */
@Composable
fun HeroSample(
    modifier: Modifier = Modifier,
    pills: MorpheusGraphSpec = heroPills,
    ribbons: MorpheusGraphSpec = heroRibbons,
    toRibbons: Easing = FastOutSlowInEasing,
    toPills: Easing = FastOutSlowInEasing,
    timing: MorphTiming = HeroTiming,
) {
    val state = rememberMorpheusGraphMorphState(pills)
    val cycleTime = rememberCycleTime(period = timing.cyclePeriod(legs = 2))
    MorphCycle(state, specs = listOf(pills, ribbons), easings = listOf(toRibbons, toPills), timing = timing, cycleTime = cycleTime)
    Box(modifier.fillMaxSize().background(NearBlack).morpheusGraphBackground(state))
}

private const val PILLS = "Pills"
private const val RIBBONS = "Ribbons"
private const val TO_RIBBONS = "Pills → Ribbons"
private const val TO_PILLS = "Ribbons → Pills"

internal val heroSample = Sample(
    id = "hero",
    title = "Pills to ribbons",
    description = "Two pills morph in place into two crossing ribbons and back, trading colors as they go.",
    category = SampleCategory.Showcase,
    recipe = CaptureRecipe(
        // Not a standard canvas: the shapes are composed for 2:1. 880×440 and 720×360 ran over the budget
        // at 30 fps; the duration stays whole.
        canvasSize = DpSize(540.dp, 270.dp),
        duration = HeroTiming.leg,
        stillAt = Duration.ZERO,
        loop = LoopMode.Boomerang,
        themes = CaptureThemes.DarkOnly,
        budgetBytes = 10_000_000,
    ),
    specs = listOf(TunableSpec(PILLS, heroPills), TunableSpec(RIBBONS, heroRibbons)),
    morphs = listOf(TunableMorph(TO_RIBBONS), TunableMorph(TO_PILLS)),
    timing = HeroTiming,
    content = { specs ->
        HeroSample(
            pills = specs[PILLS],
            ribbons = specs[RIBBONS],
            toRibbons = specs.easing(TO_RIBBONS),
            toPills = specs.easing(TO_PILLS),
            timing = specs.timing,
        )
    },
)
