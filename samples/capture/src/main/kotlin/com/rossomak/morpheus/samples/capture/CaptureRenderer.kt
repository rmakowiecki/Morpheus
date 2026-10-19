package com.rossomak.morpheus.samples.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rossomak.morpheus.samples.CaptureRecipe
import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.LoopMode
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A theme a capture renders in, and the suffix it gives a two-theme sample's files. */
enum class CaptureTheme(val dark: Boolean, val suffix: String) {
    Light(dark = false, suffix = "light"),
    Dark(dark = true, suffix = "dark"),
}

/** The themes a recipe declares, light first. */
val CaptureThemes.themes: List<CaptureTheme>
    get() = when (this) {
        CaptureThemes.LightAndDark -> listOf(CaptureTheme.Light, CaptureTheme.Dark)
        CaptureThemes.DarkOnly -> listOf(CaptureTheme.Dark)
    }

/** What a capture renders: the loop's [frames] in order, empty for [LoopMode.Still], and the [still] frame. */
class Capture(val frames: List<Frame>, val still: Frame)

/**
 * Renders [sample] in [theme] as [recipe] declares, at its default specs, on a virtual frame clock, so the same
 * input always gives the same pixels. Awaits the sample's preparation first.
 *
 * The scene matches the gallery's: a canvas-sized window reference, so the mesh density follows the canvas, a
 * lifecycle held in RESUMED, as the lib's renderer only advances while STARTED, and the sample theme. A warm-up
 * frame at time 0 starts every frame-clock reader; kept frame `i` is then at exactly `i / fps` seconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun renderCapture(sample: Sample, theme: CaptureTheme, recipe: CaptureRecipe = sample.recipe): Capture {
    // The lib's renderer switches to Dispatchers.Main.immediate; unconfined, its frame loop resumes inside
    // each render call instead of racing it.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    try {
        sample.prepare()
        val forward = renderForward(sample, theme, recipe, frameCount = forwardFrameCount(recipe))
        val still = forward[recipe.stillFrame]
        val frames = when (recipe.loop) {
            LoopMode.Crossfade -> crossfade(forward, recipe.frameCount, recipe.crossfadeFrames)
            LoopMode.Boomerang -> forward.subList(0, recipe.frameCount + 1) + forward.subList(1, recipe.frameCount).asReversed()
            LoopMode.Still -> emptyList()
        }
        return Capture(frames, still)
    } finally {
        Dispatchers.resetMain()
    }
}

/** How many frames the forward recording needs: the loop's and the still frame's. */
private fun forwardFrameCount(recipe: CaptureRecipe): Int {
    val loopFrames = when (recipe.loop) {
        LoopMode.Crossfade -> recipe.frameCount + recipe.crossfadeFrames
        // Both end frames: 0 through N.
        LoopMode.Boomerang -> recipe.frameCount + 1
        LoopMode.Still -> 0
    }
    return maxOf(loopFrames, recipe.stillFrame + 1)
}

/**
 * The first [loopFrames] of [forward], with the [crossfadeFrames] after them blended into the head: frame `k`
 * of the head runs from the tail's frame `k` to its own over the crossfade. Frame 0 is then the frame that
 * follows the last one, so the loop has no seam, and the head is its own again by frame [crossfadeFrames].
 */
private fun crossfade(forward: List<Frame>, loopFrames: Int, crossfadeFrames: Int): List<Frame> = List(loopFrames) { index ->
    if (index < crossfadeFrames) blend(forward[loopFrames + index], forward[index], step = index, steps = crossfadeFrames) else forward[index]
}

@OptIn(ExperimentalComposeUiApi::class)
private fun renderForward(sample: Sample, theme: CaptureTheme, recipe: CaptureRecipe, frameCount: Int): List<Frame> {
    val density = Density(recipe.density)
    val canvasPixels = with(density) { IntSize(recipe.canvasSize.width.roundToPx(), recipe.canvasSize.height.roundToPx()) }
    val lifecycleOwner = ResumedLifecycleOwner()
    val specs = sample.defaultSpecs
    val scene = ImageComposeScene(width = canvasPixels.width, height = canvasPixels.height, density = density) {
        CompositionLocalProvider(
            LocalWindowInfo provides rememberCanvasWindowInfo(canvasPixels, recipe.canvasSize),
            LocalLifecycleOwner provides lifecycleOwner,
        ) {
            SampleTheme(dark = theme.dark, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { sample.content(specs) }
            }
        }
    }
    try {
        // Not kept: it starts the frame-clock readers at time 0, so kept frame 0 is too.
        scene.render(0L).close()
        return List(frameCount) { index -> scene.render(frameNanos(index, recipe.fps)).use { it.toFrame() } }
    } finally {
        scene.close()
    }
}

private fun frameNanos(index: Int, fps: Int): Long = index * NANOS_PER_SECOND / fps

private fun org.jetbrains.skia.Image.toFrame(): Frame {
    val bitmap = toComposeImageBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    return Frame(bitmap.width, bitmap.height, pixels)
}

/** The scene's window info, except that its container is the canvas, as the gallery provides around its stage. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberCanvasWindowInfo(canvasPixels: IntSize, canvasSize: DpSize): WindowInfo {
    val window = LocalWindowInfo.current
    return remember(window, canvasPixels, canvasSize) {
        object : WindowInfo {
            override val isWindowFocused: Boolean get() = window.isWindowFocused
            override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers
            override val containerSize: IntSize get() = canvasPixels
            override val containerDpSize: DpSize get() = canvasSize
        }
    }
}

/** Always RESUMED; the capture never pauses. */
private class ResumedLifecycleOwner : LifecycleOwner {
    // Unsafe: the capture runs off any UI thread, and nothing else touches this registry.
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

private const val NANOS_PER_SECOND = 1_000_000_000L
