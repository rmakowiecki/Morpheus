package com.rossomak.morpheus.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** A small label drawn inside a sample, such as a tile's caption, in the sample theme's colors. */
@Composable
fun SampleLabel(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelMedium, maxLines: Int = Int.MAX_VALUE) {
    Text(text = text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
}
