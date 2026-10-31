package com.rossomak.morpheus

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Prints, per layer, still and morph frames apart, how much work the alpha settle does: its rounds, how often it hits
 * the round cap, the frontier nodes its rounds after the first recompute, the relevant nodes as a share of the pool,
 * and the drawn edges with a raw alpha above 0 among all drawn edges. Mean per frame. Every frame's rounds and cap hit
 * must match [ReferenceAlphaSettle]'s. Only runs with `MORPHEUS_BENCHMARK` set:
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*AlphaSettleReport' -i`
 */
class AlphaSettleReport {

    @Test
    fun `alpha settle work over the showcase's loop`() = benchmark(this::class, "showcase loop") {
        report(SHOWCASE_LOOP_FRAMES, ::showcaseMorphAt)
    }

    @Test
    fun `alpha settle work over the playground's loop`() = benchmark(this::class, "playground loop") {
        report(PLAYGROUND_LOOP_FRAMES, ::playgroundMorphAt)
    }

    private fun report(frames: Int, morphAt: (time: Float) -> MorpheusGraphMorph) {
        val references = HashMap<LayerField, ReferenceAlphaSettle>()
        val tallies = LinkedHashMap<String, Tally>()
        val mismatches = ArrayList<String>()
        replayLoopFields(frames, morphAt = morphAt) { time, morph, layers, fields ->
            val motion = if (morph.isMidMorph) "morph" else "still"
            fields.forEachIndexed { index, field ->
                val reference = references.getOrPut(field) { ReferenceAlphaSettle() }.apply { settle(field) }
                if (field.alphaRounds != reference.rounds || field.alphaRoundCapHit != reference.roundCapHit) {
                    mismatches +=
                        "t=$time slot ${layers[index].slotFromFront}: rounds ${field.alphaRounds} vs ${reference.rounds}, cap ${field.alphaRoundCapHit} vs ${reference.roundCapHit}"
                }
                tallies.getOrPut("slot ${layers[index].slotFromFront}, $motion") { Tally() }.add(field)
            }
        }
        for ((name, tally) in tallies) println("$name: ${tally.describe()}")
        withClue(mismatches.take(MISMATCHES_SHOWN).joinToString("\n")) { mismatches.size shouldBe 0 }
    }

    /** One layer's settle work, summed over its frames. */
    private class Tally {
        private var frames = 0
        private var rounds = 0L
        private var maxRounds = 0
        private var capHits = 0
        private var frontierVisits = 0L
        private var relevantShare = 0.0
        private var relevantNodes = 0L
        private var pool = 0L
        private var liveEdges = 0L
        private var drawnEdges = 0L

        fun add(field: LayerField) {
            frames++
            rounds += field.alphaRounds
            maxRounds = maxOf(maxRounds, field.alphaRounds)
            if (field.alphaRoundCapHit) capHits++
            frontierVisits += field.alphaFrontierVisits
            relevantShare += field.relevantNodeCount.toDouble() / field.nodeCount
            relevantNodes += field.relevantNodeCount
            pool += field.nodeCount
            liveEdges += field.liveEdgeCount
            drawnEdges += field.drawnEdgeCount
        }

        fun describe(): String =
            "$frames frames; rounds ${(rounds * HUNDRED / frames).roundToInt() / HUNDRED} mean, $maxRounds max; round cap hit in $capHits; " +
                "frontier visits ${frontierVisits / frames} a frame; relevant nodes ${relevantNodes / frames} of ${pool / frames} " +
                "(${(relevantShare * HUNDRED * HUNDRED / frames).roundToInt() / HUNDRED}%); edges above alpha 0 ${liveEdges / frames} of ${drawnEdges / frames} drawn"
    }

    private companion object {
        const val HUNDRED = 100.0
    }
}
