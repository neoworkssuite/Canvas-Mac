package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KidsCanvasPreset {
    FreeDraw,
    MirrorDraw,
    PixelArt,
    ComicCanvas,
}

private data class KidsActivityCard(
    val title: String,
    val subtitle: String,
    val badge: String,
    val symbol: String,
    val preset: KidsCanvasPreset? = null,
)

private val includedKidsActivities = listOf(
    KidsActivityCard("Free Draw", "A clean canvas for doodles, painting and imagination.", "INCLUDED", "✦", KidsCanvasPreset.FreeDraw),
    KidsActivityCard("Mirror Draw", "Draw on one side and mirror it across the canvas.", "INCLUDED", "↔", KidsCanvasPreset.MirrorDraw),
    KidsActivityCard("Pixel Art", "A square canvas for chunky pixel-style pictures.", "INCLUDED", "▦", KidsCanvasPreset.PixelArt),
    KidsActivityCard("Comic Canvas", "A wide page for characters, stories and speech bubbles.", "INCLUDED", "▤", KidsCanvasPreset.ComicCanvas),
)

private val kidsPackCategories = listOf(
    KidsActivityCard("Colouring Pages", "Animals, space, dinosaurs, fantasy and seasonal packs.", "PACKS", "◉"),
    KidsActivityCard("Dot to Dot", "Numbered connect-the-dots from easy to challenging.", "PACKS", "•••"),
    KidsActivityCard("Mazes", "Simple paths, themed mazes and puzzle adventures.", "PACKS", "⌁"),
    KidsActivityCard("Colour by Number", "Number-guided colouring with reusable palettes.", "PACKS", "123"),
    KidsActivityCard("Trace & Draw", "Trace shapes, letters, animals and drawing guides.", "PACKS", "✎"),
    KidsActivityCard("Finish the Picture", "Complete half-drawn scenes and silly characters.", "PACKS", "½"),
    KidsActivityCard("Spot the Difference", "Visual puzzle sheets made for marking directly.", "PACKS", "◎"),
    KidsActivityCard("Sticker Scenes", "Build scenes from themed reusable sticker packs.", "PACKS", "★"),
)

@Composable
fun KidsActivityScreen(
    onBack: () -> Unit,
    onStartPreset: (KidsCanvasPreset) -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(NeoCanvasColors.workspace)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).background(NeoCanvasColors.chrome).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(neoCanvasIcon(), "NeoCanvas Kids", Modifier.size(44.dp))
            Column {
                Text("Kids", color = NeoCanvasColors.paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Creative activities made for touch and Pencil", color = NeoCanvasColors.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back to Gallery", color = NeoCanvasColors.accent) }
        }

        message?.let {
            Row(
                Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, color = NeoCanvasColors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Dismiss", color = NeoCanvasColors.accent, modifier = Modifier.clickable { message = null })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            modifier = Modifier.fillMaxSize().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "included-heading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                KidsSectionHeading("START CREATING", "Included activities work offline")
            }
            items(includedKidsActivities, key = { "included-${it.title}" }) { activity ->
                KidsActivityTile(activity) {
                    activity.preset?.let(onStartPreset)
                }
            }

            item(key = "packs-heading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                KidsSectionHeading("ACTIVITY LIBRARY", "Downloadable packs will live here")
            }
            items(kidsPackCategories, key = { "pack-${it.title}" }) { activity ->
                KidsActivityTile(activity) {
                    message = "${activity.title} packs are ready for the NeoWorks Kids download feed. The pack source still needs to be connected."
                }
            }
        }
    }
}

@Composable
private fun KidsSectionHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(title, color = NeoCanvasColors.faint, fontSize = 10.sp, letterSpacing = .9.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = NeoCanvasColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun KidsActivityTile(activity: KidsActivityCard, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(NeoCanvasColors.panelRaised)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(NeoCanvasColors.chrome),
                contentAlignment = Alignment.Center,
            ) {
                Text(activity.symbol, color = NeoCanvasColors.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(NeoCanvasColors.panel)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(activity.badge, color = NeoCanvasColors.faint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            activity.title,
            color = NeoCanvasColors.paper,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            activity.subtitle,
            color = NeoCanvasColors.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (activity.preset != null) {
            Button(onClick = onClick, modifier = Modifier.padding(top = 14.dp)) { Text("Start") }
        } else {
            Text(
                "Browse packs",
                color = NeoCanvasColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}
