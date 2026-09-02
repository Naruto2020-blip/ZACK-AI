package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ModelQuotaRecordEntity
import com.example.data.local.ReminderTaskEntity
import com.example.data.model.GeminiModelSpec
import com.example.data.model.QuotaResetHelper
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()
    val favoriteMessages: Flow<List<ChatMessageEntity>> = chatDao.getFavoriteMessages()
    val allTasks: Flow<List<ReminderTaskEntity>> = chatDao.getAllTasks()

    fun searchSessions(query: String): Flow<List<ChatSessionEntity>> {
        return chatDao.searchSessions(query)
    }

    suspend fun toggleMessageFavorite(messageId: Long, isFavorite: Boolean) {
        chatDao.updateMessageFavorite(messageId, isFavorite)
    }

    suspend fun setMessageFavorite(messageId: Long, isFavorite: Boolean) {
        chatDao.updateMessageFavorite(messageId, isFavorite)
    }

    suspend fun insertTask(task: ReminderTaskEntity): Long {
        return chatDao.insertTask(task)
    }

    suspend fun insertTask(title: String, reminderDateTime: Long? = null): Long {
        val task = ReminderTaskEntity(
            title = title,
            reminderDateTime = reminderDateTime,
            createdAt = System.currentTimeMillis(),
            isCompleted = false
        )
        return chatDao.insertTask(task)
    }

    suspend fun updateTask(task: ReminderTaskEntity) {
        chatDao.updateTask(task)
    }

    suspend fun updateTaskCompleted(taskId: Long, isCompleted: Boolean) {
        chatDao.updateTaskCompleted(taskId, isCompleted)
    }

    suspend fun deleteTask(id: Long) {
        chatDao.deleteTaskById(id)
    }

    suspend fun clearCompletedTasks() {
        chatDao.clearCompletedTasks()
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity> {
        return chatDao.getMessagesForSessionSync(sessionId)
    }

    suspend fun createNewSession(
        title: String = "",
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
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun clearAll() {
        chatDao.clearAllMessages()
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
            // Auto update session title with the beginning of the first user message
            val updatedTitle = if ((session.title.isBlank() || session.title == "Nueva Conversación" || session.title == "Conversación Principal") && role == "user") {
                val cleanPrompt = content.replace(Regex("^\\[(📷 Foto|📂 Documento).*?\\]\\n\\n", RegexOption.DOT_MATCHES_ALL), "").trim()
                val snippet = if (cleanPrompt.isNotBlank()) cleanPrompt else content
                val firstLine = snippet.lines().firstOrNull { it.isNotBlank() }?.trim() ?: snippet
                if (firstLine.length > 35) firstLine.take(35).trim() + "..." else firstLine
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
