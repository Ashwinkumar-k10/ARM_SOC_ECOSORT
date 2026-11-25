package com.ecosort.edge.data.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import com.ecosort.edge.domain.model.AppLanguage
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
        if (internal.exists() && internal.length() > MIN_MODEL_BYTES) return internal

        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            val extFile = File(extDir, MODEL_FILENAME)
            if (extFile.exists() && extFile.canRead() && extFile.length() > MIN_MODEL_BYTES) return extFile
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dlFile = File(downloads, MODEL_FILENAME)
        if (dlFile.exists() && dlFile.canRead() && dlFile.length() > MIN_MODEL_BYTES) return dlFile

        return null
    }

    fun isModelAvailable(): Boolean = findModelFile() != null

    fun isLoadedInMemory(): Boolean = llmModule != null

    fun getStatusMessage(): String {
        val f = findModelFile() ?: return "Not imported yet (select llama3_2_1b_int8.pte, 1.43 GB)"
        val mb = f.length() / (1024L * 1024L)
        return if (llmModule != null) {
            "Verified & Loaded in RAM ($mb MB)"
        } else {
            "File Found ($mb MB) — Warming Up..."
        }
    }

    fun importModelFromUri(uri: Uri): Pair<Boolean, String> {
        val dest = File(context.filesDir, MODEL_FILENAME)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            } ?: return false to "Could not read selected file."

            if (!dest.exists() || dest.length() < MIN_MODEL_BYTES) {
                val mb = dest.length() / (1024L * 1024L)
                dest.delete()
                return false to "Wrong or incomplete file ($mb MB). Expected llama3_2_1b_int8.pte (~1,433 MB)."
            }

            llmModule = null
            val loaded = ensureLoaded()
            if (loaded) {
                val mb = dest.length() / (1024L * 1024L)
                true to "Imported & Verified ($mb MB)!"
            } else {
                false to "Copied file, but ExecuTorch LlmModule failed to load it."
            }
        } catch (t: Throwable) {
            false to "Import failed: ${t.message}"
        }
    }

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (llmModule != null) return true
        val modelFile = findModelFile() ?: return false
        return try {
            llmModule = LlmModule(modelFile.absolutePath, tokenizerFile.absolutePath, 0.0f)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun generate(
        decision: PolicyDecision,
        userQuestion: String?,
        language: AppLanguage = AppLanguage.EN,
        onToken: (String) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!ensureLoaded()) {
            onError("Llama 3.2 1B INT8 ($MODEL_FILENAME) is not imported yet. Open the Assistant tab to import it.")
            return
        }

        val prompt = buildPrompt(decision, userQuestion, language)
        val t0 = SystemClock.elapsedRealtime()
        val module = llmModule ?: return

        try {
            module.generate(
                prompt,
                72,
                object : LlmCallback {
                    override fun onResult(result: String) {
                        val cleaned = result.replace("<|eot_id|>", "")
                        if (cleaned.isNotEmpty()) {
                            onToken(cleaned)
                        }
                    }

                    override fun onStats(stats: String) {}
                },
                false
            )
            onComplete(SystemClock.elapsedRealtime() - t0)
        } catch (t: Throwable) {
            onError("ExecuTorch LlmModule error: ${t.message}")
        }
    }

    private fun buildPrompt(decision: PolicyDecision, userQuestion: String?, language: AppLanguage): String {
        val item = if (decision.predictedClass == "battery") "electronic waste battery" else decision.predictedClass.replace("_", " ")
        val binLabel = if (decision.stream == StreamType.SPECIAL_CARE) {
            "Red Bin (Special-Care Collection)"
        } else {
            decision.binName
        }

        val task = when {
            !userQuestion.isNullOrBlank() -> userQuestion.trim()
            decision.stream == StreamType.SPECIAL_CARE ->
                "List 2 brief safety steps before placing this item in the Red Bin (keep dry, tape terminals, never crush)."
            else ->
                "List 2 brief steps to prepare this item for its assigned bin."
        }

        val langInstruction = when (language) {
            AppLanguage.EN -> "Reply in English."
            AppLanguage.TA -> "Reply in simple Tamil (தமிழ்)."
            AppLanguage.KN -> "Reply in simple Kannada (ಕನ್ನಡ)."
            AppLanguage.HI -> "Reply in simple Hindi (हिन्दी)."
        }

        val sys = "You are EcoSort-Edge, a household recycling helper. $langInstruction Reply directly in under 35 words without repeating the prompt."
        val usr = "Item: $item. Assigned Bin: $binLabel. $task"

        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
            "$sys<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n" +
            "$usr<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    companion object {
        const val MODEL_FILENAME = "llama3_2_1b_int8.pte"
        private const val MIN_MODEL_BYTES = 1_000_000_000L
    }
}
