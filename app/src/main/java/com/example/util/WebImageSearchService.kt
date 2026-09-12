package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WebImageResult(
    val title: String,
    val imageUrl: String,
    val sourceName: String,
    val score: Int = 100
)

/**
 * Servicio de búsqueda de imágenes reales y verificadas en la web (Wikipedia, Wikimedia Commons).
 * Permite a ZACK AI entregar fotografías, escudos patrios, banderas, monumentos, personajes y objetos reales
 * sin recurrir a alucinaciones o dibujos falsos cuando el usuario solicita imágenes de elementos reales.
 */
object WebImageSearchService {
    private const val TAG = "WebImageSearch"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val USER_AGENT = "ZackAIAndroid/2.5 (com.example.zackai; support@zackai.app)"

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (t: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (t: Throwable) {
            println("[$TAG WARN] $msg")
        }
    }

    /**
     * Limpia la frase del usuario para extraer exclusivamente el término de búsqueda de la imagen.
     * Ejemplo: "pasame una imagen del escudo de Costa Rica" -> "escudo de Costa Rica"
     */
    fun extractSearchQuery(rawPrompt: String): String {
        var q = rawPrompt.trim()

        // 1. Quitar signos de puntuación iniciales y finales
        q = q.replace(Regex("^[¿?¡!\"'()]+|[¿?¡!\"'()]+$"), "").trim()

        // Si la frase contiene una petición incrustada ("busca la imagen en internet de X y verás")
        val embeddedPattern = Regex(
            "(?i)(?:busca(?:rme)?|buscar|encuentra|dame|mu[eé]strame|ense[ñn]ame|m[aá]ndame|quiero\\s+ver|p[aá]same)\\s+(?:la\\s+|una\\s+|el\\s+)?(?:imagen|foto|fotograf[ií]a|dibujo)?\\s*(?:en\\s+internet|de\\s+internet|en\\s+la\\s+web|de\\s+la\\s+web|en\\s+google)?\\s*(?:del?|sobre|acerca\\s+de)?\\s*([^.,;!\\n\\r]+)"
        )
        val match = embeddedPattern.find(q)
        if (match != null) {
            var extracted = match.groupValues[1].trim()
            val suffixRegex = Regex("(?i)\\s+(?:y\\s+ver[aá]s|y\\s+lo\\s+ver[aá]s|por\\s+favor|gracias|que\\s+est[eé]\\s+bien|porfa|que\\s+sea\\s+real|oficial|en\\s+hd|ya\\s+que.*)$")
            extracted = suffixRegex.replace(extracted, "").trim()
            if (extracted.isNotBlank()) {
                return extracted
            }
        }

        // 2. Quitar prefijos comunes de solicitud
        val prefixPatterns = listOf(
            Regex("(?i)^(?:por\\s+favor\\s+)?(?:pasame|pásame|busca(?:rme)?|buscar|encuentra|dame|muéstrame|muestrame|enseñame|consigue|consígueme|consigueme|quiero\\s+(?:ver|una|un)?|pon|ponme|ver|mándame|mandame|envíame|enviame)\\s+"),
            Regex("(?i)^(?:la|una|un|el|los|las)\\s+"),
            Regex("(?i)^(?:imagen|foto|fotografía|fotografia|dibujo|retrato|ilustración|ilustracion|cuadro|diseño)\\s+"),
            Regex("(?i)^(?:en\\s+internet|de\\s+internet|en\\s+la\\s+web|de\\s+la\\s+web|en\\s+google|de\\s+google|real|oficial)\\s+"),
            Regex("(?i)^(?:del?|sobre|acerca\\s+de|para)\\s+"),
            Regex("(?i)^(?:la|el|los|las|un|una)\\s+")
        )

        for (pattern in prefixPatterns) {
            q = pattern.replace(q, "").trim()
        }

        // 3. Quitar coletillas conversacionales al final (ej: "y verás", "por favor", "gracias")
        val suffixPatterns = listOf(
            Regex("(?i)\\s+(?:y\\s+ver[aá]s|y\\s+lo\\s+ver[aá]s|por\\s+favor|gracias|que\\s+est[eé]\\s+bien|porfa|que\\s+sea\\s+real|oficial|en\\s+hd)$"),
            Regex("(?i)\\s+(?:en\\s+internet|de\\s+internet|en\\s+la\\s+web|de\\s+la\\s+web)$")
        )
        for (pattern in suffixPatterns) {
            q = pattern.replace(q, "").trim()
        }

        val base = q.ifBlank { rawPrompt.trim() }
        // Si el usuario dijo "escudo Costa Rica" o "bandera Costa Rica", normalizar a "escudo de Costa Rica"
        return if (Regex("(?i)^(escudo|bandera|himno|mapa|capital|presidente|moneda)\\s+(?!de\\s+|del\\s+)(.+)").matches(base)) {
            base.replace(Regex("(?i)^(escudo|bandera|himno|mapa|capital|presidente|moneda)\\s+(.+)"), "$1 de $2")
        } else {
            base
        }
    }

