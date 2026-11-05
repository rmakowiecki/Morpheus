@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.ExperimentalMorpheusGraphApi
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import com.rossomak.morpheus.tuner.toKotlinSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

internal const val GALLERY_TITLE = "Morpheus samples"

/**
 * The samples gallery: the registry's samples in a list, and the open one in a detail pane with a theme
 * control and a tuning sheet. List and detail sit side by side from [WIDE_LAYOUT_MIN_WIDTH]; below it the
 * detail opens over the list. From [SIDE_COLUMN_MIN_WIDTH] the panes sit in a column beside the detail; below it
 * they are sheets. An exported spec's Kotlin source goes to the clipboard and [onSpecExport].
 *
 * With a [benchmark] it opens set up as `docs/benchmark.md`'s recipe asks: its sample on the 16:9
 * preview with scale to fit off, the benchmark's skip switches on, and every reading of the frame stats also
 * printed (see [LogBenchmarkReadings]).
 */
@Composable
fun Gallery(samples: List<Sample> = SampleRegistry.samples, onSpecExport: (source: String) -> Unit = {}, benchmark: GalleryBenchmark? = null) {
    val frameStats = remember { GalleryFrameStats().apply { benchmark?.let { stats.setUpForBenchmark(it.skips) } } }
    Gallery(samples = samples, onSpecExport = onSpecExport, benchmark = benchmark, frameStats = frameStats)
}

/** The [Gallery] reporting into [frameStats], which a test can feed and read. */
@Composable
internal fun Gallery(samples: List<Sample>, onSpecExport: (source: String) -> Unit, benchmark: GalleryBenchmark?, frameStats: GalleryFrameStats) {
    // Grouped by category in menu order, each group in registry order.
    val orderedSamples = remember(samples) { samples.sortedBy { it.category.ordinal } }
    var selectedId by remember { mutableStateOf(benchmark?.sampleId) }
    // Held here rather than in a pane, so a layout switch keeps the open sample's preparation and tuning.
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val previewFrame = remember { PreviewFrame().apply { if (benchmark != null) setUpForBenchmark() } }
    val session = remember(selectedId) { samples.firstOrNull { it.id == selectedId }?.let { GallerySession(it, frameStats.stats) } }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(session) { session?.prepare() }

    val systemDark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (systemDark) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints {
                val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
                val hasSideColumn = maxWidth >= SIDE_COLUMN_MIN_WIDTH
                val detail = @Composable { openSession: GallerySession, modifier: Modifier ->
                    SampleDetail(
                        session = openSession,
                        themeMode = themeMode,
                        systemDark = systemDark,
                        previewFrame = previewFrame,
                        frameStats = frameStats,
                        onThemeModeChange = { themeMode = it },
                        opensOnePaneAtATime = !hasSideColumn,
                        logsBenchmarkReadings = benchmark != null,
                        onBack = if (wide) {
                            null
                        } else {
                            {
                                // The next sample opens with no sheet over it.
                                OpenPanes(openSession, frameStats).closeAll()
                                selectedId = null
                            }
                        },
                        modifier = modifier.testTag(DETAIL_TEST_TAG),
                    )
                }
                val tuneSheet = @Composable { openSession: GallerySession, modifier: Modifier ->
                    TuneSheetContent(
                        session = openSession,
                        showViolations = frameStats.stats.showViolations,
                        onShowViolationsChange = { frameStats.stats.showViolations = it },
                        onExport = { spec -> coroutineScope.launch { exportSpec(spec, clipboard, onSpecExport) } },
                        modifier = modifier,
                    )
                }
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SampleList(
                            samples = orderedSamples,
                            selectedId = selectedId,
                            onSelect = { selectedId = it },
                            modifier = Modifier.width(LIST_WIDTH).fillMaxHeight(),
                        )
                        VerticalDivider()
                        if (session == null) {
                            NoSampleSelected(modifier = Modifier.weight(1f).fillMaxHeight())
                        } else {
                            detail(session, Modifier.weight(1f).fillMaxHeight())
                            PanesBesideDetail(hasSideColumn = hasSideColumn, session = session, frameStats = frameStats) { tuneSheet(session, Modifier) }
                        }
                    }
                } else {
                    if (session == null) {
                        SampleList(samples = orderedSamples, selectedId = null, onSelect = { selectedId = it }, modifier = Modifier.fillMaxSize())
                    } else {
                        detail(session, Modifier.fillMaxSize())
                        PaneSheets(session = session, frameStats = frameStats) { tuneSheet(session, Modifier) }
                    }
                }
            }
        }
    }
}

