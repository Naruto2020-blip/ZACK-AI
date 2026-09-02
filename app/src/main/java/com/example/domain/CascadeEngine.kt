package com.example.domain

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.model.CascadeExecutionResult
import com.example.data.model.CascadeHop
import com.example.data.model.GeminiModelSpec
import com.example.data.remote.BlobDto
import com.example.data.remote.ContentDto
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiClient
import com.example.data.remote.GenerateContentRequestDto
import com.example.data.remote.GenerationConfigDto
import com.example.data.remote.PartDto
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CascadeEngine(
    private val apiService: GeminiApiService,
    private val repository: ChatRepository
) {
    private val tag = "CascadeEngine"
    private val maxGlobalTimeoutMs = 50_000L

    suspend fun executeCascade(
        history: List<ChatMessageEntity>,
        newPrompt: String,
        primaryModel: GeminiModelSpec = GeminiModelSpec.GEMINI_FLASH_LATEST,
        autoCascadeEnabled: Boolean = true,
        systemInstruction: String? = null,
        temperature: Float = 0.7f,
        attachmentMimeType: String? = null,
        attachmentBase64: String? = null,
        onCascadeHop: ((CascadeHop) -> Unit)? = null
    ): CascadeExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val timedResult = withTimeoutOrNull(maxGlobalTimeoutMs) {
            runCascadeInternal(
                history = history,
                newPrompt = newPrompt,
                primaryModel = primaryModel,
                autoCascadeEnabled = autoCascadeEnabled,
                systemInstruction = systemInstruction,
                temperature = temperature,
                attachmentMimeType = attachmentMimeType,
                attachmentBase64 = attachmentBase64,
                onCascadeHop = onCascadeHop,
                startTime = startTime
            )
        }

        if (timedResult != null) {
            timedResult
        } else {
            val totalLatency = System.currentTimeMillis() - startTime
            Log.w(tag, "Cascade execution timed out after ${totalLatency}ms")
            CascadeExecutionResult(
                content = "⏱️ Tiempo de espera agotado (50s)\n\nEl servidor de Google tardó más de 50 segundos en responder. Por favor, intenta enviar tu mensaje nuevamente o prueba cambiando tu API Key en los Ajustes si la cuota fue excedida.",
                usedModel = primaryModel,
                requestedPrimaryModel = primaryModel,
                wasCascaded = false,
                hops = emptyList(),
                latencyMs = totalLatency,
                isError = true
            )
        }
    }

    private suspend fun runCascadeInternal(
        history: List<ChatMessageEntity>,
        newPrompt: String,
        primaryModel: GeminiModelSpec,
        autoCascadeEnabled: Boolean,
        systemInstruction: String?,
        temperature: Float,
        attachmentMimeType: String?,
        attachmentBase64: String?,
        onCascadeHop: ((CascadeHop) -> Unit)?,
        startTime: Long
    ): CascadeExecutionResult {
        val apiKey = GeminiClient.getApiKey()
        if (!GeminiClient.hasValidApiKey()) {
            Log.w(tag, "No Gemini API Key provided")
            return CascadeExecutionResult(
                content = "⚠️ Clave de API no detectada.\n\nPor favor, verifica la configuración de tu clave de API en Google AI Studio.",
                usedModel = primaryModel,
                requestedPrimaryModel = primaryModel,
                wasCascaded = false,
                hops = emptyList(),
                latencyMs = 0L,
                isError = true
            )
        }
        val hops = mutableListOf<CascadeHop>()

        // Prepare request body with history
        val contents = mutableListOf<ContentDto>()
        
        // Clean history: exclude error messages and ensure alternating user/model roles
        val validHistory = history
            .filter { !it.isError && it.content.isNotBlank() && !it.content.startsWith("⏱️") && !it.content.startsWith("⚠️") }
            .takeLast(8)

        var lastRole: String? = null
        for (msg in validHistory) {
            val role = if (msg.role == "user") "user" else "model"
            if (role != lastRole) {
                contents.add(
                    ContentDto(
                        role = role,
                        parts = listOf(PartDto(text = msg.content))
                    )
                )
                lastRole = role
            }
        }
        
        // Ensure that if the last history message was user, remove it so the new user prompt is the active turn
        if (contents.isNotEmpty() && contents.last().role == "user") {
            contents.removeAt(contents.size - 1)
        }
        
        // Build parts for current prompt
        val currentParts = mutableListOf<PartDto>()
        if (!attachmentBase64.isNullOrBlank() && !attachmentMimeType.isNullOrBlank()) {
            currentParts.add(
                PartDto(
                    inlineData = BlobDto(
                        mimeType = attachmentMimeType,
                        data = attachmentBase64
                    )
                )
            )
        }
        currentParts.add(PartDto(text = newPrompt))

        // Add current prompt
        contents.add(
            ContentDto(
                role = "user",
                parts = currentParts
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
        var isNetworkOffline = false
        var previousModel = primaryModel

        for ((index, model) in modelsToTry.withIndex()) {
            val modelAttemptStartTime = System.currentTimeMillis()
            var requestSuccess = false
            var responseText: String? = null
            var failureReason: String? = null
            var httpCode: Int? = null

            // Try the main model id and any fallback aliases
            val modelEndpoints = (listOf(model.id) + model.fallbackAliases).distinct()

            for (endpoint in modelEndpoints) {
                try {
                    Log.d(tag, "Attempting request with model endpoint: $endpoint")
                    val response = withTimeoutOrNull(15_000L) {
                        apiService.generateContent(
                            model = endpoint,
                            apiKeyQuery = apiKey,
                            request = request
                        )
                    }

                    if (response == null) {
                        failureReason = "Tiempo de espera individual agotado (15s)"
                        continue
                    }

                    httpCode = response.code()

                    // Quick retry on 503 (temporary network spike/service unavailable)
                    val finalResponse = if (httpCode == 503) {
                        Log.w(tag, "Model $endpoint returned 503 (Saturación), retrying once...")
                        kotlinx.coroutines.delay(300L)
                        withTimeoutOrNull(10_000L) {
                            apiService.generateContent(
                                model = endpoint,
                                apiKeyQuery = apiKey,
                                request = request
                            )
                        } ?: response
                    } else response

                    httpCode = finalResponse.code()

                    if (finalResponse.isSuccessful) {
                        val body = finalResponse.body()
                        val text = body?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!text.isNullOrBlank()) {
                            responseText = text
                            requestSuccess = true
                            break
                        } else {
                            failureReason = "Respuesta vacía del modelo"
                        }
                    } else {
                        val errorBody = finalResponse.errorBody()?.string() ?: ""
                        Log.w(tag, "Model $endpoint returned error $httpCode: $errorBody")
                        
                        // If it's a 400, retry once with simplified request (only user prompt)
                        if (httpCode == 400 && (request.systemInstruction != null || contents.size > 1)) {
                            val simpleRequest = GenerateContentRequestDto(
                                contents = listOf(
                                    ContentDto(
                                        role = "user",
                                        parts = currentParts
                                    )
                                )
                            )
                            val retryResponse = withTimeoutOrNull(10_000L) {
                                apiService.generateContent(
                                    model = endpoint,
                                    apiKeyQuery = apiKey,
                                    request = simpleRequest
                                )
                            }
                            if (retryResponse != null && retryResponse.isSuccessful) {
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
                                    else -> "Parámetro o formato no soportado (HTTP 400)"
                                }
                            }
                            httpCode == 403 -> "Acceso denegado o permisos insuficientes (HTTP 403)"
                            else -> "Error de conexión (HTTP $httpCode)"
                        }
                        
                        // If quota is exhausted (429), switch immediately to next model
                        if (httpCode == 429) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Exception calling $endpoint: ${e.message}", e)
                    if (e is UnknownHostException || e is ConnectException || e is NoRouteToHostException) {
                        isNetworkOffline = true
                        failureReason = "Sin conexión a los servidores de Google (Verifica tu conexión a Internet)"
                        break
                    } else if (e is SocketTimeoutException) {
                        failureReason = "Tiempo de espera agotado al conectar con el modelo"
                    } else {
                        failureReason = e.localizedMessage ?: "Error de comunicación de red"
                    }
                }
            }

            // If the network is completely unreachable, stop immediately instead of waiting through the whole cascade
            if (isNetworkOffline) {
                val totalLatency = System.currentTimeMillis() - startTime
                return CascadeExecutionResult(
                    content = "⚠️ Error de conexión a Internet\n\nNo fue posible establecer comunicación con los servidores de Google (generativelanguage.googleapis.com).\n\nPor favor, verifica que tu dispositivo cuente con conexión activa a Internet (WiFi o datos móviles) e intenta nuevamente.",
                    usedModel = primaryModel,
                    requestedPrimaryModel = primaryModel,
                    wasCascaded = false,
                    hops = emptyList(),
                    latencyMs = totalLatency,
                    isError = true
                )
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

                return CascadeExecutionResult(
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

        // If all models in the cascade failed, return clear error result
        val totalLatency = System.currentTimeMillis() - startTime
        val isApiKeyIssue = lastErrorText?.contains("Clave de API", ignoreCase = true) == true

        val finalMessage = if (isApiKeyIssue) {
            "⚠️ Error de Clave de API de Gemini (HTTP 400)\n\nLa clave de API actual no es válida o no está autorizada por Google AI Studio."
        } else {
            "⚠️ No fue posible obtener respuesta de los modelos.\n\nMotivo: ${lastErrorText ?: "Error de comunicación con el servicio"}\n\nPor favor, verifica tu conexión o intenta nuevamente."
        }

        return CascadeExecutionResult(
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
                    apiKeyQuery = apiKey,
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
