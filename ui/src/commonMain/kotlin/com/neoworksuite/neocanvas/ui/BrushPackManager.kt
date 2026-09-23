package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neoworksuite.neocanvas.brushes.NeoBrushPack
import com.neoworksuite.neocanvas.brushes.NeoBrushPackCodec

data class BrushPackPreview(
    val pack: NeoBrushPack,
    val provenanceLabel: String,
)

class BrushPackManager(private val library: BrushLibraryState) {
    var pending: BrushPackPreview? by mutableStateOf(null)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    val primaryActionLabel: String
        get() = if (pending?.pack?.manifest?.id in library.installedPacks.map { it.pack.manifest.id }) "Replace" else "Install"

    fun preview(bytes: ByteArray) {
        errorMessage = null
        pending = runCatching { NeoBrushPackCodec.decode(bytes, "1.0.0") }
            .map { pack ->
                // Checksums prove integrity, not authorship. Signature verification will enable an official badge later.
                BrushPackPreview(pack, "Imported Pack")
            }
            .getOrElse {
                errorMessage = "This brush pack could not be opened. Check the file and try again."
                null
            }
    }

    fun install(): PackInstallResult? {
        val pack = pending?.pack ?: return null
        return runCatching { library.installPack(pack) }
            .onSuccess { pending = null; errorMessage = null }
            .getOrElse { error ->
                errorMessage = error.message ?: "This brush pack could not be installed."
                null
            }
    }

    fun cancel() { pending = null; errorMessage = null }

    fun remove(packId: String, activeBrushId: String): String? {
        val containsActive = library.installedPacks.firstOrNull { it.pack.manifest.id == packId }
            ?.pack?.brushes?.containsKey(activeBrushId) == true
        if (!library.removePack(packId)) return null
        return if (containsActive) "neo.pencil" else null
    }
}

data class PendingBrushImport(val name: String, val bytes: ByteArray)

class PendingExternalImportQueue(private val maxBytes: Int = 25 * 1024 * 1024) {
    private var receiver: ((PendingBrushImport) -> Unit)? = null
    var pending: PendingBrushImport? = null
        private set

    fun attach(onImport: (PendingBrushImport) -> Unit) {
        receiver = onImport
        pending?.also { pending = null; onImport(it) }
    }

    fun offer(name: String, bytes: ByteArray): Boolean {
        if (!(name.endsWith(".neobrush", true) || name.endsWith(".neobrushpack", true)) || bytes.size > maxBytes) return false
        val item = PendingBrushImport(name.substringAfterLast('/').substringAfterLast('\\'), bytes.copyOf())
        val target = receiver
        if (target == null) pending = item else target(item)
        return true
    }

    fun cancel() { pending = null }
}
