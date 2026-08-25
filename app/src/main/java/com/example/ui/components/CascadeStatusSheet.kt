package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeminiModelSpec
import com.example.data.model.ModelHealthStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepIndigo
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.RadiantViolet
import com.example.ui.theme.RoseRed
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CascadeStatusSheet(
    uiState: ChatUiState,
    onDismiss: () -> Unit,
    onSelectModel: (GeminiModelSpec) -> Unit,
    onToggleAutoCascade: (Boolean) -> Unit,
    onSetPersona: (String) -> Unit,
    onSetTemperature: (Float) -> Unit,
    onRunDiagnostics: () -> Unit,
    onResetQuotas: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimaryDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = ObsidianBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Arquitectura de Cascada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "9 Modelos Gratuitos con Renovación Diaria",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondaryDark
                    )
                }
            }

            // Quota Reset Timer Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10192A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Renovación Diaria de Cuotas (00:00 UTC)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Restante: ${uiState.timeUntilUtcReset}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onResetQuotas,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("reset_quota_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Restablecer Cuotas",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianBackground,
                contentColor = ElectricCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = ElectricCyan,
                        height = 3.dp
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Modelos & Cascada",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Ajustes & Parámetros",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // Models Tab
                CascadeModelsList(
                    uiState = uiState,
                    onSelectModel = onSelectModel,
                    onToggleAutoCascade = onToggleAutoCascade,
                    onRunDiagnostics = onRunDiagnostics
                )
            } else {
                // Settings Tab
                CascadeSettingsPanel(
                    uiState = uiState,
                    onSetPersona = onSetPersona,
                    onSetTemperature = onSetTemperature,
                    onResetQuotas = onResetQuotas
                )
            }
        }
    }
}

@Composable
fun CascadeModelsList(
    uiState: ChatUiState,
    onSelectModel: (GeminiModelSpec) -> Unit,
    onToggleAutoCascade: (Boolean) -> Unit,
    onRunDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Auto Cascade Mode Switch Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = ObsidianCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cascada Automática Inteligente",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                    }
                    Text(
                        text = if (uiState.isAutoCascadeEnabled)
                            "Si se alcanza la cuota diaria en 3.7 Flash, conmuta automáticamente a los 8 modelos de respaldo."
                        else "Modo manual: solo se usará el modelo seleccionado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Switch(
                    checked = uiState.isAutoCascadeEnabled,
                    onCheckedChange = onToggleAutoCascade,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondaryDark,
                        uncheckedTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.testTag("auto_cascade_switch")
                )
            }
        }

        // Diagnostics / Ping Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cadena de 9 Modelos (Orden de Fallback)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )

            OutlinedButton(
                onClick = onRunDiagnostics,
                enabled = !uiState.isRunningDiagnostics,
                modifier = Modifier.testTag("diagnostics_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ElectricCyan
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
            ) {
                if (uiState.isRunningDiagnostics) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probando...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diagnóstico", fontSize = 12.sp)
                }
            }
        }

        // 9 Models Cascade List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(GeminiModelSpec.ALL_CASCADE_ORDER) { index, spec ->
                val runtimeStatus = uiState.modelRuntimeStatuses.find { it.spec == spec }
                val isSelected = uiState.selectedModel == spec

                ModelCascadeItemCard(
                    spec = spec,
                    index = index + 1,
                    isSelected = isSelected,
                    isPrimary = spec.isPrimary,
                    runtimeStatus = runtimeStatus,
                    onSelect = { onSelectModel(spec) }
                )
            }
        }
    }
}

