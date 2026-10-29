@file:OptIn(ExperimentalMorpheusGraphApi::class, ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)

package com.rossomak.morpheus.samples.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.LocalMorpheusGraphFrameStats
import com.rossomak.morpheus.MorpheusGraphFrameStats
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.CycleControl
import com.rossomak.morpheus.samples.LocalCycleControl
import com.rossomak.morpheus.samples.LocalSampleTileStats
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTheme
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.SampleTileStats
import com.rossomak.morpheus.samples.TileSize
import com.rossomak.morpheus.samples.catalog.PresetTourSample
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Samples and helpers in a scene of the test's own, where the test can change what they are given between frames. */
class SampleSceneTest {
    @Test
    fun `tuning a preset leaves the tour's clock running`() {
        val control = CycleControl()
        var specs by mutableStateOf(MorpheusGraphPresets.all)
        inScene(SCENE_SIZE, { CompositionLocalProvider(LocalCycleControl provides control) { PresetTourSample(specs = specs) } }) { render ->
            render(0)
            render(1.seconds.inWholeNanoseconds)
            control.time shouldBe 1.seconds
            // Tuning the preset on stage, and then one that isn't.
            specs = specs.mapIndexed { index, spec -> if (index == 0) spec.copy(seed = spec.seed + 1) else spec }
            render(1500.milliseconds.inWholeNanoseconds)
            control.time shouldBe 1500.milliseconds
            specs = specs.mapIndexed { index, spec -> if (index == 3) spec.copy(seed = spec.seed + 1) else spec }
            render(2.seconds.inWholeNanoseconds)
            control.time shouldBe 2.seconds
        }
    }

    @Test
    fun `each tile of a grid reports into the stats its host gives for its label`() {
        val tileStats = HashMap<String, MorpheusGraphFrameStats>()
        val pageStats = MorpheusGraphFrameStats()
        val content = @Composable {
            CompositionLocalProvider(
                LocalSampleTileStats provides SampleTileStats { label -> tileStats.getOrPut(label) { MorpheusGraphFrameStats() } },
                LocalMorpheusGraphFrameStats provides pageStats,
            ) { TwoTileGrid() }
        }
        inScene(SCENE_SIZE, content) { render ->
            render(0)
            render(FRAME_NANOS)
        }
        tileStats.keys shouldBe setOf("First", "Second")
        for (stats in tileStats.values) {
            stats.recordFrame(PUBLISH_INTERVAL_NANOS)
            stats.breakdown.backgroundCount shouldBe 1
            stats.breakdown.total.nodes shouldBeGreaterThan 0f
        }
        // Tiles that report on their own add nothing to the page's stats.
        pageStats.recordFrame(PUBLISH_INTERVAL_NANOS)
        pageStats.breakdown.backgroundCount shouldBe 0
    }

    @Test
    fun `a grid without a host reports nothing, not even into the page's stats`() {
        val pageStats = MorpheusGraphFrameStats()
        inScene(SCENE_SIZE, { CompositionLocalProvider(LocalMorpheusGraphFrameStats provides pageStats) { TwoTileGrid() } }) { render ->
            render(0)
            render(FRAME_NANOS)
        }
        pageStats.recordFrame(PUBLISH_INTERVAL_NANOS)
        pageStats.breakdown.backgroundCount shouldBe 0
    }

    @Test
    fun `tiles of one row share a size`() {
        val failure = runCatching {
            inScene(SCENE_SIZE, { SampleGrid(columns = 2, tiles = listOf(SampleTile("A") {}, SampleTile("B", TileSize.FixedHeight(40.dp)) {})) }) { render -> render(0) }
        }.exceptionOrNull()
        (failure?.message ?: "").contains("share a size") shouldBe true
    }

    @Test
    fun `a fixed-height tile keeps its height while the others share the rest`() {
        // Only the stats-less layout is checked: the fixed tile's surface is 40 dp tall at density 1.
        val heights = HashMap<String, Int>()
        val tiles = listOf(
            SampleTile("Bar", TileSize.FixedHeight(40.dp)) { Box(Modifier.fillMaxSize().onSizeChanged { heights["Bar"] = it.height }) },
            SampleTile("Rest") { Box(Modifier.fillMaxSize().onSizeChanged { heights["Rest"] = it.height }) },
        )
        inScene(SCENE_SIZE, { SampleGrid(columns = 1, tiles = tiles) }) { render ->
            render(0)
            render(FRAME_NANOS)
        }
        heights["Bar"] shouldBe 40
        heights.getValue("Rest") shouldBeGreaterThan 40
    }

    @Composable
    private fun TwoTileGrid() {
        val spec: MorpheusGraphSpec = MorpheusGraphPresets.baseline
        SampleGrid(
            columns = 2,
            tiles = listOf(
                SampleTile("First") { Box(Modifier.fillMaxSize().morpheusGraphBackground(spec)) },
                SampleTile("Second") { Box(Modifier.fillMaxSize().morpheusGraphBackground(spec)) },
            ),
        )
    }

    /** Runs [body] with a function that renders the scene's frame at the given nanoseconds, with the sample theme and a started lifecycle. */
    private fun inScene(size: DpSize, content: @Composable () -> Unit, body: (render: (Long) -> Unit) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val pixels = IntSize(size.width.value.toInt(), size.height.value.toInt())
            val lifecycleOwner = StartedLifecycleOwner()
            val scene = ImageComposeScene(width = pixels.width, height = pixels.height) {
                CompositionLocalProvider(
                    LocalWindowInfo provides windowInfoOf(pixels, size),
                    LocalLifecycleOwner provides lifecycleOwner,
                ) {
                    SampleTheme(dark = true, modifier = Modifier.fillMaxSize(), content = content)
                }
            }
            try {
                body { nanos -> scene.render(nanos).close() }
            } finally {
                scene.close()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun windowInfoOf(pixels: IntSize, size: DpSize) = object : WindowInfo {
        override val isWindowFocused: Boolean get() = true
        override val keyboardModifiers: PointerKeyboardModifiers get() = PointerKeyboardModifiers()
        override val containerSize: IntSize get() = pixels
        override val containerDpSize: DpSize get() = size
    }

    private class StartedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        val SCENE_SIZE = DpSize(320.dp, 180.dp)
        const val FRAME_NANOS = 33_000_000L
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L
    }
}
