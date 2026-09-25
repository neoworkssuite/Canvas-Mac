package com.neoworksuite.neocanvas.platform

object WindowsLaunchContract {
    const val packageVersion = "1.0.0"
    const val runtimeRelativePath = "windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe"
    const val installerRelativePath = "windowsApp/build/compose/binaries/main/exe/NeoCanvas-$packageVersion.exe"

    fun documentArgument(args: Array<String>): String? = args
        .asSequence()
        .map(String::trim)
        .firstOrNull { it.endsWith(".neocanvas", ignoreCase = true) }
}
