package com.rossomak.morpheus

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun Canvas.drawLineBatch(points: FloatArray, count: Int, paint: Paint) {
    nativeCanvas.drawLines(points, 0, count, paint.asFrameworkPaint())
}

internal actual fun Canvas.drawPointBatch(points: FloatArray, count: Int, paint: Paint) {
    nativeCanvas.drawPoints(points, 0, count, paint.asFrameworkPaint())
}
