@file:Suppress("MagicNumber")

package com.rossomak.morpheus

import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How a layer's comets look. A morph's comets carry light from the nodes only its start shows to the nodes only
 * its end shows: each piece of the leaving nodes sends a stream of heads to the nearest piece of the
 * arriving nodes, and each arriving piece takes one from its nearest leaving piece, and a node lights while
 * a head passes over it. Every field is averaged over a morph's two
 * ends.
 */
public data class MorpheusGraphComets(
    /** Share of the nodes under a head that light: 0 = no comets, 1 = as many as the layer's fillChance. */
    val strength: Float = 1f,
    /**
     * Mean head radius, in multiples of the spread of the shape slice it carries: the radius of a disc as large
     * as the slice. Never changes how many heads a stream has.
     */
    val size: Float = 1f,
    /** Distance between neighbouring heads of a stream, in slice spreads (head radii at a size of 1): under 2 they overlap into one stream. */
    val headSpacing: Float = 1f,
    /** Heads per stream; null = from headSpacing. */
    val headsPerStream: Int? = null,
    /** Share of a comet's nodes that heat up as a head reaches them and cool once it has passed. */
    val spark: Float = 0f,
    /**
     * How far apart in time a stream's heads travel, 0 to 1. At 0, with [easing] at 0 too, the heads are synced:
     * they set off and land with their slices ([lead]). Any other value sets them free within the comet zones'
     * window around the morph's middle ([zone]), at 1 each in a pseudorandom fifth of it, shown only within it.
     */
    val stagger: Float = 0f,
    /**
     * How much each head eases in and out within its window, 0 to 1, on top of the morph's own timing: at 0 it
     * moves at a constant pace through the morph's fraction, so on screen it follows whatever easing drives
     * that fraction; at 1 it speeds up from its start and slows down into its end. It still moves in a straight
     * line. Mostly matters with [stagger]: a short window in the middle of an eased morph otherwise starts and
     * stops at full speed. Above 0 it frees the heads from their slices, as [stagger] does.
     */
    val easing: Float = 0f,
    /**
     * Standard deviation of the heads' sizes around [size], in the same units: each head draws its own size,
     * kept the same over its whole travel and never under a fifth of [size].
     */
    val sizeDeviation: Float = 0f,
    /**
     * About how wide, in node spacings, the pieces are that comets leave from and land on: a large connected
     * group of leaving or arriving nodes is split along its shape into pieces this wide, each streaming to the
     * piece of the other side it lies nearest to.
     */
    val pieceSize: Float = 15f,
    /**
     * In a relay morph, the share of each shape's front that sends or takes comets, measured in morph at a
     * [MorpheusGraphLayerSpec.morphGap] of 0 and stretched with the sweeps at any other gap, so it picks the same
     * cells. Wider launches the heads earlier and lands them later.
     */
    val zone: Float = 0.1f,
    /**
     * How long the trail of lit nodes behind each head is, in its middle radii: a node a head has left fades out
     * over this much of the head's travel, and a sparking node cools over it. A comet on screen is about its head's
     * width plus this tail, whatever its speed. Never shorter than a quarter of a radius, so no node pops out.
     */
    val tail: Float = 1.5f,
    /**
     * In a relay morph, how long before the earlier of its two slices' fades a synced head sets off, and how long
     * after the later one it lands, as a share of the morph; below 0 the flight overlaps the fades.
     */
    val lead: Float = 0.025f,
) {
    init {
        require(strength >= 0f) { "Comet strength must not be negative, was $strength" }
        require(size >= 0f) { "Comet size must not be negative, was $size" }
        require(headSpacing > 0f) { "Comet head spacing must be above 0, was $headSpacing" }
        require(headsPerStream == null || headsPerStream >= 1) { "A comet stream needs at least one head, was $headsPerStream" }
        require(spark >= 0f) { "Comet spark must not be negative, was $spark" }
        require(stagger in 0f..1f) { "Comet stagger must be within 0..1, was $stagger" }
        require(easing in 0f..1f) { "Comet easing must be within 0..1, was $easing" }
        require(sizeDeviation >= 0f) { "Comet size deviation must not be negative, was $sizeDeviation" }
        require(pieceSize > 0f) { "Comet piece size must be above 0, was $pieceSize" }
        require(zone in 0f..1f) { "Comet zone must be within 0..1, was $zone" }
        require(tail >= 0f) { "Comet tail must not be negative, was $tail" }
        require(lead in -1f..1f) { "Comet lead must be within -1..1, was $lead" }
    }
}

/**
 * Every field interpolated; [MorpheusGraphComets.headsPerStream] is automatic when either end leaves it
 * so, else the two counts interpolated and rounded.
 */
internal fun lerp(from: MorpheusGraphComets, to: MorpheusGraphComets, fraction: Float): MorpheusGraphComets {
    val fromHeads = from.headsPerStream
    val toHeads = to.headsPerStream
    return MorpheusGraphComets(
        strength = lerp(from.strength, to.strength, fraction),
        size = lerp(from.size, to.size, fraction),
        headSpacing = lerp(from.headSpacing, to.headSpacing, fraction),
        headsPerStream = if (fromHeads == null || toHeads == null) null else lerp(fromHeads.toFloat(), toHeads.toFloat(), fraction).roundToInt(),
        spark = lerp(from.spark, to.spark, fraction),
        stagger = lerp(from.stagger, to.stagger, fraction),
        easing = lerp(from.easing, to.easing, fraction),
        sizeDeviation = lerp(from.sizeDeviation, to.sizeDeviation, fraction),
        pieceSize = lerp(from.pieceSize, to.pieceSize, fraction),
        zone = lerp(from.zone, to.zone, fraction),
        tail = lerp(from.tail, to.tail, fraction),
        lead = lerp(from.lead, to.lead, fraction),
    )
}

/** Leaving or arriving nodes join one group when their cells of a grid this many node spacings wide touch. */
private const val COMET_CLUSTER_CELLS = 2.5f

/** A group of fewer leaving or arriving nodes sends or takes no comet: a few nodes at a shape's rim read fine as the plain switch. */
private const val COMET_MIN_CLUSTER = 8

/** At most this many heads per morph, however many streams and heads the settings ask for. */
private const val COMET_MAX_HEADS = 128

/**
 * A slice's spread is never under this many node spacings, so even a small slice's head lights a patch a few
 * nodes across, where every lit node has lit neighbours to draw its edges to.
 */
private const val COMET_MIN_SPREAD_SPACINGS = 2f

