@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphFrameBreakdown
import com.rossomak.morpheus.MorpheusGraphFrameStats
import com.rossomak.morpheus.MorpheusGraphLayerBreakdown
import com.rossomak.morpheus.samples.SampleTileStats
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The gallery-wide frame stats every sample reports into, and whether the breakdown and comet debug panels are
 * open. Kept across samples, like the preview frame, so the debug switches stay as set.
 */
@Stable
internal class GalleryFrameStats {
    val stats = MorpheusGraphFrameStats()
    var isBreakdownExpanded by mutableStateOf(false)
    var isCometDebugExpanded by mutableStateOf(false)
}

/** One tile of a grid sample, under its label, with the stats it reports into. */
@Stable
internal class TileStats(val label: String, val stats: MorpheusGraphFrameStats)

/**
 * The stats of each tile of a grid sample, handed out by label as [SampleTileStats] so a grid's tiles don't sum into
 * one set of rows. The debug switches are the page's [source] ones, mirrored to every tile; the frame intervals
 * go to every tile too, so all of them publish a window together. One per open sample.
 */
@Stable
internal class TileStatsGroup(private val source: MorpheusGraphFrameStats) : SampleTileStats {
    private val byLabel = LinkedHashMap<String, MorpheusGraphFrameStats>()

    /** The tiles in the order the grid first asked for them. */
    var tiles: List<TileStats> by mutableStateOf(emptyList())
        private set

    val drawMillis: Float get() = tiles.sumOf { it.stats.drawMillis.toDouble() }.toFloat()

    val isStale: Boolean get() = tiles.any { it.stats.isStale }

    /** True while the open sample is a grid whose tiles report separately. */
    val isTiled: Boolean get() = tiles.isNotEmpty()

    override fun statsFor(tileLabel: String): MorpheusGraphFrameStats = byLabel.getOrPut(tileLabel) {
        MorpheusGraphFrameStats().also { stats ->
            mirrorSwitches(stats)
            tiles = tiles + TileStats(tileLabel, stats)
        }
    }

    fun recordFrame(intervalNanos: Long) = byLabel.values.forEach { it.recordFrame(intervalNanos) }

    fun reset() = byLabel.values.forEach { it.reset() }

    /** Copies the page's debug switches to every tile; a switch already at its value starts no new window. */
    fun mirrorSwitches() = byLabel.values.forEach(::mirrorSwitches)

    private fun mirrorSwitches(target: MorpheusGraphFrameStats) {
        target.skipEdges = source.skipEdges
        target.skipHalos = source.skipHalos
        target.skipDots = source.skipDots
        target.skipFillRecolor = source.skipFillRecolor
        target.showViolations = source.showViolations
        target.cometDebug = source.cometDebug
    }
}

/** Feeds the page's stats, and every tile's, the interval between frames of the gallery's frame clock, as the tuner playground does. */
@Composable
internal fun RecordFrameIntervals(frameStats: GalleryFrameStats, tiles: TileStatsGroup) {
    LaunchedEffect(frameStats, tiles) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNanos ->
                frameStats.stats.recordFrame(nowNanos - previousNanos)
                tiles.recordFrame(nowNanos - previousNanos)
                previousNanos = nowNanos
            }
        }
    }
}

/**
 * `frame 9.8 ms · graphs 6.1 ms`, marked when a debug switch is on, when the frame interval sits at a display
 * refresh interval ("capped": the frame-rate cap hides the cost) and when the preview [canvasScaled]. It is
 * the open or closed state of the frame stats pane, as Tune is of the tuner's, and dims while the reading
 * is from before the last reset.
 */
