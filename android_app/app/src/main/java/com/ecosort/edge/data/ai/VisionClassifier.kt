package com.ecosort.edge.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.ecosort.edge.domain.guard.PolicyGuard
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp

data class VisionPrediction(
    val predictedClass: String,
    val confidence: Float,
    val entropy: Float,
    val inferenceMs: Float,
    val probabilities: FloatArray
)

class VisionClassifier(context: Context) {

    private val module: Module

    val isReady: Boolean
        get() = true

    init {
        val modelFile = copyAssetIfNeeded(context, "waste_classifier_int8.pte")
        module = Module.load(modelFile.absolutePath)
    }

    fun classify(bitmap: Bitmap): VisionPrediction {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatBuffer = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val planeSize = INPUT_SIZE * INPUT_SIZE

        for (i in 0 until planeSize) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 255.0f
            val g = ((px shr 8) and 0xFF) / 255.0f
            val b = (px and 0xFF) / 255.0f
            floatBuffer[i] = (r - MEAN[0]) / STD[0]
            floatBuffer[planeSize + i] = (g - MEAN[1]) / STD[1]
            floatBuffer[2 * planeSize + i] = (b - MEAN[2]) / STD[2]
        }

        val inputTensor = Tensor.fromBlob(floatBuffer, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))

        val t0 = SystemClock.elapsedRealtimeNanos()
        val outputs = module.forward(EValue.from(inputTensor))
        val inferMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0f

        val logits = outputs[0].toTensor().dataAsFloatArray
        val probs = softmax(logits)

        var bestIdx = 0
        for (i in 1 until probs.size) {
            if (probs[i] > probs[bestIdx]) bestIdx = i
        }
        val entropy = PolicyGuard.computeEntropy(probs)

        return VisionPrediction(
            predictedClass = CLASSES[bestIdx],
            confidence = probs[bestIdx],
            entropy = entropy,
            inferenceMs = inferMs,
            probabilities = probs
        )
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val v = exp((logits[i] - maxLogit).toDouble())
            exps[i] = v.toFloat()
            sum += v
        }
        for (i in exps.indices) {
            exps[i] = (exps[i] / sum).toFloat()
        }
        return exps
    }

    companion object {
        private const val INPUT_SIZE = 256
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        val CLASSES = listOf(
            "battery",
            "cardboard",
            "food_waste",
            "glass_bottle",
            "metal_can",
            "multi_layer_packaging",
            "paper",
            "plastic_bottle",
            "vegetable_scrap"
        )

        fun copyAssetIfNeeded(context: Context, assetName: String): File {
            val outFile = File(context.filesDir, assetName)
            if (outFile.exists() && outFile.length() > 0L) return outFile
            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return outFile
        }
    }
}
