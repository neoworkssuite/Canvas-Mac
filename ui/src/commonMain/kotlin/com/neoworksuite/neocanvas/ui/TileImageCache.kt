package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey

/** Bounded image cache; byte comparison catches preview, undo and reopened-document changes. */
internal class TileImageCache(private val capacity: Int = 128) {
    init { require(capacity > 0) }
    private data class Entry(val rgba: ByteArray, val image: ImageBitmap)
    private val entries = linkedMapOf<TileKey, Entry>()

    fun image(key: TileKey, rgba: ByteArray): ImageBitmap {
        require(rgba.size == TileFormat.BYTES_PER_TILE)
        val previous = entries.remove(key)
        if (previous != null && previous.rgba.contentEquals(rgba)) {
            entries[key] = previous
            return previous.image
        }
        val image = ImageBitmap(256, 256)
        val canvas = Canvas(image)
        val paint = Paint().apply { isAntiAlias = false }
        fun packed(x: Int, y: Int): Int {
            val i = (y * 256 + x) * 4
            return ((rgba[i + 3].toInt() and 255) shl 24) or
                ((rgba[i].toInt() and 255) shl 16) or
                ((rgba[i + 1].toInt() and 255) shl 8) or (rgba[i + 2].toInt() and 255)
        }
        // Combine same-colour pixels into scanline spans when creating a changed tile.
        for (y in 0 until 256) {
            var x = 0
            while (x < 256) {
                val start = x
                val color = packed(x, y)
                x++
                while (x < 256 && packed(x, y) == color) x++
                if ((color ushr 24) != 0) {
                    paint.color = Color(color)
                    canvas.drawRect(start.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat(), paint)
                }
            }
        }
        entries[key] = Entry(rgba.copyOf(), image)
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return image
    }
}
