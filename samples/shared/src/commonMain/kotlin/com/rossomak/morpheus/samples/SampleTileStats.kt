@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphFrameStats

/**
 * What a host offers a [SampleGrid] so each tile reports its frame stats on its own: the stats for the tile
 * with this label. Asking again for a label gives the same stats.
 */
fun interface SampleTileStats {
    fun statsFor(tileLabel: String): MorpheusGraphFrameStats
}

/** The host's [SampleTileStats], if it offers them; a capture doesn't, so a grid there reports no stats at all. */
val LocalSampleTileStats: ProvidableCompositionLocal<SampleTileStats?> = staticCompositionLocalOf { null }
