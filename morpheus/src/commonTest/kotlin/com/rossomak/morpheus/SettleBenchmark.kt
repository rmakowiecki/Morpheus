@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Times the settles at a morph's start: both ends of the "Pills to ribbons" showcase and, on the front
 * layer, the comet transit mesh, all settled in one frame. The gallery's half-second averages dilute that
 * spike, so this measures it alone. The front layer is timed again without comets, so its two ends alone
 * show too, and the transit mesh as the difference. Also times the living-Delaunay preset's mesh phase, which settles
 * every frame, and replays the showcase's loop as the gallery runs it. Printed, not asserted; it only runs with
 * `MORPHEUS_BENCHMARK` set, on the JVM and in headless Chrome (the browser's timer ticks at about 100 µs):
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*SettleBenchmark*' --rerun :morpheus:wasmJsBrowserTest --tests '*SettleBenchmark*' --rerun -i`
 */
class SettleBenchmark {

    @Test
    fun `settling the showcase's morph meshes`() {
        if (!benchmarksEnabled()) return
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, fraction = MID_FRACTION)
        for (layer in morphLayers(morph, FILL)) timeMorphMesh(layer, "")
        val withoutComets = MorpheusGraphMorph(heroPills.withoutFrontComets(), heroRibbons.withoutFrontComets(), fraction = MID_FRACTION)
        timeMorphMesh(morphLayers(withoutComets, FILL).first { it.slotFromFront == 0 }, ", without comets")
    }

    private fun MorpheusGraphSpec.withoutFrontComets() = copy(layers = layers.dropLast(1) + layers.last().copy(cometStrength = 0f))

    /** Times rebuilding [layer]'s morph meshes; [label] marks a variant in the printed line. */
    private fun timeMorphMesh(layer: MorphLayer, label: String) {
        val field = LayerField(layer.spec, layer.seed, max(WIDTH, HEIGHT), PX_PER_DP)
        field.shape = layer.shape
        field.advance(0f, WIDTH, HEIGHT)
        val shape = layer.shape as LayerShape.Morph
        repeat(WARM_UP_RUNS) { field.rebuildMorphMesh(shape, WIDTH, HEIGHT) }
        val nanos = LongArray(MEASURED_RUNS) {
            val started = TimeSource.Monotonic.markNow()
            field.rebuildMorphMesh(shape, WIDTH, HEIGHT)
            started.elapsedNow().inWholeNanoseconds
        }.apply { sort() }
        val settlesBefore = field.settleCount
        field.rebuildMorphMesh(shape, WIDTH, HEIGHT)
        val meshes = if (field.settleCount - settlesBefore > END_SETTLES) "two ends and the transit mesh" else "two ends"
        println(
            "slot ${layer.slotFromFront} (density ${layer.spec.density.toInt()}$label, $meshes): " +
                "median ${nanos[nanos.size / 2].millis()} ms, fastest ${nanos.first().millis()} ms over $MEASURED_RUNS runs",
        )
    }

    /** The living-Delaunay preset settles every frame, so its whole mesh phase is settle work; timed per frame through the gallery's probe. */
    @Test
    fun `the living-Delaunay preset's mesh phase`() {
        if (!benchmarksEnabled()) return
        val spec = MorpheusGraphPresets.livingDelaunay
        spec.layers.forEachIndexed { index, layer ->
            val field = LayerField(layer, spec.seed, max(WIDTH, HEIGHT), PX_PER_DP)
            repeat(WARM_UP_FRAMES) { frame -> field.advance(frame * FRAME_SECONDS, WIDTH, HEIGHT) }
            val stats = MorpheusGraphFrameStats()
            val meshNanos = stats.slotRecord(index).phaseNanos
            val nanos = LongArray(MEASURED_FRAMES) { measured ->
                val before = meshNanos[FramePhase.Mesh.ordinal]
                stats.probe.startLayer(index, field)
                field.advance((WARM_UP_FRAMES + measured) * FRAME_SECONDS, WIDTH, HEIGHT, stats.probe)
                stats.probe.endLayer(field)
                meshNanos[FramePhase.Mesh.ordinal] - before
            }
            val mean = nanos.sum() / MEASURED_FRAMES
            nanos.sort()
            println("living-Delaunay layer $index: mesh median ${nanos[nanos.size / 2].millis()} ms, mean ${mean.millis()} ms per frame over $MEASURED_FRAMES frames")
        }
    }

    /**
     * The showcase's loop as the gallery's benchmark recipe runs it: a 640×360 dp canvas at device scale 2 in a
     * 1440 dp wide window, one field per mesh kept across frames as the modifier keeps them, at a steady 60 fps.
     * Prints each `advance` phase, every layer summed, on the frame where the morph starts or ends and as a mean
     * over the half second from there, the window the gallery's readout averages over. Medians over the loops.
     */
    @Test
    fun `the showcase's loop as the gallery runs it`() {
        if (!benchmarksEnabled()) return
        val fields = HashMap<LayerFamilyKey, LayerField>()
        val spikes = HashMap<String, MutableList<LongArray>>()
        val windows = HashMap<String, MutableList<LongArray>>()
        for (loop in 0 until WARM_UP_LOOPS + MEASURED_LOOPS) {
            val frames = Array(LOOP_FRAMES) { frame -> galleryFrame(fields, loop * LOOP_FRAMES + frame, fractionAt(frame * FRAME_SECONDS)) }
            if (loop < WARM_UP_LOOPS) continue
            val fractions = FloatArray(LOOP_FRAMES) { frame -> fractionAt(frame * FRAME_SECONDS) }
            for ((event, frame) in loopEvents(fractions)) {
                spikes.getOrPut(event) { mutableListOf() } += frames[frame]
                val window = frame until minOf(frame + WINDOW_FRAMES, LOOP_FRAMES)
                windows.getOrPut(event) { mutableListOf() } += LongArray(ADVANCE_PHASES.size) { phase -> window.sumOf { frames[it][phase] } / WINDOW_FRAMES }
            }
        }
        for (event in spikes.keys.sorted()) {
            println("showcase loop, $event: frame ${spikes.getValue(event).medianPhases()}; half second from there, per frame ${windows.getValue(event).medianPhases()}")
        }
    }

    /** Advances every mesh of the showcase's morph at [fraction] by one gallery frame; returns each `advance` phase's nanoseconds, layers summed. */
    private fun galleryFrame(fields: HashMap<LayerFamilyKey, LayerField>, frame: Int, fraction: Float): LongArray {
        val stats = MorpheusGraphFrameStats()
        val layers = morphLayers(MorpheusGraphMorph(heroPills, heroRibbons, fraction), FILL)
        for (layer in layers) {
            val field = fields.getOrPut(LayerFamilyKey(layer.seed, layer.spec)) { LayerField(layer.spec, layer.seed, GALLERY_REFERENCE, PX_PER_DP) }
            field.spec = layer.spec
            field.shape = layer.shape
            stats.probe.startLayer(layer.slotFromFront, field)
            field.advance(frame * FRAME_SECONDS, GALLERY_WIDTH, GALLERY_HEIGHT, stats.probe)
            stats.probe.endLayer(field)
        }
        val slots = layers.map { it.slotFromFront }.distinct()
        return LongArray(ADVANCE_PHASES.size) { phase -> slots.sumOf { stats.slotRecord(it).phaseNanos[ADVANCE_PHASES[phase].ordinal] } }
    }

    /** The frames where the morph leaves or reaches either end, as the gallery's showcase seeks it back and forth. */
    private fun loopEvents(fractions: FloatArray): Map<String, Int> {
        val frames = 1 until fractions.size
        return mapOf(
            "1 forward start" to frames.first { fractions[it - 1] == 0f && fractions[it] > 0f },
            "2 forward end" to frames.first { fractions[it - 1] < 1f && fractions[it] == 1f },
            "3 reverse start" to frames.first { fractions[it - 1] == 1f && fractions[it] < 1f },
            "4 reverse end" to frames.first { fractions[it - 1] > 0f && fractions[it] == 0f },
        )
    }

    /** The showcase sample's own fraction at [time] into its loop: hold, morph, hold, then the same mirrored. */
    private fun fractionAt(time: Float): Float {
        val forward = if (time <= HALF_LOOP_SECONDS) time else HALF_LOOP_SECONDS * 2 - time
        return FastOutSlowInEasing.transform(((forward - HOLD_SECONDS) / MORPH_SECONDS).coerceIn(0f, 1f))
    }

    private fun List<LongArray>.medianPhases(): String = ADVANCE_PHASES.indices.joinToString { phase ->
        val sorted = map { it[phase] }.sorted()
        "${ADVANCE_PHASES[phase].name.lowercase()} ${sorted[sorted.size / 2].millis()} ms"
    }

    private fun Long.millis(): String {
        val micros = (this + NANOS_PER_MICRO / 2) / NANOS_PER_MICRO
        return "${micros / MICROS_PER_MILLI}.${(micros % MICROS_PER_MILLI).toString().padStart(MILLI_DIGITS, '0')}"
    }

    /** What keeps a field across the loop, as the modifier keys them. */
    private data class LayerFamilyKey(val seed: Int, val density: Float, val jitter: Float, val edgeRule: EdgeRule, val strictTriangles: Boolean) {
        constructor(seed: Int, spec: MorpheusGraphLayerSpec) : this(seed, spec.density, spec.jitter, spec.edgeRule, spec.strictTriangles)
    }

    private companion object {
        /** A 16:9 canvas at 2x scale. */
        const val WIDTH = 640f
        const val HEIGHT = 360f
        const val PX_PER_DP = 2f
        const val MID_FRACTION = 0.5f
        const val END_SETTLES = 2
        const val WARM_UP_RUNS = 200
        const val MEASURED_RUNS = 200
        const val FRAME_SECONDS = 1f / 60f
        const val WARM_UP_FRAMES = 600
        const val MEASURED_FRAMES = 3_000
        val FILL = MorpheusGraphFill.Solid(Color.White)

        /** The benchmark recipe's canvas, 640×360 dp at device scale 2, and its window's longer side, 1440 dp. */
        const val GALLERY_WIDTH = 1280f
        const val GALLERY_HEIGHT = 720f
        const val GALLERY_REFERENCE = 2880f

        /** The showcase sample's loop: hold the pills, morph, hold the ribbons, then back. */
        const val HOLD_SECONDS = 0.75f
        const val MORPH_SECONDS = 3.5f
        const val HALF_LOOP_SECONDS = HOLD_SECONDS + MORPH_SECONDS + HOLD_SECONDS
        const val LOOP_FRAMES = 600
        const val WINDOW_FRAMES = 30
        const val WARM_UP_LOOPS = 1
        const val MEASURED_LOOPS = 7
        val ADVANCE_PHASES = listOf(FramePhase.Shape, FramePhase.Mesh, FramePhase.EdgeFades, FramePhase.AlphaSettle)

        const val NANOS_PER_MICRO = 1_000L
        const val MICROS_PER_MILLI = 1_000L
        const val MILLI_DIGITS = 3
    }
}
