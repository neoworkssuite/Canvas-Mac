package com.neoworksuite.neocanvas

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Item
import androidx.compose.ui.window.Menu
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.neoworksuite.neocanvas.ui.NeoCanvasApp
import com.neoworksuite.neocanvas.ui.neoCanvasIcon
import com.neoworksuite.neocanvas.ui.rememberEditorState

fun main() = application {
    val editor = rememberEditorState()
    val windowState = remember { WindowState(placement = WindowPlacement.Maximized) }
    Window(
        onCloseRequest = { editor.requestClose { exitApplication() } },
        title = if (editor.hasUnsavedChanges) "NeoCanvas • Unsaved changes" else "NeoCanvas",
        icon = neoCanvasIcon(),
        state = windowState,
    ) {
        MenuBar {
            Menu("File") {
                Item("Close", onClick = { editor.requestClose { exitApplication() } })
            }
        }
        NeoCanvasApp(state = editor)
    }
}
