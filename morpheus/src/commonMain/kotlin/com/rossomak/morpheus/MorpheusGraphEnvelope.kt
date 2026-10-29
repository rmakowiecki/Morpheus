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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

// The ribbon's center line and thickness each follow one slow sine across the width, at cycle counts
// far enough apart that the band never looks like a regular wave.
private const val RIBBON_CURVE_CYCLES = 0.8f
private const val RIBBON_THICKNESS_CYCLES = 1.3f
private const val RIBBON_THICKNESS_VARIATION = 0.35f

// Density holds at full until this fraction of the ribbon's half-thickness, then falls off, so the
// band's border is ragged rather than a ruled line.
private const val RIBBON_SOFT_EDGE = 0.55f
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
 * Only the random phases live here; every shape value comes from the spec passed in, so one envelope
 * serves every frame of a morph. Each ribbon takes its meander phases by its place among the spec's
 * ribbons, so two ribbons in one spec meander differently, and the first takes the same phases as a
 * lone ribbon.
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
                is Pill -> 1f - (pillAxisDistance(primitive, width, height, x, y) / hypot(width, height)).coerceIn(0f, 1f)
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

    private fun ribbonDensity(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float, y: Float): Float {
        val across = x / width
        val center = ribbonCenterAt(ribbon, phases, width, height, x)
        val thicknessScale = 1f + RIBBON_THICKNESS_VARIATION * sin(across * TWO_PI * RIBBON_THICKNESS_CYCLES + phases.thickness)
        val halfThickness = (ribbon.width * height * 0.5f * thicknessScale).coerceAtLeast(1f)
        return 1f - smoothStep(RIBBON_SOFT_EDGE, 1f, abs(y - center) / halfThickness)
    }

    /** The ribbon's center line at [x], meander and tilt included. */
    private fun ribbonCenterAt(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float): Float {
        val meander = ribbon.curve * sin(x / width * TWO_PI * RIBBON_CURVE_CYCLES + phases.curve)
        return (ribbon.center + meander) * height + ribbonSlope(ribbon) * (x - width / 2f)
    }

    private fun ribbonSlope(ribbon: Ribbon): Float = tan(ribbon.tilt.coerceIn(-MAX_RIBBON_TILT, MAX_RIBBON_TILT) * DEGREES_TO_RADIANS)

    /** Full density out to [Bloom.plateau] of the reach, thinning out to nothing at [Bloom.reach]. */
    private fun bloomDensity(bloom: Bloom, width: Float, height: Float, x: Float, y: Float): Float {
        val deltaX = bloom.x * width - x
        val deltaY = bloom.y * height - y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY) / max(width, height)
        val reach = bloom.reach.coerceAtLeast(MIN_BLOOM_REACH)
        return 1f - smoothStep(reach * bloom.plateau.coerceIn(0f, MAX_BLOOM_PLATEAU), reach, distance)
    }

    /** Full density out to [Pill.plateau] of the half-thickness from the axis, thinning out to nothing at the rim. */
    private fun pillDensity(pill: Pill, width: Float, height: Float, x: Float, y: Float): Float {
        val halfThickness = pillHalfThickness(pill, width, height)
        val plateau = halfThickness * pill.plateau.coerceIn(0f, MAX_PILL_PLATEAU)
        return 1f - smoothStep(plateau, halfThickness, pillAxisDistance(pill, width, height, x, y))
    }
}

private class RibbonPhases(val curve: Float, val thickness: Float)

/** Half of the pill's thickness, in pixels. */
private fun pillHalfThickness(pill: Pill, width: Float, height: Float): Float =
    pill.thickness.coerceAtLeast(MIN_PILL_THICKNESS) * max(width, height) / 2f

/** How far each end cap's center sits from the pill's center, in pixels; 0 leaves a disc. */
private fun pillHalfSpan(pill: Pill, width: Float, height: Float, halfThickness: Float): Float =
    (pill.length * max(width, height) / 2f - halfThickness).coerceAtLeast(0f)

/** Distance from the point to the segment between the pill's end cap centers, in pixels. */
private fun pillAxisDistance(pill: Pill, width: Float, height: Float, x: Float, y: Float): Float {
    val halfSpan = pillHalfSpan(pill, width, height, pillHalfThickness(pill, width, height))
    val angle = pill.rotation * DEGREES_TO_RADIANS
    val cosine = cos(angle)
    val sine = sin(angle)
    val deltaX = x - pill.x * width
    val deltaY = y - pill.y * height
    val along = (deltaX * cosine + deltaY * sine).coerceIn(-halfSpan, halfSpan)
    return hypot(deltaX - along * cosine, deltaY - along * sine)
}

private const val MIN_BLOOM_REACH = 0.01f
private const val MAX_BLOOM_PLATEAU = 0.95f
private const val MAX_RIBBON_TILT = 80f
private const val MIN_PILL_THICKNESS = 0.01f
private const val MAX_PILL_PLATEAU = 0.95f

internal const val TWO_PI = 6.2831855f

private fun closenessOf(distance: Float, width: Float, height: Float): Float =
    1f - (distance / hypot(width, height)).coerceIn(0f, 1f)

internal fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
    val progress = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
