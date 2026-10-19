package com.rossomak.morpheus.samples

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How the capture tool renders a sample, so captures are reproducible without command-line flags. The
 * gallery also shows the sample at [canvasSize], with a window reference of that size.
 *
 * Animated captures render [duration] at [fps] (plus [crossfade] in [LoopMode.Crossfade]); the still
 * frame is the one at [stillAt]. A file over [budgetBytes] fails the capture.
 *
 * [fps] is at least [MIN_CAPTURE_FPS]: the library caps one clock step at 0.1 s, so fewer frames a second
 * would slow the motion down. [duration], [crossfade] and [stillAt] are each a whole number of frames, so
 * every frame count and timestamp is exact.
 */
@Immutable
data class CaptureRecipe(
    val canvasSize: DpSize = DpSize(360.dp, 200.dp),
    val density: Float = 1f,
    val duration: Duration = 6.seconds,
    val fps: Int = 30,
    val stillAt: Duration = 1500.milliseconds,
    val loop: LoopMode = LoopMode.Crossfade,
    val crossfade: Duration = 1.seconds,
    val themes: CaptureThemes = CaptureThemes.LightAndDark,
    val budgetBytes: Long = 5_000_000,
) {
    init {
        require(canvasSize.width > 0.dp && canvasSize.height > 0.dp) { "The canvas needs an area, not $canvasSize" }
        require(density > 0f) { "Density must be positive, not $density" }
        require(duration.isPositive()) { "Duration must be positive, not $duration" }
        require(fps >= MIN_CAPTURE_FPS) { "Frame rate must be at least $MIN_CAPTURE_FPS, not $fps: the library caps one clock step at 0.1 s" }
        require(!stillAt.isNegative() && stillAt <= duration) { "The still frame at $stillAt is outside the $duration duration" }
        require(!crossfade.isNegative() && crossfade < duration) { "The $crossfade crossfade doesn't fit the $duration duration" }
        require(budgetBytes > 0) { "The byte budget must be positive, not $budgetBytes" }
        require(isWholeFrames(duration)) { "The $duration duration isn't a whole number of frames at $fps fps" }
        require(isWholeFrames(crossfade)) { "The $crossfade crossfade isn't a whole number of frames at $fps fps" }
        require(isWholeFrames(stillAt)) { "The still frame at $stillAt isn't on a frame at $fps fps" }
    }

    /** Frames in [duration]: the forward half of a boomerang, and the whole loop of a crossfade. */
    val frameCount: Int get() = framesIn(duration)

    /** Frames in [crossfade]. */
    val crossfadeFrames: Int get() = framesIn(crossfade)

    /** The index of the still frame in the forward recording. */
    val stillFrame: Int get() = framesIn(stillAt)

    private fun isWholeFrames(span: Duration): Boolean = span.inWholeNanoseconds * fps % NANOS_PER_SECOND == 0L

    private fun framesIn(span: Duration): Int = (span.inWholeNanoseconds * fps / NANOS_PER_SECOND).toInt()
}

/** The lowest capture frame rate; see [CaptureRecipe]. */
const val MIN_CAPTURE_FPS = 20

private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * How a capture closes its loop. A seamless cycle isn't an option: node drift is a sum of sines with
 * non-integer frequency ratios, so it never repeats.
 */
enum class LoopMode {
    /**
     * Renders past the duration by the recipe's crossfade length and blends that tail into the head.
     * For directional motion (wave travel, comets, a rotating brush), which would run backwards in a boomerang.
     */
    Crossfade,

    /**
     * Records the duration as the forward half and appends it mirrored, without repeating either end frame.
     * For morphs and scrolls, which read naturally backwards. Such a sample loops A→B→A live by itself, and its
     * duration covers only the A→B half, holds included.
     */
    Boomerang,

    /** Only the still frame, as a PNG. */
    Still,
}

/** The theme variants a capture renders: both, or only dark for a dark-only showcase. */
enum class CaptureThemes { LightAndDark, DarkOnly }