private const val PI_FLOAT = 3.1415927f

/** At a [MorpheusGraphComets.stagger] of 1, each head travels this much less than the whole morph. */
private const val MAX_STAGGER = 0.8f

/** No head is smaller than this share of [MorpheusGraphComets.size], however far below the mean it draws. */
private const val MIN_HEAD_SIZE_SHARE = 0.2f

/** Irwin–Hall draws summed into one head's size deviate: their sum, recentred, has a variance of a third. */
private const val SIZE_DRAWS = 4

/** A head's radius at either end is never under this share of its radius halfway along: on a thin shape the middle shrinks instead. */
private const val MIN_END_RADIUS_SHARE = 2f / 3f

/** Salts a head's draws, so its size and its window never share one: odd salts for its size draws, this even one for its window. */
private const val STAGGER_SALT = 2

/** A synced head's flight never spans less of the morph than this (see [syncedFlight]). */
private const val COMET_MIN_FLIGHT = 0.1f

/**
 * One head of a comet stream, in pixels: it travels in a straight line from the centroid of a slice of the
 * leaving nodes to the centroid of the matching slice of the arriving ones. Its radius grows linearly from
 * [sourceRadius] to [middleRadius] halfway along, then shrinks linearly to [targetRadius]: the middle one is
 * the mean of the two slices' spreads times the head's own size (see [MorpheusGraphComets.size]), and each
 * end one is the same slice's spread times that size, but never more than its anchor's depth in the shape, so the
 * head lies within the start shape where it appears and within the end shape where it lands; the middle one
 * is at most 1.5 times the smaller end's. It travels between the morph's fractions
 * [startsAt] and [endsAt] only, and doesn't exist outside them; within them its progress along the line, and
 * its radius with it, follows [travelEased] by [easing]. A synced head's [sourceDensity] and [targetDensity] are the
 * shares of the nodes its start and end shapes show around either end of its line, which its nodes blend between.
 */
internal data class CometHead(
    val sourceX: Float,
    val sourceY: Float,
    val targetX: Float,
    val targetY: Float,
    val sourceRadius: Float,
    val targetRadius: Float,
    val startsAt: Float = 0f,
    val endsAt: Float = 1f,
    val easing: Float = 0f,
    val middleRadius: Float = (sourceRadius + targetRadius) / 2f,
    val sourceDensity: Float = 1f,
    val targetDensity: Float = 1f,
) {
    /** The largest radius the head reaches. */
    val maxRadius: Float get() = max(middleRadius, max(sourceRadius, targetRadius))

    /** The head's radius once it is [progress] along its line. */
    fun radiusAt(progress: Float): Float =
        if (progress <= 0.5f) lerp(sourceRadius, middleRadius, 2f * progress) else lerp(middleRadius, targetRadius, 2f * progress - 1f)

    /** How far along its line the head is at the morph's [fraction], 0 to 1, or null outside its window. */
    fun progressAt(fraction: Float): Float? {
        if (fraction < startsAt || fraction > endsAt) return null
        val travel = if (endsAt > startsAt) (fraction - startsAt) / (endsAt - startsAt) else 1f
        return travelEased(travel.coerceIn(0f, 1f), easing)
    }

    /** The morph's fraction at which the head is [progress] along its line; the inverse of [progressAt]. */
    fun fractionAt(progress: Float): Float = startsAt + (endsAt - startsAt) * travelUneased(progress, easing)

    /**
     * How much of the morph the head takes to go from progress [from] to [to] along its line, through its easing.
     * Past either end of the line it carries on at its mean pace, so a tail behind a head that has landed still
     * takes as long to fade as one in flight.
     */
    fun fractionSpan(from: Float, to: Float): Float {
        val window = endsAt - startsAt
        val outside = max(0f, -from) + max(0f, to - 1f)
        return fractionAt(to.coerceIn(0f, 1f)) - fractionAt(from.coerceIn(0f, 1f)) + outside * window
    }
}

/**
 * Progress along a head's line after a share [travel] of its window, eased by [easing]: a blend of the even
 * pace and a quadratic ease in and out. Each half is a quadratic, so [travelUneased] needs only `sqrt`, and
 * it is symmetric, so the reverse morph retraces the same path at the same pace.
 */
internal fun travelEased(travel: Float, easing: Float): Float {
    if (travel > 0.5f) return 1f - travelEased(1f - travel, easing)
    return travel * (1f - easing) + 2f * easing * travel * travel
}

/** The share of its window after which a head is [progress] along its line; the inverse of [travelEased]. */
internal fun travelUneased(progress: Float, easing: Float): Float {
    if (progress > 0.5f) return 1f - travelUneased(1f - progress, easing)
    if (progress <= 0f) return 0f
    // The root of 2·easing·t² + (1 − easing)·t − progress = 0, in the form that stays exact at an easing of 0.
    val linear = 1f - easing
    return 2f * progress / (linear + sqrt(linear * linear + 8f * easing * progress))
}

/**
 * A morph's comet heads, how many streams they make up, how deep every cell lies inside the start and end
 * shapes, and, for [MorpheusGraphCometDebug], the pieces they came from and which pieces each stream joins.
 * The pieces and matches are built by [debugPlan] only the first time an overlay asks for them, so a morph
 * drawn without overlays never pays for them. In a relay morph, [emitters] and [receivers] are the zones the comets
 * leave and land on, and [isSynced] says the heads are timed and sized by those zones rather than running free.
 */
internal class CometStreams(
    val heads: List<CometHead>,
    val streamCount: Int,
    val startClearance: Clearance? = null,
    val endClearance: Clearance? = null,
    val emitters: BooleanArray? = null,
    val receivers: BooleanArray? = null,
    val isSynced: Boolean = false,
    private val handOverNodes: IntArray = IntArray(0),
    private val handOverSwitches: FloatArray = FloatArray(0),
    debugPlan: () -> CometDebugPlan = { CometDebugPlan.None },
) {
    private val lazyDebugPlan = lazy(LazyThreadSafetyMode.NONE, debugPlan)

    private val rankedSwitches = FloatArray(handOverNodes.size)

    val pieces: List<CometPiece> get() = lazyDebugPlan.value.pieces

    /** Each stream's source and target, as indices into [pieces]. */
    val matches: List<Pair<Int, Int>> get() = lazyDebugPlan.value.matches

    /** True once an overlay has asked for the [pieces] or [matches]. */
    val isDebugPlanBuilt: Boolean get() = lazyDebugPlan.isInitialized()

    /**
     * Moves the switch of every node a synced head carries off or lands on to that head's own departure or landing, so
     * its source slice fades out as the head sets off and its target slice fades in as it lands. Rewritten into
     * [switches] each time the morph's switches are ranked; other nodes keep their ranked switch.
     */
    fun handOver(switches: FloatArray) {
        for (position in handOverNodes.indices) {
            rankedSwitches[position] = switches[handOverNodes[position]]
            switches[handOverNodes[position]] = handOverSwitches[position]
        }
    }

    /** Puts back into [switches] the ranked switches the last [handOver] replaced. */
    fun restoreRanked(switches: FloatArray) {
        for (position in handOverNodes.indices) switches[handOverNodes[position]] = rankedSwitches[position]
    }

    companion object {
        val None = CometStreams(emptyList(), 0)
    }
}

