package com.ecosort.edge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosort.edge.R
import com.ecosort.edge.domain.model.AppLanguage
import com.ecosort.edge.domain.model.ScanRecord
import com.ecosort.edge.domain.model.SocTelemetry
import com.ecosort.edge.domain.model.StreamType
import com.ecosort.edge.ui.AppLocalization
import com.ecosort.edge.ui.AppStrings
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun HomeScreen(
    history: List<ScanRecord>,
    telemetry: SocTelemetry,
    language: AppLanguage,
    strings: AppStrings,
    onSelectLanguage: (AppLanguage) -> Unit,
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF6F5F3),
                        border = BorderStroke(1.dp, EcoColors.Border),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ecosort_logo),
                            contentDescription = "EcoSort Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }
                    Column {
                        Text("EcoSort-Edge", style = MaterialTheme.typography.headlineLarge, fontSize = 21.sp)
                        Text(strings.appSubtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = EcoColors.Surface,
                    border = BorderStroke(1.dp, EcoColors.Border),
                    modifier = Modifier.clickable { showTelemetry = !showTelemetry }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EcoColors.EmeraldBadge)
                        )
                        Text(strings.offlineAi, style = MaterialTheme.typography.labelMedium, color = EcoColors.TextPrimary)
                    }
                }
            }
        }

        // Language Selector Bar (English / தமிழ் / ಕನ್ನಡ / हिन्दी)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = lang == language
                    Surface(
                        shape = CircleShape,
                        color = if (selected) EcoColors.ForestPrimary else EcoColors.Surface,
                        border = BorderStroke(1.dp, if (selected) EcoColors.ForestPrimary else EcoColors.Border),
                        modifier = Modifier.clickable { onSelectLanguage(lang) }
                    ) {
                        Text(
                            lang.nativeName,
                            color = if (selected) Color.White else EcoColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
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
                        Text(strings.telemetryTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${strings.visionLabel}: ${if (telemetry.visionReady) strings.ready else strings.error} · " +
                                "${strings.llamaLabel}: ${if (telemetry.llamaReady) strings.ready.substringBefore(" ") else strings.importRequired}",
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
                        Text(strings.impactTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(strings.airGapped, color = EcoColors.EmeraldBg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ImpactStat(strings.totalScans, totalScans.toString())
                        ImpactStat(strings.sortedClean, divertedCount.toString())
                        ImpactStat(strings.hazardsCaught, hazardCount.toString())
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
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.scanWasteItem, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionCard(
                        title = strings.sanitaryPath,
                        subtitle = strings.cameraFreePrivacy,
                        icon = Icons.Outlined.PrivacyTip,
                        modifier = Modifier.weight(1f),
                        onClick = onSanitaryClick
                    )
                    QuickActionCard(
                        title = strings.ecoAssistant,
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
            Text(strings.recentActivity, style = MaterialTheme.typography.titleMedium)
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
                        strings.noScansYet,
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
                                AppLocalization.localizedClassName(rec.predictedClass, language),
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp
                            )
                            Text(
                                AppLocalization.localizedBinName(rec.stream, rec.binName, language),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp
                            )
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
