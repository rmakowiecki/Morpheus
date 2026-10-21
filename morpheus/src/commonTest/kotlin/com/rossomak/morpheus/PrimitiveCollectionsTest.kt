package com.rossomak.morpheus

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

/**
 * The mesh code's primitive collections against the standard ones, over random sequences of inserts,
 * removals and lookups. Each run grows from the smallest table through several resizes, empties most of
 * it again (leaving tombstones), and is cleared and refilled.
 */
class PrimitiveCollectionsTest {

    @Test
    fun `the long set agrees with a hash set through growth, removals and a clear`() {
        val set = LongHashSet()
        val reference = HashSet<Long>()
        val keys = keyPool()
        val random = Random(SEED)
        for ((phase, addShare) in PHASES.withIndex()) {
            repeat(OPERATIONS_PER_PHASE) { step ->
                val key = keys[random.nextInt(keys.size)]
                withClue("phase $phase, step $step, key $key") {
                    when {
                        random.nextFloat() < addShare -> set.add(key) shouldBe reference.add(key)
                        random.nextBoolean() -> set.remove(key) shouldBe reference.remove(key)
                        else -> (key in set) shouldBe (key in reference)
                    }
                    set.size shouldBe reference.size
                }
            }
            withClue("after phase $phase") { keys.forEach { key -> (key in set) shouldBe (key in reference) } }
        }
        set.clear()
        reference.clear()
        set.size shouldBe 0
        keys.forEach { key -> (key in set) shouldBe false }
        keys.take(OPERATIONS_PER_PHASE).forEach { key -> set.add(key) shouldBe reference.add(key) }
        set.size shouldBe reference.size
        keys.forEach { key -> (key in set) shouldBe (key in reference) }
    }

    @Test
    fun `the long-to-int map agrees with a hash map through growth, removals and a clear`() {
        val map = LongIntHashMap()
        val reference = HashMap<Long, Int>()
        val keys = keyPool()
        val random = Random(SEED)
        for ((phase, addShare) in PHASES.withIndex()) {
            repeat(OPERATIONS_PER_PHASE) { step ->
                val key = keys[random.nextInt(keys.size)]
                withClue("phase $phase, step $step, key $key") {
                    when {
                        random.nextFloat() < addShare -> {
                            val value = random.nextInt()
                            map[key] = value
                            reference[key] = value
                        }
                        random.nextBoolean() -> map.remove(key) shouldBe (reference.remove(key) != null)
                        else -> map.get(key, missing = MISSING) shouldBe (reference[key] ?: MISSING)
                    }
                    map.size shouldBe reference.size
                }
            }
            withClue("after phase $phase") { keys.forEach { key -> map.get(key, missing = MISSING) shouldBe (reference[key] ?: MISSING) } }
        }
        map.clear()
        map.size shouldBe 0
        keys.forEach { key -> (key in map) shouldBe false }
        keys.take(OPERATIONS_PER_PHASE).forEachIndexed { index, key -> map[key] = index }
        map.size shouldBe OPERATIONS_PER_PHASE
        keys.forEachIndexed { index, key -> map.get(key, missing = MISSING) shouldBe (if (index < OPERATIONS_PER_PHASE) index else MISSING) }
    }

    /** Edge keys of a dense grid, which share most of their bits, plus a few extremes. */
    private fun keyPool(): List<Long> {
        val edges = (0 until GRID_NODES).flatMap { node -> listOf(edgeKey(node, node + 1), edgeKey(node, node + GRID_WIDTH)) }
        return (edges + listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)).shuffled(Random(SEED))
    }

    private companion object {
        const val SEED = 23
        const val GRID_WIDTH = 40
        const val GRID_NODES = 1_500
        const val OPERATIONS_PER_PHASE = 3_000
        const val MISSING = Int.MIN_VALUE

        /** Each phase's share of adds: fill up through the resizes, drain, then churn. */
        val PHASES = listOf(0.9f, 0.1f, 0.5f)
    }
}
