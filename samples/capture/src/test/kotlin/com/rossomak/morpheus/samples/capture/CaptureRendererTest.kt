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
 * The renderer over the whole registry, so every sample a later change registers is covered as it lands. Driven
 * only through [renderCapture]'s input and output.
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
                    val capture = captureOf(sample, theme)
                    capture.frames shouldHaveSize declaredFrameCount(recipe)
                    for ((index, frame) in (capture.frames + capture.still).withIndex()) {
                        withClue("frame $index") {
                            frame.width shouldBe width
                            frame.height shouldBe height
                            frame.isUniform shouldBe false
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `rendering a sample twice gives the same frames`() {
        for (sample in samples.filterNot { it.galleryOnly }) {
            val theme = sample.recipe.themes.themes.first()
            val first = captureOf(sample, theme)
            val second = runBlocking { renderCapture(sample, theme) }
            withClue(sample.id) {
                for (index in 0 until minOf(DETERMINISM_PREFIX, first.frames.size)) {
                    withClue("frame $index") { first.frames[index].contentEquals(second.frames[index]) shouldBe true }
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
                val frames = captureOf(sample, theme).frames
                val steps = frames.zipWithNext { a, b -> a.meanChannelDifference(b) }.sorted()
                val medianStep = steps[steps.size / 2]
                withClue("${sample.id} in $theme, median step $medianStep") {
                    frames.last().meanChannelDifference(frames.first()) shouldBeLessThanOrEqual SEAM_TOLERANCE * medianStep
                }
            }
        }
    }

    @Test
    fun `a boomerang's second half is its first half reversed`() {
        for (sample in samples.filter { it.recipe.loop == LoopMode.Boomerang }) {
            for (theme in sample.recipe.themes.themes) {
                withClue("${sample.id} in $theme") { assertMirrored(captureOf(sample, theme).frames, sample.recipe.frameCount) }
            }
        }
    }

    @Test
    fun `a two-theme sample looks different in light and dark`() {
        for (sample in samples.filter { it.recipe.themes == CaptureThemes.LightAndDark }) {
            withClue(sample.id) {
                captureOf(sample, CaptureTheme.Light).still.contentEquals(captureOf(sample, CaptureTheme.Dark).still) shouldBe false
            }
        }
    }

    private companion object {
        /** Each sample and theme renders once for every test in the class; only the determinism test renders again. */
        private val captures = HashMap<Pair<String, CaptureTheme>, Capture>()

        fun captureOf(sample: Sample, theme: CaptureTheme): Capture = captures.getOrPut(sample.id to theme) { runBlocking { renderCapture(sample, theme) } }

        const val DETERMINISM_PREFIX = 10
        const val SEAM_TOLERANCE = 1.5
        val KEBAB_CASE = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}

/** How many frames a capture of [recipe] has: the loop, twice over less both ends for a boomerang, or none for a still. */
internal fun declaredFrameCount(recipe: CaptureRecipe): Int = when (recipe.loop) {
    LoopMode.Crossfade -> recipe.frameCount
    LoopMode.Boomerang -> 2 * recipe.frameCount
    LoopMode.Still -> 0
}

/** [frames] is forward frames 0..[forwardFrames], then frames [forwardFrames] − 1 down to 1. */
internal fun assertMirrored(frames: List<Frame>, forwardFrames: Int) {
    frames shouldHaveSize 2 * forwardFrames
    for (offset in 1 until forwardFrames) {
        withClue("frame ${forwardFrames + offset} against frame ${forwardFrames - offset}") {
            frames[forwardFrames + offset].contentEquals(frames[forwardFrames - offset]) shouldBe true
        }
    }
}
