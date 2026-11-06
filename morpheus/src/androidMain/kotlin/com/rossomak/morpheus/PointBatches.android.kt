package com.rossomak.morpheus

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun Canvas.drawLineBatch(batch: PointBuffer, paint: Paint) {
    nativeCanvas.drawLines(batch.floats, 0, batch.size, paint.asFrameworkPaint())
}

internal actual fun Canvas.drawPointBatch(batch: PointBuffer, paint: Paint) {
    nativeCanvas.drawPoints(batch.floats, 0, batch.size, paint.asFrameworkPaint())
}
