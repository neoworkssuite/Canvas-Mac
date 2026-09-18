package com.neoworksuite.neocanvas.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WindowsLaunchContractTest {
    @Test fun runtime_application_is_not_the_installer() {
        assertEquals("windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe", WindowsLaunchContract.runtimeRelativePath)
        assertNotEquals(WindowsLaunchContract.runtimeRelativePath, WindowsLaunchContract.installerRelativePath)
    }
}
