package com.rossomak.morpheus.samples.capture

import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class SelectSamplesTest {
    private val first = sample("first")
    private val galleryOnly = sample("gallery-only", galleryOnly = true)
    private val second = sample("second")
    private val registry = listOf(first, galleryOnly, second)

    @Test
    fun `an id selects that sample`() {
        selectSamples(listOf("second"), registry) shouldBe listOf(second)
    }

    @Test
    fun `all selects every sample that isn't gallery-only, in registry order`() {
        selectSamples(listOf("all"), registry) shouldBe listOf(first, second)
    }

    @Test
    fun `an unknown id lists the valid ids`() {
        shouldThrow<CaptureException> { selectSamples(listOf("third"), registry) }.message shouldContain "first, second, all"
    }

    @Test
    fun `a gallery-only id says the sample is gallery-only`() {
        shouldThrow<CaptureException> { selectSamples(listOf("gallery-only"), registry) }.message shouldContain "gallery-only, so it has no media"
    }

    @Test
    fun `no id or more than one lists the valid ids`() {
        shouldThrow<CaptureException> { selectSamples(emptyList(), registry) }.message shouldContain "first, second, all"
        shouldThrow<CaptureException> { selectSamples(listOf("first", "second"), registry) }.message shouldContain "first, second, all"
    }

    private fun sample(id: String, galleryOnly: Boolean = false) =
        Sample(id = id, title = id, description = id, category = SampleCategory.Catalog, galleryOnly = galleryOnly, content = {})
}
