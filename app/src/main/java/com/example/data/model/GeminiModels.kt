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
    val totalDailyRequests: Int = 1500, // Total quota allocated per daily renewal cycle
    val fallbackAliases: List<String> = emptyList()
) {
    GEMINI_3_8_FLASH(
        id = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        roleBadge = "Principal (Alta Inteligencia y Velocidad)",
        isPrimary = true,
        orderIndex = 1,
        description = "Modelo oficial de última generación con razonamiento rápido y multimodalidad en milisegundos.",
        speedRating = 5,
        reasoningRating = 5,
        totalDailyRequests = 1500,
        fallbackAliases = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-flash-latest")
    ),
    GEMINI_3_5_FLASH_LITE(
        id = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        roleBadge = "Ultra Rápido (Sub-segundo)",
        isPrimary = false,
        orderIndex = 2,
        description = "Respuestas instantáneas de ultra baja latencia (menos de 1 segundo).",
        speedRating = 5,
        reasoningRating = 4,
        totalDailyRequests = 1500,
        fallbackAliases = listOf("gemini-3.1-flash-lite", "gemini-3.8-flash")
    ),
    GEMINI_3_1_FLASH_LITE(
        id = "gemini-3.1-flash-lite",
        displayName = "Gemini 3.1 Flash-Lite",
        roleBadge = "Respaldo #1 (Velocidad Pura)",
        isPrimary = false,
        orderIndex = 3,
        description = "Motor ligero de latencia mínima para respuestas directas e inmediatas.",
        speedRating = 5,
        reasoningRating = 4,
        totalDailyRequests = 1500,
        fallbackAliases = listOf("gemini-3.1-flash-lite-preview", "gemini-3.5-flash-lite", "gemini-3.8-flash")
    ),
    GEMINI_3_5_FLASH(
        id = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        roleBadge = "Respaldo #2 (Estable)",
        isPrimary = false,
        orderIndex = 4,
        description = "Modelo equilibrado para procesamiento de textos y tareas generales.",
        speedRating = 5,
        reasoningRating = 5,
        totalDailyRequests = 1500,
        fallbackAliases = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
    );

    companion object {
        // Alias for backward compatibility
        val GEMINI_FLASH_LATEST = GEMINI_3_8_FLASH

        val ALL_CASCADE_ORDER = listOf(
            GEMINI_3_8_FLASH,
            GEMINI_3_5_FLASH_LITE,
            GEMINI_3_1_FLASH_LITE,
            GEMINI_3_5_FLASH
        )

        fun fromId(id: String): GeminiModelSpec {
            return ALL_CASCADE_ORDER.find { it.id == id || it.fallbackAliases.contains(id) }
                ?: when {
                    id.contains("flash-lite", ignoreCase = true) -> GEMINI_3_5_FLASH_LITE
                    id.contains("3.5", ignoreCase = true) -> GEMINI_3_5_FLASH
                    id.contains("3.1", ignoreCase = true) -> GEMINI_3_1_FLASH_LITE
                    else -> GEMINI_3_8_FLASH
                }
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
    private val COSTA_RICA_TIMEZONE: TimeZone = TimeZone.getTimeZone("America/Costa_Rica") // UTC-6

    /**
     * Calculates the time remaining until the next 00:00:00 daily quota reset in Costa Rica time (UTC-6).
     */
    fun getTimeUntilNextUtcReset(): Pair<Long, String> {
        val calendar = Calendar.getInstance(COSTA_RICA_TIMEZONE)
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
        val calendar = Calendar.getInstance(COSTA_RICA_TIMEZONE)
        val y = calendar.get(Calendar.YEAR)
        val m = calendar.get(Calendar.MONTH) + 1
        val d = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", y, m, d)
    }
}
