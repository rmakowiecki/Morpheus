@file:Suppress("LoopWithTooManyJumpStatements", "TooManyFunctions")

package com.rossomak.morpheus

import kotlin.math.max
import kotlin.math.min

/**
 * Safety cap on [enforceMinDegree]'s repair rounds. Every pair can be added and removed at most once
 * per call, so the rounds converge on their own; the cap only bounds a bug.
 */
internal const val MAX_ENFORCEMENT_ROUNDS = 8

private const val TRIANGLE_CORNERS = 3

/**
 * The fewest nodes a separate graph may have: a smaller one reads as a stray scrap next to the mesh.
 * Also the fewest nodes a piece hanging from a cut node keeps when [enforceMinDegree] cuts it loose
 * instead of pruning it.
 */
internal const val MIN_GRAPH_NODES = 6

/**
 * The edge set a layer actually draws, after [enforceMinDegree].
 *
 * [alive] is false for every pruned node. [converged] is false only when the repair rounds hit
 * [MAX_ENFORCEMENT_ROUNDS] and the removal-only terminal pass had to finish the job.
 */
internal class MeshTopology(
    val edges: LongArray,
    val alive: BooleanArray,
    val repairedEdgeCount: Int,
    val prunedNodeCount: Int,
    val converged: Boolean,
)

/** Which edges of an edge list [MeshWorkspace.enforceMinDegree] takes; an interface, so a key is never boxed. */
internal fun interface EdgeFilter {
    fun keeps(key: Long): Boolean

    companion object {
        val All = EdgeFilter { true }
    }
}

/**
 * Enforces the network-graph invariant: every node that is drawn has at least two edges. A node with
 * one edge reads as a dangling tail, a node with none as a stray dot, and no slider setting is
 * allowed to produce either.
 *
 * Starting from [baseEdges] (what the edge rule and its filters kept), each round:
 * 1. with [strictTriangles], every edge whose ends share no neighbour is closed into a triangle by
 *    adding a third edge from [candidateEdges], or dropped when none fits, so the mesh has no strings
 *    or bridges, only faces;
 * 2. every node short of two edges is repaired from [candidateEdges] no longer than
 *    [maxRepairLength], shortest first but preferring an edge that closes a triangle (with
 *    [strictTriangles], only triangle-closing repairs are allowed);
 * 3. every node still short is pruned, along with its edges;
 * 4. once those settle, and with [keepConnectivity], every cut node (one whose removal splits its graph, as at either end of a
 *    bridge) gets an edge between two of its neighbours on either side, closing a triangle across it,
 *    or without [strictTriangles] any candidate edge across; a piece that still hangs from it is
 *    pruned when smaller than [MIN_GRAPH_NODES] and cut loose otherwise. Every separate graph smaller
 *    than [MIN_GRAPH_NODES] is pruned.
 *
 * With [strictTriangles] and [triangles] (the Delaunay triangles the candidates come from), every
 * triangle with all three corners kept and two of its edges also gets its third, so no face of the mesh
 * is a polygon: a dropped diagonal comes back unless it is longer than [maxRepairLength].
 *
 * Repair edges bypass the rule's keep chance and minimum-angle filters but not the length cap, so a
 * sparse area keeps its nodes connected while a far outlier disappears instead of growing a long line.
 * A pair removed during the call is never re-added, which is what makes the rounds converge. A final
 * removal-only pass guarantees the invariants even if they did not.
 *
 * Runs on a [MeshWorkspace] of its own; a layer settling every frame reuses one instead.
 */
internal fun enforceMinDegree(
    count: Int,
    xs: FloatArray,
    ys: FloatArray,
    baseEdges: LongArray,
    candidateEdges: LongArray,
    maxRepairLength: Float,
    strictTriangles: Boolean,
    triangles: IntArray? = null,
    keepConnectivity: Boolean = true,
): MeshTopology = MeshWorkspace().enforceMinDegree(
    count = count,
    xs = xs,
    ys = ys,
    baseEdges = baseEdges,
    candidateEdges = candidateEdges,
    maxRepairLength = maxRepairLength,
    strictTriangles = strictTriangles,
    triangles = triangles,
    keepConnectivity = keepConnectivity,
)

/** What breaks [enforceMinDegree]'s connectivity rules in a graph: nodes of graphs under [MIN_GRAPH_NODES], and cut nodes. */
internal class ConnectivityViolations(val smallGraphNodes: List<Int>, val cutNodes: List<Int>)

/** [ConnectivityViolations] of the graph of [edges] over [count] nodes. */
internal fun connectivityViolations(count: Int, edges: LongArray): ConnectivityViolations = MeshWorkspace().connectivityViolations(count, edges)

/**
 * Every triangle of the graph of [edges] over [count] nodes, as node index triples, each once: each
 * triple ascending, the triples in ascending order, so the list is the same whatever order [edges] come in.
 */
internal fun meshTriangles(count: Int, edges: LongArray): IntArray = TriangleSearch().meshTriangles(count, edges)

/** Every unique edge of [triangles] (vertex index triples), unfiltered, in order of first use. Repair candidates for Delaunay rules. */
internal fun triangleEdgeKeys(triangles: IntArray): LongArray = TriangleSearch().triangleEdgeKeys(triangles)

