package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // --- Sessions ---
    @Query("SELECT * FROM chat_sessions WHERE id IN (SELECT DISTINCT sessionId FROM chat_messages) ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    // --- Messages ---
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // --- Favorites ---
    @Query("UPDATE chat_messages SET isFavorite = :isFavorite WHERE id = :messageId")
    suspend fun updateMessageFavorite(messageId: Long, isFavorite: Boolean)

    @Query("SELECT * FROM chat_messages WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteMessages(): Flow<List<ChatMessageEntity>>

    // --- Search Sessions ---
    @Query("SELECT * FROM chat_sessions WHERE id IN (SELECT DISTINCT sessionId FROM chat_messages) AND (title LIKE '%' || :query || '%' OR id IN (SELECT sessionId FROM chat_messages WHERE content LIKE '%' || :query || '%')) ORDER BY updatedAt DESC")
    fun searchSessions(query: String): Flow<List<ChatSessionEntity>>

    // --- Tasks & Reminders ---
    @Query("SELECT * FROM reminder_tasks ORDER BY isCompleted ASC, reminderDateTime ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<ReminderTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ReminderTaskEntity): Long

    @Update
    suspend fun updateTask(task: ReminderTaskEntity)

    @Query("UPDATE reminder_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskCompleted(id: Long, isCompleted: Boolean)

    @Query("DELETE FROM reminder_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM reminder_tasks WHERE isCompleted = 1")
    suspend fun clearCompletedTasks()

    // --- Quota & Health Tracking ---
    @Query("SELECT * FROM model_quota_records WHERE dateUtc = :dateUtc")
    fun getQuotaRecordsForDate(dateUtc: String): Flow<List<ModelQuotaRecordEntity>>

    @Query("SELECT * FROM model_quota_records WHERE modelId = :modelId AND dateUtc = :dateUtc LIMIT 1")
    suspend fun getQuotaRecord(modelId: String, dateUtc: String): ModelQuotaRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuotaRecord(record: ModelQuotaRecordEntity)

    @Query("DELETE FROM model_quota_records WHERE dateUtc != :todayUtc")
    suspend fun cleanOldQuotaRecords(todayUtc: String)

    @Query("UPDATE model_quota_records SET isQuotaExhausted = 0 WHERE dateUtc = :dateUtc")
    suspend fun resetAllQuotasForDate(dateUtc: String)
}