@Composable
fun ModelCascadeItemCard(
    spec: GeminiModelSpec,
    index: Int,
    isSelected: Boolean,
    isPrimary: Boolean,
    runtimeStatus: com.example.data.model.ModelRuntimeStatus?,
    onSelect: () -> Unit
) {
    val isQuotaExhausted = runtimeStatus?.status == ModelHealthStatus.DAILY_QUOTA_EXHAUSTED
    val isError = runtimeStatus?.status == ModelHealthStatus.ERROR

    val cardBorderColor = when {
        isSelected -> ElectricCyan
        isPrimary -> RadiantViolet.copy(alpha = 0.6f)
        isQuotaExhausted -> RoseRed.copy(alpha = 0.5f)
        else -> ObsidianCardBorder
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("model_item_${spec.id}"),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF132238) else ObsidianCard,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number / Status Node
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isQuotaExhausted -> RoseRed.copy(alpha = 0.2f)
                            isPrimary -> RadiantViolet.copy(alpha = 0.2f)
                            isSelected -> ElectricCyan.copy(alpha = 0.2f)
                            else -> Color(0xFF1E293B)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    color = when {
                        isQuotaExhausted -> RoseRed
                        isPrimary -> RadiantViolet
                        isSelected -> ElectricCyan
                        else -> TextSecondaryDark
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = spec.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) ElectricCyan else TextPrimaryDark
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Role Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isPrimary) RadiantViolet.copy(alpha = 0.2f) else DeepIndigo.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = spec.roleBadge,
                                color = if (isPrimary) RadiantViolet else CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Health Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            isQuotaExhausted -> RoseRed.copy(alpha = 0.15f)
                            isSelected -> EmeraldGreen.copy(alpha = 0.15f)
                            else -> EmeraldGreen.copy(alpha = 0.1f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isQuotaExhausted -> RoseRed
                                            isSelected -> EmeraldGreen
                                            else -> EmeraldGreen.copy(alpha = 0.7f)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isQuotaExhausted -> "Cuota Agotada"
                                    isSelected -> "Activo"
                                    else -> "En Espera"
                                },
                                color = when {
                                    isQuotaExhausted -> RoseRed
                                    isSelected -> EmeraldGreen
                                    else -> EmeraldGreen.copy(alpha = 0.8f)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Latency and stats row
                if (runtimeStatus != null && runtimeStatus.callsToday > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Consultas hoy: ${runtimeStatus.callsToday}",
                            color = TextSecondaryDark,
                            fontSize = 10.sp
                        )
                        if (runtimeStatus.lastLatencyMs > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•  Latencia: ${runtimeStatus.lastLatencyMs}ms",
                                color = CyanAccent,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CascadeSettingsPanel(
    uiState: ChatUiState,
    onSetPersona: (String) -> Unit,
    onSetTemperature: (Float) -> Unit,
    onResetQuotas: () -> Unit
) {
    val personas = listOf(
        "Asistente Inteligente",
        "Programador Experto",
        "Razonamiento & Análisis",
        "Redactor Creativo"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Persona Selection
        Text(
            text = "Rol / Persona del Sistema",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CyanAccent,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            personas.take(2).forEach { persona ->
                val isSelected = uiState.systemPersona == persona
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetPersona(persona) },
                    label = { Text(persona, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan,
                        containerColor = ObsidianCard,
                        labelColor = TextSecondaryDark
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            personas.drop(2).forEach { persona ->
                val isSelected = uiState.systemPersona == persona
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetPersona(persona) },
                    label = { Text(persona, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan,
                        containerColor = ObsidianCard,
                        labelColor = TextSecondaryDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Temperature Slider
        Text(
            text = "Nivel de Creatividad / Temperatura: ${String.format("%.1f", uiState.temperature)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CyanAccent
        )

        Slider(
            value = uiState.temperature,
            onValueChange = onSetTemperature,
            valueRange = 0.0f..1.0f,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Preciso / Código (0.0)", color = TextSecondaryDark, fontSize = 10.sp)
            Text("Equilibrado (0.7)", color = TextSecondaryDark, fontSize = 10.sp)
            Text("Creativo (1.0)", color = TextSecondaryDark, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual Quota Reset button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = ObsidianCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restablecer Cuotas Manualmente",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Limpia banderas de cuotas agotadas para simular el inicio de un nuevo ciclo diario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onResetQuotas,
                    colors = ButtonDefaults.buttonColors(containerColor = RadiantViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reset_all_quotas_btn")
                ) {
                    Text("Restablecer", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
