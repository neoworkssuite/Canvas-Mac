package com.neoworksuite.neocanvas

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.platform.WindowsEditorFileActions
import com.neoworksuite.neocanvas.platform.WindowsLaunchContract
import com.neoworksuite.neocanvas.platform.WindowsSelfTest
import com.neoworksuite.neocanvas.ui.NeoCanvasApp
import com.neoworksuite.neocanvas.ui.neoCanvasIcon
import com.neoworksuite.neocanvas.ui.rememberEditorState

fun main(args: Array<String>) {
    if (WindowsLaunchContract.isSelfTest(args)) {
        val report = WindowsSelfTest.run()
        WindowsLaunchContract.selfTestReportPath(args)?.let { path ->
            java.io.File(path).apply { parentFile?.mkdirs() }.writeText(report)
        } ?: println(report)
        return
    }

    application {
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
        NeoCanvasApp(
            fileActions = fileActions,
            state = editor,
            startInEditor = launchResult is LoadResult.Success,
        )
    }
    }
}