/** The pieces a morph's comets came from and which pieces each stream joins, for [MorpheusGraphCometDebug]. */
internal class CometDebugPlan(val pieces: List<CometPiece>, val matches: List<Pair<Int, Int>>) {
    companion object {
        val None = CometDebugPlan(emptyList(), emptyList())
    }
}

/**
 * One piece of the leaving ([isSource]) or arriving nodes, for [MorpheusGraphCometDebug]: its nodes, its
 * centroid, and whether it [sends] or takes a stream: false when it is too small, or the kept shape absorbs it.
 */
internal class CometPiece(val nodes: IntArray, val centerX: Float, val centerY: Float, val isSource: Boolean, val sends: Boolean)

/**
 * The comet streams from the [leaving] nodes to the [arriving] ones, under [comets], for nodes [spacing]
 * apart. Each side is grouped into clusters of touching nodes, and each cluster is split along its own
 * shape into pieces about [MorpheusGraphComets.pieceSize] across, so two crossing ribbons become their arms.
 * Each piece of one side is matched with the piece of the other that it lies nearest to, both ways, by the
 * gap between them, so a comet lands on the first geometry it meets. A piece lying nearer to the [kept]
 * nodes, the ones both ends show, than to any piece of the other side sends or takes no stream: the kept
 * shape absorbs it. Only kept clusters as large as a leaving or arriving one count, so the stray nodes the
 * density floor keeps everywhere don't absorb whatever they happen to lie in. A matched pair is cut into slices along one shared axis, the larger piece's, and slice
 * i of one sends a head to slice i of the other. Each head draws its size and window from [seed] and its
 * two pieces, so the same morph in the same direction draws the same heads every time.
 *
 * Given the [switches] of a relay morph (see [MORPH_RELAY_SPLIT]), only the emitter and receiver zones exchange
 * comets (see [MorpheusGraphComets.zone]), and synced heads carry the density of the two shapes there (see
 * [MorpheusGraphComets.stagger]). When [handsOver], a synced head's slices switch with it (see [CometStreams.handOver]).
 */
@Suppress("LongParameterList") // The three node sets, their homes, the settings, the layer's seed, the switches and the hand-over.
internal fun cometStreams(
    leaving: BooleanArray,
    arriving: BooleanArray,
    kept: BooleanArray,
    homeX: FloatArray,
    homeY: FloatArray,
    count: Int,
    spacing: Float,
    comets: MorpheusGraphComets,
    seed: Int,
    switches: FloatArray? = null,
    handsOver: Boolean = true,
): CometStreams {
    val grid = CellGrid(homeX, homeY, count, spacing * COMET_CLUSTER_CELLS)
    val pieceCells = max(1, (comets.pieceSize / COMET_CLUSTER_CELLS).let { side -> ceil(side * side).toInt() })
    val emitters = switches?.let { zoneOf(leaving, it, grid, homeX, homeY, count, comets.zone, last = true) }
    val receivers = switches?.let { zoneOf(arriving, it, grid, homeX, homeY, count, comets.zone, last = false) }
    val (sourceClusters, smallSources) = clustersOf(emitters ?: leaving, grid, homeX, homeY, count).partition { it.size >= COMET_MIN_CLUSTER }
    val (targetClusters, smallTargets) = clustersOf(receivers ?: arriving, grid, homeX, homeY, count).partition { it.size >= COMET_MIN_CLUSTER }
    val sources = sourceClusters.flatMap { it.pieces(grid, pieceCells, homeX, homeY) }
    val targets = targetClusters.flatMap { it.pieces(grid, pieceCells, homeX, homeY) }
    if (sources.isEmpty() || targets.isEmpty()) return CometStreams.None
    val keptShape = BooleanArray(count)
    clustersOf(kept, grid, homeX, homeY, count).filter { it.size >= COMET_MIN_CLUSTER }.forEach { cluster -> cluster.nodes.forEach { keptShape[it] = true } }
    val pairs = matchPieces(sources, targets, grid, grid.stepsFrom(keptShape, count))
    val shownAtStart = BooleanArray(count) { leaving[it] || kept[it] }
    val shownAtEnd = BooleanArray(count) { arriving[it] || kept[it] }
    val startClearance = Clearance(shownAtStart, homeX, homeY, count, spacing)
    val endClearance = Clearance(shownAtEnd, homeX, homeY, count, spacing)
    val timing = headTiming(switches, comets, grid, shownAtStart, shownAtEnd, count)
    val streams = pairs.map { (source, target) -> StreamPlan(sources[source], targets[target], spacing, comets) }
    val wanted = streams.sumOf { it.headCount }
    val handOverCentres = if (handsOver && timing is HeadTiming.Synced) FloatArray(count) { Float.NaN } else null
    val heads = streams.flatMap { stream ->
        val headCount = if (wanted <= COMET_MAX_HEADS) stream.headCount else max(1, stream.headCount * COMET_MAX_HEADS / wanted)
        stream.heads(headCount, spacing, comets, seed, startClearance, endClearance, timing, handOverCentres)
    }
    val handOverNodes = handOverNodesOf(handOverCentres)
    val handOverSwitches = FloatArray(handOverNodes.size) { switchAtCentre(handOverCentres!![handOverNodes[it]]) }
    return CometStreams(
        heads,
        streams.size,
        startClearance,
        endClearance,
        emitters,
        receivers,
        isSynced = timing is HeadTiming.Synced,
        handOverNodes = handOverNodes,
        handOverSwitches = handOverSwitches,
    ) {
        val pieces = sources.mapIndexed { index, piece -> piece.toCometPiece(isSource = true, sends = pairs.any { it.first == index }) } +
            targets.mapIndexed { index, piece -> piece.toCometPiece(isSource = false, sends = pairs.any { it.second == index }) } +
            smallSources.map { it.toCometPiece(isSource = true, sends = false) } +
            smallTargets.map { it.toCometPiece(isSource = false, sends = false) }
        CometDebugPlan(pieces, matches = pairs.map { (source, target) -> source to sources.size + target })
    }
}

