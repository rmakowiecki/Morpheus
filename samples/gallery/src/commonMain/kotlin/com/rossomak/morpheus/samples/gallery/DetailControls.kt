@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.samples.CycleControl
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The text and content description of the compact detail's menu button. */
internal const val MORE_OPTIONS = "More options"

/**
 * The cycle across the full width if the sample [GallerySession.runsCycle], then Tune and Reset to defaults, wrapping when
 * the two don't fit side by side.
 */
@Composable
internal fun CompactControls(session: GallerySession, panes: OpenPanes) {
    if (session.runsCycle) {
        CycleControls(control = session.cycle, fillsWidth = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    }
    if (GalleryPane.Tune in panes.available) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TuneButton(onClick = { panes.toggle(GalleryPane.Tune) })
            ResetButton(session)
        }
    }
}

@Composable
internal fun RegularControls(
    themeMode: ThemeMode,
    themeEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    session: GallerySession,
    panes: OpenPanes,
    frameStats: GalleryFrameStats,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeControl(selected = themeMode, enabled = themeEnabled, onSelect = onThemeModeChange)
        if (GalleryPane.Tune in panes.available) {
            TuneButton(onClick = { panes.toggle(GalleryPane.Tune) })
            ResetButton(session)
        }
        if (session.runsCycle) CycleControls(control = session.cycle, fillsWidth = false)
        PerformanceStatsButton(isOpen = panes.isOpen(GalleryPane.PerformanceStats), onClick = { panes.toggle(GalleryPane.PerformanceStats) })
        if (GalleryPane.CometDebug in panes.available) {
            CometDebugButton(frameStats = frameStats, onClick = { panes.toggle(GalleryPane.CometDebug) })
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the samples") }
}

@Composable
private fun TuneButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(GalleryPane.Tune.title, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ResetButton(session: GallerySession) {
    OutlinedButton(onClick = session::resetSpecs, enabled = session.isTuned) {
        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Reset to defaults", maxLines = 1, softWrap = false)
    }
}

/**
 * The compact detail's menu: the theme, disabled unless [themeEnabled], and every pane but Tune, checked while open.
 * Comet debug's item also says when its overlays stay on with the pane closed, as its button does.
 */
@Composable
internal fun CompactMenu(
    themeMode: ThemeMode,
    themeEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    panes: OpenPanes,
    frameStats: GalleryFrameStats,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = MORE_OPTIONS) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onThemeModeChange(mode)
                        expanded = false
                    },
                    enabled = themeEnabled,
                    leadingIcon = { RadioButton(selected = mode == themeMode, onClick = null, enabled = themeEnabled) },
                    modifier = Modifier.semantics { selected = mode == themeMode },
                )
            }
            HorizontalDivider()
            (panes.available - GalleryPane.Tune).forEach { pane ->
                val isOpen = panes.isOpen(pane)
                val overlaysOn = pane == GalleryPane.CometDebug && frameStats.stats.cometDebug.any
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(pane.title)
                            if (overlaysOn && !isOpen) Text(OVERLAYS_ON, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        panes.toggle(pane)
                        expanded = false
                    },
                    leadingIcon = { Checkbox(checked = isOpen, onCheckedChange = null) },
                    modifier = Modifier.semantics { stateDescription = paneStateDescription(isOpen, overlaysOn) },
                )
            }
        }
    }
}

/** Under the Comet debug menu item while its overlays stay on with the pane closed. */
internal const val OVERLAYS_ON = "Overlays on"

/**
 * Pause/Play and the [ScrubSlot], only for a sample that runs a cycle. The button lays out both labels, so it keeps the
 * wider one's width. When it [fillsWidth], the slider takes the width the button leaves, with the time under it.
 */
@Composable
private fun CycleControls(control: CycleControl, fillsWidth: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { control.isPaused = !control.isPaused }) {
            Box(contentAlignment = Alignment.Center) {
                PlayPauseLabel(icon = Icons.Filled.Pause, label = "Pause", shown = !control.isPaused)
                PlayPauseLabel(icon = Icons.Filled.PlayArrow, label = "Play", shown = control.isPaused)
            }
        }
        if (fillsWidth) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) { ScrubSlot(control, sliderModifier = Modifier.fillMaxWidth()) }
        } else {
            ScrubSlot(control, sliderModifier = Modifier.width(SCRUB_SLIDER_WIDTH))
        }
    }
}

/**
 * Toggles a pane: an icon with [label] as tooltip and content description, filled while [isActive]. [stateDescription]
 * says the same to a screen reader, which can't see the fill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaneButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        val colors = if (isActive) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.outlinedIconButtonColors()
        OutlinedIconButton(onClick = onClick, colors = colors, modifier = Modifier.semantics { this.stateDescription = stateDescription }) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** A Pause/Play label; hidden, it still takes its width but is neither seen nor found. */
@Composable
private fun PlayPauseLabel(icon: ImageVector, label: String, shown: Boolean) {
    val visibility = if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
    Row(modifier = visibility, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(label, maxLines = 1, softWrap = false)
    }
}

/**
 * The cycle's slider and time. Paused, the slider scrubs the cycle. Playing, it is disabled and follows the cycle,
 * polled every [PLAYHEAD_REFRESH] so only this slot recomposes, not every frame. The time is monospace and padded
 * to [TIME_CHARS] to keep the slot's width. Until the cycle reports its period, the slider rests at the start.
 */
@Composable
private fun ScrubSlot(control: CycleControl, sliderModifier: Modifier) {
    // Read in the coroutine, not in composition, so a frame's new time doesn't recompose anything.
    // Keyed on the control alone, so Play doesn't reset the playhead to zero for a frame.
    val playhead by produceState(Duration.ZERO, control) {
        snapshotFlow { control.isPaused }.collectLatest { isPaused ->
            while (!isPaused) {
                value = control.time
                delay(PLAYHEAD_REFRESH)
            }
        }
    }
    val period = control.period
    val time = if (control.isPaused) control.time else playhead
    Slider(
        value = if (period == null) 0f else (time / period).toFloat().coerceIn(0f, 1f),
        onValueChange = { fraction -> if (period != null) control.time = period * fraction.toDouble() },
        enabled = control.isPaused && period != null,
        modifier = sliderModifier,
    )
    Text(text = timeText(time, period), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
}

/** `  3.4 /  12.0 s`, each number padded to [TIME_CHARS]; dashes until there is a [period]. */
private fun timeText(time: Duration, period: Duration?): String {
    val shownTime = period?.let { seconds(time) } ?: "–"
    val shownPeriod = period?.let(::seconds) ?: "–"
    return "${shownTime.padStart(TIME_CHARS)} / ${shownPeriod.padStart(TIME_CHARS)} s"
}

/** [duration] in seconds, to a tenth. */
private fun seconds(duration: Duration): String {
    val tenths = (duration / 100.milliseconds).roundToInt()
    return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}"
}

private const val TENTHS_PER_SECOND = 10

/** Fits up to 999.9 s. */
private const val TIME_CHARS = 5
private val PLAYHEAD_REFRESH = 250.milliseconds
private val SCRUB_SLIDER_WIDTH = 240.dp

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
