@file:Suppress("MagicNumber")

package com.rossomak.morpheus

import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Rect
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Ring
import com.rossomak.morpheus.ShapePrimitive.Triangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Where a layer's nodes are present is a density field over the surface: 0 to 1, evaluated at a point.
// MorpheusGraphEnvelope combines a layer's primitives into that field; this file holds what each
// primitive contributes on its own. Every kind needs its own function because each is a different
// geometry (a distance to a curve, a distance into a rounded polygon) and takes different parameters,
// but they all end the same way: a distance in pixels, turned into a 0..1 falloff with smoothStep.
// Sizes are fractions of the longer side and angles are degrees, so a spec reads the same on any
// surface.
//
// Run for every node on every frame, so these allocate nothing: only floats.

// The ribbon's center line and thickness each follow one slow sine across the width, at cycle counts
// far enough apart that the band never looks like a regular wave.
private const val RIBBON_CURVE_CYCLES = 0.8f
private const val RIBBON_THICKNESS_CYCLES = 1.3f
private const val RIBBON_THICKNESS_VARIATION = 0.35f

// Density holds at full until this fraction of the ribbon's half-thickness, then falls off, so the
// band's border is ragged rather than a ruled line.
private const val RIBBON_SOFT_EDGE = 0.55f
private const val MAX_RIBBON_TILT = 80f

private const val RADIANS_TO_DEGREES = 57.29578f
private const val FULL_TURN = 360f
private const val MIN_SHAPE_SIZE = 0.01f
private const val MAX_SHAPE_PLATEAU = 0.95f

// Keeps the segment projection finite when a rounded triangle collapses to its incircle.
private const val MIN_SEGMENT_LENGTH_SQUARED = 1e-6f

internal class RibbonPhases(val curve: Float, val thickness: Float)

internal fun ribbonDensity(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float, y: Float): Float {
    val across = x / width
    val center = ribbonCenterAt(ribbon, phases, width, height, x)
    val thicknessScale = 1f + RIBBON_THICKNESS_VARIATION * sin(across * TWO_PI * RIBBON_THICKNESS_CYCLES + phases.thickness)
    val halfThickness = (ribbon.width * height * 0.5f * thicknessScale).coerceAtLeast(1f)
    return 1f - smoothStep(RIBBON_SOFT_EDGE, 1f, abs(y - center) / halfThickness)
}

/** The ribbon's center line at [x], meander and tilt included. */
internal fun ribbonCenterAt(ribbon: Ribbon, phases: RibbonPhases, width: Float, height: Float, x: Float): Float {
    val meander = ribbon.curve * sin(x / width * TWO_PI * RIBBON_CURVE_CYCLES + phases.curve)
    val slope = tan(ribbon.tilt.coerceIn(-MAX_RIBBON_TILT, MAX_RIBBON_TILT) * DEGREES_TO_RADIANS)
    return (ribbon.center + meander) * height + slope * (x - width / 2f)
}

/** Full density out to [Bloom.plateau] of the reach, thinning out to nothing at [Bloom.reach]. */
internal fun bloomDensity(bloom: Bloom, width: Float, height: Float, x: Float, y: Float): Float {
    val deltaX = bloom.x * width - x
    val deltaY = bloom.y * height - y
    val distance = sqrt(deltaX * deltaX + deltaY * deltaY) / max(width, height)
    val reach = bloom.reach.coerceAtLeast(MIN_SHAPE_SIZE)
    return 1f - smoothStep(reach * bloom.plateau.coerceIn(0f, MAX_SHAPE_PLATEAU), reach, distance)
}

/** Full density out to [Pill.plateau] of the half-thickness from the axis, thinning out to nothing at the rim. */
internal fun pillDensity(pill: Pill, width: Float, height: Float, x: Float, y: Float): Float {
    val halfThickness = pillHalfThickness(pill, width, height)
    val plateau = halfThickness * pill.plateau.coerceIn(0f, MAX_SHAPE_PLATEAU)
    return 1f - smoothStep(plateau, halfThickness, pillAxisDistance(pill, width, height, x, y))
}

/** Half of the pill's thickness, in pixels. */
private fun pillHalfThickness(pill: Pill, width: Float, height: Float): Float =
    pill.thickness.coerceAtLeast(MIN_SHAPE_SIZE) * max(width, height) / 2f