/** The nodes a synced head hands over, those with a fade centre in [centres]; none without [centres]. */
private fun handOverNodesOf(centres: FloatArray?): IntArray = centres?.let { centres.indices.filter { !centres[it].isNaN() }.toIntArray() } ?: IntArray(0)

/**
 * How [comets]' heads fly: over the whole morph without the relay's [switches]; synced to them, sized by the share of
 * the nodes each end shows, by [shownAtStart] and [shownAtEnd], with no stagger and no easing; free otherwise.
 */
@Suppress("LongParameterList") // The switches, the settings, the grid, what each end shows and the node count.
private fun headTiming(switches: FloatArray?, comets: MorpheusGraphComets, grid: CellGrid, shownAtStart: BooleanArray, shownAtEnd: BooleanArray, count: Int): HeadTiming = when {
    switches == null -> HeadTiming.Whole
    comets.stagger == 0f && comets.easing == 0f -> HeadTiming.Synced(switches, CellShares(grid, shownAtStart, count), CellShares(grid, shownAtEnd, count))
    else -> HeadTiming.Flight
}

/**
 * Each source piece paired with the target piece it lies nearest to, and each target piece with its nearest source,
 * by the gap between them and then their centroids; a piece lying nearer to the kept shape, by [keptSteps], than to
 * its match pairs with nothing.
 */
private fun matchPieces(sources: List<Cluster>, targets: List<Cluster>, grid: CellGrid, keptSteps: IntArray): Set<Pair<Int, Int>> {
    val gaps = Array(sources.size) { source -> IntArray(targets.size) { target -> grid.gap(sources[source], targets[target]) } }
    val pairs = LinkedHashSet<Pair<Int, Int>>()
    sources.forEachIndexed { source, piece ->
        val target = targets.indices.minWith(compareBy({ gaps[source][it] }, { centroidDistanceSquared(piece, targets[it]) }))
        if (piece.stepsTo(keptSteps) >= gaps[source][target]) pairs += source to target
    }
    targets.forEachIndexed { target, piece ->
        val source = sources.indices.minWith(compareBy({ gaps[it][target] }, { centroidDistanceSquared(sources[it], piece) }))
        if (piece.stepsTo(keptSteps) >= gaps[source][target]) pairs += source to target
    }
    return pairs
}

/** One stream's two pieces, the axis it slices them along and how many heads it wants. */
private class StreamPlan(val source: Cluster, val target: Cluster, spacing: Float, comets: MorpheusGraphComets) {
    /** The larger piece's, the one with the lower first node on a tie. */
    private val axisOwner = when {
        source.size != target.size -> if (source.size > target.size) source else target
        source.firstNode < target.firstNode -> source
        else -> target
    }
    private val axisX: Float
    private val axisY: Float
    val headCount: Int

    init {
        val (x, y) = axisOwner.principalAxis()
        axisX = x
        axisY = y
        // Heads headSpacing slice spreads apart along the owner, a spread being the radius of the disc of a
        // slice's nodes: length / count = headSpacing · sqrt(nodes / count / π) · spacing, solved for the count.
        // Size scales the radii only, never the count.
        // Never more heads than the smaller piece has slices of the minimum spread, so a small piece paired
        // with a large one isn't piled with heads repeating its few nodes.
        val smallerSlices = max(1, (min(source.size, target.size) / (PI_FLOAT * COMET_MIN_SPREAD_SPACINGS * COMET_MIN_SPREAD_SPACINGS)).toInt())
        headCount = min(
            smallerSlices,
            comets.headsPerStream ?: run {
                val lengthInSpreads = axisOwner.lengthAlong(axisX, axisY) / (comets.headSpacing * spacing)
                ceil(PI_FLOAT * lengthInSpreads * lengthInSpreads / axisOwner.size).toInt().coerceAtLeast(1)
            },
        )
    }

    /**
     * The stream's heads, each with its own size and window, drawn from the two pieces' first nodes. Each head
     * leaves from the node of its source slice lying deepest in the start shape, by [startClearance], and lands
     * on the node of its target slice lying deepest in the end shape, by [endClearance]. Its radius at either
     * end fits within that depth, and its middle radius is at most 1 / [MIN_END_RADIUS_SHARE] times the
     * smaller end's, so a head on a thin shape stays small all the way.
     */
    @Suppress("LongParameterList") // The count, the grid's spacing, the settings, the seed, the two clearances, the timing and the hand-over.
    fun heads(
        headCount: Int,
        spacing: Float,
        comets: MorpheusGraphComets,
        seed: Int,
        startClearance: Clearance,
        endClearance: Clearance,
        timing: HeadTiming,
        handOverCentres: FloatArray?,
    ): List<CometHead> {
        val synced = timing as? HeadTiming.Synced
        val sourceSlices = source.slices(axisX, axisY, headCount, spacing, startClearance, synced?.switches, synced?.startShares)
        val targetSlices = target.slices(axisX, axisY, headCount, spacing, endClearance, synced?.switches, synced?.endShares)
        val windowStart = if (timing is HeadTiming.Whole) 0f else (MORPH_RELAY_SPLIT - comets.zone).coerceAtLeast(0f)
        val window = if (timing is HeadTiming.Whole) 1f else (MORPH_RELAY_SPLIT + comets.zone).coerceAtMost(1f) - windowStart
        val duration = window * (1f - MAX_STAGGER * comets.stagger)
        val reach = MORPH_FADE_WINDOW / 2f + comets.lead
        return List(headCount) { slice ->
            val from = sourceSlices[slice]
            val to = targetSlices[slice]
            var draws = 0f
            repeat(SIZE_DRAWS) { draw -> draws += headRandom(source.firstNode, target.firstNode, slice, 2 * draw + 1, seed) }
            val deviate = (draws - SIZE_DRAWS / 2f) * sqrt(12f / SIZE_DRAWS)
            val headSize = max(comets.size + comets.sizeDeviation * deviate, MIN_HEAD_SIZE_SHARE * comets.size)
            val opens = windowStart + (window - duration) * headRandom(source.firstNode, target.firstNode, slice, STAGGER_SALT, seed)
            val flight = if (synced != null) syncedFlight(from.meanCentre, to.meanCentre, reach) else opens..opens + duration
            val startsAt = flight.start
            val endsAt = flight.endInclusive
            val sourceRadius = min(from.spread * headSize, from.depth)
            val targetRadius = min(to.spread * headSize, to.depth)
            val middleRadius = min((from.spread + to.spread) / 2f * headSize, min(sourceRadius, targetRadius) / MIN_END_RADIUS_SHARE)
            CometHead(
                sourceX = from.anchorX,
                sourceY = from.anchorY,
                targetX = to.anchorX,
                targetY = to.anchorY,
                sourceRadius = sourceRadius,
                targetRadius = targetRadius,
                startsAt = startsAt,
                endsAt = endsAt,
                easing = comets.easing,
                middleRadius = middleRadius,
                sourceDensity = from.density,
                targetDensity = to.density,
            ).also { if (handOverCentres != null) handOver(from, min(from.meanCentre, startsAt + reach), to, max(to.meanCentre, endsAt - reach), handOverCentres) }
        }
    }
}

