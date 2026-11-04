@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.samples.SampleRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The stage, the area the open sample's canvas is scaled into, keeps its bounds while a sample is open: pausing,
 * scrubbing, opening and closing each pane, a reading with every note on against none yet, and every preview
 * preset leave it where it was, with the side column, with list and detail only, and with the detail alone. The
 * detail pane is never narrower than the side column leaves it at its narrowest. Below 840 dp, where panes are sheets,
 * opening one closes the others and Back closes them all. The frame clock is driven by hand, since the
 * gallery records frame intervals on every frame and never idles.
 */
@OptIn(ExperimentalTestApi::class)
class StableStageTest {
    @Test
    fun `a sample with a cycle keeps its stage in the wide layout`() = checkStageStaysStill(WIDE, CYCLE_SAMPLE_ID)

    @Test
    fun `a sample without a cycle keeps its stage in the wide layout`() = checkStageStaysStill(WIDE, STILL_SAMPLE_ID)

    @Test
    fun `a grid sample keeps its stage in the wide layout`() = checkStageStaysStill(WIDE, GRID_SAMPLE_ID)

    @Test
    fun `a sample with a cycle keeps its stage beside the list`() = checkStageStaysStill(MIDDLE, CYCLE_SAMPLE_ID)

    @Test
    fun `a sample without a cycle keeps its stage beside the list`() = checkStageStaysStill(MIDDLE, STILL_SAMPLE_ID)

    @Test
    fun `a grid sample keeps its stage beside the list`() = checkStageStaysStill(MIDDLE, GRID_SAMPLE_ID)

    @Test
    fun `a sample with a cycle keeps its stage in the narrow layout`() = checkStageStaysStill(NARROW, CYCLE_SAMPLE_ID)

    @Test
    fun `a sample without a cycle keeps its stage in the narrow layout`() = checkStageStaysStill(NARROW, STILL_SAMPLE_ID)

    @Test
    fun `a grid sample keeps its stage in the narrow layout`() = checkStageStaysStill(NARROW, GRID_SAMPLE_ID)

    @Test
    fun `sheets open one at a time and Back closes them all`() = runSkikoComposeUiTest(size = NARROW, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        val frameStats = GalleryFrameStats()
        mainClock.autoAdvance = false
        setContent { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null, frameStats = frameStats) }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()

        button("Tune").performClick()
        settle()
        onNodeWithText(TUNE_SHEET_TEXT).assertExists()
        // The button's own click, not a tap that the open sheet would take as a dismissal.
        button("Frame stats").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        onNodeWithText(TUNE_SHEET_TEXT).assertDoesNotExist()
        frameStats.isFrameStatsOpen shouldBe true

        button("Comet debug").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        frameStats.isFrameStatsOpen shouldBe false
        frameStats.isCometDebugExpanded shouldBe true

        button("Back to the samples").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        frameStats.isFrameStatsOpen shouldBe false
        frameStats.isCometDebugExpanded shouldBe false
        onNodeWithText(sample.title).performClick()
        settle()
        onNodeWithText(TUNE_SHEET_TEXT).assertDoesNotExist()
    }

    private fun checkStageStaysStill(windowSize: Size, sampleId: String) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == sampleId }
        val frameStats = GalleryFrameStats()
        mainClock.autoAdvance = false
        setContent { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null, frameStats = frameStats) }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()
        onNodeWithTag(DETAIL_TEST_TAG).fetchSemanticsNode().boundsInRoot.width shouldBeGreaterThanOrEqual DETAIL_MIN_WIDTH.value
        val stage = stageBounds()

        button("Pause").performClick()
        settle()
        stageBounds() shouldBe stage
        if (sample.timing != null) {
            onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
            settle()
            stageBounds() shouldBe stage
        }
        button("Play").performClick()
        settle()
        stageBounds() shouldBe stage

        val panes = buildList {
            if (sample.specs.isNotEmpty() || sample.morphs.isNotEmpty()) add("Tune")
            add("Frame stats")
            if (sample.timing != null) add("Comet debug")
        }
        panes.forEach { pane ->
            button(pane).performClick()
            settle()
            stageBounds() shouldBe stage
            button(pane).performClick()
            settle()
            stageBounds() shouldBe stage
        }

        // No reading yet, then every note on: the reading lives in the pane, so neither moves the stage.
        runOnIdle { frameStats.stats.reset() }
        button("Frame stats").performClick()
        settle()
        stageBounds() shouldBe stage
        runOnIdle {
            frameStats.stats.skipEdges = true
            // Set here, not left to the window size. The stage writes it only when it recomposes, on a new size or preset, and
            // a reading recomposes only the pane.
            frameStats.isCanvasScaled = true
            // Half a second at 60 Hz with nothing drawn: a reading at the refresh interval, so capped.
            repeat(FRAMES_PER_WINDOW) { frameStats.stats.recordFrame(REFRESH_INTERVAL_NANOS) }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText("debug switches on · capped · canvas scaled").assertExists()
        stageBounds() shouldBe stage
        button("Frame stats").performClick()
        settle()

        PreviewPreset.entries.forEach { preset ->
            button(preset.label).performClick()
            settle()
            withClue(preset) { stageBounds() shouldBe stage }
        }
    }

    private fun SkikoComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MILLIS)
        waitForIdle()
    }

    private fun SkikoComposeUiTest.stageBounds(): Rect = onNodeWithTag(STAGE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** A button by its text, or an icon button by what it says to a screen reader. */
    private fun SkikoComposeUiTest.button(label: String) = onNode((hasText(label) or hasContentDescription(label)) and hasClickAction())

    private companion object {
        val WIDE = Size(1400f, 900f)
        val MIDDLE = Size(1000f, 900f)
        val NARROW = Size(700f, 900f)

        const val CYCLE_SAMPLE_ID = "fill-morph"
        const val STILL_SAMPLE_ID = "layers"
        const val GRID_SAMPLE_ID = "seed-grid"

        /** Shown only in the Tune pane. */
        const val TUNE_SHEET_TEXT = "Copy as Kotlin"

        /** Long enough for a sheet to open or close. */
        const val SETTLE_MILLIS = 700L
        const val REFRESH_INTERVAL_NANOS = 16_666_667L
        const val FRAMES_PER_WINDOW = 31
    }
}
