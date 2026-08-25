package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ModelQuotaRecordEntity
import com.example.data.model.GeminiModelSpec
import com.example.data.model.QuotaResetHelper
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity> {
        return chatDao.getMessagesForSessionSync(sessionId)
    }

    suspend fun createNewSession(
        title: String = "Nueva Conversación",
        systemPersona: String = "default"
    ): String {
        val id = UUID.randomUUID().toString()
        val session = ChatSessionEntity(
            id = id,
            title = title,
            systemPersona = systemPersona
        )
        chatDao.insertSession(session)
        return id
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(session.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateSessionPersona(sessionId: String, persona: String) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            chatDao.updateSession(session.copy(systemPersona = persona, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun clearAll() {
        chatDao.clearAllSessions()
    }

    suspend fun insertMessage(
        sessionId: String,
        role: String,
        content: String,
        modelUsed: String? = null,
        wasCascaded: Boolean = false,
        cascadeReason: String? = null,
        latencyMs: Long = 0L,
        isError: Boolean = false
    ): Long {
        val msg = ChatMessageEntity(
            sessionId = sessionId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            modelUsed = modelUsed,
            wasCascaded = wasCascaded,
            cascadeReason = cascadeReason,
            latencyMs = latencyMs,
            isError = isError
        )
        val id = chatDao.insertMessage(msg)
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            // Auto update session title if it's the first user message
            val updatedTitle = if (session.title == "Nueva Conversación" && role == "user") {
                content.take(30).trim() + if (content.length > 30) "..." else ""
            } else {
                session.title
            }
            chatDao.updateSession(
                session.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    fun getQuotaRecords(): Flow<List<ModelQuotaRecordEntity>> {
        val today = QuotaResetHelper.getTodayUtcDateString()
        return chatDao.getQuotaRecordsForDate(today)
    }

    suspend fun recordModelUsage(
        model: GeminiModelSpec,
        success: Boolean,
        latencyMs: Long,
        isQuotaExhausted: Boolean = false,
        errorReason: String? = null
    ) {
        val today = QuotaResetHelper.getTodayUtcDateString()
        val current = chatDao.getQuotaRecord(model.id, today)
        val updated = if (current != null) {
            current.copy(
                requestCount = current.requestCount + 1,
                isQuotaExhausted = current.isQuotaExhausted || isQuotaExhausted,
                lastFailureReason = if (!success) errorReason else current.lastFailureReason,
                lastLatencyMs = latencyMs,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            ModelQuotaRecordEntity(
                modelId = model.id,
                dateUtc = today,
                requestCount = 1,
                isQuotaExhausted = isQuotaExhausted,
                lastFailureReason = errorReason,
                lastLatencyMs = latencyMs,
                updatedAt = System.currentTimeMillis()
            )
        }
        chatDao.saveQuotaRecord(updated)
    }

    suspend fun resetAllDailyQuotas() {
        val today = QuotaResetHelper.getTodayUtcDateString()
        chatDao.resetAllQuotasForDate(today)
    }
}
