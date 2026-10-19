package com.rossomak.morpheus.samples.capture

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** A rendered frame: [width] × [height] `0xAARRGGBB` pixels, row by row. */
class Frame(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "A $width × $height frame needs ${width * height} pixels, not ${argb.size}" }
    }

    val isUniform: Boolean get() = argb.all { it == argb[0] }

    fun contentEquals(other: Frame): Boolean = width == other.width && height == other.height && argb.contentEquals(other.argb)

    /** The mean absolute difference per color channel from [other], 0 to 255. */
    fun meanChannelDifference(other: Frame): Double {
        require(width == other.width && height == other.height) { "Can't compare a $width × $height frame with a ${other.width} × ${other.height} one" }
        var sum = 0L
        for (index in argb.indices) {
            val a = argb[index]
            val b = other.argb[index]
            for (shift in CHANNEL_SHIFTS) sum += kotlin.math.abs((a shr shift and CHANNEL_MASK) - (b shr shift and CHANNEL_MASK))
        }
        return sum.toDouble() / (argb.size * CHANNEL_SHIFTS.size)
    }

    fun encodePng(): ByteArray {
        // 0xAARRGGBB in little-endian order is B, G, R, A.
        val bytes = ByteBuffer.allocate(argb.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(argb) }.array()
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        return Image.makeRaster(info, bytes, width * Int.SIZE_BYTES).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "Skia couldn't encode a $width × $height PNG" }.bytes
        }
    }
}

/** [from] blended [step] of [steps] of the way to [to], in rounded integer arithmetic, so it is the same on every run. */
fun blend(from: Frame, to: Frame, step: Int, steps: Int): Frame {
    require(from.width == to.width && from.height == to.height) { "Can't blend a ${from.width} × ${from.height} frame with a ${to.width} × ${to.height} one" }
    require(step in 0..steps && steps > 0) { "Step $step isn't within 0..$steps" }
    val pixels = IntArray(from.argb.size) { index ->
        val a = from.argb[index]
        val b = to.argb[index]
        var pixel = 0
        for (shift in ALL_SHIFTS) {
            val channel = ((a shr shift and CHANNEL_MASK) * (steps - step) + (b shr shift and CHANNEL_MASK) * step + steps / 2) / steps
            pixel = pixel or (channel shl shift)
        }
        pixel
    }
    return Frame(from.width, from.height, pixels)
}

private const val CHANNEL_MASK = 0xFF
private val CHANNEL_SHIFTS = intArrayOf(16, 8, 0)
private val ALL_SHIFTS = intArrayOf(24, 16, 8, 0)
