package com.ecosort.edge.domain.model

import android.graphics.Bitmap

enum class StreamType {
    WET,
    DRY,
    SANITARY,
    SPECIAL_CARE,
    REJECT
}

data class PolicyDecision(
    val stream: StreamType,
    val streamId: String,
    val binName: String,
    val colorHex: Long,
    val status: String,
    val hazardLevel: String,
    val action: String,
    val flags: List<String>,
    val labels: Map<String, String>,
    val tipEn: String,
    val tipKn: String,
    val predictedClass: String,
    val confidence: Float,
    val entropy: Float,
    val inferenceMs: Float
)

data class ScanRecord(
    val id: Long,
    val timestampMs: Long,
    val predictedClass: String,
    val binName: String,
    val stream: StreamType,
    val colorHex: Long,
    val confidence: Float,
    val inferenceMs: Float
)

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val latencyMs: Long? = null,
    val isStreaming: Boolean = false
)

data class SocTelemetry(
    val usedRamMb: Long,
    val totalRamMb: Long,
    val batteryTempC: Float,
    val batteryPct: Int,
    val visionReady: Boolean,
    val llamaReady: Boolean
)

data class ActiveScan(
    val bitmap: Bitmap?,
    val decision: PolicyDecision
)
