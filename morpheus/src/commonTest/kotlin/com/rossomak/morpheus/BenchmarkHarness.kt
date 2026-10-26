@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.time.TimeSource

// What every benchmark here shares: the enable flag, a header naming the platform and its timer, batch
// timing for calls shorter than the timer's step, and GalleryLoop, which replays frames as the gallery
// runs them. A benchmark is a test class whose tests each run their body through benchmark(); see
// docs/benchmark.md for the command and how to compare a base against a change.

/** True when `MORPHEUS_BENCHMARK` is set and not empty, as on both platforms, so the benchmarks and reports run; otherwise they return at once. */
internal expect fun benchmarksEnabled(): Boolean

/** The platform the benchmarks run on, as the header prints it: the JDK, or the browser and whether the page is cross-origin isolated. */
internal expect fun benchmarkPlatform(): String

/**
 * Runs [body] when the benchmarks are on, after a header line naming [owner], its [case] when it has more
 * than one, the platform and the timer's measured step. Otherwise returns at once, after [owner]'s skip line
 * the first time, so a plain build says once per class that it skipped them. A benchmark that isn't the first
 * in its run warns: the JIT keeps what the earlier ones taught it, and on wasm that moves the timings, so
 * compare only numbers from benchmarks run alone, as `scripts/headless_benchmark.py` runs them.
 */
internal fun benchmark(owner: KClass<*>, case: String? = null, body: () -> Unit) {
    val className = owner.simpleName
    if (!benchmarksEnabled()) {
        if (skippedClasses.add(className)) println("$className skipped: MORPHEUS_BENCHMARK not set")
        return
    }
    println("${listOfNotNull(className, case).joinToString(", ")} on $platformLine")
    if (benchmarksBefore > 0) {
        println("warning: not run alone, $benchmarksBefore benchmark(s) ran before it in this run and may have moved its timings")
    }
    benchmarksBefore++
    body()
}

/** The header's platform and timer step, measured once per run. */
private val platformLine by lazy { "${benchmarkPlatform()}, timer step ${timerStepNanos().micros()} µs" }

/** How many benchmarks have run in this test run so far. */
private var benchmarksBefore = 0

/** The classes that have printed their skip line in this test run. */
private val skippedClasses = HashSet<String?>()

/**
 * The timer's step: the smallest nonzero difference between two reads of the monotonic clock. Readings
 * come in whole steps, so the smallest difference over many tries is one step.
 */
internal fun timerStepNanos(): Long {
    var smallest = Long.MAX_VALUE
    repeat(TIMER_STEP_TRIES) {
        val start = TimeSource.Monotonic.markNow()
        var elapsed: Long
        do {
            elapsed = start.elapsedNow().inWholeNanoseconds
        } while (elapsed == 0L)
        smallest = min(smallest, elapsed)
    }
    return smallest
}

/**
 * Times [call] over [samples] batches, after [warmUp] calls. Each batch runs [call] often enough to last
 * at least [MIN_BATCH_NANOS], so a call shorter than the timer's step still times; a call that long already
 * runs alone. Returns each batch's nanoseconds per call, sorted. Batching averages a spike away, so a
 * one-frame spike is timed per frame through [GalleryLoop] instead.
 */
internal fun timeCalls(warmUp: Int, samples: Int, call: () -> Unit): LongArray {
    repeat(warmUp) { call() }
    var batch = 1
    while (timeBatch(batch, call) < MIN_BATCH_NANOS) batch *= 2
    return LongArray(samples) { timeBatch(batch, call) / batch }.apply { sort() }
}

/** The nanoseconds [calls] calls of [call] take, run back to back. */
private fun timeBatch(calls: Int, call: () -> Unit): Long {
    val started = TimeSource.Monotonic.markNow()
    repeat(calls) { call() }
    return started.elapsedNow().inWholeNanoseconds
}

/** The canvas a [GalleryLoop] draws on: its size in pixels, the window's longer side in pixels, which sets the grid's cell size, and the device scale. */
internal data class BenchmarkCanvas(val width: Float, val height: Float, val referenceSize: Float, val pxPerDp: Float) {
    companion object {
        /** The benchmark recipe's canvas: 640×360 dp at device scale 2. The gallery gives a sample a window of its canvas's size, so the canvas sets the cell size. */
        val Recipe = BenchmarkCanvas(width = 1280f, height = 720f, referenceSize = 1280f, pxPerDp = 2f)
    }
}

