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
import androidx.compose.material.icons.outlined.Send
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
import com.ecosort.edge.domain.model.ChatMessage
import com.ecosort.edge.ui.theme.EcoColors

@Composable
fun AssistantScreen(
    activeScan: ActiveScan?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    llamaAvailable: Boolean,
    onAskLlama: (String?) -> Unit,
    onImportModelUri: (android.net.Uri) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isImporting = true
            onImportModelUri(uri)
            isImporting = false
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
        // Header + Context Pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EcoColors.Surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Eco Assistant", style = MaterialTheme.typography.headlineMedium)
            if (activeScan != null) {
                Surface(
                    shape = CircleShape,
                    color = Color(activeScan.decision.colorHex).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(activeScan.decision.colorHex))
                ) {
                    Text(
                        "Context: ${activeScan.decision.predictedClass.replace("_", " ")} → ${activeScan.decision.binName}",
                        color = EcoColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Text("Scan an item first to ground Llama 3.2 1B INT8.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider(color = EcoColors.Border)

        if (!llamaAvailable) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EcoColors.SurfaceCard,
                border = BorderStroke(1.dp, EcoColors.Border),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("On-Device Llama 3.2 1B INT8 Setup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Place llama3_2_1b_int8.pte (1.43 GB) in Downloads or tap below to import it for 100% offline ExecuTorch LlmModule inference.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColors(containerColor = EcoColors.ForestPrimary)
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isImporting) "Importing..." else "Import llama3_2_1b_int8.pte")
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
                                if (msg.text.isEmpty() && msg.isStreaming) "Thinking on-device..." else msg.text,
                                color = if (msg.isUser) Color.White else EcoColors.TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            if (msg.latencyMs != null) {
                                Text(
                                    "ExecuTorch Llama INT8 · ${msg.latencyMs} ms",
                                    color = EcoColors.TextSecondary,
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
                    "How do I prep this?",
                    "Why this bin?",
                    "Environmental impact?"
                ).forEach { prompt ->
                    Surface(
                        shape = CircleShape,
                        color = EcoColors.Surface,
                        border = BorderStroke(1.dp, EcoColors.Border),
                        modifier = Modifier.clickable(enabled = !isGenerating) { onAskLlama(prompt) }
                    ) {
                        Text(prompt, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        }

        // Input Bar
        Surface(color = EcoColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask about recycling or disposal...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
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
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = EcoColors.ForestPrimary)
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Send")
                }
            }
        }
    }
}
