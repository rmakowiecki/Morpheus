@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.TimeSource

private const val NANOS_PER_MILLI = 1_000_000f
private const val PUBLISH_INTERVAL_NANOS = 500_000_000L

/**
 * Average frame interval and graph draw time, published twice a second so the readout doesn't
 * recompose every frame. Draw time is CPU time spent recording every graph on the page into the
 * display list, summed per frame — a relative cost signal between presets, not a GPU measurement.
 *
 * Alongside them, [breakdown] splits the graphs' time and work per spec layer. Every reading is an
 * average per frame over the half second it was published for; the host feeds the frames through
 * [recordFrame], the renderer feeds everything else.
 *
 * The debug switches ([skipEdges], [skipHalos], [skipDots], [skipFillRecolor], [showViolations], [cometDebug]) are read
 * by every graph that reports here, every frame. A skipped step records no time and no draw calls, so
 * its raster and GPU cost shows as the change it makes to [frameMillis]. Flipping any of them, like
 * [reset], starts a fresh window.
 */
@ExperimentalMorpheusGraphApi
@Stable
public class MorpheusGraphFrameStats {
    public var frameMillis: Float by mutableFloatStateOf(0f)
        private set
    public var drawMillis: Float by mutableFloatStateOf(0f)
        private set

    /** The last published window's breakdown, per spec layer and in total. */
    public var breakdown: MorpheusGraphFrameBreakdown by mutableStateOf(MorpheusGraphFrameBreakdown.Empty)
        private set

    /** Draws no edges. */
    public var skipEdges: Boolean by ResettingSwitch(false)

    /** Draws no halos. */
    public var skipHalos: Boolean by ResettingSwitch(false)

    /** Draws no dots. */
    public var skipDots: Boolean by ResettingSwitch(false)

    /** Skips a gradient or custom fill's recolor, so each such layer shows in its mask color, white. */
    public var skipFillRecolor: Boolean by ResettingSwitch(false)

    /** Rings nodes that break the connectivity or alpha rules, as the diagnostics overloads' own argument does. */
    public var showViolations: Boolean by ResettingSwitch(false)

    /** The comet overlays each running morph draws, as the diagnostics overloads' own argument does. */
    public var cometDebug: MorpheusGraphCometDebug by ResettingSwitch(MorpheusGraphCometDebug.None)

    /** True while any debug switch is on, the violations and comet overlays included. */
    public val anySwitchOn: Boolean
        get() = skipEdges || skipHalos || skipDots || skipFillRecolor || showViolations || cometDebug.any

    /**
     * True from a [reset] until the next window is published: [frameMillis], [drawMillis] and [breakdown]
     * then still show the setup before it.
     */
    public var isStale: Boolean by mutableStateOf(false)
        private set

    private var intervalNanosSum = 0L
    private var drawNanosSum = 0L
    private var frames = 0
    private val slots = HashMap<Int, SlotRecord>()
    private val backgrounds = HashSet<Int>()

    /** Times one layer at a time for the renderer; one is enough, as graphs draw one after another. */
    internal val probe = LayerProbe(this)

    public fun recordDraw(nanos: Long) {
        drawNanosSum += nanos
    }

    public fun recordFrame(intervalNanos: Long) {
        intervalNanosSum += intervalNanos
        frames++
        if (intervalNanosSum >= PUBLISH_INTERVAL_NANOS) {
            frameMillis = intervalNanosSum / frames / NANOS_PER_MILLI
            drawMillis = drawNanosSum / frames / NANOS_PER_MILLI
            breakdown = publishBreakdown()
            isStale = false
            clearWindow()
        }
    }

    /** Discards the current window, so the next published reading starts clean. The last published values stay. */
    public fun reset() {
        clearWindow()
        isStale = true
    }

    private fun clearWindow() {
        intervalNanosSum = 0L
        drawNanosSum = 0L
        frames = 0
        slots.clear()
        backgrounds.clear()
    }

    /**
     * Notes that the background with [backgroundId] starts drawing, so the breakdown can tell one background
     * from several, and counts its meshes per spec layer afresh. Only the id is kept, so a background that goes
     * away isn't held on to.
     */
    internal fun recordBackground(backgroundId: Int) {
        backgrounds += backgroundId
        slots.values.forEach { it.startDraw() }
    }

    internal fun slotRecord(slotFromFront: Int): SlotRecord = slots.getOrPut(slotFromFront) { SlotRecord() }

    private fun publishBreakdown(): MorpheusGraphFrameBreakdown {
        val layers = slots.entries.sortedByDescending { it.key }.map { (slot, record) -> record.toBreakdown(slot, frames) }
        return MorpheusGraphFrameBreakdown(
            total = MorpheusGraphLayerBreakdown.sumOf(layers),
            layers = layers,
            backgroundCount = backgrounds.size,
        )
    }

