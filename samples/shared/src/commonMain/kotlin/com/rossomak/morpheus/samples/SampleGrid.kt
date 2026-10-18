package com.rossomak.morpheus.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One labeled cell of a [SampleGrid]; [content] draws inside the tile's clipped bounds. */
class SampleTile(val label: String, val content: @Composable BoxScope.() -> Unit)

/**
 * Lays [tiles] out in rows of [columns] filling the canvas, each under its [SampleLabel], each clipped to
 * its own rounded background. Tiles keep the canvas's window reference, so a spec gets the same cell size
 * in every tile whatever the tile's size.
 */
@Composable
fun SampleGrid(tiles: List<SampleTile>, columns: Int, modifier: Modifier = Modifier, spacing: Dp = 8.dp) {
    require(columns > 0) { "A grid needs at least one column, not $columns" }
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        tiles.chunked(columns).forEach { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { tile ->
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SampleLabel(tile.label)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            content = tile.content,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
