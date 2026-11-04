@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.LocalMorpheusGraphFrameStats
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.CycleControl
import com.rossomak.morpheus.samples.LocalCycleControl
import com.rossomak.morpheus.samples.LocalSampleTileStats
import com.rossomak.morpheus.samples.SampleTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * The open sample on the [previewFrame]'s canvas, centered on the sample theme's background, under its title,
 * the theme control, the Tune and Reset to defaults actions, Pause for every sample, the scrub slider and the comet
 * debug button for a sample that runs a cycle, the Frame stats button and the preview canvas control. Frame stats
 * opens the frame stats pane, which shows the reading under a header with its notes (debug switches on, capped,
 * canvas scaled); the sample reports into [frameStats] through
 * [LocalMorpheusGraphFrameStats], so neither needs anything from the sample. A window wide enough shows the panes in a
 * side column; [opensOnePaneAtATime] is for a narrower one, whose panes are modal sheets, so opening one closes the
 * others. [onBack] is the single-pane layout's way back to the list. [logsBenchmarkReadings] prints each reading
 * for the benchmark recipe's script.
 *
 * While a sample is open, nothing in the control rows appears, disappears or changes width, so the stage keeps its
 * size until the window or the preview's canvas changes.
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
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sample = session.sample
    val darkOnly = sample.recipe.themes == CaptureThemes.DarkOnly
    val dark = darkOnly || themeMode.isDark(systemDark)
    val canvasSize = previewFrame.canvasSize(sample.recipe)
    RecordFrameIntervals(frameStats, session.tiles)
    if (logsBenchmarkReadings) LogBenchmarkReadings(frameStats.stats) { frameStats.isCanvasScaled }
    // No published reading straddles a change of what is measured.
    LaunchedEffect(frameStats, session, dark, canvasSize, previewFrame.scalesToFit) { resetStats(frameStats, session) }
    LaunchedEffect(frameStats, session) {
        snapshotFlow { session.currentSpecs to session.cycle.isPaused }.collect { resetStats(frameStats, session) }
    }
    // A grid's tiles report with the page's switches: a flipped switch reaches every tile's stats.
    LaunchedEffect(frameStats, session) {
        snapshotFlow { frameStats.stats.switches() }.collect { session.tiles.mirrorSwitches() }
    }
    // Opens or closes a pane; with one pane at a time, opening it closes the others first.
    val toggle = { isOpen: Boolean, setOpen: (Boolean) -> Unit ->
        if (!isOpen && opensOnePaneAtATime) closePanes(session, frameStats)
        setOpen(!isOpen)
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the samples") }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(text = sample.title, style = MaterialTheme.typography.titleLarge)
                Text(text = sample.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeControl(selected = themeMode, enabled = !darkOnly, onSelect = onThemeModeChange)
            if (session.isTunable) {
                FilledTonalButton(onClick = { toggle(session.isTuning) { session.isTuning = it } }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Tune", maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = session::resetSpecs, enabled = session.isTuned) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Reset to defaults", maxLines = 1, softWrap = false)
                }
            }
            CycleControls(control = session.cycle, runsCycle = session.runsCycle)
            FrameStatsButton(isOpen = frameStats.isFrameStatsOpen, onClick = { toggle(frameStats.isFrameStatsOpen) { frameStats.isFrameStatsOpen = it } })
            if (session.runsCycle) CometDebugButton(frameStats = frameStats, onClick = { toggle(frameStats.isCometDebugExpanded) { frameStats.isCometDebugExpanded = it } })
        }
        PreviewFrameControl(
            frame = previewFrame,
            canvasSize = canvasSize,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        SampleTheme(dark = dark, modifier = Modifier.weight(1f).fillMaxWidth().testTag(STAGE_TEST_TAG)) {
            when (val preparation = session.preparation) {
                Preparation.Running -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Preparation.Failed -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = preparation.message, color = MaterialTheme.colorScheme.error)
                }
                Preparation.Done -> SampleStage(
                    canvasSize = canvasSize,
                    scalesToFit = previewFrame.scalesToFit,
                    onScaledChange = { frameStats.isCanvasScaled = it },
                ) {
                    CompositionLocalProvider(
                        LocalCycleControl provides session.cycle,
                        LocalMorpheusGraphFrameStats provides frameStats.stats,
                        LocalSampleTileStats provides session.tiles,
                        LocalLifecycleOwner provides rememberPausableLifecycleOwner(isPaused = session.cycle.isPaused),
                    ) { sample.content(session.specs) }
                }
            }
        }
    }
}

/** The area the sample's canvas is scaled into, for the layout test. */
internal const val STAGE_TEST_TAG = "sample-stage"

/** The canvas as shown on the stage, at its scaled size, for the layout test. */
internal const val CANVAS_TEST_TAG = "sample-canvas"

