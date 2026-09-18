package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neoworksuite.neocanvas.brushes.BrushCatalog
import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.brushes.BuiltInBrushes
import com.neoworksuite.neocanvas.brushes.BrushCategory
import com.neoworksuite.neocanvas.brushes.BrushMode
import com.neoworksuite.neocanvas.brushes.NeoBrushCodec

enum class BrushShelf { All, Favourites, Recent, Category }

class BrushLibraryState(
    private val catalog: BrushCatalog = BuiltInBrushes,
    initialSnapshot: ByteArray? = null,
    private val onPersist: (ByteArray) -> Unit = {},
) {
    private val restored = initialSnapshot?.let { runCatching { BrushLibrarySnapshotCodec.decode(it) }.getOrNull() }
    private val favouriteIds = mutableStateListOf<String>()
    private val recentIds = mutableStateListOf<String>()
    val customBrushes = mutableStateListOf<BrushDefinition>()

    init {
        customBrushes.addAll(restored?.brushes.orEmpty())
        favouriteIds.addAll(restored?.favourites.orEmpty().filter { find(it) != null })
        recentIds.addAll(restored?.recent.orEmpty().filter { find(it) != null }.take(12))
    }

    val categories: List<BrushCategory>
        get() = catalog.categories + if (customBrushes.isNotEmpty()) listOf(BrushCategory("custom", "Custom")) else emptyList()
    val allBrushes: List<BrushDefinition> get() = catalog.paintBrushes + customBrushes

    var query: String by mutableStateOf("")
    var shelf: BrushShelf by mutableStateOf(BrushShelf.Category)
        private set
    var selectedCategoryId: String? by mutableStateOf(catalog.categories.firstOrNull()?.id)
        private set

    val visibleBrushes: List<BrushDefinition>
        get() {
            if (query.isNotBlank()) return allBrushes.filter {
                it.name.contains(query.trim(), true) || categories.firstOrNull { category -> category.id == it.categoryId }?.name?.contains(query.trim(), true) == true
            }
            return when (shelf) {
                BrushShelf.All -> allBrushes
                BrushShelf.Favourites -> favouriteIds.mapNotNull(::find)
                BrushShelf.Recent -> recentIds.mapNotNull(::find)
                BrushShelf.Category -> allBrushes.filter { it.categoryId == selectedCategoryId }
            }
        }

    fun isFavourite(id: String): Boolean = id in favouriteIds

    fun toggleFavourite(id: String) {
        if (id in favouriteIds) favouriteIds.remove(id) else if (find(id) != null) favouriteIds += id
        persist()
    }

    fun choose(brush: BrushDefinition) {
        recentIds.remove(brush.id)
        recentIds.add(0, brush.id)
        while (recentIds.size > 12) recentIds.removeAt(recentIds.lastIndex)
        persist()
    }

    fun selectCategory(id: String) {
        if (categories.none { it.id == id }) return
        query = ""
        selectedCategoryId = id
        shelf = BrushShelf.Category
    }

    fun showAll() = show(BrushShelf.All)
    fun showFavourites() = show(BrushShelf.Favourites)
    fun showRecent() = show(BrushShelf.Recent)

    fun saveCustom(name: String, source: BrushDefinition): BrushDefinition {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 80) { "Use a brush name between 1 and 80 characters." }
        val brush = source.copy(
            id = uniqueId("user.${slug(clean)}"),
            name = clean,
            mode = BrushMode.PAINT,
            categoryId = "custom",
            version = source.version + 1,
        )
        customBrushes += brush
        selectedCategoryId = "custom"
        shelf = BrushShelf.Category
        persist()
        return brush
    }

    fun importBrush(bytes: ByteArray): BrushDefinition {
        val decoded = NeoBrushCodec.decode(bytes)
        val baseId = if (decoded.id.startsWith("user.")) decoded.id else "user.${slug(decoded.name)}"
        val imported = decoded.copy(id = uniqueId(baseId), mode = BrushMode.PAINT, categoryId = "custom")
        customBrushes += imported
        persist()
        return imported
    }

    fun exportBrush(id: String): ByteArray? = customBrushes.firstOrNull { it.id == id }?.let(NeoBrushCodec::encode)

    private fun find(id: String): BrushDefinition? = catalog.find(id) ?: customBrushes.firstOrNull { it.id == id }

    private fun uniqueId(base: String): String {
        if (find(base) == null) return base
        var suffix = 2
        while (find("$base-$suffix") != null) suffix++
        return "$base-$suffix"
    }

    private fun slug(name: String): String = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        .replace(Regex("-+"), "-").trim('-').ifEmpty { "brush" }

    private fun persist() = onPersist(BrushLibrarySnapshotCodec.encode(favouriteIds, recentIds, customBrushes))

    private fun show(next: BrushShelf) {
        query = ""
        selectedCategoryId = null
        shelf = next
    }
}

private data class BrushLibrarySnapshot(val favourites: List<String>, val recent: List<String>, val brushes: List<BrushDefinition>)

private object BrushLibrarySnapshotCodec {
    private const val HEADER = "NEOCANVAS_BRUSH_LIBRARY=1"
    private const val START = "---BRUSH---"
    private const val END = "---END---"

    fun encode(favourites: List<String>, recent: List<String>, brushes: List<BrushDefinition>): ByteArray = buildString {
        appendLine(HEADER)
        favourites.forEach { append("favourite=").appendLine(it) }
        recent.forEach { append("recent=").appendLine(it) }
        brushes.forEach { brush ->
            appendLine(START)
            append(NeoBrushCodec.encode(brush).decodeToString())
            appendLine(END)
        }
    }.encodeToByteArray()

    fun decode(bytes: ByteArray): BrushLibrarySnapshot {
        require(bytes.size <= 2_000_000) { "Brush library is too large." }
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).lines()
        require(lines.firstOrNull() == HEADER) { "Unsupported brush library." }
        val favourites = mutableListOf<String>()
        val recent = mutableListOf<String>()
        val brushes = mutableListOf<BrushDefinition>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index++]
            when {
                line.isEmpty() -> Unit
                line.startsWith("favourite=") -> favourites += line.removePrefix("favourite=")
                line.startsWith("recent=") -> recent += line.removePrefix("recent=")
                line == START -> {
                    val body = mutableListOf<String>()
                    while (index < lines.size && lines[index] != END) body += lines[index++]
                    require(index < lines.size && lines[index++] == END) { "Unterminated brush entry." }
                    brushes += NeoBrushCodec.decode((body.joinToString("\n") + "\n").encodeToByteArray())
                }
                else -> throw IllegalArgumentException("Unknown brush library field.")
            }
        }
        require(brushes.map { it.id }.distinct().size == brushes.size) { "Duplicate custom brush ID." }
        return BrushLibrarySnapshot(favourites.distinct(), recent.distinct(), brushes)
    }
}
