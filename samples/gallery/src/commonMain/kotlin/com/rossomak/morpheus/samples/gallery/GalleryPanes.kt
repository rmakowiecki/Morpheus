package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.rossomak.morpheus.samples.Sample

/** The panes beside a sample, in the order they stack and in which one stays when only one fits. */
internal enum class GalleryPane(val title: String) {
    Tune("Tune"),
    FrameStats("Frame stats"),
    CometDebug("Comet debug"),
    ;

    /** Tune needs something to tune; comet debug needs a cycle, since the comets are what a morph sends. */
    fun isAvailableFor(sample: Sample): Boolean = when (this) {
        Tune -> sample.specs.isNotEmpty() || sample.morphs.isNotEmpty()
        FrameStats -> true
        CometDebug -> sample.timing != null
    }
}

/** Which panes of the open [session] are open. Tune's flag is the session's, the others are gallery-wide in [frameStats]. */
@Stable
internal class OpenPanes(private val session: GallerySession, private val frameStats: GalleryFrameStats) {
    val available: List<GalleryPane> = GalleryPane.entries.filter { it.isAvailableFor(session.sample) }

    val open: List<GalleryPane> get() = available.filter(::isOpen)

    fun isOpen(pane: GalleryPane): Boolean = pane in available &&
        when (pane) {
            GalleryPane.Tune -> session.isTuning
            GalleryPane.FrameStats -> frameStats.isFrameStatsOpen
            GalleryPane.CometDebug -> frameStats.isCometDebugOpen
        }

    fun setOpen(pane: GalleryPane, isOpen: Boolean) {
        when (pane) {
            GalleryPane.Tune -> session.isTuning = isOpen
            GalleryPane.FrameStats -> frameStats.isFrameStatsOpen = isOpen
            GalleryPane.CometDebug -> frameStats.isCometDebugOpen = isOpen
        }
    }

    /** Opens or closes [pane]; opening it first closes the others if [closesOthers]. */
    fun toggle(pane: GalleryPane, closesOthers: Boolean) {
        val opens = !isOpen(pane)
        if (opens && closesOthers) closeAll()
        setOpen(pane, opens)
    }

    fun closeAll() = GalleryPane.entries.forEach { setOpen(it, false) }

    fun keepFirstOpen() = open.drop(1).forEach { setOpen(it, false) }
}

@Composable
internal fun rememberOpenPanes(session: GallerySession, frameStats: GalleryFrameStats): OpenPanes =
    remember(session, frameStats) { OpenPanes(session, frameStats) }
