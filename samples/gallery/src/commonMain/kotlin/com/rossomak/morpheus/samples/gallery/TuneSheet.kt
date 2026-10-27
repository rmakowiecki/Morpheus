package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.MorphEasing
import com.rossomak.morpheus.tuner.MorpheusGraphTuningSheetContent
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * The open sample's settings: a Morphs section with the timing and an easing picker per morph, when the
 * sample morphs, then a Specs section with a picker for its tunable specs, the Export button and the tuner
 * on the selected spec. Every sample shows the same sections, leaving out the ones it has nothing for. The
 * violations toggle is the gallery's frame stats switch, so it rings violations on every sample.
 */
@Composable
internal fun TuneSheetContent(
    session: GallerySession,
    showViolations: Boolean,
    onShowViolationsChange: (Boolean) -> Unit,
    onExport: (MorpheusGraphSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = session.selectedSpec
    if (spec == null) {
        Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            MorphsSection(session)
        }
        return
    }
    // The tuner remembers its selected layer; a different spec opens afresh, on its front layer.
    key(session.selectedSpecName) {
        MorpheusGraphTuningSheetContent(
            modifier = modifier,
            spec = spec,
            showViolations = showViolations,
            onSpecChange = session::tune,
            onShowViolationsChange = onShowViolationsChange,
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphsSection(session)
                    SectionLabel("Specs")
                    SpecTabs(session)
                    FilledTonalButton(onClick = { onExport(spec) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Copy as Kotlin")
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

/** One tab per tunable spec; the tuner below edits the selected one. */
@Composable
private fun SpecTabs(session: GallerySession) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        session.sample.specs.forEach { tunable ->
            FilterChip(
                selected = tunable.name == session.selectedSpecName,
                onClick = { session.selectedSpecName = tunable.name },
                label = { Text(tunable.name) },
            )
        }
    }
}

/**
 * The settings every morph of the open sample shares, its duration and the hold at each spec, then a row per
 * morph with its name and a menu of the easings its fraction can follow. Nothing for a sample without morphs.
 */
@Composable
private fun MorphsSection(session: GallerySession) {
    val timing = session.timing ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Morphs")
        DurationSlider("Morph duration", timing.morph, MorphDurationRange) { session.timing = timing.copy(morph = it) }
        DurationSlider("Hold at each spec", timing.hold, HoldRange) { session.timing = timing.copy(hold = it) }
        session.sample.morphs.forEach { morph ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(morph.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                EasingMenu(selected = session.easingOf(morph.name), onSelect = { session.tuneEasing(morph.name, it) })
            }
        }
    }
}

private val MorphDurationRange = 500.milliseconds..10.seconds
private val HoldRange = Duration.ZERO..5.seconds
private val DurationStep = 50.milliseconds

/** A slider over [range], in [DurationStep] steps, labelled with the value in seconds. */
@Composable
private fun DurationSlider(label: String, value: Duration, range: ClosedRange<Duration>, onValueChange: (Duration) -> Unit) {
    val steps = ((range.endInclusive - range.start) / DurationStep).toInt()
    Column {
        Text("$label: ${value.toString(DurationUnit.SECONDS, decimals = 2)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = (value / DurationStep).toFloat(),
            onValueChange = { onValueChange(DurationStep * it.roundToInt()) },
            valueRange = (range.start / DurationStep).toFloat()..(range.endInclusive / DurationStep).toFloat(),
            steps = steps - 1,
        )
    }
}

@Composable
private fun EasingMenu(selected: MorphEasing, onSelect: (MorphEasing) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { isExpanded = true }) {
            Text(selected.label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            MorphEasing.entries.forEach { easing ->
                DropdownMenuItem(
                    text = { Text(easing.label) },
                    onClick = {
                        onSelect(easing)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

/** The wide layout's sheet, titled [title], beside the detail pane so the sample stays in view. */
@Composable
internal fun SideSheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close the $title pane") }
            }
            content()
        }
    }
}

/** The single-pane layout's sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TuneBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // No scrim: the whole point of tuning is watching the sample change above the sheet.
    ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) { content() }
}