    /** A switch kept as Compose state, which starts a fresh window whenever it flips. */
    private inner class ResettingSwitch<T>(initial: T) : ReadWriteProperty<Any?, T> {
        private val state: MutableState<T> = mutableStateOf(initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            reset()
        }
    }
}

/**
 * The stats every `morpheusGraphBackground` below reports into when none is passed to it explicitly, so a
 * host can time graphs it doesn't own. Null, the default, means no timing work at all.
 */
@ExperimentalMorpheusGraphApi
public val LocalMorpheusGraphFrameStats: ProvidableCompositionLocal<MorpheusGraphFrameStats?> = staticCompositionLocalOf { null }

/**
 * One published window of [MorpheusGraphFrameStats]: [layers] has one entry per spec layer, back to front,
 * and [total] sums them. [backgroundCount] is how many backgrounds reported into the window; several sum
 * into the same spec layers.
 */
@ExperimentalMorpheusGraphApi
@Immutable
public class MorpheusGraphFrameBreakdown internal constructor(
    public val total: MorpheusGraphLayerBreakdown,
    public val layers: List<MorpheusGraphLayerBreakdown>,
    public val backgroundCount: Int,
) {
    internal companion object {
        val Empty = MorpheusGraphFrameBreakdown(MorpheusGraphLayerBreakdown.sumOf(emptyList()), emptyList(), backgroundCount = 0)
    }
}

/**
 * One spec layer's share of a window, or every layer's in a total, where [slotFromFront] is null. Two meshes
 * dissolving into each other draw one spec layer and count as one, with [meshes] at 2.
 *
 * Times are milliseconds per frame:
 * - [shapeMillis]: node positions, presences and comets;
 * - [meshMillis]: settling the topology, repairs and a morph's mesh builds;
 * - [edgeFadesMillis]: fading edges in and out, and a morph's edge weights;
 * - [alphaSettleMillis]: the dot and edge alpha rules;
 * - [fillMillis]: resolving the layer's fill, plus a gradient or custom fill's recolor;
 * - [edgesMillis], [halosMillis], [dotsMillis]: recording each element type;
 * - [compositeMillis]: the offscreen layer's save and restore.
 *
 * Counts are per frame too: the [nodes] in the pool, the [visibleNodes], the [drawnEdges], [drawnHalos] and [drawnDots],
 * and the [drawCalls]. A draw call is any painting `DrawScope` call (a line, circle or rect) or one batch of edges
 * or dots; an offscreen layer's save and restore count as one, and transforms and clips don't count. [settles]
 * (topology settles) and [morphMeshes] (a morph's mesh builds) are totals over the window, and [meshes] is
 * the most fields that drew the spec layer in any one background's frame. In a total, [meshes] sums the layers'.
 */
@ExperimentalMorpheusGraphApi
@Immutable
@Suppress("LongParameterList") // One value per phase and count, as the doc lists them.
public class MorpheusGraphLayerBreakdown internal constructor(
    public val slotFromFront: Int?,
    public val shapeMillis: Float,
    public val meshMillis: Float,
    public val edgeFadesMillis: Float,
    public val alphaSettleMillis: Float,
    public val fillMillis: Float,
    public val edgesMillis: Float,
    public val halosMillis: Float,
    public val dotsMillis: Float,
    public val compositeMillis: Float,
    public val nodes: Float,
    public val visibleNodes: Float,
    public val drawnEdges: Float,
    public val drawnHalos: Float,
    public val drawnDots: Float,
    public val drawCalls: Float,
    public val settles: Int,
    public val morphMeshes: Int,
    public val meshes: Int,
) {
    /** Every phase together. */
    public val totalMillis: Float
        get() = shapeMillis + meshMillis + edgeFadesMillis + alphaSettleMillis + fillMillis + edgesMillis + halosMillis + dotsMillis + compositeMillis

    internal companion object {
        fun sumOf(layers: List<MorpheusGraphLayerBreakdown>) = MorpheusGraphLayerBreakdown(
            slotFromFront = null,
            shapeMillis = layers.floatSum { it.shapeMillis },
            meshMillis = layers.floatSum { it.meshMillis },
            edgeFadesMillis = layers.floatSum { it.edgeFadesMillis },
            alphaSettleMillis = layers.floatSum { it.alphaSettleMillis },
            fillMillis = layers.floatSum { it.fillMillis },
            edgesMillis = layers.floatSum { it.edgesMillis },
            halosMillis = layers.floatSum { it.halosMillis },
            dotsMillis = layers.floatSum { it.dotsMillis },
            compositeMillis = layers.floatSum { it.compositeMillis },
            nodes = layers.floatSum { it.nodes },
            visibleNodes = layers.floatSum { it.visibleNodes },
            drawnEdges = layers.floatSum { it.drawnEdges },
            drawnHalos = layers.floatSum { it.drawnHalos },
            drawnDots = layers.floatSum { it.drawnDots },
            drawCalls = layers.floatSum { it.drawCalls },
            settles = layers.sumOf { it.settles },
            morphMeshes = layers.sumOf { it.morphMeshes },
            meshes = layers.sumOf { it.meshes },
        )

        private inline fun List<MorpheusGraphLayerBreakdown>.floatSum(selector: (MorpheusGraphLayerBreakdown) -> Float): Float =
            fold(0f) { sum, layer -> sum + selector(layer) }
    }
}

