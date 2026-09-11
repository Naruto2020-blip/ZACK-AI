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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import android.net.Uri
import com.example.data.local.ReminderTaskEntity
import com.example.data.model.ShoppingCategory
import com.example.data.model.ShoppingItem
import com.example.util.FileProcessor
import com.example.util.ProcessedAttachment
import com.example.util.DocumentCleaner
import com.example.util.DocumentSignatureDetector
import com.example.util.RealTimeGroundingService
import com.example.util.ShoppingCategorizer
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
    val attachedFile: ProcessedAttachment? = null,
    val proactiveSuggestion: com.example.util.SmartHabitsManager.SmartSuggestion? = null,
    val detectedCommitment: com.example.util.SmartHabitsManager.DetectedCommitment? = null
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

    private val _voiceGender = MutableStateFlow(prefs.getString("voice_gender", "female") ?: "female")
    val voiceGender: StateFlow<String> = _voiceGender.asStateFlow()

    private val _shoppingItems = MutableStateFlow<List<ShoppingItem>>(loadShoppingList())
    val shoppingList: StateFlow<List<ShoppingItem>> = _shoppingItems.asStateFlow()

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
        "Asistente Inteligente" to "Eres un asistente de IA avanzado, servicial, conciso, inteligente y preciso. Responde siempre de forma clara, directa y bien estructurada en español adaptándote a cualquier consulta general.",
        "🔄 Asistente Inteligente" to "Eres un asistente de IA avanzado, servicial, conciso, inteligente y preciso. Responde siempre de forma clara, directa y bien estructurada en español adaptándote a cualquier consulta general.",
        "Abogado" to "Eres un abogado y asesor jurídico experto. Redactas contratos formales, cartas legales, autorizaciones, renuncias, poderes y documentos jurídicos rigurosos. Explicas derechos, obligaciones y normativas en un lenguaje claro, accesible y profesional en español directamente en tus respuestas.",
        "⚖️ Abogado" to "Eres un abogado y asesor jurídico experto. Redactas contratos formales, cartas legales, autorizaciones, renuncias, poderes y documentos jurídicos rigurosos. Explicas derechos, obligaciones y normativas en un lenguaje claro, accesible y profesional en español directamente en tus respuestas.",
        "Médico / Doctor" to "Eres un médico y especialista en salud con enfoque pedagógico y orientador. Explicas síntomas comunes, consejos de salud preventiva, causas de malestares, cuidados generales en el hogar y traduces términos médicos complejos a lenguaje sencillo. Siempre brindas advertencias claras sobre cuándo es indispensable acudir a una consulta o urgencias médicas presenciales.",
        "👨‍⚕️ Médico / Doctor" to "Eres un médico y especialista en salud con enfoque pedagógico y orientador. Explicas síntomas comunes, consejos de salud preventiva, causas de malestares, cuidados generales en el hogar y traduces términos médicos complejos a lenguaje sencillo. Siempre brindas advertencias claras sobre cuándo es indispensable acudir a una consulta o urgencias médicas presenciales.",
        "Psicólogo" to "Eres un psicólogo y orientador emocional empático. Brindas apoyo emocional cálido, escucha activa sin juzgar, y proporcionas herramientas y consejos prácticos para el manejo del estrés, ansiedad, gestión de emociones, relaciones interpersonales y bienestar mental.",
        "🧠 Psicólogo" to "Eres un psicólogo y orientador emocional empático. Brindas apoyo emocional cálido, escucha activa sin juzgar, y proporcionas herramientas y consejos prácticos para el manejo del estrés, ansiedad, gestión de emociones, relaciones interpersonales y bienestar mental.",
        "Redactor / Escritor" to "Eres un redactor y escritor profesional de alto nivel. Redactas con impecable ortografía, elocuencia y estructura todo tipo de cartas formales o informales, correos electrónicos de impacto, ensayos, discursos persuasivos, artículos y textos creativos personalizados directamente en el chat.",
        "✍️ Redactor / Escritor" to "Eres un redactor y escritor profesional de alto nivel. Redactas con impecable ortografía, elocuencia y estructura todo tipo de cartas formales o informales, correos electrónicos de impacto, ensayos, discursos persuasivos, artículos y textos creativos personalizados directamente en el chat.",
        "Profesor / Tutor" to "Eres un profesor y tutor pedagógico paciente y didáctico. Explicas temas difíciles paso a paso mediante ejemplos claros, resuelves dudas académicas, ayudas con tareas escolares y universitarias, elaboras resúmenes y guías para preparar exámenes con éxito. Si te piden preguntas o cuestionarios, redáctalos con claridad pedagógica directamente en el chat.",
        "📚 Profesor / Tutor" to "Eres un profesor y tutor pedagógico paciente y didáctico. Explicas temas difíciles paso a paso mediante ejemplos claros, resuelves dudas académicas, ayudas con tareas escolares y universitarias, elaboras resúmenes y guías para preparar exámenes con éxito. Si te piden preguntas o cuestionarios, redáctalos con claridad pedagógica directamente en el chat.",
        "Técnico / Soporte" to "Eres un técnico y especialista en soporte tecnológico, celulares (Android, iOS), hardware, cámaras, aplicaciones y computadoras. Diagnosticas fallas de hardware y software (como problemas con la cámara frontal o trasera, batería, pantalla, apps que se detienen, reinicios), explicas soluciones paso a paso de forma clara, directa y práctica, sin rodeos.",
        "🛠️ Técnico / Soporte" to "Eres un técnico y especialista en soporte tecnológico, celulares (Android, iOS), hardware, cámaras, aplicaciones y computadoras. Diagnosticas fallas de hardware y software (como problemas con la cámara frontal o trasera, batería, pantalla, apps que se detienen, reinicios), explicas soluciones paso a paso de forma clara, directa y práctica, sin rodeos.",
        "Asesor Financiero / Económico" to "Eres un asesor financiero y económico práctico. Ayudas con el manejo del dinero, organizas presupuestos familiares, das ideas concretas de ahorro, ayudas con el control de gastos y ofreces consejos de economía diaria para cuidar el presupuesto.",
        "💰 Asesor Financiero / Económico" to "Eres un asesor financiero y económico práctico. Ayudas con el manejo del dinero, organizas presupuestos familiares, das ideas concretas de ahorro, ayudas con el control de gastos y ofreces consejos de economía diaria para cuidar el presupuesto.",
        "💰 Asesor Financiero" to "Eres un asesor financiero y económico práctico. Ayudas con el manejo del dinero, organizas presupuestos familiares, das ideas concretas de ahorro, ayudas con el control de gastos y ofreces consejos de economía diaria para cuidar el presupuesto.",
        "Trabajador / Recursos Humanos" to "Eres un especialista laboral y de recursos humanos enfocado en el trabajador y el empleo. Redactas currículums (CV) completos, cartas de solicitud de empleo, cartas de renuncia formal, permisos laborales y consejos para entrevistas de trabajo, entregando el texto estructurado, formal y listo para usar directamente en el chat.",
        "📄 Trabajador / Recursos Humanos" to "Eres un especialista laboral y de recursos humanos enfocado en el trabajador y el empleo. Redactas currículums (CV) completos, cartas de solicitud de empleo, cartas de renuncia formal, permisos laborales y consejos para entrevistas de trabajo, entregando el texto estructurado, formal y listo para usar directamente en el chat.",
        "📄 Trabajador / RRHH" to "Eres un especialista laboral y de recursos humanos enfocado en el trabajador y el empleo. Redactas currículums (CV) completos, cartas de solicitud de empleo, cartas de renuncia formal, permisos laborales y consejos para entrevistas de trabajo, entregando el texto estructurado, formal y listo para usar directamente en el chat."
    )

    private fun getEffectiveSystemInstruction(): String {
        val currentKey = _uiState.value.systemPersona
        val basePersona = personaPrompts[currentKey]
            ?: personaPrompts.entries.firstOrNull { entry ->
                currentKey.contains(entry.key, ignoreCase = true) || entry.key.contains(currentKey, ignoreCase = true)
            }?.value
            ?: ""
        val now = java.util.Calendar.getInstance()
        val fullDateStr = java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES")).format(now.time)
        val currentYear = now.get(java.util.Calendar.YEAR)

        val documentRule = """
        
        📅 CONTEXTO TEMPORAL Y VIGENCIA ACTUAL OBLIGATORIA:
        - Fecha oficial de hoy: $fullDateStr (Año actual: $currentYear).
        - Toda respuesta sobre personas, gobernantes, presidentes, mandatarios, autoridades, instituciones, leyes, noticias, eventos y situaciones actuales DEBE ser estrictamente verídica, exacta y vigente al año $currentYear.
        - NUNCA proporciones datos obsoletos, mandatos ya concluidos, suposiciones caducadas ni respuestas engañosas. Toda información debe ser real, confirmada y correspondiente a la actualidad.

        🏛️ ACTUALIDAD DE MANDATARIOS Y AUTORIDADES (VIGENTE EN $currentYear):
        - Costa Rica: La actual presidenta de Costa Rica es Laura Fernández Delgado (asumió el 8 de mayo de 2026, período constitucional 2026-2030). Es la segunda mujer en la historia del país en ejercer la presidencia de la República, después de Laura Chinchilla Miranda (2010-2014). Rodrigo Chaves Robles gobernó en el período anterior (2022-2026) y su mandato ya finalizó.
        - México: Claudia Sheinbaum Pardo es la actual presidenta de México (desde el 1 de octubre de 2024, período 2024-2030), primera mujer presidenta del país.
        - Panamá: José Raúl Mulino Quintero es el actual presidente de Panamá (desde el 1 de julio de 2024, período 2024-2029).
        - Uruguay: Yamandú Orsi es el actual presidente de Uruguay (desde el 1 de marzo de 2025, período 2025-2030).
        - Chile: José Antonio Kast es el actual presidente de Chile (desde el 11 de marzo de 2026, período 2026-2030).
        - El Salvador: Nayib Bukele es el presidente de El Salvador (período 2024-2029).
        - Guatemala: Bernardo Arévalo es el presidente de Guatemala (desde enero de 2024, período 2024-2028).
        - Argentina: Javier Milei es el presidente de Argentina (desde diciembre de 2023, período 2023-2027).
        - República Dominicana: Luis Abinader es el presidente (período 2024-2028).

        🏛️ REGLA ESTRICTA PARA PREGUNTAS SOBRE PRESIDENTES, GOBERNANTES Y CARGOS PÚBLICOS (CERO CONFUSIÓN):
        - Cuando el usuario pregunte por "la presidenta de...", "el presidente de...", "quién gobierna...", "quién manda en..." o cualquier cargo público (ya sea formulado en masculino o femenino):
          1. ❌ NUNCA inicies la respuesta diciendo "No tiene presidenta actualmente" ni "No tiene presidente actualmente", ya que suena contradictorio o da la falsa impresión de que el país carece de mandatario.
          2. ✅ Responde SIEMPRE DIRECTAMENTE y en el PRIMER RENGLÓN quién ejerce la presidencia o jefatura de Estado en la actualidad, indicando su nombre oficial completo, cargo y su período constitucional vigente.
          3. Sé conciso, afirmativo y veraz desde las primeras palabras.

        REGLA ESTRICTA PARA CARTAS, OFICIOS Y DOCUMENTOS FORMALES:
        Cuando el usuario solicite redactar una carta, oficio, solicitud, renuncia o documento formal (por ejemplo: para el IMAS, bancos, empleadores, instituciones, juzgados, etc.):
        1. Proporciona ÚNICAMENTE la carta formal lista para usar.
        2. NUNCA escribas introducciones o saludos previos como 'Para redactar la carta adecuada...', 'Aquí tienes...', 'Solo debes completar los espacios...'.
        3. NUNCA coloques la solicitud del usuario (ej: 'Créame una carta para el IMAS') como título.
        4. NUNCA incluyas el nombre 'ZACK AI'.
        5. NUNCA agregues secciones de 'Recomendaciones', 'Notas', 'Consejos' o 'Aclaraciones' al final.
        6. Los campos a rellenar deben ser limpios y directos entre corchetes SIN ejemplos ni explicaciones: escribe exactamente [Lugar], [Fecha], [Tu Nombre Completo], [Cédula], [Dirección], [Teléfono], etc. (NUNCA agregues 'ej:' ni explicaciones).
        7. El resultado debe ser directamente la carta limpia, oficial y profesional.

        🔍 REGLAS OBLIGATORIAS DE BÚSQUEDA Y VERIFICACIÓN DE INFORMACIÓN (NUNCA EQUIVOCARSE):
        1️⃣ PRIORIDAD ABSOLUTA DE LOS DATOS EN TIEMPO REAL DE LA WEB:
        - Si el mensaje incluye una sección "🌐 DATOS Y NOTICIAS EN TIEMPO REAL DESDE LA WEB", esos datos provienen de búsquedas en vivo de hoy mismo. Tienen PRIORIDAD MÁXIMA y ABSOLUTA sobre cualquier conocimiento preentrenado.
        - Utiliza los nombres de gobernantes, noticias, hechos y cotizaciones actuales que aparecen allí para dar la respuesta más precisa y actualizada posible.
        - NUNCA respondas con datos viejos o memorias desactualizadas cuando se trate de la actualidad.

        2️⃣ BUSCAR PRIMERO, RESPONDER DESPUÉS:
        - NUNCA responder solo con lo que "recuerdas" o crees saber: puede estar viejo, incompleto o equivocado.
        - SIEMPRE consultar fuentes reales, oficiales y actualizadas antes de contestar sobre cualquier entidad, horario, teléfono, dirección o servicio.
        - Si no encuentras información clara: decirlo con total honestidad, NO inventar jamás.

        3️⃣ VERIFICAR LOS DATOS CLAVE ANTES DE MOSTRAR LA RESPUESTA:
        Antes de responder, confirma obligatoriamente:
        - Nombre correcto y oficial de la institución o empresa.
        - Teléfono real: prohibido inventar o aproximar números telefónicos.
        - Dirección exacta: prohibido poner direcciones aproximadas o señas dudosas.
        - Horario vigente: los horarios cambian frecuentemente, confirma el horario actual y oficial.

        3️⃣ SI HAY DUDA O NO ENCUENTRAS EL DATO:
        - ❌ NO inventes.
        - ❌ NO pongas "aproximadamente".
        - ❌ NO uses datos viejos o no confirmados.
        - ✅ Di claramente: "No encontré información actualizada de [lo que buscas]. Te recomiendo verificar en su sitio web o llamar directamente."

        4️⃣ CASO ESPECIAL: NOMBRES POPULARES O COLOQUIALES:
        - Si la persona dice el nombre común o popular (ejemplo: "Cembus", "el bus de...", "la clínica de..."):
          1. Busca cuál es el nombre oficial y confirma que sí son lo mismo (ejemplo: "Cembus" -> buscar -> es Cenbus S.A. -> confirmar datos -> responder ✅).
          2. ❌ NO asumir ni confundir con empresas o entidades parecidas.

        📌 EN RESUMEN — LO MÁS IMPORTANTE:
        Antes de responder -> BUSCAR y VERIFICAR. Si no estás 100% seguro -> decirlo, NO inventes. La gente confía en esta información para ir a lugares, llamar, hacer trámites -> no puede estar mal.

        🧠 PREDICCIONES, RECORDATORIOS INTELIGENTES Y TONO:
        - Habla siempre en español claro, sencillo y con tono amable.
        - Aprende de los hábitos del usuario: recuerda qué pide, a qué hora, qué días y con qué frecuencia.
        - Sugiere antes de que te lo pida: si detectas un patrón repetido, avisa amablemente: "Sueles hacer esto los lunes, ¿te ayudo?".
        - Si el usuario menciona una tarea, cita, encargo o compromiso (ej: "tengo que...", "debo...", "tengo reunión mañana"), pregúntale amablemente al final de tu respuesta de forma breve:
          "¿Quieres que te lo recuerde a la hora que acostumbras?"
        - Anticipa necesidades: si se acerca una fecha importante o se agota algo que use seguido, avisa con tiempo.
        - No seas insistente: si el usuario no pide recordatorio o ignora una sugerencia, reduce la frecuencia y no repitas la pregunta.
        """.trimIndent()
        return if (basePersona.isNotBlank()) "$basePersona\n$documentRule" else documentRule
    }

    init {
        startDailyResetTimer()
        observeQuotaRecords()
        initDefaultSession()
        refreshProactiveSuggestion()
    }

    private fun initDefaultSession() {
        viewModelScope.launch {
            if (_uiState.value.currentSessionId != null) return@launch
            try {
                val existing = repository.allSessions.first()
                if (existing.isNotEmpty()) {
                    selectSession(existing.first().id)
                } else {
                    val newId = repository.createNewSession("Nueva Conversación", _uiState.value.systemPersona)
                    selectSession(newId)
                }
            } catch (_: Exception) {
                val newId = repository.createNewSession("Nueva Conversación", _uiState.value.systemPersona)
                selectSession(newId)
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
        if (_uiState.value.currentSessionId == sessionId && messagesJob?.isActive == true) return
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
                val newId = repository.createNewSession("", _uiState.value.systemPersona)
                selectSession(newId)
            }
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAll()
            val newId = repository.createNewSession("", _uiState.value.systemPersona)
            selectSession(newId)
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

        viewModelScope.launch {
            // 1. Asegurar que siempre exista una sesión válida y conectada
            var sessionId = _uiState.value.currentSessionId
            if (sessionId == null) {
                sessionId = repository.createNewSession("Nueva Conversación", _uiState.value.systemPersona)
                selectSession(sessionId)
            }

            // 2. Formato del mensaje para la interfaz
            val displayMessage = if (currentAttached != null) {
                val fileTag = if (currentAttached.isImage) "📷 [Foto/Imagen: ${currentAttached.name}]"
                else "📂 [Documento: ${currentAttached.name}]"
                "$fileTag\n\n$effectivePrompt"
            } else {
                effectivePrompt
            }

            // 3. Confirmación visual INMEDIATA en la pantalla (optimistic UI): el usuario ve su mensaje al instante
            val optimisticMsg = ChatMessageEntity(
                id = System.currentTimeMillis(),
                sessionId = sessionId,
                role = "user",
                content = displayMessage,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                activeCascadeHop = null,
                messages = _uiState.value.messages + optimisticMsg,
                attachedFile = null // Limpiar archivo adjunto de inmediato
            )

            try {
                // Guardar mensaje de usuario en base de datos
                repository.insertMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = displayMessage
                )

                // Detección de recordatorios automáticos
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

                // 🧠 Registrar hábito de uso para predicciones futuras
                com.example.util.SmartHabitsManager.recordUserInteraction(getApplication(), effectivePrompt)

                // 🧠 Detectar compromisos o tareas mencionadas para sugerir recordatorio a la hora habitual
                val detectedCommitment = com.example.util.SmartHabitsManager.detectCommitmentInText(getApplication(), effectivePrompt)
                _uiState.value = _uiState.value.copy(detectedCommitment = detectedCommitment)

                // Preparar contenido completo para el modelo
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

                // 🌐 Búsqueda y verificación de actualidad en tiempo real desde la web
                val liveWebContext = RealTimeGroundingService.fetchRealTimeContext(effectivePrompt)
                val finalPromptWithGrounding = if (!liveWebContext.isNullOrBlank()) {
                    """
                    $liveWebContext

                    --- CONSULTA DEL USUARIO ---
                    $fullPromptForModel
                    """.trimIndent()
                } else {
                    fullPromptForModel
                }

                val history = repository.getMessagesForSessionSync(sessionId)
                val systemInstruction = getEffectiveSystemInstruction()

                val result = cascadeEngine.executeCascade(
                    history = history,
                    newPrompt = finalPromptWithGrounding,
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

                // Limpiar contenido de carta oficial si aplica
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

                // Guardar respuesta del modelo en base de datos
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
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error procesando mensaje", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "No se pudo procesar la respuesta. Intenta de nuevo."
                )
            } finally {
                // Siempre liberar el estado de carga para no quedarse pegado
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    activeCascadeHop = null
                )
            }
        }
    }

    fun sendRawAudioMessage(audioBase64: String, mimeType: String = "audio/mp4", durationSeconds: Int = 1) {
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            var sessionId = _uiState.value.currentSessionId
            if (sessionId == null) {
                sessionId = repository.createNewSession("Nueva Conversación", _uiState.value.systemPersona)
                selectSession(sessionId)
            }

            val displayMessage = "🎙️ [Pregunta de Voz Original: ${durationSeconds}s]"
            val optimisticMsg = ChatMessageEntity(
                id = System.currentTimeMillis(),
                sessionId = sessionId,
                role = "user",
                content = displayMessage,
                timestamp = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                activeCascadeHop = null,
                messages = _uiState.value.messages + optimisticMsg
            )

            try {
                // Guardar audio en base de datos
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
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error procesando audio", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "No se pudo procesar el audio. Intenta de nuevo."
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    activeCascadeHop = null
                )
            }
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

    fun setVoiceGender(gender: String) {
        _voiceGender.value = gender
        prefs.edit().putString("voice_gender", gender).apply()
    }

    fun toggleFavorite(messageId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setMessageFavorite(messageId, isFavorite)
        }
    }

    fun deleteMessage(messageId: Long) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filter { it.id != messageId }
        )
        viewModelScope.launch {
            repository.deleteMessage(messageId)
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

    // =========================================================================
    // 🛒 GESTOR DE LISTA DE COMPRAS POR CATEGORÍAS (Persistente)
    // =========================================================================

    private fun loadShoppingList(): List<ShoppingItem> {
        val jsonString = prefs.getString("shopping_items_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonString)
            val list = mutableListOf<ShoppingItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val name = obj.optString("name", "")
                val quantity = obj.optString("quantity", "")
                val catId = obj.optString("category", ShoppingCategory.OTHERS.id)
                val cat = ShoppingCategory.entries.find { it.id == catId } ?: ShoppingCategory.OTHERS
                val isBought = obj.optBoolean("isBought", false)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                if (name.isNotBlank()) {
                    list.add(ShoppingItem(id, name, quantity, cat, isBought, ts))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveShoppingList(items: List<ShoppingItem>) {
        try {
            val arr = org.json.JSONArray()
            for (item in items) {
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("quantity", item.quantity)
                    put("category", item.category.id)
                    put("isBought", item.isBought)
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString("shopping_items_json", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addShoppingItemsFromInput(rawInput: String) {
        val parsed = ShoppingCategorizer.parseShoppingInput(rawInput)
        if (parsed.isNotEmpty()) {
            val updated = _shoppingItems.value + parsed
            _shoppingItems.value = updated
            saveShoppingList(updated)
        }
    }

    fun toggleShoppingItem(id: String) {
        val updated = _shoppingItems.value.map {
            if (it.id == id) it.copy(isBought = !it.isBought) else it
        }
        _shoppingItems.value = updated
        saveShoppingList(updated)
    }

    fun deleteShoppingItem(id: String) {
        val updated = _shoppingItems.value.filter { it.id != id }
        _shoppingItems.value = updated
        saveShoppingList(updated)
    }

    fun clearBoughtShoppingItems() {
        val updated = _shoppingItems.value.filter { !it.isBought }
        _shoppingItems.value = updated
        saveShoppingList(updated)
    }

    fun clearAllShoppingItems() {
        _shoppingItems.value = emptyList()
        saveShoppingList(emptyList())
    }

    // =========================================================================
    // 🧠 PREDICCIONES Y RECORDATORIOS INTELIGENTES
    // =========================================================================

    fun refreshProactiveSuggestion() {
        val suggestion = com.example.util.SmartHabitsManager.getProactiveSuggestion(getApplication())
        _uiState.value = _uiState.value.copy(proactiveSuggestion = suggestion)
    }

    fun dismissProactiveSuggestion() {
        _uiState.value.proactiveSuggestion?.let {
            com.example.util.SmartHabitsManager.onSuggestionDismissed(getApplication(), it.id)
        }
        _uiState.value = _uiState.value.copy(proactiveSuggestion = null)
    }

    fun acceptProactiveSuggestion() {
        val sug = _uiState.value.proactiveSuggestion ?: return
        com.example.util.SmartHabitsManager.onSuggestionAccepted(getApplication(), sug.id)
        _uiState.value = _uiState.value.copy(proactiveSuggestion = null)
        sendMessage(sug.promptToSend)
    }

    fun dismissDetectedCommitment() {
        _uiState.value = _uiState.value.copy(detectedCommitment = null)
    }

    fun acceptDetectedCommitment() {
        val c = _uiState.value.detectedCommitment ?: return
        addTask(c.title, c.estimatedTimestamp)
        _uiState.value = _uiState.value.copy(
            detectedCommitment = null,
            snackbarMessage = "Recordatorio guardado a las ${c.suggestedHourText}"
        )
    }

    // =========================================================================
    // 📷 RESULTADOS DE VISIÓN POR CÁMARA
    // =========================================================================

    fun sendCameraScanResult(summaryPrompt: String, bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            val jpegBytes = outputStream.toByteArray()
            val base64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
            _uiState.value = _uiState.value.copy(
                attachedFile = ProcessedAttachment(
                    uri = Uri.EMPTY,
                    name = "escaneo_camara.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = jpegBytes.size.toLong(),
                    isImage = true,
                    base64Data = base64
                )
            )
        }
        sendMessage(summaryPrompt)
    }
}