/** Each node's [neighbours] nearest other nodes, as unique edges, sorted. Repair candidates for [EdgeRule.Proximity]. */
internal fun nearestNeighbourEdgeKeys(count: Int, xs: FloatArray, ys: FloatArray, neighbours: Int): LongArray {
    val keys = LongArray(count * neighbours)
    var keyCount = 0
    val bestIndices = IntArray(neighbours)
    val bestDistances = FloatArray(neighbours)
    for (node in 0 until count) {
        var found = 0
        for (other in 0 until count) {
            if (other == node) continue
            val distance = squaredDistance(node, other, xs, ys)
            if (found == neighbours && distance >= bestDistances[found - 1]) continue
            // Insertion into the short sorted list of the best so far.
            var slot = if (found < neighbours) found++ else found - 1
            while (slot > 0 && bestDistances[slot - 1] > distance) {
                bestDistances[slot] = bestDistances[slot - 1]
                bestIndices[slot] = bestIndices[slot - 1]
                slot--
            }
            bestDistances[slot] = distance
            bestIndices[slot] = other
        }
        for (index in 0 until found) keys[keyCount++] = edgeKey(node, bestIndices[index])
    }
    keys.sort(0, keyCount)
    return keys.distinctSorted(keyCount)
}

/** The first [size] keys, sorted, without repeats. */
private fun LongArray.distinctSorted(size: Int): LongArray {
    var unique = 0
    for (index in 0 until size) if (index == 0 || this[index] != this[index - 1]) unique++
    val distinct = LongArray(unique)
    var next = 0
    for (index in 0 until size) if (index == 0 || this[index] != this[index - 1]) distinct[next++] = this[index]
    return distinct
}

private fun squaredDistance(first: Int, second: Int, xs: FloatArray, ys: FloatArray): Float {
    val deltaX = xs[first] - xs[second]
    val deltaY = ys[first] - ys[second]
    return deltaX * deltaX + deltaY * deltaY
}

/**
 * The memory [enforceMinDegree] works in, kept by a layer and reused by every settle: cleared, never
 * reallocated, and grown only when a settle needs more than any before it. A settle then allocates only
 * its output [MeshTopology].
 *
 * A settle runs over compact indices: only the nodes some base or candidate edge touches, numbered
 * densely in ascending pool order. Every other node has no edge to keep or repair, so it is pruned
 * without being visited. The numbering keeps pool order, so ascending compact index is ascending pool
 * index and sorted compact edge keys map back to sorted pool keys.
 *
 * Wherever the order of a walk changes the result, it runs in ascending node index (edge key for edges),
 * never in hash order, which differs between the JVM and wasm: so one spec settles on one mesh everywhere.
 * Each node's neighbours are kept ascending, which is that order. Repair candidates are the exception:
 * nearest first, ties in the order the candidate edges came in.
 */
internal class MeshWorkspace {
    private var poolCount = 0
    private var nodeCount = 0

    /** Each pool node's compact index; -1 outside a settle and for nodes it leaves out. */
    private var compactOf = IntArray(0)
    private var poolOf = IntArray(0)
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var alive = BooleanArray(0)
    private val neighbours = IntAdjacency()
    private val baseKeys = LongHashSet()
    private val removedKeys = LongHashSet()

    /** True while the nodes the compaction left out still count as pruned by the next [pruneShortNodes]. */
    private var prunesLeftOut = false

    private val repairCandidates = RepairCandidates()

    /** The Delaunay triangles' corners in compact indices, -1 for a corner left out; [faceCornerCount] of them. */
    private var faceCorners = IntArray(0)
    private var faceCornerCount = 0

    // Scratch for the walks, sized to the nodes or edges they hold.
    private var visibleBase = LongArray(0)
    private var visibleCandidates = LongArray(0)
    private var keys = LongArray(0)
    private var cuts = IntArray(0)
    private var seen = BooleanArray(0)
    private var stack = IntArray(0)
    private var walked = IntArray(0)
    private var smallNodes = IntArray(0)
    private var pieceStarts = IntArray(0)
    private var pieceOrder = IntArray(0)
    private var pieceMins = IntArray(0)
    private var pieceOf = IntArray(0)
    private val cutSearch = CutSearch()

    /** The searches over pool indices: a layer's triangles and repair candidates, and the triangles it draws. */
    val triangleSearch = TriangleSearch()

