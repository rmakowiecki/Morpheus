package com.rossomak.morpheus

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** How [PointBatches] sorts edges and dots into batches, and that the alpha rule survives its rounding. */
class PointBatchesTest {

    @Test
    fun `edges at one 8-bit level share one batch`() {
        val batches = PointBatches()
        batches.addEdge(0f, 0f, 1f, 1f, alpha = 128 / 255f)
        batches.addEdge(0f, 0f, 2f, 2f, alpha = 128.4f / 255f)
        batches.addEdge(0f, 0f, 3f, 3f, alpha = 0.9f)
        batches.edgeCalls shouldBe 2
        batches.clearEdges()
        batches.edgeCalls shouldBe 0
    }

    @Test
    fun `an edge is drawn at its nearest 8-bit level, as a color stores it`() {
        PointBatches.edgeLevel(100.49f / 255f) shouldBe 100
        PointBatches.edgeLevel(100.5f / 255f) shouldBe 101
        PointBatches.edgeLevel(MIN_VISIBLE_ALPHA) shouldBe 1
        PointBatches.edgeLevel(1f) shouldBe 255
    }

    @Test
    fun `a dot is drawn at its alpha rounded up to the alpha step`() {
        val batches = PointBatches()
        batches.dotLevel(100 / 255f) shouldBe 100
        batches.dotLevel(100.2f / 255f) shouldBe 102
        batches.dotLevel(MIN_VISIBLE_ALPHA) shouldBe 2
        batches.dotLevel(1f) shouldBe 255
    }

    @Test
    fun `a dot is drawn at its size bin's center, the bins split the spec's radius range`() {
        val batches = PointBatches()
        batches.clearDots(smallestRadius = 2f, largestRadius = 10f)
        batches.sizeBinWidth shouldBe 1f
        batches.sizeBin(2f) shouldBe 0
        batches.binRadius(0) shouldBe 2.5f
        batches.sizeBin(9.99f) shouldBe DOT_SIZE_BINS - 1
        withClue("a radius past the range goes in the last bin") { batches.sizeBin(12f) shouldBe DOT_SIZE_BINS - 1 }
    }

    @Test
    fun `a layer whose dots are all one size has one size bin, at that size`() {
        val batches = PointBatches()
        batches.clearDots(smallestRadius = 3f, largestRadius = 3f)
        batches.sizeBinWidth shouldBe 0f
        batches.binRadius(batches.sizeBin(3f)) shouldBe 3f
    }

    @Test
    fun `dots punch once per size bin and add once per alpha bucket and size bin`() {
        val batches = PointBatches()
        batches.clearDots(smallestRadius = 2f, largestRadius = 10f)
        batches.addDot(0f, 0f, radius = 2.1f, alpha = PUNCH_MIN_ALPHA)
        batches.addDot(5f, 5f, radius = 2.2f, alpha = PUNCH_MIN_ALPHA)
        batches.addDot(9f, 9f, radius = 9f, alpha = PUNCH_MIN_ALPHA)
        batches.punchCalls shouldBe 2
        batches.addCalls shouldBe 2
        batches.addDot(3f, 3f, radius = 2.1f, alpha = 1f)
        batches.punchCalls shouldBe 2
        batches.addCalls shouldBe 3
        batches.clearDots(smallestRadius = 2f, largestRadius = 10f)
        batches.punchCalls shouldBe 0
        batches.addCalls shouldBe 0
    }

    @Test
    fun `a dot fainter than the punch threshold is added but never punches`() {
        val batches = PointBatches()
        batches.clearDots(smallestRadius = 2f, largestRadius = 10f)
        val fainterThanPunch = PUNCH_MIN_ALPHA - 0.01f
        batches.addDot(0f, 0f, radius = 2.1f, alpha = fainterThanPunch)
        batches.addDot(9f, 9f, radius = 9f, alpha = 0.3f)
        batches.punchCalls shouldBe 0
        batches.addCalls shouldBe 2
        batches.addDot(5f, 5f, radius = 2.2f, alpha = PUNCH_MIN_ALPHA)
        batches.punchCalls shouldBe 1
    }

    @Test
    fun `a buffer grows to fit and keeps its array when cleared`() {
        val buffer = PointBuffer()
        repeat(100) { buffer.add(it.toFloat(), -it.toFloat()) }
        buffer.size shouldBe 200
        buffer.floats[198] shouldBe 99f
        val grown = buffer.floats
        buffer.clear()
        buffer.size shouldBe 0
        buffer.addLine(1f, 2f, 3f, 4f)
        (buffer.floats === grown) shouldBe true
        buffer.floats.copyOf(buffer.size).toList() shouldBe listOf(1f, 2f, 3f, 4f)
    }

    @Test
    fun `no drawn dot is fainter than an edge it anchors once both are rounded, over the showcase's and the playground's loops`() {
        val batches = PointBatches()
        val broken = ArrayList<String>()
        val loops = listOf(Triple("showcase", SHOWCASE_LOOP_FRAMES, ::showcaseMorphAt), Triple("playground", PLAYGROUND_LOOP_FRAMES, ::playgroundMorphAt))
        for ((name, frames, morphAt) in loops) {
            replayLoopFields(frames, FRAME_STRIDE, morphAt) { time, _, layers, fields ->
                fields.forEachIndexed { index, field ->
                    field.forEachVisibleEdge(time) { alpha, start, end ->
                        for (node in intArrayOf(start, end)) {
                            if (batches.dotLevel(field.nodeAlpha(node)) < PointBatches.edgeLevel(alpha)) {
                                broken += "$name at ${time}s slot ${layers[index].slotFromFront} node $node"
                            }
                        }
                    }
                }
            }
        }
        withClue(broken.take(BROKEN_SHOWN).joinToString("\n")) { broken.size shouldBe 0 }
    }

    private companion object {
        /** Every fourth frame of a loop: a fifteenth of a second apart, under the longest step a field takes at once. */
        const val FRAME_STRIDE = 4
        const val BROKEN_SHOWN = 10
    }
}
