package com.rossomak.morpheus.samples

import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.test.Test

// Gradle runs a module's tests from its own folder, samples/shared.
private val readme = File("../../README.md").readText()
private val fallbackPage = File("../gallery/src/wasmJsMain/resources/index.html").readText()

class ViewerNoticeTest {
    @Test
    fun `the README carries the notice`() {
        readme.collapseWhitespace() shouldContain ViewerNotice.HEADLINE
        readme.collapseWhitespace() shouldContain ViewerNotice.RISK
    }

    @Test
    fun `the Pages fallback page carries the notice`() {
        fallbackPage shouldContain ViewerNotice.HEADLINE
        fallbackPage shouldContain ViewerNotice.RISK
    }
}

/** The README wraps its lines and quotes the notice as a block quote, so compare the words only. */
private fun String.collapseWhitespace(): String = replace(Regex("\\s*\\n>?\\s*"), " ")
