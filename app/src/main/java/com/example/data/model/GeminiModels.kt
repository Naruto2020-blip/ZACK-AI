package com.example.data.model

import com.squareup.moshi.JsonClass
import java.util.Calendar
import java.util.TimeZone

/**
 * Metadata and specifications for the Gemini models in the cascade.
 */
enum class GeminiModelSpec(
    val id: String,
    val displayName: String,
    val roleBadge: String,
    val isPrimary: Boolean,
    val orderIndex: Int,
    val description: String,
    val speedRating: Int, // 1-5
    val reasoningRating: Int, // 1-5
    val fallbackAliases: List<String> = emptyList()
) {
    GEMINI_FLASH_LATEST(
        id = "gemini-2.5-flash",
        displayName = "Gemini 2.5 Flash",
        roleBadge = "Principal (Máxima Disponibilidad)",
        isPrimary = true,
        orderIndex = 1,
        description = "Nodo ultrarrápido y multimodal con la mejor velocidad y disponibilidad.",
        speedRating = 5,
        reasoningRating = 5,
        fallbackAliases = listOf("gemini-2.0-flash", "gemini-1.5-flash")
    ),
    GEMINI_3_5_FLASH(
        id = "gemini-2.0-flash",
        displayName = "Gemini 2.0 Flash",
        roleBadge = "Respaldo #1 (Alta Velocidad)",
        isPrimary = false,
        orderIndex = 2,
        description = "Excelente velocidad de respuesta y multimodalidad para uso general diario.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.5-flash", "gemini-1.5-flash")
    ),
    GEMINI_3_7_FLASH(
        id = "gemini-1.5-flash",
        displayName = "Gemini 1.5 Flash",
        roleBadge = "Respaldo #2 (Universal)",
        isPrimary = false,
        orderIndex = 3,
        description = "Máxima compatibilidad y disponibilidad global garantizada.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.0-flash")
    ),
    GEMINI_3_1_PRO(
        id = "gemini-2.5-pro",
        displayName = "Gemini 2.5 Pro",
        roleBadge = "Respaldo #3 (Pro / Razonamiento)",
        isPrimary = false,
        orderIndex = 4,
        description = "Razonamiento lógico complejo, matemáticas, código y análisis profundo.",
        speedRating = 4,
        reasoningRating = 5,
        fallbackAliases = listOf("gemini-2.5-flash")
    ),
    GEMINI_3_1_FLASH_LITE(
        id = "gemini-2.0-flash-lite",
        displayName = "Gemini 2.0 Flash-Lite",
        roleBadge = "Respaldo #4 (Baja Latencia)",
        isPrimary = false,
        orderIndex = 5,
        description = "Modelo ligero optimizado para respuestas instantáneas de mínima latencia.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.5-flash")
    );

    companion object {
        val ALL_CASCADE_ORDER = listOf(
            GEMINI_FLASH_LATEST,
            GEMINI_3_5_FLASH,
            GEMINI_3_7_FLASH,
            GEMINI_3_1_PRO,
            GEMINI_3_1_FLASH_LITE
        )

        fun fromId(id: String): GeminiModelSpec {
            return ALL_CASCADE_ORDER.find { it.id == id || it.fallbackAliases.contains(id) }
                ?: GEMINI_FLASH_LATEST
        }
    }
}

enum class ModelHealthStatus {
    AVAILABLE,
    STANDBY,
    DAILY_QUOTA_EXHAUSTED,
    OVERLOADED,
    ERROR
}

data class ModelRuntimeStatus(
    val spec: GeminiModelSpec,
    val status: ModelHealthStatus = ModelHealthStatus.AVAILABLE,
    val callsToday: Int = 0,
    val successfulCalls: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastErrorMessage: String? = null,
    val lastActiveTimestamp: Long = 0L
)

data class CascadeHop(
    val fromModel: GeminiModelSpec,
    val toModel: GeminiModelSpec,
    val reason: String,
    val httpCode: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class CascadeExecutionResult(
    val content: String,
    val usedModel: GeminiModelSpec,
    val requestedPrimaryModel: GeminiModelSpec,
    val wasCascaded: Boolean,
    val hops: List<CascadeHop> = emptyList(),
    val latencyMs: Long = 0L,
    val isError: Boolean = false
)

object QuotaResetHelper {
    /**
     * Calculates the time remaining until the next 00:00:00 UTC daily quota reset.
     */
    fun getTimeUntilNextUtcReset(): Pair<Long, String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 24)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val resetTime = calendar.timeInMillis
        val diffMs = (resetTime - now).coerceAtLeast(0)

        val hours = diffMs / (1000 * 60 * 60)
        val minutes = (diffMs / (1000 * 60)) % 60
        val seconds = (diffMs / 1000) % 60

        return Pair(diffMs, String.format("%02dh %02dm %02ds", hours, minutes, seconds))
    }

    fun getTodayUtcDateString(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", y, m, d)
    }
}
