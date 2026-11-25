package com.ecosort.edge.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecosort.edge.domain.model.ActiveScan
import com.ecosort.edge.domain.model.AppLanguage
import com.ecosort.edge.domain.model.ChatMessage
import com.ecosort.edge.ui.AppLocalization
import com.ecosort.edge.ui.AppStrings
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun AssistantScreen(
    activeScan: ActiveScan?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    llamaAvailable: Boolean,
    llamaStatusText: String,
    isImporting: Boolean,
    language: AppLanguage,
    strings: AppStrings,
    onAskLlama: (String?) -> Unit,
    onImportModelUri: (android.net.Uri) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportModelUri(uri)
        }
    }

    LaunchedEffect(activeScan?.decision?.predictedClass, llamaAvailable) {
        if (activeScan != null && llamaAvailable && messages.isEmpty() && !isGenerating) {
            onAskLlama(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EcoColors.Canvas)
    ) {
        // Header + Context + Model Status Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EcoColors.Surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.ecoAssistant, color = EcoColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Surface(
                    shape = CircleShape,
                    color = if (llamaAvailable) EcoColors.EmeraldBg.copy(alpha = 0.35f) else EcoColors.SurfaceCard,
                    border = BorderStroke(1.dp, if (llamaAvailable) EcoColors.EmeraldBadge else EcoColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (llamaAvailable) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EcoColors.EmeraldBadge, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            if (llamaAvailable) strings.llamaReadyBadge else strings.modelNeededBadge,
                            color = EcoColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(llamaStatusText, color = EcoColors.TextSecondary, fontSize = 12.sp)

            if (activeScan != null) {
                Surface(
                    shape = CircleShape,
                    color = Color(activeScan.decision.colorHex).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(activeScan.decision.colorHex))
                ) {
                    Text(
                        "${AppLocalization.localizedClassName(activeScan.decision.predictedClass, language)} → ${AppLocalization.localizedBinName(activeScan.decision.stream, activeScan.decision.binName, language)}",
                        color = EcoColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Text(strings.scanFirstHint, color = EcoColors.TextSecondary, fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = EcoColors.Border)

        if (!llamaAvailable || isImporting) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EcoColors.SurfaceCard,
                border = BorderStroke(1.dp, EcoColors.Border),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.importCardTitle, color = EcoColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        llamaStatusText,
                        color = EcoColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = EcoColors.ForestPrimary, contentColor = Color.White)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(strings.copyingVerifying, color = Color.White)
                        } else {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.selectModelFile, color = Color.White)
                        }
                    }
                }
            }
        }

        // Chat Feed
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isUser) EcoColors.ForestPrimary else EcoColors.Surface,
                        border = if (msg.isUser) null else BorderStroke(1.dp, EcoColors.Border),
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (msg.text.isEmpty() && msg.isStreaming) strings.thinkingOnDevice else msg.text,
                                color = if (msg.isUser) Color.White else EcoColors.TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            if (msg.latencyMs != null) {
                                Text(
                                    "ExecuTorch Llama INT8 · ${msg.latencyMs} ms",
                                    color = if (msg.isUser) Color.White.copy(alpha = 0.7f) else EcoColors.TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Suggested Prompts
        if (activeScan != null && llamaAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    strings.promptPrep,
                    strings.promptWhyBin,
                    strings.promptImpact
                ).forEach { prompt ->
                    Surface(
                        shape = CircleShape,
                        color = EcoColors.Surface,
                        border = BorderStroke(1.dp, EcoColors.Border),
                        modifier = Modifier.clickable(enabled = !isGenerating) { onAskLlama(prompt) }
                    ) {
                        Text(prompt, color = EcoColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        }

        // High-Contrast Light Input Bar (safe even in System Dark Mode)
        Surface(color = EcoColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(strings.askPlaceholder, color = EcoColors.TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EcoColors.TextPrimary,
                        unfocusedTextColor = EcoColors.TextPrimary,
                        focusedContainerColor = EcoColors.Canvas,
                        unfocusedContainerColor = EcoColors.Canvas,
                        focusedBorderColor = EcoColors.ForestPrimary,
                        unfocusedBorderColor = EcoColors.Border,
                        cursorColor = EcoColors.ForestPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        val q = inputText.trim()
                        if (q.isNotEmpty() && activeScan != null && !isGenerating) {
                            inputText = ""
                            onAskLlama(q)
                        }
                    },
                    enabled = activeScan != null && !isGenerating && inputText.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = EcoColors.ForestPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = EcoColors.Border,
                        disabledContentColor = EcoColors.TextSecondary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                }
            }
        }
    }
}
