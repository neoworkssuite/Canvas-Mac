package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.brushes.PointerKind

object WindowsSelfTest {
    const val passMarker = "NEOCANVAS_WINDOWS_SELF_TEST=PASS"

    fun run(): String {
        val osName = System.getProperty("os.name").orEmpty()
        check(osName.contains("Windows", ignoreCase = true)) { "Expected Windows runtime, got $osName" }

        val mouse = WindowsInputAdapter.sample(10f, 20f, 1L, pressure = null, pen = false)
        check(mouse.pointerKind == PointerKind.MOUSE)
        check(mouse.pressure == 1f)

        val pen = WindowsInputAdapter.sample(10f, 20f, 2L, pressure = .42f, pen = true)
        check(pen.pointerKind == PointerKind.STYLUS)
        check(pen.pressure == .42f)

        return buildString {
            appendLine(passMarker)
            appendLine("os=$osName")
            appendLine("arch=" + System.getProperty("os.arch").orEmpty())
            appendLine("java=" + System.getProperty("java.version").orEmpty())
            appendLine("packageVersion=" + WindowsLaunchContract.packageVersion)
            appendLine("pressure=mouse:${mouse.pressure},pen:${pen.pressure}")
        }.trimEnd()
    }
}
