package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Native iPad/iOS entry point for NeoCanvas with UIKit-backed local services. */
fun MainViewController(): UIViewController {
    var controller: UIViewController? = null
    val fileActions = IosEditorFileActions { controller }

    return ComposeUIViewController {
        NeoCanvasApp(fileActions = fileActions)
    }.also { controller = it }
}
