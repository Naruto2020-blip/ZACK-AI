package com.example.data.model

import java.util.UUID

enum class ShoppingCategory(
    val id: String,
    val title: String,
    val emoji: String,
    val colorHex: Long
) {
    MEAT("carniceria", "CARNICERÍA", "🥩", 0xFFEF4444),
    VEGGIES("feria", "FERIA / VERDULERÍA", "🥬", 0xFF10B981),
    SUPERMARKET("supermercado", "PALÍ / SUPERMERCADO", "🏪", 0xFF3B82F6),
    OTHERS("varios", "VARIOS", "📦", 0xFF8B5CF6)
}

data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String = "",
    val category: ShoppingCategory,
    val isBought: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
