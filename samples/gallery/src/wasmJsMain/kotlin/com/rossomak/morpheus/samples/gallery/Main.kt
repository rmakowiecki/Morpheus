package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

/**
 * Fills the browser window with the gallery. `?benchmark` in the address opens it in benchmark mode on "Pills
 * to ribbons", `&sample=comet-playground` on the comet playground instead, and `&skip=halos,dots` or
 * `&skip=all` with those skip switches on (see [Gallery] and [GalleryBenchmark.fromQuery]).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = GALLERY_TITLE
    val query = URLSearchParams(window.location.search.toJsString())
    val benchmark = if (query.has("benchmark")) GalleryBenchmark.fromQuery(skip = query.get("skip"), sample = query.get("sample")) else null
    ComposeViewport(checkNotNull(document.body) { "The page has no body" }) {
        Gallery(benchmark = benchmark)
    }
}
