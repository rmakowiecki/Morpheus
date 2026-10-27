@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphCometDebug
import com.rossomak.morpheus.MorpheusGraphFrameStats

/** Opens and closes the comet debug pane, filled while any comet overlay is on. */
@Composable
internal fun CometDebugButton(frameStats: GalleryFrameStats, modifier: Modifier = Modifier) {
    val active = frameStats.isCometDebugExpanded || frameStats.stats.cometDebug.any
    val colors = if (active) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.outlinedButtonColors()
    OutlinedButton(onClick = { frameStats.isCometDebugExpanded = !frameStats.isCometDebugExpanded }, colors = colors, modifier = modifier) {
        Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Comet debug")
    }
}

/** One chip per comet overlay (see [MorpheusGraphCometDebug]), and a pair turning them all on or off. */
@Composable
internal fun CometDebugPanel(stats: MorpheusGraphFrameStats, modifier: Modifier = Modifier) {
    val debug = stats.cometDebug
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CometDebugChip("Pieces", debug.pieces) { stats.cometDebug = debug.copy(pieces = it) }
                CometDebugChip("Centroids", debug.centroids) { stats.cometDebug = debug.copy(centroids = it) }
                CometDebugChip("Heads", debug.heads) { stats.cometDebug = debug.copy(heads = it) }
                CometDebugChip("Head targets", debug.targets) { stats.cometDebug = debug.copy(targets = it) }
                CometDebugChip("Comet paths", debug.paths) { stats.cometDebug = debug.copy(paths = it) }
                CometDebugChip("End discs", debug.endDiscs) { stats.cometDebug = debug.copy(endDiscs = it) }
                CometDebugChip("Clearance", debug.clearance) { stats.cometDebug = debug.copy(clearance = it) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { stats.cometDebug = MorpheusGraphCometDebug.All }) { Text("All") }
                TextButton(onClick = { stats.cometDebug = MorpheusGraphCometDebug.None }) { Text("None") }
            }
            Text(
                text = "Leaving pieces: squares · arriving: dots · grey: sends nothing. Clearance: green start, red end.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CometDebugChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onChange(!selected) }, label = { Text(label) })
}
