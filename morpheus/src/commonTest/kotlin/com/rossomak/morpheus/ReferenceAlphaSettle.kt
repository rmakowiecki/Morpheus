package com.rossomak.morpheus

import androidx.compose.ui.util.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * [LayerField]'s alpha settle as it was before it recomputed only each round's frontier: every round tallies,
 * lowers and limits every node and every drawn edge. The reference the field's settle must match bit for bit
 * ([AlphaSettleTest]) and is timed against ([AlphaSettleBenchmark]). Its formulas are copies, not shared with the
 * field, so a formula changed in one fails the test instead of changing both.
 *
 * [load] copies a field's inputs as its last `advance` left them, so [settle] runs on the same positions, presences,
 * hot weights and edge fades the field settled. With [withConstants], it is the first step towards the field's
 * settle: each node's base alpha and full-at level computed once per frame instead of once per round, and each
 * node's depth alpha kept in an array, but every round still covering every node and edge.
 */
internal class ReferenceAlphaSettle(private val withConstants: Boolean = false) {
    private var spec = MorpheusGraphLayerSpec()
    private var cell = 0f
    var count = 0
        private set
    var edgeCount = 0
        private set

    private var depths = FloatArray(0)
    private var positionsX = FloatArray(0)
    private var positionsY = FloatArray(0)
    private var presences = FloatArray(0)
    private var hotWeights = FloatArray(0)
    private var edgeKeys = LongArray(0)
    private var edgeFades = FloatArray(0)

    private var rawEdgeAlphas = FloatArray(0)
    var edgeAlphas = FloatArray(0)
        private set
    var brightestEdgeAlphas = FloatArray(0)
        private set
    var secondEdgeAlphas = FloatArray(0)
        private set
    var visibleEdgeCounts = IntArray(0)
        private set
    var nodeAlphas = FloatArray(0)
        private set
    var glowFactors = FloatArray(0)
        private set
    var visibleNodeCount = 0
        private set

    /** The last [settle]'s rounds, and whether it stopped at [MAX_ALPHA_ROUNDS] before settling. */
    var rounds = 0
        private set
    var roundCapHit = false
        private set

    /** Edges the last [settle]'s first limit capped although neither of their dots changed in the first round. */
    var cappedWithStillDots = 0
        private set

    private val triangleSearch = TriangleSearch()
    private var triangleCache = IntArray(0)
    private var triangleCacheKeys = LongArray(0)

    // withConstants only.
    private var depthAlphas = FloatArray(0)
    private var depthAlphasDimming = Float.NaN
    private var dotBaseAlphas = FloatArray(0)
    private var dotFullAts = FloatArray(0)

    /** Copies [field]'s inputs to the settle as its last `advance` left them. */
    fun load(field: LayerField) {
        spec = field.spec
        cell = field.cellSize
        if (field.nodeCount != count) {
            count = field.nodeCount
            depths = FloatArray(count) { field.nodeDepth(it) }
            positionsX = FloatArray(count)
            positionsY = FloatArray(count)
            presences = FloatArray(count)
            hotWeights = FloatArray(count)
            brightestEdgeAlphas = FloatArray(count)
            secondEdgeAlphas = FloatArray(count)
            visibleEdgeCounts = IntArray(count)
            nodeAlphas = FloatArray(count)
            glowFactors = FloatArray(count)
            dotBaseAlphas = FloatArray(count)
            dotFullAts = FloatArray(count)
        }
        for (index in 0 until count) {
            val position = field.nodePosition(index)
            positionsX[index] = position.x
            positionsY[index] = position.y
            presences[index] = field.nodePresence(index)
            hotWeights[index] = field.nodeHotWeight(index)
        }
        edgeCount = field.drawnEdgeCount
        if (edgeKeys.size < edgeCount) {
            edgeKeys = LongArray(edgeCount)
            edgeFades = FloatArray(edgeCount)
            rawEdgeAlphas = FloatArray(edgeCount)
            edgeAlphas = FloatArray(edgeCount)
        }
        for (edge in 0 until edgeCount) {
            edgeKeys[edge] = field.drawnEdgeKey(edge)
            edgeFades[edge] = field.drawnEdgeFade(edge)
        }
    }

    /** [load]s [field] and [settle]s it, for a comparison with what the field settled. */
    fun settle(field: LayerField) {
        load(field)
        settle()
    }

    fun settle() {
        if (withConstants) updateDepthAlphas()
        updateRawEdgeAlphas()
        if (withConstants) updateDotConstants()
        for (index in 0 until count) nodeAlphas[index] = dotBaseAlpha(index)
        var settled = false
        rounds = 0
        cappedWithStillDots = 0
        while (!settled && rounds < MAX_ALPHA_ROUNDS) {
            tallyIncidentEdges()
            settled = !lowerDotsToTheirEdges()
            limitEdgesByDots()
            if (rounds == 0) countCapsWithStillDots()
            rounds++
        }
        roundCapHit = !settled
        do {
            tallyIncidentEdges()
            val hidAny = hideUnderconnectedDots()
            if (hidAny) limitEdgesByDots()
        } while (hidAny)
        visibleNodeCount = (0 until count).count { nodeAlphas[it] >= NODE_VISIBLE_ALPHA }
    }

