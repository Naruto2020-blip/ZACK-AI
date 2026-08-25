package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val systemPersona: String = "default",
    val preferredModelId: String? = null
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "user", "model", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val wasCascaded: Boolean = false,
    val cascadeReason: String? = null,
    val latencyMs: Long = 0L,
    val isError: Boolean = false
)

@Entity(tableName = "model_quota_records")
data class ModelQuotaRecordEntity(
    @PrimaryKey val modelId: String,
    val dateUtc: String,
    val requestCount: Int = 0,
    val isQuotaExhausted: Boolean = false,
    val lastFailureReason: String? = null,
    val lastLatencyMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
