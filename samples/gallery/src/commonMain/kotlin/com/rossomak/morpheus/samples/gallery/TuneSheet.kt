package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.tuner.MorpheusGraphTuningSheetContent

/**
 * The tuner on the open sample's selected spec, under a picker for its tunable specs and the Export
 * button. Violations are gallery state only: samples own their modifiers, so the toggle draws nothing.
 */
@Composable
internal fun TuneSheetContent(
    session: GallerySession,
    showViolations: Boolean,
    onShowViolationsChange: (Boolean) -> Unit,
    onExport: (MorpheusGraphSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = session.selectedSpec ?: return
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        session.sample.specs.forEach { tunable ->
                            FilterChip(
                                selected = tunable.name == session.selectedSpecName,
                                onClick = { session.selectedSpecName = tunable.name },
                                label = { Text(tunable.name) },
                            )
                        }
                    }
                    FilledTonalButton(onClick = { onExport(spec) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Copy as Kotlin")
                    }
                }
            },
        )
    }
}

/** The wide layout's sheet, beside the detail pane so the sample stays in view. */
@Composable
internal fun SideSheet(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Tune", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close the tuner") }
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
