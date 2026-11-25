package com.ecosort.edge.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
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
import com.ecosort.edge.domain.model.AppLanguage
import com.ecosort.edge.domain.model.ChatMessage
import com.ecosort.edge.domain.model.StreamType
import com.ecosort.edge.ui.AppLocalization
import com.ecosort.edge.ui.AppStrings
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun DetailScreen(
    activeScan: ActiveScan?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    llamaAvailable: Boolean,
    llamaStatusText: String,
    language: AppLanguage,
    strings: AppStrings,
    onBack: () -> Unit,
    onAskAssistant: () -> Unit
) {
    if (activeScan == null) {
        Box(modifier = Modifier.fillMaxSize().background(EcoColors.Canvas), contentAlignment = Alignment.Center) {
            Text(strings.noItemSelected, color = EcoColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val dec = activeScan.decision
    val binColor = Color(dec.colorHex)
    val cleanObjName = AppLocalization.localizedClassName(dec.predictedClass, language)
    val localBinName = AppLocalization.localizedBinName(dec.stream, dec.binName, language)
    val latestBotMsg = messages.lastOrNull { !it.isUser }

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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = EcoColors.TextPrimary)
            }
            Text(strings.sortingResult, color = EcoColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        }

        // Bold Colored Bin Card + Clear Object Name (Apple-Minimalist Visual)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = binColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Bin",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cleanObjName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        localBinName,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${(dec.confidence * 100).toInt()}% ${strings.confidenceLabel} · ${"%.1f".format(dec.inferenceMs)} ms",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Cropped Object Preview (what Vision INT8 classified)
        if (activeScan.bitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    bitmap = activeScan.bitmap.asImageBitmap(),
                    contentDescription = cleanObjName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.reticleCrop, color = EcoColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "${strings.statusLabel}: ${dec.status.replace("_", " ")} · H = ${"%.2f".format(dec.entropy)}",
                        color = EcoColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    val tip = if (language == AppLanguage.KN && dec.tipKn.isNotBlank()) dec.tipKn else dec.tipEn
                    if (tip.isNotBlank()) {
                        Text(tip, color = EcoColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EcoColors.Surface,
                border = BorderStroke(1.dp, EcoColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    strings.sanitaryPrivacyMsg,
                    color = EcoColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Fire-Hazard Interlock Banner
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
                        strings.fireHazardBanner,
                        color = Color(0xFF93000A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Instant On-Device Llama 3.2 1B INT8 Preliminary Steps Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EcoColors.Surface,
            border = BorderStroke(1.dp, EcoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = EcoColors.ForestPrimary, modifier = Modifier.size(18.dp))
                        Text(strings.llamaStepsTitle, color = EcoColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    if (latestBotMsg?.latencyMs != null) {
                        Text("${latestBotMsg.latencyMs} ms", color = EcoColors.TextSecondary, fontSize = 11.sp)
                    }
                }

                when {
                    !llamaAvailable -> Text(
                        "${strings.llamaNotImported} ($llamaStatusText)",
                        color = EcoColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    isGenerating && (latestBotMsg == null || latestBotMsg.text.isEmpty()) -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EcoColors.ForestPrimary)
                        Text(strings.generatingSteps, color = EcoColors.TextSecondary, fontSize = 13.sp)
                    }
                    latestBotMsg != null -> Text(
                        latestBotMsg.text,
                        color = EcoColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Multilingual Labels (from swm2026.yaml)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = EcoColors.Canvas,
            border = BorderStroke(1.dp, EcoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.statutoryLabelsTitle, color = EcoColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                dec.labels.forEach { (lang, label) ->
                    Text("${lang.uppercase()}: $label", color = EcoColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        // Ask Follow-Up in Assistant
        Button(
            onClick = onAskAssistant,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EcoColors.ForestPrimary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(
                if (llamaAvailable) strings.askFollowUp else strings.importLlamaBtn,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
