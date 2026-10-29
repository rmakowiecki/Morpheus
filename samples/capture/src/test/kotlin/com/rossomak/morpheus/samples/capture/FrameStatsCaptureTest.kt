@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphFrameBreakdown
import com.rossomak.morpheus.MorpheusGraphFrameStats
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import com.rossomak.morpheus.samples.showcase.HeroSample
import com.rossomak.morpheus.samples.showcase.heroPills
import com.rossomak.morpheus.samples.showcase.heroRibbons
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

/**
 * The debug switches and the breakdown, through [renderCapture] with the stats provided as a composition
 * local, as the gallery provides them. Each render's frames all report into one window, published at the end.
 */
class FrameStatsCaptureTest {
    private val hero = SampleRegistry.samples.single { it.id == "hero" }

    @Test
    fun `skipping edges removes them from the frame and their batches from the count`() {
        val normal = measure()
        val skipped = measure { skipEdges = true }
        skipped.frame.contentEquals(normal.frame) shouldBe false
        normal.breakdown.total.drawnEdges shouldBeGreaterThan 0f
        withClue("one batch per alpha level in use, in each layer") {
            normal.dropsBatchesTo(skipped, elements = normal.breakdown.total.drawnEdges, batchesPerLayer = EDGE_BATCHES_PER_LAYER)
        }
        skipped.breakdown.total.drawnEdges shouldBe 0f
        skipped.breakdown.total.edgesMillis shouldBe 0f
    }

    @Test
    fun `skipping halos removes them from the frame and their draw calls from the count`() {
        val normal = measure()
        val skipped = measure { skipHalos = true }
        skipped.frame.contentEquals(normal.frame) shouldBe false
        normal.breakdown.total.drawnHalos shouldBeGreaterThan 0f
        skipped.breakdown.total.drawCalls shouldBe normal.breakdown.total.drawCalls - normal.breakdown.total.drawnHalos
        skipped.breakdown.total.halosMillis shouldBe 0f
    }

    @Test
    fun `skipping dots removes them from the frame and their batches from the count`() {
        val normal = measure()
        val skipped = measure { skipDots = true }
        skipped.frame.contentEquals(normal.frame) shouldBe false
        normal.breakdown.total.drawnDots shouldBeGreaterThan 0f
        withClue("a punch batch per size bin and an add batch per size bin and alpha bucket in use, in each layer") {
            normal.dropsBatchesTo(skipped, elements = DRAW_CALLS_PER_DOT_PER_ELEMENT * normal.breakdown.total.drawnDots, batchesPerLayer = DOT_BATCHES_PER_LAYER)
        }
        skipped.breakdown.total.drawnDots shouldBe 0f
        skipped.breakdown.total.dotsMillis shouldBe 0f
    }

    @Test
    fun `skipping the recolor leaves the gradient layers in their mask color`() {
        val normal = measure()
        val skipped = measure { skipFillRecolor = true }
        skipped.frame.contentEquals(normal.frame) shouldBe false
        // Each masked layer's recolor is a base draw plus one per overlay, so at least one call per layer.
        val dropped = normal.breakdown.total.drawCalls - skipped.breakdown.total.drawCalls
        dropped shouldBeGreaterThanOrEqual normal.breakdown.layers.size.toFloat()
    }

    @Test
    fun `showing violations draws rings, which count as draw calls`() {
        val normal = measure(stillAt = LATE_MORPH)
        val shown = measure(stillAt = LATE_MORPH) { showViolations = true }
        withClue("the frames up to here pass through violations") { shown.breakdown.total.drawCalls shouldBeGreaterThan normal.breakdown.total.drawCalls }
    }

    @Test
    fun `edges and dots draw in far fewer draw calls than elements`() {
        val total = measure().breakdown.total
        // Halos are still drawn one by one, so they are left out of both sides.
        val batched = total.drawCalls - total.drawnHalos
        val perElement = total.drawnEdges + DRAW_CALLS_PER_DOT_PER_ELEMENT * total.drawnDots
        withClue("$batched draw calls for $perElement per element") { batched shouldBeLessThan perElement / 4 }
    }

