@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

/**
 * The stage, the area the open sample's canvas is scaled into, keeps its bounds through pausing, scrubbing, opening
 * and closing each pane, any reading, and every preview preset, in all three layouts. The detail is never narrower
 * than [DETAIL_MIN_WIDTH]. Where panes are sheets, opening one closes the others, and Back closes them all. The
 * stage still follows the window, and the canvas the preset. The frame clock is driven by hand, since the gallery
 * records frame intervals on every frame and never idles.
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
    fun `sheets open one at a time beside the list`() = checkSheetsOpenOneAtATime(MIDDLE, hasBack = false)

    @Test
    fun `sheets open one at a time and Back closes them all`() = checkSheetsOpenOneAtATime(NARROW, hasBack = true)

    @Test
    fun `stage follows the window and the canvas follows the preset`() = runSkikoComposeUiTest(size = WIDE, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        // The gallery's own space, shrunk below to stand for a resized window.
        var host by mutableStateOf(DpSize(WIDE.width.dp, WIDE.height.dp))
        mainClock.autoAdvance = false
        setContent { Box(modifier = Modifier.size(host)) { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null) } }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()

        val wideStage = stageBounds()
        host = DpSize(MIDDLE.width.dp, MIDDLE.height.dp)
        settle()
        stageBounds().size shouldNotBe wideStage.size

        // At its own size, so the canvas shows each preset's shape, not the stage's.
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        button(PreviewPreset.Wide.label).performClick()
        settle()
        val wideCanvas = canvasBounds()
        button(PreviewPreset.Square.label).performClick()
        settle()
        canvasBounds().size shouldNotBe wideCanvas.size
        canvasBounds().width shouldBe canvasBounds().height
    }

    private fun checkSheetsOpenOneAtATime(windowSize: Size, hasBack: Boolean) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        val frameStats = GalleryFrameStats()
        openSample(sample, frameStats)

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
        frameStats.isCometDebugOpen shouldBe true

        if (hasBack) {
            button("Back to the samples").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            frameStats.isFrameStatsOpen shouldBe false
            frameStats.isCometDebugOpen shouldBe false
            onNodeWithText(sample.title).performClick()
            settle()
            onNodeWithText(TUNE_SHEET_TEXT).assertDoesNotExist()
        }
    }

    private fun checkStageStaysStill(windowSize: Size, sampleId: String) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == sampleId }
        val frameStats = GalleryFrameStats()
        openSample(sample, frameStats)
        onNodeWithTag(DETAIL_TEST_TAG).fetchSemanticsNode().boundsInRoot.width shouldBeGreaterThanOrEqual DETAIL_MIN_WIDTH.value
        val stage = stageBounds()

        checkPlayback(sample, stage)
        GalleryPane.entries.filter { it.isAvailableFor(sample) }.forEach { pane -> checkPaneToggle(pane, stage) }
        checkReadings(frameStats, stage)
        checkPresets(stage)
    }

    private fun SkikoComposeUiTest.openSample(sample: Sample, frameStats: GalleryFrameStats) {
        mainClock.autoAdvance = false
        setContent { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null, frameStats = frameStats) }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()
    }

    private fun SkikoComposeUiTest.checkPlayback(sample: Sample, stage: Rect) {
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
    }

    private fun SkikoComposeUiTest.checkPaneToggle(pane: GalleryPane, stage: Rect) {
        repeat(2) {
            button(pane.title).performClick()
            settle()
            stageBounds() shouldBe stage
        }
    }

    /** No reading yet, then every note on: the reading lives in the pane, so neither moves the stage. */
    private fun SkikoComposeUiTest.checkReadings(frameStats: GalleryFrameStats, stage: Rect) {
        runOnIdle { frameStats.stats.reset() }
        button(GalleryPane.FrameStats.title).performClick()
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
        button(GalleryPane.FrameStats.title).performClick()
        settle()
    }

    private fun SkikoComposeUiTest.checkPresets(stage: Rect) {
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

    private fun SkikoComposeUiTest.canvasBounds(): Rect = onNodeWithTag(CANVAS_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

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