    /** [enforceMinDegree] over the edges of [baseEdges] and [candidateEdges] that [visible] keeps. */
    fun enforceMinDegree(
        count: Int,
        xs: FloatArray,
        ys: FloatArray,
        baseEdges: LongArray,
        candidateEdges: LongArray,
        maxRepairLength: Float,
        strictTriangles: Boolean,
        triangles: IntArray? = null,
        keepConnectivity: Boolean = true,
        visible: EdgeFilter = EdgeFilter.All,
    ): MeshTopology {
        if (visibleBase.size < baseEdges.size) visibleBase = LongArray(baseEdges.size)
        if (visibleCandidates.size < candidateEdges.size) visibleCandidates = LongArray(candidateEdges.size)
        var baseCount = 0
        for (key in baseEdges) if (visible.keeps(key)) visibleBase[baseCount++] = key
        var candidateCount = 0
        for (key in candidateEdges) if (visible.keeps(key)) visibleCandidates[candidateCount++] = key
        try {
            load(count, xs, ys, baseCount, candidateCount, maxRepairLength, triangles)
            var converged = false
            var rounds = 0
            while (!converged && rounds < MAX_ENFORCEMENT_ROUNDS) {
                converged = !repairRound(strictTriangles, keepConnectivity)
                rounds++
            }
            while (removalRound(strictTriangles, keepConnectivity)) Unit
            return toTopology(converged)
        } finally {
            release()
        }
    }

    fun connectivityViolations(count: Int, edges: LongArray): ConnectivityViolations {
        try {
            if (visibleBase.size < edges.size) visibleBase = LongArray(edges.size)
            edges.copyInto(visibleBase)
            load(count, FloatArray(count), FloatArray(count), edges.size, candidateCount = 0, maxRepairLength = 0f, poolTriangles = null)
            val small = smallGraphNodes()
            val cutCount = findCutNodes()
            return ConnectivityViolations(
                smallGraphNodes = List(small) { poolOf[smallNodes[it]] },
                cutNodes = List(cutCount) { poolOf[cuts[it]] },
            )
        } finally {
            release()
        }
    }

    /**
     * Numbers the nodes the first [baseCount] of [visibleBase] and [candidateCount] of [visibleCandidates]
     * touch, then builds the adjacency, base keys and candidates over them.
     */
    private fun load(count: Int, poolXs: FloatArray, poolYs: FloatArray, baseCount: Int, candidateCount: Int, maxRepairLength: Float, poolTriangles: IntArray?) {
        compact(count, baseCount, candidateCount)
        for (node in 0 until nodeCount) {
            xs[node] = poolXs[poolOf[node]]
            ys[node] = poolYs[poolOf[node]]
            alive[node] = true
        }
        neighbours.reset(nodeCount)
        baseKeys.clear()
        removedKeys.clear()
        for (index in 0 until baseCount) {
            val key = compactKey(visibleBase[index])
            baseKeys.add(key)
            if (!isConnected(edgeStart(key), edgeEnd(key))) connect(edgeStart(key), edgeEnd(key))
        }
        repairCandidates.load(visibleCandidates, candidateCount, compactOf, nodeCount, xs, ys, maxRepairLength)
        faceCornerCount = poolTriangles?.size ?: 0
        if (faceCorners.size < faceCornerCount) faceCorners = IntArray(faceCornerCount)
        for (corner in 0 until faceCornerCount) faceCorners[corner] = compactOf[poolTriangles!![corner]]
    }

    private fun compact(count: Int, baseCount: Int, candidateCount: Int) {
        poolCount = count
        if (compactOf.size < count) {
            compactOf = IntArray(count) { -1 }
            poolOf = IntArray(count)
        }
        nodeCount = 0
        for (index in 0 until baseCount) {
            touch(edgeStart(visibleBase[index]))
            touch(edgeEnd(visibleBase[index]))
        }
        for (index in 0 until candidateCount) {
            touch(edgeStart(visibleCandidates[index]))
            touch(edgeEnd(visibleCandidates[index]))
        }
        poolOf.sort(0, nodeCount)
        for (node in 0 until nodeCount) compactOf[poolOf[node]] = node
        prunesLeftOut = nodeCount < count
        ensureNodeCapacity(nodeCount)
    }

    private fun touch(node: Int) {
        if (compactOf[node] >= 0) return
        compactOf[node] = 0
        poolOf[nodeCount++] = node
    }

    /** Hands [compactOf] back all -1, ready for the next settle. */
    private fun release() {
        for (node in 0 until nodeCount) compactOf[poolOf[node]] = -1
    }

    private fun ensureNodeCapacity(capacity: Int) {
        if (alive.size >= capacity) return
        xs = FloatArray(capacity)
        ys = FloatArray(capacity)
        alive = BooleanArray(capacity)
        cuts = IntArray(capacity)
        seen = BooleanArray(capacity)
        stack = IntArray(capacity)
        walked = IntArray(capacity)
        smallNodes = IntArray(capacity)
        pieceStarts = IntArray(capacity + 1)
        pieceOrder = IntArray(capacity)
        pieceMins = IntArray(capacity)
        pieceOf = IntArray(capacity)
    }

    private fun compactKey(poolKey: Long): Long = edgeKey(compactOf[edgeStart(poolKey)], compactOf[edgeEnd(poolKey)])

    /** One repair round of [enforceMinDegree]; true if it changed anything. */
    private fun repairRound(strictTriangles: Boolean, keepConnectivity: Boolean): Boolean {
        var changed = strictTriangles && closeOrDropOpenEdges()
        if (strictTriangles && faceCornerCount > 0) changed = closeFaces() || changed
        changed = repairShortNodes(strictTriangles) || changed
        changed = pruneShortNodes() || changed
        // Connectivity only once the degrees settle: its search costs more, and repairs move cut nodes.
        if (!changed && keepConnectivity) changed = fixCutNodes(allowAdding = true, strictTriangles = strictTriangles) || pruneSmallGraphs()
        return changed
    }

