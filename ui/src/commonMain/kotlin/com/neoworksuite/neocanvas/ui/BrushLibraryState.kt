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
import com.neoworksuite.neocanvas.brushes.NeoBrushPack
import com.neoworksuite.neocanvas.brushes.NeoBrushPackCodec

enum class BrushShelf { All, Favourites, Recent, Category }

class BrushLibraryState(
    private val catalog: BrushCatalog = BuiltInBrushes,
    initialSnapshot: ByteArray? = null,
    private val onPersist: (ByteArray) -> Unit = {},
    private val onPersistDeferred: (((() -> ByteArray)) -> Unit)? = null,
) {
    private val restored = initialSnapshot?.let { runCatching { BrushLibrarySnapshotCodec.decode(it) }.getOrNull() }
    private val favouriteIds = mutableStateListOf<String>()
    private val recentIds = mutableStateListOf<String>()
    val customBrushes = mutableStateListOf<BrushDefinition>()
    val installedPacks = mutableStateListOf<InstalledBrushPack>()

    init {
        customBrushes.addAll(restored?.brushes.orEmpty())
        installedPacks.addAll(restored?.packs.orEmpty().map(::InstalledBrushPack))
        favouriteIds.addAll(restored?.favourites.orEmpty().filter { find(it) != null })
        recentIds.addAll(restored?.recent.orEmpty().filter { find(it) != null }.take(12))
    }

    val categories: List<BrushCategory>
        get() = catalog.categories + installedPacks.map { BrushCategory(it.pack.manifest.id, it.pack.manifest.name) } +
            if (customBrushes.isNotEmpty()) listOf(BrushCategory("custom", "Custom")) else emptyList()
    val allBrushes: List<BrushDefinition> get() = catalog.paintBrushes + customBrushes + installedPacks.flatMap { it.pack.brushes.values }

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

    fun installPack(pack: NeoBrushPack): PackInstallResult {
        val currentIndex = installedPacks.indexOfFirst { it.pack.manifest.id == pack.manifest.id }
        require(catalog.categories.none { it.id == pack.manifest.id }) { "This pack ID is reserved by NeoCanvas." }
        val otherIds = installedPacks.filterIndexed { index, _ -> index != currentIndex }.flatMap { it.pack.brushes.keys }.toSet()
        require(pack.brushes.keys.none { catalog.find(it) != null || it in otherIds || customBrushes.any { brush -> brush.id == it } }) {
            "One or more brush IDs are already in use."
        }
        if (currentIndex < 0) {
            installedPacks += InstalledBrushPack(pack)
            persist()
            return PackInstallResult.Installed
        }
        val current = installedPacks[currentIndex].pack
        require(current.manifest.author == pack.manifest.author) { "A different author already uses this pack ID." }
        val comparison = compareVersions(pack.manifest.version, current.manifest.version)
        if (comparison == 0) return PackInstallResult.Unchanged
        require(comparison > 0) { "An installed pack cannot be replaced by an older version." }
        installedPacks[currentIndex] = InstalledBrushPack(pack)
        favouriteIds.removeAll { id -> find(id) == null }
        recentIds.removeAll { id -> find(id) == null }
        persist()
        return PackInstallResult.Replaced
    }

    fun removePack(id: String): Boolean {
        val removed = installedPacks.firstOrNull { it.pack.manifest.id == id } ?: return false
        installedPacks.remove(removed)
        val ids = removed.pack.brushes.keys
        favouriteIds.removeAll { it in ids }
        recentIds.removeAll { it in ids }
        if (selectedCategoryId == id) selectCategory(catalog.categories.first().id)
        persist()
        return true
    }

    fun exportPack(id: String): ByteArray? = installedPacks.firstOrNull { it.pack.manifest.id == id }
        ?.pack?.let(NeoBrushPackCodec::encode)

    private fun find(id: String): BrushDefinition? = catalog.find(id) ?: customBrushes.firstOrNull { it.id == id }
        ?: installedPacks.firstNotNullOfOrNull { it.pack.brushes[id] }

    private fun uniqueId(base: String): String {
        if (find(base) == null) return base
        var suffix = 2
        while (find("$base-$suffix") != null) suffix++
        return "$base-$suffix"
    }

    private fun slug(name: String): String = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        .replace(Regex("-+"), "-").trim('-').ifEmpty { "brush" }

    private fun persist() {
        val favourites = favouriteIds.toList()
        val recent = recentIds.toList()
        val brushes = customBrushes.toList()
        val packs = installedPacks.map { it.pack }
        val encode = { BrushLibrarySnapshotCodec.encode(favourites, recent, brushes, packs) }
        onPersistDeferred?.invoke(encode) ?: onPersist(encode())
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toInt() }; val right = b.split('.').map { it.toInt() }
        repeat(3) { if (left[it] != right[it]) return left[it].compareTo(right[it]) }
        return 0
    }

    private fun show(next: BrushShelf) {
        query = ""
        selectedCategoryId = null
        shelf = next
    }
}

private data class BrushLibrarySnapshot(
    val favourites: List<String>, val recent: List<String>, val brushes: List<BrushDefinition>, val packs: List<NeoBrushPack>,
)

private object BrushLibrarySnapshotCodec {
    private const val HEADER_V1 = "NEOCANVAS_BRUSH_LIBRARY=1"
    private const val HEADER_V2 = "NEOCANVAS_BRUSH_LIBRARY=2"
    private const val START = "---BRUSH---"
    private const val END = "---END---"

    fun encode(favourites: List<String>, recent: List<String>, brushes: List<BrushDefinition>, packs: List<NeoBrushPack>): ByteArray = buildString {
        appendLine(HEADER_V2)
        favourites.forEach { append("favourite=").appendLine(it) }
        recent.forEach { append("recent=").appendLine(it) }
        brushes.forEach { brush ->
            appendLine(START)
            append(NeoBrushCodec.encode(brush).decodeToString())
            appendLine(END)
        }
        packs.forEach { append("pack=").appendLine(NeoBrushPackCodec.encode(it).toHex()) }
    }.encodeToByteArray()

    fun decode(bytes: ByteArray): BrushLibrarySnapshot {
        // A valid 25 MiB pack is hex-embedded in the V2 snapshot, so it can exceed 50 MiB.
        require(bytes.size <= 64 * 1024 * 1024) { "Brush library is too large." }
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).lines()
        require(lines.firstOrNull() == HEADER_V1 || lines.firstOrNull() == HEADER_V2) { "Unsupported brush library." }
        val favourites = mutableListOf<String>()
        val recent = mutableListOf<String>()
        val brushes = mutableListOf<BrushDefinition>()
        val packs = mutableListOf<NeoBrushPack>()
        var index = 1
        while (index < lines.size) {
            val line = lines[index++]
            when {
                line.isEmpty() -> Unit
                line.startsWith("favourite=") -> favourites += line.removePrefix("favourite=")
                line.startsWith("recent=") -> recent += line.removePrefix("recent=")
                line.startsWith("pack=") -> packs += NeoBrushPackCodec.decode(line.removePrefix("pack=").hexBytes(), "1.0.0")
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
        return BrushLibrarySnapshot(favourites.distinct(), recent.distinct(), brushes, packs)
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0 && all { it in "0123456789abcdef" })
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
