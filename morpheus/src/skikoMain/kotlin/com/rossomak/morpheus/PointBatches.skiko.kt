package com.rossomak.morpheus

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaPaint

internal actual fun Canvas.drawLineBatch(points: FloatArray, count: Int, paint: Paint) {
    skiaCanvas.drawLines(exactly(points, count), paint.skiaPaint)
}

internal actual fun Canvas.drawPointBatch(points: FloatArray, count: Int, paint: Paint) {
    skiaCanvas.drawPoints(exactly(points, count), paint.skiaPaint)
}

/** Skia takes a whole array, so a batch shorter than its buffer goes in as a copy of its own length. */
private fun exactly(points: FloatArray, count: Int): FloatArray = if (count == points.size) points else points.copyOf(count)