    /** One round of [enforceMinDegree]'s removal-only terminal pass; true if it removed anything. */
    private fun removalRound(strictTriangles: Boolean, keepConnectivity: Boolean): Boolean {
        var changed = strictTriangles && dropOpenEdges()
        changed = pruneShortNodes() || changed
        if (!changed && keepConnectivity) changed = fixCutNodes(allowAdding = false, strictTriangles = strictTriangles) || pruneSmallGraphs()
        return changed
    }

    /** Strict mode: close each edge without a shared neighbour into a triangle, or drop it. */
    private fun closeOrDropOpenEdges(): Boolean {
        var changed = false
        val keyCount = snapshotKeys()
        for (index in 0 until keyCount) {
            val start = edgeStart(keys[index])
            val end = edgeEnd(keys[index])
            if (!isConnected(start, end) || sharesNeighbour(start, end)) continue
            changed = true
            if (!closeWithOneEdge(start, end) && !closeWithOneEdge(end, start)) disconnect(start, end)
        }
        return changed
    }

    /** Strict mode's removal-only half, for the terminal pass. */
    private fun dropOpenEdges(): Boolean {
        var changed = false
        val keyCount = snapshotKeys()
        for (index in 0 until keyCount) {
            val start = edgeStart(keys[index])
            val end = edgeEnd(keys[index])
            if (!isConnected(start, end) || sharesNeighbour(start, end)) continue
            disconnect(start, end)
            changed = true
        }
        return changed
    }

    private fun repairShortNodes(strictTriangles: Boolean): Boolean {
        var changed = false
        for (node in 0 until nodeCount) {
            if (!alive[node]) continue
            while (neighbours.degree(node) < 2) {
                val repaired = if (strictTriangles) repairWithTriangle(node) else repairWithNearest(node)
                if (!repaired) break
                changed = true
            }
        }
        return changed
    }

    /** Prunes every node short of two edges; the first call also counts the nodes the compaction left out, which have none. */
    private fun pruneShortNodes(): Boolean {
        var changed = prunesLeftOut
        prunesLeftOut = false
        for (node in 0 until nodeCount) {
            if (!alive[node] || neighbours.degree(node) >= 2) continue
            prune(node)
            changed = true
        }
        return changed
    }

    /** Strict mode: gives each triangle with all corners alive and two of its edges the third. */
    private fun closeFaces(): Boolean {
        var changed = false
        // Closing one face can give a neighbouring face its second edge, so repeat until none closes.
        while (closeFacesOnce()) changed = true
        return changed
    }

    private fun closeFacesOnce(): Boolean {
        var changed = false
        for (base in 0 until faceCornerCount step TRIANGLE_CORNERS) {
            changed = closeFace(faceCorners[base], faceCorners[base + 1], faceCorners[base + 2]) || changed
        }
        return changed
    }

    /** Adds the triangle's one missing edge if its corners are alive and it may; true if it did. */
    private fun closeFace(first: Int, second: Int, third: Int): Boolean {
        // A corner left out has no edges, so its triangle has at most one and never closes.
        if (first < 0 || second < 0 || third < 0) return false
        if (!alive[first] || !alive[second] || !alive[third]) return false
        return when {
            !isConnected(first, second) -> isConnected(second, third) && isConnected(third, first) && addIfAllowed(first, second)
            !isConnected(second, third) -> isConnected(third, first) && addIfAllowed(second, third)
            !isConnected(third, first) -> addIfAllowed(third, first)
            else -> false
        }
    }

    private fun addIfAllowed(first: Int, second: Int): Boolean {
        if (!canAdd(first, second)) return false
        connect(first, second)
        return true
    }

    /**
     * Fixes every cut node: joins each piece hanging from it to another piece with one new edge if
     * [allowAdding] and one fits (see [enforceMinDegree]), else prunes the piece if it is smaller than
     * [MIN_GRAPH_NODES] or cuts it loose from the cut node. True if anything changed.
     */
    private fun fixCutNodes(allowAdding: Boolean, strictTriangles: Boolean): Boolean {
        var changed = false
        val cutCount = findCutNodes()
        for (index in 0 until cutCount) {
            val cut = cuts[index]
            if (!alive[cut]) continue
            val pieceCount = collectPiecesAround(cut)
            if (pieceCount < 2) continue
            pieceOf.fill(-1, 0, nodeCount)
            for (piece in 0 until pieceCount) forEachInPiece(piece) { node -> pieceOf[node] = piece }
            // Pieces come ordered by their lowest node, so a tie for the largest goes to the lowest.
            var main = 0
            for (piece in 1 until pieceCount) if (pieceSize(piece) > pieceSize(main)) main = piece
            for (piece in 0 until pieceCount) {
                if (piece == main) continue
                val joined = allowAdding && (joinAcross(cut, piece) || !strictTriangles && joinAnywhere(cut, piece))
                if (!joined) cutOff(cut, piece)
                changed = true
            }
        }
        return changed
    }