    @Test
    fun `the breakdown has one column per spec layer, back to front, summed into the total`() {
        val breakdown = measure().breakdown
        breakdown.layers.map { it.slotFromFront } shouldBe listOf(1, 0)
        breakdown.layers.map { it.meshes } shouldBe listOf(1, 1)
        breakdown.backgroundCount shouldBe 1
        breakdown.total.drawCalls shouldBe breakdown.layers.sumOf { it.drawCalls.toDouble() }.toFloat()
        breakdown.total.nodes shouldBeGreaterThanOrEqual breakdown.total.visibleNodes
    }

    @Test
    fun `a cross-family morph keeps one column per spec layer, the dissolving one drawn by two meshes`() {
        // A denser front no longer shares the ribbons' morph family, so the hero's front layer dissolves.
        val densePills = heroPills.copy(layers = heroPills.layers.mapIndexed { index, layer -> if (index == 1) layer.copy(density = 50f) else layer })
        val dissolving = Sample(id = "hero-dissolve", title = hero.title, description = hero.description, category = hero.category, recipe = hero.recipe) {
            HeroSample(pills = densePills, ribbons = heroRibbons)
        }
        val breakdown = measure(dissolving, stillAt = MID_MORPH).breakdown
        withClue("columns") { breakdown.layers.map { it.slotFromFront } shouldBe listOf(1, 0) }
        withClue("meshes") { breakdown.layers.map { it.meshes } shouldBe listOf(1, 2) }
        breakdown.total.morphMeshes shouldBeGreaterThan 0
    }

    private class Measurement(val frame: Frame, val breakdown: MorpheusGraphFrameBreakdown)

    /** Renders [sample]'s frames up to [stillAt] under the given switches, and publishes them as one window. */
    private fun measure(sample: Sample = hero, stillAt: Duration = Duration.ZERO, switches: MorpheusGraphFrameStats.() -> Unit = {}): Measurement {
        val stats = MorpheusGraphFrameStats().apply(switches)
        val recipe = sample.recipe.copy(loop = LoopMode.Still, stillAt = stillAt)
        val capture = runBlocking { renderCapture(sample, sample.recipe.themes.themes.first(), recipe, frameStats = stats) }
        stats.recordFrame(PUBLISH_INTERVAL_NANOS)
        return Measurement(capture.still, stats.breakdown)
    }

    /** Skipping a pass in [skipped] dropped at least one draw call, fewer than its [elements], and at most [batchesPerLayer] per layer. */
    private fun Measurement.dropsBatchesTo(skipped: Measurement, elements: Float, batchesPerLayer: Int) {
        val dropped = breakdown.total.drawCalls - skipped.breakdown.total.drawCalls
        withClue("$dropped draw calls dropped for $elements per element") {
            dropped shouldBeGreaterThan 0f
            dropped shouldBeLessThan elements
            dropped shouldBeLessThanOrEqual (batchesPerLayer * breakdown.layers.size).toFloat()
        }
    }

    private infix fun Int.shouldBeGreaterThan(other: Int) = withClue("$this > $other") { (this > other) shouldBe true }

    private companion object {
        /** One frame of this length publishes everything recorded so far as a window of one frame. */
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L

        /** Half way through the hero's morph, after its first hold; a whole frame at 30 fps. */
        val MID_MORPH = 2500.milliseconds

        /** Late in the morph, where the hero's mesh has nodes short of two visible edges. */
        val LATE_MORPH = 3500.milliseconds

        /** One batch per 8-bit alpha level. Mirrors `PointBatches`. */
        const val EDGE_BATCHES_PER_LAYER = 255

        /** 8 size bins of punches, and 8 of adds in each of 128 alpha buckets. Mirrors `PointBatches`. */
        const val DOT_BATCHES_PER_LAYER = 8 + 8 * 128

        /** A dot drawn per element is a punch and an add. */
        const val DRAW_CALLS_PER_DOT_PER_ELEMENT = 2
    }
}
