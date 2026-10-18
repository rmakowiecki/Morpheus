package com.rossomak.morpheus.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The time since this sample's first frame, wrapped at [period] when one is given. It follows the frame
 * clock, so it advances with a capture's virtual clock: drive morph and scroll cycles from it, never
 * from `delay`. A new [period] applies from the next frame without restarting the clock.
 */
@Composable
fun rememberCycleTime(period: Duration? = null): State<Duration> {
    require(period == null || period.isPositive()) { "A cycle period must be positive, not $period" }
    val elapsed = remember { mutableStateOf(Duration.ZERO) }
    val currentPeriod by rememberUpdatedState(period)
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                val total = nowNanos - startNanos
                elapsed.value = (currentPeriod?.let { total % it.inWholeNanoseconds } ?: total).nanoseconds
            }
        }
    }
    return elapsed
}
