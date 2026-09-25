package com.neoworksuite.neocanvas.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WindowsLaunchContractTest {
    @Test fun runtime_application_is_not_the_installer() {
        assertEquals("windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe", WindowsLaunchContract.runtimeRelativePath)
        assertEquals("windowsApp/build/compose/binaries/main/exe/NeoCanvas-1.0.0.exe", WindowsLaunchContract.installerRelativePath)
        assertNotEquals(WindowsLaunchContract.runtimeRelativePath, WindowsLaunchContract.installerRelativePath)
    }

    @Test fun document_association_selects_first_neocanvas_argument() {
        assertEquals(
            "C:\\Art\\Sketch.neocanvas",
            WindowsLaunchContract.documentArgument(arrayOf("--safe", "C:\\Art\\Sketch.neocanvas", "later.neocanvas")),
        )
        assertNull(WindowsLaunchContract.documentArgument(arrayOf("readme.txt", "--safe")))
    }
}
