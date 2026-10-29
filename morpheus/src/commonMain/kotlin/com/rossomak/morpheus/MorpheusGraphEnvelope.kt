@file:Suppress("MagicNumber")

package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.ShapePrimitive.Triangle
import com.rossomak.morpheus.ShapePrimitive.Uniform
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val WAVE_CYCLES = 1.1f
internal const val DEGREES_TO_RADIANS = 0.017453292f

/** Seeds of the second and later ribbons' phases are this far apart, so two ribbons never meander alike. */
private const val RIBBON_PHASE_SEED_STRIDE = 104_729

/**
 * The overall shape of one layer, from its [ShapePrimitive]s, in three parts:
 * - [densityAt] decides where nodes are *present*: a node shows in proportion to the density at its
 *   home, so a shape is just a density field over the surface, from 0 to 1. It is evaluated every
 *   frame, against the current spec and surface size, which is what lets shapes morph and follow a
 *   resizing surface: nodes fade in and out where the field changes, but never move.
 * - [closenessAt] orders the nodes a morph switches: how near a point is to the shape, falling off
 *   over the whole surface instead of stopping at the shape's border, so even points far outside it
 *   still rank by distance.
 * - [waveOffsetY] *moves* every node's home over time, uniformly for neighbors, so the shape itself
 *   breathes while the mesh topology stays intact.
 *
 * What each primitive contributes on its own lives in `MorpheusGraphShapeDensity.kt`. Only the random
 * phases live here; every shape value comes from the spec passed in, so one envelope serves every
 * frame of a morph. Each ribbon takes its meander phases by its place among the spec's ribbons, so
 * two ribbons in one spec meander differently, and the first takes the same phases as a lone ribbon.
 */
internal class MorpheusGraphEnvelope(private val seed: Int) {
    private val firstRibbonPhases: RibbonPhases
    private val wavePhase: Float
    private val laterRibbonPhases = ArrayList<RibbonPhases>()

    init {
        val random = Random(seed)
        firstRibbonPhases = RibbonPhases(curve = random.nextFloat() * TWO_PI, thickness = random.nextFloat() * TWO_PI)
        wavePhase = random.nextFloat() * TWO_PI
    }

    /** The strongest weighted primitive at the point, never under the spec's outlier floor. */
    fun densityAt(spec: MorpheusGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var density = 0f
        var ribbonOrdinal = 0
        for (primitive in spec.shapes) {
            val weight = primitive.weight.coerceIn(0f, 1f)
            val ordinal = if (primitive is Ribbon) ribbonOrdinal++ else 0
            if (weight <= density) continue
            val falloff = when (primitive) {
                is Uniform -> 1f
                is Ribbon -> ribbonDensity(primitive, ribbonPhases(ordinal), width, height, x, y)
                is Bloom -> bloomDensity(primitive, width, height, x, y)
                is Pill -> pillDensity(primitive, width, height, x, y)
                is Ring -> ringDensity(primitive, width, height, x, y)
                is Rect -> rectDensity(primitive, width, height, x, y)
                is Triangle -> triangleDensity(primitive, width, height, x, y)
            }
            density = max(density, weight * falloff)
        }
        return max(density, spec.envelopeFloor)
    }

    /**
     * How near the point is to the shape, from 0 (the far side of the surface) to 1 (on a ribbon's
     * center line, a bloom's, rectangle's or triangle's center, a pill's axis or a ring's curve), as the
     * strongest weighted primitive like [densityAt]. Uniform is equally near everywhere.
     */
    fun closenessAt(spec: MorpheusGraphLayerSpec, width: Float, height: Float, x: Float, y: Float): Float {
        var closeness = 0f
        var ribbonOrdinal = 0
        for (primitive in spec.shapes) {
            val weight = primitive.weight.coerceIn(0f, 1f)
            val ordinal = if (primitive is Ribbon) ribbonOrdinal++ else 0
            if (weight <= closeness) continue
            val nearness = when (primitive) {
                is Uniform -> 1f
                is Ribbon -> 1f - (abs(y - ribbonCenterAt(primitive, ribbonPhases(ordinal), width, height, x)) / height).coerceIn(0f, 1f)
                is Bloom -> closenessOf(hypot(primitive.x * width - x, primitive.y * height - y), width, height)
                is Pill -> closenessOf(pillAxisDistance(primitive, width, height, x, y), width, height)
                is Ring -> closenessOf(ringCurveDistance(primitive, width, height, x, y), width, height)
                is Rect -> closenessOf(hypot(primitive.x * width - x, primitive.y * height - y), width, height)
                is Triangle -> closenessOf(hypot(primitive.x * width - x, primitive.y * height - y), width, height)
            }
            closeness = max(closeness, weight * nearness)
        }
        return closeness
    }

    /** Vertical wave offset at [x]; [waveTravel] is the wave's accumulated travel, speed already applied. */
    fun waveOffsetY(spec: MorpheusGraphLayerSpec, width: Float, height: Float, x: Float, waveTravel: Float): Float {
        if (spec.waveAmplitude == 0f) return 0f
        val phase = x / width * TWO_PI * WAVE_CYCLES - waveTravel + wavePhase
        return spec.waveAmplitude * height * sin(phase)
    }

    private fun ribbonPhases(ordinal: Int): RibbonPhases {
        if (ordinal == 0) return firstRibbonPhases
        while (laterRibbonPhases.size < ordinal) {
            val random = Random(seed + (laterRibbonPhases.size + 1) * RIBBON_PHASE_SEED_STRIDE)
            laterRibbonPhases += RibbonPhases(curve = random.nextFloat() * TWO_PI, thickness = random.nextFloat() * TWO_PI)
        }
        return laterRibbonPhases[ordinal - 1]
    }
}

internal const val TWO_PI = 6.2831855f

private fun closenessOf(distance: Float, width: Float, height: Float): Float =
    1f - (distance / hypot(width, height)).coerceIn(0f, 1f)
