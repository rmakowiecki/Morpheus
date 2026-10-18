package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.SampleTheme

/**
 * The open sample on the [previewFrame]'s canvas, centered on the sample theme's background, under its title,
 * the theme control, the Tune action and the preview canvas control. [onBack] is the single-pane layout's
 * way back to the list.
 */
@Composable
internal fun SampleDetail(
    session: GallerySession,
    themeMode: ThemeMode,
    systemDark: Boolean,
    previewFrame: PreviewFrame,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sample = session.sample
    val darkOnly = sample.recipe.themes == CaptureThemes.DarkOnly
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the samples") }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(text = sample.title, style = MaterialTheme.typography.titleLarge)
                Text(text = sample.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeControl(selected = themeMode, enabled = !darkOnly, onSelect = onThemeModeChange)
            if (sample.specs.isNotEmpty()) {
                FilledTonalButton(onClick = { session.isTuning = !session.isTuning }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Tune")
                }
            }
        }
        PreviewFrameControl(frame = previewFrame, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp))
        SampleTheme(dark = darkOnly || themeMode.isDark(systemDark), modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val preparation = session.preparation) {
                Preparation.Running -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Preparation.Failed -> Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(text = preparation.message, color = MaterialTheme.colorScheme.error)
                }
                Preparation.Done -> SampleStage(
                    canvasSize = previewFrame.canvasSize(sample.recipe),
                    scalesToFit = previewFrame.scalesToFit,
                ) { sample.content(session.specs) }
            }
        }
    }
}

@Composable
private fun ThemeControl(selected: ThemeMode, enabled: Boolean, onSelect: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                label = { Text(mode.label) },
            )
        }
    }
}

/**
 * Runs [content] on a [canvasSize] canvas inside a window reference of that same size, so the mesh density
 * follows the canvas, not the app window, as in a capture. The canvas is centered and, when it
 * [scalesToFit], scaled uniformly to fill the stage; otherwise it shows at its own size, shrunk only if it
 * wouldn't fit. Either way it is never cropped or scrolled, and the window reference keeps the canvas size,
 * so a scaled canvas looks the same, only bigger or smaller. A null [canvasSize] fills the stage.
 */
@Composable
private fun SampleStage(canvasSize: DpSize?, scalesToFit: Boolean, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val canvas = canvasSize ?: DpSize(maxWidth, maxHeight)
        val fitScale = minOf(maxWidth / canvas.width, maxHeight / canvas.height).coerceAtLeast(0f)
        val scale = if (scalesToFit) fitScale else minOf(1f, fitScale)
        val canvasWindow = rememberCanvasWindowInfo(canvas)
        Box(modifier = Modifier.size(canvas.width * scale, canvas.height * scale), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .requiredSize(canvas)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        // Captures crop to the canvas; the graph draws past its bounds otherwise.
                        clip = true
                    },
            ) {
                CompositionLocalProvider(LocalWindowInfo provides canvasWindow, content = content)
            }
        }
    }
}

/** The real window's info, except that its container is a [canvasSize] canvas. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberCanvasWindowInfo(canvasSize: DpSize): WindowInfo {
    val window = LocalWindowInfo.current
    val density = LocalDensity.current
    return remember(window, density, canvasSize) {
        val containerSize = with(density) { IntSize(canvasSize.width.roundToPx(), canvasSize.height.roundToPx()) }
        object : WindowInfo {
            override val isWindowFocused: Boolean get() = window.isWindowFocused
            override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers
            override val containerSize: IntSize get() = containerSize
            override val containerDpSize: DpSize get() = canvasSize
        }
    }
}
