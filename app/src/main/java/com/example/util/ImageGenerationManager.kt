package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.remote.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeneratedAiImage(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val bitmap: Bitmap,
    val aspectRatio: String = "1:1",
    val modelUsed: String = "Gemini Flash Image",
    val timestamp: Long = System.currentTimeMillis()
)

object ImageGenerationManager {
    private const val TAG = "ImageGenManager"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Modelos oficiales y verificados de Gemini para generación multimodal de imágenes (API v1beta)
    private val VALID_IMAGE_MODELS = listOf(
        "gemini-2.5-flash-image",
        "gemini-3.1-flash-image-preview",
        "gemini-3-pro-image-preview"
    )

    /**
     * Genera una imagen con IA a partir de la descripción en español del usuario.
     * Sigue fielmente la instrucción, colores, cantidades, formas y estilos solicitados.
     */
    suspend fun generateImage(
        userPrompt: String,
        aspectRatio: String = "1:1",
        customApiKey: String? = null,
        context: android.content.Context? = null
    ): Result<GeneratedAiImage> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            context != null -> GeminiClient.getStoredApiKey(context)
            else -> GeminiClient.getApiKey()
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No hay clave de API de Gemini configurada. Por favor verifica los Ajustes.")
            )
        }

        // Construir la petición asegurando máxima fidelidad a la descripción en español
        val promptClean = userPrompt.trim()
        val engineeredPrompt = buildString {
            append(promptClean)
            append(". Produce this exact image adhering strictly to the user's description. Respect all specified colors, shapes, quantities, and aesthetic style faithfully. Clean, crisp, high-resolution, no watermarks, no unwanted text or borders.")
        }

        var lastError: String? = null

        // Iterar únicamente sobre los modelos de imagen válidos de Gemini
        for (model in VALID_IMAGE_MODELS) {
            try {
                Log.d(TAG, "Attempting image generation with valid model: $model")
                val result = callGeminiImageEndpoint(model, engineeredPrompt, aspectRatio, apiKey)
                if (result != null) {
                    val aiImage = GeneratedAiImage(
                        prompt = promptClean,
                        bitmap = result,
                        aspectRatio = aspectRatio,
                        modelUsed = model
                    )
                    return@withContext Result.success(aiImage)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed with model $model: ${e.message}")
                lastError = e.message

                // Si fue un límite temporal de tasa por minuto (429 / RESOURCE_EXHAUSTED), esperar 2.5s e intentar un reintento
                if (e.message?.contains("429", ignoreCase = true) == true ||
                    e.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
                    e.message?.contains("quota", ignoreCase = true) == true) {
                    try {
                        Log.d(TAG, "Rate limit hit, waiting 2.5s before retry on $model...")
                        kotlinx.coroutines.delay(2500)
                        val retryResult = callGeminiImageEndpoint(model, engineeredPrompt, aspectRatio, apiKey)
                        if (retryResult != null) {
                            val aiImage = GeneratedAiImage(
                                prompt = promptClean,
                                bitmap = retryResult,
                                aspectRatio = aspectRatio,
                                modelUsed = model
                            )
                            return@withContext Result.success(aiImage)
                        }
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "Retry on $model also failed: ${retryEx.message}")
                        lastError = retryEx.message
                    }
                }
            }
        }

        val isLimitZero = lastError?.contains("limit: 0", ignoreCase = true) == true
        val errorMessage = when {
            isLimitZero ->
                "Google no asigna cuota de imágenes a la clave genérica de desarrollo (límite 0 de peticiones). Para generar imágenes, ingresa tu propia clave de Gemini de Google AI Studio tocando el botón 'Configurar API Key' abajo."
            lastError?.contains("quota", ignoreCase = true) == true ||
            lastError?.contains("429", ignoreCase = true) == true ||
            lastError?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ->
                "Google indica que la clave actual no tiene cuota activa para generación de imágenes (límite de peticiones de Google alcanzado). Ingresa tu clave personal de Gemini para continuar."
            lastError?.contains("API_KEY_INVALID", ignoreCase = true) == true ->
                "Clave de API de Gemini no válida. Revisa tus credenciales en Ajustes."
            !lastError.isNullOrBlank() ->
                "No se pudo generar la imagen: $lastError"
            else ->
                "No se pudo obtener la imagen generada. Verifica tu conexión a internet o intenta con una descripción diferente."
        }

        Result.failure(Exception(errorMessage))
    }

    private fun callGeminiImageEndpoint(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String
    ): Bitmap? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        // Intento 1: Con responseModalities ["TEXT", "IMAGE"] y imageConfig { aspectRatio }
        try {
            val requestBodyJson = buildRequestBody(prompt, aspectRatio, includeImageConfig = true)
            val bitmap = executeImageRequest(url, requestBodyJson, apiKey)
            if (bitmap != null) return bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Standard payload attempt failed on $model: ${e.message}. Retrying with simplified payload...")
        }

        // Intento 2: Si el endpoint rechaza imageConfig, enviar solo responseModalities ["IMAGE"]
        val simplifiedBody = buildSimplifiedRequestBody(prompt)
        return executeImageRequest(url, simplifiedBody, apiKey)
    }

    private fun buildRequestBody(prompt: String, aspectRatio: String, includeImageConfig: Boolean): String {
        val root = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)

            val genConfig = JSONObject().apply {
                val modalities = JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                }
                put("responseModalities", modalities)

                if (includeImageConfig && aspectRatio.isNotBlank()) {
                    val imageConfig = JSONObject().apply {
                        put("aspectRatio", aspectRatio)
                    }
                    put("imageConfig", imageConfig)
                }
            }
            put("generationConfig", genConfig)
        }
        return root.toString()
    }

    private fun buildSimplifiedRequestBody(prompt: String): String {
        val root = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)

            val genConfig = JSONObject().apply {
                val modalities = JSONArray().apply {
                    put("IMAGE")
                }
                put("responseModalities", modalities)
            }
            put("generationConfig", genConfig)
        }
        return root.toString()
    }

    private fun executeImageRequest(url: String, jsonPayload: String, apiKey: String): Bitmap? {
        val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini image call error ${response.code}: $responseBody")
            val errObj = try { JSONObject(responseBody).optJSONObject("error") } catch (e: Exception) { null }
            val message = errObj?.optString("message") ?: "HTTP ${response.code}"
            throw Exception(message)
        }

        return parseGeminiCandidateBitmap(responseBody)
    }

    private fun parseGeminiCandidateBitmap(jsonString: String): Bitmap? {
        try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            for (cIndex in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(cIndex)
                val content = candidate.optJSONObject("content") ?: continue
                val parts = content.optJSONArray("parts") ?: continue

                for (pIndex in 0 until parts.length()) {
                    val part = parts.getJSONObject(pIndex)
                    val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    if (inlineData != null) {
                        val base64 = inlineData.optString("data", "")
                        if (base64.isNotBlank()) {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                return bitmap
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini image response", e)
        }
        return null
    }
}
