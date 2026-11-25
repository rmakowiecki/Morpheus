package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.rossomak.morpheus.samples.SampleRegistry
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

/**
 * Fills the browser window with the gallery. `?benchmark` in the address opens it in benchmark mode on "Pills
 * to ribbons", `&sample=comet-playground` on the comet playground instead, and `&skip=halos,dots` or
 * `&skip=all` with those skip switches on (see [Gallery] and [GalleryBenchmark.fromQuery]). Otherwise the hash names
 * the open sample (see [HashNavigation]).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = GALLERY_TITLE
    val query = URLSearchParams(window.location.search.toJsString())
    val benchmark = if (query.has("benchmark")) GalleryBenchmark.fromQuery(skip = query.get("skip"), sample = query.get("sample")) else null
    // A benchmark opens its own sample and ignores the hash.
    val navigation = if (benchmark == null) HashNavigation(SampleRegistry.samples.mapTo(HashSet()) { it.id }) else null
    ComposeViewport(checkNotNull(document.body) { "The page has no body" }) {
        Gallery(benchmark = benchmark, navigation = navigation)
    }
}
