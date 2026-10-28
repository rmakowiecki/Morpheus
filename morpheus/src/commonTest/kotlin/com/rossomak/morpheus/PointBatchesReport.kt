package com.rossomak.morpheus

import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Prints, per layer, still and morph frames apart, [PointBatches]' draw calls per pass, its size bin width, and
 * the work spent off canvas or on edges that round to nothing at the layer's alpha. Mean per frame, then the
 * largest. Only runs with `MORPHEUS_BENCHMARK` set:
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*PointBatchesReport' -i`
 */
class PointBatchesReport {

    @Test
    fun `draw calls per pass and invisible work over the playground's loop`() = benchmark(this::class, "playground loop") {
        report(PLAYGROUND_LOOP_FRAMES, ::playgroundMorphAt)
    }

    @Test
    fun `draw calls per pass and invisible work over the showcase's loop`() = benchmark(this::class, "showcase loop") {
        report(SHOWCASE_LOOP_FRAMES, ::showcaseMorphAt)
    }

    private fun report(frames: Int, morphAt: (time: Float) -> MorpheusGraphMorph) {
        val canvas = BenchmarkCanvas.Recipe
        val batches = PointBatches()
        val tallies = LinkedHashMap<String, Tally>()
        replayLoopFields(frames, morphAt = morphAt) { time, morph, layers, fields ->
            val layerCount = (layers.maxOfOrNull { it.slotFromFront } ?: -1) + 1
            val motion = if (morph.fraction > 0f && morph.fraction < 1f) "morph" else "still"
            fields.forEachIndexed { index, field ->
                val layer = layers[index]
                val layerAlpha = layer.fill.toLayerFill(layerCount - 1 - layer.slotFromFront, layerCount, time, Size(canvas.width, canvas.height)).layerAlpha
                val edges = field.fillEdgeBatches(batches, time)
                val dots = field.fillDotBatches(batches)
                tallies.getOrPut("slot ${layer.slotFromFront}, $motion") { Tally() }.add(
                    LayerFrame(
                        edges = edges,
                        dots = dots,
                        edgeCalls = batches.edgeCalls,
                        punchCalls = batches.punchCalls,
                        addCalls = batches.addCalls,
                        sizeBinWidth = batches.sizeBinWidth,
                        invisible = InvisibleWork.of(field, time, layerAlpha, canvas.width, canvas.height),
                    ),
                )
            }
        }
        for ((name, tally) in tallies) println("$name: ${tally.describe()}")
    }

    /** One layer's batches and invisible work in one frame. */
    @Suppress("LongParameterList") // One value per column the report prints.
    private class LayerFrame(
        val edges: Int,
        val dots: Int,
        val edgeCalls: Int,
        val punchCalls: Int,
        val addCalls: Int,
        val sizeBinWidth: Float,
        val invisible: InvisibleWork,
    )

    /** Edges and dots drawn wholly off the canvas, and edges that round to nothing at the layer's alpha. */
    private class InvisibleWork(val offCanvasEdges: Int, val offCanvasDots: Int, val vanishingEdges: Int) {
        companion object {
            /** Bounding boxes against the canvas, so an edge cutting past a corner counts as on it. */
            fun of(field: LayerField, time: Float, layerAlpha: Float, width: Float, height: Float): InvisibleWork {
                val halfStroke = field.edgeStrokeWidth / 2f
                var offCanvasEdges = 0
                var vanishingEdges = 0
                field.forEachVisibleEdge(time) { alpha, startNode, endNode ->
                    val start = field.nodePosition(startNode)
                    val end = field.nodePosition(endNode)
                    val left = min(start.x, end.x) - halfStroke
                    val right = max(start.x, end.x) + halfStroke
                    val top = min(start.y, end.y) - halfStroke
                    val bottom = max(start.y, end.y) + halfStroke
                    if (right < 0f || left > width || bottom < 0f || top > height) offCanvasEdges++
                    // The layer composites its 8-bit level at the layer's alpha into another 8-bit level.
                    if ((PointBatches.edgeLevel(alpha) * layerAlpha).roundToInt() == 0) vanishingEdges++
                }
                var offCanvasDots = 0
                field.forEachVisibleDot { node, _ ->
                    val center = field.nodePosition(node)
                    val radius = field.dotRadius(node)
                    if (center.x + radius < 0f || center.x - radius > width || center.y + radius < 0f || center.y - radius > height) offCanvasDots++
                }
                return InvisibleWork(offCanvasEdges, offCanvasDots, vanishingEdges)
            }
        }
    }

    /** One layer's frames, still or moving. */
    private class Tally {
        private val frames = ArrayList<LayerFrame>()

        fun add(frame: LayerFrame) {
            frames += frame
        }

        fun describe(): String {
            fun stat(selector: (LayerFrame) -> Int): String = "${frames.sumOf(selector) / frames.size}/${frames.maxOf(selector)}"
            val binWidths = frames.map { it.sizeBinWidth }.distinct().joinToString { "${(it * 100).roundToInt() / 100f} px" }
            return "${frames.size} frames; edges ${stat { it.edges }}, dots ${stat { it.dots }}; " +
                "calls: edge batches ${stat { it.edgeCalls }}, punches ${stat { it.punchCalls }}, adds ${stat { it.addCalls }}; size bin width $binWidths; " +
                "off canvas: edges ${stat { it.invisible.offCanvasEdges }}, dots ${stat { it.invisible.offCanvasDots }}; " +
                "edges rounding to nothing at the layer's alpha ${stat { it.invisible.vanishingEdges }}"
        }
    }
}
