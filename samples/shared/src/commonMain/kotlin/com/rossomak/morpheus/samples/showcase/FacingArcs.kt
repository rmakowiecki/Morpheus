@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.showcase

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.MorphEasing
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleSection
import com.rossomak.morpheus.samples.TunableMorph
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.easings
import kotlin.time.Duration.Companion.milliseconds

private val Teal = Color(0xFF1FD1B2)
private val Blue = Color(0xFF2F6BFF)

private const val FACING_ARCS_SEED = 43

/**
 * The playground's front layer with its two sweeps the longest pause apart: each arc leaves back first and tips last,
 * the comets set off from its tips just after they fade and cross the pause with long tails, and the other arc forms
 * tips first as they land. Overlapping sweeps would form the other arc's tips before this one's leave.
 */
private val facingArcsFront = playgroundFront.copy(
    comets = playgroundFront.comets.copy(pieceSize = 11f, zone = 0.15f, tail = 8f, lead = -0.15f),
    morphGap = 0.4f,
)

/** A "C" by the left side, open to the right: a three-quarter ring whose gap faces the middle. */
val facingArcsLeft = MorpheusGraphSpec(
    seed = FACING_ARCS_SEED,
    layers = listOf(
        playgroundGhost,
        facingArcsFront.copy(shapes = listOf(Ring(x = 0.22f, y = 0.5f, radius = 0.16f, thickness = 0.08f, startAngle = 45f, sweep = 270f))),
    ),
    fill = MorpheusGraphFill.linear(listOf(Teal, Blue)),
)

/** The mirrored "C" by the right side, open to the left, so the two arcs' tips face each other across the middle. */
val facingArcsRight = facingArcsLeft.copy(
    layers = listOf(
        playgroundGhost,
        facingArcsFront.copy(shapes = listOf(Ring(x = 0.78f, y = 0.5f, radius = 0.16f, thickness = 0.08f, startAngle = 225f, sweep = 270f))),
    ),
)

private val FacingArcsTiming = MorphTiming(hold = 750.milliseconds, morph = 1000.milliseconds)

/**
 * A "C" and a mirrored "C" on opposite sides morph into each other and back, on a loop, to check that the comets
 * leave only from the sectors nearest the other arc, its tips, and land only on the other arc's tips.
 */
@Composable
fun FacingArcsSample(
    modifier: Modifier = Modifier,
    specs: List<MorpheusGraphSpec> = listOf(facingArcsLeft, facingArcsRight),
    easings: List<Easing> = specs.map { LinearEasing },
    timing: MorphTiming = FacingArcsTiming,
) = CometPlaygroundSample(modifier, specs, easings, timing)

private const val LEFT = "Left C"
private const val RIGHT = "Right C"
private const val LEFT_TO_RIGHT = "Left → Right"
private const val RIGHT_TO_LEFT = "Right → Left"

internal val facingArcsSample = Sample(
    id = "facing-arcs",
    title = "Facing arcs",
    summary = "Two facing arcs morph into each other, to watch where comets leave and land.",
    description = "An arc on each side, tips facing, morphs into the other and back. Each arc is a ring with a sweep of three quarters of a turn. Comets leave only " +
        "from the tips nearest the other arc, and land only on its tips.",
    section = SampleSection.Showcase,
    // Drawn on its own near-black background whatever the theme.
    recipe = CaptureRecipe(themes = CaptureThemes.DarkOnly),
    specs = listOf(TunableSpec(LEFT, facingArcsLeft), TunableSpec(RIGHT, facingArcsRight)),
    morphs = listOf(TunableMorph(LEFT_TO_RIGHT, MorphEasing.Linear), TunableMorph(RIGHT_TO_LEFT, MorphEasing.Linear)),
    timing = FacingArcsTiming,
    galleryOnly = true,
    content = { specs ->
        FacingArcsSample(
            specs = listOf(specs[LEFT], specs[RIGHT]),
            easings = listOf(specs.easing(LEFT_TO_RIGHT), specs.easing(RIGHT_TO_LEFT)),
            timing = specs.timing,
        )
    },
)