    /** Prunes every separate graph smaller than [MIN_GRAPH_NODES]; true if any. */
    private fun pruneSmallGraphs(): Boolean {
        val small = smallGraphNodes()
        for (index in 0 until small) prune(smallNodes[index])
        return small > 0
    }

    /** Collects every node of a separate graph smaller than [MIN_GRAPH_NODES] in [smallNodes]; returns how many. */
    private fun smallGraphNodes(): Int {
        seen.fill(false, 0, nodeCount)
        var small = 0
        for (start in 0 until nodeCount) {
            if (seen[start] || !alive[start] || neighbours.degree(start) == 0) continue
            val size = collectFrom(start, blocked = -1, offset = 0)
            if (size < MIN_GRAPH_NODES) {
                walked.copyInto(smallNodes, destinationOffset = small, startIndex = 0, endIndex = size)
                small += size
            }
        }
        return small
    }

    private fun toTopology(converged: Boolean): MeshTopology {
        val keyCount = snapshotKeys()
        val edges = LongArray(keyCount)
        var repaired = 0
        for (index in 0 until keyCount) {
            val key = keys[index]
            if (key !in baseKeys) repaired++
            edges[index] = edgeKey(poolOf[edgeStart(key)], poolOf[edgeEnd(key)])
        }
        val poolAlive = BooleanArray(poolCount)
        var aliveCount = 0
        for (node in 0 until nodeCount) {
            if (!alive[node]) continue
            poolAlive[poolOf[node]] = true
            aliveCount++
        }
        return MeshTopology(
            edges = edges,
            alive = poolAlive,
            repairedEdgeCount = repaired,
            prunedNodeCount = poolCount - aliveCount,
            converged = converged,
        )
    }

    /** Adds `apex–far`, where `apex` already neighbours `near`, so `near–far–apex` becomes a triangle. */
    private fun closeWithOneEdge(near: Int, far: Int): Boolean {
        for (slot in repairCandidates.starts[far] until repairCandidates.starts[far + 1]) {
            val apex = repairCandidates.nodes[slot]
            if (apex != near && isConnected(near, apex) && canAdd(far, apex)) {
                connect(far, apex)
                return true
            }
        }
        return false
    }

    /** Shortest candidate, preferring one that closes a triangle with an edge the node already has. */
    private fun repairWithNearest(node: Int): Boolean {
        var fallback = -1
        for (slot in repairCandidates.starts[node] until repairCandidates.starts[node + 1]) {
            val other = repairCandidates.nodes[slot]
            if (!canAdd(node, other)) continue
            if (sharesNeighbour(node, other)) {
                connect(node, other)
                return true
            }
            if (fallback < 0) fallback = other
        }
        if (fallback < 0) return false
        connect(node, fallback)
        return true
    }

    /** Only repairs that leave the node in a triangle, cheapest first: one new edge, then two, then three. */
    private fun repairWithTriangle(node: Int): Boolean = when {
        repairToNeighbourOfNeighbour(node) -> true
        neighbours.degree(node) > 0 -> repairAroundAnchor(node, anchor = neighbours.neighbour(node, 0))
        else -> repairWithNewTriangle(node)
    }

    /** One new edge, to a node that already neighbours one of this node's neighbours. */
    private fun repairToNeighbourOfNeighbour(node: Int): Boolean {
        for (slot in repairCandidates.starts[node] until repairCandidates.starts[node + 1]) {
            val other = repairCandidates.nodes[slot]
            if (!canAdd(node, other) || !sharesNeighbour(node, other)) continue
            connect(node, other)
            return true
        }
        return false
    }

    /** Two new edges, `node–other` and `anchor–other`, closing a triangle on the node's one existing edge. */
    private fun repairAroundAnchor(node: Int, anchor: Int): Boolean {
        for (slot in repairCandidates.starts[node] until repairCandidates.starts[node + 1]) {
            val other = repairCandidates.nodes[slot]
            if (other == anchor || !canAdd(node, other) || !canAdd(anchor, other)) continue
            connect(node, other)
            connect(anchor, other)
            return true
        }
        return false
    }

    /** Edges to two candidates that are, or can become, connected to each other. */
    private fun repairWithNewTriangle(node: Int): Boolean {
        val end = repairCandidates.starts[node + 1]
        // Nothing changes until a pair is found, so both loops see the graph as it was.
        for (firstSlot in repairCandidates.starts[node] until end) {
            val first = repairCandidates.nodes[firstSlot]
            if (!canAdd(node, first)) continue
            for (secondSlot in repairCandidates.starts[node] until end) {
                val second = repairCandidates.nodes[secondSlot]
                if (second == first || !canAdd(node, second) || !isConnected(first, second) && !canAdd(first, second)) continue
                connect(node, first)
                connect(node, second)
                if (!isConnected(first, second)) connect(first, second)
                return true
            }
        }
        return false
    }

