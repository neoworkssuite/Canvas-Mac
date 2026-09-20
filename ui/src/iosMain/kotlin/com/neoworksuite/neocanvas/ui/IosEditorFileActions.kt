@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.NeoCanvasPackage
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.renderer.GalleryThumbnail
import com.neoworksuite.neocanvas.renderer.PngExporter
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import platform.Foundation.NSURL
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Native iPad local storage and image-picker bridge.
 *
 * Artwork lives under the app's Documents/NeoCanvas folder so it survives relaunches,
 * appears in the Gallery, and can be exposed through the iPad Files app.
 */
internal class IosEditorFileActions(
    private val presenter: () -> UIViewController?,
) : EditorFileActions {
    private val fm: NSFileManager get() = NSFileManager.defaultManager
    private var activeImagePickerDelegate: ImagePickerDelegate? = null
    private var currentDocumentName: String? = null

    private val documentsRoot: String
        get() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .filterIsInstance<String>().first()

    private val libraryDirectory: String get() = join(documentsRoot, "NeoCanvas")
    private val recoveryDirectory: String get() = join(libraryDirectory, "Recovery")
    private val exportDirectory: String get() = join(libraryDirectory, "Exports")
    private val palettePath: String get() = join(libraryDirectory, "palette.txt")
    private val preferencesPath: String get() = join(libraryDirectory, "preferences.txt")
    private val recoveryPath: String get() = join(recoveryDirectory, "last-session.neocanvas")

    override val supportsLocalLibrary: Boolean = true
    override val supportsSaveAs: Boolean = true
    override val supportsRecovery: Boolean = true

    init {
        ensureDirectory(libraryDirectory)
        ensureDirectory(recoveryDirectory)
        ensureDirectory(exportDirectory)
    }

    override fun resetDocumentTarget() {
        currentDocumentName = null
    }

    override fun listLocalDocuments(): List<String> {
        ensureDirectory(libraryDirectory)
        return fm.contentsOfDirectoryAtPath(libraryDirectory, null)
            ?.filterIsInstance<String>()
            .orEmpty()
            .filter { it.endsWith(".neocanvas", ignoreCase = true) && !it.endsWith(".recovery.neocanvas", ignoreCase = true) }
            .sortedBy { it.lowercase() }
    }

    override fun openLocalDocument(name: String): LoadResult {
        if (!isSafeLocalName(name) || name !in listLocalDocuments()) {
            return LoadResult.Failure("Artwork was not found in the local NeoCanvas library.")
        }
        val result = readPackage(join(libraryDirectory, name))
        if (result is LoadResult.Success) currentDocumentName = name
        return result
    }

    override fun saveNamedCopy(
        name: String,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult {
        val clean = validArtworkName(name)
            ?: return SaveResult.Failure("Use 1–80 letters, numbers, spaces, hyphens or parentheses.")
        val filename = "$clean.neocanvas"
        if (listLocalDocuments().any { it.equals(filename, ignoreCase = true) }) {
            return SaveResult.Failure("That artwork name already exists.")
        }
        return saveTo(filename, document, tiles)
    }

    override fun renameLocalDocument(name: String, newName: String): SaveResult {
        if (!isSafeLocalName(name)) return SaveResult.Failure("Invalid artwork name.")
        val clean = validArtworkName(newName)
            ?: return SaveResult.Failure("Use a valid artwork name up to 80 characters.")
        val source = join(libraryDirectory, name)
        val targetName = "$clean.neocanvas"
        val target = join(libraryDirectory, targetName)
        if (!fm.fileExistsAtPath(source)) return SaveResult.Failure("Artwork was not found.")
        if (fm.fileExistsAtPath(target) && !name.equals(targetName, ignoreCase = true)) {
            return SaveResult.Failure("That artwork name already exists.")
        }
        if (source == target) return SaveResult.Success
        return if (fm.moveItemAtPath(source, target, null)) {
            if (currentDocumentName == name) currentDocumentName = targetName
            SaveResult.Success
        } else SaveResult.Failure("Could not rename artwork.")
    }

    override fun duplicateLocalDocument(name: String): SaveResult {
        if (!isSafeLocalName(name)) return SaveResult.Failure("Invalid artwork name.")
        val source = join(libraryDirectory, name)
        val bytes = NSData.dataWithContentsOfFile(source)?.toByteArray()
            ?: return SaveResult.Failure("Could not read artwork.")
        val base = name.removeSuffix(".neocanvas")
        var ordinal = 1
        var targetName = "$base copy.neocanvas"
        while (fm.fileExistsAtPath(join(libraryDirectory, targetName))) {
            ordinal++
            targetName = "$base copy $ordinal.neocanvas"
        }
        return if (writeBytes(join(libraryDirectory, targetName), bytes)) SaveResult.Success
            else SaveResult.Failure("Could not duplicate artwork.")
    }

    override fun deleteLocalDocument(name: String): SaveResult {
        if (!isSafeLocalName(name)) return SaveResult.Failure("Invalid artwork name.")
        val path = join(libraryDirectory, name)
        if (!fm.fileExistsAtPath(path)) return SaveResult.Failure("Artwork was not found.")
        return if (fm.removeItemAtPath(path, null)) {
            if (currentDocumentName == name) currentDocumentName = null
            SaveResult.Success
        } else SaveResult.Failure("Could not delete artwork.")
    }

    override fun localDocumentThumbnail(name: String): ByteArray? {
        if (!isSafeLocalName(name)) return null
        val bytes = NSData.dataWithContentsOfFile(join(libraryDirectory, name))?.toByteArray() ?: return null
        return runCatching { NeoCanvasPackage.readThumbnail(bytes) }.getOrNull()
    }

    override fun save(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult {
        val target = currentDocumentName ?: nextUntitledName()
        return saveTo(target, document, tiles)
    }

    override fun saveAs(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult = SaveResult.Failure("Use Save As in NeoCanvas to name a local copy.")

    override fun open(): LoadResult {
        val mostRecent = listLocalDocuments().lastOrNull()
            ?: return LoadResult.Failure("No local NeoCanvas artwork has been saved yet.")
        return openLocalDocument(mostRecent)
    }

    override fun saveRecovery(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult {
        val bytes = runCatching { NeoCanvasPackage.write(document, tiles) }
            .getOrElse { return SaveResult.Failure("Could not prepare recovery copy: ${it.message}") }
        ensureDirectory(recoveryDirectory)
        return if (writeBytes(recoveryPath, bytes)) SaveResult.Success
            else SaveResult.Failure("Could not write iPad recovery copy.")
    }

    override fun loadRecovery(): LoadResult? =
        if (fm.fileExistsAtPath(recoveryPath)) readPackage(recoveryPath) else null

    override fun loadPalette(): List<String> {
        val data = NSData.dataWithContentsOfFile(palettePath)?.toByteArray() ?: return emptyList()
        return runCatching { data.decodeToString().lineSequence().map(String::trim).filter(String::isNotBlank).toList() }
            .getOrDefault(emptyList())
    }

    override fun savePalette(colors: List<String>): SaveResult {
        ensureDirectory(libraryDirectory)
        return if (writeBytes(palettePath, colors.joinToString("\n").encodeToByteArray())) SaveResult.Success
            else SaveResult.Failure("Could not save palette locally on this iPad.")
    }

    override fun loadPreferences(): Map<String, String> {
        val data = NSData.dataWithContentsOfFile(preferencesPath)?.toByteArray() ?: return emptyMap()
        return runCatching {
            data.decodeToString().lineSequence().mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    override fun savePreferences(values: Map<String, String>): SaveResult {
        ensureDirectory(libraryDirectory)
        val text = values.entries.sortedBy { it.key }.joinToString("\n") { entry ->
            "${entry.key}=${entry.value}"
        }
        return if (writeBytes(preferencesPath, text.encodeToByteArray())) SaveResult.Success
            else SaveResult.Failure("Could not save NeoCanvas preferences on this iPad.")
    }

    override fun exportPng(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult {
        ensureDirectory(exportDirectory)
        val base = currentDocumentName?.removeSuffix(".neocanvas") ?: "NeoCanvas"
        val target = join(exportDirectory, "$base.png")
        return PngExporter.export(document, tiles) { bytes ->
            check(writeBytes(target, bytes)) { "Could not write PNG to iPad Documents." }
        }
    }

    override fun openExternalUrl(url: String): Boolean {
        val target = NSURL.URLWithString(url) ?: return false
        if (!UIApplication.sharedApplication.canOpenURL(target)) return false
        UIApplication.sharedApplication.openURL(
            url = target,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
        return true
    }

    override fun importImage(onResult: (Result<ImportedImage?>) -> Unit) {
        val host = presenter()
        if (host == null) {
            onResult(Result.failure(IllegalStateException("The iPad image picker is not ready yet.")))
            return
        }
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            )) {
            onResult(Result.failure(IllegalStateException("The iPad photo library is unavailable.")))
            return
        }

        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            allowsEditing = false
            modalPresentationStyle = UIModalPresentationFullScreen
        }
        val delegate = ImagePickerDelegate(
            onResult = onResult,
            onFinished = { activeImagePickerDelegate = null },
        )
        activeImagePickerDelegate = delegate
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    private fun saveTo(
        filename: String,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult {
        ensureDirectory(libraryDirectory)
        val thumbnail = runCatching { GalleryThumbnail.render(document, tiles).encode() }
            .getOrElse { NeoCanvasPackage.transparentThumbnail() }
        val bytes = runCatching { NeoCanvasPackage.write(document, tiles, thumbnail) }
            .getOrElse { return SaveResult.Failure("Could not prepare NeoCanvas document: ${it.message}") }
        val path = join(libraryDirectory, filename)
        if (!writeBytes(path, bytes)) return SaveResult.Failure("Could not save artwork in iPad Documents.")
        currentDocumentName = filename
        return SaveResult.Success
    }

    private fun readPackage(path: String): LoadResult {
        val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray()
            ?: return LoadResult.Failure("Could not read NeoCanvas artwork.")
        return NeoCanvasPackage.read(bytes)
    }

    private fun nextUntitledName(): String {
        if (!fm.fileExistsAtPath(join(libraryDirectory, "Untitled.neocanvas"))) return "Untitled.neocanvas"
        var ordinal = 2
        while (fm.fileExistsAtPath(join(libraryDirectory, "Untitled $ordinal.neocanvas"))) ordinal++
        return "Untitled $ordinal.neocanvas"
    }

    private fun validArtworkName(value: String): String? = value.trim().takeIf {
        it.matches(Regex("[\\p{L}\\p{N} _()-]{1,80}"))
    }

    private fun isSafeLocalName(value: String): Boolean =
        value.isNotBlank() && '/' !in value && '\\' !in value && value.endsWith(".neocanvas", ignoreCase = true)

    private fun ensureDirectory(path: String) {
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    private fun writeBytes(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, true)

    private fun join(directory: String, name: String): String = "${directory.trimEnd('/')}/$name"
}

private class ImagePickerDelegate(
    private val onResult: (Result<ImportedImage?>) -> Unit,
    private val onFinished: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        if (image == null) {
            finish(Result.failure(IllegalStateException("The selected item did not contain an image.")))
            return
        }
        finish(runCatching { image.toImportedImage() })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        finish(Result.success(null))
    }

    private fun finish(result: Result<ImportedImage?>) {
        onResult(result)
        onFinished()
    }
}

private fun UIImage.toImportedImage(): ImportedImage {
    val pngData = UIImagePNGRepresentation(this)
        ?: error("iPadOS could not decode the selected image.")
    val encoded = pngData.toByteArray()
    val skiaImage = Image.makeFromEncoded(encoded)
    val width = skiaImage.width
    val height = skiaImage.height
    require(width > 0 && height > 0) { "The selected image has invalid dimensions." }
    require(width.toLong() * height <= 16_000_000L) {
        "This image is too large to import. Choose an image up to 16 million pixels."
    }

    val pixels = IntArray(width * height)
    val bitmap = skiaImage.toComposeImageBitmap()
    bitmap.readPixels(
        buffer = pixels,
        startX = 0,
        startY = 0,
        width = width,
        height = height,
        bufferOffset = 0,
        stride = width,
    )
    return ImportedImage("Imported image", width, height, pixels)
}

private fun NSData.toByteArray(): ByteArray {
    val count = length.toInt()
    if (count == 0) return ByteArray(0)
    val output = ByteArray(count)
    output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return output
}

private fun ByteArray.toNSData(): NSData = memScoped {
    if (isEmpty()) return NSData()
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}
