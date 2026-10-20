@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.max
import kotlin.time.TimeSource

private const val NANOS_PER_SECOND = 1_000_000_000f

private var nextHolderId = 0

/**
 * Draws [spec] as an animated network-graph background: every layer's edges, glows and nodes,
 * back to front, each painted with its own fill, else its spec's, else [fill] (see [MorpheusGraphFill]).
 * Paints nothing else and is transparent everywhere it doesn't draw, so it composes over whatever
 * `Modifier.background(...)` ran earlier in the chain.
 *
 * Every drawn node has at least two visible edges, and its dot is at least as opaque as each of them;
 * see [MorpheusGraphLayerSpec].
 *
 * The graph's own motion (drift, waves, pulses) ignores the system animator-duration scale ("Remove
 * animations"); timed morphs follow it (see [MorpheusGraphMorphState]). The frame loop pauses while the
 * lifecycle is below [Lifecycle.State.STARTED], and picks up where it left off.
 */
@Composable
public fun Modifier.morpheusGraphBackground(spec: MorpheusGraphSpec, fill: MorpheusGraphFill = LocalMorpheusGraphFill.current): Modifier =
    morpheusGraphBackground(MorpheusGraphMorph(spec, spec, 0f), fill)

/**
 * The [morph] between two specs, at its fraction; see [MorpheusGraphMorph]. Drive the fraction from
 * anything — a pager's scroll position, a slider, or [animateMorpheusGraphMorph] — and the background
 * follows it continuously, in either direction.
 *
 * The surface may change size at any time without reshuffling: the node grid is sized from the
 * window, not the surface, and the shape follows the surface's new bounds the same way a morph does.
 */
@Composable
public fun Modifier.morpheusGraphBackground(morph: MorpheusGraphMorph, fill: MorpheusGraphFill = LocalMorpheusGraphFill.current): Modifier =
    drawMorpheusGraph({ morph }, fill, stats = LocalMorpheusGraphFrameStats.current, layerStats = null, showViolations = false)

/**
 * Whatever [state] shows; see [MorpheusGraphMorphState]. The state is read while drawing, so seeks,
 * timed morphs and [Follow] redraw the background without recomposing anything.
 */
@Composable
public fun Modifier.morpheusGraphBackground(state: MorpheusGraphMorphState, fill: MorpheusGraphFill = LocalMorpheusGraphFill.current): Modifier =
    drawMorpheusGraph({ state.morph }, fill, stats = LocalMorpheusGraphFrameStats.current, layerStats = null, showViolations = false)

/**
 * [morpheusGraphBackground] with diagnostics: [stats] gets each frame's draw time and breakdown, [layerStats]
 * each layer's visible, pruned and repaired counts, and [showViolations] rings in red any node that breaks
 * the connectivity or alpha rules anyway, as an on-device check. A null [stats] falls back to
 * [LocalMorpheusGraphFrameStats], and the violations show if either [showViolations] or the stats ask.
 */
