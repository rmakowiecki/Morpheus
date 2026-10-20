package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import com.rossomak.morpheus.ShapePrimitive.Pill
import com.rossomak.morpheus.ShapePrimitive.Ribbon
import com.rossomak.morpheus.ShapePrimitive.Uniform
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Pins down exactly which meshes [LayerField] settles on, so a rewrite of the mesh code can prove it
 * changes nothing, and a ticket that changes meshes on purpose shows what changed. The expected values
 * live in [goldenTopology] and [goldenZeroJitterTopology]; they must pass on the JVM and in headless
 * Chrome alike.
 *
 * **Stepping contract**, which every later ticket keeps: each case starts from a fresh field and steps
 * at a fixed 30 fps from t = 0. A still case is recorded at frames 0, 45 and 150 (0 s, 1.5 s, 5 s). A
 * morph case steps its fraction linearly from 0 to 1 over 60 frames and is recorded at fractions 0.25,
 * 0.5 and 0.75, one record per mesh drawn. A frame's mesh depends on the frames before it (a fixed
 * triangulation re-settles only when presences or filters change), so the stepping is part of the record.
 *
 * Each case runs with three seeds, except where one run costs most: under a rule that re-settles every
 * frame, on the showcase's dense front mesh, and in the morphs. Those run the first seed only, which
 * keeps the whole test within a few seconds on the JVM.
 *
 * Per case and frame, the record holds the edge, alive, repaired-edge and pruned-node counts, and a
 * 64-bit hash of the sorted edge keys and the alive mask. Alphas aren't recorded: tickets that change
 * them prove themselves with capture-renderer comparisons instead.
 *
 * **Zero-jitter cases** sit on a perfect grid, so the Delaunay triangulation meets co-circular points
 * and its tie-breaks decide the mesh. They are kept apart in [goldenZeroJitterTopology] because #26 may
 * change them on purpose; every other ticket keeps them too.
 *
 * **Regenerating**: flip [REGENERATE] to true locally and run `./gradlew :morpheus:jvmTest --tests
 * '*GoldenTopologyTest'`. Each test prints its whole table in source form (find it in the system-out of
 * build/test-results/jvmTest/TEST-com.rossomak.morpheus.GoldenTopologyTest.xml); paste both over the
 * tables in GoldenTopologyRecord.kt. Never commit the switch flipped: with it on, the test asserts nothing.
 */
class GoldenTopologyTest {

    @Test
    fun `every layer settles on the recorded mesh at every recorded frame`() {
        check(recordAll(stillCases() + morphCases()), goldenTopology, "goldenTopology")
    }

    @Test
    fun `every edge rule settles a zero-jitter grid on the recorded mesh`() {
        check(recordAll(zeroJitterCases()), goldenZeroJitterTopology, "goldenZeroJitterTopology")
    }

    private fun check(actual: Map<String, TopologyRecord>, expected: Map<String, TopologyRecord>, tableName: String) {
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
        // One morph family; the front layer's comets also exercise the transit mesh.
        MorphCase("hero pills to ribbons", heroPills, heroRibbons, HERO_SURFACES),
        // A different density is a different morph family, so the front layer dissolves into another mesh.
        MorphCase(
            "hero pills dissolving",
            heroPills,
            heroPills.copy(layers = heroPills.layers.mapIndexed { index, layer -> if (index == heroPills.layers.lastIndex) layer.copy(density = DISSOLVE_DENSITY) else layer }),
            HERO_SURFACES,
        ),
    )

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

