package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Servicio de búsqueda y verificación en tiempo real para grounding de respuestas en la web.
 * Permite a ZACK AI obtener noticias, hechos y mandatarios vigentes para que ninguna respuesta esté desactualizada.
 */
object RealTimeGroundingService {
    private const val TAG = "RealTimeGrounding"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Determina si la consulta amerita búsqueda en la web en tiempo real.
     */
    fun shouldSearchWeb(query: String): Boolean {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.length < 3) return false

        // Saludos o agradecimientos netamente conversacionales
        val simpleGreetings = listOf(
            "hola", "buenos días", "buenas tardes", "buenas noches",
            "qué tal", "que tal", "cómo estás", "como estas",
            "gracias", "muchas gracias", "adiós", "adios", "chao", "chau", "ok", "vale"
        )
        if (simpleGreetings.contains(trimmed)) return false

        return true
    }

    /**
     * Consulta fuentes de noticias e internet en tiempo real y devuelve un bloque de contexto verificado.
     */
    suspend fun fetchRealTimeContext(query: String): String? = withContext(Dispatchers.IO) {
        if (!shouldSearchWeb(query)) return@withContext null

        withTimeoutOrNull(3000L) {
            try {
                val cleanedQuery = cleanQueryForSearch(query)
                val encoded = URLEncoder.encode(cleanedQuery, "UTF-8")

                // 1. Google News RSS en español latinoamericano
                val rssUrl = "https://news.google.com/rss/search?q=$encoded&hl=es-419&gl=CR&ceid=CR:es-419"
                val rssRequest = Request.Builder()
                    .url(rssUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()

                val headlines = mutableListOf<String>()
                httpClient.newCall(rssRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                        val dateRegex = Regex("<pubDate>(.*?)</pubDate>")

                        val matches = itemRegex.findAll(body).take(5)
                        for (match in matches) {
                            val itemXml = match.groupValues[1]
                            val rawTitle = titleRegex.find(itemXml)?.groupValues?.get(1) ?: continue
                            val rawDate = dateRegex.find(itemXml)?.groupValues?.get(1) ?: ""

                            val cleanTitle = cleanHtmlEntities(rawTitle.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>"), "$1"))
                            if (cleanTitle.isNotBlank() && cleanTitle.length > 8) {
                                val dateInfo = if (rawDate.isNotBlank()) " ($rawDate)" else ""
                                headlines.add("• $cleanTitle$dateInfo")
                            }
                        }
                    }
                }

                // 2. Consulta enciclopédica Wikipedia si aplica
                val wikiSnippet = fetchWikipediaSnippet(cleanedQuery)

                if (headlines.isEmpty() && wikiSnippet.isNullOrBlank()) {
                    return@withTimeoutOrNull null
                }

                val now = Calendar.getInstance()
                val fullDate = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(now.time)

                buildString {
                    appendLine("🌐 DATOS Y NOTICIAS EN TIEMPO REAL DESDE LA WEB (Fecha de hoy: $fullDate):")
                    if (headlines.isNotEmpty()) {
                        appendLine("Titulares y noticias verificadas más recientes:")
                        headlines.take(4).forEach { appendLine(it) }
                    }
                    if (!wikiSnippet.isNullOrBlank()) {
                        appendLine("Referencia enciclopédica:")
                        appendLine("• $wikiSnippet")
                    }
                    appendLine("\nOBLIGACIÓN DE VIGENCIA: Utiliza obligatoriamente estos datos y noticias reales de la web para responder a la solicitud del usuario con máxima exactitud y sin información desactualizada.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error en búsqueda de tiempo real: ${e.message}")
                null
            }
        }
    }

    private fun fetchWikipediaSnippet(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ZackAI/1.0 (Android App)")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val snippetRegex = Regex("\"snippet\":\\s*\"(.*?)\"")
                    val titleRegex = Regex("\"title\":\\s*\"(.*?)\"")
                    val titleMatch = titleRegex.find(body)?.groupValues?.get(1)
                    val snippetMatch = snippetRegex.find(body)?.groupValues?.get(1)
                    if (!titleMatch.isNullOrBlank() && !snippetMatch.isNullOrBlank()) {
                        val cleanSnippet = cleanHtmlEntities(snippetMatch.replace(Regex("<[^>]+>"), "").replace("\\\"", "\""))
                        "$titleMatch: $cleanSnippet"
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanQueryForSearch(query: String): String {
        return query
            .replace(Regex("^(dime|cuéntame|cuentame|sabes|busca|averigua|podrías decirme|podrias decirme|quiero saber|quién es|quien es|qué es|que es|cuál es|cual es|cuáles son|cuales son)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[¿?¡!.,;:()]"), " ")
            .trim()
            .ifBlank { query }
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#8216;", "'")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .trim()
    }
}