@ExperimentalMorpheusGraphApi
@Composable
public fun Modifier.morpheusGraphBackground(
    morph: MorpheusGraphMorph,
    fill: MorpheusGraphFill,
    stats: MorpheusGraphFrameStats?,
    layerStats: MorpheusGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier = drawMorpheusGraph({ morph }, fill, stats ?: LocalMorpheusGraphFrameStats.current, layerStats, showViolations)

/** The [state] overload of [morpheusGraphBackground] with diagnostics. */
@ExperimentalMorpheusGraphApi
@Composable
public fun Modifier.morpheusGraphBackground(
    state: MorpheusGraphMorphState,
    fill: MorpheusGraphFill,
    stats: MorpheusGraphFrameStats?,
    layerStats: MorpheusGraphLayerStats? = null,
    showViolations: Boolean = false,
): Modifier = drawMorpheusGraph({ state.morph }, fill, stats ?: LocalMorpheusGraphFrameStats.current, layerStats, showViolations)

@Composable
private fun Modifier.drawMorpheusGraph(
    morph: () -> MorpheusGraphMorph,
    fill: MorpheusGraphFill,
    stats: MorpheusGraphFrameStats?,
    layerStats: MorpheusGraphLayerStats?,
    showViolations: Boolean,
): Modifier {
    val windowSize = LocalWindowInfo.current.containerSize
    val holder = remember { MorpheusGraphHolder() }
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Stopped, the clock stands still rather than jumping ahead on return.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previousNanos = withFrameNanos { it }
            while (true) {
                withFrameNanos { nowNanos ->
                    elapsedNanos.longValue += nowNanos - previousNanos
                    previousNanos = nowNanos
                }
            }
        }
    }

    return this.drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        // Without stats, no clock is read at all.
        val drawStart = stats?.let { TimeSource.Monotonic.markNow() }
        val probe = stats?.probe
        stats?.recordBackground(holder.id)
        val showsViolations = showViolations || stats?.showViolations == true
        val timeSeconds = elapsedNanos.longValue / NANOS_PER_SECOND
        // No window in previews; the surface itself is the next best fixed reference.
        val windowLongSide = max(windowSize.width, windowSize.height).toFloat()
        val referenceSize = if (windowLongSide > 0f) windowLongSide else max(size.width, size.height)
        val layers = holder.layersFor(morph(), fill)
        val fields = holder.fieldsFor(layers, referenceSize, density)
        // Two meshes dissolving into each other draw one spec layer, and a fill sees them as one.
        val layerCount = (layers.maxOfOrNull { it.slotFromFront } ?: -1) + 1
        // Nodes deliberately sit past the surface's edges, and drawBehind is not clipped.
        clipRect {
            fields.forEachIndexed { index, field ->
                val layer = layers[index]
                probe?.startLayer(layer.slotFromFront, field)
                val layerFill = layer.fill.toLayerFill(layerCount - 1 - layer.slotFromFront, layerCount, timeSeconds, size)
                probe?.lap(FramePhase.Fill)
                field.advance(timeSeconds, size.width, size.height, probe)
                val halos = holder.halosFor(layerFill.maskColor)
                probe?.lapUnlessSkipped(FramePhase.Halos, skip = probe.stats.skipHalos)
                field.draw(this, layerFill, halos, timeSeconds, showsViolations, probe)
                probe?.endLayer(field)
            }
        }
        holder.endFrame()
        drawStart?.let { start -> stats?.recordDraw(start.elapsedNow().inWholeNanoseconds) }
        layerStats?.record(fields)
    }
}

/** How [LayerField.draw] colors a layer. */
internal sealed interface LayerFill {
    /** The opaque color the layer's edges, halos and dots are drawn in before any brush is applied. */
    val maskColor: Color

    /** How opaque the whole layer is composited, on top of each element's own alpha. */
    val layerAlpha: Float

    /** Every element drawn in [color]: opaque, with [color]'s alpha applied to the layer as a whole. */
    data class Solid(val color: Color) : LayerFill {
        override val maskColor: Color get() = color.copy(alpha = 1f)
        override val layerAlpha: Float get() = color.alpha
    }

    /**
     * Every element drawn in white, then the whole layer recolored with [base], keeping each pixel's
     * alpha; each of [overlays] is then blended over what lies under it at its own alpha, again keeping
     * each pixel's alpha.
     */
    data class Masked(val base: Brush, val overlays: List<Overlay> = emptyList()) : LayerFill {
        override val maskColor: Color get() = Color.White
        override val layerAlpha: Float get() = 1f
    }

    data class Overlay(val brush: Brush, val alpha: Float)
}

/**
 * Per layer, back to front: nodes drawn / nodes pruned by [enforceMinDegree] · edges it added by
 * repair. Refreshed twice a second. A layer that leans on repair hard is one whose filters fight
 * its density.
 */
@ExperimentalMorpheusGraphApi
@Stable
public class MorpheusGraphLayerStats {
    public var summary: String by mutableStateOf("")
        private set

    private var lastPublish: TimeSource.Monotonic.ValueTimeMark? = null

