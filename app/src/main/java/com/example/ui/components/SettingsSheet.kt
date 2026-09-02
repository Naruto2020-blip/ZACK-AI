package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeminiModelSpec
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepIndigo
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.RadiantViolet
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.ChatUiState

private data class PersonaOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: ChatUiState,
    onDismiss: () -> Unit,
    onSetPersona: (String) -> Unit,
    onSaveApiKey: ((String) -> Unit)? = null,
    currentThemeMode: String = "dark",
    onSetThemeMode: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var apiKeyInput by androidx.compose.runtime.remember(uiState.currentApiKey) {
        androidx.compose.runtime.mutableStateOf(uiState.currentApiKey)
    }
    var showApiKeyInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }

    val personas = listOf(
        PersonaOption(
            title = "🔄 Asistente Inteligente",
            description = "Vuelve a la IA original, sin especialidad. Es el modo por defecto.",
            icon = Icons.Default.AutoAwesome,
            accentColor = ElectricCyan
        ),
        PersonaOption(
            title = "⚖️ Abogado",
            description = "Redacta contratos, cartas legales, autorizaciones, renuncias, explica derechos y leyes en lenguaje claro.",
            icon = Icons.Default.SmartToy,
            accentColor = Color(0xFFF59E0B)
        ),
        PersonaOption(
            title = "👨‍⚕️ Médico / Doctor",
            description = "Explica síntomas, da consejos de salud, explica términos médicos, cuándo ir al médico, cuidados generales.",
            icon = Icons.Default.SmartToy,
            accentColor = Color(0xFF10B981)
        ),
        PersonaOption(
            title = "🧠 Psicólogo",
            description = "Apoyo emocional, escucha sin juzgar, da consejos para manejo de emociones, estrés, relaciones y bienestar.",
            icon = Icons.Default.Psychology,
            accentColor = RadiantViolet
        ),
        PersonaOption(
            title = "✍️ Redactor / Escritor",
            description = "Escribe cartas, correos, ensayos, discursos, textos creativos, profesionales y personalizados.",
            icon = Icons.Default.Create,
            accentColor = Color(0xFFEC4899)
        ),
        PersonaOption(
            title = "📚 Profesor / Tutor",
            description = "Explica temas difíciles paso a paso, ayuda con tareas, resúmenes, ejercicios, prepara exámenes.",
            icon = Icons.Default.Code,
            accentColor = Color(0xFF3B82F6)
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimaryDark,
        dragHandle = null
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = ObsidianBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ajustes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Configuración del Asistente",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondaryDark
                    )
                }
            }

            HorizontalDivider(color = ObsidianCardBorder, modifier = Modifier.padding(bottom = 16.dp))

            // =========================================================================
            // 🤖 BOTÓN: MODELOS (Abre pantalla nueva de Modelos)
            // =========================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showModelsDialog = true }
                    .testTag("open_models_screen_button"),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianCard
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Modelos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Ver créditos, cuotas y modelos de respaldo",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Abrir Modelos",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 🌙 MODO DE APARIENCIA: Oscuro / Claro / Seguir sistema
            // =========================================================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🌙 Modo de Apariencia",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Selecciona el tema visual de la aplicación. Se guarda automáticamente.",
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple("dark", "Modo Oscuro", "🌙"),
                            Triple("light", "Modo Claro", "☀️"),
                            Triple("system", "Sistema", "📱")
                        )

                        themeOptions.forEach { (mode, label, emoji) ->
                            val isSelected = currentThemeMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSetThemeMode(mode) }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else ObsidianCardBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                color = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = emoji, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ElectricCyan else TextSecondaryDark,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: ⏱️ Renovación Diaria de Cuotas + Contador de tiempo
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F2B1D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Renovación Diaria de Cuotas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Restablecimiento automático cada 24 horas (00:00 UTC)",
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Countdown Display Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0B1320),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Próxima renovación en:",
                                    color = TextSecondaryDark,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Automática y continua",
                                    color = EmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = uiState.timeUntilUtcReset,
                                color = ElectricCyan,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: 🔑 Clave de API de Gemini
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1E38)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "API Key de Google Gemini",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = if (uiState.isApiKeyConfigured) "● Clave activa y enlazada" else "○ Clave no configurada",
                                    color = if (uiState.isApiKeyConfigured) EmeraldGreen else Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        androidx.compose.material3.TextButton(
                            onClick = { showApiKeyInput = !showApiKeyInput }
                        ) {
                            Text(
                                text = if (showApiKeyInput) "Ocultar" else "Editar / Probar Clave",
                                color = CyanAccent,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (showApiKeyInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_api_key_field"),
                            placeholder = { Text("Pega tu API Key de AI Studio...", color = TextSecondaryDark, fontSize = 13.sp) },
                            singleLine = true,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = TextPrimaryDark,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    onSaveApiKey?.invoke(apiKeyInput)
                                    showApiKeyInput = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBackground
                                )
                            ) {
                                Text("Guardar Clave", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: 🎭 Rol / Persona del Sistema
            Text(
                text = "Rol y Personalidad del Asistente",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Selecciona cómo deseas que responda la IA en tus consultas:",
                color = TextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                personas.forEach { persona ->
                    val isSelected = uiState.systemPersona == persona.title ||
                            (uiState.systemPersona.contains("Asistente") && persona.title.contains("Asistente")) ||
                            (persona.title.contains("Abogado") && uiState.systemPersona.contains("Abogado")) ||
                            (persona.title.contains("Médico") && uiState.systemPersona.contains("Médico")) ||
                            (persona.title.contains("Psicólogo") && uiState.systemPersona.contains("Psicólogo")) ||
                            (persona.title.contains("Redactor") && uiState.systemPersona.contains("Redactor")) ||
                            (persona.title.contains("Profesor") && uiState.systemPersona.contains("Profesor"))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetPersona(persona.title) }
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) persona.accentColor else ObsidianCardBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF131D2E) else ObsidianCard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) persona.accentColor.copy(alpha = 0.2f)
                                        else Color(0xFF1E293B)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = persona.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) persona.accentColor else TextSecondaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = persona.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimaryDark else Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = persona.description,
                                    color = TextSecondaryDark,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(persona.accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = ObsidianBackground,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pantalla completa de Modelos (con toda la información intacta)
    if (showModelsDialog) {
        ModelsScreenDialog(
            uiState = uiState,
            onDismiss = { showModelsDialog = false }
        )
    }
}
