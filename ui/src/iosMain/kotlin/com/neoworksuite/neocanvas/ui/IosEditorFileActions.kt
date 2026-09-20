package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
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
 * iPad host services that need native UIKit.
 *
 * The shared editor remains platform-neutral; UIKit is only responsible for selecting and decoding
 * the image before handing normal ARGB pixels back to [EditorState].
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosEditorFileActions(
    private val presenter: () -> UIViewController?,
) : EditorFileActions by UnavailableEditorFileActions {
    private var activeImagePickerDelegate: ImagePickerDelegate? = null

    override fun importImage(onResult: (Result<ImportedImage?>) -> Unit) {
        val host = presenter()
        if (host == null) {
            onResult(Result.failure(IllegalStateException("The iPad image picker is not ready yet.")))
            return
        }

        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)) {
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
}

@OptIn(ExperimentalForeignApi::class)
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

        val result = runCatching { image.toImportedImage() }
        finish(result)
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

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toImportedImage(): ImportedImage {
    val pngData = UIImagePNGRepresentation(this)
        ?: error("iPadOS could not decode the selected image.")

    val encoded = ByteArray(pngData.length.toInt())
    if (encoded.isNotEmpty()) {
        encoded.usePinned { pinned ->
            memcpy(pinned.addressOf(0), pngData.bytes, pngData.length)
        }
    }

    val skiaImage = Image.makeFromEncoded(encoded)
    val width = skiaImage.width
    val height = skiaImage.height
    require(width > 0 && height > 0) { "The selected image has invalid dimensions." }
    require(width.toLong() * height <= 16_000_000L) {
        "This image is too large to import. Choose an image up to 16 million pixels."
    }

    val bitmap = skiaImage.toComposeImageBitmap()
    val pixels = IntArray(width * height)
    bitmap.readPixels(
        buffer = pixels,
        startX = 0,
        startY = 0,
        width = width,
        height = height,
        bufferOffset = 0,
        stride = width,
    )
    return ImportedImage(
        name = "Imported image",
        width = width,
        height = height,
        argb = pixels,
    )
}
