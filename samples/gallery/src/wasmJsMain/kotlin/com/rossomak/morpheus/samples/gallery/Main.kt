package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

/** Fills the browser window with the gallery; `?benchmark` in the address opens it in benchmark mode (see [Gallery]). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = GALLERY_TITLE
    ComposeViewport(checkNotNull(document.body) { "The page has no body" }) {
        Gallery(benchmarkMode = URLSearchParams(window.location.search.toJsString()).has("benchmark"))
    }
}
