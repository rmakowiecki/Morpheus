package com.rossomak.morpheus

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaPaint

// Skia takes whole arrays: each batch goes in as its buffer's exact-length copy, kept across frames.

internal actual fun Canvas.drawLineBatch(batch: PointBuffer, paint: Paint) {
    skiaCanvas.drawLines(batch.exactFloats(), paint.skiaPaint)
}

internal actual fun Canvas.drawPointBatch(batch: PointBuffer, paint: Paint) {
    skiaCanvas.drawPoints(batch.exactFloats(), paint.skiaPaint)
}
