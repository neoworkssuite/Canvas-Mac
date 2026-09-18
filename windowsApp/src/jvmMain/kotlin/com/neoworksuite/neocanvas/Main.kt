package com.neoworksuite.neocanvas

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember
import com.neoworksuite.neocanvas.ui.rememberEditorState
import com.neoworksuite.neocanvas.ui.NeoCanvasApp
import com.neoworksuite.neocanvas.ui.neoCanvasIcon
import com.neoworksuite.neocanvas.platform.WindowsEditorFileActions

fun main() = application {
    val fileActions = remember { WindowsEditorFileActions() }
    val editor = rememberEditorState(fileActions)
    val windowState = remember { WindowState(placement = WindowPlacement.Maximized) }
    Window(
        onCloseRequest = { editor.requestClose { exitApplication() } },
        title = if (editor.hasUnsavedChanges) "NeoCanvas • Unsaved changes" else "NeoCanvas",
        icon = neoCanvasIcon(),
        state = windowState,
    ) {
        NeoCanvasApp(fileActions, editor)
    }
}
