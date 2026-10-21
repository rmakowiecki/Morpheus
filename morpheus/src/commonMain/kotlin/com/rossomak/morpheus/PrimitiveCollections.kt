package com.rossomak.morpheus

private const val EMPTY: Byte = 0
private const val FULL: Byte = 1
private const val REMOVED: Byte = 2
private const val INITIAL_CAPACITY = 16

/** Rehashes once full and removed slots pass this share of the table, so every probe ends on an empty slot. */
private const val MAX_LOAD = 0.5f

// SplitMix64's finalizer: its shifts and multipliers (0xbf58476d1ce4e5b9, 0x94d049bb133111eb as signed longs).
private const val MIX_SHIFT_FIRST = 30
private const val MIX_SHIFT_SECOND = 27
private const val MIX_SHIFT_LAST = 31
private const val MIX_MULTIPLIER_FIRST = -0x40a7b892e31b1a47L
private const val MIX_MULTIPLIER_SECOND = -0x6b2fb644ecceee15L

/** A slot's home: the key's bits mixed, so neighbouring edge keys spread out. */
private fun slotOf(key: Long, mask: Int): Int {
    var mixed = key
    mixed = (mixed xor (mixed ushr MIX_SHIFT_FIRST)) * MIX_MULTIPLIER_FIRST
    mixed = (mixed xor (mixed ushr MIX_SHIFT_SECOND)) * MIX_MULTIPLIER_SECOND
    return (mixed xor (mixed ushr MIX_SHIFT_LAST)).toInt() and mask
}

/**
 * A set of [Long]s without boxing: open addressing with linear probing. A removed key leaves a
 * tombstone, which probes pass over and an add may reuse; a rehash drops them. [clear] keeps the table,
 * so a set cleared and refilled to the same size allocates nothing.
 */
internal class LongHashSet(initialCapacity: Int = INITIAL_CAPACITY) {
    private var keys = LongArray(tableSizeFor(initialCapacity))
    private var states = ByteArray(keys.size)
    private var removed = 0

    var size: Int = 0
        private set

    operator fun contains(key: Long): Boolean = find(key) >= 0

    /** Adds [key]; false if it was already there. */
    fun add(key: Long): Boolean {
        if (find(key) >= 0) return false
        if ((size + removed + 1) > keys.size * MAX_LOAD) rehash()
        val slot = freeSlot(key)
        if (states[slot] == REMOVED) removed--
        keys[slot] = key
        states[slot] = FULL
        size++
        return true
    }

    /** Removes [key]; false if it wasn't there. */
    fun remove(key: Long): Boolean {
        val slot = find(key)
        if (slot < 0) return false
        states[slot] = REMOVED
        size--
        removed++
        return true
    }

    fun clear() {
        if (size == 0 && removed == 0) return
        states.fill(EMPTY)
        size = 0
        removed = 0
    }

    private fun find(key: Long): Int {
        val mask = keys.size - 1
        var slot = slotOf(key, mask)
        while (true) {
            when (states[slot]) {
                EMPTY -> return -1
                FULL -> if (keys[slot] == key) return slot
            }
            slot = (slot + 1) and mask
        }
    }

    /** The first empty or removed slot on [key]'s probe path; [key] isn't in the set. */
    private fun freeSlot(key: Long): Int {
        val mask = keys.size - 1
        var slot = slotOf(key, mask)
        while (states[slot] == FULL) slot = (slot + 1) and mask
        return slot
    }

    private fun rehash() {
        val oldKeys = keys
        val oldStates = states
        // Grows only when the live keys need it; a table clogged with tombstones is rebuilt at its size.
        val capacity = if ((size + 1) > oldKeys.size * MAX_LOAD / 2) oldKeys.size * 2 else oldKeys.size
        keys = LongArray(capacity)
        states = ByteArray(capacity)
        removed = 0
        for (slot in oldKeys.indices) {
            if (oldStates[slot] != FULL) continue
            val free = freeSlot(oldKeys[slot])
            keys[free] = oldKeys[slot]
            states[free] = FULL
        }
    }
}

/** A map from [Long] to [Int] without boxing, built like [LongHashSet]. */
internal class LongIntHashMap(initialCapacity: Int = INITIAL_CAPACITY) {
    private var keys = LongArray(tableSizeFor(initialCapacity))
    private var values = IntArray(keys.size)
    private var states = ByteArray(keys.size)
    private var removed = 0

    var size: Int = 0
        private set

    operator fun contains(key: Long): Boolean = find(key) >= 0

    /** The value for [key], or [missing] if there is none. */
    fun get(key: Long, missing: Int): Int {
        val slot = find(key)
        return if (slot < 0) missing else values[slot]
    }

    operator fun set(key: Long, value: Int) {
        val existing = find(key)
        if (existing >= 0) {
            values[existing] = value
            return
        }
        if ((size + removed + 1) > keys.size * MAX_LOAD) rehash()
        val slot = freeSlot(key)
        if (states[slot] == REMOVED) removed--
        keys[slot] = key
        values[slot] = value
        states[slot] = FULL
        size++
    }

