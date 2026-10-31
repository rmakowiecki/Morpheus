package com.rossomak.morpheus

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** What [content] draws over black onto a [width] by [height] image, as ARGB pixels. */
internal fun drawOverBlack(width: Int, height: Int, content: DrawScope.() -> Unit): IntArray {
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawRect(Color.Black)
        content()
    }
    return IntArray(width * height).also { bitmap.readPixels(it) }
}
