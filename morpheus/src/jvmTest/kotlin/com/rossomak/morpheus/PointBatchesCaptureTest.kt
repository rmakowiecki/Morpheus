@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * Draws real layers in point batches and per element ([drawPerElement]) on the recipe canvas and compares the
 * pixels; see the tolerances below. Layers are drawn in solid white: a fill recolors the layer after either has
 * drawn it, the same way for each.
 */
class PointBatchesCaptureTest {

    @Test
    fun `point batches draw edges as drawLine does, and everything but dot rims and overlaps within two levels`() {
        val canvas = BenchmarkCanvas.Recipe
        val width = canvas.width.toInt()
        val height = canvas.height.toInt()
        val failures = ArrayList<String>()
        for ((name, morph) in cases()) {
            val holder = MorpheusGraphHolder()
            val layers = holder.layersFor(morph, GalleryLoop.FILL)
            val fields = holder.fieldsFor(layers, canvas.referenceSize, canvas.pxPerDp)
            for (step in 0..SETTLE_STEPS) fields.forEach { it.advance(step * STEP_SECONDS, canvas.width, canvas.height) }
            fields.forEachIndexed { index, field ->
                val case = "$name slot ${layers[index].slotFromFront}"
                val edgesBatched = renderBatched(field, width, height, edgesOnly = true)
                val edgesInLevelOrder = renderPerElement(field, width, height, edgesOnly = true, edgesInLevelOrder = true)
                if (!edgesBatched.contentEquals(edgesInLevelOrder)) failures += "$case: edges differ from drawLine in level order"
                val edgeOrder = Difference.of(renderPerElement(field, width, height, edgesOnly = true), edgesBatched)
                if (edgeOrder.largest > EDGE_ORDER_TOLERANCE) failures += "$case: edges in level order differ by ${edgeOrder.largest}"
                val comparison = DotAreas.of(field, width, height).compare(renderPerElement(field, width, height), renderBatched(field, width, height))
                println("$case: edges in level order $edgeOrder; $comparison")
                if (comparison.elsewhere > DOT_LEVEL_TOLERANCE) failures += "$case: $comparison"
            }
        }
        withClue(failures.joinToString("\n")) { failures.size shouldBe 0 }
    }

    @Test
    fun `layers draw the same point batches with or without frame stats`() {
        val canvas = BenchmarkCanvas.Recipe
        val failures = ArrayList<String>()
        for ((name, morph) in cases()) {
            val holder = MorpheusGraphHolder()
            val layers = holder.layersFor(morph, GalleryLoop.FILL)
            val fields = holder.fieldsFor(layers, canvas.referenceSize, canvas.pxPerDp)
            for (step in 0..SETTLE_STEPS) fields.forEach { it.advance(step * STEP_SECONDS, canvas.width, canvas.height) }
            fields.forEachIndexed { index, field ->
                val withoutStats = draw(canvas.width.toInt(), canvas.height.toInt()) {
                    field.draw(this, LayerFill.Solid(Color.White), HaloBrushCache(Color.White), SETTLE_STEPS * STEP_SECONDS, showViolations = false)
                }
                if (!withoutStats.contentEquals(renderBatched(field, canvas.width.toInt(), canvas.height.toInt()))) {
                    failures += "$name slot ${layers[index].slotFromFront}"
                }
            }
        }
        withClue("differ without frame stats: ${failures.joinToString()}") { failures.size shouldBe 0 }
    }

    private fun cases(): List<Pair<String, MorpheusGraphMorph>> =
        MorpheusGraphPresets.all.mapIndexed { index, spec -> "preset $index" to GalleryLoop.still(spec) } +
            listOf(
                "hero pills" to GalleryLoop.still(heroPills),
                "hero ribbons" to GalleryLoop.still(heroRibbons),
                "hero half way" to MorpheusGraphMorph(heroPills, heroRibbons, MID_FRACTION),
                "playground frame" to GalleryLoop.still(playgroundFrame),
                "playground blooms" to GalleryLoop.still(playgroundBlooms),
                "playground cross" to GalleryLoop.still(playgroundCross),
            ) +
            playgroundLegs.flatMap { (leg, morph) -> COMET_FRACTIONS.map { fraction -> "playground $leg at $fraction" to morph.copy(fraction = fraction) } }

