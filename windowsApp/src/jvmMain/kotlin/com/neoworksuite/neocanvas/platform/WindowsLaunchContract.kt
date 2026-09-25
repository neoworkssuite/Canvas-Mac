package com.neoworksuite.neocanvas.platform

object WindowsLaunchContract {
    const val packageVersion = "1.0.0"
    const val runtimeRelativePath = "windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe"
    const val installerRelativePath = "windowsApp/build/compose/binaries/main/exe/NeoCanvas-$packageVersion.exe"
    const val selfTestArgument = "--windows-self-test"

    fun isSelfTest(args: Array<String>): Boolean = args.any { it.trim().equals(selfTestArgument, ignoreCase = true) }

    fun documentArgument(args: Array<String>): String? = args
        .asSequence()
        .map(String::trim)
        .firstOrNull { it.endsWith(".neocanvas", ignoreCase = true) }
}
