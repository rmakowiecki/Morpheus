package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window

/**
 * A [GalleryNavigation] in the address bar's hash: `#comet-playground` opens that sample, and no hash is the
 * landing page. Opening a sample adds a history entry, so Back and Forward move between samples; a hash
 * typed or followed from a link opens its sample too. A hash naming none of [sampleIds] shows the landing page
 * and is cleared from the address without a history entry.
 *
 * Sample ids are part of these links, so renaming one breaks the links shared to it.
 */
internal class HashNavigation(private val sampleIds: Set<String>) : GalleryNavigation {
    override var sampleId: String? by mutableStateOf(readHash())
        private set

    init {
        // Back and Forward fire popstate; a hash edited in the address bar fires hashchange too, and both read the same hash.
        window.addEventListener("popstate") { sampleId = readHash() }
        window.addEventListener("hashchange") { sampleId = readHash() }
    }

    override fun open(sampleId: String?) {
        if (sampleId == this.sampleId) return
        this.sampleId = sampleId
        window.history.pushState(null, "", sampleId?.let { "#$it" } ?: bareUrl())
    }

    private fun readHash(): String? {
        val id = window.location.hash.removePrefix("#").takeIf { it.isNotEmpty() } ?: return null
        if (id in sampleIds) return id
        window.history.replaceState(null, "", bareUrl())
        return null
    }

    private fun bareUrl(): String = window.location.pathname + window.location.search
}
