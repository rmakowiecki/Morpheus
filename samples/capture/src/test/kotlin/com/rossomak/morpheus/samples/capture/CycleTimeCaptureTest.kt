package com.rossomak.morpheus.samples.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.rememberCycleTime
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

/**
 * A test-only sample paints the cycle-time helper's share of its period as a gray level. It proves the helper
 * follows the virtual clock and wraps at its period, and covers the boomerang and still modes no registered sample uses yet.
 */
class CycleTimeCaptureTest {
    @Test
    fun `the cycle time advances exactly with the virtual clock and wraps at its period`() {
        val recipe = recipe(LoopMode.Crossfade)
        val frames = capture(recipe).frames
        frames shouldHaveSize recipe.frameCount
        // The crossfade blends the head; from there on, each frame is the forward recording's own.
        for (index in recipe.crossfadeFrames until recipe.frameCount) {
            withClue("frame $index") { frames[index].gray shouldBeInRange expectedGray(index) }
        }
    }

    @Test
    fun `a boomerang plays the forward frames, then mirrors them without repeating either end`() {
        val recipe = recipe(LoopMode.Boomerang)
        val frames = capture(recipe).frames
        assertMirrored(frames, recipe.frameCount)
        for (index in 0..recipe.frameCount) {
            withClue("frame $index") { frames[index].gray shouldBeInRange expectedGray(index) }
        }
    }

    @Test
    fun `a still capture is only the frame at the still time`() {
        val recipe = recipe(LoopMode.Still)
        val capture = capture(recipe)
        capture.frames.shouldBeEmpty()
        capture.still.gray shouldBeInRange expectedGray(recipe.stillFrame)
    }

    private fun recipe(loop: LoopMode) = CaptureRecipe(
        canvasSize = DpSize(16.dp, 16.dp),
        duration = 2.seconds,
        fps = FPS,
        stillAt = 750.milliseconds,
        loop = loop,
        crossfade = 500.milliseconds,
        themes = CaptureThemes.DarkOnly,
    )

    private fun capture(recipe: CaptureRecipe): Capture = runBlocking { renderCapture(cycleTimeSample, CaptureTheme.Dark, recipe) }

    private fun expectedGray(index: Int): IntRange {
        val gray = ((index % FRAMES_PER_PERIOD) * 255.0 / FRAMES_PER_PERIOD).roundToInt()
        return gray - 1..gray + 1
    }

    /** The first pixel's red channel; the sample is one flat gray. */
    private val Frame.gray: Int get() = argb[0] shr 16 and 0xFF

    private companion object {
        const val FPS = 20
        val PERIOD = 1.seconds
        val FRAMES_PER_PERIOD = (PERIOD.inWholeMilliseconds * FPS / 1000).toInt()

        val cycleTimeSample = Sample(
            id = "cycle-time",
            title = "Cycle time",
            description = "The cycle-time helper's share of its period, as a gray level.",
            category = SampleCategory.Catalog,
            content = { CycleTimeGray() },
        )
    }
}

@Composable
private fun CycleTimeGray() {
    val elapsed by rememberCycleTime(period = 1.seconds)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val level = (elapsed / 1.seconds).toFloat()
            drawRect(Color(level, level, level))
        },
    )
}
