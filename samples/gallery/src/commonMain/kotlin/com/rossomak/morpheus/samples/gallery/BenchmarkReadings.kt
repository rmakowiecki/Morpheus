@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphFrameStats

/** The sample the gallery opens in benchmark mode: the benchmark recipe's "Pills to ribbons". */
internal const val BENCHMARK_SAMPLE_ID = "hero"

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

/** `morpheus-benchmark frame=… graphs=… shape=… … composite=… backgrounds=… capped=… scaled=… switches=…`, in milliseconds per frame. */
internal fun benchmarkLine(stats: MorpheusGraphFrameStats, canvasScaled: Boolean): String {
    val total = stats.breakdown.total
    val phases = PHASE_ROWS.joinToString(separator = " ") { (label, millis) -> "${label.replace(" ", "-")}=${millis(total).toFixed(BENCHMARK_DECIMALS)}" }
    val capped = isCapped(frameMillis = stats.frameMillis, drawMillis = stats.drawMillis)
    return "$BENCHMARK_LINE_PREFIX frame=${stats.frameMillis.toFixed(BENCHMARK_DECIMALS)} graphs=${stats.drawMillis.toFixed(BENCHMARK_DECIMALS)} $phases " +
        "nodes=${total.nodes.toFixed(0)} visible-nodes=${total.visibleNodes.toFixed(0)} drawn-edges=${total.drawnEdges.toFixed(0)} " +
        "backgrounds=${stats.breakdown.backgroundCount} capped=$capped scaled=$canvasScaled switches=${stats.anySwitchOn}"
}

/**
 * How the gallery opens for `docs/benchmark.md`'s recipe (see [Gallery]): with [skips]' switches on, for the
 * recipe's skip rows, or with every switch off.
 */
data class GalleryBenchmark(val skips: Set<BenchmarkSkip> = emptySet()) {
    companion object {
        /** The setup a `skip` query value asks for: switch names separated by commas, or `all`. Unknown names are ignored. */
        fun withSkips(parameter: String?): GalleryBenchmark {
            val names = parameter.orEmpty().split(",").map { it.trim() }
            return GalleryBenchmark(BenchmarkSkip.entries.filter { "all" in names || it.parameterName in names }.toSet())
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

/** The recipe's preview: 16:9, shown at its own size. */
internal fun PreviewFrame.setUpForBenchmark() {
    preset = PreviewPreset.Wide
    scalesToFit = false
}

/** Turns [skips]' switches on, once as the gallery opens; each switch then stays as set. */
internal fun MorpheusGraphFrameStats.setUpForBenchmark(skips: Set<BenchmarkSkip>) {
    skipEdges = BenchmarkSkip.Edges in skips
    skipHalos = BenchmarkSkip.Halos in skips
    skipDots = BenchmarkSkip.Dots in skips
    skipFillRecolor = BenchmarkSkip.FillRecolor in skips
}

private const val BENCHMARK_DECIMALS = 3