/**
 * A synced head's flight: from [reach] before the earlier of its slices' fade centres, [fromCentre] and [toCentre], to
 * [reach] after the later, which is its source's then its target's unless the morph's gap overlaps the sweeps so far
 * that the target fades in first. A flight shorter than [COMET_MIN_FLIGHT] is widened around its middle, and one
 * crossing the morph's ends is cut to them, then shifted back to that length. Both ends mirror, so the reverse morph
 * retraces the head.
 */
private fun syncedFlight(fromCentre: Float, toCentre: Float, reach: Float): ClosedFloatingPointRange<Float> {
    val middle = (fromCentre + toCentre) / 2f
    val half = max(abs(toCentre - fromCentre) / 2f + reach, COMET_MIN_FLIGHT / 2f)
    val startsAt = (middle - half).coerceAtLeast(0f)
    val endsAt = (middle + half).coerceAtMost(1f)
    return when {
        endsAt - startsAt >= COMET_MIN_FLIGHT -> startsAt..endsAt
        startsAt == 0f -> 0f..COMET_MIN_FLIGHT
        else -> 1f - COMET_MIN_FLIGHT..1f
    }
}

/**
 * Into [centres], the fade centre each node of a head's two slices takes: [departureCentre] for its [from] nodes, the
 * latest of them where several heads leave one node, and [landingCentre] for its [to] nodes, the earliest of them.
 */
private fun handOver(from: Slice, departureCentre: Float, to: Slice, landingCentre: Float, centres: FloatArray) {
    for (node in from.nodes) centres[node] = if (centres[node].isNaN()) departureCentre else max(centres[node], departureCentre)
    for (node in to.nodes) centres[node] = if (centres[node].isNaN()) landingCentre else min(centres[node], landingCentre)
}

/**
 * One slice of a cluster: its anchor, the node lying deepest in the shape, nearest the slice's centroid on a
 * tie; its spread: the radius of a disc as large as its nodes cover; and its anchor's depth in the shape. For a
 * synced head, also its nodes' mean fade centre (see [switchCentre]), the share of the nodes its shape shows
 * over the slice's cells, and its [nodes]; 0.5, 1 and none otherwise.
 */
private class Slice(
    val anchorX: Float,
    val anchorY: Float,
    val spread: Float,
    val depth: Float,
    val meanCentre: Float = 0.5f,
    val density: Float = 1f,
    val nodes: IntArray = IntArray(0),
)

/**
 * When a stream's heads fly: over the [Whole] morph, as outside a relay; free within the relay's flight window
 * ([Flight]); or [Synced] to the relay's [switches], sized by the share of the nodes each end shape shows per cell.
 */
private sealed interface HeadTiming {
    data object Whole : HeadTiming

    data object Flight : HeadTiming

    class Synced(val switches: FloatArray, val startShares: CellShares, val endShares: CellShares) : HeadTiming
}

/** How many of all the nodes, and how many of the [shown] ones, each cell of [grid] holds. */
private class CellShares(grid: CellGrid, shown: BooleanArray, count: Int) {
    private val cellOf = grid.cellOf
    private val total = IntArray(grid.size)
    private val shownCount = IntArray(grid.size)

    init {
        for (node in 0 until count) {
            total[cellOf[node]]++
            if (shown[node]) shownCount[cellOf[node]]++
        }
    }

    /** The share of the shown nodes over the cells the [nodes] lie in. */
    fun shareOver(nodes: IntArray): Float {
        var all = 0
        var lit = 0
        for (cell in nodes.mapTo(HashSet()) { cellOf[it] }) {
            all += total[cell]
            lit += shownCount[cell]
        }
        return if (all > 0) lit.toFloat() / all else 0f
    }
}

/**
 * Every node by a square cell of its home, [cell] wide, over the bounds of all [count] nodes, so the leaving,
 * arriving and kept nodes share one grid and gaps between them count in its cells.
 */
private class CellGrid(homeX: FloatArray, homeY: FloatArray, count: Int, val cell: Float) {
    val columns: Int
    val rows: Int
    val cellOf: IntArray
    var lowX = Float.MAX_VALUE
        private set
    var lowY = Float.MAX_VALUE
        private set

    init {
        var highX = -Float.MAX_VALUE
        var highY = -Float.MAX_VALUE
        for (node in 0 until count) {
            lowX = min(lowX, homeX[node])
            lowY = min(lowY, homeY[node])
            highX = max(highX, homeX[node])
            highY = max(highY, homeY[node])
        }
        columns = floor((highX - lowX) / cell).toInt() + 1
        rows = floor((highY - lowY) / cell).toInt() + 1
        cellOf = IntArray(count) { node -> floor((homeY[node] - lowY) / cell).toInt() * columns + floor((homeX[node] - lowX) / cell).toInt() }
    }

    val size: Int get() = columns * rows

    /** How many cells apart [first] and [second] are, side or corner steps alike. */
    fun steps(first: Int, second: Int): Int = max(abs(first % columns - second % columns), abs(first / columns - second / columns))

    /** The fewest [steps] between any cell of [first] and any cell of [second]: 0 when they share one. */
    fun gap(first: Cluster, second: Cluster): Int {
        var gap = Int.MAX_VALUE
        for (one in first.cells) for (other in second.cells) gap = min(gap, steps(one, other))
        return gap
    }