    /**
     * Determina si la petición corresponde a una búsqueda de imagen real en la web.
     */
    fun isWebImageSearchCandidate(prompt: String): Boolean {
        val p = prompt.lowercase(Locale.ROOT).trim()

        // Indicadores claros de búsqueda o imágenes de elementos reales
        val searchKeywords = listOf(
            "internet", "web", "escudo", "bandera", "mapa", "himno",
            "foto de", "imagen de", "pasame", "pásame", "busca", "buscar",
            "muéstrame", "muestrame", "mandame", "mándame", "enseñame", "monumento",
            "ciudad de", "país", "pais", "presidente", "cantante", "futbolista",
            "jugador", "actor", "actriz", "persona", "estadio", "edificio", "volcán", "volcan"
        )

        val containsRealSubject = searchKeywords.any { p.contains(it) }

        // Si explícitamente pide fantasía, estilo anime o conceptos irreales, no es candidato estricto
        val isExplicitFantasy = p.contains("estilo anime") ||
                p.contains("cyberpunk") ||
                p.contains("pixel art") ||
                p.contains("en marte") ||
                p.contains("montando un dinosaurio") ||
                p.contains("caricatura de")

        return containsRealSubject && !isExplicitFantasy
    }

    /**
     * Busca la mejor imagen real en la web utilizando Wikipedia y Wikimedia Commons.
     */
    suspend fun searchRealImage(rawPrompt: String): WebImageResult? = withContext(Dispatchers.IO) {
        val searchQuery = extractSearchQuery(rawPrompt)
        if (searchQuery.isBlank() || searchQuery.length < 2) return@withContext null

        withTimeoutOrNull(4500L) {
            try {
                val candidates = mutableListOf<WebImageResult>()

                // 1. Wikipedia en Español: coincidencia directa de título con redirección automática
                val directResult = searchWikipediaDirectTitle(searchQuery)
                if (directResult != null) {
                    candidates.add(directResult)
                }

                // 2. Wikipedia en Español: generador de búsqueda de artículos
                val wikiSearchResults = searchWikipediaGenerator(searchQuery)
                candidates.addAll(wikiSearchResults)

                // 3. Wikimedia Commons: repositorio oficial de archivos de imagen y multimedia
                val commonsResults = searchWikimediaCommons(searchQuery)
                candidates.addAll(commonsResults)

                // 4. Si aún no hay candidatos, buscar en Wikipedia global (Inglés)
                if (candidates.isEmpty()) {
                    val wikiEnResults = searchWikipediaEnglishGenerator(searchQuery)
                    candidates.addAll(wikiEnResults)
                }

                // Ordenar candidatos por relevancia descendente y retornar el mejor
                candidates.maxByOrNull { it.score }
            } catch (e: Exception) {
                logWarn("Error buscando imagen en la web: ${e.message}")
                null
            }
        }
    }

