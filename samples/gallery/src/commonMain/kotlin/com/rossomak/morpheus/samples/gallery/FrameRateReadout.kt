package com.rossomak.morpheus.samples.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/**
 * The frame rate and the frame interval, averaged over the last half second of the frame clock. The clock is
 * the gallery's own, so a slow sample drags it down the same way on every platform.
 */
@Composable
internal fun FrameRateReadout(modifier: Modifier = Modifier) {
    var frameMillis by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var windowStartNanos = withFrameNanos { it }
        var frames = 0
        while (true) {
            withFrameNanos { nowNanos ->
                frames++
                val windowNanos = nowNanos - windowStartNanos
                if (windowNanos >= PUBLISH_INTERVAL_NANOS) {
                    frameMillis = windowNanos / frames / NANOS_PER_MILLI
                    windowStartNanos = nowNanos
                    frames = 0
                }
            }
        }
    }
    Text(text = readout(frameMillis), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

private fun readout(frameMillis: Float): String {
    if (frameMillis <= 0f) return "– fps · – ms"
    val tenthsOfMillis = (frameMillis * TENTHS_PER_MILLI).roundToInt()
    return "${(MILLIS_PER_SECOND / frameMillis).roundToInt()} fps · ${tenthsOfMillis / TENTHS_PER_MILLI}.${tenthsOfMillis % TENTHS_PER_MILLI} ms"
}

private const val PUBLISH_INTERVAL_NANOS = 500_000_000L
private const val NANOS_PER_MILLI = 1_000_000f
private const val MILLIS_PER_SECOND = 1000f
private const val TENTHS_PER_MILLI = 10
