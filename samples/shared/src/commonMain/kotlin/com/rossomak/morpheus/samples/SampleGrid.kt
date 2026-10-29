@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.LocalMorpheusGraphFrameStats

/**
 * Lays [tiles] out in rows of [columns] filling the canvas, each under its [SampleLabel], each clipped to
 * its own rounded background. Tiles keep the canvas's window reference, so a spec gets the same cell size
 * in every tile whatever the tile's size.
 *
 * Each tile reports into the frame stats its host offers for its label ([LocalSampleTileStats]), so a host can
 * show the tiles side by side instead of summed. Without a host, a tile reports nothing.
 */
@Composable
fun SampleGrid(tiles: List<SampleTile>, columns: Int, modifier: Modifier = Modifier, spacing: Dp = 8.dp) {
    require(columns > 0) { "A grid needs at least one column, not $columns" }
    val rows = tiles.chunked(columns)
    rows.forEach { row -> require(row.map { it.size }.distinct().size == 1) { "The tiles of a row share a size, not ${row.map { it.size }}" } }
    val tileStats = LocalSampleTileStats.current
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            val size = row.first().size
            val rowModifier = if (size is TileSize.Weight) Modifier.weight(size.weight) else Modifier
            Row(modifier = rowModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { tile ->
                    val tileModifier = Modifier.weight(1f).let { if (size is TileSize.Weight) it.fillMaxHeight() else it }
                    Column(modifier = tileModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SampleLabel(tile.label)
                        val surface = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        Box(
                            modifier = when (size) {
                                is TileSize.Weight -> surface.weight(1f)
                                is TileSize.FixedHeight -> surface.height(size.height)
                            },
                        ) {
                            val stats = remember(tileStats, tile.label) { tileStats?.statsFor(tile.label) }
                            CompositionLocalProvider(LocalMorpheusGraphFrameStats provides stats) { tile.content(this) }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
