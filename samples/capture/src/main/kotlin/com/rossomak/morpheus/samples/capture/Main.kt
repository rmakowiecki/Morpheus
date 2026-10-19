package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleRegistry
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

/**
 * Captures one sample, by id, or `all` that aren't gallery-only, in registry order, into the repository's
 * docs/media. Stops at the first failure, keeping the media of the samples captured before it.
 */
fun main(args: Array<String>) {
    val exitCode = try {
        capture(args.toList())
        0
    } catch (failure: CaptureException) {
        System.err.println(failure.message)
        failure.cause?.let { System.err.println(it.stackTraceToString()) }
        1
    }
    // Compose and Skia leave non-daemon threads behind.
    exitProcess(exitCode)
}

private fun capture(args: List<String>) {
    val samples = selectSamples(args, SampleRegistry.samples)
    val mediaDir = mediaDir()
    Img2WebP.checkInstalled()
    for (sample in samples) {
        println("Capturing \"${sample.id}\"…")
        // A sample that throws while preparing or composing fails the run like any other failure.
        val files = runCatching { runBlocking { writeSampleMedia(sample, mediaDir) } }.getOrElse { failure ->
            throw failure as? CaptureException ?: CaptureException("Capturing \"${sample.id}\" failed: $failure", failure)
        }
        files.forEach { println("  ${it.fileName}  ${megabytes(it.toFile().length())}") }
    }
}

/** The samples [args] name: one id, or `all` for every sample that isn't gallery-only, in [registry] order. */
fun selectSamples(args: List<String>, registry: List<Sample>): List<Sample> {
    val captured = registry.filterNot { it.galleryOnly }
    val validIds = (captured.map { it.id } + ALL).joinToString()
    val arg = args.singleOrNull()
    val sample = registry.find { it.id == arg }
    val problem = when {
        arg == ALL -> return captured
        arg == null -> "Pass one sample id or \"$ALL\"; the valid ids are $validIds"
        sample == null -> "No sample is \"$arg\"; the valid ids are $validIds"
        sample.galleryOnly -> "\"$arg\" is gallery-only, so it has no media"
        else -> return listOf(sample)
    }
    throw CaptureException(problem)
}

private fun mediaDir(): Path {
    val dir = System.getProperty(MEDIA_DIR_PROPERTY)
        ?: throw CaptureException("No media folder: run the tool with `./gradlew :samples:capture:run`, which sets $MEDIA_DIR_PROPERTY")
    return Paths.get(dir)
}

private const val ALL = "all"
private const val MEDIA_DIR_PROPERTY = "morpheus.mediaDir"
