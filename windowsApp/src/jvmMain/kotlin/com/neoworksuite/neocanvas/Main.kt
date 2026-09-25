package com.neoworksuite.neocanvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.platform.WindowsEditorFileActions
import com.neoworksuite.neocanvas.platform.WindowsLaunchContract
import com.neoworksuite.neocanvas.ui.InspectorPanel
import com.neoworksuite.neocanvas.ui.NeoCanvasApp
import com.neoworksuite.neocanvas.ui.Tool
import com.neoworksuite.neocanvas.ui.neoCanvasIcon
import com.neoworksuite.neocanvas.ui.rememberEditorState

fun main(args: Array<String>) = application {
    val fileActions = remember { WindowsEditorFileActions() }
    val launchPath = remember { WindowsLaunchContract.documentArgument(args) }
    val launchResult = remember(launchPath) { launchPath?.let(fileActions::openPath) }
    val editor = rememberEditorState(fileActions)
    val windowState = remember { WindowState(placement = WindowPlacement.Maximized) }

    LaunchedEffect(launchResult) {
        launchResult?.let(editor::openProvidedDocument)
    }

    Window(
        onCloseRequest = { editor.requestClose { exitApplication() } },
        title = when {
            editor.hasUnsavedChanges -> "NeoCanvas • Unsaved changes"
            launchResult is LoadResult.Success -> "NeoCanvas • " + launchPath.orEmpty().substringAfterLast('\\').substringAfterLast('/')
            else -> "NeoCanvas"
        },
        icon = neoCanvasIcon(),
        state = windowState,
    ) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val ctrl = event.isCtrlPressed
                when {
                    ctrl && event.key == Key.Z && event.isShiftPressed -> {
                        editor.redo()
                        true
                    }
                    ctrl && event.key == Key.Z -> {
                        editor.undo()
                        true
                    }
                    ctrl && event.key == Key.Y -> {
                        editor.redo()
                        true
                    }
                    ctrl && event.key == Key.S -> {
                        editor.save()
                        true
                    }
                    ctrl && event.key == Key.O -> {
                        editor.openDocument()
                        true
                    }
                    ctrl && event.key == Key.N -> {
                        editor.newDocument()
                        true
                    }
                    event.key == Key.B -> {
                        editor.tool = Tool.Brush
                        editor.statusMessage = "Brush"
                        true
                    }
                    event.key == Key.E -> {
                        editor.tool = Tool.Eraser
                        editor.statusMessage = "Eraser"
                        true
                    }
                    event.key == Key.I -> {
                        editor.tool = Tool.Eyedropper
                        editor.statusMessage = "Eyedropper"
                        true
                    }
                    event.key == Key.S && !ctrl -> {
                        editor.tool = Tool.Smudge
                        editor.statusMessage = "Smudge"
                        true
                    }
                    event.key == Key.L -> {
                        editor.showInspector(InspectorPanel.Layers)
                        true
                    }
                    event.key == Key.C -> {
                        editor.showInspector(InspectorPanel.Colors)
                        true
                    }
                    event.key == Key.F11 -> {
                        editor.toggleCanvasOnlyMode()
                        true
                    }
                    else -> false
                }
            },
        ) {
            NeoCanvasApp(
                fileActions = fileActions,
                state = editor,
                startInEditor = launchResult is LoadResult.Success,
            )
        }
    }
}
