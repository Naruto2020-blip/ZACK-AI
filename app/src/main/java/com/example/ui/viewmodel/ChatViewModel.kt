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
import com.example.data.local.ReminderTaskEntity
import com.example.util.FileProcessor
import com.example.util.ProcessedAttachment
import com.example.util.DocumentCleaner
import com.example.util.DocumentSignatureDetector
import kotlinx.coroutines.flow.combine

data class ChatUiState(
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "",
    val messages: List<ChatMessageEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val isProcessingFile: Boolean = false,
    val selectedModel: GeminiModelSpec = GeminiModelSpec.GEMINI_FLASH_LATEST,
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

    private val prefs = application.getSharedPreferences("chat_prefs", Application.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val searchQuery = MutableStateFlow("")

    val filteredSessions: StateFlow<List<ChatSessionEntity>> = combine(sessions, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favoriteMessages: StateFlow<List<ChatMessageEntity>> = repository.favoriteMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allTasks: StateFlow<List<ReminderTaskEntity>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var messagesJob: Job? = null
    private var quotaJob: Job? = null
    private var timerJob: Job? = null

    val personaPrompts = mapOf(
        "Asistente Inteligente" to "Eres un asistente de IA avanzado, servicial, conciso y preciso. Responde siempre de forma clara y estructurada en español sin especialidad.",
        "🔄 Asistente Inteligente" to "Eres un asistente de IA avanzado, servicial, conciso y preciso. Responde siempre de forma clara y estructurada en español sin especialidad.",
        "Abogado" to "Eres un abogado y asesor jurídico experto. Redactas contratos formales, cartas legales, autorizaciones, renuncias, poderes y documentos jurídicos rigurosos. Explicas derechos, obligaciones y normativas en un lenguaje claro, accesible y profesional en español.",
        "⚖️ Abogado" to "Eres un abogado y asesor jurídico experto. Redactas contratos formales, cartas legales, autorizaciones, renuncias, poderes y documentos jurídicos rigurosos. Explicas derechos, obligaciones y normativas en un lenguaje claro, accesible y profesional en español.",
        "Médico / Doctor" to "Eres un médico y especialista en salud con enfoque pedagógico y orientador. Explicas síntomas comunes, consejos de salud preventiva, cuidados generales en el hogar y traduces términos médicos complejos a lenguaje sencillo. Siempre brindas advertencias claras sobre cuándo es indispensable acudir a una consulta o urgencias médicas presenciales.",
        "👨‍⚕️ Médico / Doctor" to "Eres un médico y especialista en salud con enfoque pedagógico y orientador. Explicas síntomas comunes, consejos de salud preventiva, cuidados generales en el hogar y traduces términos médicos complejos a lenguaje sencillo. Siempre brindas advertencias claras sobre cuándo es indispensable acudir a una consulta o urgencias médicas presenciales.",
        "Psicólogo" to "Eres un psicólogo y orientador emocional empático. Brindas apoyo emocional cálido, escucha activa sin juzgar, y proporcionas herramientas y consejos prácticos para el manejo del estrés, ansiedad, gestión de emociones, relaciones interpersonales y bienestar mental.",
        "🧠 Psicólogo" to "Eres un psicólogo y orientador emocional empático. Brindas apoyo emocional cálido, escucha activa sin juzgar, y proporcionas herramientas y consejos prácticos para el manejo del estrés, ansiedad, gestión de emociones, relaciones interpersonales y bienestar mental.",
        "Redactor / Escritor" to "Eres un redactor y escritor profesional de alto nivel. Redactas con impecable ortografía, elocuencia y estructura todo tipo de cartas formales o informales, correos electrónicos de impacto, ensayos, discursos persuasivos, artículos y textos creativos personalizados.",
        "✍️ Redactor / Escritor" to "Eres un redactor y escritor profesional de alto nivel. Redactas con impecable ortografía, elocuencia y estructura todo tipo de cartas formales o informales, correos electrónicos de impacto, ensayos, discursos persuasivos, artículos y textos creativos personalizados.",
        "Profesor / Tutor" to "Eres un profesor y tutor pedagógico paciente y didáctico. Explicas temas difíciles paso a paso mediante ejemplos claros, resuelves dudas académicas, ayudas con tareas escolares y universitarias, elaboras resúmenes y guías para preparar exámenes con éxito.",
        "📚 Profesor / Tutor" to "Eres un profesor y tutor pedagógico paciente y didáctico. Explicas temas difíciles paso a paso mediante ejemplos claros, resuelves dudas académicas, ayudas con tareas escolares y universitarias, elaboras resúmenes y guías para preparar exámenes con éxito."
    )

    private fun getEffectiveSystemInstruction(): String {
        val basePersona = personaPrompts[_uiState.value.systemPersona] ?: ""
        val documentRule = """
        
        REGLA ESTRICTA PARA CARTAS, OFICIOS Y DOCUMENTOS FORMALES:
        Cuando el usuario solicite redactar una carta, oficio, solicitud, renuncia o documento formal (por ejemplo: para el IMAS, bancos, empleadores, instituciones, juzgados, etc.):
        1. Proporciona ÚNICAMENTE la carta formal lista para usar.
        2. NUNCA escribas introducciones o saludos previos como 'Para redactar la carta adecuada...', 'Aquí tienes...', 'Solo debes completar los espacios...'.
        3. NUNCA coloques la solicitud del usuario (ej: 'Créame una carta para el IMAS') como título.
        4. NUNCA incluyas el nombre 'ZACK AI'.
        5. NUNCA agregues secciones de 'Recomendaciones', 'Notas', 'Consejos' o 'Aclaraciones' al final.
        6. Los campos a rellenar deben ser limpios y directos entre corchetes SIN ejemplos ni explicaciones: escribe exactamente [Lugar], [Fecha], [Tu Nombre Completo], [Cédula], [Dirección], [Teléfono], etc. (NUNCA agregues 'ej:' ni explicaciones).
        7. El resultado debe ser directamente la carta limpia, oficial y profesional.
        """.trimIndent()
        return if (basePersona.isNotBlank()) "$basePersona\n$documentRule" else documentRule
    }

    init {
        startDailyResetTimer()
        observeQuotaRecords()
        initDefaultSession()
    }

    private fun initDefaultSession() {
        viewModelScope.launch {
            repository.allSessions.collectLatest { sessionList ->
                if (sessionList.isEmpty()) {
                    val newId = repository.createNewSession("")
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
                    currentSessionTitle = currentSession?.title ?: ""
                )
            }
        }
    }

    fun createNewSession(title: String = "") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title, _uiState.value.systemPersona)
            _uiState.value = _uiState.value.copy(
                currentSessionId = newId,
                messages = emptyList(),
                currentSessionTitle = ""
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            val remaining = sessions.value.filter { it.id != sessionId }
            if (remaining.isNotEmpty()) {
                selectSession(remaining.first().id)
            } else {
                val newId = repository.createNewSession("")
                _uiState.value = _uiState.value.copy(
                    currentSessionId = newId,
                    messages = emptyList(),
                    currentSessionTitle = ""
                )
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAll()
            val newId = repository.createNewSession("")
            _uiState.value = _uiState.value.copy(
                currentSessionId = newId,
                messages = emptyList(),
                currentSessionTitle = ""
            )
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

            // Natural language auto-task / reminder detection
            val lowerPrompt = rawPrompt.lowercase()
            if (lowerPrompt.startsWith("recuérdame") || lowerPrompt.startsWith("recuerdame") ||
                lowerPrompt.startsWith("crear recordatorio") || lowerPrompt.startsWith("recordatorio") ||
                lowerPrompt.startsWith("crear tarea") || lowerPrompt.startsWith("nueva tarea")
            ) {
                val taskTitle = rawPrompt
                    .replace(Regex("^(recuérdame|recuerdame|crear recordatorio|recordatorio:|crear tarea|nueva tarea)\\s*(que|de|:)?\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (taskTitle.isNotBlank()) {
                    repository.insertTask(taskTitle)
                }
            }

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
            val systemInstruction = getEffectiveSystemInstruction()

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

            // Clean letter content if detected
            val cleanContent = if (!result.isError && (
                DocumentSignatureDetector.isSignableDocument(result.content) ||
                result.content.contains("Para redactar", ignoreCase = true) ||
                result.content.contains("debes completar", ignoreCase = true) ||
                result.content.contains("[ej:", ignoreCase = true)
            )) {
                DocumentCleaner.cleanLetterDocument(result.content, effectivePrompt)
            } else {
                result.content
            }

            // Save model response to database
            repository.insertMessage(
                sessionId = sessionId,
                role = "model",
                content = cleanContent,
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

    fun sendRawAudioMessage(audioBase64: String, mimeType: String = "audio/mp4", durationSeconds: Int = 1) {
        if (_uiState.value.isGenerating) return
        val sessionId = _uiState.value.currentSessionId ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                activeCascadeHop = null
            )

            val displayMessage = "🎙️ [Pregunta de Voz Original: ${durationSeconds}s]"

            // Save user audio message to database
            repository.insertMessage(
                sessionId = sessionId,
                role = "user",
                content = displayMessage
            )

            val promptForModel = "Por favor escucha con atención este audio de mi voz original y responde a mi pregunta o solicitud de forma clara, precisa y estructurada en español."
            val history = repository.getMessagesForSessionSync(sessionId)
            val systemInstruction = getEffectiveSystemInstruction()

            val result = cascadeEngine.executeCascade(
                history = history,
                newPrompt = promptForModel,
                primaryModel = _uiState.value.selectedModel,
                autoCascadeEnabled = _uiState.value.isAutoCascadeEnabled,
                systemInstruction = systemInstruction,
                temperature = _uiState.value.temperature,
                attachmentMimeType = mimeType,
                attachmentBase64 = audioBase64,
                onCascadeHop = { hop ->
                    _uiState.value = _uiState.value.copy(activeCascadeHop = hop)
                }
            )

            val cascadeReason = if (result.wasCascaded && result.hops.isNotEmpty()) {
                result.hops.joinToString(" ➔ ") { "${it.fromModel.displayName} (${it.reason})" }
            } else null

            // Clean letter content if detected
            val cleanContent = if (!result.isError && (
                DocumentSignatureDetector.isSignableDocument(result.content) ||
                result.content.contains("Para redactar", ignoreCase = true) ||
                result.content.contains("debes completar", ignoreCase = true) ||
                result.content.contains("[ej:", ignoreCase = true)
            )) {
                DocumentCleaner.cleanLetterDocument(result.content)
            } else {
                result.content
            }

            // Save model response to database
            repository.insertMessage(
                sessionId = sessionId,
                role = "model",
                content = cleanContent,
                modelUsed = result.usedModel.displayName,
                wasCascaded = result.wasCascaded,
                cascadeReason = cascadeReason,
                latencyMs = result.latencyMs,
                isError = result.isError
            )

            _uiState.value = _uiState.value.copy(
                isGenerating = false,
                activeCascadeHop = null
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

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun toggleFavorite(messageId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setMessageFavorite(messageId, isFavorite)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun addTask(title: String, reminderDateTime: Long? = null) {
        viewModelScope.launch {
            repository.insertTask(title, reminderDateTime)
        }
    }

    fun toggleTask(task: ReminderTaskEntity) {
        viewModelScope.launch {
            repository.updateTaskCompleted(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
        }
    }
}