@Composable
internal fun FrameStatsButton(frameStats: GalleryFrameStats, tiles: TileStatsGroup, canvasScaled: Boolean, modifier: Modifier = Modifier) {
    val stats = frameStats.stats
    // A grid's tiles each report their own recording time; the page's stats see none of it.
    val drawMillis = if (tiles.isTiled) tiles.drawMillis else stats.drawMillis
    val notes = buildList {
        if (stats.anySwitchOn) add("debug switches on")
        if (isCapped(frameMillis = stats.frameMillis, drawMillis = drawMillis)) add("capped")
        if (canvasScaled) add("canvas scaled")
    }
    val readout = if (stats.frameMillis <= 0f) "frame – ms · graphs – ms" else "frame ${stats.frameMillis.toFixed(1)} ms · graphs ${drawMillis.toFixed(1)} ms"
    val colors = if (frameStats.isBreakdownExpanded) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.outlinedButtonColors()
    OutlinedButton(
        onClick = { frameStats.isBreakdownExpanded = !frameStats.isBreakdownExpanded },
        colors = colors,
        modifier = modifier.alpha(if (stats.isStale || tiles.isStale) STALE_ALPHA else 1f),
    ) {
        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(
            text = (listOf(readout) + notes).joinToString(separator = " · "),
            color = if (notes.isEmpty()) Color.Unspecified else MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * The breakdown of the last published window: per phase and count, the total and then one column per spec layer,
 * back to front; then the debug switches. A grid sample shows a column per tile instead, or a row per tile past
 * [MAX_TILE_COLUMNS]. At most [maxHeight] tall, scrolling inside itself both ways, so it
 * never scrolls the page. Dims while the reading is from before the last reset.
 */
@Composable
internal fun FrameBreakdownPanel(
    frameStats: GalleryFrameStats,
    tiles: TileStatsGroup,
    modifier: Modifier = Modifier,
    maxHeight: Dp = BREAKDOWN_MAX_HEIGHT,
) {
    val stats = frameStats.stats
    val breakdown = stats.breakdown
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column(
            modifier = Modifier
                .alpha(if (stats.isStale || tiles.isStale) STALE_ALPHA else 1f)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tiles.isTiled) {
                Text(
                    text = "Frame time and unrecorded (raster) time belong to the whole page and can't be split per tile. " +
                        "Per tile: recording time, work counts and draw calls.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else if (breakdown.backgroundCount > 1) {
                Text(
                    text = "several backgrounds: ${breakdown.backgroundCount}, summed per spec layer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (tiles.isTiled) TileBreakdownTable(tiles.tiles) else BreakdownTable(breakdown)
            }
            DebugSwitches(stats)
        }
    }
}

@Composable
private fun BreakdownTable(breakdown: MorpheusGraphFrameBreakdown) {
    val headers = listOf("total") + breakdown.layers.mapIndexed { index, _ -> layerHeader(index, breakdown.layers.size) }
    BreakdownColumns(headers = headers, columns = listOf(breakdown.total) + breakdown.layers, cellWidth = CELL_WIDTH)
}

/** The phase and count rows with one column in [columns] under each of [headers]. */
@Composable
private fun BreakdownColumns(headers: List<String>, columns: List<MorpheusGraphLayerBreakdown>, cellWidth: Dp) {
    TableRow(label = "ms per frame", cells = headers, cellWidth = cellWidth, isHeader = true)
    PHASE_ROWS.forEach { (label, millis) -> TableRow(label = label, cells = columns.map { millis(it).toFixed(2) }, cellWidth = cellWidth) }
    TableRow(label = "all phases", cells = columns.map { it.totalMillis.toFixed(2) }, cellWidth = cellWidth, isHeader = true)
    TableRow(label = "per frame", cells = headers, cellWidth = cellWidth, isHeader = true)
    COUNT_ROWS.forEach { (label, count) -> TableRow(label = label, cells = columns.map { count(it).roundToInt().toString() }, cellWidth = cellWidth) }
    TableRow(label = "per window", cells = headers, cellWidth = cellWidth, isHeader = true)
    WINDOW_COUNT_ROWS.forEach { (label, count) -> TableRow(label = label, cells = columns.map { count(it).toString() }, cellWidth = cellWidth) }
}

/**
 * Each tile's total as a column under its label; past [MAX_TILE_COLUMNS] tiles the table turns, a row per tile with
 * a column per phase and count, so it stays as wide as the measurements, not as the grid.
 */
@Composable
private fun TileBreakdownTable(tiles: List<TileStats>) {
    val totals = tiles.map { it.stats.breakdown.total }
    if (tiles.size <= MAX_TILE_COLUMNS) {
        BreakdownColumns(headers = tiles.map { it.label }, columns = totals, cellWidth = TILE_CELL_WIDTH)
        return
    }
    val measurements = PHASE_ROWS.map { (label, millis) -> label to { total: MorpheusGraphLayerBreakdown -> millis(total).toFixed(2) } } +
        ("all phases" to { total: MorpheusGraphLayerBreakdown -> total.totalMillis.toFixed(2) }) +
        COUNT_ROWS.map { (label, count) -> label to { total: MorpheusGraphLayerBreakdown -> count(total).roundToInt().toString() } } +
        WINDOW_COUNT_ROWS.map { (label, count) -> label to { total: MorpheusGraphLayerBreakdown -> count(total).toString() } }
    TableRow(label = "ms and counts per frame", cells = measurements.map { it.first }, isHeader = true)
    tiles.zip(totals).forEach { (tile, total) -> TableRow(label = tile.label, cells = measurements.map { it.second(total) }) }
}

/** Spec layers are counted back to front, as the spec lists them. */
private fun layerHeader(index: Int, count: Int): String = when {
    count == 1 -> "layer"
    index == 0 -> "back"
    index == count - 1 -> "front"
    else -> "layer ${index + 1}"
}

@Composable
private fun TableRow(label: String, cells: List<String>, isHeader: Boolean = false, cellWidth: Dp = CELL_WIDTH) {
    val style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row {
        Text(text = label, style = style, color = color, modifier = Modifier.width(LABEL_WIDTH))
        cells.forEach { cell ->
            Text(
                text = cell,
                style = style.copy(fontFamily = FontFamily.Monospace),
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.width(cellWidth),
            )
        }
    }
}

@Composable
private fun DebugSwitches(stats: MorpheusGraphFrameStats) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(selected = stats.skipEdges, onClick = { stats.skipEdges = !stats.skipEdges }, label = { Text("Skip edges") })
        FilterChip(selected = stats.skipHalos, onClick = { stats.skipHalos = !stats.skipHalos }, label = { Text("Skip halos") })
        FilterChip(selected = stats.skipDots, onClick = { stats.skipDots = !stats.skipDots }, label = { Text("Skip dots") })
        FilterChip(selected = stats.skipFillRecolor, onClick = { stats.skipFillRecolor = !stats.skipFillRecolor }, label = { Text("Skip fill recolor") })
        FilterChip(selected = stats.showViolations, onClick = { stats.showViolations = !stats.showViolations }, label = { Text("Show violations") })
    }
}

internal val PHASE_ROWS: List<Pair<String, (MorpheusGraphLayerBreakdown) -> Float>> = listOf(
    "shape" to { it.shapeMillis },
    "mesh" to { it.meshMillis },
    "edge fades" to { it.edgeFadesMillis },
    "alpha settle" to { it.alphaSettleMillis },
    "fill" to { it.fillMillis },
    "edges" to { it.edgesMillis },
    "halos" to { it.halosMillis },
    "dots" to { it.dotsMillis },
    "composite" to { it.compositeMillis },
)

private val COUNT_ROWS: List<Pair<String, (MorpheusGraphLayerBreakdown) -> Float>> = listOf(
    "nodes" to { it.nodes },
    "visible nodes" to { it.visibleNodes },
    "drawn edges" to { it.drawnEdges },
    "drawn halos" to { it.drawnHalos },
    "drawn dots" to { it.drawnDots },
    "draw calls" to { it.drawCalls },
)

private val WINDOW_COUNT_ROWS: List<Pair<String, (MorpheusGraphLayerBreakdown) -> Int>> = listOf(
    "settles" to { it.settles },
    "morph meshes" to { it.morphMeshes },
    "meshes" to { it.meshes },
)

/**
 * True when [frameMillis] sits within [CAP_TOLERANCE] of a common display refresh interval while the graphs
 * take well under it ([drawMillis] below [CAP_BUSY_SHARE] of it): the frame is waiting for the display, so the
 * frame-rate cap, not the work, sets the frame time. See docs/benchmark.md for running without the cap.
 */
internal fun isCapped(frameMillis: Float, drawMillis: Float): Boolean =
    frameMillis > 0f &&
        drawMillis < frameMillis * CAP_BUSY_SHARE &&
        COMMON_REFRESH_RATES.any { rate -> abs(frameMillis * rate / MILLIS_PER_SECOND - 1f) <= CAP_TOLERANCE }

/** The value with exactly [decimals] digits after the point, rounded half up: common code has no `String.format`. */
internal fun Float.toFixed(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= DECIMAL_BASE }
    val scaled = floor(abs(toDouble()) * factor + ROUNDING_HALF).toLong()
    val sign = if (this < 0f) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    return "$sign$whole.${(scaled % factor).toString().padStart(decimals, '0')}"
}

private val BREAKDOWN_MAX_HEIGHT = 240.dp
private val LABEL_WIDTH = 104.dp
private val CELL_WIDTH = 64.dp
private val TILE_CELL_WIDTH = 124.dp

/** A grid sample's tiles show as columns up to this many, and as rows past it. */
internal const val MAX_TILE_COLUMNS = 6
private val COMMON_REFRESH_RATES = listOf(60f, 75f, 90f, 100f, 120f, 144f, 165f, 240f)
private const val CAP_TOLERANCE = 0.02f
private const val CAP_BUSY_SHARE = 0.7f
private const val STALE_ALPHA = 0.5f
private const val MILLIS_PER_SECOND = 1000f
private const val DECIMAL_BASE = 10L
private const val ROUNDING_HALF = 0.5
