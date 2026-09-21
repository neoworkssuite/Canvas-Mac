package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PsdCompatibilityPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val report = state.psdCompatibilityReport ?: return
    val notices = buildList {
        if (report.lockedLayerCount > 0) {
            add(report.lockedLayerCount.toString() + " layer lock state" +
                if (report.lockedLayerCount == 1) " is" else "s are" +
                " not currently represented in NeoCanvas PSD export.")
        }
        if (state.transformSession != null) {
            add("A transform preview is still pending. Apply or cancel it before export.")
        }
        if (state.effectPreviewPatch != null) {
            add("An FX preview is still pending. Leaving FX commits it; Cancel reverts it.")
        }
        if (state.sleepingLayerCount > 0) {
            add(state.sleepingLayerCount.toString() + " sleeping Deep Layer" +
                if (state.sleepingLayerCount == 1) " will" else "s will" +
                " be included from local dormant storage.")
        }
        if (state.workbenchItems.isNotEmpty()) {
            add(state.workbenchItems.size.toString() + " Workbench item" +
                if (state.workbenchItems.size == 1) " is" else "s are" +
                " intentionally excluded from the PSD artwork.")
        }
        if (state.versions.isNotEmpty()) {
            add("Version Tree milestones stay local and are not embedded in the PSD.")
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("PSD PREFLIGHT", color = NeoCanvasColors.paper, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = .9.sp)
                Text("Photoshop compatibility before export", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = NeoCanvasColors.accent) }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (report.canExport) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (report.canExport) "READY FOR PSD" else "PSD NEEDS ATTENTION",
                color = NeoCanvasColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                report.width.toString() + " × " + report.height + " · " +
                    report.layerCount + " raster layer" + if (report.layerCount == 1) "" else "s",
                color = NeoCanvasColors.paper,
                fontSize = 11.sp,
            )
            Text(
                "RGB · 8-bit/channel · Photoshop PSD v1",
                color = NeoCanvasColors.muted,
                fontSize = 10.sp,
            )
        }

        PsdPreflightSection(
            "PRESERVED",
            listOf(
                "Raster pixels and transparency",
                "Layer names, order and visibility",
                "Layer opacity",
                "NeoCanvas blend modes",
                "Clipping masks",
                "Alpha-lock / transparency protection",
                "Merged document preview",
            ),
        )

        if (report.hiddenLayerCount > 0 || report.clippingLayerCount > 0 || report.alphaLockedLayerCount > 0) {
            PsdPreflightSection(
                "DOCUMENT DETAIL",
                listOf(
                    report.hiddenLayerCount.toString() + " hidden layer(s)",
                    report.clippingLayerCount.toString() + " clipping layer(s)",
                    report.alphaLockedLayerCount.toString() + " alpha-locked layer(s)",
                    "Approx. raw pixel payload " + formatMemoryBytes(report.estimatedRawPixelBytes),
                ),
            )
        }

        if (report.blockingIssues.isNotEmpty()) {
            PsdPreflightSection("BLOCKING", report.blockingIssues)
        }

        if (notices.isNotEmpty()) {
            PsdPreflightSection("NOTICES", notices)
        }

        if (state.lastPsdImportNotices.isNotEmpty()) {
            PsdPreflightSection("LAST PSD IMPORT", state.lastPsdImportNotices)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Preflight reflects NeoCanvas's current exporter, not a generic PSD promise.",
                color = NeoCanvasColors.faint,
                fontSize = 9.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = report.canExport,
                onClick = { state.exportPsdFromCompatibility() },
            ) {
                Text("Export PSD", color = NeoCanvasColors.accent)
            }
        }
    }
}

@Composable
private fun PsdPreflightSection(title: String, rows: List<String>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(NeoCanvasColors.chrome).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", color = NeoCanvasColors.accent, fontSize = 11.sp)
                Text(
                    row,
                    color = NeoCanvasColors.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
    }
}
