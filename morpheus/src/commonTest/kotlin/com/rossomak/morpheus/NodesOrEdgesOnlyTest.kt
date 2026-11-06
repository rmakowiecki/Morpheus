package com.rossomak.morpheus

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A layer drawn as only its edges, at a node size of 0, or only its nodes, at an edge alpha of 0. */
class NodesOrEdgesOnlyTest {

    private val layer = MorpheusGraphLayerSpec(shapes = listOf(ShapePrimitive.Bloom(x = 0.5f, y = 0.5f, reach = 0.4f, plateau = 0.9f)), density = 30f)

    private fun field(spec: MorpheusGraphLayerSpec) = LayerField(spec, SEED, WIDTH, PX_PER_DP).apply { advance(TIME_SECONDS, WIDTH, HEIGHT) }

    @Test
    fun `at a node size of 0 no dot is drawn, and the edges still are`() {
        val field = field(layer.copy(nodeSizeDp = 0f))
        field.fillDotBatches(PointBatches()) shouldBe 0
        field.fillEdgeBatches(PointBatches(), TIME_SECONDS) shouldBeGreaterThan 0
    }

    @Test
    fun `at an edge alpha of 0 no edge is drawn, and every node keeps its dot and its glow`() {
        val withEdges = field(layer.copy(hotNodeShare = 0.3f))
        val nodesOnly = field(layer.copy(edgeAlpha = 0f, hotNodeShare = 0.3f))
        nodesOnly.fillEdgeBatches(PointBatches(), TIME_SECONDS) shouldBe 0
        nodesOnly.visibleNodeCount shouldBeGreaterThanOrEqual withEdges.visibleNodeCount
        for (node in 0 until nodesOnly.nodeCount) {
            if (nodesOnly.nodeAlpha(node) >= NODE_VISIBLE_ALPHA) nodesOnly.nodeGlowFactor(node) shouldBe 1f
        }
    }

    private companion object {
        const val SEED = 7
        const val PX_PER_DP = 2f
        const val WIDTH = 900f
        const val HEIGHT = 600f
        const val TIME_SECONDS = 2f
    }
}