    /** Both ends take the run's seed, so every seed draws the same morph on its own layout. */
    private class MorphCase(
        override val name: String,
        private val from: MorpheusGraphSpec,
        private val to: MorpheusGraphSpec,
        override val surfaces: List<Pair<Float, Float>>,
    ) : GoldenCase {
        override val seeds = SEEDS.take(1)

        override fun record(seed: Int, width: Float, height: Float): List<Pair<String, TopologyRecord>> {
            val morph = MorpheusGraphMorph(from.copy(seed = seed), to.copy(seed = seed), fraction = 0f)
            // One field per mesh, kept across frames the way the modifier keeps them.
            val fields = HashMap<String, LayerField>()
            val records = ArrayList<Pair<String, TopologyRecord>>()
            for (frame in 0..MORPH_FRAMES) {
                val fraction = frame / MORPH_FRAMES.toFloat()
                for (layer in morphLayers(morph.copy(fraction = fraction), TestFill)) {
                    val mesh = "slot ${layer.slotFromFront}/density ${layer.spec.density.toInt()}"
                    val field = fields.getOrPut(mesh) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                    field.spec = layer.spec
                    field.shape = layer.shape
                    field.advance(frame * FRAME_SECONDS, width, height)
                    // Labelled in whole percent: a float prints differently on the JVM and in the browser.
                    if (frame in MORPH_RECORDED_FRAMES) records += "fraction ${frame * 100 / MORPH_FRAMES}%/$mesh" to TopologyRecord.of(field)
                }
            }
            return records
        }
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
        const val FRAME_SECONDS = 1f / 30f
        val STILL_FRAMES = listOf(0, 45, 150)
        const val MORPH_FRAMES = 60
        val MORPH_RECORDED_FRAMES = listOf(15, 30, 45)
        const val DISSOLVE_DENSITY = 48f
        const val PX_PER_DP = 2.75f
        val TestFill = MorpheusGraphFill.Solid(Color.White)

        // The "Pills to ribbons" showcase's two ends, copied from samples/shared's showcase/Hero.kt (the
        // library can't depend on the samples). Fills are left out: they never touch the mesh.

        /** Hero.kt's heroFront. */
        val heroFront = MorpheusGraphLayerSpec(
            density = 70f,
            jitter = 0.4f,
            driftAmplitude = 0.3f,
            envelopeFloor = 0.01f,
            maxEdgeFactor = 2.4f,
            minAngleDegrees = 14f,
            edgeKeepChance = 0.92f,
            strokeWidthDp = 0.6f,
            edgeAlpha = 0.6f,
            nodeSizeDp = 1.6f,
            nodeSizeVariance = 0.4f,
            hotNodeShare = 0.12f,
            glowRadiusDp = 10f,
            glowStrength = 0.4f,
            cometStrength = 0.25f,
            cometSpark = 0.6f,
        )

        /** Hero.kt's heroGhost: [MorpheusGraphPresets.defaultBackLayer] at density 16 and alpha 0.3, spelled out. */
        val heroGhost = MorpheusGraphLayerSpec(
            shapes = listOf(Uniform()),
            density = 16f,
            jitter = 0.45f,
            driftAmplitude = 0.35f,
            driftSpeed = 0.5f,
            envelopeFloor = 0.05f,
            waveAmplitude = 0.02f,
            waveSpeed = 0.15f,
            maxEdgeFactor = 2.6f,
            edgeKeepChance = 0.8f,
            strokeWidthDp = 1f,
            edgeAlpha = 0.5f,
            lengthFalloff = 0.3f,
            nodeSizeDp = 4f,
            nodeSizeVariance = 0.2f,
            alpha = 0.3f,
        )

        /** Hero.kt's heroPills. */
        val heroPills = MorpheusGraphSpec(
            seed = 23,
            layers = listOf(
                heroGhost.copy(waveAmplitude = 0.06f, waveSpeed = 0.4f),
                heroFront.copy(
                    shapes = listOf(
                        Pill(x = 0.25f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = 55f),
                        Pill(x = 0.75f, y = 0.5f, length = 0.42f, thickness = 0.13f, rotation = -55f),
                    ),
                ),
            ),
        )

        /** Hero.kt's heroRibbons. */
        val heroRibbons = heroPills.copy(
            layers = listOf(
                heroGhost,
                heroFront.copy(
                    shapes = listOf(
                        Ribbon(center = 0.5f, width = 0.15f, curve = 0.18f, tilt = 25f),
                        Ribbon(center = 0.5f, width = 0.15f, curve = 0.18f, tilt = -25f),
                    ),
                    waveAmplitude = 0.06f,
                    waveSpeed = 0.45f,
                ),
            ),
        )
    }
}

/** One recorded mesh: its counts, and [hash] over its sorted edge keys and alive mask (see [topologyHash]). */
internal data class TopologyRecord(val edges: Int, val alive: Int, val repaired: Int, val pruned: Int, val hash: ULong) {

    fun describe(): String = "edges=$edges alive=$alive repaired=$repaired pruned=$pruned hash=${hash.toHex()}"

    fun toSource(): String = "TopologyRecord(edges = $edges, alive = $alive, repaired = $repaired, pruned = $pruned, hash = 0x${hash.toHex()}uL)"

    companion object {
        fun of(field: LayerField): TopologyRecord = with(field.topology) {
            TopologyRecord(
                edges = edges.size,
                alive = alive.count { it },
                repaired = repairedEdgeCount,
                pruned = field.prunedNodeCount,
                hash = topologyHash(edges, alive),
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
private fun tableSource(tableName: String, records: Map<String, TopologyRecord>): String = buildString {
    appendLine("internal val $tableName: Map<String, TopologyRecord> = mapOf(")
    for ((name, record) in records) appendLine("    \"$name\" to ${record.toSource()},")
    append(")")
}