    internal fun record(fields: List<LayerField>) {
        val sincePublish = lastPublish?.elapsedNow()?.inWholeNanoseconds
        if (sincePublish != null && sincePublish < PUBLISH_INTERVAL_NANOS) return
        lastPublish = TimeSource.Monotonic.markNow()
        summary = fields.joinToString(separator = "  |  ") { field ->
            "${field.visibleNodeCount}/${field.prunedNodeCount} · ${field.repairedEdgeCount}"
        }
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 500_000_000L
    }
}

/**
 * Per-modifier state kept across frames: the morph's layers, one [LayerField] per mesh and the halo brushes.
 *
 * A field is kept for as long as its layer stays in the same morph family and seed, whatever else
 * changes, so a morph, a tuning edit or a resize never rebuilds it; only a new window size, which
 * changes the grid's cell size, rebuilds them all.
 */
private class MorpheusGraphHolder {
    /** Tells the holder's background apart in [MorpheusGraphFrameStats], without the stats holding on to it. */
    val id = nextHolderId++

    private var builtReferenceSize = 0f
    private var builtPxPerDp = 0f
    private var fieldsByFamily = HashMap<LayerFamily, LayerField>()
    private var halosByColor = HashMap<Color, HaloBrushCache>()
    private var halosThisFrame = HashMap<Color, HaloBrushCache>()
    private var lastMorph: MorpheusGraphMorph? = null
    private var lastFill: MorpheusGraphFill? = null
    private var lastLayers: List<MorphLayer> = emptyList()

    /** [morph]'s layers, worked out again only when the morph or the inherited fill changes. */
    fun layersFor(morph: MorpheusGraphMorph, fill: MorpheusGraphFill): List<MorphLayer> {
        if (morph != lastMorph || fill != lastFill) {
            lastLayers = morphLayers(morph, fill)
            lastMorph = morph
            lastFill = fill
        }
        return lastLayers
    }

    fun fieldsFor(layers: List<MorphLayer>, referenceSize: Float, pxPerDp: Float): List<LayerField> {
        if (referenceSize != builtReferenceSize || pxPerDp != builtPxPerDp) {
            fieldsByFamily.clear()
            builtReferenceSize = referenceSize
            builtPxPerDp = pxPerDp
        }
        val kept = HashMap<LayerFamily, LayerField>()
        val fields = layers.map { layer ->
            val family = LayerFamily(layer.seed, layer.spec)
            val field = fieldsByFamily[family] ?: LayerField(layer.spec, layer.seed, referenceSize, pxPerDp)
            field.spec = layer.spec
            field.shape = layer.shape
            kept[family] = field
            field
        }
        fieldsByFamily = kept
        return fields
    }

    fun halosFor(color: Color): HaloBrushCache = halosThisFrame.getOrPut(color) { halosByColor[color] ?: HaloBrushCache(color) }

    /** Drops every halo cache the frame just drawn didn't use: a crossfade or an animated fill passes a new color each frame. */
    fun endFrame() {
        halosByColor = halosThisFrame
        halosThisFrame = HashMap()
    }
}

/** What a [LayerField] can't change after it is built. */
private data class LayerFamily(val seed: Int, val density: Float, val jitter: Float, val edgeRule: EdgeRule, val strictTriangles: Boolean) {
    constructor(seed: Int, spec: MorpheusGraphLayerSpec) : this(seed, spec.density, spec.jitter, spec.edgeRule, spec.strictTriangles)
}

/**
 * One radial-gradient [Brush] per halo radius, built on first use and kept. Per-halo brightness goes
 * through `drawCircle`'s alpha rather than into the brush, so a handful of brushes cover a surface.
 */
internal class HaloBrushCache(private val color: Color) {
    private val brushesByRadius = HashMap<Int, Brush>()

    fun brushFor(radius: Float): Brush {
        val key = radius.toInt().coerceAtLeast(1)
        return brushesByRadius.getOrPut(key) {
            // Fading to the same color at zero alpha, not Color.Transparent, which would pass through black.
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = 1f), color.copy(alpha = 0f)),
                center = Offset.Zero,
                radius = key.toFloat(),
            )
        }
    }
}
