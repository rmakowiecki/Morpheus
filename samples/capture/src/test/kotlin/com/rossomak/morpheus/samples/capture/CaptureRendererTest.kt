package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * The renderer over the whole registry, so every sample registered later is covered as it lands. Each sample and
 * theme renders once for the whole class, and only a [CaptureDigest] of it is kept: a capture's frames run to a few
 * hundred MB each, which a registry's worth of them wouldn't fit in memory.
 */
class CaptureRendererTest {
    private val samples = SampleRegistry.samples

    @Test
    fun `every sample renders its declared frames at its declared size in each theme`() {
        for (sample in samples) {
            val recipe = sample.recipe
            val width = (recipe.canvasSize.width.value * recipe.density).roundToInt()
            val height = (recipe.canvasSize.height.value * recipe.density).roundToInt()
            for (theme in recipe.themes.themes) {
                withClue("${sample.id} in $theme") {
                    val digest = digestOf(sample, theme)
                    digest.frameCount shouldBe declaredFrameCount(recipe)
                    digest.wrongSizeFrames shouldBe emptyList()
                    digest.uniformFrames shouldBe emptyList()
                    digest.frameSize shouldBe (width to height)
                }
            }
        }
    }

    @Test
    fun `rendering a sample twice gives the same frames`() {
        for (sample in samples.filterNot { it.galleryOnly }) {
            val theme = sample.recipe.themes.themes.first()
            val first = digestOf(sample, theme)
            val second = CaptureDigest(sample, runBlocking { renderCapture(sample, theme) })
            withClue(sample.id) {
                for ((index, frame) in first.head.withIndex()) {
                    withClue("frame $index") { frame.contentEquals(second.head[index]) shouldBe true }
                }
                withClue("still") { first.still.contentEquals(second.still) shouldBe true }
            }
        }
    }

    @Test
    fun `sample ids are unique and kebab-case`() {
        val ids = samples.map { it.id }
        ids.toSet() shouldHaveSize ids.size
        for (id in ids) withClue(id) { KEBAB_CASE.matches(id) shouldBe true }
    }

    @Test
    fun `a crossfade's seam moves no more than a typical frame step`() {
        for (sample in samples.filter { it.recipe.loop == LoopMode.Crossfade }) {
            for (theme in sample.recipe.themes.themes) {
                val digest = digestOf(sample, theme)
                withClue("${sample.id} in $theme, median step ${digest.medianStep}") {
                    digest.seamStep shouldBeLessThanOrEqual SEAM_TOLERANCE * digest.medianStep
                }
            }
        }
    }

    @Test
    fun `a boomerang's second half is its first half reversed`() {
        for (sample in samples.filter { it.recipe.loop == LoopMode.Boomerang }) {
            for (theme in sample.recipe.themes.themes) {
                withClue("${sample.id} in $theme") {
                    val digest = digestOf(sample, theme)
                    digest.frameCount shouldBe 2 * sample.recipe.frameCount
                    digest.unmirroredFrames shouldBe emptyList()
                }
            }
        }
    }

    @Test
    fun `a two-theme sample looks different in light and dark`() {
        for (sample in samples.filter { it.recipe.themes == CaptureThemes.LightAndDark }) {
            withClue(sample.id) {
                digestOf(sample, CaptureTheme.Light).still.contentEquals(digestOf(sample, CaptureTheme.Dark).still) shouldBe false
            }
        }
    }

    private companion object {
        private val digests = HashMap<Pair<String, CaptureTheme>, CaptureDigest>()

        fun digestOf(sample: Sample, theme: CaptureTheme): CaptureDigest =
            digests.getOrPut(sample.id to theme) { CaptureDigest(sample, runBlocking { renderCapture(sample, theme) }) }

        const val DETERMINISM_PREFIX = 10
        const val SEAM_TOLERANCE = 1.5
        val KEBAB_CASE = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }

    /** What the tests ask of one rendered [Capture], taken once so its frames can be dropped. */
    private class CaptureDigest(sample: Sample, capture: Capture) {
        val frameCount = capture.frames.size
        val frameSize: Pair<Int, Int> = (capture.frames + capture.still).first().let { it.width to it.height }
        val wrongSizeFrames = (capture.frames + capture.still).withIndex().filter { (_, frame) -> frame.width to frame.height != frameSize }.map { it.index }
        val uniformFrames = (capture.frames + capture.still).withIndex().filter { (_, frame) -> frame.isUniform }.map { it.index }
        val head: List<Frame> = capture.frames.take(DETERMINISM_PREFIX)
        val still: Frame = capture.still

        /** The mean difference between neighbouring frames, the middle one of them, and across the loop's seam. */
        val medianStep: Double
        val seamStep: Double

        /** For a boomerang, the frames in its second half that aren't the matching ones of its first. */
        val unmirroredFrames: List<Int>

        init {
            val frames = capture.frames
            if (sample.recipe.loop == LoopMode.Crossfade && frames.size > 1) {
                val steps = frames.zipWithNext { a, b -> a.meanChannelDifference(b) }.sorted()
                medianStep = steps[steps.size / 2]
                seamStep = frames.last().meanChannelDifference(frames.first())
            } else {
                medianStep = 0.0
                seamStep = 0.0
            }
            val forward = sample.recipe.frameCount
            unmirroredFrames = if (sample.recipe.loop == LoopMode.Boomerang) {
                (1 until forward).filterNot { offset -> frames[forward + offset].contentEquals(frames[forward - offset]) }.map { forward + it }
            } else {
                emptyList()
            }
        }
    }
}

internal fun declaredFrameCount(recipe: CaptureRecipe): Int = when (recipe.loop) {
    LoopMode.Crossfade -> recipe.frameCount
    LoopMode.Boomerang -> 2 * recipe.frameCount
    LoopMode.Still -> 0
}

/** [frames] is forward frames 0..[forwardFrames], then [forwardFrames] − 1 down to 1. */
internal fun assertMirrored(frames: List<Frame>, forwardFrames: Int) {
    frames shouldHaveSize 2 * forwardFrames
    for (offset in 1 until forwardFrames) {
        withClue("frame ${forwardFrames + offset} against frame ${forwardFrames - offset}") {
            frames[forwardFrames + offset].contentEquals(frames[forwardFrames - offset]) shouldBe true
        }
    }
}
