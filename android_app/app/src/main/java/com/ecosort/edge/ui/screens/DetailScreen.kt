package com.ecosort.edge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosort.edge.domain.model.ActiveScan
import com.ecosort.edge.domain.model.StreamType
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun DetailScreen(
    activeScan: ActiveScan?,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit
) {
    if (activeScan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No item selected", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val dec = activeScan.decision
    val binColor = Color(dec.colorHex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EcoColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Classification Result", style = MaterialTheme.typography.headlineMedium)
        }

        // Scanned Image Preview (null for Sanitary Privacy Override)
        if (activeScan.bitmap != null) {
            Image(
                bitmap = activeScan.bitmap.asImageBitmap(),
                contentDescription = dec.predictedClass,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EcoColors.Surface,
                border = BorderStroke(1.dp, EcoColors.Border),
                modifier = Modifier.fillMaxWidth().height(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Camera-Free Sanitary Privacy Mode Active", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Statutory SWM 2026 Bin Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = binColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dec.status.replace("_", " "), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(dec.binName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    dec.predictedClass.replace("_", " ").replaceFirstChar { it.uppercase() },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp
                )
            }
        }

        // Fire-Hazard Interlock Warning
        if (dec.stream == StreamType.SPECIAL_CARE) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EcoColors.HazardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = EcoColors.HazardRed)
                    Text(
                        "Fire-Hazard Interlock: Never place batteries in dry/wet bins. Tape terminals and drop at Red Bin / e-waste.",
                        color = Color(0xFF93000A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Telemetry Strip
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Confidence", "${(dec.confidence * 100).toInt()}%", Modifier.weight(1f))
            MetricCard("Latency", "${"%.1f".format(dec.inferenceMs)} ms", Modifier.weight(1f))
            MetricCard("Entropy (H)", "%.2f".format(dec.entropy), Modifier.weight(1f))
        }

        // Multilingual Statutory Labels (from swm2026.yaml)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = EcoColors.Surface,
            border = BorderStroke(1.dp, EcoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SWM 2026 Guidance", style = MaterialTheme.typography.titleMedium)
                if (dec.tipEn.isNotBlank()) Text(dec.tipEn, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                dec.labels.forEach { (lang, label) ->
                    Text("${lang.uppercase()}: $label", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Ask Llama 3.2 1B INT8 Button
        Button(
            onClick = onAskAssistant,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EcoColors.ForestPrimary),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Ask Eco Assistant (Llama 3.2 INT8)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = EcoColors.Surface,
        border = BorderStroke(1.dp, EcoColors.Border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = EcoColors.ForestPrimary)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp)
        }
    }
}
