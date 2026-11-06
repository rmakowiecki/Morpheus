package com.rossomak.morpheus.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * One labeled cell of a [SampleGrid]; [content] draws inside the tile's clipped bounds. [size] is how tall the
 * tile's surface is, and the tiles of one row share it.
 */
class SampleTile(val label: String, val size: TileSize = TileSize.Weight(1f), val content: @Composable BoxScope.() -> Unit)

/** How tall a [SampleTile]'s surface is: a [Weight] share of the height the fixed tiles leave, or a [FixedHeight]. */
@Immutable
sealed interface TileSize {
    data class Weight(val weight: Float) : TileSize {
        init {
            require(weight > 0f) { "A tile's weight must be positive, not $weight" }
        }
    }

    data class FixedHeight(val height: Dp) : TileSize
}
