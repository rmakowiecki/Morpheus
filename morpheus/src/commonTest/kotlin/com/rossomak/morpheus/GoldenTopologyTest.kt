package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.fail

/**
 * Pins the meshes [LayerField] settles on, so a change meant to leave meshes alone can prove it, and
 * one that changes them shows what changed. The expected values live in [goldenTopology] and
 * [goldenZeroJitterTopology]; they must pass on the JVM and in headless Chrome alike.
 *
 * **Stepping**, which every later ticket keeps: each case starts from a fresh field and steps
 * at a fixed 30 fps from t = 0. A still case is recorded at frames 0, 45 and 150 (0 s, 1.5 s, 5 s). A
 * morph case steps its fraction linearly from 0 to 1 over 60 frames, then holds it at 1 until frame 75, and
 * is recorded at fractions 0.25, 0.5, 0.75 and 1 and at frame 75 (settled), one record per mesh drawn. A
 * frame's mesh depends on the frames before it (a fixed triangulation re-settles only when presences or
 * filters change), so the stepping is part of the record.
 *
 * Each case runs with three seeds, except where one run costs most: under a rule that re-settles every
 * frame, on the showcase's dense front mesh, and in the morphs. Those run the first seed only, which
 * keeps the whole test within a few seconds on the JVM. The showcase's morph and the comet playground's
 * three morphs run at their samples' own seeds instead, on their own surfaces.
 *
 * Per case and frame, the record holds the edge, alive, repaired-edge and pruned-node counts, the nodes
 * only a comet lights that a morph's mesh joins, and a 64-bit hash of the sorted edge keys and the alive mask. Alphas aren't recorded; capture-renderer
 * comparisons cover them. The hash mixer is deliberately separate from the hash tables', so retuning
 * one doesn't invalidate the record.
 *
 * **Zero-jitter cases** sit on a perfect grid, so Delaunay meets co-circular points and its tie-breaks
 * decide the mesh. They are recorded apart in [goldenZeroJitterTopology] because #26 may change them
 * on purpose; every other ticket keeps them.
 *
 * **Regenerating**: flip [REGENERATE] to true locally and run `./gradlew :morpheus:jvmTest --tests
 * '*GoldenTopologyTest'`. Each test prints its whole table in source form (find it in the system-out of
 * build/test-results/jvmTest/TEST-com.rossomak.morpheus.GoldenTopologyTest.xml); paste both over the
 * tables in GoldenTopologyRecord.kt. Never commit the switch flipped: with it on, the test asserts nothing.
 */
class GoldenTopologyTest {

    @Test
    fun `every layer settles on the recorded mesh at every recorded frame`() {
        assertRecorded(recordAll(stillCases() + morphCases()), goldenTopology, "goldenTopology")
    }

    /** The showcase's comets must keep lighting nodes outside both ends, or the record stops covering the transit mesh. */
    @Test
    fun `the showcase morph joins comet-only nodes on every recorded surface`() {
        val case = morphCases().first { it.name == SHOWCASE_MORPH }
        for (seed in case.seeds) {
            for ((width, height) in case.surfaces) {
                val joined = case.record(seed, width, height).maxOf { (_, record) -> record.cometNodes }
                if (joined == 0) fail("$SHOWCASE_MORPH/seed $seed/${width.toInt()}x${height.toInt()} joins no comet-only node")
            }
        }
    }

    @Test
    fun `every edge rule settles a zero-jitter grid on the recorded mesh`() {
        assertRecorded(recordAll(zeroJitterCases()), goldenZeroJitterTopology, "goldenZeroJitterTopology")
    }

