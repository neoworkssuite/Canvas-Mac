package com.neoworksuite.neocanvas.core.model

/**
 * Snapshots collection contents into read-only Kotlin collection implementations.
 * They deliberately do not implement MutableList or MutableSet on supported targets.
 */
internal fun <T> immutableListSnapshot(source: List<T>): List<T> = ImmutableListSnapshot(source.toList())

internal fun <T> immutableSetSnapshot(source: Set<T>): Set<T> = ImmutableSetSnapshot(source.toList())

private class ImmutableListSnapshot<T>(private val elements: List<T>) : AbstractList<T>() {
    override val size: Int
        get() = elements.size

    override fun get(index: Int): T = elements[index]
}

private class ImmutableSetSnapshot<T>(private val elements: List<T>) : AbstractSet<T>() {
    override val size: Int
        get() = elements.size

    override fun contains(element: T): Boolean = element in elements

    override fun iterator(): Iterator<T> = SnapshotIterator(elements)
}

private class SnapshotIterator<T>(private val elements: List<T>) : Iterator<T> {
    private var index = 0

    override fun hasNext(): Boolean = index < elements.size

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return elements[index++]
    }
}
