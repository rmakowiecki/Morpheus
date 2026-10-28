package com.rossomak.morpheus

/**
 * How each layer draws its edges and dots, chosen through [MorpheusGraphFrameStats.drawingApproach] while the
 * approaches are compared. Halos, the fill's recolor and the debug overlays draw the same in every approach.
 */
@ExperimentalMorpheusGraphApi
public enum class MorpheusGraphDrawingApproach {
    /** One draw call per edge, and a punch and an add per dot. The default. */
    PerElement,

    /**
     * Edges and dots in batches, one draw call each. Edges are drawn as [PerElement] draws them, but in alpha
     * order, which can move a pixel by a few levels where edges overlap. Each dot's alpha rounds up to the next
     * even level out of 255 and its radius to its size bin's center; where dots overlap, their alphas sum.
     */
    PointBatches,
}