    private fun assertRecorded(actual: Map<String, TopologyRecord>, expected: Map<String, TopologyRecord>, tableName: String) {
        if (REGENERATE) {
            println(tableSource(tableName, actual))
            return
        }
        val mismatches = (expected.keys + actual.keys).sorted().mapNotNull { name ->
            val want = expected[name]
            val got = actual[name]
            when {
                want == got -> null
                want == null -> "$name: not recorded, got ${got?.describe()}"
                got == null -> "$name: recorded but no longer run, expected ${want.describe()}"
                else -> "$name:\n    expected ${want.describe()}\n    actual   ${got.describe()}"
            }
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} of ${expected.size} recorded meshes in $tableName changed:\n" + mismatches.joinToString("\n"))
        }
    }

    private fun recordAll(cases: List<GoldenCase>): Map<String, TopologyRecord> {
        val records = LinkedHashMap<String, TopologyRecord>()
        val runs = cases.flatMap { case -> case.seeds.flatMap { seed -> case.surfaces.map { surface -> Triple(case, seed, surface) } } }
        for ((case, seed, surface) in runs) {
            val (width, height) = surface
            val prefix = "${case.name}/seed $seed/${width.toInt()}x${height.toInt()}"
            case.record(seed, width, height).forEach { (frame, record) -> records["$prefix/$frame"] = record }
        }
        return records
    }

    private fun stillCases(): List<GoldenCase> {
        val presetCases = MorpheusGraphPresets.all.flatMapIndexed { presetIndex, preset ->
            preset.layers.mapIndexed { layerIndex, layer ->
                StillCase("preset $presetIndex/layer $layerIndex", layer, STANDARD_SURFACES)
            }
        }
        val showcaseCases = listOf("pills" to heroPills, "ribbons" to heroRibbons).flatMap { (end, spec) ->
            spec.layers.mapIndexed { layerIndex, layer -> StillCase("hero $end/layer $layerIndex", layer, HERO_SURFACES) }
        }
        return presetCases + showcaseCases
    }

    private fun morphCases(): List<GoldenCase> = listOf(
        // Same density at both ends: one morph family.
        // At the showcase's own seed: the first seed's layout plans so few comets that none lights a node outside both ends.
        MorphCase(SHOWCASE_MORPH, heroPills, heroRibbons, HERO_SURFACES, seeds = listOf(heroPills.seed)),
        // A different density is a different morph family, so the front layer dissolves into another mesh.
        MorphCase(
            "hero pills dissolving",
            heroPills,
            heroPills.copy(layers = heroPills.layers.mapIndexed { index, layer -> if (index == heroPills.layers.lastIndex) layer.copy(density = DISSOLVE_DENSITY) else layer }),
            HERO_SURFACES,
        ),
    ) + playgroundLegs.map { (leg, morph) -> MorphCase("playground $leg", morph.from, morph.to, PLAYGROUND_SURFACES, seeds = listOf(playgroundFrame.seed)) }

    private fun zeroJitterCases(): List<GoldenCase> = EdgeRule.entries.map { edgeRule ->
        StillCase("zero jitter/$edgeRule", MorpheusGraphLayerSpec(edgeRule = edgeRule, jitter = 0f), STANDARD_SURFACES)
    }

    private interface GoldenCase {
        val name: String
        val surfaces: List<Pair<Float, Float>>
        val seeds: List<Int>

        /** The records of one run on a fresh field, keyed by frame (and mesh, for a morph). */
        fun record(seed: Int, width: Float, height: Float): List<Pair<String, TopologyRecord>>
    }

    private class StillCase(override val name: String, private val layer: MorpheusGraphLayerSpec, override val surfaces: List<Pair<Float, Float>>) : GoldenCase {
        override val seeds = if (layer.edgeRule == EdgeRule.FixedTriangulation && layer.density < DENSE_DENSITY) SEEDS else SEEDS.take(1)

        override fun record(seed: Int, width: Float, height: Float): List<Pair<String, TopologyRecord>> {
            val field = LayerField(layer, seed, max(width, height), PX_PER_DP)
            val records = ArrayList<Pair<String, TopologyRecord>>()
            for (frame in 0..STILL_FRAMES.last()) {
                field.advance(frame * FRAME_SECONDS, width, height)
                if (frame in STILL_FRAMES) records += "frame $frame" to TopologyRecord.of(field)
            }
            return records
        }
    }

    /** Both ends take the run's seed, so every seed draws the same morph on its own layout; [seeds] defaults to the first. */
    private class MorphCase(
        override val name: String,
        private val from: MorpheusGraphSpec,
        private val to: MorpheusGraphSpec,
        override val surfaces: List<Pair<Float, Float>>,
        override val seeds: List<Int> = SEEDS.take(1),
    ) : GoldenCase {

        override fun record(seed: Int, width: Float, height: Float): List<Pair<String, TopologyRecord>> {
            val morph = MorpheusGraphMorph(from.copy(seed = seed), to.copy(seed = seed), fraction = 0f)
            // One field per mesh, kept across frames the way the modifier keeps them.
            val fields = HashMap<String, LayerField>()
            val records = ArrayList<Pair<String, TopologyRecord>>()
            for (frame in 0..MORPH_SETTLED_FRAME) {
                val fraction = min(frame / MORPH_FRAMES.toFloat(), 1f)
                for (layer in morphLayers(morph.copy(fraction = fraction), TEST_FILL)) {
                    val mesh = "slot ${layer.slotFromFront}/density ${layer.spec.density.toInt()}"
                    val field = fields.getOrPut(mesh) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                    field.spec = layer.spec
                    field.shape = layer.shape
                    field.advance(frame * FRAME_SECONDS, width, height)
                    if (frame in MORPH_RECORDED_FRAMES) records += "${morphLabel(frame)}/$mesh" to TopologyRecord.of(field)
                }
            }
            return records
        }

        /** Whole percent, since a float prints differently on the JVM and in the browser. */
        private fun morphLabel(frame: Int) = if (frame == MORPH_SETTLED_FRAME) "settled" else "fraction ${frame * 100 / MORPH_FRAMES}%"
    }

    private companion object {
        /** Flip locally to print the tables instead of checking them; see the class doc. Never commit it true. */
        const val REGENERATE = false

        val SEEDS = listOf(1, 42, 9_001)

        /** A layer at least this dense runs [SEEDS]' first seed only. */
        const val DENSE_DENSITY = 40f
        val STANDARD_SURFACES = listOf(640f to 360f, 360f to 640f)

        /** The showcase's own canvas besides the standard ones. */
        val HERO_SURFACES = STANDARD_SURFACES + (540f to 270f)

        /** The playground's square capture canvas, 480 dp at device scale 2. */
        val PLAYGROUND_SURFACES = listOf(960f to 960f)
        const val FRAME_SECONDS = 1f / 30f
        val STILL_FRAMES = listOf(0, 45, 150)
        const val MORPH_FRAMES = 60
        const val MORPH_SETTLED_FRAME = 75
        val MORPH_RECORDED_FRAMES = listOf(15, 30, 45, MORPH_FRAMES, MORPH_SETTLED_FRAME)
        const val DISSOLVE_DENSITY = 48f
        const val SHOWCASE_MORPH = "hero pills to ribbons"
        const val PX_PER_DP = 2.75f
        val TEST_FILL = MorpheusGraphFill.Solid(Color.White)
    }
}