    /** Removes [key]; false if it wasn't there. */
    fun remove(key: Long): Boolean {
        val slot = find(key)
        if (slot < 0) return false
        states[slot] = REMOVED
        size--
        removed++
        return true
    }

    fun clear() {
        if (size == 0 && removed == 0) return
        states.fill(EMPTY)
        size = 0
        removed = 0
    }

    private fun find(key: Long): Int {
        val mask = keys.size - 1
        var slot = slotOf(key, mask)
        while (true) {
            when (states[slot]) {
                EMPTY -> return -1
                FULL -> if (keys[slot] == key) return slot
            }
            slot = (slot + 1) and mask
        }
    }

    private fun freeSlot(key: Long): Int {
        val mask = keys.size - 1
        var slot = slotOf(key, mask)
        while (states[slot] == FULL) slot = (slot + 1) and mask
        return slot
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = values
        val oldStates = states
        val capacity = if ((size + 1) > oldKeys.size * MAX_LOAD / 2) oldKeys.size * 2 else oldKeys.size
        keys = LongArray(capacity)
        values = IntArray(capacity)
        states = ByteArray(capacity)
        removed = 0
        for (slot in oldKeys.indices) {
            if (oldStates[slot] != FULL) continue
            val free = freeSlot(oldKeys[slot])
            keys[free] = oldKeys[slot]
            values[free] = oldValues[slot]
            states[free] = FULL
        }
    }
}

/** The smallest power of two of at least [capacity] and [INITIAL_CAPACITY]. */
private fun tableSizeFor(capacity: Int): Int {
    var size = INITIAL_CAPACITY
    while (size < capacity) size *= 2
    return size
}

private const val INITIAL_STRIDE = 16

/**
 * Each node's neighbours in one [IntArray], a fixed [stride] of slots per node. When a node would
 * outgrow it, the whole array is rebuilt at double the stride, and keeps that size from then on.
 * [reset] keeps the arrays, so refilling it allocates only to grow.
 */
internal class IntAdjacency {
    private var stride = INITIAL_STRIDE
    private var neighbours = IntArray(0)
    private var degrees = IntArray(0)

    /** Nodes in use since the last [reset]. */
    var nodeCount: Int = 0
        private set

    /** Empties the first [nodeCount] nodes and makes room for that many. */
    fun reset(nodeCount: Int) {
        this.nodeCount = nodeCount
        if (degrees.size < nodeCount) {
            degrees = IntArray(nodeCount)
            neighbours = IntArray(nodeCount * stride)
        } else {
            degrees.fill(0, 0, nodeCount)
        }
    }

    fun degree(node: Int): Int = degrees[node]

    /** [node]'s neighbour at [position], below [degree]. */
    fun neighbour(node: Int, position: Int): Int = neighbours[node * stride + position]

    fun contains(node: Int, other: Int): Boolean {
        val start = node * stride
        for (slot in start until start + degrees[node]) if (neighbours[slot] == other) return true
        return false
    }

    /** Adds [other] after [node]'s last neighbour. */
    fun append(node: Int, other: Int) {
        if (degrees[node] == stride) grow()
        neighbours[node * stride + degrees[node]++] = other
    }

    /** Adds [other] to [node]'s neighbours, kept ascending. */
    fun insertSorted(node: Int, other: Int) {
        if (degrees[node] == stride) grow()
        val start = node * stride
        var slot = start + degrees[node]++
        while (slot > start && neighbours[slot - 1] > other) {
            neighbours[slot] = neighbours[slot - 1]
            slot--
        }
        neighbours[slot] = other
    }

    /** Removes [other] from [node]'s neighbours, keeping the rest in order; false if it wasn't one. */
    fun remove(node: Int, other: Int): Boolean {
        val start = node * stride
        val end = start + degrees[node]
        for (slot in start until end) {
            if (neighbours[slot] != other) continue
            neighbours.copyInto(neighbours, destinationOffset = slot, startIndex = slot + 1, endIndex = end)
            degrees[node]--
            return true
        }
        return false
    }

    /** True if two nodes with ascending neighbours share one, by a merge walk. */
    fun sharesSortedNeighbour(first: Int, second: Int): Boolean {
        var firstSlot = first * stride
        val firstEnd = firstSlot + degrees[first]
        var secondSlot = second * stride
        val secondEnd = secondSlot + degrees[second]
        while (firstSlot < firstEnd && secondSlot < secondEnd) {
            val firstNeighbour = neighbours[firstSlot]
            val secondNeighbour = neighbours[secondSlot]
            when {
                firstNeighbour == secondNeighbour -> return true
                firstNeighbour < secondNeighbour -> firstSlot++
                else -> secondSlot++
            }
        }
        return false
    }

    private fun grow() {
        val wider = stride * 2
        val rebuilt = IntArray(degrees.size * wider)
        for (node in 0 until nodeCount) neighbours.copyInto(rebuilt, destinationOffset = node * wider, startIndex = node * stride, endIndex = node * stride + degrees[node])
        neighbours = rebuilt
        stride = wider
    }
}
