package com.ecosort.edge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosort.edge.domain.model.ScanRecord
import com.ecosort.edge.domain.model.SocTelemetry
import com.ecosort.edge.domain.model.StreamType
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun HomeScreen(
    history: List<ScanRecord>,
    telemetry: SocTelemetry,
    onScanClick: () -> Unit,
    onSanitaryClick: () -> Unit,
    onAssistantClick: () -> Unit
) {
    var showTelemetry by remember { mutableStateOf(false) }

    val totalScans = history.size
    val divertedCount = history.count { it.stream == StreamType.DRY || it.stream == StreamType.WET }
    val hazardCount = history.count { it.stream == StreamType.SPECIAL_CARE }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(EcoColors.Canvas),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("EcoSort-Edge", style = MaterialTheme.typography.headlineLarge)
                    Text("SWM 2026 On-Device Segregation", style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    shape = CircleShape,
                    color = EcoColors.Surface,
                    border = BorderStroke(1.dp, EcoColors.Border),
                    modifier = Modifier.clickable { showTelemetry = !showTelemetry }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EcoColors.EmeraldBadge)
                        )
                        Text("Offline AI", style = MaterialTheme.typography.labelMedium, color = EcoColors.TextPrimary)
                    }
                }
            }
        }

        // Expandable Live SoC Telemetry
        if (showTelemetry) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EcoColors.Surface,
                    border = BorderStroke(1.dp, EcoColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Arm SoC Telemetry (Live)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Vision INT8: ${if (telemetry.visionReady) "Ready (2.41 MB)" else "Error"} · " +
                                "Llama 3.2 INT8: ${if (telemetry.llamaReady) "Ready" else "Import Required"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "RAM: ${telemetry.usedRamMb} / ${telemetry.totalRamMb} MB · " +
                                "Battery: ${telemetry.batteryPct}% (${telemetry.batteryTempC}°C)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Executive Impact Card (100% Real Scan Counts)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = EcoColors.ForestPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Household Segregation Impact", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Air-Gapped", color = EcoColors.EmeraldBg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ImpactStat("Total Scans", totalScans.toString())
                        ImpactStat("Sorted Clean", divertedCount.toString())
                        ImpactStat("Hazards Caught", hazardCount.toString())
                    }
                }
            }
        }

        // Primary Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EcoColors.ForestAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Scan Waste Item", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(
                        title = "Sanitary Path",
                        subtitle = "Camera-free privacy",
                        icon = Icons.Outlined.PrivacyTip,
                        modifier = Modifier.weight(1f),
                        onClick = onSanitaryClick
                    )
                    QuickActionCard(
                        title = "Eco Assistant",
                        subtitle = "Llama 3.2 INT8",
                        icon = Icons.Outlined.AutoAwesome,
                        modifier = Modifier.weight(1f),
                        onClick = onAssistantClick
                    )
                }
            }
        }

        // Recent Activity
        item {
            Text("Recent Sorting Activity", style = MaterialTheme.typography.titleMedium)
        }

        if (history.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EcoColors.Surface,
                    border = BorderStroke(1.dp, EcoColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No items scanned yet. Tap 'Scan Waste Item' to classify with ExecuTorch INT8.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(history.take(10), key = { it.id }) { rec ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EcoColors.Canvas,
                    border = BorderStroke(1.dp, EcoColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(rec.colorHex))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                rec.predictedClass.replace("_", " ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp
                            )
                            Text(rec.binName, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                        }
                        Text(
                            "${(rec.confidence * 100).toInt()}% · ${rec.inferenceMs.toInt()}ms",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactStat(label: String, value: String) {
    Column {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFA3D0C2), fontSize = 11.sp)
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EcoColors.Surface,
        border = BorderStroke(1.dp, EcoColors.Border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = EcoColors.ForestPrimary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp)
        }
    }
}
