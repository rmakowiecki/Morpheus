package com.rossomak.morpheus.samples.capture

import java.nio.file.Path

/** libwebp's `img2webp`, which turns PNG frames into a looping lossy WebP. */
object Img2WebP {
    /** Fails with the install commands unless `img2webp` runs. */
    fun checkInstalled() {
        // Starting a missing command throws an IOException.
        val installed = runCatching {
            val process = ProcessBuilder(COMMAND, "-version").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor() == 0
        }.getOrDefault(false)
        if (!installed) {
            throw CaptureException(
                """
                |$COMMAND isn't installed; it comes with libwebp:
                |  macOS: brew install webp
                |  Linux: sudo apt install webp
                """.trimMargin(),
            )
        }
    }

    /** Encodes [frames], in order, into an endlessly looping [output], each frame shown for [delayMillis]. */
    fun encode(frames: List<Path>, delayMillis: Int, output: Path) {
        // Every file is its own argument, never one joined string, so no path is ever split or quoted.
        val command = listOf(COMMAND, "-loop", "0", "-lossy", "-q", QUALITY.toString(), "-d", delayMillis.toString()) +
            frames.map { it.toString() } +
            listOf("-o", output.toString())
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw CaptureException("$COMMAND failed with exit code $exitCode on ${output.fileName}:\n$log")
    }

    /** Lossy WebP quality, from 0 to 100. */
    const val QUALITY = 80

    private const val COMMAND = "img2webp"
}