    /** Each cell's [steps] to the nearest cell holding one of the [members], by breadth-first search; [Int.MAX_VALUE] when there are none. */
    fun stepsFrom(members: BooleanArray, count: Int): IntArray {
        val result = IntArray(size) { Int.MAX_VALUE }
        val queue = IntArray(size)
        var tail = 0
        for (node in 0 until count) {
            if (!members[node] || result[cellOf[node]] == 0) continue
            result[cellOf[node]] = 0
            queue[tail++] = cellOf[node]
        }
        var head = 0
        while (head < tail) {
            val cell = queue[head++]
            forEachNeighbour(cell) { next ->
                if (result[next] == Int.MAX_VALUE) {
                    result[next] = result[cell] + 1
                    queue[tail++] = next
                }
            }
        }
        return result
    }

    /** Runs [action] on each cell touching [cell], side or corner, inside the grid. */
    inline fun forEachNeighbour(cell: Int, action: (Int) -> Unit) {
        val column = cell % columns
        val row = cell / columns
        for (rowStep in -1..1) {
            for (columnStep in -1..1) {
                if (rowStep == 0 && columnStep == 0) continue
                val nextColumn = column + columnStep
                val nextRow = row + rowStep
                if (nextColumn in 0 until columns && nextRow in 0 until rows) action(nextRow * columns + nextColumn)
            }
        }
    }
}

/** Touching leaving or arriving nodes, or one piece of them, by index in index order, with their homes and their cells in ascending order. */
private class Cluster(val nodes: IntArray, val cells: IntArray, private val homeX: FloatArray, private val homeY: FloatArray) {
    val size: Int get() = nodes.size
    val firstNode: Int get() = nodes[0]
    val centerX: Float = mean { homeX[it] }
    val centerY: Float = mean { homeY[it] }

    fun toCometPiece(isSource: Boolean, sends: Boolean): CometPiece = CometPiece(nodes, centerX, centerY, isSource, sends)

    /** The fewest steps from any of the cluster's cells to a cell [stepsFrom] measured from. */
    fun stepsTo(stepsFrom: IntArray): Int = cells.minOf { stepsFrom[it] }

    /**
     * The cluster split along its own shape into about one piece per [pieceCells] cells: seeds picked
     * farthest first, by steps through the cluster's cells, from the cell of its first node, then every cell
     * joined to the seed it reaches first. Distance runs through the shape, so two crossing ribbons split
     * into arms. A piece under [COMET_MIN_CLUSTER] nodes joins the piece it borders most.
     */
    fun pieces(grid: CellGrid, pieceCells: Int, homeX: FloatArray, homeY: FloatArray): List<Cluster> {
        val pieceCount = (cells.size + pieceCells - 1) / pieceCells
        if (pieceCount <= 1) return listOf(this)
        val local = HashMap<Int, Int>(cells.size * 2)
        cells.forEachIndexed { index, cell -> local[cell] = index }
        val steps = IntArray(cells.size) { Int.MAX_VALUE }
        val labels = IntArray(cells.size) { -1 }
        val queue = IntArray(cells.size)
        val seeds = IntArray(pieceCount)
        seeds[0] = local.getValue(grid.cellOf[firstNode])
        for (seed in 1 until pieceCount) {
            spread(grid, local, intArrayOf(seeds[seed - 1]), steps, labels, queue, labelsSeeds = false)
            var farthest = 0
            for (index in cells.indices) if (steps[index] > steps[farthest]) farthest = index
            if (steps[farthest] == 0) return listOf(this)
            seeds[seed] = farthest
        }
        steps.fill(Int.MAX_VALUE)
        spread(grid, local, seeds, steps, labels, queue, labelsSeeds = true)
        mergeSmallPieces(grid, local, labels, pieceCount)
        val members = HashMap<Int, ArrayList<Int>>()
        for (node in nodes) members.getOrPut(labels[local.getValue(grid.cellOf[node])]) { ArrayList() } += node
        return members.values.sortedBy { it[0] }.map { pieceNodes ->
            Cluster(pieceNodes.toIntArray(), pieceNodes.map { grid.cellOf[it] }.distinct().sorted().toIntArray(), homeX, homeY)
        }
    }

    /**
     * Breadth-first from the cells [from], lowering [steps] through the cluster's cells and handing each cell
     * it lowers the label of the cell it was reached from; with [labelsSeeds], seed i is labelled i first,
     * so each cell takes the label of the seed that reaches it, the earlier seed on a tie.
     */
    @Suppress("LongParameterList") // The grid, the cells' indices, the seeds and the three work arrays.
    private fun spread(grid: CellGrid, local: Map<Int, Int>, from: IntArray, steps: IntArray, labels: IntArray, queue: IntArray, labelsSeeds: Boolean) {
        var tail = 0
        from.forEachIndexed { label, seed ->
            steps[seed] = 0
            if (labelsSeeds) labels[seed] = label
            queue[tail++] = seed
        }
        var head = 0
        while (head < tail) {
            val index = queue[head++]
            grid.forEachNeighbour(cells[index]) { neighbour ->
                val next = local[neighbour] ?: return@forEachNeighbour
                if (steps[next] > steps[index] + 1) {
                    steps[next] = steps[index] + 1
                    labels[next] = labels[index]
                    queue[tail++] = next
                }
            }
        }
    }

    /** Joins each piece under [COMET_MIN_CLUSTER] nodes, smallest label first, to the piece sharing the most cell borders with it. */
    private fun mergeSmallPieces(grid: CellGrid, local: Map<Int, Int>, labels: IntArray, pieceCount: Int) {
        val nodeCounts = IntArray(pieceCount)
        for (node in nodes) nodeCounts[labels[local.getValue(grid.cellOf[node])]]++
        for (piece in 0 until pieceCount) {
            if (nodeCounts[piece] == 0 || nodeCounts[piece] >= COMET_MIN_CLUSTER) continue
            val borders = IntArray(pieceCount)
            for (index in cells.indices) {
                if (labels[index] != piece) continue
                grid.forEachNeighbour(cells[index]) { neighbour ->
                    val other = local[neighbour]?.let { labels[it] } ?: return@forEachNeighbour
                    if (other != piece) borders[other]++
                }
            }
            val into = borders.indices.maxBy { borders[it] }
            if (borders[into] > 0) {
                for (index in cells.indices) if (labels[index] == piece) labels[index] = into
                nodeCounts[into] += nodeCounts[piece]
                nodeCounts[piece] = 0
            }
        }
    }

