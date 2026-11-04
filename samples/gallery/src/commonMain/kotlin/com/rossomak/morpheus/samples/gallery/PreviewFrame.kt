package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.ClassicCanvas
import com.rossomak.morpheus.samples.PortraitCanvas
import com.rossomak.morpheus.samples.SquareCanvas
import com.rossomak.morpheus.samples.WideCanvas

/** The canvas sizes the preview control offers. [size] is null where the size comes from elsewhere. */
internal enum class PreviewPreset(val label: String, val size: DpSize?) {
    /** The sample's own capture canvas: what its media will look like. */
    Sample("Sample", null),

    /** The whole pane, at whatever ratio it has. */
    Fill("Fill pane", null),
    Wide("16:9", WideCanvas),
    Classic("4:3", ClassicCanvas),
    Square("1:1", SquareCanvas),
    Portrait("9:16", PortraitCanvas),

    /** A width and height typed in. */
    Custom("Custom", null),
}

/**
 * The gallery-wide preview canvas, kept across samples: a [preset], the [customSize] for
 * [PreviewPreset.Custom], and whether a fixed-size canvas [scalesToFit] the pane or shows at its own size
 * (shrunk only when it wouldn't fit).
 */
@Stable
internal class PreviewFrame {
    var preset by mutableStateOf(PreviewPreset.Sample)
    var customSize by mutableStateOf(WideCanvas)
    var scalesToFit by mutableStateOf(true)

    /** The canvas for a sample with this [recipe], or null to fill the pane. */
    fun canvasSize(recipe: CaptureRecipe): DpSize? = when (preset) {
        PreviewPreset.Sample -> recipe.canvasSize
        PreviewPreset.Fill -> null
        PreviewPreset.Custom -> customSize
        else -> preset.size
    }
}

/**
 * Picks the preview canvas: a preset, a custom width and height in dp, and scale-to-fit. Every control is always
 * there, so the row wraps the same whatever the preset: the width and height fields take input only under
 * [PreviewPreset.Custom] and otherwise show the [canvasSize] (a dash when the canvas fills the pane), and scale-to-fit
 * is off limits only when the canvas fills the pane.
 */
@Composable
internal fun PreviewFrameControl(frame: PreviewFrame, canvasSize: DpSize?, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewPreset.entries.forEach { preset ->
            FilterChip(selected = frame.preset == preset, onClick = { frame.preset = preset }, label = { Text(preset.label) })
        }
        if (frame.preset == PreviewPreset.Custom) {
            DpField(label = "Width", value = frame.customSize.width.value.toInt()) { frame.customSize = frame.customSize.copy(width = it.dp) }
            DpField(label = "Height", value = frame.customSize.height.value.toInt()) { frame.customSize = frame.customSize.copy(height = it.dp) }
        } else {
            ShownDpField(label = "Width", value = canvasSize?.width?.value?.toInt())
            ShownDpField(label = "Height", value = canvasSize?.height?.value?.toInt())
        }
        val scaleEnabled = frame.preset != PreviewPreset.Fill
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = frame.scalesToFit, onCheckedChange = { frame.scalesToFit = it }, enabled = scaleEnabled)
            Text(text = "Scale to fit", color = LocalContentColor.current.copy(alpha = if (scaleEnabled) 1f else DISABLED_ALPHA))
        }
    }
}

/**
 * A disabled [DpField]'s look, showing a [value] that comes from elsewhere, or a dash: never empty, since an empty
 * field holds its label inside and takes another height.
 */
@Composable
private fun ShownDpField(label: String, value: Int?) {
    OutlinedTextField(
        value = value?.toString() ?: "–",
        onValueChange = {},
        enabled = false,
        label = { Text("$label (dp)") },
        singleLine = true,
        modifier = Modifier.width(DP_FIELD_WIDTH),
    )
}

/** A whole number of dp; only values in [MIN_CANVAS_DP]..[MAX_CANVAS_DP] reach [onValueChange]. */
@Composable
private fun DpField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(MAX_DIGITS)
            text.toIntOrNull()?.takeIf { it in MIN_CANVAS_DP..MAX_CANVAS_DP }?.let(onValueChange)
        },
        label = { Text("$label (dp)") },
        isError = parsed == null || parsed !in MIN_CANVAS_DP..MAX_CANVAS_DP,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(DP_FIELD_WIDTH),
    )
}

private val DP_FIELD_WIDTH = 120.dp

/** Material's alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f
private const val MIN_CANVAS_DP = 16
private const val MAX_CANVAS_DP = 4000
private const val MAX_DIGITS = 4
