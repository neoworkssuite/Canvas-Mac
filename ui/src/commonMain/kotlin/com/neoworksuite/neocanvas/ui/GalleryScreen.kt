package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.core.store.SaveResult
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
fun GalleryScreen(
    actions: EditorFileActions,
    onNew: () -> Unit,
    onImportDocument: () -> Unit,
    kidsModeEnabled: Boolean,
    onKids: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var documents by remember { mutableStateOf(runCatching { actions.listLocalDocuments() }.getOrDefault(emptyList())) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargets by remember { mutableStateOf(emptySet<String>()) }
    var preview by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var stackMembers by remember {
        mutableStateOf(
            reconcileGalleryStack(
                runCatching { actions.loadGalleryStack() }.getOrDefault(emptySet()),
                documents,
            ),
        )
    }
    var stackOpen by remember { mutableStateOf(false) }

    fun saveStack(next: Set<String>, success: String): Boolean {
        val clean = reconcileGalleryStack(next, documents)
        val result = try {
            actions.saveGalleryStack(clean)
        } catch (error: Exception) {
            SaveResult.Failure(error.message ?: "Could not save Gallery stack.")
        }
        return if (result == SaveResult.Success) {
            stackMembers = clean
            message = success
            true
        } else {
            message = (result as SaveResult.Failure).message
            false
        }
    }

    fun refresh() {
        documents = runCatching { actions.listLocalDocuments() }.getOrDefault(emptyList())
        val clean = reconcileGalleryStack(stackMembers, documents)
        if (clean != stackMembers) {
            stackMembers = clean
            runCatching { actions.saveGalleryStack(clean) }
        }
        if (stackMembers.isEmpty()) stackOpen = false
    }
    fun apply(result: SaveResult, success: String) {
        message = if (result == SaveResult.Success) success else (result as SaveResult.Failure).message
        if (result == SaveResult.Success) refresh()
    }
    val visibleDocuments = if (stackOpen) documents.filter { it in stackMembers } else documents.filter { it !in stackMembers }

    Box(Modifier.fillMaxSize().background(NeoCanvasColors.workspace)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(72.dp).background(NeoCanvasColors.chrome).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(neoCanvasIcon(), "NeoCanvas", Modifier.size(44.dp))
                Column {
                    Text(if (stackOpen) "Stack" else "Gallery", color = NeoCanvasColors.paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Local artwork on this device", color = NeoCanvasColors.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                if (stackOpen) GalleryAction("Back") { stackOpen = false; selected = emptySet() }
                GalleryAction(if (selecting) "Done" else "Select") { selecting = !selecting; if (!selecting) selected = emptySet() }
                GalleryAction("Open file") { onImportDocument() }
                if (kidsModeEnabled) GalleryAction("Kids") { onKids() }
                Button(onClick = onNew) { Text("+  New artwork") }
            }

            if (selecting && selected.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(NeoCanvasColors.panel).padding(horizontal = 20.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${selected.size} selected", color = NeoCanvasColors.paper, modifier = Modifier.weight(1f))
                    if (!stackOpen && selected.size > 1) GalleryAction("Stack") {
                        if (saveStack(selected, "Created local stack")) {
                            selected = emptySet()
                            selecting = false
                        }
                    }
                    GalleryAction("Duplicate") {
                        selected.forEach { apply(actions.duplicateLocalDocument(it), "Duplicated artwork") }
                        selected = emptySet()
                    }
                    GalleryAction("Delete", danger = true) { deleteTargets = selected }
                }
            }

            message?.let {
                Row(Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(it, color = NeoCanvasColors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Dismiss", color = NeoCanvasColors.accent, modifier = Modifier.clickable { message = null })
                }
            }

            if (documents.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(neoCanvasIcon(), null, Modifier.size(112.dp))
                    Text("Your Gallery is ready", color = NeoCanvasColors.paper, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (kidsModeEnabled) "Create a canvas or open Kids activities. Everything stays local."
                        else "Create a canvas. Everything stays local.",
                        color = NeoCanvasColors.muted,
                        modifier = Modifier.padding(10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onNew) { Text("Create artwork") }
                        if (kidsModeEnabled) Button(onClick = onKids) { Text("Kids activities") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(230.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (!stackOpen && stackMembers.isNotEmpty()) item(key = "local-stack") {
                        GalleryStackCard(stackMembers.size) { stackOpen = true }
                    }
                    items(visibleDocuments, key = { it }) { name ->
                        val thumbnail = remember(name, documents) {
                            actions.localDocumentThumbnail(name)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
                        }
                        GalleryArtworkCard(
                            name = name,
                            thumbnail = thumbnail,
                            selected = name in selected,
                            selecting = selecting,
                            onOpen = { if (selecting) selected = selected.toggle(name) else onOpen(name) },
                            onPreview = { preview = name },
                            onSelect = { selecting = true; selected = selected.toggle(name) },
                            onRename = { renameTarget = name; renameText = name.removeSuffix(".neocanvas") },
                            onDuplicate = { apply(actions.duplicateLocalDocument(name), "Duplicated artwork") },
                            onDelete = { deleteTargets = setOf(name) },
                        )
                    }
                }
            }

            if (GalleryPromotion.enabled && !stackOpen) {
                KickstarterGalleryBanner(
                    onOpen = {
                        if (!actions.openExternalUrl(GalleryPromotion.url)) {
                            message = GalleryPromotion.url
                        }
                    },
                )
            }
        }

        preview?.let { current ->
            val scope = visibleDocuments.ifEmpty { documents }
            val previewBitmap = remember(current, documents) {
                actions.localDocumentThumbnail(current)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            }
            GalleryPreview(
                name = current,
                thumbnail = previewBitmap,
                onClose = { preview = null },
                onOpen = { preview = null; onOpen(current) },
                onPrevious = { preview = scope.circularNeighbour(current, -1) },
                onNext = { preview = scope.circularNeighbour(current, 1) },
            )
        }
    }

    renameTarget?.let { original ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = NeoCanvasColors.panel,
            title = { Text("Rename artwork", color = NeoCanvasColors.paper) },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true,
                label = { Text("Name") }, colors = studioTextFieldColors()) },
            confirmButton = { TextButton(onClick = {
                val result = actions.renameLocalDocument(original, renameText)
                if (result == SaveResult.Success) {
                    val renamed = renameText.trim() + ".neocanvas"
                    val nextStack = renameGalleryStackMember(stackMembers, original, renamed)
                    renameTarget = null
                    documents = runCatching { actions.listLocalDocuments() }.getOrDefault(documents)
                    stackMembers = reconcileGalleryStack(nextStack, documents)
                    val stackResult = runCatching { actions.saveGalleryStack(stackMembers) }
                        .getOrElse { SaveResult.Failure(it.message ?: "Could not update Gallery stack.") }
                    message = if (stackResult == SaveResult.Success) {
                        "Renamed artwork"
                    } else {
                        "Artwork renamed · " + (stackResult as SaveResult.Failure).message
                    }
                } else {
                    message = (result as SaveResult.Failure).message
                }
            }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }
    if (deleteTargets.isNotEmpty()) AlertDialog(
        onDismissRequest = { deleteTargets = emptySet() }, containerColor = NeoCanvasColors.panel,
        title = { Text("Delete ${deleteTargets.size} artwork${if (deleteTargets.size == 1) "" else "s"}?", color = NeoCanvasColors.paper) },
        text = { Text("The local document will be moved to NeoCanvas trash.", color = NeoCanvasColors.muted) },
        confirmButton = { TextButton(onClick = {
            var lastFailure: String? = null
            deleteTargets.forEach { name ->
                val result = actions.deleteLocalDocument(name)
                if (result is SaveResult.Failure) lastFailure = result.message
            }
            documents = runCatching { actions.listLocalDocuments() }.getOrDefault(documents)
            val clean = reconcileGalleryStack(stackMembers, documents)
            stackMembers = clean
            runCatching { actions.saveGalleryStack(clean) }
            if (stackMembers.isEmpty()) stackOpen = false
            message = lastFailure ?: "Moved artwork to local trash"
            selected = emptySet()
            deleteTargets = emptySet()
        }) { Text("Delete", color = Color(0xFFFF7777)) } },
        dismissButton = { TextButton(onClick = { deleteTargets = emptySet() }) { Text("Cancel") } },
    )
}

@Composable
private fun KickstarterGalleryBanner(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = neoCanvasIcon(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(GalleryPromotion.title, color = NeoCanvasColors.paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(GalleryPromotion.message, color = NeoCanvasColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Button(onClick = onOpen) { Text("View Kickstarter") }
    }
}

internal fun reconcileGalleryStack(
    members: Set<String>,
    documents: Collection<String>,
): Set<String> {
    val available = documents.toSet()
    return members.filterTo(linkedSetOf()) { it in available }
}

internal fun renameGalleryStackMember(
    members: Set<String>,
    oldName: String,
    newName: String,
): Set<String> = members.mapTo(linkedSetOf()) { if (it == oldName) newName else it }

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
private fun List<String>.circularNeighbour(value: String, delta: Int): String {
    if (isEmpty()) return value
    val index = indexOf(value).takeIf { it >= 0 } ?: 0
    return this[(index + delta + size) % size]
}

@Composable
private fun GalleryAction(label: String, danger: Boolean = false, onClick: () -> Unit) = TextButton(onClick = onClick) {
    Text(label, color = if (danger) Color(0xFFFF7777) else NeoCanvasColors.accent)
}

@Composable
private fun GalleryArtworkCard(
    name: String,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NeoCanvasColors.panel)) {
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GalleryAction("Rename", onClick = onRename)
            GalleryAction("Copy", onClick = onDuplicate)
            GalleryAction("Delete", true, onDelete)
        }
        Column(
            Modifier.fillMaxWidth().offset(x = if (revealed) (-190).dp else 0.dp)
                .clip(RoundedCornerShape(16.dp)).background(if (selected) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
                .clickable(onClick = onOpen)
                .pointerInput(name) {
                    detectHorizontalDragGestures(
                        onDragEnd = { revealed = drag < -30f; drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                    )
                }
                .semantics { contentDescription = "Artwork ${name.removeSuffix(".neocanvas")}" },
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFFF1EEE8)), contentAlignment = Alignment.Center) {
                if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(neoCanvasIcon(), null, Modifier.size(74.dp))
                Text("Preview", color = Color(0xFF59616B), fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clickable(onClick = onPreview))
                if (selecting) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape)
                    .background(if (selected) NeoCanvasColors.accent else NeoCanvasColors.chrome).clickable(onClick = onSelect), contentAlignment = Alignment.Center) {
                    Text(if (selected) "✓" else "", color = NeoCanvasColors.ink)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name.removeSuffix(".neocanvas"),
                    color = if (selected) NeoCanvasColors.ink else NeoCanvasColors.paper,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (!selecting) {
                    Text(
                        "Open",
                        color = if (selected) NeoCanvasColors.ink else NeoCanvasColors.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryStackCard(count: Int, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NeoCanvasColors.panelRaised).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFF303946)), contentAlignment = Alignment.Center) {
            Image(neoCanvasIcon(), null, Modifier.size(82.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape).background(NeoCanvasColors.chrome).padding(horizontal = 9.dp, vertical = 5.dp)) {
                Text("$count", color = NeoCanvasColors.paper)
            }
        }
        Text("Stack", color = NeoCanvasColors.paper, fontWeight = FontWeight.Medium, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun GalleryPreview(name: String, thumbnail: ImageBitmap?, onClose: () -> Unit, onOpen: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xF211151B)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GalleryAction("Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Text(name.removeSuffix(".neocanvas"), color = NeoCanvasColors.paper, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            GalleryAction("Open", onClick = onOpen)
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            GalleryAction("‹", onClick = onPrevious)
            Box(Modifier.weight(1f).fillMaxSize().padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EEE8)), contentAlignment = Alignment.Center) {
                if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Image(neoCanvasIcon(), null, Modifier.size(180.dp))
            }
            GalleryAction("›", onClick = onNext)
        }
    }
}