    /** The edge between two of [cut]'s neighbours, one in [piece] and one outside it, that closes a triangle across [cut]; shortest first. */
    private fun joinAcross(cut: Int, piece: Int): Boolean {
        var bestNear = -1
        var bestFar = -1
        var bestDistance = Float.MAX_VALUE
        for (position in pieceStarts[pieceOrder[piece]] until pieceStarts[pieceOrder[piece] + 1]) {
            val near = walked[position]
            if (!isConnected(near, cut)) continue
            for (slot in repairCandidates.starts[near] until repairCandidates.starts[near + 1]) {
                val far = repairCandidates.nodes[slot]
                if (pieceOf[far] == piece || !isConnected(far, cut) || !canAdd(near, far)) continue
                val distance = squaredDistance(near, far, xs, ys)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestNear = near
                    bestFar = far
                }
                break
            }
        }
        if (bestNear < 0) return false
        connect(bestNear, bestFar)
        return true
    }

    /** The shortest candidate edge from [piece] to any node outside it other than [cut]. */
    private fun joinAnywhere(cut: Int, piece: Int): Boolean {
        var bestNear = -1
        var bestFar = -1
        var bestDistance = Float.MAX_VALUE
        for (position in pieceStarts[pieceOrder[piece]] until pieceStarts[pieceOrder[piece] + 1]) {
            val near = walked[position]
            for (slot in repairCandidates.starts[near] until repairCandidates.starts[near + 1]) {
                val far = repairCandidates.nodes[slot]
                if (far == cut || pieceOf[far] == piece || !canAdd(near, far)) continue
                val distance = squaredDistance(near, far, xs, ys)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestNear = near
                    bestFar = far
                }
                break
            }
        }
        if (bestNear < 0) return false
        connect(bestNear, bestFar)
        return true
    }

    /** Prunes [piece] if it is too small to stand alone, else removes its edges to [cut]. */
    private fun cutOff(cut: Int, piece: Int) {
        if (pieceSize(piece) < MIN_GRAPH_NODES) {
            forEachInPiece(piece) { node -> prune(node) }
        } else {
            forEachInPiece(piece) { node -> if (isConnected(node, cut)) disconnect(node, cut) }
        }
    }

    private fun pieceSize(piece: Int): Int = pieceStarts[pieceOrder[piece] + 1] - pieceStarts[pieceOrder[piece]]

    /** Runs [action] on each node of the [piece]th piece in order, in the order its walk found them. */
    private inline fun forEachInPiece(piece: Int, action: (Int) -> Unit) {
        for (position in pieceStarts[pieceOrder[piece]] until pieceStarts[pieceOrder[piece] + 1]) action(walked[position])
    }

    /**
     * The groups [cut]'s graph falls into without [cut], each holding at least one of its neighbours:
     * walked into [walked] from its neighbours in ascending order, then ordered by their lowest node
     * through [pieceOrder]. Returns how many.
     */
    private fun collectPiecesAround(cut: Int): Int {
        seen.fill(false, 0, nodeCount)
        var pieceCount = 0
        var end = 0
        for (position in 0 until neighbours.degree(cut)) {
            val start = neighbours.neighbour(cut, position)
            if (seen[start]) continue
            pieceStarts[pieceCount] = end
            end = collectFrom(start, blocked = cut, offset = end)
            var lowest = Int.MAX_VALUE
            for (walk in pieceStarts[pieceCount] until end) lowest = min(lowest, walked[walk])
            pieceMins[pieceCount] = lowest
            pieceCount++
        }
        pieceStarts[pieceCount] = end
        for (piece in 0 until pieceCount) {
            var slot = piece
            while (slot > 0 && pieceMins[pieceOrder[slot - 1]] > pieceMins[piece]) {
                pieceOrder[slot] = pieceOrder[slot - 1]
                slot--
            }
            pieceOrder[slot] = piece
        }
        return pieceCount
    }

    /** Walks every node reachable from [start] without passing [blocked] into [walked] from [offset], marked in [seen]; returns the end. */
    private fun collectFrom(start: Int, blocked: Int, offset: Int): Int {
        var end = offset
        var depth = 0
        seen[start] = true
        stack[depth++] = start
        while (depth > 0) {
            val node = stack[--depth]
            walked[end++] = node
            for (position in 0 until neighbours.degree(node)) {
                val other = neighbours.neighbour(node, position)
                if (other == blocked || seen[other]) continue
                seen[other] = true
                stack[depth++] = other
            }
        }
        return end
    }

    /** Collects every cut node of the current graph in [cuts], ascending; returns how many. */
    private fun findCutNodes(): Int = cutSearch.cutNodes(neighbours, nodeCount, cuts)

    /** Copies every current edge into [keys], in ascending key order; returns how many. */
    private fun snapshotKeys(): Int {
        var total = 0
        for (node in 0 until nodeCount) total += neighbours.degree(node)
        if (keys.size < total / 2) keys = LongArray(total / 2)
        var keyCount = 0
        for (node in 0 until nodeCount) {
            for (position in 0 until neighbours.degree(node)) {
                val other = neighbours.neighbour(node, position)
                if (node < other) keys[keyCount++] = edgeKey(node, other)
            }
        }
        return keyCount
    }

    private fun prune(node: Int) {
        alive[node] = false
        while (neighbours.degree(node) > 0) disconnect(node, neighbours.neighbour(node, neighbours.degree(node) - 1))
    }

    private fun canAdd(first: Int, second: Int): Boolean {
        if (!alive[first] || !alive[second] || isConnected(first, second)) return false
        val key = edgeKey(first, second)
        return key in repairCandidates && key !in removedKeys
    }

    private fun sharesNeighbour(first: Int, second: Int): Boolean = neighbours.sharesSortedNeighbour(first, second)

    private fun isConnected(first: Int, second: Int): Boolean = neighbours.contains(first, second)

    private fun connect(first: Int, second: Int) {
        neighbours.insertSorted(first, second)
        neighbours.insertSorted(second, first)
    }

    private fun disconnect(first: Int, second: Int) {
        neighbours.remove(first, second)
        neighbours.remove(second, first)
        removedKeys.add(edgeKey(first, second))
    }
}

