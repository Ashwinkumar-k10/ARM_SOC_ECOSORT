package com.ecosort.edge.data.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import com.ecosort.edge.domain.model.PolicyDecision
import com.ecosort.edge.domain.model.StreamType
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File

class LlamaAdvisor(private val context: Context) {

    private var llmModule: LlmModule? = null
    private val tokenizerFile: File = VisionClassifier.copyAssetIfNeeded(context, "tokenizer.model")

    fun findModelFile(): File? {
        val internal = File(context.filesDir, MODEL_FILENAME)
        if (internal.exists() && internal.length() > 100_000_000L) return internal

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dlFile = File(downloads, MODEL_FILENAME)
        if (dlFile.exists() && dlFile.canRead() && dlFile.length() > 100_000_000L) return dlFile

        return null
    }

    fun isModelAvailable(): Boolean = findModelFile() != null

    fun importModelFromUri(uri: Uri): Boolean {
        val dest = File(context.filesDir, MODEL_FILENAME)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1024 * 1024)
            }
        } ?: return false
        llmModule = null
        return dest.exists() && dest.length() > 100_000_000L
    }

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (llmModule != null) return true
        val modelFile = findModelFile() ?: return false
        llmModule = LlmModule(modelFile.absolutePath, tokenizerFile.absolutePath, 0.0f)
        return true
    }

    fun generate(
        decision: PolicyDecision,
        userQuestion: String?,
        onToken: (String) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!ensureLoaded()) {
            onError(
                "Llama 3.2 1B INT8 binary ($MODEL_FILENAME, 1.43 GB) is not on this device yet. " +
                    "Tap 'Import llama3_2_1b_int8.pte' above or push it via adb to ${context.filesDir.absolutePath}."
            )
            return
        }

        val prompt = buildPrompt(decision, userQuestion)
        val t0 = SystemClock.elapsedRealtime()
        val module = llmModule ?: return

        try {
            module.generate(
                prompt,
                192,
                object : LlmCallback {
                    override fun onResult(result: String) {
                        val cleaned = result.replace("<|eot_id|>", "")
                        if (cleaned.isNotEmpty()) {
                            onToken(cleaned)
                        }
                    }

                    override fun onStats(stats: String) {
                        // Stats reported by ExecuTorch native runner
                    }
                },
                false
            )
            onComplete(SystemClock.elapsedRealtime() - t0)
        } catch (t: Throwable) {
            onError("ExecuTorch LlmModule error: ${t.message}")
        }
    }

    private fun buildPrompt(decision: PolicyDecision, userQuestion: String?): String {
        val item = decision.predictedClass.replace("_", " ")
        val binLabel = if (decision.stream == StreamType.SPECIAL_CARE) {
            "Red Bin (Special-Care Collection)"
        } else {
            decision.binName
        }

        val task = when {
            !userQuestion.isNullOrBlank() -> userQuestion.trim()
            decision.stream == StreamType.SPECIAL_CARE ->
                "List 2 brief safety steps before placing this battery in the Red Bin (keep dry, tape terminals, never crush)."
            else ->
                "List 2 brief steps to prepare this item for its assigned bin."
        }

        val sys = "You are EcoSort-Edge, a household recycling helper. Reply directly in under 35 words without repeating the prompt."
        val usr = "Item: $item. Assigned Bin: $binLabel. $task"

        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
            "$sys<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n" +
            "$usr<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    companion object {
        const val MODEL_FILENAME = "llama3_2_1b_int8.pte"
    }
}
