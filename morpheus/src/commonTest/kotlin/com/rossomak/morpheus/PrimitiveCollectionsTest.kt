package com.rossomak.morpheus

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test

/**
 * The mesh code's primitive collections against the standard ones, over random sequences of inserts,
 * removals, lookups and iteration. The hash tables grow from the smallest table through several resizes,
 * empty most of it again (leaving tombstones), and are cleared and refilled; the adjacency outgrows its stride.
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
            withClue("after phase $phase") {
                keys.forEach { key -> (key in set) shouldBe (key in reference) }
                set.visited() shouldBe reference.sorted()
            }
        }
        set.clear()
        reference.clear()
        set.size shouldBe 0
        set.visited() shouldBe emptyList()
        keys.forEach { key -> (key in set) shouldBe false }
        keys.take(OPERATIONS_PER_PHASE).forEach { key -> set.add(key) shouldBe reference.add(key) }
        set.size shouldBe reference.size
        keys.forEach { key -> (key in set) shouldBe (key in reference) }
        set.visited() shouldBe reference.sorted()
    }

    /** Every key [LongHashSet.forEach] visits, sorted, so a key visited twice or skipped shows. */
    private fun LongHashSet.visited(): List<Long> {
        val visited = ArrayList<Long>()
        forEach { key -> visited += key }
        return visited.sorted()
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

    @Test
    fun `the tables agree with the standard ones when tombstones, not live keys, fill them`() {
        val set = LongHashSet()
        val map = LongIntHashMap()
        val reference = HashSet<Long>()
        val liveKeys = ArrayDeque<Long>()
        val random = Random(SEED)
        // Never more than a few live keys, so a full table is mostly tombstones and rehashes at its size.
        repeat(CHURN_OPERATIONS) { step ->
            val key = random.nextLong()
            withClue("step $step, key $key") {
                set.add(key) shouldBe reference.add(key)
                map[key] = step
                liveKeys.addLast(key)
                if (liveKeys.size > CHURN_LIVE_KEYS) {
                    val oldest = liveKeys.removeFirst()
                    set.remove(oldest) shouldBe reference.remove(oldest)
                    map.remove(oldest) shouldBe true
                }
                set.size shouldBe reference.size
                map.size shouldBe reference.size
                (key in set) shouldBe true
                map.get(key, missing = MISSING) shouldBe step
            }
        }
        reference.forEach { key -> (key in set) shouldBe true }
        set.visited() shouldBe reference.sorted()
    }

    @Test
    fun `the adjacency keeps sorted neighbour lists through inserts, removals and stride doublings`() {
        val adjacency = IntAdjacency()
        val reference = List(ADJACENCY_NODES) { HashSet<Int>() }
        val random = Random(SEED)
        repeat(2) { round ->
            adjacency.reset(ADJACENCY_NODES)
            reference.forEach { it.clear() }
            repeat(ADJACENCY_OPERATIONS) { step ->
                val node = random.nextInt(ADJACENCY_NODES)
                val other = random.nextInt(ADJACENCY_NEIGHBOURS)
                withClue("round $round, step $step, node $node, other $other") {
                    if (random.nextFloat() < ADJACENCY_ADD_SHARE) {
                        if (reference[node].add(other)) adjacency.insertSorted(node, other)
                    } else {
                        adjacency.remove(node, other) shouldBe reference[node].remove(other)
                    }
                    adjacency.degree(node) shouldBe reference[node].size
                    adjacency.contains(node, other) shouldBe (other in reference[node])
                }
            }
            for (node in 0 until ADJACENCY_NODES) {
                withClue("round $round, node $node") {
                    List(adjacency.degree(node)) { adjacency.neighbour(node, it) } shouldBe reference[node].sorted()
                    for (second in 0 until ADJACENCY_NODES) {
                        adjacency.sharesSortedNeighbour(node, second) shouldBe reference[node].any { it in reference[second] }
                    }
                }
            }
        }
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
        const val CHURN_OPERATIONS = 2_000
        const val CHURN_LIVE_KEYS = 3
        const val ADJACENCY_NODES = 8
        const val ADJACENCY_NEIGHBOURS = 80
        const val ADJACENCY_OPERATIONS = 1_500
        const val ADJACENCY_ADD_SHARE = 0.6f

        /** Each phase's share of adds: fill up through the resizes, drain, then churn. */
        val PHASES = listOf(0.9f, 0.1f, 0.5f)
    }
}
