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
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphCometDebug
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.roundToInt
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
    fun `a squarish window wide enough for the side column still opens sheets beside the list`() = checkSheetsOpenOneAtATime(SQUARISH, hasBack = false)

    @Test
    fun `a window taller than it is wide opens the detail over the list, however wide`() = checkSheetsOpenOneAtATime(PORTRAIT, hasBack = true)

    @Test
    fun `leaving a sample by the browser's Back closes its panes too`() = runSkikoComposeUiTest(size = NARROW, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        val frameStats = GalleryFrameStats()
        val navigation = InMemoryNavigation(initialSampleId = null)
        mainClock.autoAdvance = false
        setContent { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null, frameStats = frameStats, navigation = navigation) }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()
        button(GalleryPane.PerformanceStats.title).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        frameStats.isPerformanceStatsOpen shouldBe true

        // What the browser's Back does: straight to the landing page, past the in-app Back.
        runOnIdle { navigation.open(null) }
        settle()
        frameStats.isPerformanceStatsOpen shouldBe false
        onNodeWithText(sample.title).performClick()
        settle()
        frameStats.isPerformanceStatsOpen shouldBe false
    }

    @Test
    fun `a phone shows the stage at the full width and nothing past the window's sides`() = checkFitsPhone(PHONE)

    @Test
    fun `the narrowest phone shows nothing past the window's sides`() = checkFitsPhone(NARROWEST_PHONE)

    @Test
    fun `a sample with a cycle keeps its stage on a phone`() = checkStageStaysStillOnPhone(PHONE, CYCLE_SAMPLE_ID)

    @Test
    fun `a sample without a cycle keeps its stage on a phone`() = checkStageStaysStillOnPhone(PHONE, STILL_SAMPLE_ID)

    @Test
    fun `a sample keeps its stage on a phone held sideways`() = checkStageStaysStillOnPhone(LANDSCAPE_PHONE, CYCLE_SAMPLE_ID)

    @Test
    fun `the benchmark's window shows the whole detail without scrolling`() = runSkikoComposeUiTest(size = BENCHMARK_PAGE, density = Density(1f)) {
        mainClock.autoAdvance = false
        setContent { Gallery(samples = SampleRegistry.samples, onSpecExport = {}, benchmark = GalleryBenchmark()) }
        settle()
        onNodeWithTag(STAGE_TEST_TAG, useUnmergedTree = true).assertExists()
        detailScrollRange().maxValue() shouldBe 0f
    }

    @Test
    fun `a tall window gives the stage at least half the detail's height`() = runSkikoComposeUiTest(size = TALL, density = Density(1f)) {
        openSample(SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }, GalleryFrameStats())
        val detailHeight = onNodeWithTag(DETAIL_TEST_TAG).fetchSemanticsNode().size.height.toFloat()
        onNodeWithTag(STAGE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().size.height.toFloat() shouldBeGreaterThanOrEqual detailHeight / 2
    }

    @Test
    fun `a phone shows the sample's own canvas scaled to fit and keeps the preview setting for later`() = runSkikoComposeUiTest(size = WIDE, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        var host by mutableStateOf(DpSize(WIDE.width.dp, WIDE.height.dp))
        mainClock.autoAdvance = false
        setContent { Box(modifier = Modifier.size(host)) { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null) } }
        settle()
        onNodeWithText(sample.title).performClick()
        settle()
        scaleToFitSwitch().performClick()
        button(PreviewPreset.Fill.label).performClick()
        settle()

        host = DpSize(PHONE.width.dp, PHONE.height.dp)
        settle()
        onAllNodesWithText(PreviewPreset.Fill.label).fetchSemanticsNodes().isEmpty() shouldBe true
        val canvas = canvasBounds()
        canvas.width shouldBe PHONE.width
        canvas.height shouldBe (PHONE.width * (sample.recipe.canvasSize.height / sample.recipe.canvasSize.width)).roundToInt().toFloat()

        host = DpSize(WIDE.width.dp, WIDE.height.dp)
        settle()
        button(PreviewPreset.Fill.label).assertIsSelected()
        scaleToFitSwitch().assertIsOff()
    }

    @Test
    fun `a phone's menu picks the theme and opens the panes, and says which are on`() = runSkikoComposeUiTest(size = PHONE, density = Density(1f)) {
        val frameStats = GalleryFrameStats()
        openSample(SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }, frameStats)
        menuItem(ThemeMode.Dark.label).performClick()
        settle()
        menuItem(ThemeMode.Dark.label).assertIsSelected()
        onNode(hasText(ThemeMode.Light.label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsNotSelected()
        button(ThemeMode.Dark.label).performClick()
        settle()

        menuItem(GalleryPane.PerformanceStats.title).performClick()
        settle()
        frameStats.isPerformanceStatsOpen shouldBe true
        // A sheet now covers the menu button, so its own click.
        button(MORE_OPTIONS).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        button(GalleryPane.PerformanceStats.title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Open"))
        button(GalleryPane.CometDebug.title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
        runOnIdle { frameStats.stats.cometDebug = MorpheusGraphCometDebug(heads = true) }
        settle()
        button(GalleryPane.CometDebug.title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed, overlays on"))
        onNodeWithText(OVERLAYS_ON).assertExists()
    }

    @Test
    fun `a phone's menu shows a dark-only sample's theme as dark and fixed`() = runSkikoComposeUiTest(size = PHONE, density = Density(1f)) {
        openSample(SampleRegistry.samples.first { it.recipe.themes == CaptureThemes.DarkOnly }, GalleryFrameStats())
        menuItem(ThemeMode.Dark.label).assertIsSelected().assertIsNotEnabled()
        onNode(hasText(ThemeMode.Light.label)).assertIsNotSelected().assertIsNotEnabled()
    }

    @Test
    fun `each sample opens its detail at the top`() = runSkikoComposeUiTest(size = SHORT, density = Density(1f)) {
        val samples = SampleRegistry.samples.filter { it.id == CYCLE_SAMPLE_ID || it.id == STILL_SAMPLE_ID }
        mainClock.autoAdvance = false
        setContent { Gallery(samples = samples, onSpecExport = {}, benchmark = null) }
        settle()
        onNodeWithText(samples[0].title).performClick()
        settle()
        onNodeWithTag(DETAIL_SCROLL_TEST_TAG).performTouchInput { swipeUp() }
        settle()
        detailScrollRange().value() shouldBeGreaterThan 0f
        onNodeWithText(samples[1].title).performClick()
        settle()
        detailScrollRange().value() shouldBe 0f
    }

    @Test
    fun `a short window scrolls the detail instead of squeezing the stage`() = runSkikoComposeUiTest(size = SHORT, density = Density(1f)) {
        openSample(SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }, GalleryFrameStats())
        // Its size, not its bounds: those end where the detail's viewport does.
        onNodeWithTag(STAGE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode().size.height.toFloat() shouldBeGreaterThanOrEqual MIN_STAGE_HEIGHT_DP
        detailScrollRange().maxValue() shouldBeGreaterThan 0f
    }

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
        scaleToFitSwitch().performClick()
        button(PreviewPreset.Wide.label).performClick()
        settle()
        val wideCanvas = canvasBounds()
        button(PreviewPreset.Square.label).performClick()
        settle()
        canvasBounds().size shouldNotBe wideCanvas.size
        canvasBounds().width shouldBe canvasBounds().height
    }

    /** The stage at the full width under the top bar, and no text or button past the window's left or right. */
    private fun checkFitsPhone(windowSize: Size) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        openSample(SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }, GalleryFrameStats())
        val stage = stageBounds()
        stage.width shouldBe windowSize.width
        stage.top shouldBeLessThan windowSize.height
        val shown = onAllNodes(hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        shown.forEach { node ->
            withClue(node.config) {
                node.boundsInRoot.left shouldBeGreaterThanOrEqual 0f
                node.boundsInRoot.right shouldBeLessThanOrEqual windowSize.width
            }
        }
    }

    private fun checkSheetsOpenOneAtATime(windowSize: Size, hasBack: Boolean) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == CYCLE_SAMPLE_ID }
        val frameStats = GalleryFrameStats()
        openSample(sample, frameStats)

        button(GalleryPane.Tune.title).performClick()
        settle()
        onNodeWithText(TUNE_SHEET_TEXT).assertExists()
        // The button's own click, not a tap that the open sheet would take as a dismissal.
        button(GalleryPane.PerformanceStats.title).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        onNodeWithText(TUNE_SHEET_TEXT).assertDoesNotExist()
        frameStats.isPerformanceStatsOpen shouldBe true
        button(GalleryPane.PerformanceStats.title).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Open"))

        button(GalleryPane.CometDebug.title).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        frameStats.isPerformanceStatsOpen shouldBe false
        frameStats.isCometDebugOpen shouldBe true

        if (hasBack) {
            button("Back to the samples").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            frameStats.isPerformanceStatsOpen shouldBe false
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

        checkPlayback(sample, stage) { stageBounds() }
        GalleryPane.entries.filter { it.isAvailableFor(sample) }.forEach { pane -> checkPaneToggle(pane, stage) }
        checkReadings(frameStats, stage)
        checkPresets(stage)
    }

    /**
     * Playback, then each pane from the Tune button or the menu: none resizes the stage of a compact detail. Its size,
     * not its bounds: a tap on a control at the fold scrolls it into view.
     */
    private fun checkStageStaysStillOnPhone(windowSize: Size, sampleId: String) = runSkikoComposeUiTest(size = windowSize, density = Density(1f)) {
        val sample = SampleRegistry.samples.single { it.id == sampleId }
        val frameStats = GalleryFrameStats()
        openSample(sample, frameStats)
        val stage = stageBounds()
        checkPlayback(sample, stage.size) { stageBounds().size }
        GalleryPane.entries.filter { it.isAvailableFor(sample) }.forEach { pane ->
            repeat(2) {
                if (pane == GalleryPane.Tune) {
                    // The button's own click: a tap would land on the open sheet, which takes it as a dismissal.
                    button(pane.title).performSemanticsAction(SemanticsActions.OnClick)
                } else {
                    button(MORE_OPTIONS).performSemanticsAction(SemanticsActions.OnClick)
                    settle()
                    button(pane.title).performClick()
                }
                settle()
                withClue(pane) { stageBounds().size shouldBe stage.size }
            }
        }
        frameStats.isPerformanceStatsOpen shouldBe false
    }

    private fun SkikoComposeUiTest.openSample(sample: Sample, frameStats: GalleryFrameStats) {
        mainClock.autoAdvance = false
        setContent { Gallery(samples = listOf(sample), onSpecExport = {}, benchmark = null, frameStats = frameStats) }
        settle()
        // A short window shows the landing page's graph over the list, so the sample's row may be below the fold. A swipe,
        // since scrolling to the row waits on a frame clock that only settle advances.
        if (onAllNodesWithText(sample.title).fetchSemanticsNodes().isEmpty()) {
            onNode(hasScrollAction()).performTouchInput { swipeUp() }
            settle()
        }
        onNodeWithText(sample.title).performClick()
        settle()
    }

    private fun <T> SkikoComposeUiTest.checkPlayback(sample: Sample, stage: T, stageNow: SkikoComposeUiTest.() -> T) {
        button("Pause").performClick()
        settle()
        stageNow() shouldBe stage
        if (sample.timing != null) {
            onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
            settle()
            stageNow() shouldBe stage
        }
        button("Play").performClick()
        settle()
        stageNow() shouldBe stage
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
        button(GalleryPane.PerformanceStats.title).performClick()
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
        button(GalleryPane.PerformanceStats.title).performClick()
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

    private fun SkikoComposeUiTest.detailScrollRange(): ScrollAxisRange =
        onNodeWithTag(DETAIL_SCROLL_TEST_TAG).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun SkikoComposeUiTest.scaleToFitSwitch() = onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    /** Opens the compact detail's menu, then finds the item with [label]. */
    private fun SkikoComposeUiTest.menuItem(label: String): SemanticsNodeInteraction {
        button(MORE_OPTIONS).performClick()
        settle()
        return button(label)
    }

    /** A button by its text, or an icon button by what it says to a screen reader. */
    private fun SkikoComposeUiTest.button(label: String) = onNode((hasText(label) or hasContentDescription(label)) and hasClickAction())

    private companion object {
        val WIDE = Size(1400f, 900f)
        val MIDDLE = Size(1000f, 900f)
        val NARROW = Size(700f, 900f)
        val SQUARISH = Size(1300f, 1000f)
        val PORTRAIT = Size(1000f, 1200f)
        val PHONE = Size(360f, 800f)
        val LANDSCAPE_PHONE = Size(800f, 360f)
        val NARROWEST_PHONE = Size(320f, 640f)
        val TALL = Size(1400f, 1400f)

        /** What the benchmark's 1440 x 900 Chrome window leaves the page under its tabs and toolbar, give or take. */
        val BENCHMARK_PAGE = Size(1440f, 780f)

        /** Wide, but short enough that the controls leave the stage less than its least height. */
        val SHORT = Size(1000f, 500f)
        const val MIN_STAGE_HEIGHT_DP = 320f

        const val CYCLE_SAMPLE_ID = "fill-morph"
        const val STILL_SAMPLE_ID = "layers"
        const val GRID_SAMPLE_ID = "seeds"

        /** Shown only in the Tune pane. */
        const val TUNE_SHEET_TEXT = "Copy as Kotlin"

        /** Long enough for a sheet to open or close. */
        const val SETTLE_MILLIS = 700L
        const val REFRESH_INTERVAL_NANOS = 16_666_667L
        const val FRAMES_PER_WINDOW = 31
    }
}
