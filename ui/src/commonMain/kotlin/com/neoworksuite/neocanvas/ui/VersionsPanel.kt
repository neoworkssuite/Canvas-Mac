package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VersionsPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var branchSource by remember { mutableStateOf<LocalVersionEntry?>(null) }
    var branchName by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "VERSIONS",
                    color = NeoCanvasColors.paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .9.sp,
                )
                Text(
                    "BRANCH · " + state.activeVersionBranch.uppercase() + " · local only",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Done", color = NeoCanvasColors.accent)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeoCanvasColors.chrome)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(60) },
                singleLine = true,
                label = { Text("Milestone name") },
                placeholder = { Text("e.g. Colour approved") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Creates a complete local snapshot of layers and pixels.",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = label.trim().isNotEmpty(),
                    onClick = {
                        if (state.createVersion(label)) label = ""
                    },
                ) {
                    Text("Save milestone", color = NeoCanvasColors.accent)
                }
            }
        }

        branchSource?.let { source ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeoCanvasColors.panelRaised)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "BRANCH FROM · " + source.label,
                    color = NeoCanvasColors.paper,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it.take(30) },
                    singleLine = true,
                    label = { Text("New branch name") },
                    placeholder = { Text("e.g. Client B") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { branchSource = null; branchName = "" }) {
                        Text("Cancel", color = NeoCanvasColors.muted)
                    }
                    TextButton(
                        enabled = branchName.trim().isNotEmpty(),
                        onClick = {
                            if (state.branchFromVersion(source.id, branchName)) {
                                branchSource = null
                                branchName = ""
                            }
                        },
                    ) {
                        Text("Create branch", color = NeoCanvasColors.accent)
                    }
                }
            }
        }

        state.versionComparison?.let { comparison ->
            VersionComparisonCard(
                comparison = comparison,
                onClose = state::closeVersionComparison,
            )
        }

        state.versionError?.let { error ->
            Text(
                error,
                color = NeoCanvasColors.accent,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeoCanvasColors.panelRaised)
                    .padding(8.dp),
            )
        }

        if (state.versions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No milestones yet.\nSave one before a major change.",
                    color = NeoCanvasColors.muted,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(state.versions, key = { _, item -> item.id }) { index, version ->
                    VersionTimelineRow(
                        version = version,
                        number = state.versions.size - index,
                        newest = index == 0,
                        onCompare = { state.compareVersion(version.id) },
                        onRestore = { state.restoreVersion(version.id) },
                        onBranch = { branchSource = version; branchName = "" },
                        onDelete = { state.deleteVersion(version.id) },
                    )
                }
            }
        }

        Text(
            "Restore is non-destructive: NeoCanvas first saves the current canvas as “Before restore”.",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun VersionTimelineRow(
    version: LocalVersionEntry,
    number: Int,
    newest: Boolean,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
    onBranch: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (newest) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.clip(CircleShape)
                    .background(if (newest) NeoCanvasColors.accent else NeoCanvasColors.line)
                    .padding(5.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                version.label,
                color = NeoCanvasColors.paper,
                fontSize = 12.sp,
                fontWeight = if (newest) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                version.branch.uppercase() + " · VERSION " + number +
                    if (newest) " · LATEST" else " · LOCAL",
                color = NeoCanvasColors.faint,
                fontSize = 8.sp,
                letterSpacing = .6.sp,
            )
        }
        TextButton(onClick = onCompare) {
            Text("Compare", color = NeoCanvasColors.accent, fontSize = 10.sp)
        }
        TextButton(onClick = onRestore) {
            Text("Restore", color = NeoCanvasColors.accent, fontSize = 10.sp)
        }
        TextButton(onClick = onBranch) {
            Text("Branch", color = NeoCanvasColors.accent, fontSize = 10.sp)
        }
        TextButton(onClick = onDelete) {
            Text("Delete", color = NeoCanvasColors.muted, fontSize = 10.sp)
        }
    }
}


@Composable
private fun VersionComparisonCard(
    comparison: VersionComparison,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeoCanvasColors.panelRaised)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "COMPARE · " + comparison.version.label,
                    color = NeoCanvasColors.paper,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    comparison.version.branch.uppercase() + " · READ ONLY",
                    color = NeoCanvasColors.faint,
                    fontSize = 8.sp,
                    letterSpacing = .6.sp,
                )
            }
            TextButton(onClick = onClose) {
                Text("Close", color = NeoCanvasColors.muted, fontSize = 9.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VersionPreview(
                label = "CURRENT",
                image = comparison.currentThumbnail,
                info = comparison.currentWidth.toString() + " × " + comparison.currentHeight +
                    " · " + comparison.currentLayerCount + " layers",
                modifier = Modifier.weight(1f),
            )
            VersionPreview(
                label = comparison.version.label.uppercase(),
                image = comparison.versionThumbnail,
                info = comparison.versionWidth.toString() + " × " + comparison.versionHeight +
                    " · " + comparison.versionLayerCount + " layers",
                modifier = Modifier.weight(1f),
            )
        }

        val sizeText = if (comparison.dimensionsChanged) {
            "Canvas changed: " + comparison.versionWidth + "×" + comparison.versionHeight +
                " → " + comparison.currentWidth + "×" + comparison.currentHeight
        } else {
            "Canvas size unchanged"
        }
        val layersText = when {
            comparison.layerDelta > 0 -> "+" + comparison.layerDelta + " layers in current"
            comparison.layerDelta < 0 -> (-comparison.layerDelta).toString() + " fewer layers in current"
            else -> "Layer count unchanged"
        }
        Text(
            sizeText + " · " + layersText,
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun VersionPreview(
    label: String,
    image: com.neoworksuite.neocanvas.renderer.PngImage,
    info: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(image) { versionPreviewBitmap(image) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = NeoCanvasColors.faint,
            fontSize = 8.sp,
            letterSpacing = .6.sp,
            maxLines = 1,
        )
        Box(
            Modifier.fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeoCanvasColors.workspace),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = label + " version preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            info,
            color = NeoCanvasColors.muted,
            fontSize = 8.sp,
            maxLines = 1,
        )
    }
}

private fun versionPreviewBitmap(
    source: com.neoworksuite.neocanvas.renderer.PngImage,
): ImageBitmap {
    val image = ImageBitmap(source.width, source.height)
    val canvas = Canvas(image)
    val paint = Paint().apply { isAntiAlias = false }

    fun packed(x: Int, y: Int): Int {
        val offset = (y * source.width + x) * 4
        return ((source.rgba[offset + 3].toInt() and 255) shl 24) or
            ((source.rgba[offset].toInt() and 255) shl 16) or
            ((source.rgba[offset + 1].toInt() and 255) shl 8) or
            (source.rgba[offset + 2].toInt() and 255)
    }

    for (y in 0 until source.height) {
        var x = 0
        while (x < source.width) {
            val start = x
            val color = packed(x, y)
            x++
            while (x < source.width && packed(x, y) == color) x++
            if ((color ushr 24) != 0) {
                paint.color = Color(color)
                canvas.drawRect(
                    start.toFloat(),
                    y.toFloat(),
                    x.toFloat(),
                    (y + 1).toFloat(),
                    paint,
                )
            }
        }
    }
    return image
}