/** The steps [MorpheusGraphLayerBreakdown] times; see there. */
internal enum class FramePhase { Shape, Mesh, EdgeFades, AlphaSettle, Fill, Edges, Halos, Dots, Composite }

/** One spec layer's sums over the current window. */
internal class SlotRecord {
    val phaseNanos = LongArray(FramePhase.entries.size)
    var nodes = 0L
    var visibleNodes = 0L
    var drawnEdges = 0L
    var drawnHalos = 0L
    var drawnDots = 0L
    var drawCalls = 0L
    var settles = 0
    var morphMeshes = 0
    private var drawMeshes = 0
    private var maxMeshes = 0

    fun addMesh() {
        drawMeshes++
        maxMeshes = max(maxMeshes, drawMeshes)
    }

    fun startDraw() {
        drawMeshes = 0
    }

    fun toBreakdown(slotFromFront: Int, frames: Int): MorpheusGraphLayerBreakdown {
        fun millis(phase: FramePhase) = phaseNanos[phase.ordinal] / frames / NANOS_PER_MILLI
        fun perFrame(sum: Long) = sum.toFloat() / frames
        return MorpheusGraphLayerBreakdown(
            slotFromFront = slotFromFront,
            shapeMillis = millis(FramePhase.Shape),
            meshMillis = millis(FramePhase.Mesh),
            edgeFadesMillis = millis(FramePhase.EdgeFades),
            alphaSettleMillis = millis(FramePhase.AlphaSettle),
            fillMillis = millis(FramePhase.Fill),
            edgesMillis = millis(FramePhase.Edges),
            halosMillis = millis(FramePhase.Halos),
            dotsMillis = millis(FramePhase.Dots),
            compositeMillis = millis(FramePhase.Composite),
            nodes = perFrame(nodes),
            visibleNodes = perFrame(visibleNodes),
            drawnEdges = perFrame(drawnEdges),
            drawnHalos = perFrame(drawnHalos),
            drawnDots = perFrame(drawnDots),
            drawCalls = perFrame(drawCalls),
            settles = settles,
            morphMeshes = morphMeshes,
            meshes = maxMeshes,
        )
    }
}

/**
 * Times one field's frame phase by phase into its spec layer's [SlotRecord], from timestamps taken only at
 * phase boundaries, and carries the debug switches to [LayerField.draw].
 */
internal class LayerProbe(val stats: MorpheusGraphFrameStats) {
    private var record = SlotRecord()
    private var mark = TimeSource.Monotonic.markNow()
    private var settlesBefore = 0
    private var morphMeshesBefore = 0

    fun startLayer(slotFromFront: Int, field: LayerField) {
        record = stats.slotRecord(slotFromFront)
        record.addMesh()
        settlesBefore = field.settleCount
        morphMeshesBefore = field.morphMeshBuildCount
        mark = TimeSource.Monotonic.markNow()
    }

    /** Adds the time since the last lap, or since [startLayer], to [phase]. */
    fun lap(phase: FramePhase) {
        val now = TimeSource.Monotonic.markNow()
        record.phaseNanos[phase.ordinal] += (now - mark).inWholeNanoseconds
        mark = now
    }

    /** Restarts the clock without charging the time since the last lap to any phase. */
    fun skipLap() {
        mark = TimeSource.Monotonic.markNow()
    }

    /** Like [lap], except that a [skip]ped step is charged nothing. */
    fun lapUnlessSkipped(phase: FramePhase, skip: Boolean) {
        if (skip) skipLap() else lap(phase)
    }

    fun recordDrawn(edges: Int, halos: Int, dots: Int, drawCalls: Int) {
        record.drawnEdges += edges
        record.drawnHalos += halos
        record.drawnDots += dots
        record.drawCalls += drawCalls
    }

    fun endLayer(field: LayerField) {
        record.nodes += field.nodeCount
        record.visibleNodes += field.visibleNodeCount
        record.settles += field.settleCount - settlesBefore
        record.morphMeshes += field.morphMeshBuildCount - morphMeshesBefore
    }
}

/**
 * Runs [step], which returns how many draw calls it made, and charges its time to [phase]. A [skip]ped step
 * neither runs nor records any time, and makes no draw calls. Without a probe, [step] simply runs.
 */
internal inline fun LayerProbe?.timed(phase: FramePhase, skip: Boolean, step: () -> Int): Int {
    if (this == null) return step()
    if (skip) {
        skipLap()
        return 0
    }
    return step().also { lap(phase) }
}
