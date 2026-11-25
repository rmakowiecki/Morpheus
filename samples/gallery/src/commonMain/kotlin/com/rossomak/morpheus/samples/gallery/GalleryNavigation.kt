package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which sample the gallery has open, if any; null is the landing page. On the desktop it lives in memory
 * ([InMemoryNavigation]); in the browser it lives in the address bar's hash, so a sample has a link and
 * the browser's Back and Forward move between samples.
 */
@Stable
interface GalleryNavigation {
    /** The open sample's id, or null for the landing page. */
    val sampleId: String?

    /** Opens the sample with [sampleId], or the landing page for null. */
    fun open(sampleId: String?)
}

/** A [GalleryNavigation] with no history, starting at [initialSampleId]. */
internal class InMemoryNavigation(initialSampleId: String?) : GalleryNavigation {
    override var sampleId: String? by mutableStateOf(initialSampleId)
        private set

    override fun open(sampleId: String?) {
        this.sampleId = sampleId
    }
}
