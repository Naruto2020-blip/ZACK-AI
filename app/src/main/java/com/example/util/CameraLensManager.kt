package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.ImageProxy
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiClient
import com.example.domain.CascadeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object CameraLensManager {

    enum class LensMode(
        val title: String,
        val subtitle: String,
        val emoji: String
    ) {
        AUTO(
            title = "Auto Inteligente",
            subtitle = "Reconoce todo al instante (texto, objetos, QR, facturas)",
            emoji = "⚡"
        ),
        TEXT_TRANSLATE(
            title = "Texto y Traducción",
            subtitle = "Lee texto y traduce al español automáticamente",
            emoji = "📝"
        ),
        OBJECTS_NATURE(
            title = "Objetos y Naturaleza",
            subtitle = "Plantas, animales, alimentos, productos y herramientas",
            emoji = "🌿"
        ),
        QR_BARCODE(
            title = "QR y Códigos",
            subtitle = "Lee y explica contenido de códigos QR y de barras",
            emoji = "🏁"
        ),
        INVOICE_RECEIPT(
            title = "Facturas y Recibos",
            subtitle = "Extrae montos, fechas, nombres y desglose",
            emoji = "🧾"
        )
    }

    data class LensAnalysisResult(
        val mode: LensMode,
        val textResult: String,
        val detectedCategory: String,
        val detectedUrl: String? = null,
        val isCommitment: Boolean = false,
        val latencyMs: Long = 0L
    )

    fun buildPromptForMode(mode: LensMode): String {
        return when (mode) {
            LensMode.AUTO -> """
                Analiza esta imagen con visión en tiempo real.
                REGLAS ESTRICTAS:
                1. Responde rápido y directo, sin introducciones largas ni rodeos.
                2. Habla siempre en español claro, sencillo y con tono amable.
                3. Si detectas texto en otro idioma (cartel, menú, documento, etiqueta), tradúcelo al español automáticamente.
                4. Si es un objeto, planta, animal, alimento o herramienta, identifícalo con claridad y añade 2 o 3 datos útiles.
                5. Si ves un código QR o de barras, lee su contenido exacto y explica qué contiene o hacia dónde dirige.
                6. En facturas, recibos o tarjetas, extrae los datos importantes: montos, fechas, nombres.
            """.trimIndent()

            LensMode.TEXT_TRANSLATE -> """
                Lee cualquier texto presente en esta imagen al instante (carteles, documentos, etiquetas, menús, empaques).
                REGLAS ESTRICTAS:
                1. Si el texto está en otro idioma, tradúcelo al español automáticamente.
                2. Presenta de forma directa y limpia:
                   - Texto detectado (idioma original)
                   - Traducción al español
                3. Responde rápido y directo, sin explicaciones largas. Habla en español claro, sencillo y con tono amable.
            """.trimIndent()

            LensMode.OBJECTS_NATURE -> """
                Identifica con claridad el objeto, planta, animal, producto, herramienta o alimento presente en esta imagen.
                REGLAS ESTRICTAS:
                1. Da el nombre común directo (y científico si es una planta o animal).
                2. Añade 2 o 3 detalles prácticos y útiles (origen, cuidados, uso, recomendaciones).
                3. Responde rápido y directo, sin introducciones vacías. Habla en español claro, sencillo y con tono amable.
            """.trimIndent()

            LensMode.QR_BARCODE -> """
                Analiza el código QR o código de barras en esta imagen.
                REGLAS ESTRICTAS:
                1. Extrae el contenido exacto (enlace URL, texto codificado o número de código de barras).
                2. Explica qué contiene y cuál es su función o destino.
                3. Si es un enlace web, indícalo claramente en una línea separada con el prefijo "URL: ".
                4. Responde rápido y directo en español claro y amable.
            """.trimIndent()

            LensMode.INVOICE_RECEIPT -> """
                Analiza esta factura, recibo, comprobante o tarjeta comercial.
                REGLAS ESTRICTAS:
                1. Extrae de forma concisa los datos importantes:
                   - Nombre del establecimiento o emisor:
                   - Fecha y hora:
                   - Monto total a pagar (con moneda):
                   - Desglose o conceptos principales:
                   - Método de pago / Número de recibo (si aparece):
                2. Responde rápido y directo sin rodeos. Habla en español claro, sencillo y con tono amable.
            """.trimIndent()
        }
    }

    /**
     * Procesa una captura ImageProxy de CameraX, la rota según su orientación y la comprime a JPEG Base64 optimizado.
     */
    fun processImageProxyToBase64(imageProxy: ImageProxy): Pair<String, Bitmap?> {
        val plane = imageProxy.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Pair("", null)

        // Corregir rotación si es necesario
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        // Redimensionar para velocidad (máximo 1080px en lado mayor)
        val maxSide = 1080
        val width = rawBitmap.width
        val height = rawBitmap.height
        val scale = if (width > maxSide || height > maxSide) {
            maxSide.toFloat() / Math.max(width, height).toFloat()
        } else 1.0f

        matrix.postScale(scale, scale)

        val scaledBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height, matrix, true)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
        val jpegBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        return Pair(base64, scaledBitmap)
    }

    /**
     * Envía la imagen al modelo multimodal ultra rápido con el prompt especializado del modo.
     */
    suspend fun analyzeImage(
        context: Context,
        base64Jpeg: String,
        mode: LensMode
    ): Result<LensAnalysisResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = GeminiClient.getStoredApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No hay clave de API configurada para procesar la visión por cámara.")
            )
        }

        val prompt = buildPromptForMode(mode)
        val systemInstruction = """
            Eres el motor de reconocimiento visual en tiempo real de ZACK AI.
            REGLAS FUNDAMENTALES:
            - Responde rápido y directo, sin explicaciones largas.
            - Habla siempre en español claro, sencillo y con tono amable.
            - Sé muy exacto al leer texto, montos, números y códigos.
        """.trimIndent()

        // Modelos visuales multimodales de Google Gemini compatibles con generateContent
        val visionModels = listOf(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-flash",
            "gemini-3.1-pro-preview"
        )

        val promptWithInstructions = """
            $systemInstruction
            
            $prompt
        """.trimIndent()

        var lastError: String? = null
        for (modelId in visionModels) {
            try {
                // Petición estándar multimodal con compatibilidad total para todos los modelos
                val response = GeminiClient.service.generateContent(
                    model = modelId,
                    apiKeyQuery = apiKey,
                    request = com.example.data.remote.GenerateContentRequestDto(
                        contents = listOf(
                            com.example.data.remote.ContentDto(
                                role = "user",
                                parts = listOf(
                                    com.example.data.remote.PartDto(
                                        inlineData = com.example.data.remote.BlobDto(
                                            mimeType = "image/jpeg",
                                            data = base64Jpeg
                                        )
                                    ),
                                    com.example.data.remote.PartDto(text = promptWithInstructions)
                                )
                            )
                        ),
                        generationConfig = com.example.data.remote.GenerationConfigDto(
                            temperature = 0.2f,
                            maxOutputTokens = 800
                        )
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val candidate = body?.candidates?.firstOrNull()
                    val text = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("\n") ?: ""

                    if (text.isNotBlank()) {
                        val latency = System.currentTimeMillis() - startTime

                        // Extraer URL si existe en el resultado
                        val urlRegex = Regex("""(?i)\b(https?://[^\s]+)""")
                        val detectedUrl = urlRegex.find(text)?.value

                        // Detectar si hay compromiso o fecha
                        val hasCommitment = text.contains("fecha", ignoreCase = true) ||
                                text.contains("vence", ignoreCase = true) ||
                                text.contains("plazo", ignoreCase = true)

                        val categoryLabel = when (mode) {
                            LensMode.AUTO -> "Reconocimiento Inteligente"
                            LensMode.TEXT_TRANSLATE -> "Texto y Traducción"
                            LensMode.OBJECTS_NATURE -> "Objeto / Naturaleza"
                            LensMode.QR_BARCODE -> "Código QR / Barras"
                            LensMode.INVOICE_RECEIPT -> "Datos de Factura / Recibo"
                        }

                        return@withContext Result.success(
                            LensAnalysisResult(
                                mode = mode,
                                textResult = text.trim(),
                                detectedCategory = categoryLabel,
                                detectedUrl = detectedUrl,
                                isCommitment = hasCommitment,
                                latencyMs = latency
                            )
                        )
                    }
                } else {
                    val code = response.code()
                    val errorRaw = response.errorBody()?.string() ?: ""
                    lastError = when (code) {
                        429 -> "Límite de peticiones alcanzado. Espera unos segundos y vuelve a intentar."
                        403 -> "Clave de API inválida o sin permisos para visión artificial."
                        404 -> null // Continuar al siguiente modelo silenciosamente
                        else -> {
                            try {
                                val json = JSONObject(errorRaw)
                                val errObj = json.optJSONObject("error")
                                errObj?.optString("message") ?: errorRaw
                            } catch (e: Exception) {
                                errorRaw.ifBlank { "Error del servidor ($code)" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage
            }
        }

        Result.failure(
            IllegalStateException(lastError ?: "No fue posible analizar la imagen. Por favor, reintenta enfocar.")
        )
    }
}
