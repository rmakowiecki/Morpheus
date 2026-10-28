package com.rossomak.morpheus

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.ceil
import kotlin.math.min

/** The levels an 8-bit alpha takes, 0 to 255. */
private const val ALPHA_LEVELS = 255

/** The size bins a layer's dot radii are split into, between the smallest and largest radius its spec allows. */
internal const val DOT_SIZE_BINS = 8

/** Dot alphas round up to a multiple of this many levels out of 255, so a dot is drawn at most two levels too opaque. */
internal const val DOT_ALPHA_STEP = 2

/** The dot alpha buckets: 128, the last one capped at 255. */
private const val DOT_ALPHA_BUCKETS = (ALPHA_LEVELS + DOT_ALPHA_STEP - 1) / DOT_ALPHA_STEP

/** A radius range narrower than this, in pixels, is one size bin: every dot is the same size. */
private const val SAME_RADIUS_EPSILON = 1e-3f

/** Half a level: added before truncating, it rounds to the nearest level. */
private const val HALF_LEVEL = 0.5f

/** Half way across a size bin, where its dots' radius is. */
private const val BIN_CENTER = 0.5f

/** The float count a buffer starts at: 16 points, or 8 lines. */
private const val INITIAL_FLOATS = 32

/**
 * One layer's edges and dots for [MorpheusGraphDrawingApproach.PointBatches], refilled every frame: each
 * edge goes into the batch of its 8-bit alpha level, each dot into a punch batch per size bin and an add
 * batch per alpha bucket and size bin. Each non-empty batch is one draw call: up to 255 for the edges, 8
 * for the punches and 1,024 for the adds, though a frame uses a fraction of them.
 *
 * Dot alphas round up to a multiple of [DOT_ALPHA_STEP] levels, so a dot is never drawn fainter than its own
 * 8-bit level and stays at least as opaque as every edge it anchors, which round to nearest. The buffers only
 * grow, to the largest batch seen; on Android a frame allocates nothing, while Skia on desktop and the web
 * takes each batch as an array copy of its own length.
 */
internal class PointBatches {
    private val edgeBatches = arrayOfNulls<PointBuffer>(ALPHA_LEVELS + 1)
    private val punchBatches = arrayOfNulls<PointBuffer>(DOT_SIZE_BINS)
    private val addBatches = arrayOfNulls<PointBuffer>(DOT_ALPHA_BUCKETS * DOT_SIZE_BINS)
    private var smallestRadius = 0f

    /** Built on first draw: on desktop a paint needs the native graphics library, which counting alone never loads. */
    private val paint by lazy(LazyThreadSafetyMode.NONE) { Paint().apply { isAntiAlias = true } }

    /** The radius span of one size bin as of the last [clearDots], in pixels; 0 when every dot is the same size. */
    var sizeBinWidth = 0f
        private set

    /** Draw calls made since [resetDrawCalls]. */
    var drawCalls = 0
        private set

    val edgeCalls: Int get() = edgeBatches.nonEmptyCount()
    val punchCalls: Int get() = punchBatches.nonEmptyCount()
    val addCalls: Int get() = addBatches.nonEmptyCount()

    fun resetDrawCalls() {
        drawCalls = 0
    }

    fun clearEdges() {
        edgeBatches.forEach { it?.clear() }
    }

    /** Adds the edge from ([startX], [startY]) to ([endX], [endY]), drawn at [alpha]'s [edgeLevel]. */
    fun addEdge(startX: Float, startY: Float, endX: Float, endY: Float, alpha: Float) {
        edgeBatches.bufferAt(edgeLevel(alpha)).addLine(startX, startY, endX, endY)
    }

    /** Draws each edge batch in [color] at its level, [strokeWidth] wide with butt caps, as `drawLine` does. */
    fun drawEdges(canvas: Canvas, color: Color, strokeWidth: Float) {
        paint.blendMode = BlendMode.SrcOver
        paint.strokeCap = StrokeCap.Butt
        paint.strokeWidth = strokeWidth
        for (level in 1..ALPHA_LEVELS) {
            val batch = edgeBatches.nonEmptyAt(level) ?: continue
            paint.color = color.copy(alpha = level / ALPHA_LEVELS.toFloat())
            canvas.drawLineBatch(batch.floats, batch.size, paint)
            drawCalls++
        }
    }

    /**
     * Empties the dot batches and splits [smallestRadius] to [largestRadius] into the size bins. The range
     * comes from the spec, not the frame's dots, so the bins hold still from frame to frame.
     */
    fun clearDots(smallestRadius: Float, largestRadius: Float) {
        punchBatches.forEach { it?.clear() }
        addBatches.forEach { it?.clear() }
        this.smallestRadius = smallestRadius
        sizeBinWidth = if (largestRadius - smallestRadius < SAME_RADIUS_EPSILON) 0f else (largestRadius - smallestRadius) / DOT_SIZE_BINS
    }

