package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.brushes.NeoBrushPack

data class InstalledBrushPack(val pack: NeoBrushPack)

enum class PackInstallResult { Installed, Replaced, Unchanged }
