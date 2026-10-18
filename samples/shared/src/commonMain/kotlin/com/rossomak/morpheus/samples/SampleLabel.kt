package com.rossomak.morpheus.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A small label drawn inside a sample, such as a tile's caption, in the sample theme's colors. */
@Composable
fun SampleLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
}
