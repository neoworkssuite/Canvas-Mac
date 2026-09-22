package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Swift-owned networking bridge for the App Store lookup. */
interface NativeUpdateLookup {
    fun check(onResult: (json: String?, errorMessage: String?) -> Unit)
}

/** Native iPad/iOS entry point for NeoCanvas with UIKit-backed local services. */
fun MainViewController(updateLookup: NativeUpdateLookup? = null): UIViewController {
    var controller: UIViewController? = null
    val fileActions = IosEditorFileActions(
        presenter = { controller },
        updateLookup = updateLookup,
    )

    return ComposeUIViewController {
        NeoCanvasApp(fileActions = fileActions)
    }.also { controller = it }
}
