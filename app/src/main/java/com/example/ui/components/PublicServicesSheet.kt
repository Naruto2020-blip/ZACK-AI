package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.GeminiClient
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class ServiceCategory(val label: String, val emoji: String, val icon: ImageVector) {
    TODOS("Todos", "🌟", Icons.Default.Public),
    SALUD("Salud (CCSS)", "🏥", Icons.Default.LocalHospital),
    BANCOS("Bancos", "🏦", Icons.Default.AccountBalance),
    CORREOS("Correos", "📮", Icons.Default.Mail),
    SERVICIOS_BASICOS("Agua/Luz/Tel", "💧", Icons.Default.WaterDamage),
    TRAMITES("Trámites/Gobierno", "🏛️", Icons.Default.AccountBalance),
    TRANSPORTE("Transporte", "🚌", Icons.Default.DirectionsBus),
    FERIADOS("Feriados y Cierres", "📅", Icons.Default.Event)
}

data class SupportedCountry(
    val code: String,
    val name: String,
    val flag: String,
    val timeZoneId: String,
    val timeZoneLabel: String
)

data class PublicServiceEntity(
    val id: String,
    val countryCode: String,
    val category: ServiceCategory,
    val institutionName: String,
    val shortSubtitle: String,
    val todaySchedule: String,
    val tomorrowSchedule: String,
    val location: String,
    val phone: String,
    val openDays: String,
    val holidaysNote: String,
    val importantNotes: String,
    val startHour: Int = 8,
    val endHour: Int = 16,
    val openOnSaturday: Boolean = false,
    val openOnSunday: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicServicesSheet(
    onDismiss: () -> Unit,
    onSendToChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val countries = remember {
        listOf(
            SupportedCountry("CR", "Costa Rica", "🇨🇷", "America/Costa_Rica", "Hora CR (UTC-6)"),
            SupportedCountry("PE", "Perú", "🇵🇪", "America/Lima", "Hora Perú (UTC-5)"),
            SupportedCountry("MX", "México", "🇲🇽", "America/Mexico_City", "Hora CDMX (UTC-6)"),
            SupportedCountry("CO", "Colombia", "🇨🇴", "America/Bogota", "Hora Colombia (UTC-5)"),
            SupportedCountry("ES", "España", "🇪🇸", "Europe/Madrid", "Hora España (UTC+1/2)"),
            SupportedCountry("GLOBAL", "Otro País", "🌎", TimeZone.getDefault().id, "Hora Local")
        )
    }

    // Detección automática del país del usuario
    val defaultCountry = remember {
        val deviceTz = TimeZone.getDefault().id
        val deviceCountry = Locale.getDefault().country
        when {
            deviceTz.contains("Costa_Rica", ignoreCase = true) || deviceCountry.equals("CR", ignoreCase = true) -> countries[0]
            deviceTz.contains("Lima", ignoreCase = true) || deviceCountry.equals("PE", ignoreCase = true) -> countries[1]
            deviceTz.contains("Mexico", ignoreCase = true) || deviceCountry.equals("MX", ignoreCase = true) -> countries[2]
            deviceTz.contains("Bogota", ignoreCase = true) || deviceCountry.equals("CO", ignoreCase = true) -> countries[3]
            deviceTz.contains("Madrid", ignoreCase = true) || deviceCountry.equals("ES", ignoreCase = true) -> countries[4]
            else -> countries[0] // Costa Rica por defecto según prompt principal
        }
    }

    var selectedCountry by remember { mutableStateOf(defaultCountry) }
    var selectedCategory by remember { mutableStateOf(ServiceCategory.TODOS) }
    var searchQuery by remember { mutableStateOf("") }

    // Consulta con IA en tiempo real para cualquier trámite o sede
    var aiQueryText by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }
    var searchStatusMessage by remember { mutableStateOf("Buscando información actualizada...") }
    var aiResponseText by remember { mutableStateOf<String?>(null) }
    var showAiQueryBox by remember { mutableStateOf(true) }

    // Reloj dinámico en tiempo real para el país seleccionado
    var currentCountryTimeText by remember { mutableStateOf("") }
    var currentDayOfWeek by remember { mutableStateOf(Calendar.MONDAY) }
    var currentHourOfDay by remember { mutableStateOf(12) }

    fun updateCurrentTime() {
        val tz = TimeZone.getTimeZone(selectedCountry.timeZoneId)
        val cal = Calendar.getInstance(tz)
        currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        currentHourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM · h:mm a", Locale("es", selectedCountry.code))
        sdf.timeZone = tz
        currentCountryTimeText = sdf.format(cal.time).replaceFirstChar { it.uppercase() }
    }

    LaunchedEffect(selectedCountry) {
        updateCurrentTime()
    }

    val servicesList = remember(selectedCountry) {
        getServicesForCountry(selectedCountry.code)
    }

    val filteredServices = remember(servicesList, selectedCategory, searchQuery) {
        servicesList.filter { item ->
            val matchCat = selectedCategory == ServiceCategory.TODOS || item.category == selectedCategory
            val matchSearch = searchQuery.isBlank() ||
                    item.institutionName.contains(searchQuery, ignoreCase = true) ||
                    item.shortSubtitle.contains(searchQuery, ignoreCase = true) ||
                    item.location.contains(searchQuery, ignoreCase = true) ||
                    item.importantNotes.contains(searchQuery, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimaryDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(ElectricCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏛️", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Servicios Públicos y Horarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Horarios en tiempo real, sedes y trámites oficiales",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondaryDark
                    )
                }
            }

            // Barra de Zona Horaria y País Automático
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(selectedCountry.flag, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedCountry.name.uppercase(),
                                color = ElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = selectedCountry.timeZoneLabel,
                                color = ElectricCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⏰ $currentCountryTimeText",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selector Horizontal de Países (Costa Rica 🇨🇷 destacado, Perú 🇵🇪, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                countries.forEach { c ->
                    val isSel = c.code == selectedCountry.code
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSel) ElectricCyan else ObsidianCard,
                        border = BorderStroke(1.dp, if (isSel) ElectricCyan else ObsidianCardBorder),
                        modifier = Modifier.clickable {
                            selectedCountry = c
                            aiResponseText = null
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.flag, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = c.name,
                                color = if (isSel) DarkBackground else TextPrimaryDark,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & AI Lookup toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar CCSS, bancos, AyA, correos...",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("public_services_search_field")
                )

                // Botón para consulta personalizada con IA / Búsqueda en Internet
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (showAiQueryBox) ElectricCyan.copy(alpha = 0.18f) else ObsidianCard,
                    border = BorderStroke(1.dp, if (showAiQueryBox) ElectricCyan else ObsidianCardBorder),
                    modifier = Modifier
                        .height(48.dp)
                        .clickable { showAiQueryBox = !showAiQueryBox }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showAiQueryBox) "Ocultar Buscador" else "🔍 Buscar en Internet",
                            color = if (showAiQueryBox) ElectricCyan else TextPrimaryDark,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 🔍 CAJA DE CONSULTA Y BÚSQUEDA EN INTERNET EN TIEMPO REAL
            AnimatedVisibility(
                visible = showAiQueryBox,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌐", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Búsqueda en Internet en Tiempo Real (${selectedCountry.name})",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ElectricCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "SITIOS OFICIALES",
                                    color = ElectricCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 📏 CAMPO DE TEXTO AMPLIO (Mínimo 3 líneas, letra legible, color claro sobre fondo oscuro)
                        OutlinedTextField(
                            value = aiQueryText,
                            onValueChange = { aiQueryText = it },
                            placeholder = {
                                Text(
                                    text = "Escribe la institución o sucursal que buscas (ej. EBAIS San Rafael de Heredia, BNCR San Pedro, Correos Escazú, dirección, teléfonos o requisitos)...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 18.sp
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 20.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = ElectricCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("public_services_ai_multiline_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (aiQueryText.isNotBlank()) {
                                    isAiLoading = true
                                    searchStatusMessage = "Buscando información actualizada..."
                                    aiResponseText = null
                                    coroutineScope.launch {
                                        val resp = executeAiPublicServiceQuery(
                                            context = context,
                                            country = selectedCountry.name,
                                            tzLabel = selectedCountry.timeZoneLabel,
                                            query = aiQueryText,
                                            onStatusUpdate = { searchStatusMessage = it }
                                        )
                                        isAiLoading = false
                                        aiResponseText = resp
                                    }
                                }
                            },
                            enabled = !isAiLoading && aiQueryText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DarkBackground,
                                disabledContainerColor = ObsidianCard,
                                disabledContentColor = TextSecondaryDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("public_services_search_internet_btn")
                        ) {
                            if (isAiLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = DarkBackground,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = searchStatusMessage,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkBackground
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Buscar en internet en tiempo real",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!aiResponseText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianCard,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("📋", fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Datos concretos oficiales:",
                                                color = ElectricCyan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(aiResponseText ?: ""))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copiar",
                                                    tint = TextSecondaryDark,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = aiResponseText ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chips de Categorías (Salud, Bancos, Correos, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ServiceCategory.values().forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ElectricCyan.copy(alpha = 0.18f) else ObsidianCard,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else ObsidianCardBorder
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.emoji, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.label,
                                color = if (isSelected) ElectricCyan else TextPrimaryDark,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lista de Servicios Públicos con Horarios y Detalles
            if (filteredServices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏛️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No se encontraron servicios con ese término",
                            color = TextSecondaryDark,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prueba usando la consulta con IA arriba para sedes específicas",
                            color = ElectricCyan,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredServices, key = { it.id }) { item ->
                        ServiceItemCard(
                            service = item,
                            currentDayOfWeek = currentDayOfWeek,
                            currentHourOfDay = currentHourOfDay,
                            onCall = { phone ->
                                try {
                                    val clean = phone.replace(Regex("[^0-9+]"), "")
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            },
                            onCopy = { textToCopy ->
                                clipboardManager.setText(AnnotatedString(textToCopy))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun ServiceItemCard(
    service: PublicServiceEntity,
    currentDayOfWeek: Int,
    currentHourOfDay: Int,
    onCall: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isOpenNow = remember(currentDayOfWeek, currentHourOfDay, service) {
        val isWeekday = currentDayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isSaturday = currentDayOfWeek == Calendar.SATURDAY
        val isSunday = currentDayOfWeek == Calendar.SUNDAY

        when {
            isWeekday -> currentHourOfDay in service.startHour until service.endHour
            isSaturday -> service.openOnSaturday && (currentHourOfDay in 9 until 13)
            isSunday -> service.openOnSunday
            else -> false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, if (expanded) ElectricCyan.copy(alpha = 0.5f) else ObsidianCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fila de Título y Estado Abierto/Cerrado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(service.category.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = service.institutionName,
                            color = TextPrimaryDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = service.shortSubtitle,
                            color = TextSecondaryDark,
                            fontSize = 11.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Badge Abierto / Cerrado
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOpenNow) Color(0xFF065F46) else Color(0xFF7F1D1D)
                ) {
                    Text(
                        text = if (isOpenNow) "🟢 ABIERTO" else "🔴 CERRADO",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = ObsidianCardBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Horario de Hoy y de Mañana
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📅 Horario Hoy:",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = service.todaySchedule,
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📅 Horario Mañana:",
                        color = AmberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = service.tomorrowSchedule,
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ubicación & Teléfono
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = service.location,
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = service.phone,
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Botón llamar
                    IconButton(
                        onClick = { onCall(service.phone) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Llamar",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // Botón copiar
                    IconButton(
                        onClick = {
                            val copySummary = """
                                🏛️ ${service.institutionName}
                                • Hoy: ${service.todaySchedule}
                                • Mañana: ${service.tomorrowSchedule}
                                • Ubicación: ${service.location}
                                • Teléfono: ${service.phone}
                                • Notas: ${service.importantNotes}
                            """.trimIndent()
                            onCopy(copySummary)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Sección Expandida con Feriados, Cierres Especiales y Trámites
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBackground,
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📌 Días y Jornada de Atención:",
                                color = TextPrimaryDark,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = service.openDays,
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )

                            Text(
                                text = "⚠️ Feriados y Cierres Especiales:",
                                color = AmberGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = service.holidaysNote,
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )

                            Text(
                                text = "📋 Requisitos y Notas de Trámite:",
                                color = ElectricCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = service.importantNotes,
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Realiza búsqueda ligera en internet en tiempo real para obtener datos actualizados de instituciones o sucursales.
 */
private suspend fun fetchWebSearchSnippets(query: String, country: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
        val searchKeywords = if (country.contains("Costa Rica", ignoreCase = true)) {
            "$query Costa Rica horario direccion telefono"
        } else {
            "$query $country horario direccion telefono"
        }
        val encoded = URLEncoder.encode(searchKeywords, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/110.0")
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val html = response.body?.string() ?: ""
            val regex = Regex("""class="result__snippet[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(html).take(4).map { match ->
                android.text.Html.fromHtml(match.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }.filter { it.isNotBlank() }.toList()
            if (matches.isNotEmpty()) {
                return@withContext matches.joinToString("\n• ", prefix = "DATOS ACTUALIZADOS EXTRAÍDOS DE INTERNET EN TIEMPO REAL:\n• ")
            }
        }
    } catch (_: Exception) {
        // En caso de corte o bloqueo, el modelo usará su base de conocimiento oficial
    }
    return@withContext ""
}

/**
 * Consulta inteligente en tiempo real sobre horarios, direcciones, teléfonos y servicios públicos.
 */
suspend fun executeAiPublicServiceQuery(
    context: Context,
    country: String,
    tzLabel: String,
    query: String,
    onStatusUpdate: (String) -> Unit = {}
): String = withContext(Dispatchers.IO) {
    val apiKey = GeminiClient.getStoredApiKey(context)
    if (apiKey.isBlank()) {
        return@withContext "Por favor configura tu clave de API en Ajustes para consultar horarios en tiempo real."
    }

    onStatusUpdate("Buscando información actualizada en sitios oficiales...")
    val liveSearchSnippets = fetchWebSearchSnippets(query, country)
    onStatusUpdate("Buscando información actualizada...")

    val liveSearchPart = if (liveSearchSnippets.isNotBlank()) {
        "\n$liveSearchSnippets\n"
    } else {
        ""
    }

    val prompt = """
        Eres el informador de Horarios y Servicios Públicos en tiempo real para $country.
        ZONA HORARIA OFICIAL: $tzLabel
        CONSULTA DEL USUARIO: $query
        $liveSearchPart
        INSTRUCCIONES OBLIGATORIAS:
        1. 🔍 BÚSQUEDA Y SITIOS OFICIALES:
           - Si la consulta es sobre Costa Rica, prioriza fuentes oficiales: CCSS (ccss.sa.cr), bancos públicos y privados (BNCR, BCR, Banco Popular, BAC), Correos de Costa Rica, AyA, ICE/kölbi, TSE / Registro Civil, INCOFER, COSEVI o Municipalidades.
           - Si no encuentras el dato exacto al instante, responde: 'Buscando información actualizada...' y entrega la información oficial confirmada de la institución, su red de sucursales o la central de atención. NUNCA digas que no puedes ni te quedes sin datos.
           - NUNCA respondas solo con frases amables sin dar la información real.

        2. 📋 RESPONDE SIEMPRE CON DATOS CONCRETOS (Estructura obligatoria):
           Para la institución o sucursal consultada debes entregar:
           ✅ Horario completo: Horario de apertura y cierre (lunes a viernes, jornada de cajas vs plataforma, y fines de semana si aplica).
           ✅ Dirección exacta: Ubicación precisa, cantón, distrito o señas claras de referencia.
           ✅ Teléfono: Teléfono directo de la sucursal, central telefónica o WhatsApp oficial si lo encuentras.
           ✅ Horarios especiales o cambios recientes: Avisa si aplican feriados según ley (ej. Ley 9875), citas EDUS obligatorias o trámites en línea.

        3. FORMATO:
           - Sé claro y directo. No uses frases largas sin información ni rodeos.
           - Estructura con viñetas limpias para que sea rápido de leer.
    """.trimIndent()

    try {
        val response = GeminiClient.service.generateContent(
            model = "gemini-flash-latest",
            apiKeyQuery = apiKey,
            request = com.example.data.remote.GenerateContentRequestDto(
                contents = listOf(
                    com.example.data.remote.ContentDto(
                        role = "user",
                        parts = listOf(com.example.data.remote.PartDto(text = prompt))
                    )
                ),
                generationConfig = com.example.data.remote.GenerationConfigDto(
                    temperature = 0.1f,
                    maxOutputTokens = 1000
                )
            )
        )

        if (response.isSuccessful) {
            val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                return@withContext text.trim()
            }
        }
        return@withContext "Buscando información actualizada... Por favor especifica la sucursal o cantón exacto para mayor precisión."
    } catch (e: Exception) {
        return@withContext "Error de conexión al consultar servicio: ${e.localizedMessage ?: "Revisa tu conexión a internet"}"
    }
}

/**
 * Directorio oficial de servicios públicos para Costa Rica 🇨🇷 y países hermanos.
 */
fun getServicesForCountry(countryCode: String): List<PublicServiceEntity> {
    return when (countryCode) {
        "CR" -> listOf(
            PublicServiceEntity(
                id = "cr_ccss_ebais",
                countryCode = "CR",
                category = ServiceCategory.SALUD,
                institutionName = "CCSS - EBAIS y Clínicas Periféricas",
                shortSubtitle = "Atención médica general, enfermería y citas EDUS",
                todaySchedule = "7:00 AM - 4:00 PM (Vespertino hasta 7:00 PM o 10:00 PM)",
                tomorrowSchedule = "7:00 AM - 4:00 PM (Sábados sólo urgencias)",
                location = "Cobertura nacional en los 84 cantones del país",
                phone = "905-MISALUD (905-6472583) / 9-1-1 Urgencias",
                openDays = "Lunes a Jueves 7:00 AM - 4:00 PM | Viernes 7:00 AM - 3:00 PM. Servicios de emergencias 24/7 en Clínicas y Hospitales mayores.",
                holidaysNote = "Cerrado en feriados nacionales oficiales de Costa Rica (Ley 9875). Servicio de Urgencias y Hospitalización activo 24 horas.",
                importantNotes = "Se recomienda sacar cita a las 6:00 AM desde la app oficial EDUS o en ventanilla con cédula vigente.",
                startHour = 7,
                endHour = 16
            ),
            PublicServiceEntity(
                id = "cr_ccss_farmacias",
                countryCode = "CR",
                category = ServiceCategory.SALUD,
                institutionName = "CCSS - Farmacias Públicas (Despacho)",
                shortSubtitle = "Retiro de medicamentos y tratamientos crónicos",
                todaySchedule = "7:00 AM - 4:00 PM (Hospitales 24h)",
                tomorrowSchedule = "7:00 AM - 4:00 PM",
                location = "En cada EBAIS, CAIS, Clínica y Hospital de la CCSS",
                phone = "905-6472583",
                openDays = "Lunes a Viernes 7:00 AM - 4:00 PM. Ventanillas de emergencias hospitalarias abiertas 24/7.",
                holidaysNote = "Cerrado en feriados nacionales (excepto farmacias de emergencias de hospitales).",
                importantNotes = "Puedes consultar el estado de preparación de tu receta y solicitar envío a domicilio o sucursal de Correos desde la app EDUS.",
                startHour = 7,
                endHour = 16
            ),
            PublicServiceEntity(
                id = "cr_correos",
                countryCode = "CR",
                category = ServiceCategory.CORREOS,
                institutionName = "Correos de Costa Rica",
                shortSubtitle = "Paquetería, Pymexpress, entrega de pasaportes y BoxCorreos",
                todaySchedule = "8:00 AM - 5:00 PM",
                tomorrowSchedule = "8:00 AM - 12:00 MD (Sucursales principales)",
                location = "Más de 110 sucursales en las 7 provincias de Costa Rica",
                phone = "800-900-2000 / WhatsApp (+506) 8444-2428",
                openDays = "Lunes a Viernes 8:00 AM - 5:00 PM. Sábados 8:00 AM - 12:00 MD en agencias cabecera de cantón.",
                holidaysNote = "Cerrado los domingos y en todos los feriados de ley en Costa Rica.",
                importantNotes = "Para retirar pasaportes o cédulas tramitadas con Correos se debe presentar comprobante y documento de identidad original.",
                startHour = 8,
                endHour = 17,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_banco_nacional",
                countryCode = "CR",
                category = ServiceCategory.BANCOS,
                institutionName = "Banco Nacional de Costa Rica (BNCR)",
                shortSubtitle = "Cajas, plataformas de servicios y créditos",
                todaySchedule = "8:30 AM - 3:45 PM (Vespertino en malls 4:00 PM - 6:00 PM)",
                tomorrowSchedule = "8:30 AM - 3:45 PM (Sábados 9:00 AM - 1:00 PM en centros comerciales)",
                location = "Red nacional de agencias y sucursales en todo el país",
                phone = "(+506) 2212-2000",
                openDays = "Lunes a Viernes 8:30 AM - 3:45 PM en sucursales regulares. Horario extendido en centros comerciales.",
                holidaysNote = "Cierres totales en feriados nacionales. Cajeros automáticos (ATM) y Banca en Línea disponibles 24/7.",
                importantNotes = "Atención preferencial a adultos mayores y personas con discapacidad en la primera hora de la mañana.",
                startHour = 8,
                endHour = 16,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_banco_costa_rica",
                countryCode = "CR",
                category = ServiceCategory.BANCOS,
                institutionName = "Banco de Costa Rica (BCR)",
                shortSubtitle = "Plataforma bancaria y Puntos País (Citas Licencias y Pasaportes)",
                todaySchedule = "9:00 AM - 4:00 PM (Hasta 6:00 PM en Centros Comerciales)",
                tomorrowSchedule = "9:00 AM - 4:00 PM (Sábados 10:00 AM - 4:00 PM en malls)",
                location = "Oficinas centrales en San José y agencias en los 84 cantones",
                phone = "(+506) 2211-1111 / Citas 800-BCRCITA",
                openDays = "Lunes a Viernes 9:00 AM - 4:00 PM. Sucursales en centros comerciales abren hasta las 6:00 PM y sábados.",
                holidaysNote = "Cerrado en días feriados de pago obligatorio y no obligatorio.",
                importantNotes = "La renovación de licencias de conducir y pasaportes en Punto País requiere cita previa en bancobcr.com.",
                startHour = 9,
                endHour = 16,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_banco_popular",
                countryCode = "CR",
                category = ServiceCategory.BANCOS,
                institutionName = "Banco Popular y de Desarrollo Comunal",
                shortSubtitle = "Cajas, ahorros, crédito social y pensiones",
                todaySchedule = "8:45 AM - 4:30 PM",
                tomorrowSchedule = "8:45 AM - 4:30 PM",
                location = "Agencias en cabeceras de cantón y distritos principales",
                phone = "(+506) 2202-2020",
                openDays = "Lunes a Viernes 8:45 AM - 4:30 PM. Agencias de centros comerciales abren sábados de 10:00 AM a 2:00 PM.",
                holidaysNote = "Cerrado en feriados oficiales.",
                importantNotes = "Servicio de SINPE Móvil y cajeros automáticos 24/7.",
                startHour = 8,
                endHour = 16
            ),
            PublicServiceEntity(
                id = "cr_bac",
                countryCode = "CR",
                category = ServiceCategory.BANCOS,
                institutionName = "BAC Credomatic",
                shortSubtitle = "Banca privada, trámites rápidos y plataformas empresariales",
                todaySchedule = "8:30 AM - 4:00 PM (10:00 AM - 6:00 PM en Centros Comerciales)",
                tomorrowSchedule = "8:30 AM - 4:00 PM (Sábados 10:00 AM - 2:00 PM en malls)",
                location = "Múltiples sucursales y Rapibancos en todo el país",
                phone = "(+506) 2295-9898",
                openDays = "Lunes a Viernes 8:30 AM - 4:00 PM (calle). Lunes a Sábado en centros comerciales.",
                holidaysNote = "Cerrado en feriados nacionales de ley.",
                importantNotes = "Retiro y depósitos rápidos mediante ATM Full 24/7 sin ingresar a ventanilla.",
                startHour = 8,
                endHour = 16,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_aya",
                countryCode = "CR",
                category = ServiceCategory.SERVICIOS_BASICOS,
                institutionName = "AyA (Acueductos y Alcantarillados)",
                shortSubtitle = "Atención al usuario, pagos, contratos y reporte de averías",
                todaySchedule = "7:00 AM - 3:00 PM (Reporte de fugas 24 horas)",
                tomorrowSchedule = "7:00 AM - 3:00 PM",
                location = "Sede central en Pavas, San José y agencias cantonales",
                phone = "800-REPORTE (800-7376783) / WhatsApp 8376-7832",
                openDays = "Lunes a Viernes 7:00 AM - 3:00 PM en plataformas presenciales. Línea de emergencias y reportes 24/7.",
                holidaysNote = "Oficinas administrativas cerradas en feriados. Cuadrillas técnicas de averías de guardia 24 horas.",
                importantNotes = "Para reportar fugas o falta de agua ten a mano el número de NIS que aparece en tu factura.",
                startHour = 7,
                endHour = 15
            ),
            PublicServiceEntity(
                id = "cr_ice",
                countryCode = "CR",
                category = ServiceCategory.SERVICIOS_BASICOS,
                institutionName = "ICE / kölbi (Electricidad y Telecomunicaciones)",
                shortSubtitle = "Tiendas kölbi, contratos de luz, fibra óptica y averías",
                todaySchedule = "8:00 AM - 5:00 PM (Centros comerciales hasta las 7:00 PM)",
                tomorrowSchedule = "8:00 AM - 5:00 PM (Sábados 9:00 AM - 2:00 PM en malls)",
                location = "Oficinas centrales en Sabana Norte y agencias kölbi en todo el país",
                phone = "1115 (Atención Comercial) / 1119 (Soporte Técnico Averías 24h)",
                openDays = "Lunes a Viernes 8:00 AM - 5:00 PM. Tiendas en centros comerciales abiertas sábados y domingos.",
                holidaysNote = "Centros de contacto telefónico 1115 y 1119 atienden los 365 días del año.",
                importantNotes = "Reportes de fallas eléctricas de CNFL al 800-ENERGIA (800-3637442).",
                startHour = 8,
                endHour = 17,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_registro_civil",
                countryCode = "CR",
                category = ServiceCategory.TRAMITES,
                institutionName = "Registro Civil / TSE (Tribunal Supremo de Elecciones)",
                shortSubtitle = "Cédulas de identidad, certificaciones de nacimiento y estado civil",
                todaySchedule = "7:00 AM - 3:00 PM (Jornada continua)",
                tomorrowSchedule = "7:00 AM - 3:00 PM",
                location = "Sede central frente al Parque Nacional, San José y 32 oficinas regionales",
                phone = "(+506) 2287-5555",
                openDays = "Lunes a Viernes 7:00 AM - 3:00 PM de forma continua sin cerrar al mediodía.",
                holidaysNote = "Cerrado los fines de semana y feriados oficiales.",
                importantNotes = "Certificaciones digitales gratuitas disponibles 24/7 en el portal oficial tse.go.cr.",
                startHour = 7,
                endHour = 15
            ),
            PublicServiceEntity(
                id = "cr_imas",
                countryCode = "CR",
                category = ServiceCategory.TRAMITES,
                institutionName = "IMAS (Instituto Mixto de Ayuda Social)",
                shortSubtitle = "Subsidios sociales, becas estudiantiles Avancemos y atención",
                todaySchedule = "7:00 AM - 4:00 PM",
                tomorrowSchedule = "7:00 AM - 4:00 PM",
                location = "40 Unidades Locales de Desarrollo Social (ULDES) en todo Costa Rica",
                phone = "800-000-IMAS (800-000-4627) / (+506) 2202-4000",
                openDays = "Lunes a Viernes 7:00 AM - 4:00 PM.",
                holidaysNote = "Cerrado fines de semana y feriados nacionales.",
                importantNotes = "Para ser atendido debes solicitar cita previa a través del sistema de citas en imas.go.cr o llamar a la línea gratuita.",
                startHour = 7,
                endHour = 16
            ),
            PublicServiceEntity(
                id = "cr_municipalidades",
                countryCode = "CR",
                category = ServiceCategory.TRAMITES,
                institutionName = "Municipalidades de Costa Rica (San José, Heredia, Alajuela, etc.)",
                shortSubtitle = "Pago de bienes inmuebles, patentes, permisos y aseo de vías",
                todaySchedule = "7:30 AM - 3:30 PM (Varia según cantón hasta las 4:00 PM)",
                tomorrowSchedule = "7:30 AM - 3:30 PM",
                location = "Palacios Municipales en los 84 cantones del país",
                phone = "San José: 2547-6000 | Alajuela: 2436-2300 | Heredia: 2277-1400",
                openDays = "Lunes a Viernes 7:30 AM - 3:30 PM / 4:00 PM.",
                holidaysNote = "Cerrado los fines de semana y en feriados cívicos y cantonales.",
                importantNotes = "La mayoría de municipalidades cuentan con pago en línea por SINPE en sus sitios web oficiales.",
                startHour = 7,
                endHour = 15
            ),
            PublicServiceEntity(
                id = "cr_incofer",
                countryCode = "CR",
                category = ServiceCategory.TRANSPORTE,
                institutionName = "INCOFER (Tren Urbano de Pasajeros)",
                shortSubtitle = "Rutas San José - Cartago / Heredia / Alajuela / Belén",
                todaySchedule = "Mañana: 5:30 AM - 9:00 AM | Tarde: 3:30 PM - 8:00 PM",
                tomorrowSchedule = "Mañana: 5:30 AM - 9:00 AM | Tarde: 3:30 PM - 8:00 PM",
                location = "Estaciones del Atlántico y del Pacífico en San José y andenes urbanos",
                phone = "(+506) 2221-0777",
                openDays = "Lunes a Viernes en horas pico. Sábados horario reducido especial en ruta a Cartago y Heredia.",
                holidaysNote = "Servicio suspendido o reducido en feriados de ley y días santos.",
                importantNotes = "Pago con tarjeta electrónica de débito/crédito mediante SINPE-TP disponible en andenes y molinetes.",
                startHour = 5,
                endHour = 20,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "cr_cosevi",
                countryCode = "CR",
                category = ServiceCategory.TRANSPORTE,
                institutionName = "COSEVI (Consejo de Seguridad Vial)",
                shortSubtitle = "Pruebas de manejo, infracciones, devolución de placas y licencias",
                todaySchedule = "7:00 AM - 3:00 PM",
                tomorrowSchedule = "7:00 AM - 3:00 PM",
                location = "Sede central en La Uruca, San José y sedes regionales",
                phone = "(+506) 2280-4565 / Citas 2522-0800",
                openDays = "Lunes a Viernes 7:00 AM - 3:00 PM de forma ininterrumpida.",
                holidaysNote = "Cerrado fines de semana y días de asueto público.",
                importantNotes = "La impugnación de partes de tránsito tiene un plazo de 10 días hábiles posteriores a la boleta.",
                startHour = 7,
                endHour = 15
            ),
            PublicServiceEntity(
                id = "cr_feriados",
                countryCode = "CR",
                category = ServiceCategory.FERIADOS,
                institutionName = "Calendario Oficial de Feriados y Cierres en Costa Rica",
                shortSubtitle = "Feriados de ley de pago obligatorio y no obligatorio (Ley 9875)",
                todaySchedule = "Consulta de fechas de cierre oficial",
                tomorrowSchedule = "Consulta de fechas de cierre oficial",
                location = "Aplica en todo el territorio de Costa Rica",
                phone = "Ministerio de Trabajo: 800-TRABAJO (800-8722256)",
                openDays = "Feriados Oficiales: 1 Ene (Año Nuevo), 11 Abr (Juan Santamaría), Jueves y Viernes Santo, 1 May (Trabajo), 25 Jul (Nicoya), 2 Ago (Virgen de los Ángeles), 15 Ago (Madre), 31 Ago (Cultura Afro), 15 Sep (Independencia), 1 Dic (Abolición Ejército), 25 Dic (Navidad).",
                holidaysNote = "En estas fechas las oficinas públicas del Estado permanecen cerradas al público.",
                importantNotes = "Servicios hospitalarios de la CCSS y cuerpos de emergencia (Bomberos 118, Cruz Roja 128, Policía 911) operan las 24 horas del día.",
                startHour = 0,
                endHour = 24
            )
        )
        "PE" -> listOf(
            PublicServiceEntity(
                id = "pe_essalud",
                countryCode = "PE",
                category = ServiceCategory.SALUD,
                institutionName = "EsSalud / MINSA Perú",
                shortSubtitle = "Seguro Social de Salud y Hospitales Públicos",
                todaySchedule = "7:00 AM - 4:00 PM (Urgencias 24h)",
                tomorrowSchedule = "7:00 AM - 4:00 PM",
                location = "Lima y todas las regiones del Perú",
                phone = "(01) 411-8000 / Emergencias 107",
                openDays = "Lunes a Sábado 7:00 AM - 4:00 PM. Emergencias 24 horas.",
                holidaysNote = "Cerrado en Feriados Nacionales (28 y 29 de Julio Fiestas Patrias).",
                importantNotes = "Citas en línea mediante la app EsSalud Mi Consulta.",
                startHour = 7,
                endHour = 16,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "pe_banco_nacion",
                countryCode = "PE",
                category = ServiceCategory.BANCOS,
                institutionName = "Banco de la Nación del Perú",
                shortSubtitle = "Pagos del Estado, tasas del TUPA y pensiones",
                todaySchedule = "8:00 AM - 5:00 PM",
                tomorrowSchedule = "8:00 AM - 5:00 PM (Sábados 9:00 AM - 1:00 PM)",
                location = "Red de agencias en todas las provincias de Perú",
                phone = "0800-10-700 / (01) 440-5305",
                openDays = "Lunes a Viernes 8:00 AM - 5:00 PM. Sábados 9:00 AM - 1:00 PM.",
                holidaysNote = "Cerrado en días feriados decretados por el Estado.",
                importantNotes = "Pago de tasas judiciales y policiales en Págalo.pe las 24 horas.",
                startHour = 8,
                endHour = 17,
                openOnSaturday = true
            ),
            PublicServiceEntity(
                id = "pe_reniec",
                countryCode = "PE",
                category = ServiceCategory.TRAMITES,
                institutionName = "RENIEC (Registro Nacional de Identificación)",
                shortSubtitle = "DNI electrónico, partidas y registros de estado civil",
                todaySchedule = "8:45 AM - 4:45 PM",
                tomorrowSchedule = "8:45 AM - 4:45 PM",
                location = "Oficinas registrales en todo el territorio peruano",
                phone = "(01) 315-2700 / 0800-11040",
                openDays = "Lunes a Viernes 8:45 AM - 4:45 PM.",
                holidaysNote = "Cerrado feriados y días no laborables del sector público.",
                importantNotes = "Renovación de DNI azul y DNIe con cita previa en reniec.gob.pe.",
                startHour = 8,
                endHour = 17
            ),
            PublicServiceEntity(
                id = "pe_sunat",
                countryCode = "PE",
                category = ServiceCategory.TRAMITES,
                institutionName = "SUNAT (Superintendencia de Aduanas y Tributos)",
                shortSubtitle = "RUC, comprobantes de pago y declaraciones de impuestos",
                todaySchedule = "8:30 AM - 4:30 PM",
                tomorrowSchedule = "8:30 AM - 4:30 PM",
                location = "Centros de Servicios al Contribuyente a nivel nacional",
                phone = "0801-12-100 / (01) 315-0777",
                openDays = "Lunes a Viernes 8:30 AM - 4:30 PM.",
                holidaysNote = "Cerrado en feriados nacionales.",
                importantNotes = "Trámites de RUC y clave SOL en línea mediante la app Personas SUNAT.",
                startHour = 8,
                endHour = 16
            )
        )
        "MX" -> listOf(
            PublicServiceEntity(
                id = "mx_imss",
                countryCode = "MX",
                category = ServiceCategory.SALUD,
                institutionName = "IMAS / IMSS (Instituto Mexicano del Seguro Social)",
                shortSubtitle = "Unidades de Medicina Familiar (UMF) y Hospitales",
                todaySchedule = "8:00 AM - 8:00 PM (Urgencias 24 horas)",
                tomorrowSchedule = "8:00 AM - 8:00 PM",
                location = "Cobertura en las 32 entidades federativas de México",
                phone = "800 623 2323 / Citas App IMSS Digital",
                openDays = "Lunes a Viernes turno matutino y vespertino. Urgencias 24/7.",
                holidaysNote = "Días de asueto oficial opera exclusivamente personal de guardia en urgencias.",
                importantNotes = "Citas previas disponibles en la app IMSS Digital con CURP y correo electrónico.",
                startHour = 8,
                endHour = 20
            ),
            PublicServiceEntity(
                id = "mx_sat",
                countryCode = "MX",
                category = ServiceCategory.TRAMITES,
                institutionName = "SAT (Servicio de Administración Tributaria)",
                shortSubtitle = "Constancia de Situación Fiscal, RFC y firma electrónica",
                todaySchedule = "9:00 AM - 4:00 PM (Viernes hasta las 3:00 PM)",
                tomorrowSchedule = "9:00 AM - 4:00 PM",
                location = "Módulos de atención tributaria en todas las ciudades de México",
                phone = "55 627 22 728 (MarcaSAT)",
                openDays = "Lunes a Jueves 9:00 AM - 4:00 PM | Viernes 9:00 AM - 3:00 PM.",
                holidaysNote = "Cerrado en días inhábiles señalados en el Código Fiscal de la Federación.",
                importantNotes = "Atención presencial requiere cita registrada previamente en citas.sat.gob.mx.",
                startHour = 9,
                endHour = 16
            ),
            PublicServiceEntity(
                id = "mx_cfe",
                countryCode = "MX",
                category = ServiceCategory.SERVICIOS_BASICOS,
                institutionName = "CFE (Comisión Federal de Electricidad)",
                shortSubtitle = "Contrataciones, aclaración de recibos y reporte de apagones",
                todaySchedule = "8:00 AM - 5:00 PM (CFEmáticos y reportes 24 horas)",
                tomorrowSchedule = "8:00 AM - 5:00 PM",
                location = "Centros de Atención a Clientes en toda la República Mexicana",
                phone = "071 (Atención Telefónica 24 horas) / 800 888 2338",
                openDays = "Lunes a Viernes 8:00 AM - 5:00 PM. Cajeros CFEmáticos abiertos 24/7.",
                holidaysNote = "Atención telefónica y reporte de fallas activo los 365 días del año.",
                importantNotes = "Reportes inmediatos a través de la aplicación CFE Contigo.",
                startHour = 8,
                endHour = 17
            )
        )
        else -> listOf(
            PublicServiceEntity(
                id = "global_embassy",
                countryCode = "GLOBAL",
                category = ServiceCategory.TRAMITES,
                institutionName = "Servicios Consulares y Embajadas",
                shortSubtitle = "Pasaportes, visados, legalizaciones y asistencia",
                todaySchedule = "9:00 AM - 2:00 PM (Horario consular)",
                tomorrowSchedule = "9:00 AM - 2:00 PM",
                location = "Capitales y sedes consulares internacionales",
                phone = "Varía según delegación",
                openDays = "Lunes a Viernes 9:00 AM - 2:00 PM.",
                holidaysNote = "Cierran en feriados locales y en festividades nacionales de su país emisor.",
                importantNotes = "La mayoría de consulados atienden únicamente con cita previa confirmada por internet.",
                startHour = 9,
                endHour = 14
            ),
            PublicServiceEntity(
                id = "global_salud",
                countryCode = "GLOBAL",
                category = ServiceCategory.SALUD,
                institutionName = "Centros de Salud y Hospitales Públicos",
                shortSubtitle = "Atención de urgencias médicas y farmacias de turno",
                todaySchedule = "8:00 AM - 5:00 PM (Emergencias 24h)",
                tomorrowSchedule = "8:00 AM - 5:00 PM",
                location = "Red de salud municipal y provincial",
                phone = "Línea de emergencias local (911 / 112)",
                openDays = "Atención ambulatoria de lunes a viernes. Urgencias médicas operan 24 horas continuas.",
                holidaysNote = "Guardias de urgencias activas los 365 días del año.",
                importantNotes = "Lleva siempre contigo documento de identidad y póliza de seguro médico.",
                startHour = 8,
                endHour = 17
            )
        )
    }
}
