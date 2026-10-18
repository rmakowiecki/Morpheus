package com.rossomak.morpheus.samples

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

/** One labeled cell of a [SampleGrid]; [content] draws inside the tile's clipped bounds. */
class SampleTile(val label: String, val content: @Composable BoxScope.() -> Unit)
