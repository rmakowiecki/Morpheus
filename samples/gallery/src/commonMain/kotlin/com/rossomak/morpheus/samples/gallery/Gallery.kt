@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
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
 * detail opens over the list. An exported spec's Kotlin source goes to the clipboard and [onSpecExport].
 */
@Composable
fun Gallery(samples: List<Sample> = SampleRegistry.samples, onSpecExport: (source: String) -> Unit = {}) {
    // Showcases first, then the catalog, each in registry order.
    val orderedSamples = remember(samples) { samples.sortedBy { it.category.ordinal } }
    var selectedId by remember { mutableStateOf<String?>(null) }
    // Held here rather than in a pane, so a layout switch keeps the open sample's preparation and tuning.
    val session = remember(selectedId) { samples.firstOrNull { it.id == selectedId }?.let(::GallerySession) }
    var themeMode by remember { mutableStateOf(ThemeMode.System) }
    val previewFrame = remember { PreviewFrame() }
    val frameStats = remember { GalleryFrameStats() }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(session) { session?.prepare() }

    val systemDark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (systemDark) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints {
                val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
                val detail = @Composable { openSession: GallerySession, modifier: Modifier ->
                    SampleDetail(
                        session = openSession,
                        themeMode = themeMode,
                        systemDark = systemDark,
                        previewFrame = previewFrame,
                        frameStats = frameStats,
                        onThemeModeChange = { themeMode = it },
                        showsInlineBreakdown = !wide,
                        onBack = if (wide) null else ({ selectedId = null }),
                        modifier = modifier,
                    )
                }
                val tuning = session?.takeIf { it.isTuning }
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
                        }
                        val showsFrameStats = session != null && frameStats.isBreakdownExpanded
                        if (tuning != null || showsFrameStats) {
                            VerticalDivider()
                            SideColumn(
                                tuning = tuning?.let { open -> { tuneSheet(open, Modifier) } },
                                onCloseTuning = { tuning?.isTuning = false },
                                frameStats = frameStats.takeIf { showsFrameStats },
                                modifier = Modifier.width(SIDE_SHEET_WIDTH).fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    if (session == null) {
                        SampleList(samples = orderedSamples, selectedId = null, onSelect = { selectedId = it }, modifier = Modifier.fillMaxSize())
                    } else {
                        detail(session, Modifier.fillMaxSize())
                    }
                    if (tuning != null) {
                        TuneBottomSheet(onDismiss = { tuning.isTuning = false }) { tuneSheet(tuning, Modifier) }
                    }
                }
            }
        }
    }
}

/**
 * The wide layout's right-hand column, which stacks the tuner and the frame stats panes like an IDE's side panes.
 * The tuner takes whatever height the frame stats pane leaves; that pane takes only what its table needs, up to
 * [FRAME_STATS_MAX_SHARE] of the column, and scrolls inside itself past that. Either pane alone fills what it needs.
 */
@Composable
private fun SideColumn(
    tuning: (@Composable () -> Unit)?,
    onCloseTuning: () -> Unit,
    frameStats: GalleryFrameStats?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val frameStatsMaxHeight = maxHeight * FRAME_STATS_MAX_SHARE
        Column(modifier = Modifier.fillMaxSize()) {
            if (tuning != null) {
                SideSheet(title = "Tune", onClose = onCloseTuning, modifier = Modifier.weight(1f).fillMaxWidth()) { tuning() }
            }
            if (frameStats != null) {
                if (tuning != null) HorizontalDivider()
                SideSheet(
                    title = "Frame stats",
                    onClose = { frameStats.isBreakdownExpanded = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = frameStatsMaxHeight),
                ) {
                    FrameBreakdownPanel(frameStats = frameStats, maxHeight = Dp.Infinity, modifier = Modifier.fillMaxWidth())
                }
            }
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
private const val FRAME_STATS_MAX_SHARE = 0.4f
