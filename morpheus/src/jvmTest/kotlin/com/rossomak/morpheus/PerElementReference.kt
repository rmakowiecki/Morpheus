package com.rossomak.morpheus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * [field] drawn per element, as [LayerField.draw] drew it before point batches: into one offscreen layer, each
 * edge through `drawLine`, then the halos, then each dot as a [BlendMode.DstOut] punch and a [BlendMode.Plus]
 * add at its own radius and alpha. In solid [color], at [timeSeconds]; [edgesOnly] skips halos and dots, and
 * [edgesInLevelOrder] draws the edges in alpha-level order, as point batches do. The reference
 * [PointBatchesCaptureTest] compares point batches against.
 */
internal fun DrawScope.drawPerElement(field: LayerField, color: Color, halos: HaloBrushCache, timeSeconds: Float, edgesOnly: Boolean = false, edgesInLevelOrder: Boolean = false) {
    drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
    val edges = ArrayList<Triple<Float, Int, Int>>()
    field.forEachVisibleEdge(timeSeconds) { alpha, start, end -> edges += Triple(alpha, start, end) }
    if (edgesInLevelOrder) edges.sortBy { (alpha) -> PointBatches.edgeLevel(alpha) }
    for ((alpha, start, end) in edges) {
        drawLine(color = color.copy(alpha = alpha), start = field.nodePosition(start), end = field.nodePosition(end), strokeWidth = field.edgeStrokeWidth)
    }
    if (!edgesOnly) {
        with(field) { drawGlows(halos) }
        field.forEachVisibleDot { node, alpha ->
            val radius = field.dotRadius(node)
            val center = field.nodePosition(node)
            drawCircle(color = color, radius = radius, center = center, blendMode = BlendMode.DstOut)
            drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center, blendMode = BlendMode.Plus)
        }
    }
    drawIntoCanvas { it.restore() }
}