/**
 * One recorded mesh: its counts, [cometNodes] (the comet-only nodes a morph's mesh joins, left out of the
 * source when 0), and [hash] over its sorted edge keys and alive mask (see [topologyHash]).
 */
internal data class TopologyRecord(val edges: Int, val alive: Int, val repaired: Int, val pruned: Int, val hash: ULong, val cometNodes: Int = 0) {

    fun describe(): String = "edges=$edges alive=$alive repaired=$repaired pruned=$pruned cometNodes=$cometNodes hash=${hash.toHex()}"

    /** One line, or wrapped onto three to fit the line length when it has [cometNodes] or [wraps]. */
    fun toSource(wraps: Boolean = false): String {
        val counts = "edges = $edges, alive = $alive, repaired = $repaired, pruned = $pruned, hash = 0x${hash.toHex()}uL"
        return when {
            cometNodes != 0 -> "TopologyRecord(\n        $counts,\n        cometNodes = $cometNodes,\n    )"
            wraps -> "TopologyRecord(\n        $counts,\n    )"
            else -> "TopologyRecord($counts)"
        }
    }

    companion object {
        fun of(field: LayerField): TopologyRecord = with(field.topology) {
            TopologyRecord(
                edges = edges.size,
                alive = alive.count { it },
                repaired = repairedEdgeCount,
                pruned = field.prunedNodeCount,
                hash = topologyHash(edges, alive),
                cometNodes = field.cometOnlyNodeCount,
            )
        }
    }
}

/** A 64-bit hash of [edges] in order, then [alive] packed 64 nodes to a word; the same on every platform. */
internal fun topologyHash(edges: LongArray, alive: BooleanArray): ULong {
    var hash = HASH_SEED
    hash = mixHash(hash xor edges.size.toLong())
    for (key in edges) hash = mixHash(hash xor key)
    hash = mixHash(hash xor alive.size.toLong())
    var word = 0L
    for (node in alive.indices) {
        if (alive[node]) word = word or (1L shl (node % Long.SIZE_BITS))
        if (node % Long.SIZE_BITS == Long.SIZE_BITS - 1 || node == alive.lastIndex) {
            hash = mixHash(hash xor word)
            word = 0L
        }
    }
    return hash.toULong()
}

/** SplitMix64's finalizer: every input bit reaches every output bit. */
private fun mixHash(value: Long): Long {
    var mixed = value
    mixed = (mixed xor (mixed ushr 30)) * MIX_FIRST
    mixed = (mixed xor (mixed ushr 27)) * MIX_SECOND
    return mixed xor (mixed ushr 31)
}

private val HASH_SEED = 0x6A09E667F3BCC908uL.toLong()
private val MIX_FIRST = 0xBF58476D1CE4E5B9uL.toLong()
private val MIX_SECOND = 0x94D049BB133111EBuL.toLong()

private fun ULong.toHex(): String = toString(radix = 16).padStart(16, '0')

/** [records] as the Kotlin source of a table named [tableName], ready to paste into GoldenTopologyRecord.kt. */
/** The repository's line length (`.editorconfig`), which a recorded line wraps to fit. */
private const val MAX_LINE_LENGTH = 180

private fun tableSource(tableName: String, records: Map<String, TopologyRecord>): String = buildString {
    appendLine("internal val $tableName: Map<String, TopologyRecord> = mapOf(")
    for ((name, record) in records) {
        val line = "    \"$name\" to ${record.toSource()},"
        appendLine(if (line.length > MAX_LINE_LENGTH) "    \"$name\" to ${record.toSource(wraps = true)}," else line)
    }
    append(")")
}