    private fun searchWikipediaDirectTitle(query: String): WebImageResult? {
        val variants = linkedSetOf<String>()
        variants.add(query)

        // TitleCase: e.g. "Escudo de Costa Rica"
        val words = query.split(Regex("\\s+"))
        val tc = words.joinToString(" ") { w ->
            val lw = w.lowercase(Locale.ROOT)
            if (lw in listOf("de", "del", "la", "el", "en", "y", "los", "las")) lw
            else w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        variants.add(tc)

        // Traducciones comunes para símbolos nacionales si aplica
        if (query.contains("escudo de", ignoreCase = true)) {
            val country = query.replace(Regex("(?i)escudo de"), "").trim()
            val countryCap = country.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            variants.add("Coat of arms of $countryCap")
            variants.add("Coat of arms of $country")
        }
        if (query.contains("bandera de", ignoreCase = true)) {
            val country = query.replace(Regex("(?i)bandera de"), "").trim()
            val countryCap = country.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            variants.add("Flag of $countryCap")
            variants.add("Flag of $country")
        }

        for (candidateTitle in variants) {
            val isEnglish = candidateTitle.startsWith("Coat of arms", ignoreCase = true) || candidateTitle.startsWith("Flag of", ignoreCase = true)
            val domain = if (isEnglish) "en.wikipedia.org" else "es.wikipedia.org"
            val res = queryWikipediaPageByTitle(domain, candidateTitle)
            if (res != null) return res
        }
        return null
    }

    private fun queryWikipediaPageByTitle(domain: String, title: String): WebImageResult? {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://$domain/w/api.php?action=query&titles=$encoded&redirects=1&prop=pageimages|description&piprop=original|thumbnail&pithumbsize=1280&format=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val queryObj = json.optJSONObject("query") ?: return null
                val pages = queryObj.optJSONObject("pages") ?: return null

                val keys = pages.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "-1") continue
                    val page = pages.optJSONObject(key) ?: continue
                    val pageTitle = page.optString("title", title)
                    val thumbObj = page.optJSONObject("thumbnail")
                    val origObj = page.optJSONObject("original")

                    val imgUrl = thumbObj?.optString("source") 
                        ?: origObj?.optString("source")
                        ?: continue

                    if (imgUrl.isNotBlank()) {
                        val finalUrl = sanitizeWikimediaUrl(imgUrl)
                        return WebImageResult(
                            title = pageTitle,
                            imageUrl = finalUrl,
                            sourceName = if (domain.startsWith("en.")) "Wikipedia (EN)" else "Wikipedia",
                            score = 500
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            logDebug("Direct query error for '$title' on $domain: ${e.message}")
            null
        }
    }

    private fun searchWikipediaGenerator(query: String): List<WebImageResult> {
        val results = mutableListOf<WebImageResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://es.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=6&prop=pageimages|description&piprop=original|thumbnail&pithumbsize=1280&format=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val queryObj = json.optJSONObject("query") ?: return emptyList()
                val pages = queryObj.optJSONObject("pages") ?: return emptyList()

                val queryWords = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 2 }
                val queryLower = query.lowercase(Locale.ROOT)
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val title = page.optString("title", "")
                    val thumbObj = page.optJSONObject("thumbnail")
                    val origObj = page.optJSONObject("original")

                    val imgUrl = thumbObj?.optString("source")
                        ?: origObj?.optString("source")
                        ?: continue

                    if (imgUrl.isNotBlank()) {
                        val titleLower = title.lowercase(Locale.ROOT)
                        var score = 30
                        for (w in queryWords) {
                            if (titleLower.contains(w)) score += 20
                        }
                        if (titleLower.contains(queryLower)) {
                            score += 50
                        }
                        if (titleLower == queryLower) {
                            score += 200
                        } else if (titleLower.startsWith("escudo de") || titleLower.startsWith("bandera de")) {
                            score += 150
                        }

                        results.add(
                            WebImageResult(
                                title = title,
                                imageUrl = sanitizeWikimediaUrl(imgUrl),
                                sourceName = "Wikipedia",
                                score = score
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logDebug("Generator search error: ${e.message}")
        }
        return results
    }

    private fun searchWikimediaCommons(query: String): List<WebImageResult> {
        val results = mutableListOf<WebImageResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=6&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=1280&format=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val queryObj = json.optJSONObject("query") ?: return emptyList()
                val pages = queryObj.optJSONObject("pages") ?: return emptyList()

                val queryWords = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 2 }
                val queryLower = query.lowercase(Locale.ROOT)
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val rawTitle = page.optString("title", "")
                    val title = rawTitle.replace("File:", "").replace("Archivo:", "").trim()

                    val infos = page.optJSONArray("imageinfo") ?: continue
                    if (infos.length() == 0) continue
                    val info = infos.optJSONObject(0) ?: continue

                    val thumbUrl = info.optString("thumburl").ifBlank { null }
                    val origUrl = info.optString("url").ifBlank { null }
                    val finalUrl = thumbUrl ?: origUrl ?: continue

                    val titleLower = title.lowercase(Locale.ROOT)
                    var score = 25
                    for (w in queryWords) {
                        if (titleLower.contains(w)) score += 15
                    }
                    if (titleLower.contains(queryLower)) {
                        score += 35
                    }

                    // Boost oficial para escudo y bandera
                    if (titleLower.startsWith("coat of arms of") || titleLower.startsWith("escudo de")) {
                        score += 150
                    }
                    if (titleLower.startsWith("flag of") || titleLower.startsWith("bandera de")) {
                        score += 120
                    }
                    if (titleLower.endsWith(".svg") || titleLower.endsWith(".svg.png")) {
                        score += 50
                    }

                    // Penalizaciones severas para monedas, años pasados, antigüedades o edificios
                    val hasHistoricalYear = Regex("\\b(18\\d\\d|19\\d\\d|200\\d|201\\d)\\b").containsMatchIn(titleLower)
                    val userAskedYear = Regex("\\b\\d{4}\\b").containsMatchIn(query)
                    if (hasHistoricalYear && !userAskedYear) {
                        score -= 120
                    }

                    val penaltyWords = listOf("gold", "oro", "coin", "moneda", "teatro", "antiguo", "antigua", "provincial", "canton", "municipal", "dorado", "stamp", "sello")
                    for (pw in penaltyWords) {
                        if (titleLower.contains(pw) && !queryLower.contains(pw)) {
                            score -= 80
                        }
                    }

                    results.add(
                        WebImageResult(
                            title = title,
                            imageUrl = sanitizeWikimediaUrl(finalUrl),
                            sourceName = "Wikimedia Commons",
                            score = score
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logDebug("Commons search error: ${e.message}")
        }
        return results
    }

    private fun searchWikipediaEnglishGenerator(query: String): List<WebImageResult> {
        val results = mutableListOf<WebImageResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=4&prop=pageimages&piprop=original|thumbnail&pithumbsize=1280&format=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val queryObj = json.optJSONObject("query") ?: return emptyList()
                val pages = queryObj.optJSONObject("pages") ?: return emptyList()

                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val title = page.optString("title", "")
                    val thumbObj = page.optJSONObject("thumbnail")
                    val origObj = page.optJSONObject("original")

                    val imgUrl = thumbObj?.optString("source")
                        ?: origObj?.optString("source")
                        ?: continue

                    if (imgUrl.isNotBlank()) {
                        results.add(
                            WebImageResult(
                                title = title,
                                imageUrl = sanitizeWikimediaUrl(imgUrl),
                                sourceName = "Wikipedia",
                                score = 40
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logDebug("Wikipedia English error: ${e.message}")
        }
        return results
    }

    /**
     * Si la URL original apunta a un archivo SVG sin renderizar, asegura que se utilice
     * la versión rasterizada en PNG de alta resolución de los servidores de Wikimedia.
     */
    private fun sanitizeWikimediaUrl(url: String): String {
        var clean = url.trim()
        val pathOnly = clean.substringBefore("?")
        // Si termina directamente en .svg sin /thumb/, convertir a thumbnail PNG de 1280px
        if (pathOnly.endsWith(".svg", ignoreCase = true) && !clean.contains("/thumb/")) {
            val parts = pathOnly.split("/wikipedia/commons/")
            if (parts.size == 2) {
                val subPath = parts[1]
                val fileName = subPath.substringAfterLast("/")
                clean = "https://upload.wikimedia.org/wikipedia/commons/thumb/$subPath/1280px-$fileName.png"
            }
        }
        return clean
    }
}
