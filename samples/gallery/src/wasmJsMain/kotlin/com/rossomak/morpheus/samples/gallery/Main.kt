package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** Fills the browser window with the gallery. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = GALLERY_TITLE
    ComposeViewport(checkNotNull(document.body) { "The page has no body" }) {
        Gallery()
    }
}