    /** Adds the dot at ([x], [y]), drawn at its [sizeBin]'s [binRadius] and at [alpha]'s [dotLevel]. */
    fun addDot(x: Float, y: Float, radius: Float, alpha: Float) {
        val bin = sizeBin(radius)
        punchBatches.bufferAt(bin).add(x, y)
        addBatches.bufferAt(dotBucket(alpha) * DOT_SIZE_BINS + bin).add(x, y)
    }

    /** The size bin a dot of [radius] falls in, as of the last [clearDots]. */
    fun sizeBin(radius: Float): Int = if (sizeBinWidth == 0f) 0 else ((radius - smallestRadius) / sizeBinWidth).toInt().coerceIn(0, DOT_SIZE_BINS - 1)

    /** Size bin [bin]'s center radius, which every dot in it is drawn at. */
    fun binRadius(bin: Int): Float = smallestRadius + (bin + BIN_CENTER) * sizeBinWidth

    /** The 8-bit level a dot at [alpha] is drawn at: rounded up to a multiple of [DOT_ALPHA_STEP], at most 255. */
    fun dotLevel(alpha: Float): Int = bucketLevel(dotBucket(alpha))

    private fun bucketLevel(bucket: Int): Int = min(ALPHA_LEVELS, (bucket + 1) * DOT_ALPHA_STEP)

    private fun dotBucket(alpha: Float): Int = (ceil(alpha * ALPHA_LEVELS / DOT_ALPHA_STEP).toInt() - 1).coerceIn(0, DOT_ALPHA_BUCKETS - 1)

    /**
     * Draws the dots as round points in [color]: every punch first, at alpha 1 with [BlendMode.DstOut], then
     * every add at its bucket's alpha with [BlendMode.Plus]. One dot's punch and add share its bin's radius,
     * or a ring would show at its rim.
     */
    fun drawDots(canvas: Canvas, color: Color) {
        paint.strokeCap = StrokeCap.Round
        paint.blendMode = BlendMode.DstOut
        paint.color = color.copy(alpha = 1f)
        for (bin in punchBatches.indices) {
            val batch = punchBatches.nonEmptyAt(bin) ?: continue
            paint.strokeWidth = 2f * binRadius(bin)
            canvas.drawPointBatch(batch.floats, batch.size, paint)
            drawCalls++
        }
        paint.blendMode = BlendMode.Plus
        for (addBatchIndex in addBatches.indices) {
            val batch = addBatches.nonEmptyAt(addBatchIndex) ?: continue
            paint.color = color.copy(alpha = bucketLevel(addBatchIndex / DOT_SIZE_BINS) / ALPHA_LEVELS.toFloat())
            paint.strokeWidth = 2f * binRadius(addBatchIndex % DOT_SIZE_BINS)
            canvas.drawPointBatch(batch.floats, batch.size, paint)
            drawCalls++
        }
    }

    private fun Array<PointBuffer?>.bufferAt(index: Int): PointBuffer = this[index] ?: PointBuffer().also { this[index] = it }

    private fun Array<PointBuffer?>.nonEmptyAt(index: Int): PointBuffer? = this[index]?.takeIf { it.size > 0 }

    private fun Array<PointBuffer?>.nonEmptyCount(): Int = indices.count { nonEmptyAt(it) != null }

    companion object {
        /** The 8-bit level an edge at [alpha] is drawn at: the nearest, as a color stores it, at least 1. */
        fun edgeLevel(alpha: Float): Int = (alpha * ALPHA_LEVELS + HALF_LEVEL).toInt().coerceIn(1, ALPHA_LEVELS)
    }
}

/** X and y pairs, refilled every frame: [clear] keeps the array, which only grows. */
internal class PointBuffer {
    /** Pairs in the first [size] values. */
    var floats = FloatArray(INITIAL_FLOATS)
        private set

    var size = 0
        private set

    fun add(x: Float, y: Float) {
        if (size + 2 > floats.size) floats = floats.copyOf(floats.size * 2)
        floats[size] = x
        floats[size + 1] = y
        size += 2
    }

    fun addLine(startX: Float, startY: Float, endX: Float, endY: Float) {
        add(startX, startY)
        add(endX, endY)
    }

    fun clear() {
        size = 0
    }
}

/*
 * Compose's own drawRawPoints draws one line or point at a time on Skia, so batches go to the platform
 * canvas directly: Skia's drawLines and drawPoints on desktop and the web, android.graphics.Canvas's on Android.
 */

/** Draws the first [count] values of [points], each four a line from one point to the next, in one call. */
internal expect fun Canvas.drawLineBatch(points: FloatArray, count: Int, paint: Paint)

/** Draws the first [count] values of [points], each two a point, in one call. */
internal expect fun Canvas.drawPointBatch(points: FloatArray, count: Int, paint: Paint)
