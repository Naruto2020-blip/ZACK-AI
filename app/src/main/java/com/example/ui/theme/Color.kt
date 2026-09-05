package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// CompositionLocal to track if dark theme is currently active
val LocalAppIsDark = compositionLocalOf { true }

@Composable
fun isAppDark(): Boolean = LocalAppIsDark.current

// Brand Accents
val ElectricCyan = Color(0xFF00E5FF)
val CyanAccent = Color(0xFF38BDF8)
val RadiantViolet = Color(0xFF8B5CF6)
val NeonPurple = Color(0xFFA855F7)
val DeepIndigo = Color(0xFF6366F1)
val NeonPink = Color(0xFFFF3385)

// Status & Quota Colors
val EmeraldGreen = Color(0xFF10B981)
val AmberGold = Color(0xFFF59E0B)
val RoseRed = Color(0xFFEF4444)
val CrimsonRed = Color(0xFFDC2626)
val SlateBlue = Color(0xFF3B82F6)

// Raw Dark Theme Surfaces & Texts
val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF111827)
val DarkCard = Color(0xFF1B2234)
val DarkCardBorder = Color(0xFF2E384D)
val DarkSubtle = Color(0xFF242E42)
val DarkTextPrimary = Color(0xFFF3F4F6)
val DarkTextSecondary = Color(0xFF9CA3AF)
val DarkTextTertiary = Color(0xFF6B7280)

// Raw Light Theme Surfaces & Texts (Crisp white background, rich dark text)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF8FAFC)
val LightCardBorder = Color(0xFFE2E8F0)
val LightSubtle = Color(0xFFF1F5F9)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF64748B)

// Theme-Aware Dynamic Colors
// In dark mode: Obsidian colors. In light mode: Pure white/clean surfaces with dark high-contrast typography.
val ObsidianBackground: Color
    @Composable get() = if (LocalAppIsDark.current) DarkBackground else LightBackground

val ObsidianSurface: Color
    @Composable get() = if (LocalAppIsDark.current) DarkSurface else LightSurface

val ObsidianCard: Color
    @Composable get() = if (LocalAppIsDark.current) DarkCard else LightCard

val ObsidianCardBorder: Color
    @Composable get() = if (LocalAppIsDark.current) DarkCardBorder else LightCardBorder

val ObsidianSubtle: Color
    @Composable get() = if (LocalAppIsDark.current) DarkSubtle else LightSubtle

val TextPrimaryDark: Color
    @Composable get() = if (LocalAppIsDark.current) DarkTextPrimary else LightTextPrimary

val TextSecondaryDark: Color
    @Composable get() = if (LocalAppIsDark.current) DarkTextSecondary else LightTextSecondary

val TextTertiaryDark: Color
    @Composable get() = if (LocalAppIsDark.current) DarkTextTertiary else LightTextTertiary
