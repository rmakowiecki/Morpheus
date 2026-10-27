package com.rossomak.morpheus

/**
 * Which overlays a running morph's comets draw over each layer, to see how they were planned. A still frame
 * draws none: pieces, streams and heads exist only while a morph runs. With every overlay off, debugging
 * costs nothing: no overlay draws, and the data only overlays read is built the first time one asks for it.
 */
@ExperimentalMorpheusGraphApi
public data class MorpheusGraphCometDebug(
    /**
     * A marker on every node of each piece of the leaving and arriving nodes, one color per piece: squares on
     * the leaving pieces, dots on the arriving ones, grey for a piece that sends or takes no stream.
     */
    val pieces: Boolean = false,
    /** A cross at each piece's centroid, and a thin line from each leaving piece to each arriving piece it streams to. */
    val centroids: Boolean = false,
    /** A ring where each head is at the morph's fraction, while it travels in its window. */
    val heads: Boolean = false,
    /** A dot where each head lands. */
    val targets: Boolean = false,
    /** A faint line along each head's path. */
    val paths: Boolean = false,
    /** Each head's disc where it leaves and where it lands, at those two radii, to check it fits each shape. */
    val endDiscs: Boolean = false,
    /** The start shape's cells in one tint and the end shape's in another, the deeper inside, the stronger. */
    val clearance: Boolean = false,
) {
    /** True when any overlay is on. */
    val any: Boolean get() = pieces || centroids || heads || targets || paths || endDiscs || clearance

    /** Every overlay either this or [other] turns on. */
    public infix fun or(other: MorpheusGraphCometDebug): MorpheusGraphCometDebug = MorpheusGraphCometDebug(
        pieces = pieces || other.pieces,
        centroids = centroids || other.centroids,
        heads = heads || other.heads,
        targets = targets || other.targets,
        paths = paths || other.paths,
        endDiscs = endDiscs || other.endDiscs,
        clearance = clearance || other.clearance,
    )

    public companion object {
        public val None: MorpheusGraphCometDebug = MorpheusGraphCometDebug()
        public val All: MorpheusGraphCometDebug = MorpheusGraphCometDebug(
            pieces = true,
            centroids = true,
            heads = true,
            targets = true,
            paths = true,
            endDiscs = true,
            clearance = true,
        )
    }
}
