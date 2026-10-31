package com.rossomak.morpheus

import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * [LayerField]'s alpha settle, which recomputes only each round's frontier, against [ReferenceAlphaSettle], which
 * sweeps every node and edge every round: after every `advance`, both settle the same inputs and every dot alpha,
 * glow factor, tally entry and edge alpha must match bit for bit, for every node in the pool, as must the visible
 * node count, the rounds and whether the round cap was hit.
 */
class AlphaSettleTest {

    @Test
    fun `the alpha settle matches the reference on every golden case and the showcase's loop`() {
        val check = SettleCheck()
        GoldenTopologyTest().cases().forEach(check::compareCase)
        replayLoopFields(SHOWCASE_LOOP_FRAMES, morphAt = ::showcaseMorphAt) { _, morph, layers, fields ->
            fields.forEachIndexed { index, field -> check.compare("showcase loop/slot ${layers[index].slotFromFront}", field, morphing = morph.isMidMorph) }
        }
        check.assertMatched()
        // The cases must reach every path the frontier rounds take a shortcut on.
        withClue("frames whose first limit caps an edge neither of whose dots changed") { check.capsWithStillDots shouldBeGreaterThan 0 }
        withClue("morph frames with edges at a morph weight of 0") { check.zeroWeightFrames shouldBeGreaterThan 0 }
        withClue("nodes leaving the relevant list as their last edge finished fading") { check.nodesLeavingMidFade shouldBeGreaterThan 0 }
    }

    /** Still fields settle in two rounds; the playground's dense morph meshes are where the settle runs out of rounds. */
    @Test
    fun `the alpha settle matches the reference where it hits the round cap`() {
        val check = SettleCheck()
        check.compareCase(GoldenTopologyTest().cases().single { it.name == ROUND_CAP_CASE })
        check.assertMatched()
        withClue("frames hitting the round cap") { check.roundCapHits shouldBeGreaterThan 0 }
    }

    /** Compares fields with their own [ReferenceAlphaSettle]s, and counts how often the cases reach each edge case. */
    private class SettleCheck {
        private val references = HashMap<LayerField, ReferenceAlphaSettle>()

        /** Per field, the nodes the last frame kept relevant only through a drawn edge, at presence 0. */
        private val heldByEdges = HashMap<LayerField, Set<Int>>()
        val mismatches = ArrayList<String>()
        var roundCapHits = 0
        var capsWithStillDots = 0
        var zeroWeightFrames = 0
        var nodesLeavingMidFade = 0

        fun compareCase(case: GoldenTopologyTest.GoldenCase) {
            for (seed in case.seeds) {
                for ((width, height) in case.surfaces) {
                    val run = "${case.name}/seed $seed/${width.toInt()}x${height.toInt()}"
                    case.record(seed, width, height) { mesh, field -> compare("$run/$mesh", field, morphing = mesh != "still") }
                }
            }
        }

        fun assertMatched() {
            withClue("${mismatches.size} mismatches, the first: ${mismatches.take(MISMATCHES_SHOWN).joinToString("\n")}") { mismatches.size shouldBe 0 }
        }

        fun compare(name: String, field: LayerField, morphing: Boolean) {
            val reference = references.getOrPut(field) { ReferenceAlphaSettle() }.apply { settle(field) }
            val mismatch = mismatchOf(field, reference)
            if (mismatch != null) mismatches += "$name: $mismatch"
            if (reference.roundCapHit) roundCapHits++
            if (reference.cappedWithStillDots > 0) capsWithStillDots++
            if (morphing && (0 until field.drawnEdgeCount).any { field.drawnEdgeFade(it) == 0f }) zeroWeightFrames++
            val endpoints = HashSet<Int>()
            for (edge in 0 until field.drawnEdgeCount) {
                val key = field.drawnEdgeKey(edge)
                endpoints += edgeStart(key)
                endpoints += edgeEnd(key)
            }
            nodesLeavingMidFade += heldByEdges[field].orEmpty().count { it !in endpoints && field.nodePresence(it) <= 0f }
            heldByEdges[field] = endpoints.filterTo(HashSet()) { field.nodePresence(it) <= 0f }
        }

        private fun mismatchOf(field: LayerField, reference: ReferenceAlphaSettle): String? {
            for (node in 0 until field.nodeCount) {
                val differs = field.nodeAlpha(node).toRawBits() != reference.nodeAlphas[node].toRawBits() ||
                    field.nodeGlowFactor(node).toRawBits() != reference.glowFactors[node].toRawBits() ||
                    field.brightestEdgeAlpha(node).toRawBits() != reference.brightestEdgeAlphas[node].toRawBits() ||
                    field.secondEdgeAlpha(node).toRawBits() != reference.secondEdgeAlphas[node].toRawBits() ||
                    field.visibleEdgeCount(node) != reference.visibleEdgeCounts[node]
                if (differs) {
                    return "node $node: dot ${field.nodeAlpha(node)} vs ${reference.nodeAlphas[node]}, glow ${field.nodeGlowFactor(node)} vs ${reference.glowFactors[node]}, " +
                        "brightest ${field.brightestEdgeAlpha(node)} vs ${reference.brightestEdgeAlphas[node]}, second ${field.secondEdgeAlpha(node)} vs " +
                        "${reference.secondEdgeAlphas[node]}, visible edges ${field.visibleEdgeCount(node)} vs ${reference.visibleEdgeCounts[node]}"
                }
            }
            for (edge in 0 until field.drawnEdgeCount) {
                if (field.settledEdgeAlpha(edge).toRawBits() != reference.edgeAlphas[edge].toRawBits()) {
                    return "edge $edge: ${field.settledEdgeAlpha(edge)} vs ${reference.edgeAlphas[edge]}"
                }
            }
            return when {
                field.visibleNodeCount != reference.visibleNodeCount -> "visible nodes ${field.visibleNodeCount} vs ${reference.visibleNodeCount}"
                field.alphaRounds != reference.rounds -> "rounds ${field.alphaRounds} vs ${reference.rounds}"
                field.alphaRoundCapHit != reference.roundCapHit -> "round cap hit ${field.alphaRoundCapHit} vs ${reference.roundCapHit}"
                else -> null
            }
        }
    }

    private companion object {
        const val ROUND_CAP_CASE = "playground blooms to cross"
    }
}
