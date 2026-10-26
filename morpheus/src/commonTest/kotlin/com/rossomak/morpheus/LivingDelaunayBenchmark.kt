@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import kotlin.test.Test

/**
 * The living-Delaunay preset settles its mesh every frame, so its mesh phase is all settle work. Replays it
 * still through [GalleryLoop] on the recipe's canvas and prints each `advance` phase per frame, every layer
 * summed. Printed, not asserted; runs on [benchmark], see `docs/benchmark.md`.
 */
class LivingDelaunayBenchmark {

    @Test
    fun `the living-Delaunay preset's frame phases`() = benchmark("LivingDelaunayBenchmark") {
        val loop = GalleryLoop()
        val layers = GalleryLoop.stillLayers(MorpheusGraphPresets.livingDelaunay)
        repeat(WARM_UP_FRAMES) { loop.frame(layers) }
        val frames = List(MEASURED_FRAMES) { loop.frame(layers) }
        println("living Delaunay, per frame over $MEASURED_FRAMES frames: median ${frames.medianPhases()}; mean ${frames.meanPhases()}")
    }

    private companion object {
        const val WARM_UP_FRAMES = 600
        const val MEASURED_FRAMES = 3_000
    }
}