/** How far each end cap's center sits from the pill's center, in pixels; 0 leaves a disc. */
private fun pillHalfSpan(pill: Pill, width: Float, height: Float, halfThickness: Float): Float =
    (pill.length * max(width, height) / 2f - halfThickness).coerceAtLeast(0f)

/** Distance from the point to the segment between the pill's end cap centers, in pixels. */
internal fun pillAxisDistance(pill: Pill, width: Float, height: Float, x: Float, y: Float): Float {
    val halfSpan = pillHalfSpan(pill, width, height, pillHalfThickness(pill, width, height))
    val angle = pill.rotation * DEGREES_TO_RADIANS
    val cosine = cos(angle)
    val sine = sin(angle)
    val deltaX = x - pill.x * width
    val deltaY = y - pill.y * height
    val along = (deltaX * cosine + deltaY * sine).coerceIn(-halfSpan, halfSpan)
    return hypot(deltaX - along * cosine, deltaY - along * sine)
}

internal fun ringDensity(ring: Ring, width: Float, height: Float, x: Float, y: Float): Float {
    val halfThickness = ring.thickness.coerceAtLeast(MIN_SHAPE_SIZE) * max(width, height) / 2f
    val plateau = halfThickness * ring.plateau.coerceIn(0f, MAX_SHAPE_PLATEAU)
    return 1f - smoothStep(plateau, halfThickness, ringCurveDistance(ring, width, height, x, y))
}

/** In pixels; outside an arc's sweep, the distance to the nearer end. */
internal fun ringCurveDistance(ring: Ring, width: Float, height: Float, x: Float, y: Float): Float {
    val radius = ring.radius.coerceAtLeast(0f) * max(width, height)
    val centerX = ring.x * width
    val centerY = ring.y * height
    val deltaX = x - centerX
    val deltaY = y - centerY
    val sweep = ring.sweep.coerceIn(0f, FULL_TURN)
    val distance = hypot(deltaX, deltaY)
    if (sweep >= FULL_TURN) return abs(distance - radius)

    var angleFromStart = (atan2(deltaY, deltaX) * RADIANS_TO_DEGREES - ring.startAngle) % FULL_TURN
    if (angleFromStart < 0f) angleFromStart += FULL_TURN
    if (angleFromStart <= sweep) return abs(distance - radius)

    val startAngle = ring.startAngle * DEGREES_TO_RADIANS
    val endAngle = (ring.startAngle + sweep) * DEGREES_TO_RADIANS
    val startCapDistance = hypot(x - (centerX + radius * cos(startAngle)), y - (centerY + radius * sin(startAngle)))
    val endCapDistance = hypot(x - (centerX + radius * cos(endAngle)), y - (centerY + radius * sin(endAngle)))
    return min(startCapDistance, endCapDistance)
}

internal fun rectDensity(rect: Rect, width: Float, height: Float, x: Float, y: Float): Float {
    val longSide = max(width, height)
    val halfWidth = rect.width.coerceAtLeast(MIN_SHAPE_SIZE) * longSide / 2f
    val halfHeight = rect.height.coerceAtLeast(MIN_SHAPE_SIZE) * longSide / 2f
    val inradius = min(halfWidth, halfHeight)
    val cornerRadius = (rect.cornerRadius * longSide).coerceIn(0f, inradius)
    val cosine = cos(rect.rotation * DEGREES_TO_RADIANS)
    val sine = sin(rect.rotation * DEGREES_TO_RADIANS)
    val deltaX = x - rect.x * width
    val deltaY = y - rect.y * height
    val depth = roundedRectDepth(
        localX = abs(localX(deltaX, deltaY, cosine, sine)),
        localY = abs(localY(deltaX, deltaY, cosine, sine)),
        halfWidth = halfWidth,
        halfHeight = halfHeight,
        cornerRadius = cornerRadius,
    )
    return solidDensity(depth, inradius, rect.plateau)
}

