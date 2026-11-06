package com.rossomak.morpheus.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphFill

/**
 * The theme every sample renders in, in the gallery and in a capture alike, so a capture looks like the
 * preview: stock Material3 light or dark colors, a surface background, and graphs filled solid in
 * `onSurfaceVariant` unless a sample picks its own fill.
 */
@Composable
fun SampleTheme(dark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colorScheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalMorpheusGraphFill provides MorpheusGraphFill.Solid(colorScheme.onSurfaceVariant)) {
            Surface(modifier = modifier, color = colorScheme.surface, content = content)
        }
    }
}
