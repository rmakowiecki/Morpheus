package com.rossomak.morpheus.samples.capture

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.rossomak.morpheus.samples.SampleRegistry
import com.rossomak.morpheus.samples.catalog.ImageBrushSource
import com.rossomak.morpheus.samples.catalog.coverCrop
import com.rossomak.morpheus.samples.catalog.fitCover
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class ImageBrushTest {
    @Test
    fun `preparing the image brush sample loads its image, so the solid fallback can't hide a missing one`() {
        val sample = SampleRegistry.samples.single { it.id == "image-brush" }
        runBlocking { sample.prepare() }
        (ImageBrushSource.image != null) shouldBe true
        // A second preparation, as the gallery does on every open, keeps the image it has.
        val loaded = ImageBrushSource.image
        runBlocking { sample.prepare() }
        (ImageBrushSource.image === loaded) shouldBe true
    }

    @Test
    fun `a wider surface crops the top and bottom of the image`() {
        coverCrop(source = IntSize(1000, 1000), target = IntSize(400, 200)) shouldBe IntRect(IntOffset(0, 250), IntSize(1000, 500))
    }

    @Test
    fun `a taller surface crops the sides of the image`() {
        coverCrop(source = IntSize(1000, 500), target = IntSize(200, 200)) shouldBe IntRect(IntOffset(250, 0), IntSize(500, 500))
    }

    @Test
    fun `a surface of the image's own shape shows all of it`() {
        coverCrop(source = IntSize(1024, 682), target = IntSize(512, 341)) shouldBe IntRect(IntOffset(0, 0), IntSize(1024, 682))
    }

    @Test
    fun `the image is fitted to the surface's size`() {
        val fitted = fitCover(ImageBitmap(8, 4), IntSize(6, 6))
        fitted?.width shouldBe 6
        fitted?.height shouldBe 6
    }

    @Test
    fun `an empty surface gets no fitted image`() {
        fitCover(ImageBitmap(8, 4), IntSize.Zero) shouldBe null
        fitCover(ImageBitmap(8, 4), IntSize(0, 10)) shouldBe null
        fitCover(ImageBitmap(8, 4), IntSize(10, 0)) shouldBe null
    }
}
