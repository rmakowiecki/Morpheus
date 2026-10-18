package com.rossomak.morpheus.samples.gallery

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Opens the gallery; an exported spec also goes to standard output. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = GALLERY_TITLE,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        Gallery(onSpecExport = ::println)
    }
}
