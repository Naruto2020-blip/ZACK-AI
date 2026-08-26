package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ModelQuotaRecordEntity
import com.example.data.model.CascadeHop
import com.example.data.model.GeminiModelSpec
import com.example.data.model.ModelHealthStatus
import com.example.data.model.ModelRuntimeStatus
import com.example.data.model.QuotaResetHelper
import com.example.data.remote.GeminiClient
import com.example.data.repository.ChatRepository
import com.example.domain.CascadeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import android.net.Uri
import com.example.util.FileProcessor
import com.example.util.ProcessedAttachment

data class ChatUiState(
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "Nueva Conversación",
    val messages: List<ChatMessageEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val isProcessingFile: Boolean = false,
    val selectedModel: GeminiModelSpec = GeminiModelSpec.GEMINI_3_7_FLASH,
    val isAutoCascadeEnabled: Boolean = true,
    val activeCascadeHop: CascadeHop? = null,
    val modelRuntimeStatuses: List<ModelRuntimeStatus> = emptyList(),
    val timeUntilUtcReset: String = "--:--:--",
    val systemPersona: String = "Asistente Inteligente",
    val temperature: Float = 0.7f,
    val isRunningDiagnostics: Boolean = false,
    val snackbarMessage: String? = null,
    val isApiKeyConfigured: Boolean = false,
    val currentApiKey: String = "",
    val attachedFile: ProcessedAttachment? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatDao())
    private val cascadeEngine = CascadeEngine(GeminiClient.service, repository)

    private val _uiState = MutableStateFlow(
        ChatUiState(
            isApiKeyConfigured = GeminiClient.hasValidApiKey(application),
            currentApiKey = GeminiClient.getStoredApiKey(application)
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var messagesJob: Job? = null
    private var quotaJob: Job? = null
    private var timerJob: Job? = null

    val personaPrompts = mapOf(
        "Asistente Inteligente" to "Eres un asistente de IA avanzado, servicial, conciso y preciso. Responde siempre de forma clara y estructurada en español.",
        "Programador Experto" to "Eres un ingeniero de software senior y arquitecto de código. Proporciona soluciones limpias, código idiomático bien comentado, explicaciones de complejidad y mejores prácticas de ingeniería.",
        "Razonamiento & Análisis" to "Eres un especialista en razonamiento analítico, pensamiento crítico y resolución lógica de problemas. Desglosa los temas paso a paso con rigor.",
        "Redactor Creativo" to "Eres un escritor creativo y experto en comunicación persuasiva. Redacta contenido cautivador, original y bien estilizado."
    )

    init {
        startDailyResetTimer()
        observeQuotaRecords()
        initDefaultSession()
    }

    private fun initDefaultSession() {
        viewModelScope.launch {
            repository.allSessions.collectLatest { sessionList ->
                if (sessionList.isEmpty()) {
                    val newId = repository.createNewSession("Conversación Principal")
                    selectSession(newId)
                } else if (_uiState.value.currentSessionId == null) {
                    selectSession(sessionList.first().id)
                }
            }
        }
    }

    private fun startDailyResetTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val (_, timeStr) = QuotaResetHelper.getTimeUntilNextUtcReset()
                _uiState.value = _uiState.value.copy(timeUntilUtcReset = timeStr)
                delay(1000L)
            }
        }
    }

    private fun observeQuotaRecords() {
        quotaJob?.cancel()
        quotaJob = viewModelScope.launch {
            repository.getQuotaRecords().collectLatest { records ->
                val recordMap = records.associateBy { it.modelId }
                val statuses = GeminiModelSpec.ALL_CASCADE_ORDER.map { spec ->
                    val record = recordMap[spec.id]
                    val health = when {
                        record?.isQuotaExhausted == true -> ModelHealthStatus.DAILY_QUOTA_EXHAUSTED
                        record != null && record.lastFailureReason?.contains("503") == true -> ModelHealthStatus.OVERLOADED
                        record != null && record.lastFailureReason != null -> ModelHealthStatus.ERROR
                        spec == _uiState.value.selectedModel -> ModelHealthStatus.AVAILABLE
                        else -> ModelHealthStatus.STANDBY
                    }
                    ModelRuntimeStatus(
                        spec = spec,
                        status = health,
                        callsToday = record?.requestCount ?: 0,
                        successfulCalls = if (record != null && !record.isQuotaExhausted) record.requestCount else 0,
                        lastLatencyMs = record?.lastLatencyMs ?: 0L,
                        lastErrorMessage = record?.lastFailureReason,
                        lastActiveTimestamp = record?.updatedAt ?: 0L
                    )
                }
                _uiState.value = _uiState.value.copy(modelRuntimeStatuses = statuses)
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (_uiState.value.currentSessionId == sessionId) return
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId)

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessagesForSession(sessionId).collectLatest { msgs ->
                val currentSession = sessions.value.find { it.id == sessionId }
                _uiState.value = _uiState.value.copy(
                    messages = msgs,
                    currentSessionTitle = currentSession?.title ?: "Conversación"
                )
            }
        }
    }

    fun createNewSession(title: String = "Nueva Conversación") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title, _uiState.value.systemPersona)
            selectSession(newId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            val remaining = sessions.value.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                selectSession(remaining.first().id)
            } else {
                createNewSession()
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAll()
            createNewSession("Conversación Principal")
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun setSelectedModel(model: GeminiModelSpec) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }

    fun toggleAutoCascade(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoCascadeEnabled = enabled)
    }

    fun setSystemPersona(persona: String) {
        _uiState.value = _uiState.value.copy(systemPersona = persona)
        val currentSessionId = _uiState.value.currentSessionId
        if (currentSessionId != null) {
            viewModelScope.launch {
                repository.updateSessionPersona(currentSessionId, persona)
            }
        }
    }

    fun setTemperature(temp: Float) {
        _uiState.value = _uiState.value.copy(temperature = temp)
    }

    fun attachFileUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingFile = true)
            try {
                val processed = FileProcessor.processUri(getApplication(), uri)
                _uiState.value = _uiState.value.copy(
                    attachedFile = processed,
                    isProcessingFile = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingFile = false,
                    snackbarMessage = "Error al procesar archivo: ${e.localizedMessage}"
                )
            }
        }
    }

    fun removeAttachedFile() {
        _uiState.value = _uiState.value.copy(attachedFile = null)
    }

    fun sendMessage(prompt: String, customActionPrefix: String? = null) {
        val currentAttached = _uiState.value.attachedFile
        val rawPrompt = prompt.trim()
        
        val effectivePrompt = when {
            rawPrompt.isNotBlank() && customActionPrefix != null -> "$customActionPrefix\n\n$rawPrompt"
            rawPrompt.isNotBlank() -> rawPrompt
            currentAttached != null && customActionPrefix != null -> customActionPrefix
            currentAttached != null -> "Por favor analiza en detalle el contenido de este archivo adjunto (${currentAttached.name}) y proporciona un desglose estructurado."
            else -> return
        }

        if (_uiState.value.isGenerating) return

        val sessionId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                activeCascadeHop = null
            )

            // Format displayed user message
            val displayMessage = if (currentAttached != null) {
                val fileTag = if (currentAttached.isImage) "📷 [Foto/Imagen: ${currentAttached.name}]"
                else "📂 [Documento: ${currentAttached.name}]"
                "$fileTag\n\n$effectivePrompt"
            } else {
                effectivePrompt
            }

            // Save user message to database
            repository.insertMessage(
                sessionId = sessionId,
                role = "user",
                content = displayMessage
            )

            // Prepare prompt content: if text was extracted from docx/txt/pdf, append it directly into the prompt
            val fullPromptForModel = if (currentAttached != null && !currentAttached.extractedText.isNullOrBlank()) {
                """
                [DOCUMENTO ADJUNTO: ${currentAttached.name}]
                --- CONTENIDO EXTRAÍDO DEL DOCUMENTO ---
                ${currentAttached.extractedText}
                --- FIN DEL CONTENIDO ---

                SOLICITUD DEL USUARIO:
                $effectivePrompt
                """.trimIndent()
            } else {
                effectivePrompt
            }

            val history = repository.getMessagesForSessionSync(sessionId)
            val systemInstruction = personaPrompts[_uiState.value.systemPersona]

            val result = cascadeEngine.executeCascade(
                history = history,
                newPrompt = fullPromptForModel,
                primaryModel = _uiState.value.selectedModel,
                autoCascadeEnabled = _uiState.value.isAutoCascadeEnabled,
                systemInstruction = systemInstruction,
                temperature = _uiState.value.temperature,
                attachmentMimeType = currentAttached?.mimeType,
                attachmentBase64 = currentAttached?.base64Data,
                onCascadeHop = { hop ->
                    _uiState.value = _uiState.value.copy(activeCascadeHop = hop)
                }
            )

            val cascadeReason = if (result.wasCascaded && result.hops.isNotEmpty()) {
                result.hops.joinToString(" ➔ ") { "${it.fromModel.displayName} (${it.reason})" }
            } else null

            // Save model response to database
            repository.insertMessage(
                sessionId = sessionId,
                role = "model",
                content = result.content,
                modelUsed = result.usedModel.displayName,
                wasCascaded = result.wasCascaded,
                cascadeReason = cascadeReason,
                latencyMs = result.latencyMs,
                isError = result.isError
            )

            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                activeCascadeHop = null,
                attachedFile = null // Clear attachment after successful message
            )
        }
    }

    fun regenerateLastMessage() {
        val sessionId = _uiState.value.currentSessionId ?: return
        val currentMsgs = _uiState.value.messages
        val lastUserMsg = currentMsgs.findLast { it.role == "user" } ?: return

        sendMessage(lastUserMsg.content)
    }

    fun runDiagnostics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunningDiagnostics = true)
            var countSuccess = 0

            GeminiModelSpec.ALL_CASCADE_ORDER.forEach { spec ->
                val (success, _) = cascadeEngine.pingModel(spec)
                if (success) countSuccess++
                delay(300L)
            }

            _uiState.value = _uiState.value.copy(
                isRunningDiagnostics = false,
                snackbarMessage = "Diagnóstico completado: $countSuccess/${GeminiModelSpec.ALL_CASCADE_ORDER.size} modelos operativos"
            )
        }
    }

    fun resetDailyQuotasManual() {
        viewModelScope.launch {
            repository.resetAllDailyQuotas()
            _uiState.value = _uiState.value.copy(snackbarMessage = "Límites diarios restablecidos con éxito.")
        }
    }

    fun saveApiKey(newKey: String) {
        val trimmed = newKey.trim()
        GeminiClient.saveCustomApiKey(getApplication(), trimmed)
        val isValid = GeminiClient.hasValidApiKey(getApplication())
        _uiState.value = _uiState.value.copy(
            isApiKeyConfigured = isValid,
            currentApiKey = trimmed,
            snackbarMessage = if (isValid) "API Key de Gemini guardada correctamente" else "API Key eliminada o vacía"
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
