package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun WorkbenchOverlay(
    state: EditorState,
    origin: Offset,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    if (!state.workbenchVisible || state.workbenchItems.isEmpty() || scale <= 0f) return
    val density = LocalDensity.current
    val imageCache = remember { WorkbenchImageCache() }

    Box(modifier.fillMaxSize()) {
        state.workbenchItems.forEach { item ->
            val screenX = origin.x + item.x * scale
            val screenY = origin.y + item.y * scale
            val widthDp = with(density) { (item.width * scale).toDp() }
            val heightDp = with(density) { (item.height * scale).toDp() }

            val dragModifier = Modifier
                .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                .size(widthDp, heightDp)
                .pointerInput(item.id, scale) {
                    detectDragGestures(
                        onDragStart = { state.bringWorkbenchItemToFront(item.id) },
                        onDragEnd = state::commitWorkbenchPositions,
                        onDragCancel = state::commitWorkbenchPositions,
                    ) { change, amount ->
                        state.moveWorkbenchItem(item.id, amount.x / scale, amount.y / scale)
                        change.consume()
                    }
                }

            when (item) {
                is WorkbenchItem.Reference -> {
                    Box(
                        dragModifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF17191E))
                            .padding(6.dp),
                    ) {
                        Image(
                            bitmap = imageCache.image(item),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
                        )
                    }
                }

                is WorkbenchItem.Note -> {
                    Column(
                        dragModifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF1A8))
                            .padding(12.dp),
                    ) {
                        Text("NOTE", color = Color(0xFF625A32), fontSize = 8.sp, letterSpacing = .8.sp)
                        Text(
                            item.text,
                            color = Color(0xFF27230F),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }

                is WorkbenchItem.ColourCard -> {
                    val colour = parseColorHex(item.hex) ?: Color.Gray
                    Box(
                        dragModifier.clip(RoundedCornerShape(10.dp))
                            .background(colour)
                            .padding(10.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            item.hex,
                            color = if (colour.luminance() > .55f) Color.Black else Color.White,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

private class WorkbenchImageCache {
    private data class Entry(val pixels: IntArray, val image: ImageBitmap)
    private val images = linkedMapOf<String, Entry>()

    fun image(reference: WorkbenchItem.Reference): ImageBitmap {
        val previous = images.remove(reference.id)
        if (previous != null && previous.pixels.contentEquals(reference.argb)) {
            images[reference.id] = previous
            return previous.image
        }

        val image = ImageBitmap(reference.pixelWidth, reference.pixelHeight)
        val canvas = Canvas(image)
        val paint = Paint().apply { isAntiAlias = false }

        for (y in 0 until reference.pixelHeight) {
            var x = 0
            while (x < reference.pixelWidth) {
                val start = x
                val packed = reference.argb[y * reference.pixelWidth + x]
                x++
                while (x < reference.pixelWidth && reference.argb[y * reference.pixelWidth + x] == packed) x++
                if ((packed ushr 24) != 0) {
                    paint.color = Color(packed)
                    canvas.drawRect(start.toFloat(), y.toFloat(), x.toFloat(), (y + 1).toFloat(), paint)
                }
            }
        }

        images[reference.id] = Entry(reference.argb.copyOf(), image)
        while (images.size > 12) images.remove(images.keys.first())
        return image
    }
}
