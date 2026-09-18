package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import com.neoworksuite.neocanvas.ui.resources.Res
import com.neoworksuite.neocanvas.ui.resources.neocanvas_logo

@Composable
fun neoCanvasIcon(): Painter = painterResource(Res.drawable.neocanvas_logo)
