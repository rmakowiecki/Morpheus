package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.samples.CaptureThemes
import com.rossomak.morpheus.samples.Sample
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes [sample]'s media into [mediaDir]: per theme, a looping WebP and a PNG still, or only the still in
 * [com.rossomak.morpheus.samples.LoopMode.Still]. Files are `<id>-light` and `<id>-dark`, or `<id>` for dark-only.
 * Everything is written to a temporary directory first and moved only when every file fits the budget, so a
 * failed capture leaves [mediaDir] as it was.
 */
suspend fun writeSampleMedia(sample: Sample, mediaDir: Path): List<Path> {
    val recipe = sample.recipe
    val workDir = Files.createTempDirectory("morpheus-capture-${sample.id}-")
    try {
        val files = recipe.themes.themes.flatMap { theme ->
            val baseName = if (recipe.themes == CaptureThemes.DarkOnly) sample.id else "${sample.id}-${theme.suffix}"
            writeCapture(renderCapture(sample, theme), baseName, recipe.fps, workDir)
        }

        val overBudget = files.filter { Files.size(it) > recipe.budgetBytes }
        if (overBudget.isNotEmpty()) {
            val sizes = overBudget.joinToString("\n") { "  ${it.fileName} is ${megabytes(Files.size(it))}, over the ${megabytes(recipe.budgetBytes)} budget" }
            throw CaptureException(
                "Nothing was written for \"${sample.id}\":\n$sizes\n" +
                    "To shrink it, lower the recipe's fps, then its canvas area, then its duration, or the WebP quality (${Img2WebP.QUALITY}).",
            )
        }

        Files.createDirectories(mediaDir)
        return files.map { Files.move(it, mediaDir.resolve(it.fileName.toString()), StandardCopyOption.REPLACE_EXISTING) }
    } finally {
        workDir.toFile().deleteRecursively()
    }
}

private fun writeCapture(capture: Capture, baseName: String, fps: Int, workDir: Path): List<Path> {
    val animation = if (capture.frames.isNotEmpty()) {
        val framesDir = Files.createDirectory(workDir.resolve("$baseName-frames"))
        val framePaths = capture.frames.mapIndexed { index, frame ->
            framesDir.resolve("%05d.png".format(Locale.ROOT, index)).also { Files.write(it, frame.encodePng()) }
        }
        workDir.resolve("$baseName.webp").also { Img2WebP.encode(framePaths, delayMillis = (MILLIS_PER_SECOND / fps).roundToInt(), output = it) }
    } else {
        null
    }
    val still = workDir.resolve("$baseName.png").also { Files.write(it, capture.still.encodePng()) }
    return listOfNotNull(animation, still)
}

/** [bytes] in decimal megabytes, as the budget counts them. */
fun megabytes(bytes: Long): String = "%.2f MB".format(Locale.ROOT, bytes / BYTES_PER_MEGABYTE)

private const val MILLIS_PER_SECOND = 1000.0
private const val BYTES_PER_MEGABYTE = 1_000_000.0