    /** [field] drawn per element in white over black, as ARGB pixels; [edgesOnly] and [edgesInLevelOrder] as in [drawPerElement]. */
    private fun renderPerElement(field: LayerField, width: Int, height: Int, edgesOnly: Boolean = false, edgesInLevelOrder: Boolean = false): IntArray = draw(width, height) {
        drawPerElement(field, Color.White, HaloBrushCache(Color.White), SETTLE_STEPS * STEP_SECONDS, edgesOnly, edgesInLevelOrder)
    }

    /** [field] drawn by [LayerField.draw], in point batches, in white over black, as ARGB pixels; [edgesOnly] skips halos and dots. */
    private fun renderBatched(field: LayerField, width: Int, height: Int, edgesOnly: Boolean = false): IntArray {
        val stats = MorpheusGraphFrameStats().apply {
            skipHalos = edgesOnly
            skipDots = edgesOnly
        }
        stats.probe.startLayer(0, field)
        return draw(width, height) {
            field.draw(this, LayerFill.Solid(Color.White), HaloBrushCache(Color.White), SETTLE_STEPS * STEP_SECONDS, showViolations = false, probe = stats.probe)
        }
    }

    /** What [content] draws over black onto a [width] by [height] image, as ARGB pixels. */
    private fun draw(width: Int, height: Int, content: DrawScope.() -> Unit): IntArray {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Color.Black)
            content()
        }
        return IntArray(width * height).also { bitmap.readPixels(it) }
    }

    /** How many pixels differ between two images, and the largest difference in any channel, in levels out of 255. */
    private class Difference(val pixels: Int, val largest: Int) {
        override fun toString(): String = "$pixels pixels differ, by at most $largest"

        companion object {
            fun of(first: IntArray, second: IntArray): Difference {
                var pixels = 0
                var largest = 0
                for (pixel in first.indices) {
                    val difference = channelDifference(first[pixel], second[pixel])
                    if (difference > 0) pixels++
                    largest = max(largest, difference)
                }
                return Difference(pixels, largest)
            }
        }
    }

    /**
     * Which pixels lie within a pixel of a drawn dot's rim, at its own radius or its size bin's, and which lie
     * in more than one dot; and how many drawn dot pairs overlap, and how many dots overlap another.
     */
    private class DotAreas(private val width: Int, private val height: Int) {
        private val rim = BooleanArray(width * height)
        private val dotsCovering = IntArray(width * height)
        var overlappingPairs = 0
        var overlappingDots = 0
        var dots = 0

        /** Counts [dot] on every pixel within its outer reach, and marks those in its rim band. */
        fun mark(dot: Dot) {
            for (y in max(0, floor(dot.y - dot.outer).toInt())..min(height - 1, ceil(dot.y + dot.outer).toInt())) {
                for (x in max(0, floor(dot.x - dot.outer).toInt())..min(width - 1, ceil(dot.x + dot.outer).toInt())) {
                    val distance = sqrt((x + 0.5f - dot.x).let { it * it } + (y + 0.5f - dot.y).let { it * it })
                    if (distance > dot.outer) continue
                    dotsCovering[y * width + x]++
                    if (distance >= dot.inner) rim[y * width + x] = true
                }
            }
        }

        /** The largest difference in any channel, in levels out of 255, elsewhere, at rims and in overlaps. */
        fun compare(perElement: IntArray, batched: IntArray): Comparison {
            var elsewhere = 0
            var atRims = 0
            var inOverlaps = 0
            for (pixel in perElement.indices) {
                val difference = channelDifference(perElement[pixel], batched[pixel])
                when {
                    dotsCovering[pixel] > 1 -> inOverlaps = max(inOverlaps, difference)
                    rim[pixel] -> atRims = max(atRims, difference)
                    else -> elsewhere = max(elsewhere, difference)
                }
            }
            return Comparison(elsewhere, atRims, inOverlaps, overlappingPairs, overlappingDots, dots)
        }

        companion object {
            fun of(field: LayerField, width: Int, height: Int): DotAreas {
                val batches = PointBatches()
                batches.clearDots(field.smallestDotRadius, field.largestDotRadius)
                val areas = DotAreas(width, height)
                val dots = ArrayList<Dot>()
                field.forEachVisibleDot { node, _ ->
                    val center = field.nodePosition(node)
                    val radius = field.dotRadius(node)
                    val binRadius = batches.binRadius(batches.sizeBin(radius))
                    // A pixel is near the rim when its square, not only its center, comes within a pixel of it.
                    val dot = Dot(center.x, center.y, inner = min(radius, binRadius) - RIM_REACH, outer = max(radius, binRadius) + RIM_REACH)
                    areas.mark(dot)
                    dots += dot
                }
                areas.dots = dots.size
                val overlapping = BooleanArray(dots.size)
                for (first in dots.indices) {
                    for (second in first + 1 until dots.size) {
                        if (!dots[first].overlaps(dots[second])) continue
                        areas.overlappingPairs++
                        overlapping[first] = true
                        overlapping[second] = true
                    }
                }
                areas.overlappingDots = overlapping.count { it }
                return areas
            }
        }
    }

    /** A drawn dot's rim band, between [inner] and [outer] from its center: its own and its size bin's radius, a pixel's reach either side. */
    private class Dot(val x: Float, val y: Float, val inner: Float, val outer: Float) {
        /** Whether the two dots' larger radii, without the rim's reach, overlap. */
        fun overlaps(other: Dot): Boolean {
            val reach = outer + other.outer - 2f * RIM_REACH
            return (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) < reach * reach
        }
    }

    private class Comparison(val elsewhere: Int, val atRims: Int, val inOverlaps: Int, val overlappingPairs: Int, val overlappingDots: Int, val dots: Int) {
        override fun toString(): String {
            val share = if (dots == 0) 0.0 else overlappingDots * 100.0 / dots
            return "largest difference elsewhere $elsewhere, at dot rims $atRims, in overlaps $inOverlaps; " +
                "$overlappingPairs overlapping dot pairs; $overlappingDots of $dots dots overlap another (${"%.1f".format(share)}%)"
        }
    }

    private companion object {
        /** How far drawing edges in level order rather than edge order may move a pixel, in levels out of 255: 8-bit blending rounds at every draw. */
        const val EDGE_ORDER_TOLERANCE = 3

        /** A pixel, plus half a pixel's diagonal: a pixel whose square comes within a pixel of a rim is near it. */
        const val RIM_REACH = 1f + 0.7072f

        /** How far a dot's alpha may round up, in levels out of 255. */
        const val DOT_LEVEL_TOLERANCE = 2
        const val MID_FRACTION = 0.5f

        /** Where each playground morph's comets are on their way, half way, and nearly arrived. */
        val COMET_FRACTIONS = listOf(0.25f, MID_FRACTION, 0.75f)

        /** Two seconds in fifteenths, so fades settle before the layers are drawn. */
        const val SETTLE_STEPS = 30
        const val STEP_SECONDS = 1f / 15f
    }
}

/** The largest difference between two ARGB pixels in any channel, in levels out of 255. */
private fun channelDifference(first: Int, second: Int): Int =
    (0 until 4).maxOf { channel -> abs((first shr (channel * 8) and 0xFF) - (second shr (channel * 8) and 0xFF)) }
