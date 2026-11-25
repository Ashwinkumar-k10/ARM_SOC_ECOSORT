package com.ecosort.edge.domain.guard

import android.content.Context
import com.ecosort.edge.domain.model.PolicyDecision
import com.ecosort.edge.domain.model.StreamType
import org.yaml.snakeyaml.Yaml
import kotlin.math.ln

class PolicyGuard(context: Context) {

    private data class StreamConfig(
        val id: String,
        val binName: String,
        val colorHex: Long,
        val action: String,
        val labels: Map<String, String>
    )

    private data class ClassConfig(
        val stream: StreamType,
        val hazardLevel: String,
        val flags: List<String>,
        val tipEn: String,
        val tipKn: String
    )

    private val streams = mutableMapOf<StreamType, StreamConfig>()
    private val classes = mutableMapOf<String, ClassConfig>()

    init {
        val yamlText = context.assets.open("swm2026.yaml").bufferedReader().use { it.readText() }
        val root = Yaml().load<Map<String, Any>>(yamlText)

        @Suppress("UNCHECKED_CAST")
        val rawStreams = root["streams"] as Map<String, Map<String, Any>>
        for ((key, map) in rawStreams) {
            val st = parseStreamType(key)
            val hexStr = (map["color_hex"] as String).removePrefix("#")
            val colorLong = (0xFF000000L or hexStr.toLong(16))
            @Suppress("UNCHECKED_CAST")
            val labels = (map["labels"] as? Map<String, String>) ?: emptyMap()
            streams[st] = StreamConfig(
                id = map["id"] as String,
                binName = map["bin_name"] as String,
                colorHex = colorLong,
                action = map["action"] as String,
                labels = labels
            )
        }

        @Suppress("UNCHECKED_CAST")
        val rawClasses = root["classes"] as Map<String, Map<String, Any>>
        for ((clsName, map) in rawClasses) {
            val st = parseStreamType(map["stream"] as String)
            @Suppress("UNCHECKED_CAST")
            val flags = (map["flags"] as? List<String>) ?: emptyList()
            classes[clsName] = ClassConfig(
                stream = st,
                hazardLevel = map["hazard_level"] as? String ?: "none",
                flags = flags,
                tipEn = map["tip_en"] as? String ?: "",
                tipKn = map["tip_kn"] as? String ?: ""
            )
        }
    }

    fun evaluate(
        predictedClass: String,
        confidence: Float,
        entropy: Float,
        inferenceMs: Float,
        sanitaryButton: Boolean = false
    ): PolicyDecision {
        if (sanitaryButton) {
            val s = streams.getValue(StreamType.SANITARY)
            val c = classes["sanitary_item"]
            return PolicyDecision(
                stream = StreamType.SANITARY,
                streamId = s.id,
                binName = s.binName,
                colorHex = s.colorHex,
                status = "SANITARY_PRIVACY_OVERRIDE",
                hazardLevel = c?.hazardLevel ?: "biohazard",
                action = s.action,
                flags = c?.flags ?: emptyList(),
                labels = s.labels,
                tipEn = c?.tipEn ?: "",
                tipKn = c?.tipKn ?: "",
                predictedClass = "sanitary_item",
                confidence = 1.0f,
                entropy = 0.0f,
                inferenceMs = 0.0f
            )
        }

        // Zero-tolerance battery fire-hazard interlock
        if (predictedClass == "battery" && confidence >= 0.30f) {
            val s = streams.getValue(StreamType.SPECIAL_CARE)
            val c = classes.getValue("battery")
            return PolicyDecision(
                stream = StreamType.SPECIAL_CARE,
                streamId = s.id,
                binName = s.binName,
                colorHex = s.colorHex,
                status = "HAZARD_INTERCEPTED",
                hazardLevel = c.hazardLevel,
                action = s.action,
                flags = c.flags,
                labels = s.labels,
                tipEn = c.tipEn,
                tipKn = c.tipKn,
                predictedClass = predictedClass,
                confidence = confidence,
                entropy = entropy,
                inferenceMs = inferenceMs
            )
        }

        // Shannon Entropy & Confidence OOD gate
        if (entropy > 2.00f || confidence < 0.30f) {
            val s = streams.getValue(StreamType.REJECT)
            return PolicyDecision(
                stream = StreamType.REJECT,
                streamId = s.id,
                binName = s.binName,
                colorHex = s.colorHex,
                status = "AMBIGUITY_HOLD",
                hazardLevel = "unknown",
                action = s.action,
                flags = listOf("manual_inspection_required"),
                labels = s.labels,
                tipEn = "Uncertain object. Please place in inspection tray or try a clearer angle.",
                tipKn = "",
                predictedClass = predictedClass,
                confidence = confidence,
                entropy = entropy,
                inferenceMs = inferenceMs
            )
        }

        val c = classes[predictedClass] ?: return evaluate(
            predictedClass, 0.0f, 9.9f, inferenceMs, sanitaryButton = false
        )
        val s = streams.getValue(c.stream)
        return PolicyDecision(
            stream = c.stream,
            streamId = s.id,
            binName = s.binName,
            colorHex = s.colorHex,
            status = "STATUTORY_ROUTED",
            hazardLevel = c.hazardLevel,
            action = s.action,
            flags = c.flags,
            labels = s.labels,
            tipEn = c.tipEn,
            tipKn = c.tipKn,
            predictedClass = predictedClass,
            confidence = confidence,
            entropy = entropy,
            inferenceMs = inferenceMs
        )
    }

    private fun parseStreamType(raw: String): StreamType = when (raw.lowercase()) {
        "wet" -> StreamType.WET
        "dry" -> StreamType.DRY
        "sanitary" -> StreamType.SANITARY
        "special_care" -> StreamType.SPECIAL_CARE
        else -> StreamType.REJECT
    }

    companion object {
        fun computeEntropy(probs: FloatArray): Float {
            var h = 0.0
            for (p in probs) {
                h -= p * ln(p.toDouble() + 1e-12)
            }
            return h.toFloat()
        }
    }
}