    /**
     * The direction the cluster is longest in: the leading eigenvector of its covariance, with no
     * trigonometry, so every platform gets the same bits. Pointing right, or down when it is vertical.
     */
    fun principalAxis(): Pair<Float, Float> {
        var xx = 0f
        var yy = 0f
        var xy = 0f
        for (node in nodes) {
            val deltaX = homeX[node] - centerX
            val deltaY = homeY[node] - centerY
            xx += deltaX * deltaX
            yy += deltaY * deltaY
            xy += deltaX * deltaY
        }
        val halfGap = (xx - yy) / 2f
        val largest = (xx + yy) / 2f + sqrt(halfGap * halfGap + xy * xy)
        // Of the two forms of the same eigenvector, the one further from zero.
        var (x, y) = if (xx >= yy) largest - yy to xy else xy to largest - xx
        if (x * x + y * y <= 0f) {
            x = 1f
            y = 0f
        }
        val length = sqrt(x * x + y * y)
        x /= length
        y /= length
        return if (x < 0f || (x == 0f && y < 0f)) -x to -y else x to y
    }

    /** How far the cluster reaches along the unit axis ([axisX], [axisY]). */
    fun lengthAlong(axisX: Float, axisY: Float): Float {
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (node in nodes) {
            val along = homeX[node] * axisX + homeY[node] * axisY
            low = min(low, along)
            high = max(high, along)
        }
        return high - low
    }

    /**
     * The cluster cut into [count] slices of equal node counts along the unit axis ([axisX], [axisY]). A
     * cluster with fewer nodes than slices repeats some. Each node covers a square [spacing] wide, and no
     * spread is under [COMET_MIN_SPREAD_SPACINGS]. Each slice is anchored on its node deepest in the shape,
     * by [clearance]. Given [switches] and [shares], each also takes its nodes' mean fade centre and its cells' share.
     */
    @Suppress("LongParameterList") // The axis, the count, the spacing, the clearance, and the switches and shares a synced head needs.
    fun slices(axisX: Float, axisY: Float, count: Int, spacing: Float, clearance: Clearance, switches: FloatArray? = null, shares: CellShares? = null): List<Slice> {
        // Stable, so nodes level along the axis stay in index order on every platform.
        val sorted = nodes.sortedBy { homeX[it] * axisX + homeY[it] * axisY }.toIntArray()
        return List(count) { slice ->
            val start = min(slice * sorted.size / count, sorted.size - 1)
            val end = max(start + 1, (slice + 1) * sorted.size / count)
            var sumX = 0f
            var sumY = 0f
            for (position in start until end) {
                sumX += homeX[sorted[position]]
                sumY += homeY[sorted[position]]
            }
            val members = end - start
            val centerX = sumX / members
            val centerY = sumY / members
            var anchor = sorted[start]
            var anchorOffset = Float.MAX_VALUE
            for (position in start until end) {
                val node = sorted[position]
                val deltaX = homeX[node] - centerX
                val deltaY = homeY[node] - centerY
                val offset = deltaX * deltaX + deltaY * deltaY
                val depth = clearance.depthOf(node)
                val anchorDepth = clearance.depthOf(anchor)
                if (depth > anchorDepth || (depth == anchorDepth && offset < anchorOffset)) {
                    anchor = node
                    anchorOffset = offset
                }
            }
            val spread = max(sqrt(members / PI_FLOAT), COMET_MIN_SPREAD_SPACINGS) * spacing
            if (switches == null || shares == null) return@List Slice(homeX[anchor], homeY[anchor], spread, clearance.depthOf(anchor))
            val sliceNodes = sorted.copyOfRange(start, end)
            Slice(homeX[anchor], homeY[anchor], spread, clearance.depthOf(anchor), meanSwitchCentre(sliceNodes, switches), shares.shareOver(sliceNodes), sliceNodes)
        }
    }

    private inline fun mean(value: (Int) -> Float): Float {
        var sum = 0f
        for (node in nodes) sum += value(node)
        return sum / nodes.size
    }
}

/**
 * How deep each point lies inside the shape the [members] draw, on a grid one node [spacing] wide. A cell is
 * inside when at least half the nodes of its 3 × 3 block are members, which closes the holes the fill
 * chance leaves; a cell's steps are how many cells, side or corner, separate it from the nearest outside
 * cell, beyond the grid included.
 */
internal class Clearance(members: BooleanArray, homeX: FloatArray, homeY: FloatArray, count: Int, private val spacing: Float) {
    private val grid = CellGrid(homeX, homeY, count, spacing)
    private val steps = IntArray(grid.size) { Int.MAX_VALUE }

    init {
        val shown = IntArray(grid.size)
        val total = IntArray(grid.size)
        for (node in 0 until count) {
            total[grid.cellOf[node]]++
            if (members[node]) shown[grid.cellOf[node]]++
        }
        val inside = BooleanArray(grid.size) { cell ->
            var shownNear = shown[cell]
            var totalNear = total[cell]
            grid.forEachNeighbour(cell) { neighbour ->
                shownNear += shown[neighbour]
                totalNear += total[neighbour]
            }
            totalNear > 0 && 2 * shownNear >= totalNear
        }
        val queue = IntArray(grid.size)
        var tail = 0
        for (cell in 0 until grid.size) {
            if (inside[cell]) continue
            steps[cell] = 0
            queue[tail++] = cell
        }
        // Outside the grid is outside the shape: border cells are one step from it. Queued after every 0.
        for (cell in 0 until grid.size) {
            val column = cell % grid.columns
            val row = cell / grid.columns
            val onBorder = column == 0 || row == 0 || column == grid.columns - 1 || row == grid.rows - 1
            if (!inside[cell] || !onBorder) continue
            steps[cell] = 1
            queue[tail++] = cell
        }
        var head = 0
        while (head < tail) {
            val cell = queue[head++]
            grid.forEachNeighbour(cell) { next ->
                if (steps[next] > steps[cell] + 1) {
                    steps[next] = steps[cell] + 1
                    queue[tail++] = next
                }
            }
        }
    }

    /**
     * How far [node] lies inside the shape, in pixels: from its cell's steps, half a cell less, so a disc this
     * wide around anywhere in the cell stays in. Never under one spacing.
     */
    fun depthOf(node: Int): Float = max(steps[grid.cellOf[node]] - 0.5f, 1f) * spacing

