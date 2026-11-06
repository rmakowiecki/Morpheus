package com.rossomak.morpheus.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The time since this sample's first frame, wrapped at [period] when one is given. It follows the frame
 * clock, so it advances with a capture's virtual clock: drive morph and scroll cycles from it, never
 * from `delay`. A new [period] applies from the next frame without restarting the clock.
 *
 * Under a [LocalCycleControl], a periodic cycle reports its [period] and time to the control, and holds
 * at the control's time while it [CycleControl.isPaused]; resuming continues from there.
 */
@Composable
fun rememberCycleTime(period: Duration? = null): State<Duration> {
    require(period == null || period.isPositive()) { "A cycle period must be positive, not $period" }
    val elapsed = remember { mutableStateOf(Duration.ZERO) }
    val currentPeriod by rememberUpdatedState(period)
    val control = LocalCycleControl.current?.takeIf { period != null }
    DisposableEffect(control, period) {
        control?.period = period
        onDispose { control?.period = null }
    }
    LaunchedEffect(control) {
        var startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                if (control?.isPaused == true) {
                    // Resuming picks up from the held time, wherever it was scrubbed to.
                    startNanos = nowNanos - control.time.inWholeNanoseconds
                    elapsed.value = control.time
                } else {
                    val total = nowNanos - startNanos
                    elapsed.value = (currentPeriod?.let { total % it.inWholeNanoseconds } ?: total).nanoseconds
                    control?.time = elapsed.value
                }
            }
        }
    }
    return elapsed
}

/**
 * Lets a host pause a sample's periodic cycle and scrub it by hand, such as a morph held at one end while
 * its spec is tuned. [period] is the running cycle's, or null while no sample runs one.
 */
@Stable
class CycleControl {
    var isPaused by mutableStateOf(false)

    /** Where the cycle is: written by the cycle while it runs, and by the host while paused. */
    var time by mutableStateOf(Duration.ZERO)

    var period: Duration? by mutableStateOf(null)
        internal set
}

/** The host's [CycleControl], if it offers one; a capture doesn't, so its cycles always run. */
val LocalCycleControl: ProvidableCompositionLocal<CycleControl?> = staticCompositionLocalOf { null }