/** Starts a fresh window on the page's stats and on every tile's. */
private fun resetStats(frameStats: GalleryFrameStats, session: GallerySession) {
    frameStats.stats.reset()
    session.tiles.reset()
}

/**
 * Pauses and resumes the sample, whatever it is. A sample that [runsCycle] also gets the [ScrubSlot]. The Pause/Play
 * button lays out both labels and shows one, so it keeps the wider one's width whichever shows.
 */
@Composable
private fun CycleControls(control: CycleControl, runsCycle: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { control.isPaused = !control.isPaused }) {
            Box(contentAlignment = Alignment.Center) {
                PlayPauseLabel(icon = Icons.Filled.Pause, label = "Pause", shown = !control.isPaused)
                PlayPauseLabel(icon = Icons.Filled.PlayArrow, label = "Play", shown = control.isPaused)
            }
        }
        if (runsCycle) ScrubSlot(control)
    }
}

/**
 * Opens and closes a pane: an icon with its [label] as a tooltip and as what a screen reader says, filled while
 * [isActive]. An icon, not a labelled button, so the control row has room for it beside the scrub slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaneButton(icon: ImageVector, label: String, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        val colors = if (isActive) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.outlinedIconButtonColors()
        OutlinedIconButton(onClick = onClick, colors = colors) { Icon(icon, contentDescription = label) }
    }
}

/** One of the Pause/Play button's labels; a hidden one still takes its width, but is neither seen nor found. */
@Composable
private fun PlayPauseLabel(icon: ImageVector, label: String, shown: Boolean) {
    val visibility = if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
    Row(modifier = visibility, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(label, maxLines = 1, softWrap = false)
    }
}

/**
 * The cycle's slider and time. While paused the slider scrubs the cycle, so a morph can be held anywhere. While
 * playing it is disabled and follows the cycle, read every [PLAYHEAD_REFRESH] rather than on every frame: the
 * cycle's time changes each frame, and only this slot recomposes, that often. Paused, the slider reads the time
 * directly and nothing polls. The time is monospace and padded to [TIME_CHARS], so the slot keeps its width. Until
 * the sample's cycle reports its period, the slider rests at the start.
 */
@Composable
private fun ScrubSlot(control: CycleControl) {
    // Read in the coroutine, not in composition, so a frame's new time doesn't recompose anything.
    val playhead by produceState(Duration.ZERO, control, control.isPaused) {
        if (control.isPaused) return@produceState
        while (true) {
            value = control.time
            delay(PLAYHEAD_REFRESH)
        }
    }
    val period = control.period
    val time = if (control.isPaused) control.time else playhead
    Slider(
        value = if (period == null) 0f else (time / period).toFloat().coerceIn(0f, 1f),
        onValueChange = { fraction -> if (period != null) control.time = period * fraction.toDouble() },
        enabled = control.isPaused && period != null,
        modifier = Modifier.width(SCRUB_SLIDER_WIDTH),
    )
    Text(text = timeText(time, period), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
}

/** `  3.4 /  12.0 s`, each number padded to [TIME_CHARS]; dashes until there is a [period]. */
private fun timeText(time: Duration, period: Duration?): String {
    val shownTime = if (period == null) "–" else seconds(time)
    val shownPeriod = if (period == null) "–" else seconds(period)
    return "${shownTime.padStart(TIME_CHARS)} / ${shownPeriod.padStart(TIME_CHARS)} s"
}

/** [duration] in seconds, to a tenth. */
private fun seconds(duration: Duration): String {
    val tenths = (duration / 100.milliseconds).roundToInt()
    return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}"
}

private const val TENTHS_PER_SECOND = 10

/** Up to 999.9 s, so any cycle's time fits the slot's width. */
private const val TIME_CHARS = 5
private val PLAYHEAD_REFRESH = 250.milliseconds
private val SCRUB_SLIDER_WIDTH = 240.dp

/** A canvas scaled by less than this shows at its own size, give or take a pixel's rounding. */
private const val SCALE_EPSILON = 1e-3f

@Composable
private fun ThemeControl(selected: ThemeMode, enabled: Boolean, onSelect: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                label = { Text(mode.label, maxLines = 1, softWrap = false) },
            )
        }
    }
}

/**
 * Runs [content] on a [canvasSize] canvas inside a window reference of that same size, so the mesh density
 * follows the canvas, not the app window, as in a capture. The canvas is centered and, when it
 * [scalesToFit], scaled uniformly to fill the stage; otherwise it shows at its own size, shrunk only if it
 * wouldn't fit. Either way it is never cropped or scrolled, and the window reference keeps the canvas size,
 * so a scaled canvas looks the same, only bigger or smaller. A null [canvasSize] fills the stage.
 * [onScaledChange] hears whether the canvas shows at other than its own size.
 */
@Composable
private fun SampleStage(canvasSize: DpSize?, scalesToFit: Boolean, onScaledChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
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