/** The triangle searches over pool indices, keeping their memory between calls like [MeshWorkspace]. */
internal class TriangleSearch {
    private val poolNeighbours = IntAdjacency()
    private val edgeIndices = LongIntHashMap()
    private val seenKeys = LongHashSet()
    private var sortedKeys = LongArray(0)
    private var found = IntArray(0)

    /** See the top-level [com.rossomak.morpheus.meshTriangles]. */
    fun meshTriangles(count: Int, edges: LongArray): IntArray {
        poolNeighbours.reset(count)
        for (key in edges) {
            if (poolNeighbours.contains(edgeStart(key), edgeEnd(key))) continue
            poolNeighbours.insertSorted(edgeStart(key), edgeEnd(key))
            poolNeighbours.insertSorted(edgeEnd(key), edgeStart(key))
        }
        if (sortedKeys.size < edges.size) sortedKeys = LongArray(edges.size)
        edges.copyInto(sortedKeys)
        sortedKeys.sort(0, edges.size)
        var corners = 0
        for (index in 0 until edges.size) {
            val key = sortedKeys[index]
            val low = min(edgeStart(key), edgeEnd(key))
            val high = max(edgeStart(key), edgeEnd(key))
            // From the edge between each triangle's two lowest corners only.
            for (position in 0 until poolNeighbours.degree(low)) {
                val apex = poolNeighbours.neighbour(low, position)
                if (apex <= high || !poolNeighbours.contains(high, apex)) continue
                corners = addTriangle(corners, low, high, apex)
            }
        }
        return found.copyOf(corners)
    }

    /**
     * The triangles of the first [edgeCount] of [edgeKeys], nodes below [count], as triples of their
     * indices there, each once. Each triangle comes from the edge between its two lowest corners, in
     * [edgeKeys] order, apexes in the order the edges name them.
     */
    fun edgeTriangles(count: Int, edgeKeys: LongArray, edgeCount: Int): IntArray {
        poolNeighbours.reset(count)
        edgeIndices.clear()
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            edgeIndices[key] = edge
            poolNeighbours.append(edgeStart(key), edgeEnd(key))
            poolNeighbours.append(edgeEnd(key), edgeStart(key))
        }
        var corners = 0
        for (edge in 0 until edgeCount) {
            val key = edgeKeys[edge]
            val start = edgeStart(key)
            val end = edgeEnd(key)
            // Keys hold the lower end first.
            for (position in 0 until poolNeighbours.degree(start)) {
                val apex = poolNeighbours.neighbour(start, position)
                if (apex <= end) continue
                val toEnd = edgeIndices.get(edgeKey(end, apex), missing = -1)
                if (toEnd < 0) continue
                corners = addTriangle(corners, edge, edgeIndices.get(edgeKey(start, apex), missing = -1), toEnd)
            }
        }
        return found.copyOf(corners)
    }

    /** See the top-level [com.rossomak.morpheus.triangleEdgeKeys]. */
    fun triangleEdgeKeys(triangles: IntArray): LongArray {
        seenKeys.clear()
        if (sortedKeys.size < triangles.size) sortedKeys = LongArray(triangles.size)
        var keyCount = 0
        for (base in triangles.indices step TRIANGLE_CORNERS) {
            for (side in 0 until TRIANGLE_CORNERS) {
                val key = edgeKey(triangles[base + side], triangles[base + (side + 1) % TRIANGLE_CORNERS])
                if (seenKeys.add(key)) sortedKeys[keyCount++] = key
            }
        }
        return sortedKeys.copyOf(keyCount)
    }

    private fun addTriangle(corners: Int, first: Int, second: Int, third: Int): Int {
        if (found.size < corners + TRIANGLE_CORNERS) found = found.copyOf(max(found.size * 2, corners + TRIANGLE_CORNERS))
        found[corners] = first
        found[corners + 1] = second
        found[corners + 2] = third
        return corners + TRIANGLE_CORNERS
    }
}

/**
 * Each node's repair candidates within reach, nearest first, ties in the order the candidate edges came
 * in: `nodes[starts[node] until starts[node + 1]]`, in compact indices. Kept by [MeshWorkspace].
 */
private class RepairCandidates {
    var starts = IntArray(1)
        private set
    var nodes = IntArray(0)
        private set
    private var distances = FloatArray(0)
    private var ends = IntArray(0)
    private val keys = LongHashSet()
    private var accepted = LongArray(0)

