package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.collections.shouldBeEmpty
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test

/**
 * Keeps a morph meeting the still spec at both of its ends: a node's heat, dot alpha and presence just
 * short of an end match the end itself, within a small tolerance, in both directions. Comet sparks once
 * broke this: a sparking node was hot from the morph's first frame, so as a morph came back to fraction 0
 * a group of nodes lost its glow in one frame, and gained it again as the next morph left. Checked on the
 * "Pills to ribbons" showcase both ways, and on a morph between two corners with sparks at full.
 */
class MorphEndContinuityTest {

    @Test
    fun `a morph's nodes don't jump as it reaches or leaves either end`() {
        val cornerToCorner = MorpheusGraphPresets.MorphFamily.cornerBloom.withFrontSpark() to
            MorpheusGraphPresets.MorphFamily.bottomStartBloom.withFrontSpark()
        val morphs = listOf(
            "pills to ribbons" to (heroPills to heroRibbons),
            "ribbons to pills" to (heroRibbons to heroPills),
            "corner to corner" to cornerToCorner,
        )
        val steps = listOf(NEAR_START to 0f, 0f to NEAR_START, NEAR_END to 1f, 1f to NEAR_END)
        val jumps = morphs.flatMap { (name, ends) ->
            SURFACES.flatMap { (width, height) ->
                steps.flatMap { (held, next) -> jumpsBetween("$name/${width.toInt()}x${height.toInt()}/$held to $next", ends, held, next, width, height) }
            }
        }

        jumps.shouldBeEmpty()
    }

    private fun MorpheusGraphSpec.withFrontSpark() = copy(layers = layers.dropLast(1) + layers.last().copy(cometSpark = 1f))

    /**
     * Holds the morph at [held] until its edges settle, then steps one frame to [next], and lists every
     * node whose heat, dot alpha or presence moved further than its tolerance in that frame.
     */
    private fun jumpsBetween(
        where: String,
        ends: Pair<MorpheusGraphSpec, MorpheusGraphSpec>,
        held: Float,
        next: Float,
        width: Float,
        height: Float,
    ): List<String> {
        val morph = MorpheusGraphMorph(ends.first, ends.second, fraction = held)
        val fields = HashMap<Int, LayerField>()
        fun step(fraction: Float, frame: Int) {
            for (layer in morphLayers(morph.copy(fraction = fraction), TEST_FILL)) {
                val field = fields.getOrPut(layer.slotFromFront) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(frame * FRAME_SECONDS, width, height)
            }
        }
        repeat(HELD_FRAMES) { frame -> step(held, frame) }
        val before = fields.mapValues { (_, field) -> NodeState.of(field) }
        step(next, HELD_FRAMES)
        return fields.flatMap { (slot, field) ->
            val after = NodeState.of(field)
            val was = before.getValue(slot)
            (0 until field.nodeCount).mapNotNull { node ->
                val moved = listOfNotNull(
                    "heat".takeIf { abs(after.heat[node] - was.heat[node]) > MAX_HEAT_STEP },
                    "alpha".takeIf { abs(after.alpha[node] - was.alpha[node]) > MAX_ALPHA_STEP },
                    "presence".takeIf { abs(after.presence[node] - was.presence[node]) > MAX_PRESENCE_STEP },
                )
                "$where/slot $slot/node $node: $moved".takeIf { moved.isNotEmpty() }
            }
        }
    }

    /** Every node's heat, dot alpha and presence after one [LayerField.advance]. */
    private class NodeState(val heat: FloatArray, val alpha: FloatArray, val presence: FloatArray) {
        companion object {
            fun of(field: LayerField) = NodeState(
                FloatArray(field.nodeCount) { field.nodeHotWeight(it) },
                FloatArray(field.nodeCount) { field.nodeAlpha(it) },
                FloatArray(field.nodeCount) { field.nodePresence(it) },
            )
        }
    }

    private companion object {
        /** The standard surfaces, in px, and the recipe canvas the showcase is measured on. */
        val SURFACES = listOf(640f to 360f, 360f to 640f, 540f to 270f, BenchmarkCanvas.Recipe.width to BenchmarkCanvas.Recipe.height)
        const val FRAME_SECONDS = 1f / 60f
        const val PX_PER_DP = 2f

        /** A frame's step of the showcase's eased morph as it eases into an end is about this small. */
        const val NEAR_START = 0.001f
        const val NEAR_END = 0.999f

        /** Long enough at one fraction for edges and dots to finish fading. */
        const val HELD_FRAMES = 60
        const val MAX_HEAT_STEP = 0.05f
        const val MAX_ALPHA_STEP = 0.1f
        const val MAX_PRESENCE_STEP = 0.05f
        val TEST_FILL = MorpheusGraphFill.Solid(Color.White)
    }
}