/** The [session]'s panes after its detail: the side column when [hasSideColumn], else sheets. */
@Composable
private fun PanesBesideDetail(hasSideColumn: Boolean, session: GallerySession, frameStats: GalleryFrameStats, tuneSheet: @Composable () -> Unit) {
    if (!hasSideColumn) {
        PaneSheets(session = session, frameStats = frameStats, tuneSheet = tuneSheet)
        return
    }
    // Always there while a sample is open, so opening or closing a pane never changes the stage's width.
    VerticalDivider()
    SideColumn(session = session, frameStats = frameStats, tuneSheet = tuneSheet, modifier = Modifier.width(SIDE_SHEET_WIDTH).fillMaxHeight())
}

/** The panes as modal sheets, one at a time. Of those the side column left open, the first stays. */
@Composable
private fun PaneSheets(session: GallerySession, frameStats: GalleryFrameStats, tuneSheet: @Composable () -> Unit) {
    val panes = rememberOpenPanes(session, frameStats)
    LaunchedEffect(panes) { panes.keepFirstOpen() }
    val pane = panes.open.firstOrNull() ?: return
    PaneBottomSheet(title = pane.title, onDismiss = { panes.setOpen(pane, false) }) {
        val paneModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
        PaneContent(pane = pane, session = session, frameStats = frameStats, tuneSheet = tuneSheet, modifier = paneModifier)
    }
}

/**
 * Stacks the open panes like an IDE's side panes. The tuner takes the height the frame stats pane leaves; that pane
 * takes what its table needs, up to [FRAME_STATS_MAX_SHARE] of the column, and scrolls past that. With no pane open,
 * the column shows a hint.
 */
@Composable
private fun SideColumn(session: GallerySession, frameStats: GalleryFrameStats, tuneSheet: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val panes = rememberOpenPanes(session, frameStats)
    val open = panes.open
    if (open.isEmpty()) {
        NoPaneOpen(available = panes.available, modifier = modifier)
        return
    }
    BoxWithConstraints(modifier = modifier) {
        val frameStatsMaxHeight = maxHeight * FRAME_STATS_MAX_SHARE
        Column(modifier = Modifier.fillMaxSize()) {
            open.forEachIndexed { index, pane ->
                if (index > 0) HorizontalDivider()
                val sheetModifier = when (pane) {
                    GalleryPane.Tune -> Modifier.weight(1f)
                    GalleryPane.FrameStats -> Modifier.heightIn(max = frameStatsMaxHeight)
                    GalleryPane.CometDebug -> Modifier
                }
                SideSheet(title = pane.title, onClose = { panes.setOpen(pane, false) }, modifier = sheetModifier.fillMaxWidth()) {
                    PaneContent(pane = pane, session = session, frameStats = frameStats, tuneSheet = tuneSheet, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PaneContent(pane: GalleryPane, session: GallerySession, frameStats: GalleryFrameStats, tuneSheet: @Composable () -> Unit, modifier: Modifier) {
    when (pane) {
        GalleryPane.Tune -> tuneSheet()
        GalleryPane.FrameStats -> FrameBreakdownPanel(frameStats = frameStats, tiles = session.tiles, modifier = modifier)
        GalleryPane.CometDebug -> CometDebugPanel(stats = frameStats.stats, modifier = modifier)
    }
}

/** The side column's hint with no pane open, naming the [available] panes. */
@Composable
private fun NoPaneOpen(available: List<GalleryPane>, modifier: Modifier = Modifier) {
    val titles = available.map { it.title }
    val paneNames = if (titles.size == 1) titles.single() else titles.dropLast(1).joinToString(separator = ", ") + " or " + titles.last()
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Open $paneNames",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Copies [spec] as Kotlin source. A browser that refuses clipboard access rejects with a JS error rather than
 * an [Exception], hence [Throwable]; the export hook still gets the source.
 */
private suspend fun exportSpec(spec: MorpheusGraphSpec, clipboard: Clipboard, onSpecExport: (String) -> Unit) {
    val source = spec.toKotlinSource()
    onSpecExport(source)
    try {
        clipboard.setClipEntry(plainTextClipEntry(source))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") refused: Throwable) {
        // Nothing else to do: the source is still in the export hook, and the gallery must keep running.
    }
}

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val LIST_WIDTH = 320.dp
private val SIDE_SHEET_WIDTH = 380.dp

/** The narrowest the detail gets beside the side column. */
internal val DETAIL_MIN_WIDTH = 540.dp

/** The list, the detail at [DETAIL_MIN_WIDTH], the side column and two dividers. */
private val SIDE_COLUMN_MIN_WIDTH = LIST_WIDTH + DETAIL_MIN_WIDTH + SIDE_SHEET_WIDTH + 2.dp

internal const val DETAIL_TEST_TAG = "sample-detail"
private const val FRAME_STATS_MAX_SHARE = 0.4f
