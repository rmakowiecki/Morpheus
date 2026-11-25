@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.LocalMorpheusGraphFrameStats
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LocalCycleControl
import com.rossomak.morpheus.samples.LocalSampleTileStats
import com.rossomak.morpheus.samples.SampleTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The open sample, its title, description and controls. The sample reports into [frameStats] through
 * [LocalMorpheusGraphFrameStats]. [opensOnePaneAtATime] is for a window without a side column, whose panes are sheets.
 * [onBack] is the single-pane layout's way back to the list. [logsBenchmarkReadings] prints each reading for the
 * benchmark script.
 *
 * The detail scrolls, and starts at the top for each sample. A regular one has the stage last, on the
 * [previewFrame]'s canvas; see [StageBelow] for its height. A [compact] one, a phone's, has the stage first at the full
 * width, on the sample's own canvas scaled to fit, then the controls and the description. Its theme and its panes but
 * Tune are in a menu, and it has no preview canvas control.
 *
 * While a sample is open no control appears, disappears or changes width, so the stage keeps its size until the
 * window or the preview's canvas changes.
 */
@Suppress("LongParameterList") // The gallery-wide state the pane shows, one piece each.
@Composable
internal fun SampleDetail(
    session: GallerySession,
    themeMode: ThemeMode,
    systemDark: Boolean,
    previewFrame: PreviewFrame,
    frameStats: GalleryFrameStats,
    opensOnePaneAtATime: Boolean,
    logsBenchmarkReadings: Boolean,
    compact: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sample = session.sample
    val darkOnly = sample.recipe.themes == CaptureThemes.DarkOnly
    val dark = darkOnly || themeMode.isDark(systemDark)
    // A dark-only sample's disabled theme control shows what the stage shows.
    val shownThemeMode = if (darkOnly) ThemeMode.Dark else themeMode
    val canvasSize = if (compact) sample.recipe.canvasSize else previewFrame.canvasSize(sample.recipe)
    val scalesToFit = compact || previewFrame.scalesToFit
    RecordFrameIntervals(frameStats, session.tiles)
    if (logsBenchmarkReadings) LogBenchmarkReadings(frameStats.stats) { frameStats.isCanvasScaled }
    // No published reading straddles a change of what is measured.
    LaunchedEffect(frameStats, session, dark, canvasSize, scalesToFit) { resetStats(frameStats, session) }
    LaunchedEffect(frameStats, session) {
        snapshotFlow { session.currentSpecs to session.cycle.isPaused }.collect { resetStats(frameStats, session) }
    }
    // A grid's tiles report with the page's switches: a flipped switch reaches every tile's stats.
    LaunchedEffect(frameStats, session) {
        snapshotFlow { frameStats.stats.switches() }.collect { session.tiles.mirrorSwitches() }
    }
    val panes = rememberOpenPanes(session, frameStats, closesOthers = opensOnePaneAtATime)
    val scrollState = remember(session) { ScrollState(initial = 0) }
    // Built in each layout's own place, so crossing the compact line restarts the sample's content; the session, and with
    // it the preparation and tuning, stays.
    val stage = @Composable { stagePadding: PaddingValues, stageModifier: Modifier ->
        StageArea(session, frameStats, dark = dark, canvasSize = canvasSize, scalesToFit = scalesToFit, padding = stagePadding, modifier = stageModifier)
    }
    BoxWithConstraints(modifier = modifier) {
        val paneWidth = maxWidth
        val paneHeight = maxHeight
        val scrolling = Modifier.fillMaxWidth().verticalScroll(scrollState).testTag(DETAIL_SCROLL_TEST_TAG)
        if (compact) {
            Column(modifier = scrolling) {
                DetailHeader(onBack) {
                    Text(text = sample.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    CompactMenu(
                        themeMode = shownThemeMode,
                        themeEnabled = !darkOnly,
                        onThemeModeChange = onThemeModeChange,
                        panes = panes,
                        frameStats = frameStats,
                    )
                }
                val canvasHeight = paneWidth * (sample.recipe.canvasSize.height / sample.recipe.canvasSize.width)
                val stageHeight = minOf(canvasHeight + COMPACT_STAGE_PADDING * 2, paneHeight * MAX_COMPACT_STAGE_SHARE)
                stage(PaddingValues(vertical = COMPACT_STAGE_PADDING), Modifier.fillMaxWidth().height(stageHeight))
                CompactControls(session = session, panes = panes)
                DescriptionText(sample.description, Modifier.padding(16.dp))
            }
        } else {
            StageBelow(paneHeight = paneHeight, modifier = scrolling, stage = { stage(REGULAR_STAGE_PADDING, Modifier.fillMaxSize()) }) {
                DetailHeader(onBack) {
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(text = sample.title, style = MaterialTheme.typography.titleLarge)
                        DescriptionText(sample.description)
                    }
                }
                RegularControls(
                    themeMode = shownThemeMode,
                    themeEnabled = !darkOnly,
                    onThemeModeChange = onThemeModeChange,
                    session = session,
                    panes = panes,
                    frameStats = frameStats,
                )
                PreviewFrameControl(
                    frame = previewFrame,
                    canvasSize = canvasSize,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** The Back button, when there is an [onBack], then the [title]. */
@Composable
private fun DetailHeader(onBack: (() -> Unit)?, title: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BackButton(onBack)
        title()
    }
}

@Composable
private fun DescriptionText(description: String, modifier: Modifier = Modifier) {
    Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The stage: the [session]'s sample on a [canvasSize] canvas inside [padding], or its preparation's progress or failure. */
@Composable
private fun StageArea(
    session: GallerySession,
    frameStats: GalleryFrameStats,
    dark: Boolean,
    canvasSize: DpSize?,
    scalesToFit: Boolean,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SampleTheme(dark = dark, modifier = modifier.testTag(STAGE_TEST_TAG)) {
        when (val preparation = session.preparation) {
            Preparation.Running -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Preparation.Failed -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(text = preparation.message, color = MaterialTheme.colorScheme.error)
            }
            Preparation.Done -> SampleStage(
                canvasSize = canvasSize,
                scalesToFit = scalesToFit,
                padding = padding,
                onScaledChange = { frameStats.isCanvasScaled = it },
            ) {
                CompositionLocalProvider(
                    LocalCycleControl provides session.cycle,
                    LocalMorpheusGraphFrameStats provides frameStats.stats,
                    LocalSampleTileStats provides session.tiles,
                    LocalLifecycleOwner provides rememberPausableLifecycleOwner(isPaused = session.cycle.isPaused),
                ) { session.sample.content(session.specs) }
            }
        }
    }
}

internal const val STAGE_TEST_TAG = "sample-stage"

internal const val CANVAS_TEST_TAG = "sample-canvas"

internal const val DETAIL_SCROLL_TEST_TAG = "sample-detail-scroll"

/** The stage's least height in a regular detail, however short the window. */
private val MIN_STAGE_HEIGHT = 320.dp

/** The stage's least share of a regular detail's height. */
private const val MIN_STAGE_SHARE = 0.5f

/** The most of a compact detail's height the stage takes, so a very tall canvas still leaves the controls in view. */
private const val MAX_COMPACT_STAGE_SHARE = 0.75f

/** Above and below a compact stage's canvas, which takes the full width. */
private val COMPACT_STAGE_PADDING = 8.dp

private val REGULAR_STAGE_PADDING = PaddingValues(horizontal = 16.dp, vertical = 32.dp)

/**
 * The [header] over the [stage]. The stage takes the height the header leaves of the [paneHeight], but at least
 * [MIN_STAGE_HEIGHT] and [MIN_STAGE_SHARE] of it; past that, the scrolling [modifier] scrolls.
 */
@Composable
private fun StageBelow(paneHeight: Dp, modifier: Modifier, stage: @Composable () -> Unit, header: @Composable () -> Unit) {
    Layout(
        content = {
            Column { header() }
            Box { stage() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val headerPlaceable = measurables[0].measure(Constraints(minWidth = width, maxWidth = width))
        val pane = paneHeight.roundToPx()
        val minStageHeight = maxOf(MIN_STAGE_HEIGHT.roundToPx(), (pane * MIN_STAGE_SHARE).roundToInt())
        val stageHeight = maxOf(pane - headerPlaceable.height, minStageHeight)
        val stagePlaceable = measurables[1].measure(Constraints.fixed(width, stageHeight))
        layout(width, headerPlaceable.height + stageHeight) {
            headerPlaceable.place(0, 0)
            stagePlaceable.place(0, headerPlaceable.height)
        }
    }
}

/** Starts a fresh window on the page's stats and on every tile's. */
private fun resetStats(frameStats: GalleryFrameStats, session: GallerySession) {
    frameStats.stats.reset()
    session.tiles.reset()
}

/** A canvas scaled by less than this shows at its own size, give or take a pixel's rounding. */
private const val SCALE_EPSILON = 1e-3f

/**
 * Runs [content] on a [canvasSize] canvas inside a window reference of that same size, so the cell density
 * follows the canvas, not the app window, as in a capture. The canvas is centered and, when it
 * [scalesToFit], scaled uniformly to fill the stage; otherwise it shows at its own size, shrunk only if it
 * wouldn't fit. Either way it is never cropped or scrolled, and the window reference keeps the canvas size,
 * so a scaled canvas looks the same, only bigger or smaller. A null [canvasSize] fills the stage.
 * [onScaledChange] hears whether the canvas shows at other than its own size.
 */
@Composable
private fun SampleStage(
    canvasSize: DpSize?,
    scalesToFit: Boolean,
    padding: PaddingValues,
    onScaledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        val canvas = canvasSize ?: DpSize(maxWidth, maxHeight)
        val fitScale = minOf(maxWidth / canvas.width, maxHeight / canvas.height).coerceAtLeast(0f)
        val scale = if (scalesToFit) fitScale else minOf(1f, fitScale)
        val scaled = abs(scale - 1f) > SCALE_EPSILON
        SideEffect { onScaledChange(scaled) }
        val canvasWindow = rememberCanvasWindowInfo(canvas)
        Box(modifier = Modifier.size(canvas.width * scale, canvas.height * scale).testTag(CANVAS_TEST_TAG), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .requiredSize(canvas)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        // Captures crop to the canvas; the graph draws past its bounds otherwise.
                        clip = true
                    },
            ) {
                CompositionLocalProvider(LocalWindowInfo provides canvasWindow, content = content)
            }
        }
    }
}

/** The real window's info, except that its container is a [canvasSize] canvas. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberCanvasWindowInfo(canvasSize: DpSize): WindowInfo {
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    return remember(window, density, canvasSize) {
        val containerSize = with(density) { IntSize(canvasSize.width.roundToPx(), canvasSize.height.roundToPx()) }
        object : WindowInfo {
            override val isWindowFocused: Boolean get() = window.isWindowFocused
            override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers
            override val containerSize: IntSize get() = containerSize
            override val containerDpSize: DpSize get() = canvasSize
        }
    }
}
