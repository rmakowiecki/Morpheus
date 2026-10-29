@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerContext
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec
import com.rossomak.morpheus.samples.resources.Res
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val IMAGE_BRUSH = "Image brush"

/** Fewer, bigger, brighter nodes and thick, opaque edges, so there is enough paint for the image to show through. */
private val imageBrushSpec = MorpheusGraphSpec(
    seed = 15,
    layers = listOf(
        fillSampleLayer.copy(
            density = 60f,
            driftAmplitude = 0.7f,
            driftSpeed = 1.5f,
            waveAmplitude = 0.02f,
            waveSpeed = 0.4f,
            strokeWidthDp = 1.8f,
            edgeAlpha = 0.9f,
            lengthFalloff = 0.2f,
            nodeSizeDp = 3f,
            nodeSizeVariance = 0.4f,
            hotNodeShare = 0.3f,
            glowRadiusDp = 26f,
            glowStrength = 0.5f,
        ),
    ),
)

/** The photograph the sample paints with, decoded by [load] and kept for as long as the process lives. */
object ImageBrushSource {
    var image: ImageBitmap? = null
        private set

    /** Decodes the image unless an earlier call already did, since the gallery prepares a sample each time it opens. */
    suspend fun load() {
        if (image == null) image = Res.readBytes("drawable/image_brush.jpg").decodeToImageBitmap()
    }
}

/** The centered crop of an image of [source] size that covers [target] without stretching it. */
fun coverCrop(source: IntSize, target: IntSize): IntRect {
    val scale = max(target.width.toFloat() / source.width, target.height.toFloat() / source.height)
    val width = min(source.width, (target.width / scale).roundToInt())
    val height = min(source.height, (target.height / scale).roundToInt())
    return IntRect(IntOffset((source.width - width) / 2, (source.height - height) / 2), IntSize(width, height))
}

/** [image] scaled and cropped to cover a surface of [size], or null when the surface is empty. */
fun fitCover(image: ImageBitmap, size: IntSize): ImageBitmap? {
    if (size.width <= 0 || size.height <= 0) return null
    val fitted = ImageBitmap(size.width, size.height)
    val crop = coverCrop(IntSize(image.width, image.height), size)
    val paint = Paint().apply { filterQuality = FilterQuality.Medium }
    Canvas(fitted).drawImageRect(image, crop.topLeft, crop.size, IntOffset.Zero, size, paint)
    return fitted
}

private class FittedBrush(val size: IntSize, val brush: Brush)

private val emptyBrush = SolidColor(Color.Transparent)

/**
 * Compose's `ImageShader` takes no matrix, so the image can't be scaled by the shader. This keeps a copy already
 * fitted to the surface, rebuilt only when the surface changes size.
 */
private class ImageFill(private val image: ImageBitmap) : MorpheusGraphFill.Custom {
    private var fitted: FittedBrush? = null

    override fun brushFor(context: MorpheusGraphLayerContext): Brush {
        val size = IntSize(ceil(context.size.width).toInt(), ceil(context.size.height).toInt())
        fitted?.takeIf { it.size == size }?.let { return it.brush }
        val brush = fitCover(image, size)?.let { ShaderBrush(ImageShader(it)) } ?: emptyBrush
        fitted = FittedBrush(size, brush)
        return brush
    }
}

/** An image brush when the image loaded, a solid fill when it didn't. */
@Composable
fun ImageBrushSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = imageBrushSpec) {
    val image = ImageBrushSource.image
    val fill = remember(image) { image?.let(::ImageFill) ?: MorpheusGraphFill.Solid(SampleStyle.blue) }
    SampleGraph(spec, modifier, fill)
}

internal val imageBrushSample = Sample(
    id = "image-brush",
    title = "Image brush",
    description = "A custom fill: a photograph shows through the nodes and edges.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(IMAGE_BRUSH, imageBrushSpec)),
    prepare = { ImageBrushSource.load() },
    content = { specs -> ImageBrushSample(spec = specs[IMAGE_BRUSH]) },
)
