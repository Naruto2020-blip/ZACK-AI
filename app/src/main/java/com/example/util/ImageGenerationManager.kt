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

    /**
     * Genera una imagen con IA a partir de la descripción en español del usuario.
     * Sigue fielmente la instrucción, colores, cantidades, formas y estilos solicitados.
     */
    suspend fun generateImage(
        userPrompt: String,
        aspectRatio: String = "1:1",
        customApiKey: String? = null
    ): Result<GeneratedAiImage> = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey.trim() else GeminiClient.getApiKey()

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

        // 1. Intentar con gemini-2.5-flash-image (modelo oficial para generación de imágenes)
        val candidateModels = listOf(
            "gemini-2.5-flash-image",
            "gemini-3.1-flash-image-preview"
        )

        var lastError: String? = null

        for (model in candidateModels) {
            try {
                Log.d(TAG, "Attempting image generation with model: $model")
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
            }
        }

        // 2. Si fallaron los modelos Gemini Flash Image, intentar con imagen-3.0-generate-002 (Predict API)
        try {
            Log.d(TAG, "Attempting image generation with imagen-3.0-generate-002")
            val imagenResult = callImagenPredictEndpoint("imagen-3.0-generate-002", engineeredPrompt, aspectRatio, apiKey)
            if (imagenResult != null) {
                val aiImage = GeneratedAiImage(
                    prompt = promptClean,
                    bitmap = imagenResult,
                    aspectRatio = aspectRatio,
                    modelUsed = "Imagen 3"
                )
                return@withContext Result.success(aiImage)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed with Imagen 3: ${e.message}")
            lastError = e.message
        }

        val errorMessage = when {
            lastError?.contains("quota", ignoreCase = true) == true ->
                "Límite de cuota excedido para generación de imágenes. Intenta nuevamente en unos momentos."
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

        val requestJson = JSONObject().apply {
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

                val imageConfig = JSONObject().apply {
                    put("aspectRatio", aspectRatio)
                    put("imageSize", "1K")
                }
                put("imageConfig", imageConfig)
            }
            put("generationConfig", genConfig)
        }

        val requestBody = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
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

    private fun callImagenPredictEndpoint(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String
    ): Bitmap? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:predict?key=$apiKey"

        val requestJson = JSONObject().apply {
            val instancesArr = JSONArray().apply {
                val instObj = JSONObject().apply {
                    put("prompt", prompt)
                }
                put(instObj)
            }
            put("instances", instancesArr)

            val paramsObj = JSONObject().apply {
                put("sampleCount", 1)
                put("aspectRatio", aspectRatio)
            }
            put("parameters", paramsObj)
        }

        val requestBody = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Imagen predict error ${response.code}: $responseBody")
            val errObj = try { JSONObject(responseBody).optJSONObject("error") } catch (e: Exception) { null }
            val message = errObj?.optString("message") ?: "HTTP ${response.code}"
            throw Exception(message)
        }

        try {
            val root = JSONObject(responseBody)
            val predictions = root.optJSONArray("predictions") ?: return null
            if (predictions.length() > 0) {
                val pred = predictions.getJSONObject(0)
                val base64 = pred.optString("bytesBase64Encoded", "")
                if (base64.isNotBlank()) {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Imagen prediction response", e)
        }
        return null
    }
}
