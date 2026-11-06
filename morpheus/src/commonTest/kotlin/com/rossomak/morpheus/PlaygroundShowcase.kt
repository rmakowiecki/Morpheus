package com.rossomak.morpheus

import androidx.compose.animation.core.FastOutSlowInEasing
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill

// The "Comet playground" sample's three specs, copied from samples/shared's showcase/CometPlayground.kt (the
// library can't depend on the samples). Fills are left out: they never touch the mesh. Its ghost layer is the
// hero's, spelled out in HeroShowcase.kt.

internal val playgroundFront = heroFront.copy(density = 100f, comets = heroFront.comets.copy(zone = 0.75f, tail = 4f, lead = 0.25f), morphGap = -1f)

internal val playgroundFrame = MorpheusGraphSpec(
    seed = 41,
    layers = listOf(
        heroGhost,
        playgroundFront.copy(
            shapes = listOf(
                Pill(x = 0.5f, y = 0.09f, length = 0.66f, thickness = 0.1f, rotation = 0f),
                Pill(x = 0.91f, y = 0.5f, length = 0.66f, thickness = 0.1f, rotation = 90f),
                Pill(x = 0.5f, y = 0.91f, length = 0.66f, thickness = 0.1f, rotation = 0f),
                Pill(x = 0.09f, y = 0.5f, length = 0.66f, thickness = 0.1f, rotation = 90f),
            ),
        ),
    ),
)

internal val playgroundBlooms = playgroundFrame.copy(
    layers = listOf(
        heroGhost,
        playgroundFront.copy(
            shapes = listOf(
                Bloom(x = 0.2f, y = 0.2f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.8f, y = 0.2f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.2f, y = 0.8f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.8f, y = 0.8f, reach = 0.15f, plateau = 0.6f),
                Bloom(x = 0.5f, y = 0.5f, reach = 0.17f, plateau = 0.6f),
            ),
            comets = playgroundFront.comets.copy(zone = 0.4f, tail = 2f, lead = -0.2f),
        ),
    ),
)

internal val playgroundCross = playgroundFrame.copy(
    layers = listOf(
        heroGhost,
        playgroundFront.copy(
            shapes = listOf(
                Pill(x = 0.5f, y = 0.5f, length = 0.9f, thickness = 0.13f, rotation = 0f),
                Pill(x = 0.5f, y = 0.5f, length = 0.9f, thickness = 0.13f, rotation = 90f),
            ),
        ),
    ),
)

/** The playground's three morphs in loop order, by the names the sample's Tune sheet gives them. */
internal val playgroundLegs = listOf(
    "frame to blooms" to MorpheusGraphMorph(playgroundFrame, playgroundBlooms, 0f),
    "blooms to cross" to MorpheusGraphMorph(playgroundBlooms, playgroundCross, 0f),
    "cross to frame" to MorpheusGraphMorph(playgroundCross, playgroundFrame, 0f),
)

/** The playground sample's loop at 60 fps: each spec held for half its hold, the morph to the next, half a hold. */
internal const val PLAYGROUND_LOOP_FRAMES = 405
private const val PLAYGROUND_HOLD_SECONDS = 0.75f
private const val PLAYGROUND_MORPH_SECONDS = 1.5f
private const val PLAYGROUND_LEG_SECONDS = PLAYGROUND_HOLD_SECONDS + PLAYGROUND_MORPH_SECONDS

/** What the playground sample shows at [time] into its loop, at its default easings and timing. */
internal fun playgroundMorphAt(time: Float): MorpheusGraphMorph {
    val index = (time / PLAYGROUND_LEG_SECONDS).toInt().coerceIn(0, playgroundLegs.lastIndex)
    val linear = ((time - index * PLAYGROUND_LEG_SECONDS - PLAYGROUND_HOLD_SECONDS / 2) / PLAYGROUND_MORPH_SECONDS).coerceIn(0f, 1f)
    return playgroundLegs[index].second.copy(fraction = FastOutSlowInEasing.transform(linear))
}
