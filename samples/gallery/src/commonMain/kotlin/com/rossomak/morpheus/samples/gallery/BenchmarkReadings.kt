@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphDrawingApproach
import com.rossomak.morpheus.MorpheusGraphFrameStats

/** The sample the gallery opens in benchmark mode unless asked for another: the benchmark recipe's "Pills to ribbons". */
internal const val BENCHMARK_SAMPLE_ID = "hero"

/** The samples the benchmark measures: "Pills to ribbons", and the heavier "Comet playground". */
internal val BENCHMARK_SAMPLE_IDS = listOf(BENCHMARK_SAMPLE_ID, "comet-playground")

/** Starts every line [LogBenchmarkReadings] prints, so a script can pick them out of a browser's log. */
internal const val BENCHMARK_LINE_PREFIX = "morpheus-benchmark"

/**
 * Prints each window [stats] publishes as one line, so `scripts/gallery_benchmark.py` can read the readout
 * without anyone at the screen: frame and graphs, the phase table's totals, and the button's flags. In the
 * browser, a line goes to the console. [canvasScaled] reads whether the preview shows at other than its own size.
 */
@Composable
internal fun LogBenchmarkReadings(stats: MorpheusGraphFrameStats, canvasScaled: () -> Boolean) {
    LaunchedEffect(stats) {
        snapshotFlow { stats.breakdown }.collect {
            if (!stats.isStale && stats.frameMillis > 0f) println(benchmarkLine(stats, canvasScaled()))
        }
    }
}

/** `morpheus-benchmark frame=… graphs=… shape=… … composite=… draw-calls=… backgrounds=… capped=… scaled=… switches=… drawing=…`, in milliseconds per frame. */
internal fun benchmarkLine(stats: MorpheusGraphFrameStats, canvasScaled: Boolean): String {
    val total = stats.breakdown.total
    val phases = PHASE_ROWS.joinToString(separator = " ") { (label, millis) -> "${label.replace(" ", "-")}=${millis(total).toFixed(BENCHMARK_DECIMALS)}" }
    val capped = isCapped(frameMillis = stats.frameMillis, drawMillis = stats.drawMillis)
    return "$BENCHMARK_LINE_PREFIX frame=${stats.frameMillis.toFixed(BENCHMARK_DECIMALS)} graphs=${stats.drawMillis.toFixed(BENCHMARK_DECIMALS)} $phases " +
        "nodes=${total.nodes.toFixed(0)} visible-nodes=${total.visibleNodes.toFixed(0)} drawn-edges=${total.drawnEdges.toFixed(0)} draw-calls=${total.drawCalls.toFixed(0)} " +
        "backgrounds=${stats.breakdown.backgroundCount} capped=$capped scaled=$canvasScaled switches=${stats.anySwitchOn} " +
        "drawing=${stats.drawingApproach.parameterName}"
}

/**
 * How the gallery opens for `docs/benchmark.md`'s recipe (see [Gallery]): on the sample [sampleId] names, with
 * [skips]' switches on, for the recipe's skip rows, or with every switch off; drawing in the [drawing] approach.
 */
data class GalleryBenchmark(
    val skips: Set<BenchmarkSkip> = emptySet(),
    val sampleId: String = BENCHMARK_SAMPLE_ID,
    val drawing: MorpheusGraphDrawingApproach = MorpheusGraphDrawingApproach.PerElement,
) {
    init {
        require(sampleId in BENCHMARK_SAMPLE_IDS) { "The benchmark measures $BENCHMARK_SAMPLE_IDS, not \"$sampleId\"" }
    }

    companion object {
        /**
         * The setup the `skip`, `sample` and `drawing` query values ask for: switch names separated by commas, or
         * `all`; one of [BENCHMARK_SAMPLE_IDS]; and an approach's [parameterName]. Unknown switch names are ignored,
         * an unknown or missing sample opens "Pills to ribbons", and an unknown or missing approach draws per element.
         */
        fun fromQuery(skip: String?, sample: String?, drawing: String? = null): GalleryBenchmark {
            val names = skip.orEmpty().split(",").map { it.trim() }
            val skips = BenchmarkSkip.entries.filter { "all" in names || it.parameterName in names }.toSet()
            return GalleryBenchmark(
                skips = skips,
                sampleId = sample?.takeIf { it in BENCHMARK_SAMPLE_IDS } ?: BENCHMARK_SAMPLE_ID,
                drawing = MorpheusGraphDrawingApproach.entries.firstOrNull { it.parameterName == drawing } ?: MorpheusGraphDrawingApproach.PerElement,
            )
        }
    }
}

/** A skip switch the benchmark can open with, by its name in the `skip` query value. */
enum class BenchmarkSkip(internal val parameterName: String) {
    Edges("edges"),
    Halos("halos"),
    Dots("dots"),
    FillRecolor("fill"),
}

/** A drawing approach's name in the `drawing` query value and in each reading. */
internal val MorpheusGraphDrawingApproach.parameterName: String
    get() = when (this) {
        MorpheusGraphDrawingApproach.PerElement -> "per-element"
        MorpheusGraphDrawingApproach.PointBatches -> "point-batches"
    }

/** The recipe's preview: 16:9, shown at its own size. */
internal fun PreviewFrame.setUpForBenchmark() {
    preset = PreviewPreset.Wide
    scalesToFit = false
}

/** Turns [benchmark]'s skip switches on and picks its drawing approach, once as the gallery opens; each then stays as set. */
internal fun MorpheusGraphFrameStats.setUpForBenchmark(benchmark: GalleryBenchmark) {
    skipEdges = BenchmarkSkip.Edges in benchmark.skips
    skipHalos = BenchmarkSkip.Halos in benchmark.skips
    skipDots = BenchmarkSkip.Dots in benchmark.skips
    skipFillRecolor = BenchmarkSkip.FillRecolor in benchmark.skips
    drawingApproach = benchmark.drawing
}

private const val BENCHMARK_DECIMALS = 3