internal fun triangleDensity(triangle: Triangle, width: Float, height: Float, x: Float, y: Float): Float {
    val longSide = max(width, height)
    val halfBase = triangle.width.coerceAtLeast(MIN_SHAPE_SIZE) * longSide / 2f
    val triangleHeight = triangle.height.coerceAtLeast(MIN_SHAPE_SIZE) * longSide
    val inradius = triangleInradius(halfBase, triangleHeight)
    val cornerRadius = (triangle.cornerRadius * longSide).coerceIn(0f, inradius)
    val cosine = cos(triangle.rotation * DEGREES_TO_RADIANS)
    val sine = sin(triangle.rotation * DEGREES_TO_RADIANS)
    val deltaX = x - triangle.x * width
    val deltaY = y - triangle.y * height
    val depth = roundedTriangleDepth(
        localX = abs(localX(deltaX, deltaY, cosine, sine)),
        localY = localY(deltaX, deltaY, cosine, sine),
        halfBase = halfBase,
        triangleHeight = triangleHeight,
        cornerRadius = cornerRadius,
    )
    return solidDensity(depth, inradius, triangle.plateau)
}

/** [depth] is how far in from the rim the point is, in pixels. */
private fun solidDensity(depth: Float, inradius: Float, plateau: Float): Float =
    smoothStep(0f, 1f - plateau.coerceIn(0f, MAX_SHAPE_PLATEAU), depth / inradius)

private fun localX(deltaX: Float, deltaY: Float, cosine: Float, sine: Float): Float = deltaX * cosine + deltaY * sine

private fun localY(deltaX: Float, deltaY: Float, cosine: Float, sine: Float): Float = -deltaX * sine + deltaY * cosine

/** Takes the point's distances from the center along the rectangle's own axes, both non-negative. */
private fun roundedRectDepth(localX: Float, localY: Float, halfWidth: Float, halfHeight: Float, cornerRadius: Float): Float {
    // Distance to the rectangle shrunk by the corner radius, less that radius: negative inside.
    val beyondX = localX - (halfWidth - cornerRadius)
    val beyondY = localY - (halfHeight - cornerRadius)
    val outside = hypot(max(beyondX, 0f), max(beyondY, 0f))
    val inside = min(max(beyondX, beyondY), 0f)
    return cornerRadius - outside - inside
}

/** Takes the point relative to the centroid on the triangle's own axes, [localX] non-negative and [localY] pointing to the base. */
private fun roundedTriangleDepth(localX: Float, localY: Float, halfBase: Float, triangleHeight: Float, cornerRadius: Float): Float {
    val inradius = triangleInradius(halfBase, triangleHeight)
    val centroidToBase = triangleHeight / 3f
    val centroidToApex = 2f * triangleHeight / 3f
    // Rounding is the triangle shrunk about its incenter until its edges sit a corner radius further in,
    // then grown back by that radius. It is symmetric, so only the right half is checked.
    val incenterY = centroidToBase - inradius
    val shrink = (inradius - cornerRadius) / inradius
    val apexY = incenterY + (-centroidToApex - incenterY) * shrink
    val baseY = incenterY + inradius * shrink
    val shrunkHalfBase = halfBase * shrink
    val distance = min(
        segmentDistance(localX, localY, 0f, apexY, shrunkHalfBase, baseY),
        segmentDistance(localX, localY, 0f, baseY, shrunkHalfBase, baseY),
    )
    val isInside = localY >= apexY && localY <= baseY && localX * (baseY - apexY) <= shrunkHalfBase * (localY - apexY)
    val signedDistance = if (isInside) -distance else distance
    return cornerRadius - signedDistance
}

private fun triangleInradius(halfBase: Float, triangleHeight: Float): Float =
    halfBase * triangleHeight / (halfBase + hypot(halfBase, triangleHeight))

private fun segmentDistance(pointX: Float, pointY: Float, startX: Float, startY: Float, endX: Float, endY: Float): Float {
    val edgeX = endX - startX
    val edgeY = endY - startY
    val lengthSquared = (edgeX * edgeX + edgeY * edgeY).coerceAtLeast(MIN_SEGMENT_LENGTH_SQUARED)
    val along = (((pointX - startX) * edgeX + (pointY - startY) * edgeY) / lengthSquared).coerceIn(0f, 1f)
    return hypot(pointX - startX - edgeX * along, pointY - startY - edgeY * along)
}

internal fun smoothStep(edgeStart: Float, edgeEnd: Float, value: Float): Float {
    val progress = ((value - edgeStart) / (edgeEnd - edgeStart)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