    /** Runs [action] on every inside cell's center and steps, for [MorpheusGraphCometDebug.clearance]. */
    fun forEachInsideCell(action: (centerX: Float, centerY: Float, steps: Int) -> Unit) {
        for (cell in 0 until grid.size) {
            if (steps[cell] <= 0) continue
            action(grid.lowX + (cell % grid.columns + 0.5f) * grid.cell, grid.lowY + (cell / grid.columns + 0.5f) * grid.cell, steps[cell])
        }
    }
}

/** A stable random in 0 until 1 for one head's draw, by its two pieces' first nodes, its slice, a [salt] and the layer's [seed]. */
private fun headRandom(sourceNode: Int, targetNode: Int, slice: Int, salt: Int, seed: Int): Float {
    var hash = sourceNode * 73_856_093 xor targetNode * 19_349_663 xor slice * 83_492_791 xor (seed + salt * 0x27D4EB2D) * 0x165667B1
    hash = hash xor (hash ushr 13)
    hash *= 0x5BD1E995
    hash = hash xor (hash ushr 15)
    hash *= 0x5BD1E995
    hash = hash xor (hash ushr 13)
    return (hash ushr 8) / 16_777_216f
}

private fun centroidDistanceSquared(first: Cluster, second: Cluster): Float {
    val deltaX = second.centerX - first.centerX
    val deltaY = second.centerY - first.centerY
    return deltaX * deltaX + deltaY * deltaY
}

/**
 * The zone of each cluster of [members] that sends or takes comets: the cells whose nodes, on average, switch within
 * [width] of the cluster's [last] (or first) cell, and further cells in that order while the zone holds fewer than
 * [COMET_MIN_CLUSTER] nodes, so a small piece still sends or takes a head. Each shape picks its own, so a piece the
 * front reaches late still takes part, and whole cells keep the front's jitter from speckling the zone into stray
 * fragments.
 */
@Suppress("LongParameterList") // The members, their switches, the grid, the homes and their count, the zone's width and its end.
private fun zoneOf(members: BooleanArray, switches: FloatArray, grid: CellGrid, homeX: FloatArray, homeY: FloatArray, count: Int, width: Float, last: Boolean): BooleanArray {
    val zone = BooleanArray(count)
    for (cluster in clustersOf(members, grid, homeX, homeY, count)) {
        val cellNodes = cluster.nodes.groupBy { grid.cellOf[it] }
        val means = cellNodes.mapValues { (_, nodes) -> meanSwitchCentre(nodes.toIntArray(), switches) }
        val inZone = zoneCells(means, cellNodes.mapValues { (_, nodes) -> nodes.size }, width, last)
        for (node in cluster.nodes) zone[node] = zone[node] || grid.cellOf[node] in inZone
    }
    return zone
}

/**
 * The mean switch fade centre of [nodes] by [switches], skipping a node with no switch: one the same at both ends,
 * though its presence crosses the threshold that puts it in a comet mask. 0.5 when none of them switches.
 */
private fun meanSwitchCentre(nodes: IntArray, switches: FloatArray): Float {
    var centres = 0f
    var switched = 0
    for (node in nodes) {
        val switchAt = switches[node]
        if (switchAt.isNaN()) continue
        centres += switchCentre(switchAt)
        switched++
    }
    return if (switched > 0) centres / switched else 0.5f
}

/**
 * The cells of one cluster in its zone, by their [means] switch fade centres and node [counts]: those within [width]
 * of its [last] (or first) cell, and further cells in that order while they hold fewer than [COMET_MIN_CLUSTER] nodes.
 */
private fun zoneCells(means: Map<Int, Float>, counts: Map<Int, Int>, width: Float, last: Boolean): Set<Int> {
    val ordered = means.entries.sortedWith(compareBy({ (_, mean) -> if (last) -mean else mean }, { (cell, _) -> cell }))
    val edge = ordered.first().value
    val inZone = HashSet<Int>()
    var zoneNodes = 0
    for ((cell, mean) in ordered) {
        val outside = if (last) mean < edge - width else mean > edge + width
        if (outside && zoneNodes >= COMET_MIN_CLUSTER) break
        inZone += cell
        zoneNodes += counts.getValue(cell)
    }
    return inZone
}

/**
 * The [members] whose cells of [grid] touch, side or corner, grouped by union-find over the occupied cells:
 * no distance checks. Groups come in the order of their first node, small ones included.
 */
private fun clustersOf(members: BooleanArray, grid: CellGrid, homeX: FloatArray, homeY: FloatArray, count: Int): List<Cluster> {
    val parents = IntArray(grid.size) { -1 }
    for (node in 0 until count) if (members[node]) parents[grid.cellOf[node]] = grid.cellOf[node]
    unionTouchingCells(parents, grid.columns, grid.rows)
    val groupOfRoot = IntArray(parents.size) { -1 }
    val groups = ArrayList<ArrayList<Int>>()
    for (node in 0 until count) {
        if (!members[node]) continue
        val root = find(parents, grid.cellOf[node])
        if (groupOfRoot[root] < 0) {
            groupOfRoot[root] = groups.size
            groups.add(ArrayList())
        }
        groups[groupOfRoot[root]] += node
    }
    return groups.map { group ->
        Cluster(group.toIntArray(), group.map { grid.cellOf[it] }.distinct().sorted().toIntArray(), homeX, homeY)
    }
}

/** Joins every occupied cell of a [columns] × [rows] grid with each occupied cell it touches, side or corner. */
private fun unionTouchingCells(parents: IntArray, columns: Int, rows: Int) {
    for (cell in parents.indices) {
        if (parents[cell] < 0) continue
        val column = cell % columns
        val row = cell / columns
        if (column + 1 < columns) union(parents, cell, cell + 1)
        if (row + 1 < rows) {
            if (column > 0) union(parents, cell, cell + columns - 1)
            union(parents, cell, cell + columns)
            if (column + 1 < columns) union(parents, cell, cell + columns + 1)
        }
    }
}

/** Joins [second]'s set to [first]'s, when [second] is an occupied cell. */
private fun union(parents: IntArray, first: Int, second: Int) {
    if (parents[second] < 0) return
    val firstRoot = find(parents, first)
    val secondRoot = find(parents, second)
    if (firstRoot != secondRoot) parents[max(firstRoot, secondRoot)] = min(firstRoot, secondRoot)
}

private fun find(parents: IntArray, cell: Int): Int {
    var current = cell
    while (parents[current] != current) {
        parents[current] = parents[parents[current]]
        current = parents[current]
    }
    return current
}
