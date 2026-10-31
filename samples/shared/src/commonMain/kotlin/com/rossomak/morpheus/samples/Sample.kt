package com.rossomak.morpheus.samples

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.rossomak.morpheus.MorpheusGraphSpec
import kotlin.time.Duration

/**
 * One registry entry: a sample composable plus what the gallery and the capture tool need to know about it.
 *
 * [id] is kebab-case and names the sample's media files. [specs] are the specs the gallery can tune, by
 * name, [morphs] the easings of the morphs between them and [timing] how long each morph and each hold takes;
 * a sample with morphs has a timing, one without has none. [content] receives all of them through [SampleSpecs],
 * tuned in the gallery and at their defaults in a capture, and passes them to the sample composable as
 * plain parameters. A [galleryOnly] sample runs in the gallery but produces no media. [prepare] loads
 * anything asynchronous, such as an image, before [content] first composes.
 */
class Sample(
    val id: String,
    val title: String,
    val description: String,
    val category: SampleCategory,
    val recipe: CaptureRecipe = CaptureRecipe(),
    val specs: List<TunableSpec> = emptyList(),
    val morphs: List<TunableMorph> = emptyList(),
    val timing: MorphTiming? = null,
    val galleryOnly: Boolean = false,
    val prepare: suspend () -> Unit = {},
    val content: @Composable (specs: SampleSpecs) -> Unit,
) {
    init {
        require(KEBAB_CASE.matches(id)) { "Sample id \"$id\" isn't kebab-case" }
        val duplicates = specs.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Sample \"$id\" names more than one spec $duplicates" }
        val duplicateMorphs = morphs.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicateMorphs.isEmpty()) { "Sample \"$id\" names more than one morph $duplicateMorphs" }
        require(morphs.isEmpty() == (timing == null)) { "Sample \"$id\" needs a timing exactly when it names morphs" }
    }

    /** Every spec, morph easing and the timing at its default, as a capture renders it. */
    val defaultSpecs: SampleSpecs
        get() = SampleSpecs(specs.associate { it.name to it.default }, morphs.associate { it.name to it.default }, timing)

    override fun toString(): String = "Sample($id)"
}

/** Showcases are eye-catchers; the rest cover one capability each: shapes, fills, morphs. */
enum class SampleCategory { Showcase, BasicShapes, Fills, Morphs }

/** A spec the gallery can tune, under the [name] its sample's content asks for it by. */
@Immutable
data class TunableSpec(val name: String, val default: MorpheusGraphSpec)

/**
 * A morph whose easing the gallery can pick, under the [name] its sample's content asks for it by, such as
 * "Pills → Ribbons". The sample drives the morph's fraction through this easing over the morph's duration.
 */
@Immutable
data class TunableMorph(val name: String, val default: MorphEasing = MorphEasing.FastOutSlowIn)

/**
 * The easings the gallery offers for a sample's morphs. "Out" is how a morph leaves its start and "in" how it
 * arrives at its end, as in Material's names: [SlowOutFastIn] is [FastOutSlowIn] played backwards,
 * [FastOutLinearIn] only accelerates and [LinearOutSlowIn] only decelerates.
 */
@Suppress("MagicNumber")
enum class MorphEasing(val label: String, val easing: Easing) {
    Linear("Linear", LinearEasing),
    FastOutSlowIn("Fast out, slow in", FastOutSlowInEasing),
    SlowOutFastIn("Slow out, fast in", CubicBezierEasing(0.8f, 0f, 0.6f, 1f)),
    LinearOutSlowIn("Linear out, slow in", LinearOutSlowInEasing),
    FastOutLinearIn("Fast out, linear in", FastOutLinearInEasing),
    CubicInOut("Cubic in and out", CubicBezierEasing(0.65f, 0f, 0.35f, 1f)),
}

/**
 * How long a sample's morphs take: each spec rests for [hold] between two morphs, half of it at the end of
 * the morph that arrives and half at the start of the one that leaves, and each morph takes [morph].
 */
@Immutable
data class MorphTiming(val hold: Duration, val morph: Duration) {
    init {
        require(!hold.isNegative()) { "A hold can't be negative, not $hold" }
        require(morph.isPositive()) { "A morph must take some time, not $morph" }
    }

    /** One morph with half a hold on either side: the stretch from one spec's rest to the next's. */
    val leg: Duration get() = hold + morph
}

/**
 * A sample's specs and morph easings by name, and its morphs' timing. Asking for a name the sample doesn't
 * declare, or for the timing of a sample without morphs, is a programming error and throws.
 */
class SampleSpecs(
    private val specs: Map<String, MorpheusGraphSpec>,
    private val morphs: Map<String, MorphEasing> = emptyMap(),
    private val morphTiming: MorphTiming? = null,
) {
    operator fun get(name: String): MorpheusGraphSpec =
        requireNotNull(specs[name]) { "No spec named \"$name\"; the sample declares ${specs.keys}" }

    fun easing(name: String): Easing =
        requireNotNull(morphs[name]) { "No morph named \"$name\"; the sample declares ${morphs.keys}" }.easing

    val timing: MorphTiming get() = requireNotNull(morphTiming) { "The sample names no morphs, so it has no timing" }
}

private val KEBAB_CASE = Regex("[a-z0-9]+(-[a-z0-9]+)*")
