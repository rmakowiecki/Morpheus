package com.rossomak.morpheus.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.rossomak.morpheus.MorpheusGraphSpec

/**
 * One registry entry: a sample composable plus what the gallery and the capture tool need to know about it.
 *
 * [id] is kebab-case and names the sample's media files. [specs] are the specs the gallery can tune, by
 * name; [content] receives them through [SampleSpecs], tuned in the gallery and at their defaults in a
 * capture, and passes them to the sample composable as plain parameters. A [galleryOnly] sample runs in
 * the gallery but produces no media. [prepare] loads anything asynchronous, such as an image, before
 * [content] first composes.
 */
class Sample(
    val id: String,
    val title: String,
    val description: String,
    val category: SampleCategory,
    val recipe: CaptureRecipe = CaptureRecipe(),
    val specs: List<TunableSpec> = emptyList(),
    val galleryOnly: Boolean = false,
    val prepare: suspend () -> Unit = {},
    val content: @Composable (specs: SampleSpecs) -> Unit,
) {
    init {
        require(KEBAB_CASE.matches(id)) { "Sample id \"$id\" isn't kebab-case" }
        val duplicates = specs.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Sample \"$id\" names more than one spec $duplicates" }
    }

    /** Every spec at its default, as a capture renders it. */
    val defaultSpecs: SampleSpecs get() = SampleSpecs(specs.associate { it.name to it.default })

    override fun toString(): String = "Sample($id)"
}

/** Showcases are the eye-catchers; the catalog covers one capability per sample. */
enum class SampleCategory { Showcase, Catalog }

/** A spec the gallery can tune, under the [name] its sample's content asks for it by. */
@Immutable
data class TunableSpec(val name: String, val default: MorpheusGraphSpec)

/** A sample's specs by name. Asking for a name the sample doesn't declare is a programming error and throws. */
class SampleSpecs(private val specs: Map<String, MorpheusGraphSpec>) {
    operator fun get(name: String): MorpheusGraphSpec =
        requireNotNull(specs[name]) { "No spec named \"$name\"; the sample declares ${specs.keys}" }
}

private val KEBAB_CASE = Regex("[a-z0-9]+(-[a-z0-9]+)*")