    operator fun contains(key: Long): Boolean = key in keys

    /** Loads the first [edgeCount] of [poolEdges] over [nodeCount] compact nodes at [xs], [ys], dropping any longer than [maxRepairLength]. */
    fun load(poolEdges: LongArray, edgeCount: Int, compactOf: IntArray, nodeCount: Int, xs: FloatArray, ys: FloatArray, maxRepairLength: Float) {
        val maxLengthSquared = maxRepairLength * maxRepairLength
        keys.clear()
        if (accepted.size < edgeCount) accepted = LongArray(edgeCount)
        if (starts.size < nodeCount + 1) {
            starts = IntArray(nodeCount + 1)
            ends = IntArray(nodeCount)
        }
        starts.fill(0, 0, nodeCount + 1)
        var acceptedCount = 0
        for (index in 0 until edgeCount) {
            val key = edgeKey(compactOf[edgeStart(poolEdges[index])], compactOf[edgeEnd(poolEdges[index])])
            val start = edgeStart(key)
            val end = edgeEnd(key)
            if (squaredDistance(start, end, xs, ys) > maxLengthSquared) continue
            if (!keys.add(key)) continue
            accepted[acceptedCount++] = key
            starts[start + 1]++
            starts[end + 1]++
        }
        for (node in 0 until nodeCount) starts[node + 1] += starts[node]
        if (nodes.size < acceptedCount * 2) {
            nodes = IntArray(acceptedCount * 2)
            distances = FloatArray(acceptedCount * 2)
        }
        // Filled in edge order, then sorted by distance in place: insertion sort is stable and the runs are short.
        starts.copyInto(ends, endIndex = nodeCount)
        for (index in 0 until acceptedCount) {
            val start = edgeStart(accepted[index])
            val end = edgeEnd(accepted[index])
            add(start, end, xs, ys)
            add(end, start, xs, ys)
        }
        for (node in 0 until nodeCount) sortByDistance(starts[node], starts[node + 1])
    }

    private fun add(node: Int, other: Int, xs: FloatArray, ys: FloatArray) {
        val slot = ends[node]++
        nodes[slot] = other
        distances[slot] = squaredDistance(node, other, xs, ys)
    }

    private fun sortByDistance(from: Int, until: Int) {
        for (slot in from + 1 until until) {
            val other = nodes[slot]
            val distance = distances[slot]
            var target = slot
            while (target > from && distances[target - 1] > distance) {
                nodes[target] = nodes[target - 1]
                distances[target] = distances[target - 1]
                target--
            }
            nodes[target] = other
            distances[target] = distance
        }
    }
}

/** Finds the cut nodes of a graph by an iterative Hopcroft–Tarjan search, keeping its memory between searches. */
private class CutSearch {
    private var order = IntArray(0)
    private var low = IntArray(0)
    private var parent = IntArray(0)
    private var nextNeighbour = IntArray(0)
    private var isCut = BooleanArray(0)
    private var stack = IntArray(0)
    private var time = 0

    /** Writes the cut nodes of [neighbours]' first [nodeCount] nodes into [into], ascending; returns how many. */
    fun cutNodes(neighbours: IntAdjacency, nodeCount: Int, into: IntArray): Int {
        if (order.size < nodeCount) {
            order = IntArray(nodeCount)
            low = IntArray(nodeCount)
            parent = IntArray(nodeCount)
            nextNeighbour = IntArray(nodeCount)
            isCut = BooleanArray(nodeCount)
            stack = IntArray(nodeCount)
        }
        order.fill(-1, 0, nodeCount)
        parent.fill(-1, 0, nodeCount)
        nextNeighbour.fill(0, 0, nodeCount)
        isCut.fill(false, 0, nodeCount)
        time = 0
        for (root in 0 until nodeCount) if (order[root] < 0 && neighbours.degree(root) > 0) searchFrom(root, neighbours)
        var cutCount = 0
        for (node in 0 until nodeCount) if (isCut[node]) into[cutCount++] = node
        return cutCount
    }

    private fun searchFrom(root: Int, neighbours: IntAdjacency) {
        var depth = 0
        var rootChildren = 0
        stack[0] = root
        visit(root)
        while (depth >= 0) {
            val node = stack[depth]
            if (nextNeighbour[node] == neighbours.degree(node)) {
                depth--
                finish(node, root)
                continue
            }
            val other = neighbours.neighbour(node, nextNeighbour[node]++)
            if (order[other] >= 0) {
                if (other != parent[node]) low[node] = min(low[node], order[other])
                continue
            }
            if (node == root) rootChildren++
            parent[other] = node
            visit(other)
            stack[++depth] = other
        }
        if (rootChildren > 1) isCut[root] = true
    }

    private fun visit(node: Int) {
        order[node] = time
        low[node] = time++
    }

    /** Hands [node]'s low point to its parent, which is a cut node if nothing under [node] reaches above it. */
    private fun finish(node: Int, root: Int) {
        val up = parent[node]
        if (up < 0) return
        low[up] = min(low[up], low[node])
        if (up != root && low[node] >= order[up]) isCut[up] = true
    }
}
