@file:OptIn(ExperimentalMorpheusGraphApi::class)

package com.rossomak.morpheus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

/**
 * Renders layers headlessly on the recipe canvas and checks what a viewer sees of the dots: that overlapping dots
 * keep the mask's color, and that no dot is dimmer at its center than the edges and halo alone would be there.
 */
class DotColourTest {

    @Test
    fun `on a solid mask, dots keep the mask's hue and leave no hole`() {
        check(LayerFill.Solid(MASK), judgeHue = true)
    }

    @Test
    fun `with a recoloring fill, no dot is dimmer than the edges and halo under it`() {
        check(LayerFill.Masked(SolidColor(RECOLOR)), judgeHue = false)
    }

    @Test
    fun `with an image brush, no dot is dimmer than the edges and halo under it`() {
        check(LayerFill.Masked(imageBrush()), judgeHue = false)
    }

    private fun check(fill: LayerFill, judgeHue: Boolean) {
        val failures = SETTLED.flatMap { (name, fields) ->
            fields.flatMap { (slot, field) -> failuresOf("$name slot $slot", field, fill, judgeHue) }
        }
        withClue(failures.joinToString("\n")) { failures.size shouldBe 0 }
    }

    /** What is wrong with [field] drawn in [fill], tagged with [case]; [judgeHue] also checks the hue, for a solid mask. */
    private fun failuresOf(case: String, field: LayerField, fill: LayerFill, judgeHue: Boolean): List<String> {
        val withDots = render(field, fill, skipDots = false)
        val withoutDots = render(field, fill, skipDots = true)
        val failures = ArrayList<String>()
        if (judgeHue) {
            val offHue = offHuePixels(withDots)
            if (offHue != 0) failures += "$case: $offHue pixels off the mask's hue"
        }
        val (holes, dots) = dotsDimmerThanUnder(field, withDots, withoutDots)
        if (holes != 0) failures += "$case: $holes of $dots dots dimmer than what lies under them"
        return failures
    }

    /** How many of [field]'s dots on the canvas come out dimmer at their center than [withoutDots] is there, and how many dots that is out of. */
    private fun dotsDimmerThanUnder(field: LayerField, withDots: IntArray, withoutDots: IntArray): Pair<Int, Int> {
        var dots = 0
        var holes = 0
        field.forEachVisibleDot { node, _ ->
            val center = field.nodePosition(node)
            if (center.x < 0f || center.y < 0f || center.x >= WIDTH || center.y >= HEIGHT) return@forEachVisibleDot
            dots++
            val pixel = center.y.toInt() * WIDTH + center.x.toInt()
            if (brightness(withDots[pixel]) < (1f - HOLE_SHARE) * brightness(withoutDots[pixel])) holes++
        }
        return holes to dots
    }

    /** [field] drawn in [fill] over black, as ARGB pixels; [skipDots] leaves out the dots. */
    private fun render(field: LayerField, fill: LayerFill, skipDots: Boolean): IntArray {
        val stats = MorpheusGraphFrameStats().apply { this.skipDots = skipDots }
        stats.probe.startLayer(0, field)
        return drawOverBlack(WIDTH, HEIGHT) {
            field.draw(this, fill, HaloBrushCache(fill.maskColor), SETTLE_FRAMES * FRAME_SECONDS, showViolations = false, probe = stats.probe)
        }
    }

    /** How many pixels bright enough to judge have a red to blue ratio away from [MASK]'s. */
    private fun offHuePixels(pixels: IntArray): Int = pixels.count { argb ->
        val blue = argb and 0xFF
        blue >= MIN_JUDGED_BLUE && abs((argb shr 16 and 0xFF) * MASK.blue - MASK.red * blue) > HUE_TOLERANCE * MASK.blue * blue
    }

    private fun brightness(argb: Int): Float = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3f

    /** A tiled two-tone diagonal image, drawn as a brush the way an app's own image would be. */
    private fun imageBrush(): Brush {
        val image = ImageBitmap(IMAGE_SIZE, IMAGE_SIZE)
        val corner = Offset(IMAGE_SIZE.toFloat(), IMAGE_SIZE.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(corner.x, corner.y)) {
            drawRect(Brush.linearGradient(listOf(IMAGE_LIGHT, IMAGE_DARK), Offset.Zero, corner))
        }
        return ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
    }

    private companion object {
        val MASK = Color(0xFF5B8CE0)
        val RECOLOR = Color(0xFFE8A070)
        val IMAGE_LIGHT = Color(0xFFF0C080)
        val IMAGE_DARK = Color(0xFF3070C0)
        val WIDTH = BenchmarkCanvas.Recipe.width.toInt()
        val HEIGHT = BenchmarkCanvas.Recipe.height.toInt()
        const val SETTLE_FRAMES = 30
        const val FRAME_SECONDS = 1f / 60f
        const val MID_FRACTION = 0.5f
        const val IMAGE_SIZE = 64

        /** Pixels this dark are too coarse to judge a hue from. */
        const val MIN_JUDGED_BLUE = 60
        const val HUE_TOLERANCE = 0.12f

        /** A dot this much dimmer than what lies under it reads as a hole. */
        const val HOLE_SHARE = 0.12f

        /** Each scenario's layer fields, settled; built once and drawn by every test. */
        val SETTLED: Map<String, Map<Int, LayerField>> by lazy { scenarios().mapValues { (_, morph) -> fieldsOf(morph) } }

        fun scenarios(): Map<String, MorpheusGraphMorph> = mapOf(
            "playground still" to GalleryLoop.still(playgroundFrame),
            "playground blooms half way" to MorpheusGraphMorph(playgroundFrame, playgroundBlooms, MID_FRACTION),
            "hero pills still" to GalleryLoop.still(heroPills),
            "hero half way" to MorpheusGraphMorph(heroPills, heroRibbons, MID_FRACTION),
        )

        fun fieldsOf(morph: MorpheusGraphMorph): Map<Int, LayerField> {
            val canvas = BenchmarkCanvas.Recipe
            val fields = HashMap<Int, LayerField>()
            for (frame in 0 until SETTLE_FRAMES) {
                for (layer in morphLayers(morph, GalleryLoop.FILL)) {
                    val field = fields.getOrPut(layer.slotFromFront) { LayerField(layer.spec, layer.seed, canvas.referenceSize, canvas.pxPerDp) }
                    field.spec = layer.spec
                    field.shape = layer.shape
                    field.advance(frame * FRAME_SECONDS, canvas.width, canvas.height)
                }
            }
            return fields
        }
    }
}
