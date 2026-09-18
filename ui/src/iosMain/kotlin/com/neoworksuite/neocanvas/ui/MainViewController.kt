package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Native iPad/iOS entry point for NeoCanvas.
 *
 * Apple-specific document and image services will be connected
 * through EditorFileActions in the next Apple platform phase.
 */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        NeoCanvasApp()
    }