/**
 * Replays frames as the gallery's modifier runs them on [canvas] at [fps], through the modifier's own
 * [MorpheusGraphHolder]: its layers and fields, kept across frames as the modifier keeps them, each frame's
 * `advance` phases timed through the gallery's own probe. [frame] takes the morph to draw; [still] gives
 * the one the modifier draws for a still spec.
 */
internal class GalleryLoop(private val canvas: BenchmarkCanvas = BenchmarkCanvas.Recipe, private val fps: Int = 60) {
    private val holder = MorpheusGraphHolder()
    private var frames = 0

    /** Advances [morph] by one frame; returns each of [ADVANCE_PHASES]' nanoseconds, every layer summed. */
    fun frame(morph: MorpheusGraphMorph): LongArray {
        val stats = MorpheusGraphFrameStats()
        val time = frames++ / fps.toFloat()
        val layers = holder.layersFor(morph, FILL)
        val fields = holder.fieldsFor(layers, canvas.referenceSize, canvas.pxPerDp)
        fields.forEachIndexed { index, field ->
            stats.probe.startLayer(layers[index].slotFromFront, field)
            field.advance(time, canvas.width, canvas.height, stats.probe)
            stats.probe.endLayer(field)
        }
        holder.endFrame()
        val slots = layers.map { it.slotFromFront }.distinct()
        return LongArray(ADVANCE_PHASES.size) { phase -> slots.sumOf { stats.slotRecord(it).phaseNanos[ADVANCE_PHASES[phase].ordinal] } }
    }

    companion object {
        /** The phases of a layer's `advance`, in the order [frame] returns them. */
        val ADVANCE_PHASES = listOf(FramePhase.Shape, FramePhase.Mesh, FramePhase.EdgeFades, FramePhase.AlphaSettle)

        /** The fill the layers are drawn with; `advance` doesn't read it. */
        val FILL = MorpheusGraphFill.Solid(Color.White)

        /** The morph the modifier draws for the still [spec]. */
        fun still(spec: MorpheusGraphSpec): MorpheusGraphMorph = MorpheusGraphMorph(spec, spec, 0f)

        /** [morph]'s layers. */
        fun morphLayers(morph: MorpheusGraphMorph): List<MorphLayer> = morphLayers(morph, FILL)
    }
}

/** These frames' mean, phase by phase: one frame of [GalleryLoop.ADVANCE_PHASES]' nanoseconds. */
internal fun List<LongArray>.meanFrame(): LongArray = LongArray(GalleryLoop.ADVANCE_PHASES.size) { phase -> sumOf { it[phase] } / size }

/** Each of [GalleryLoop.ADVANCE_PHASES]' median over these frames, as "shape 0.123 ms, mesh …". */
internal fun List<LongArray>.medianPhases(): String = describePhases { phase -> map { it[phase] }.sorted().let { it[it.size / 2] } }

/** Each of [GalleryLoop.ADVANCE_PHASES]' mean over these frames, as "shape 0.123 ms, mesh …". */
internal fun List<LongArray>.meanPhases(): String = meanFrame().let { mean -> describePhases { phase -> mean[phase] } }

/** Each of [GalleryLoop.ADVANCE_PHASES] with the nanoseconds [nanosOf] gives it, as "shape 0.123 ms, mesh …". */
private fun describePhases(nanosOf: (phase: Int) -> Long): String = GalleryLoop.ADVANCE_PHASES.indices.joinToString { phase ->
    "${GalleryLoop.ADVANCE_PHASES[phase].name.lowercase()} ${nanosOf(phase).millis()} ms"
}

/** Nanoseconds as milliseconds with three decimals, the same on every platform. */
internal fun Long.millis(): String = thousandths(NANOS_PER_MICRO * MICROS_PER_MILLI)

/** Nanoseconds as microseconds with three decimals. */
internal fun Long.micros(): String = thousandths(NANOS_PER_MICRO)

/** Nanoseconds in units of [unitNanos], rounded to thousandths and printed with three decimals. */
private fun Long.thousandths(unitNanos: Long): String {
    val step = unitNanos / THOUSAND
    val rounded = (this + step / 2) / step
    return "${rounded / THOUSAND}.${(rounded % THOUSAND).toString().padStart(THOUSANDTHS_DIGITS, '0')}"
}

private const val TIMER_STEP_TRIES = 1_000
private const val MIN_BATCH_NANOS = 2_000_000L
private const val NANOS_PER_MICRO = 1_000L
private const val MICROS_PER_MILLI = 1_000L
private const val THOUSAND = 1_000L
private const val THOUSANDTHS_DIGITS = 3