    private fun countCapsWithStillDots() {
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val dotsStill = nodeAlphas[edgeStart(key)] == dotBaseAlpha(edgeStart(key)) && nodeAlphas[edgeEnd(key)] == dotBaseAlpha(edgeEnd(key))
            if (dotsStill && edgeAlphas[edge] != rawEdgeAlphas[edge]) cappedWithStillDots++
        }
    }

    private fun updateDepthAlphas() {
        if (depthAlphas.size == count && depthAlphasDimming == spec.depthDimming) return
        depthAlphas = FloatArray(count) { 1f - spec.depthDimming * (1f - depths[it]) }
        depthAlphasDimming = spec.depthDimming
    }

    private fun updateDotConstants() {
        val nominalEdgeAlpha = spec.edgeAlpha * spec.alpha
        for (index in 0 until count) {
            dotBaseAlphas[index] = computeDotBaseAlpha(index)
            dotFullAts[index] = computeFullAt(index, nominalEdgeAlpha)
        }
    }

    private fun updateRawEdgeAlphas() {
        val referenceLength = spec.maxEdgeFactor * cell
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val start = edgeStart(key)
            val end = edgeEnd(key)
            val deltaX = positionsX[end] - positionsX[start]
            val deltaY = positionsY[end] - positionsY[start]
            val length = sqrt(deltaX * deltaX + deltaY * deltaY)
            val lengthFactor = 1f - spec.lengthFalloff * (length / referenceLength).coerceIn(0f, 1f)
            val depthFactor = min(depthAlpha(start), depthAlpha(end))
            rawEdgeAlphas[edge] = (spec.edgeAlpha * spec.alpha * lengthFactor * depthFactor).coerceIn(0f, 1f)
        }
        if (spec.strictTriangles) fillDrawnTriangles()
        for (edge in 0 until edgeCount) {
            val alpha = rawEdgeAlphas[edge] * edgeFades[edge]
            rawEdgeAlphas[edge] = alpha
            edgeAlphas[edge] = alpha
        }
    }

    private fun fillDrawnTriangles() {
        if (!triangleCacheKeys.contentEquals(edgeKeys.copyOf(edgeCount))) {
            triangleCacheKeys = edgeKeys.copyOf(edgeCount)
            triangleCache = triangleSearch.edgeTriangles(count, edgeKeys, edgeCount)
        }
        fillTriangles(triangleCache, rawEdgeAlphas)
    }

    private fun dotBaseAlpha(index: Int): Float = if (withConstants) dotBaseAlphas[index] else computeDotBaseAlpha(index)

    private fun computeDotBaseAlpha(index: Int): Float {
        val baseAlpha = lerp(max(NODE_ALPHA, spec.edgeAlpha), 1f, hotWeights[index])
        return (baseAlpha * spec.alpha * depthAlpha(index) * presences[index]).coerceIn(0f, 1f)
    }

    private fun computeFullAt(index: Int, nominalEdgeAlpha: Float): Float =
        max(NODE_FULL_AT_EDGE_SHARE * nominalEdgeAlpha * depthAlpha(index) * presences[index], 2f * EDGE_VISIBLE_ALPHA)

    private fun tallyIncidentEdges() {
        brightestEdgeAlphas.fill(0f)
        secondEdgeAlphas.fill(0f)
        visibleEdgeCounts.fill(0)
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            recordIncidentEdge(edgeStart(key), edgeAlphas[edge])
            recordIncidentEdge(edgeEnd(key), edgeAlphas[edge])
        }
    }

    private fun recordIncidentEdge(node: Int, alpha: Float) {
        if (alpha >= EDGE_VISIBLE_ALPHA) visibleEdgeCounts[node]++
        if (alpha > brightestEdgeAlphas[node]) {
            secondEdgeAlphas[node] = brightestEdgeAlphas[node]
            brightestEdgeAlphas[node] = alpha
        } else if (alpha > secondEdgeAlphas[node]) {
            secondEdgeAlphas[node] = alpha
        }
    }

    private fun lowerDotsToTheirEdges(): Boolean {
        val nominalEdgeAlpha = spec.edgeAlpha * spec.alpha
        var lowered = false
        for (index in 0 until count) {
            val second = secondEdgeAlphas[index]
            val fullAt = if (withConstants) dotFullAts[index] else computeFullAt(index, nominalEdgeAlpha)
            val factor = if (second < EDGE_VISIBLE_ALPHA) 0f else smoothStep(EDGE_VISIBLE_ALPHA, fullAt, second)
            val alpha = min(nodeAlphas[index], dotBaseAlpha(index) * factor)
            if (nodeAlphas[index] - alpha > ALPHA_SETTLE_EPSILON) lowered = true
            nodeAlphas[index] = alpha
            glowFactors[index] = factor
        }
        return lowered
    }

    private fun limitEdgesByDots() {
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            edgeAlphas[edge] = min(rawEdgeAlphas[edge], min(nodeAlphas[edgeStart(key)], nodeAlphas[edgeEnd(key)]))
        }
    }

    private fun hideUnderconnectedDots(): Boolean {
        var hidAny = false
        for (index in 0 until count) {
            if (nodeAlphas[index] <= 0f || secondEdgeAlphas[index] >= EDGE_VISIBLE_ALPHA) continue
            nodeAlphas[index] = 0f
            glowFactors[index] = 0f
            hidAny = true
        }
        return hidAny
    }

    private fun depthAlpha(index: Int): Float = if (withConstants) depthAlphas[index] else 1f - spec.depthDimming * (1f - depths[index])
}
