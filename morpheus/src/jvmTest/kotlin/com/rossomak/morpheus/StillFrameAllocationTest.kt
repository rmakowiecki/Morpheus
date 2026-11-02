@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sun.management.ThreadMXBean
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import java.lang.management.ManagementFactory
import java.util.IdentityHashMap
import kotlin.test.Test

/**
 * A still solid or gradient frame on a fixed triangulation allocates nothing before drawing (see [prepareFrame]),
 * and a batch whose size held passes Skia the same array. The morph is read from a still [MorpheusGraphMorphState],
 * as the modifier reads it; bytes come from [ThreadMXBean.getCurrentThreadAllocatedBytes].
 *
 * Runs only in `jvmAllocationTest`, with escape analysis off: Kotlin/Wasm has none, and HotSpot's would hide, once
 * the frame's methods are compiled, allocations the browser still makes. Boxed small integers come from the JVM's
 * cache and so measure 0 here, though they allocate on the web.
 */
class StillFrameAllocationTest {

    @Test
    fun `a still frame allocates nothing before drawing`() {
        val failures = ArrayList<String>()
        for (case in CASES) {
            val frames = StillFrames(case)
            repeat(WARM_UP_FRAMES) { frames.prepare() }
            // The JVM now and then charges a few bytes of its own to the thread, so one clean window of several passes.
            var bytes = allocatedBy { repeat(MEASURED_FRAMES) { frames.prepare() } }
            var windows = 1
            while (bytes != 0L && windows++ < MEASURED_WINDOWS) bytes = allocatedBy { repeat(MEASURED_FRAMES) { frames.prepare() } }
            println("${case.name}: prepareFrame allocates ${bytes / MEASURED_FRAMES} bytes a frame")
            if (bytes != 0L) failures += "${case.name}: $bytes bytes over $MEASURED_FRAMES frames; one frame, step by step: ${frames.breakdown()}"
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `a batch the same size as last frame passes Skia the same array`() {
        val failures = ArrayList<String>()
        for (case in CASES) {
            val frames = StillFrames(case)
            repeat(WARM_UP_FRAMES) { frames.prepareAndDraw() }
            var resized = 0
            var batches = 0
            val drawBytes = (0 until MEASURED_FRAMES).sumOf {
                val before = frames.drawnBatches()
                frames.prepare()
                val bytes = allocatedBy { frames.draw() }
                for ((buffer, drawn) in frames.drawnBatches()) {
                    val (size, array) = before[buffer] ?: continue
                    batches++
                    if (size != drawn.first) {
                        resized++
                    } else if (array !== drawn.second) {
                        failures += "${case.name}: a batch of $size floats got a new array"
                    }
                }
                bytes
            }
            // Edges change alpha level and dots alpha bucket as the frame moves on, so some batches change size.
            println(
                "${case.name}: draw allocates ${drawBytes / MEASURED_FRAMES} bytes a frame, Skiko's and the test canvas's included; " +
                    "${resized * 100 / maxOf(batches, 1)}% of batches changed size from one frame to the next",
            )
        }
        withClue(failures.distinct().joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `the other edge rules' still frames, printed`() {
        val solid = MorpheusGraphFill.Solid(Color.White)
        val cases = listOf(Case("baseline (Proximity)", MorpheusGraphPresets.baseline, solid), Case("livingDelaunay", MorpheusGraphPresets.livingDelaunay, solid))
        for (case in cases) {
            val frames = StillFrames(case)
            repeat(WARM_UP_FRAMES) { frames.prepare() }
            val bytes = allocatedBy { repeat(MEASURED_FRAMES) { frames.prepare() } }
            println("${case.name}: prepareFrame allocates ${bytes / MEASURED_FRAMES} bytes a frame (not asserted); one frame: ${frames.breakdown()}")
        }
    }

    private class Case(val name: String, val spec: MorpheusGraphSpec, val fill: MorpheusGraphFill)

    /** [case]'s spec held still on the recipe canvas, one frame at a time, 60 a second, as the modifier draws it. */
    private class StillFrames(private val case: Case) {
        private val canvas = BenchmarkCanvas.Recipe
        private val size = canvas.size
        private val state = MorpheusGraphMorphState(case.spec)
        private val holder = MorpheusGraphHolder()
        private val drawScope = CanvasDrawScope()
        private val bitmapCanvas = Canvas(ImageBitmap(canvas.width.toInt(), canvas.height.toInt()))
        private val density = Density(canvas.pxPerDp)
        private var frame = 0
        private var time = 0f

        fun prepare() {
            time = frame++ / FPS
            holder.prepareFrame(state.morph, case.fill, canvas, time)
        }

        fun draw() {
            drawScope.draw(density, LayoutDirection.Ltr, bitmapCanvas, size) {
                val fields = holder.fields
                for (index in fields.indices) fields[index].draw(this, holder.layerFill(index), holder.halos(index), time, showViolations = false)
            }
        }

        fun prepareAndDraw() {
            prepare()
            draw()
        }

        /** Every batch the last [draw] drew, with its size and the array it passed Skia. */
        fun drawnBatches(): Map<PointBuffer, Pair<Int, FloatArray>> =
            holder.fields.flatMap { field -> field.pointBatches.nonEmptyBatches() }.associateWithTo(IdentityHashMap()) { it.size to it.lastExactFloats }

        /** One more frame, each step of [prepare] measured on its own: what it allocated, by step. */
        fun breakdown(): String {
            time = frame++ / FPS
            lateinit var morph: MorpheusGraphMorph
            val getter = allocatedBy { morph = state.morph }
            val start = allocatedBy { holder.startFrame(morph, case.fill, canvas.referenceSize, canvas.pxPerDp) }
            val probe = AllocationProbe()
            val layers = allocatedBy { for (index in holder.fields.indices) holder.prepareLayer(index, time, size, probe) }
            val phases = FramePhase.entries.filter { probe.bytes[it.ordinal] != 0L }.joinToString { "$it ${probe.bytes[it.ordinal]}" }
            return "morph getter $getter, startFrame $start, prepareLayer $layers of which [$phases]"
        }
    }

    /** Charges the bytes allocated between the frame's phase boundaries to each phase, rather than the time. */
    private class AllocationProbe : LayerProbe(MorpheusGraphFrameStats()) {
        val bytes = LongArray(FramePhase.entries.size)
        private var mark = 0L

        override fun startLayer(slotFromFront: Int, field: LayerField) {
            mark = allocatedBytes()
        }

        override fun lap(phase: FramePhase) {
            val now = allocatedBytes()
            bytes[phase.ordinal] += now - mark
            mark = allocatedBytes()
        }

        override fun skipLap() {
            mark = allocatedBytes()
        }

        override fun endLayer(field: LayerField) = Unit
    }

    private companion object {
        const val WARM_UP_FRAMES = 300
        const val MEASURED_FRAMES = 100
        const val MEASURED_WINDOWS = 3
        const val FPS = 60f

        val GRADIENT = MorpheusGraphFill.Linear(
            stops = listOf(GradientStop(0f, Color(0xFF6A5AE0)), GradientStop(0.6f, Color(0xFF2EC4B6)), GradientStop(1f, Color(0xFFFF9F1C))),
            angleDegrees = 30f,
        )
        val CASES = listOf(
            Case("ribbonMesh solid", MorpheusGraphPresets.ribbonMesh, MorpheusGraphFill.Solid(Color.White)),
            Case("ribbonMesh gradient", MorpheusGraphPresets.ribbonMesh, GRADIENT),
            Case("heroPills solid", heroPills, MorpheusGraphFill.Solid(Color.White)),
            Case("heroPills gradient", heroPills, GRADIENT),
        )

        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes

        inline fun allocatedBy(block: () -> Unit): Long {
            val before = allocatedBytes()
            block()
            return allocatedBytes() - before
        }
    }
}
