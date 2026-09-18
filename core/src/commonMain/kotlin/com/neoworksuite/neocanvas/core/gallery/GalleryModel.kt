package com.neoworksuite.neocanvas.core.gallery

data class GalleryArtwork(
    val id: String,
    val name: String,
    val order: Int,
    val stackId: String? = null,
    val modifiedAtEpochMillis: Long = 0,
)

data class GalleryStack(val id: String, val name: String, val order: Int)

data class GallerySnapshot(
    val artworks: List<GalleryArtwork> = emptyList(),
    val stacks: List<GalleryStack> = emptyList(),
) {
    init {
        require(artworks.map { it.id }.distinct().size == artworks.size) { "Artwork ids must be unique." }
        require(stacks.map { it.id }.distinct().size == stacks.size) { "Stack ids must be unique." }
    }

    val rootArtworks: List<GalleryArtwork>
        get() = artworks.filter { it.stackId == null }.sortedBy { it.order }

    fun artworksIn(stackId: String): List<GalleryArtwork> =
        artworks.filter { it.stackId == stackId }.sortedBy { it.order }

    fun normalized(): GallerySnapshot {
        val normalizedArtworks = artworks.groupBy { it.stackId }.values.flatMap { group ->
            group.sortedWith(compareBy<GalleryArtwork> { it.order }.thenBy { it.name.lowercase() })
                .mapIndexed { index, artwork -> artwork.copy(order = index) }
        }
        return copy(
            artworks = normalizedArtworks,
            stacks = stacks.sortedWith(compareBy<GalleryStack> { it.order }.thenBy { it.name.lowercase() })
                .mapIndexed { index, stack -> stack.copy(order = index) },
        )
    }

    fun reorderArtwork(id: String, destination: Int): GallerySnapshot {
        val artwork = artworks.first { it.id == id }
        val scope = artworks.filter { it.stackId == artwork.stackId }.sortedBy { it.order }.toMutableList()
        scope.removeAll { it.id == id }
        scope.add(destination.coerceIn(0, scope.size), artwork)
        val reordered = scope.mapIndexed { index, item -> item.copy(order = index) }.associateBy { it.id }
        return copy(artworks = artworks.map { reordered[it.id] ?: it }).normalized()
    }

    fun createStack(name: String, ids: List<String>, stackId: String): GallerySnapshot {
        require(ids.distinct().size >= 2) { "Choose at least two artworks for a stack." }
        require(stackId.isNotBlank() && stacks.none { it.id == stackId }) { "Stack id must be unique." }
        val selected = artworks.filter { it.id in ids }
        require(selected.size == ids.distinct().size) { "Every stacked artwork must exist." }
        val cleanName = uniqueName(name, stacks.map { it.name })
        val nextStack = GalleryStack(stackId, cleanName, stacks.size)
        val positions = ids.withIndex().associate { it.value to it.index }
        return copy(
            stacks = stacks + nextStack,
            artworks = artworks.map { item -> positions[item.id]?.let { item.copy(stackId = stackId, order = it) } ?: item },
        ).normalized()
    }

    fun renameArtwork(id: String, name: String): GallerySnapshot {
        val clean = uniqueName(name, artworks.filterNot { it.id == id }.map { it.name })
        require(artworks.any { it.id == id }) { "Artwork does not exist." }
        return copy(artworks = artworks.map { if (it.id == id) it.copy(name = clean) else it })
    }

    private fun uniqueName(candidate: String, others: List<String>): String {
        val clean = candidate.trim()
        require(clean.isNotEmpty() && clean.length <= 80) { "Use a name from 1 to 80 characters." }
        require(others.none { it.equals(clean, ignoreCase = true) }) { "That name already exists." }
        return clean
    }
}
