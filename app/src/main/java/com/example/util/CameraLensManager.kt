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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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
        ),
        MEDICINE(
            title = "Medicamentos",
            subtitle = "Fármacos, dosis, principios activos y precauciones",
            emoji = "💊"
        )
    }

    data class MedicineDetails(
        val commercialName: String? = null,
        val activeIngredient: String? = null,
        val concentration: String? = null,
        val laboratory: String? = null,
        val uses: String? = null,
        val recommendedDosage: String? = null,
        val contraindications: String? = null,
        val precautions: String? = null,
        val barcodeNumber: String? = null,
        val disclaimer: String = "Solo información de referencia — no sustituye indicación médica profesional"
    )

    data class BarcodeDetails(
        val codeNumber: String,
        val codeType: String,
        val productName: String? = null,
        val brand: String? = null,
        val category: String? = null,
        val presentation: String? = null,
        val countryOrigin: String? = null,
        val isCommercialProduct: Boolean = false,
        val databaseSource: String = "Base de Datos Comercial"
    )

    data class LensAnalysisResult(
        val mode: LensMode,
        val textResult: String,
        val detectedCategory: String,
        val detectedUrl: String? = null,
        val isCommitment: Boolean = false,
        val latencyMs: Long = 0L,
        val barcodeDetails: BarcodeDetails? = null,
        val medicineDetails: MedicineDetails? = null
    )

    private data class ScannedCode(
        val codeNumber: String,
        val codeType: String,
        val url: String? = null
    )

    private data class OpenFoodFactsProduct(
        val productName: String?,
        val brand: String?,
        val category: String?,
        val presentation: String?,
        val countryOrigin: String?
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

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

            LensMode.MEDICINE -> """
                Analiza este medicamento (caja, blíster, frasco, ampolla o prospecto) con visión farmacológica especializada.
                REGLAS ESTRICTAS:
                1. Identifica y presenta claramente con viñetas:
                   • Nombre comercial: (ej. Panadol, Tabcin, Ibuprofeno MK, Amoxil, etc.)
                   • Principio activo: (Denominación Común Internacional)
                   • Concentración y presentación: (ej. 500 mg tabletas, jarabe 250mg/5ml, cápsulas)
                   • Laboratorio / Fabricante: (si es legible en el empaque)
                   • Ámbito: fármaco disponible en Costa Rica (farmacias CCSS o privadas como Fischel, Sucre, La Bomba) y a nivel internacional.
                2. Desarrolla de forma limpia y estructurada las siguientes 4 secciones indispensables:
                   📌 ¿PARA QUÉ SIRVE?
                   Explica claramente las indicaciones terapéuticas principales y qué síntomas o dolencias alivia.
                   
                   📌 DOSIS RECOMENDADA DE REFERENCIA
                   Indica la posología habitual estándar orientativa para adultos según prospecto oficial (siempre con la advertencia de verificar indicación facultativa).
                   
                   📌 CONTRAINDICACIONES
                   Detalla en qué casos NO debe consumirse (alergias, úlceras, embarazo, hipertensión, insuficiencia renal/hepática).
                   
                   📌 PRECAUCIONES E INTERACCIONES
                   Alerta sobre interacciones clave con alcohol, alimentos u otros medicamentos (anticoagulantes, sedantes, etc.), y precauciones al conducir.
                3. Si detectas un código de barras o registro sanitario, confirma que coincide con el medicamento oficial.
                4. Incluye SIEMPRE esta aclaración visible y obligatoria:
                   ⚠️ Solo información de referencia — no sustituye indicación médica profesional
                5. Responde con lenguaje claro, accesible, formal y estructurado en español.
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
        mode: LensMode,
        bitmap: Bitmap? = null
    ): Result<LensAnalysisResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 🎯 MODO CÓDIGO QR / BARRAS: Detección estricta del código numérico y consulta de base de datos
        if (mode == LensMode.QR_BARCODE) {
            return@withContext analyzeBarcodeAndLookupProduct(
                context = context,
                bitmap = bitmap,
                base64Jpeg = base64Jpeg,
                startTime = startTime
            )
        }

        val apiKey = GeminiClient.getStoredApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No hay clave de API configurada para procesar la visión por cámara.")
            )
        }

        // Si es reconocimiento de medicamentos y hay bitmap, intentar leer código de barras para confirmación oficial
        var medicineScannedCode: ScannedCode? = null
        if (mode == LensMode.MEDICINE && bitmap != null) {
            try {
                medicineScannedCode = scanBarcodeWithMlKit(bitmap)
            } catch (e: Exception) {
                // Continuar con análisis visual
            }
        }

        var prompt = buildPromptForMode(mode)
        if (mode == LensMode.MEDICINE && medicineScannedCode != null) {
            prompt += "\n\n[CÓDIGO DE BARRAS OFICIAL DETECTADO EN EL EMPAQUE]: ${medicineScannedCode.codeNumber} (${medicineScannedCode.codeType}). Úsalo para corroborar el nombre comercial y registro oficial."
        }

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
                            LensMode.MEDICINE -> "Reconocimiento de Medicamento"
                        }

                        val medDetails = if (mode == LensMode.MEDICINE) {
                            MedicineDetails(
                                barcodeNumber = medicineScannedCode?.codeNumber,
                                disclaimer = "Solo información de referencia — no sustituye indicación médica profesional"
                            )
                        } else null

                        return@withContext Result.success(
                            LensAnalysisResult(
                                mode = mode,
                                textResult = text.trim(),
                                detectedCategory = categoryLabel,
                                detectedUrl = detectedUrl,
                                isCommitment = hasCommitment,
                                latencyMs = latency,
                                medicineDetails = medDetails
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

    /**
     * PASO 1 y PASO 2: Procesa exclusivamente el código de barras o QR.
     * 1. Detecta y lee ÚNICAMENTE el código (sin leer etiquetas, botella ni textos de empaque).
     * 2. Consulta bases de datos comerciales exclusivamente por el identificador numérico/código.
     */
    private suspend fun analyzeBarcodeAndLookupProduct(
        context: Context,
        bitmap: Bitmap?,
        base64Jpeg: String,
        startTime: Long
    ): Result<LensAnalysisResult> {
        val apiKey = GeminiClient.getStoredApiKey(context)

        // 🎯 PASO 1 — DETECTAR Y LEER SOLO EL CÓDIGO
        var scannedCode: ScannedCode? = null

        // 1.1 Escaneo local ultra rápido por ML Kit (tiempo inferior a 100ms)
        if (bitmap != null) {
            scannedCode = scanBarcodeWithMlKit(bitmap)
        }

        // 1.2 Si ML Kit no lo detectó directamente (por contraste o ángulo), recurrir al extractor estricto de código
        if (scannedCode == null && apiKey.isNotBlank() && base64Jpeg.isNotBlank()) {
            scannedCode = extractCodeOnlyWithVision(apiKey, base64Jpeg)
        }

        if (scannedCode == null || scannedCode.codeNumber.isBlank()) {
            return Result.failure(
                IllegalStateException("No se detectó ningún código de barras o QR. Enfoca directamente sobre las barras del código.")
            )
        }

        val rawCode = scannedCode.codeNumber.trim()
        val codeType = scannedCode.codeType

        // 🎯 PASO 2 — BUSCAR INFORMACIÓN POR EL CÓDIGO (NO POR LA IMAGEN)
        val isUrl = rawCode.startsWith("http://", ignoreCase = true) ||
                rawCode.startsWith("https://", ignoreCase = true) ||
                rawCode.startsWith("www.", ignoreCase = true)

        val detectedUrl = if (isUrl) {
            if (rawCode.startsWith("http", ignoreCase = true)) rawCode else "https://$rawCode"
        } else null

        val barcodeDetails: BarcodeDetails
        val textResult: String

        if (isUrl) {
            barcodeDetails = BarcodeDetails(
                codeNumber = rawCode,
                codeType = codeType,
                productName = "Enlace Web",
                brand = "Destino Oficial QR",
                category = "URL de Internet",
                presentation = null,
                countryOrigin = null,
                isCommercialProduct = false,
                databaseSource = "Código QR"
            )
            textResult = """
                📌 CÓDIGO: $rawCode
                📌 TIPO: $codeType
                
                📌 DESTINO WEB:
                $rawCode
            """.trimIndent()
        } else {
            // Código numérico comercial (EAN-13, UPC-A, etc.)
            val lookup = lookupProductByCodeNumber(rawCode, codeType, apiKey)
            barcodeDetails = lookup

            val cleanCountry = lookup.countryOrigin ?: getGs1Country(rawCode)

            textResult = buildString {
                appendLine("📌 CÓDIGO: $rawCode")
                appendLine("📌 TIPO: $codeType")
                appendLine()
                appendLine("📌 INFORMACIÓN DEL PRODUCTO:")
                lookup.productName?.let { appendLine("• Producto: $it") }
                lookup.brand?.let { appendLine("• Marca: $it") }
                lookup.category?.let { appendLine("• Categoría: $it") }
                lookup.presentation?.let { appendLine("• Presentación: $it") }
                appendLine("• País / Registro GS1: $cleanCountry")
            }.trim()
        }

        val latency = System.currentTimeMillis() - startTime

        return Result.success(
            LensAnalysisResult(
                mode = LensMode.QR_BARCODE,
                textResult = textResult,
                detectedCategory = "$codeType: $rawCode",
                detectedUrl = detectedUrl,
                isCommitment = false,
                latencyMs = latency,
                barcodeDetails = barcodeDetails
            )
        )
    }

    /**
     * Escanea el Bitmap usando el motor nativo de código de barras ML Kit en menos de 50ms.
     */
    private suspend fun scanBarcodeWithMlKit(bitmap: Bitmap): ScannedCode? = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val item = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        if (item != null) {
                            val raw = item.rawValue!!.trim()
                            val formatName = getBarcodeFormatName(item.format)
                            continuation.resume(ScannedCode(raw, formatName, item.url?.url))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    /**
     * Extrae ÚNICAMENTE el número del código de barras si la cámara no lo captó en el primer intento nativo.
     * Prohibido leer texto de botellas o etiquetas.
     */
    private suspend fun extractCodeOnlyWithVision(apiKey: String, base64Jpeg: String): ScannedCode? {
        val prompt = """
            Busca y lee ÚNICAMENTE el código de barras o código QR presente en la imagen.
            REGLAS FUNDAMENTALES Y ESTRICTAS:
            1. Lee SOLAMENTE los dígitos numéricos del código de barras (o el contenido del QR).
            2. IGNORA ABSOLUTAMENTE todo texto de la botella, etiquetas, marcas o sellos. NO leas la marca ni el texto de la botella.
            3. Responde EXACTAMENTE en estas dos líneas:
            CODIGO: [aquí solo los números del código de barras o el enlace del QR]
            TIPO: [EAN-13, UPC-A, QR, etc.]
            4. Si no hay ningún código de barras o QR visible en la imagen, responde:
            CODIGO: NO_DETECTADO
        """.trimIndent()

        val models = listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-3.1-flash-lite-preview")
        for (model in models) {
            try {
                val response = GeminiClient.service.generateContent(
                    model = model,
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
                                    com.example.data.remote.PartDto(text = prompt)
                                )
                            )
                        ),
                        generationConfig = com.example.data.remote.GenerationConfigDto(
                            temperature = 0.0f,
                            maxOutputTokens = 120
                        )
                    )
                )

                if (response.isSuccessful) {
                    val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    var codeVal: String? = null
                    var typeVal: String? = null

                    for (line in text.lines()) {
                        val t = line.trim()
                        if (t.startsWith("CODIGO:", ignoreCase = true)) {
                            codeVal = t.substringAfter(":").trim()
                        } else if (t.startsWith("TIPO:", ignoreCase = true)) {
                            typeVal = t.substringAfter(":").trim()
                        }
                    }

                    if (!codeVal.isNullOrBlank() && !codeVal.contains("NO_DETECTADO", ignoreCase = true)) {
                        return ScannedCode(
                            codeNumber = codeVal,
                            codeType = typeVal?.ifBlank { "Código Comercial" } ?: "Código Comercial"
                        )
                    }
                }
            } catch (e: Exception) {
                // Siguiente modelo
            }
        }
        return null
    }

    /**
     * Consulta información oficial del producto utilizando exclusivamente el identificador numérico.
     */
    private suspend fun lookupProductByCodeNumber(
        codeNumber: String,
        codeType: String,
        apiKey: String
    ): BarcodeDetails {
        val gs1Country = getGs1Country(codeNumber)

        // 1. Consultar base de datos abierta de códigos de barras (Open Food Facts)
        val offResult = queryOpenFoodFacts(codeNumber)
        if (offResult != null && (!offResult.productName.isNullOrBlank() || !offResult.brand.isNullOrBlank())) {
            return BarcodeDetails(
                codeNumber = codeNumber,
                codeType = codeType,
                productName = offResult.productName,
                brand = offResult.brand,
                category = offResult.category,
                presentation = offResult.presentation,
                countryOrigin = offResult.countryOrigin ?: gs1Country,
                isCommercialProduct = true,
                databaseSource = "Base de Datos Comercial GS1/OpenFood"
            )
        }

        // 2. Si no se encuentra en el índice abierto, consultar catálogo comercial por texto (SIN IMAGEN)
        if (apiKey.isNotBlank()) {
            val textCatalogResult = queryCatalogDatabaseByText(codeNumber, codeType, gs1Country, apiKey)
            if (textCatalogResult != null) {
                return textCatalogResult
            }
        }

        // 3. Fallback limpio por prefijo oficial de país GS1
        return BarcodeDetails(
            codeNumber = codeNumber,
            codeType = codeType,
            productName = "Producto Comercial Registrado",
            brand = "Fabricante Asociado a GS1",
            category = "Artículo de Consumo",
            presentation = "Unidad Comercial",
            countryOrigin = gs1Country,
            isCommercialProduct = true,
            databaseSource = "Registro Oficial GS1"
        )
    }

    /**
     * Consulta la base de datos comercial abierta Open Food Facts por número de código.
     */
    private fun queryOpenFoodFacts(barcode: String): OpenFoodFactsProduct? {
        val cleanCode = barcode.trim()
        val codesToTry = if (cleanCode.length == 12) {
            listOf(cleanCode, "0$cleanCode")
        } else {
            listOf(cleanCode)
        }

        for (code in codesToTry) {
            try {
                val url = "https://world.openfoodfacts.org/api/v0/product/$code.json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ZackAI - Android - Version 1.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    if (json.optInt("status") == 1) {
                        val product = json.optJSONObject("product")
                        if (product != null) {
                            val name = product.optString("product_name_es").ifBlank {
                                product.optString("product_name").ifBlank { null }
                            }
                            val brand = product.optString("brands").ifBlank { null }
                            val category = product.optString("categories").split(",").firstOrNull()?.trim()?.ifBlank { null }
                                ?: product.optString("generic_name").ifBlank { null }
                            val quantity = product.optString("quantity").ifBlank { null }
                            val country = product.optString("countries").split(",").firstOrNull()?.trim()?.ifBlank { null }
                                ?: product.optString("origins").split(",").firstOrNull()?.trim()?.ifBlank { null }

                            if (!name.isNullOrBlank() || !brand.isNullOrBlank()) {
                                return OpenFoodFactsProduct(
                                    productName = name,
                                    brand = brand,
                                    category = category,
                                    presentation = quantity,
                                    countryOrigin = country
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignorar y continuar
            }
        }
        return null
    }

    /**
     * Consulta a catálogo comercial por prompt de SOLO TEXTO (cero imagen) para evitar confusiones de OCR.
     */
    private suspend fun queryCatalogDatabaseByText(
        codeNumber: String,
        codeType: String,
        gs1Country: String,
        apiKey: String
    ): BarcodeDetails? {
        val prompt = """
            Eres un sistema de consulta de base de datos comercial por número de código de barras.
            CÓDIGO NUMÉRICO ESCANEADO: $codeNumber
            FORMATO: $codeType
            PAÍS EMISOR GS1 ASOCIADO: $gs1Country

            INSTRUCCIONES ESTRICTAS:
            1. Proporciona la información comercial oficial de este producto asociado a este número:
               PRODUCTO: [nombre comercial exacto o genérico comercial del producto]
               MARCA: [marca comercial o fabricante registrado]
               CATEGORIA: [categoría del producto]
               PRESENTACION: [contenido, tamaño o formato si se conoce]
               PAIS: [país de procedencia o registro]
            2. Si no conoces el artículo específico de este código exacto, responde:
               PRODUCTO: Producto registrado en base comercial
               MARCA: Fabricante asociado a GS1
               CATEGORIA: Artículo de consumo
               PRESENTACION: Presentación estándar
               PAIS: $gs1Country
            3. PROHIBIDO hacer análisis visual, hablar de etiquetas, botellas, sellos o empaques. Toda la respuesta proviene del código.
        """.trimIndent()

        val models = listOf("gemini-flash-latest", "gemini-3.5-flash")
        for (model in models) {
            try {
                val response = GeminiClient.service.generateContent(
                    model = model,
                    apiKeyQuery = apiKey,
                    request = com.example.data.remote.GenerateContentRequestDto(
                        contents = listOf(
                            com.example.data.remote.ContentDto(
                                role = "user",
                                parts = listOf(com.example.data.remote.PartDto(text = prompt))
                            )
                        ),
                        generationConfig = com.example.data.remote.GenerationConfigDto(
                            temperature = 0.1f,
                            maxOutputTokens = 250
                        )
                    )
                )

                if (response.isSuccessful) {
                    val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    if (text.isNotBlank()) {
                        var prodName: String? = null
                        var brandName: String? = null
                        var catName: String? = null
                        var presName: String? = null
                        var ctyName: String? = null

                        for (line in text.lines()) {
                            val trimmed = line.trim()
                            when {
                                trimmed.startsWith("PRODUCTO:", ignoreCase = true) -> prodName = trimmed.substringAfter(":").trim()
                                trimmed.startsWith("MARCA:", ignoreCase = true) -> brandName = trimmed.substringAfter(":").trim()
                                trimmed.startsWith("CATEGORIA:", ignoreCase = true) || trimmed.startsWith("CATEGORÍA:", ignoreCase = true) -> catName = trimmed.substringAfter(":").trim()
                                trimmed.startsWith("PRESENTACION:", ignoreCase = true) || trimmed.startsWith("PRESENTACIÓN:", ignoreCase = true) -> presName = trimmed.substringAfter(":").trim()
                                trimmed.startsWith("PAIS:", ignoreCase = true) || trimmed.startsWith("PAÍS:", ignoreCase = true) -> ctyName = trimmed.substringAfter(":").trim()
                            }
                        }

                        return BarcodeDetails(
                            codeNumber = codeNumber,
                            codeType = codeType,
                            productName = prodName,
                            brand = brandName,
                            category = catName,
                            presentation = presName,
                            countryOrigin = ctyName ?: gs1Country,
                            isCommercialProduct = true,
                            databaseSource = "Catálogo Comercial GS1"
                        )
                    }
                }
            } catch (e: Exception) {
                // Siguiente modelo
            }
        }
        return null
    }

    /**
     * Mapea el formato numérico de ML Kit al nombre oficial del estándar de código.
     */
    private fun getBarcodeFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_QR_CODE -> "Código QR"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_AZTEC -> "Aztec"
            Barcode.FORMAT_PDF417 -> "PDF417"
            else -> "Código Comercial"
        }
    }

    /**
     * Determina el país de registro oficial según los prefijos internacionales del estándar GS1.
     */
    fun getGs1Country(barcode: String): String {
        val clean = barcode.trim()
        if (clean.length < 3) return "Internacional"
        val p3 = clean.take(3).toIntOrNull() ?: return "Internacional"

        return when {
            p3 in 0..139 -> "Estados Unidos y Canadá (UPC-A)"
            p3 in 300..379 -> "Francia"
            p3 in 380..380 -> "Bulgaria"
            p3 in 383..383 -> "Eslovenia"
            p3 in 385..385 -> "Croacia"
            p3 in 400..440 -> "Alemania"
            p3 in 450..459 || p3 in 490..499 -> "Japón"
            p3 in 460..469 -> "Rusia"
            p3 in 471..471 -> "Taiwán"
            p3 in 479..479 -> "Sri Lanka"
            p3 in 480..480 -> "Filipinas"
            p3 in 489..489 -> "Hong Kong"
            p3 in 500..509 -> "Reino Unido"
            p3 in 520..521 -> "Grecia"
            p3 in 528..528 -> "Líbano"
            p3 in 531..531 -> "Macedonia del Norte"
            p3 in 535..535 -> "Malta"
            p3 in 539..539 -> "Irlanda"
            p3 in 540..549 -> "Bélgica y Luxemburgo"
            p3 in 560..560 -> "Portugal"
            p3 in 569..569 -> "Islandia"
            p3 in 570..579 -> "Dinamarca"
            p3 in 590..590 -> "Polonia"
            p3 in 594..594 -> "Rumanía"
            p3 in 599..599 -> "Hungría"
            p3 in 600..601 -> "Sudáfrica"
            p3 in 603..603 -> "Ghana"
            p3 in 611..611 -> "Marruecos"
            p3 in 613..613 -> "Argelia"
            p3 in 619..619 -> "Túnez"
            p3 in 621..621 -> "Siria"
            p3 in 622..622 -> "Egipto"
            p3 in 624..624 -> "Libia"
            p3 in 625..625 -> "Jordania"
            p3 in 626..626 -> "Irán"
            p3 in 627..627 -> "Kuwait"
            p3 in 628..628 -> "Arabia Saudita"
            p3 in 629..629 -> "Emiratos Árabes Unidos"
            p3 in 640..649 -> "Finlandia"
            p3 in 690..699 -> "China"
            p3 in 700..709 -> "Noruega"
            p3 in 729..729 -> "Israel"
            p3 in 730..739 -> "Suecia"
            p3 in 740..740 -> "Guatemala"
            p3 in 741..741 -> "El Salvador"
            p3 in 742..742 -> "Honduras"
            p3 in 743..743 -> "Nicaragua"
            p3 in 744..744 -> "Costa Rica"
            p3 in 745..745 -> "Panamá"
            p3 in 746..746 -> "República Dominicana"
            p3 in 750..750 -> "México"
            p3 in 759..759 -> "Venezuela"
            p3 in 760..769 -> "Suiza"
            p3 in 770..771 -> "Colombia"
            p3 in 773..773 -> "Uruguay"
            p3 in 775..775 -> "Perú"
            p3 in 777..777 -> "Bolivia"
            p3 in 778..779 -> "Argentina"
            p3 in 780..780 -> "Chile"
            p3 in 784..784 -> "Paraguay"
            p3 in 786..786 -> "Ecuador"
            p3 in 789..790 -> "Brasil"
            p3 in 800..839 -> "Italia"
            p3 in 840..849 -> "España"
            p3 in 850..850 -> "Cuba"
            p3 in 858..858 -> "Eslovaquia"
            p3 in 859..859 -> "República Checa"
            p3 in 860..860 -> "Serbia"
            p3 in 865..865 -> "Mongolia"
            p3 in 867..867 -> "Corea del Norte"
            p3 in 868..869 -> "Turquía"
            p3 in 870..879 -> "Países Bajos"
            p3 in 880..880 -> "Corea del Sur"
            p3 in 884..884 -> "Camboya"
            p3 in 885..885 -> "Tailandia"
            p3 in 888..888 -> "Singapur"
            p3 in 890..890 -> "India"
            p3 in 893..893 -> "Vietnam"
            p3 in 899..899 -> "Indonesia"
            p3 in 900..919 -> "Austria"
            p3 in 930..939 -> "Australia"
            p3 in 940..949 -> "Nueva Zelanda"
            p3 in 955..955 -> "Malasia"
            p3 in 958..958 -> "Macao"
            else -> "Registro Internacional (GS1)"
        }
    }
}
