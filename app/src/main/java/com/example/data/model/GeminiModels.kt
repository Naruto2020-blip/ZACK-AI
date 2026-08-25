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
    GEMINI_3_7_FLASH(
        id = "gemini-3.7-flash",
        displayName = "Gemini 3.7 Flash",
        roleBadge = "Principal / Recomendado",
        isPrimary = true,
        orderIndex = 1,
        description = "Máximo razonamiento híbrido y velocidad ultrarrápida. Primer modelo al que la app envía tus consultas.",
        speedRating = 5,
        reasoningRating = 5,
        fallbackAliases = listOf("gemini-3.7-flash-preview", "gemini-2.5-flash", "gemini-flash-latest")
    ),
    GEMINI_2_5_FLASH(
        id = "gemini-2.5-flash",
        displayName = "Gemini 2.5 Flash",
        roleBadge = "Respaldo #1",
        isPrimary = false,
        orderIndex = 2,
        description = "Excelente balance entre razonamiento general y alta velocidad de inferencia.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.5-flash-preview-05-20", "gemini-2.0-flash")
    ),
    GEMINI_2_5_FLASH_LITE(
        id = "gemini-2.5-flash-lite",
        displayName = "Gemini 2.5 Flash-Lite",
        roleBadge = "Respaldo #2",
        isPrimary = false,
        orderIndex = 3,
        description = "Optimizado para latencia ultrabaja, alta eficiencia y mínimo costo computacional.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.5-flash-lite-preview-02-05", "gemini-2.0-flash-lite")
    ),
    GEMINI_2_0_FLASH(
        id = "gemini-2.0-flash",
        displayName = "Gemini 2.0 Flash",
        roleBadge = "Respaldo #3",
        isPrimary = false,
        orderIndex = 4,
        description = "Generación ágil multimodal de última generación con respuestas dinámicas.",
        speedRating = 5,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-2.0-flash-exp", "gemini-1.5-flash")
    ),
    GEMINI_2_0_FLASH_LITE(
        id = "gemini-2.0-flash-lite",
        displayName = "Gemini 2.0 Flash-Lite",
        roleBadge = "Respaldo #4",
        isPrimary = false,
        orderIndex = 5,
        description = "Micro-modelo diseñado para tareas de respuesta inmediata y alto throughput.",
        speedRating = 5,
        reasoningRating = 3,
        fallbackAliases = listOf("gemini-2.0-flash-lite-preview-02-05", "gemini-1.5-flash-8b")
    ),
    GEMINI_2_5_PRO(
        id = "gemini-2.5-pro",
        displayName = "Gemini 2.5 Pro",
        roleBadge = "Respaldo #5",
        isPrimary = false,
        orderIndex = 6,
        description = "Razonamiento complejo avanzado, lógica matemática, análisis y generación de código profundo.",
        speedRating = 4,
        reasoningRating = 5,
        fallbackAliases = listOf("gemini-2.5-pro-preview-03-25", "gemini-1.5-pro")
    ),
    GEMINI_1_5_FLASH(
        id = "gemini-1.5-flash",
        displayName = "Gemini 1.5 Flash",
        roleBadge = "Respaldo #6",
        isPrimary = false,
        orderIndex = 7,
        description = "Modelo consolidado con gran estabilidad, multimodalidad y ventana de contexto.",
        speedRating = 4,
        reasoningRating = 4,
        fallbackAliases = listOf("gemini-1.5-flash-latest")
    ),
    GEMINI_1_5_FLASH_8B(
        id = "gemini-1.5-flash-8b",
        displayName = "Gemini 1.5 Flash-8B",
        roleBadge = "Respaldo #7",
        isPrimary = false,
        orderIndex = 8,
        description = "Variante hiper-ligera de 8 billones de parámetros para consultas rápidas.",
        speedRating = 5,
        reasoningRating = 3,
        fallbackAliases = listOf("gemini-1.5-flash-8b-latest")
    ),
    GEMINI_1_5_PRO(
        id = "gemini-1.5-pro",
        displayName = "Gemini 1.5 Pro",
        roleBadge = "Respaldo #8",
        isPrimary = false,
        orderIndex = 9,
        description = "Excelente capacidad analítica y manejo de amplios volúmenes de contexto.",
        speedRating = 3,
        reasoningRating = 5,
        fallbackAliases = listOf("gemini-1.5-pro-latest")
    );

    companion object {
        val ALL_CASCADE_ORDER = listOf(
            GEMINI_3_7_FLASH,
            GEMINI_2_5_FLASH,
            GEMINI_2_5_FLASH_LITE,
            GEMINI_2_0_FLASH,
            GEMINI_2_0_FLASH_LITE,
            GEMINI_2_5_PRO,
            GEMINI_1_5_FLASH,
            GEMINI_1_5_FLASH_8B,
            GEMINI_1_5_PRO
        )

        fun fromId(id: String): GeminiModelSpec {
            return ALL_CASCADE_ORDER.find { it.id == id || it.fallbackAliases.contains(id) }
                ?: GEMINI_3_7_FLASH
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
