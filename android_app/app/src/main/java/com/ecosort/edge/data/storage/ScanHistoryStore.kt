package com.ecosort.edge.data.storage

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ecosort.edge.domain.model.ScanRecord
import com.ecosort.edge.domain.model.SocTelemetry
import com.ecosort.edge.domain.model.StreamType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanHistoryStore(private val context: Context) {

    private val file = File(context.filesDir, "scan_history.json")

    fun load(): List<ScanRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ScanRecord(
                            id = o.getLong("id"),
                            timestampMs = o.getLong("timestampMs"),
                            predictedClass = o.getString("predictedClass"),
                            binName = o.getString("binName"),
                            stream = StreamType.valueOf(o.getString("stream")),
                            colorHex = o.getLong("colorHex"),
                            confidence = o.getDouble("confidence").toFloat(),
                            inferenceMs = o.getDouble("inferenceMs").toFloat()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(record: ScanRecord): List<ScanRecord> {
        val updated = (listOf(record) + load()).take(50)
        val arr = JSONArray()
        for (r in updated) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("timestampMs", r.timestampMs)
                    put("predictedClass", r.predictedClass)
                    put("binName", r.binName)
                    put("stream", r.stream.name)
                    put("colorHex", r.colorHex)
                    put("confidence", r.confidence.toDouble())
                    put("inferenceMs", r.inferenceMs.toDouble())
                }
            )
        }
        file.writeText(arr.toString())
        return updated
    }

    fun readTelemetry(visionReady: Boolean, llamaReady: Boolean): SocTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / (1024L * 1024L)
        val availMb = mi.availMem / (1024L * 1024L)

        val bmIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = bmIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val level = bmIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bmIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        return SocTelemetry(
            usedRamMb = totalMb - availMb,
            totalRamMb = totalMb,
            batteryTempC = tempTenths / 10.0f,
            batteryPct = pct,
            visionReady = visionReady,
            llamaReady = llamaReady
        )
    }
}
