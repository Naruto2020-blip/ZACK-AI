package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.RadiantViolet
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

data class AiModeItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val emoji: String,
    val accentColor: Color
)

val AI_MODES_LIST = listOf(
    AiModeItem(
        key = "🔄 Asistente Inteligente",
        title = "🔄 ASISTENTE INTELIGENTE",
        subtitle = "Normal / Predeterminado",
        description = "Vuelve a la IA original, sin especialidad. Es el modo por defecto.",
        emoji = "🔄",
        accentColor = ElectricCyan
    ),
    AiModeItem(
        key = "⚖️ Abogado",
        title = "⚖️ ABOGADO",
        subtitle = "Leyes, Contratos y Trámites",
        description = "Redacta contratos, cartas legales, autorizaciones, renuncias, explica derechos y leyes en lenguaje claro.",
        emoji = "⚖️",
        accentColor = Color(0xFFF59E0B)
    ),
    AiModeItem(
        key = "👨‍⚕️ Médico / Doctor",
        title = "👨‍⚕️ MÉDICO / DOCTOR",
        subtitle = "Salud, Síntomas y Cuidados",
        description = "Explica síntomas, da consejos de salud, explica términos médicos, cuándo ir al médico, cuidados generales.",
        emoji = "👨‍⚕️",
        accentColor = Color(0xFF10B981)
    ),
    AiModeItem(
        key = "🧠 Psicólogo",
        title = "🧠 PSICÓLOGO",
        subtitle = "Apoyo Emocional y Bienestar",
        description = "Apoyo emocional, escucha sin juzgar, da consejos para manejo de emociones, estrés, relaciones y bienestar.",
        emoji = "🧠",
        accentColor = RadiantViolet
    ),
    AiModeItem(
        key = "✍️ Redactor / Escritor",
        title = "✍️ REDACTOR / ESCRITOR",
        subtitle = "Textos, Cartas y Ensayos",
        description = "Escribe cartas, correos, ensayos, discursos, textos creativos, profesionales y personalizados.",
        emoji = "✍️",
        accentColor = Color(0xFFEC4899)
    ),
    AiModeItem(
        key = "📚 Profesor / Tutor",
        title = "📚 PROFESOR / TUTOR",
        subtitle = "Tareas, Explicaciones y Exámenes",
        description = "Explica temas difíciles paso a paso, ayuda con tareas, resúmenes, ejercicios, prepara exámenes.",
        emoji = "📚",
        accentColor = Color(0xFF3B82F6)
    )
)

@Composable
fun ModesDialog(
    currentPersona: String,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = ObsidianBackground,
            border = BorderStroke(1.dp, ObsidianCardBorder),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🎭", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Modos de Especialidad",
                                color = TextPrimaryDark,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Elige la personalidad de la IA",
                                color = TextSecondaryDark,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Modes List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AI_MODES_LIST.forEach { mode ->
                        val isSelected = currentPersona.contains(mode.emoji) ||
                                currentPersona.equals(mode.title, ignoreCase = true) ||
                                (mode.key.contains("Asistente") && currentPersona.contains("Asistente"))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectMode(mode.key)
                                }
                                .testTag("mode_card_${mode.key}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) ObsidianCard else Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) mode.accentColor else ObsidianCardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(mode.accentColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = mode.emoji, fontSize = 20.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) mode.accentColor else TextPrimaryDark,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Seleccionado",
                                                tint = mode.accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = mode.subtitle,
                                        color = TextSecondaryDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 1.dp, bottom = 4.dp)
                                    )

                                    Text(
                                        text = mode.description,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "💡 Puedes cambiar el modo en cualquier momento desde el menú lateral.",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
