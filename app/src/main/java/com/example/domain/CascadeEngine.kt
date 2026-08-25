package com.example.domain

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.model.CascadeExecutionResult
import com.example.data.model.CascadeHop
import com.example.data.model.GeminiModelSpec
import com.example.data.remote.ContentDto
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiClient
import com.example.data.remote.GenerateContentRequestDto
import com.example.data.remote.GenerationConfigDto
import com.example.data.remote.PartDto
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CascadeEngine(
    private val apiService: GeminiApiService,
    private val repository: ChatRepository
) {
    private val tag = "CascadeEngine"

    suspend fun executeCascade(
        history: List<ChatMessageEntity>,
        newPrompt: String,
        primaryModel: GeminiModelSpec = GeminiModelSpec.GEMINI_3_7_FLASH,
        autoCascadeEnabled: Boolean = true,
        systemInstruction: String? = null,
        temperature: Float = 0.7f,
        onCascadeHop: ((CascadeHop) -> Unit)? = null
    ): CascadeExecutionResult = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        if (!GeminiClient.hasValidApiKey()) {
            Log.w(tag, "No Gemini API Key provided")
            return@withContext CascadeExecutionResult(
                content = "No se ha detectado una clave de API de Gemini válida.\n\nPor favor, abre el menú lateral (icono de 3 barras) o toca la tarjeta de aviso para ingresar tu API Key gratuita de Google AI Studio.",
                usedModel = primaryModel,
                requestedPrimaryModel = primaryModel,
                wasCascaded = false,
                hops = emptyList(),
                latencyMs = 0L,
                isError = true
            )
        }
        val hops = mutableListOf<CascadeHop>()
        val startTime = System.currentTimeMillis()

        // Prepare request body with history
        val contents = mutableListOf<ContentDto>()
        
        // Add last turns for context (limit to last 10 messages for token efficiency)
        val recentHistory = history.takeLast(10)
        recentHistory.forEach { msg ->
            if (msg.role == "user" || msg.role == "model") {
                contents.add(
                    ContentDto(
                        role = msg.role,
                        parts = listOf(PartDto(text = msg.content))
                    )
                )
            }
        }
        // Add current prompt
        contents.add(
            ContentDto(
                role = "user",
                parts = listOf(PartDto(text = newPrompt))
            )
        )

    val systemContent = if (!systemInstruction.isNullOrBlank()) {
            ContentDto(parts = listOf(PartDto(text = systemInstruction)))
        } else null

        val genConfig = if (temperature != 0.7f) {
            GenerationConfigDto(temperature = temperature)
        } else null

        val request = GenerateContentRequestDto(
            contents = contents,
            generationConfig = genConfig,
            systemInstruction = systemContent
        )

        // Build list of models to try
        val modelsToTry = if (autoCascadeEnabled) {
            val list = mutableListOf<GeminiModelSpec>()
            list.add(primaryModel)
            GeminiModelSpec.ALL_CASCADE_ORDER.forEach { model ->
                if (model != primaryModel && !list.contains(model)) {
                    list.add(model)
                }
            }
            list
        } else {
            listOf(primaryModel)
        }

        var lastErrorText: String? = null
        var previousModel = primaryModel

        for ((index, model) in modelsToTry.withIndex()) {
            val modelAttemptStartTime = System.currentTimeMillis()
            var requestSuccess = false
            var responseText: String? = null
            var failureReason: String? = null
            var httpCode: Int? = null

            // Try the main model id and any fallback aliases if needed
            val modelEndpoints = listOf(model.id) + model.fallbackAliases

            for (endpoint in modelEndpoints) {
                try {
                    Log.d(tag, "Attempting request with model endpoint: $endpoint")
                    val response = apiService.generateContent(
                        model = endpoint,
                        apiKey = apiKey,
                        request = request
                    )

                    httpCode = response.code()
                    if (response.isSuccessful) {
                        val body = response.body()
                        val text = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!text.isNullOrBlank()) {
                            responseText = text
                            requestSuccess = true
                            break
                        } else {
                            failureReason = "Respuesta vacía del modelo"
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        Log.w(tag, "Model $endpoint returned error $httpCode: $errorBody")
                        
                        // If it's a 400, retry once with simplified request (no system instruction or config)
                        if (httpCode == 400 && (request.systemInstruction != null || request.generationConfig != null)) {
                            Log.d(tag, "Retrying $endpoint with simple contents payload...")
                            val simpleRequest = GenerateContentRequestDto(
                                contents = listOf(
                                    ContentDto(
                                        role = "user",
                                        parts = listOf(PartDto(text = newPrompt))
                                    )
                                )
                            )
                            val retryResponse = apiService.generateContent(
                                model = endpoint,
                                apiKey = apiKey,
                                request = simpleRequest
                            )
                            if (retryResponse.isSuccessful) {
                                val retryText = retryResponse.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                if (!retryText.isNullOrBlank()) {
                                    responseText = retryText
                                    requestSuccess = true
                                    break
                                }
                            }
                        }

                        failureReason = when {
                            httpCode == 429 -> "Cuota diaria agotada (HTTP 429 - Resource Exhausted)"
                            httpCode == 503 -> "Saturación temporal de red (HTTP 503 - Service Unavailable)"
                            httpCode in listOf(500, 502, 504) -> "Error de servidor en nodo de inferencia (HTTP $httpCode)"
                            httpCode == 404 -> "Modelo no encontrado en esta región (HTTP 404)"
                            httpCode == 400 -> {
                                when {
                                    errorBody.contains("API key not valid", ignoreCase = true) || errorBody.contains("API_KEY_INVALID", ignoreCase = true) ->
                                        "Clave de API inválida o expirada (HTTP 400)"
                                    errorBody.contains("User location", ignoreCase = true) ->
                                        "Región geográfica no soportada (HTTP 400)"
                                    else -> "Parámetro o payload no soportado (HTTP 400)"
                                }
                            }
                            httpCode == 403 -> "Acceso denegado o permisos insuficientes (HTTP 403)"
                            else -> "Error de conexión (HTTP $httpCode)"
                        }
                        // If it's a 429 quota exhaustion or 503, immediately switch to backup model
                        if (httpCode == 429 || httpCode == 503) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Exception calling $endpoint: ${e.message}", e)
                    failureReason = e.localizedMessage ?: "Excepción de red"
                }
            }

            val modelLatency = System.currentTimeMillis() - modelAttemptStartTime

            if (requestSuccess && responseText != null) {
                // Success!
                repository.recordModelUsage(
                    model = model,
                    success = true,
                    latencyMs = modelLatency,
                    isQuotaExhausted = false
                )

                val wasCascaded = model != primaryModel || hops.isNotEmpty()
                val totalLatency = System.currentTimeMillis() - startTime

                return@withContext CascadeExecutionResult(
                    content = responseText,
                    usedModel = model,
                    requestedPrimaryModel = primaryModel,
                    wasCascaded = wasCascaded,
                    hops = hops,
                    latencyMs = totalLatency,
                    isError = false
                )
            } else {
                // Mark quota / error in repository
                val isQuotaExhausted = httpCode == 429
                repository.recordModelUsage(
                    model = model,
                    success = false,
                    latencyMs = modelLatency,
                    isQuotaExhausted = isQuotaExhausted,
                    errorReason = failureReason
                )

                lastErrorText = failureReason

                // If there is another model in the cascade, log a hop!
                if (index < modelsToTry.size - 1 && autoCascadeEnabled) {
                    val nextModel = modelsToTry[index + 1]
                    val hop = CascadeHop(
                        fromModel = model,
                        toModel = nextModel,
                        reason = failureReason ?: "Conmutación por disponibilidad",
                        httpCode = httpCode
                    )
                    hops.add(hop)
                    onCascadeHop?.invoke(hop)
                    previousModel = model
                    Log.i(tag, "Cascading from ${model.displayName} to ${nextModel.displayName}. Reason: ${hop.reason}")
                }
            }
        }

        // If all models in the cascade failed, return comprehensive error result
        val totalLatency = System.currentTimeMillis() - startTime
        val isApiKeyIssue = lastErrorText?.contains("Clave de API", ignoreCase = true) == true

        val finalMessage = if (isApiKeyIssue) {
            "⚠️ Error de Clave de API de Gemini (HTTP 400)\n\nLa clave de API actual no es válida o no está autorizada por Google AI Studio.\n\n👉 Por favor, abre el menú lateral (icono ☰) o toca 'Configurar API Key' para ingresar tu clave gratuita de Google AI Studio (https://aistudio.google.com/app/apikey)."
        } else {
            "⚠️ Todos los modelos de la cascada experimentaron un error o cuota agotada.\n\nÚltimo motivo: ${lastErrorText ?: "Error de comunicación con la API de Gemini"}\n\nPor favor, verifica tu conexión o intenta nuevamente en unos instantes."
        }

        return@withContext CascadeExecutionResult(
            content = finalMessage,
            usedModel = previousModel,
            requestedPrimaryModel = primaryModel,
            wasCascaded = hops.isNotEmpty(),
            hops = hops,
            latencyMs = totalLatency,
            isError = true
        )
    }

    suspend fun pingModel(model: GeminiModelSpec): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val apiKey = GeminiClient.getApiKey()
        val startTime = System.currentTimeMillis()
        val request = GenerateContentRequestDto(
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(PartDto(text = "Ping. Responde con 'PONG'"))
                )
            ),
            generationConfig = GenerationConfigDto(maxOutputTokens = 10)
        )

        val endpoints = listOf(model.id) + model.fallbackAliases
        for (ep in endpoints) {
            try {
                val response = apiService.generateContent(
                    model = ep,
                    apiKey = apiKey,
                    request = request
                )
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    repository.recordModelUsage(model, true, latency, false)
                    return@withContext Pair(true, latency)
                } else if (response.code() == 429) {
                    repository.recordModelUsage(model, false, latency, true, "Cuota agotada (429)")
                    return@withContext Pair(false, latency)
                }
            } catch (e: Exception) {
                // Continue to next endpoint
            }
        }
        val latency = System.currentTimeMillis() - startTime
        repository.recordModelUsage(model, false, latency, false, "Sin respuesta")
        return@withContext Pair(false, latency)
    }
}
