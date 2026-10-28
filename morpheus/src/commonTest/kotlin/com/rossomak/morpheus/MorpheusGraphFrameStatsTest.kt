@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** How [MorpheusGraphFrameStats] windows, averages and keys what the renderer records. */
class MorpheusGraphFrameStatsTest {

    @Test
    fun `a window publishes per-frame averages per spec layer, back to front, and their total`() {
        val stats = MorpheusGraphFrameStats()
        repeat(2) {
            stats.recordBackground(BACKGROUND)
            stats.slotRecord(0).apply {
                addMesh()
                drawCalls += 30
            }
            stats.slotRecord(1).apply {
                addMesh()
                drawCalls += 10
                settles++
            }
            stats.recordFrame(HALF_WINDOW_NANOS)
        }
        val breakdown = stats.breakdown
        breakdown.layers.map { it.slotFromFront } shouldBe listOf(1, 0)
        breakdown.layers.map { it.drawCalls } shouldBe listOf(10f, 30f)
        breakdown.total.drawCalls shouldBe 40f
        breakdown.total.settles shouldBe 2
        breakdown.layers.map { it.meshes } shouldBe listOf(1, 1)
        breakdown.backgroundCount shouldBe 1
        stats.frameMillis shouldBe HALF_WINDOW_NANOS / NANOS_PER_MILLI
    }

    @Test
    fun `two meshes drawing one spec layer count as one column with two meshes`() {
        val stats = MorpheusGraphFrameStats()
        stats.recordBackground(BACKGROUND)
        repeat(2) { stats.slotRecord(0).addMesh() }
        stats.recordFrame(WINDOW_NANOS)
        stats.breakdown.layers.single().meshes shouldBe 2
    }

    @Test
    fun `several backgrounds sum into the same spec layers`() {
        val stats = MorpheusGraphFrameStats()
        listOf(FIRST_BACKGROUND, SECOND_BACKGROUND).forEach { background ->
            stats.recordBackground(background)
            stats.slotRecord(0).apply {
                addMesh()
                drawCalls += 5
            }
        }
        stats.recordFrame(WINDOW_NANOS)
        stats.breakdown.backgroundCount shouldBe 2
        stats.breakdown.layers.single().drawCalls shouldBe 10f
        stats.breakdown.layers.single().meshes shouldBe 1
    }

    @Test
    fun `reset discards the window so far, keeping the last published values`() {
        val stats = MorpheusGraphFrameStats()
        stats.recordDraw(DRAW_NANOS)
        stats.recordFrame(WINDOW_NANOS)
        stats.recordDraw(DRAW_NANOS * 3)
        stats.recordFrame(HALF_WINDOW_NANOS)
        stats.reset()
        stats.drawMillis shouldBe DRAW_NANOS / NANOS_PER_MILLI
        stats.recordDraw(DRAW_NANOS)
        stats.recordFrame(WINDOW_NANOS)
        stats.drawMillis shouldBe DRAW_NANOS / NANOS_PER_MILLI
    }

    @Test
    fun `flipping a switch starts a fresh window`() {
        val stats = MorpheusGraphFrameStats()
        stats.recordDraw(DRAW_NANOS * 3)
        stats.recordFrame(HALF_WINDOW_NANOS)
        stats.skipEdges = true
        stats.recordDraw(DRAW_NANOS)
        stats.recordFrame(WINDOW_NANOS)
        stats.drawMillis shouldBe DRAW_NANOS / NANOS_PER_MILLI
    }

    @Test
    fun `changing the drawing approach starts a fresh window, but isn't a debug switch`() {
        val stats = MorpheusGraphFrameStats()
        stats.recordDraw(DRAW_NANOS * 3)
        stats.recordFrame(HALF_WINDOW_NANOS)
        stats.drawingApproach = MorpheusGraphDrawingApproach.PointBatches
        stats.anySwitchOn shouldBe false
        stats.recordDraw(DRAW_NANOS)
        stats.recordFrame(WINDOW_NANOS)
        stats.drawMillis shouldBe DRAW_NANOS / NANOS_PER_MILLI
    }

    @Test
    fun `the readings are stale from a reset until the next window is published`() {
        val stats = MorpheusGraphFrameStats()
        stats.isStale shouldBe false
        stats.reset()
        stats.isStale shouldBe true
        stats.recordFrame(HALF_WINDOW_NANOS)
        stats.isStale shouldBe true
        stats.recordFrame(HALF_WINDOW_NANOS)
        stats.isStale shouldBe false
    }

    @Test
    fun `a switch is on until flipped back, and any one counts`() {
        val stats = MorpheusGraphFrameStats()
        stats.anySwitchOn shouldBe false
        stats.skipHalos = true
        stats.anySwitchOn shouldBe true
        stats.skipHalos = false
        stats.showViolations = true
        stats.anySwitchOn shouldBe true
    }

    @Test
    fun `no frames publish nothing`() {
        val stats = MorpheusGraphFrameStats()
        stats.recordDraw(DRAW_NANOS)
        stats.slotRecord(0).addMesh()
        stats.drawMillis shouldBe 0f
        stats.breakdown.layers shouldBe emptyList()
    }

    private companion object {
        const val WINDOW_NANOS = 500_000_000L
        const val HALF_WINDOW_NANOS = 250_000_000L
        const val DRAW_NANOS = 2_000_000L
        const val NANOS_PER_MILLI = 1_000_000f
        const val BACKGROUND = 0
        const val FIRST_BACKGROUND = 1
        const val SECOND_BACKGROUND = 2
    }
}
