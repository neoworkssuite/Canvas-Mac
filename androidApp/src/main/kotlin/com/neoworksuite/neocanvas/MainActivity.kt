package com.neoworksuite.neocanvas

import android.os.Bundle
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.neoworksuite.neocanvas.ui.NeoCanvasApp
import com.neoworksuite.neocanvas.platform.AndroidEditorFileActions

class MainActivity : ComponentActivity() {
    private var imageCallback: ((Result<com.neoworksuite.neocanvas.ui.ImportedImage?>) -> Unit)? = null
    private var documentCallback: ((Result<com.neoworksuite.neocanvas.core.store.LoadResult?>) -> Unit)? = null
    private var psdCallback: ((Result<com.neoworksuite.neocanvas.renderer.PsdImportResult?>) -> Unit)? = null
    private val imagePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val callback = imageCallback
        imageCallback = null
        callback?.invoke(runCatching {
            if (uri == null) return@runCatching null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) {
                "Choose a valid image below 16 megapixels."
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                ?: error("Unable to decode image.")
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                com.neoworksuite.neocanvas.ui.ImportedImage("Imported image", bitmap.width, bitmap.height, pixels)
            } finally { bitmap.recycle() }
        })
    }
    private val documentPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val callback = documentCallback
        documentCallback = null
        callback?.invoke(runCatching {
            if (uri == null) return@runCatching null
            val importFile = java.io.File(cacheDir, "import-${java.util.UUID.randomUUID()}.neocanvas")
            try {
                contentResolver.openInputStream(uri)?.use { input -> importFile.outputStream().use(input::copyTo) }
                    ?: error("Unable to read selected document.")
                com.neoworksuite.neocanvas.platform.AndroidDocumentStore().load(importFile.absolutePath)
            } finally {
                importFile.delete()
            }
        })
    }
    private val psdPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val callback = psdCallback
        psdCallback = null
        callback?.invoke(runCatching {
            if (uri == null) return@runCatching null
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected PSD.")
            com.neoworksuite.neocanvas.renderer.PsdCodec.decode(bytes)
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = AndroidEditorFileActions(
            localDirectory = getExternalFilesDir(null) ?: filesDir,
            imagePicker = { callback ->
                imageCallback = callback
                imagePicker.launch(arrayOf("image/png", "image/jpeg"))
            },
            documentPicker = { callback ->
                documentCallback = callback
                // NeoCanvas packages have no universally registered Android MIME type.
                // The package reader validates the selected bytes before opening them.
                documentPicker.launch(arrayOf("*/*"))
            },
            psdPicker = { callback ->
                psdCallback = callback
                psdPicker.launch(arrayOf("image/vnd.adobe.photoshop", "application/octet-stream"))
            },
            fileSharer = { file, mimeType ->
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType(mimeType)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share NeoCanvas artwork",
                ))
            },
        )
        setContent {
            NeoCanvasApp(actions)
        }
    }
}
