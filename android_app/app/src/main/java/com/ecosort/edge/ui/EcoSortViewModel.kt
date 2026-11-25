package com.ecosort.edge.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ecosort.edge.data.ai.LlamaAdvisor
import com.ecosort.edge.data.ai.VisionClassifier
import com.ecosort.edge.data.storage.ScanHistoryStore
import com.ecosort.edge.domain.guard.PolicyGuard
import com.ecosort.edge.domain.model.ActiveScan
import com.ecosort.edge.domain.model.ChatMessage
import com.ecosort.edge.domain.model.ScanRecord
import com.ecosort.edge.domain.model.SocTelemetry
import com.ecosort.edge.domain.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EcoSortViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val vision = VisionClassifier(ctx)
    private val policy = PolicyGuard(ctx)
    private val llama = LlamaAdvisor(ctx)
    private val historyStore = ScanHistoryStore(ctx)

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<ScanRecord>> = _history.asStateFlow()

    private val _activeScan = MutableStateFlow<ActiveScan?>(null)
    val activeScan: StateFlow<ActiveScan?> = _activeScan.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _llamaAvailable = MutableStateFlow(llama.isModelAvailable())
    val llamaAvailable: StateFlow<Boolean> = _llamaAvailable.asStateFlow()

    private val _llamaStatusText = MutableStateFlow(llama.getStatusMessage())
    val llamaStatusText: StateFlow<String> = _llamaStatusText.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _telemetry = MutableStateFlow(historyStore.readTelemetry(vision.isReady, _llamaAvailable.value))
    val telemetry: StateFlow<SocTelemetry> = _telemetry.asStateFlow()

    init {
        // Pre-warm Llama 3.2 1B INT8 in background on launch if already on device
        viewModelScope.launch(Dispatchers.IO) {
            if (llama.isModelAvailable()) {
                llama.ensureLoaded()
                _llamaAvailable.value = llama.isModelAvailable()
                _llamaStatusText.value = llama.getStatusMessage()
                refreshTelemetry()
            }
        }
    }

    fun refreshTelemetry() {
        _llamaAvailable.value = llama.isModelAvailable()
        _llamaStatusText.value = llama.getStatusMessage()
        _telemetry.value = historyStore.readTelemetry(vision.isReady, _llamaAvailable.value)
    }

    fun classifyBitmap(bitmap: Bitmap, fromCameraReticle: Boolean = true, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val pred = vision.classify(bitmap, fromCameraReticle = fromCameraReticle)
            val decision = policy.evaluate(
                predictedClass = pred.predictedClass,
                confidence = pred.confidence,
                entropy = pred.entropy,
                inferenceMs = pred.inferenceMs
            )
            _activeScan.value = ActiveScan(bitmap = pred.croppedBitmap, decision = decision)
            _history.value = historyStore.append(
                ScanRecord(
                    id = System.currentTimeMillis(),
                    timestampMs = System.currentTimeMillis(),
                    predictedClass = decision.predictedClass,
                    binName = decision.binName,
                    stream = decision.stream,
                    colorHex = decision.colorHex,
                    confidence = decision.confidence,
                    inferenceMs = decision.inferenceMs
                )
            )
            _chatMessages.value = emptyList()
            _isProcessing.value = false
            refreshTelemetry()
            launch(Dispatchers.Main) { onDone() }

            // Auto-trigger fast preliminary Llama advice immediately upon capture
            if (llama.isModelAvailable()) {
                askLlama(null)
            }
        }
    }

    fun classifySampleAsset(assetName: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = ctx.assets.open("samples/$assetName").use { BitmapFactory.decodeStream(it) }
            if (bmp != null) {
                classifyBitmap(bmp, fromCameraReticle = false, onDone = onDone)
            }
        }
    }

    fun classifyUri(uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bmp != null) {
                classifyBitmap(bmp, fromCameraReticle = false, onDone = onDone)
            }
        }
    }

    fun triggerSanitaryOverride(onDone: () -> Unit) {
        val decision = policy.evaluate("sanitary_item", 1.0f, 0.0f, 0.0f, sanitaryButton = true)
        _activeScan.value = ActiveScan(bitmap = null, decision = decision)
        _history.value = historyStore.append(
            ScanRecord(
                id = System.currentTimeMillis(),
                timestampMs = System.currentTimeMillis(),
                predictedClass = decision.predictedClass,
                binName = decision.binName,
                stream = StreamType.SANITARY,
                colorHex = decision.colorHex,
                confidence = 1.0f,
                inferenceMs = 0.0f
            )
        )
        _chatMessages.value = emptyList()
        refreshTelemetry()
        onDone()
    }

    fun importLlamaModel(uri: Uri) {
        if (_isImporting.value) return
        _isImporting.value = true
        _llamaStatusText.value = "Copying & verifying 1.43 GB model..."
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, msg) = llama.importModelFromUri(uri)
            _llamaAvailable.value = ok
            _llamaStatusText.value = msg
            _isImporting.value = false
            refreshTelemetry()
        }
    }

    fun askLlama(question: String?) {
        val scan = _activeScan.value ?: return
        if (_isGenerating.value) return

        val userText = question?.trim().takeIf { !it.isNullOrEmpty() }
            ?: "Preliminary safe handling steps for ${formatClassName(scan.decision.predictedClass)}"

        val userMsg = ChatMessage(id = System.currentTimeMillis(), isUser = true, text = userText)
        val botId = userMsg.id + 1
        val botPlaceholder = ChatMessage(id = botId, isUser = false, text = "", isStreaming = true)
        _chatMessages.value = _chatMessages.value + userMsg + botPlaceholder
        _isGenerating.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val acc = StringBuilder()
            llama.generate(
                decision = scan.decision,
                userQuestion = question,
                onToken = { token ->
                    acc.append(token)
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == botId) it.copy(text = acc.toString()) else it
                    }
                },
                onComplete = { ms ->
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == botId) it.copy(isStreaming = false, latencyMs = ms) else it
                    }
                    _llamaStatusText.value = llama.getStatusMessage()
                    _isGenerating.value = false
                },
                onError = { err ->
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == botId) it.copy(text = err, isStreaming = false) else it
                    }
                    _isGenerating.value = false
                }
            )
        }
    }

    companion object {
        fun formatClassName(raw: String): String =
            if (raw == "battery") "Electronic Waste"
            else raw.split("_").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
