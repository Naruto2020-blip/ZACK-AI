package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.CompressResult
import com.example.util.FileProcessor
import com.example.util.MergeResult
import com.example.util.PdfToolsManager
import com.example.util.ShareUtils
import com.example.util.SplitResult
import kotlinx.coroutines.launch

enum class DocumentToolTab(val title: String, val icon: ImageVector) {
    FILL_FORM("Rellenar PDF", Icons.Default.EditNote),
    SUMMARIZE("Resumir", Icons.Default.Description),
    SPELLCHECK("Corrector", Icons.Default.Spellcheck),
    TRANSLATE("Traducir", Icons.Default.Translate),
    COMPRESS("Comprimir PDF", Icons.Default.Compress),
    MERGE_SPLIT("Unir / Dividir", Icons.Default.CallMerge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentToolsDialog(
    initialTab: DocumentToolTab = DocumentToolTab.FILL_FORM,
    onSendAiPrompt: (prompt: String, attachedUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimaryDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isAppDark()) Color(0xFF475569) else Color(0xFFCBD5E1))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = if (isAppDark()) DarkBackground else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "📄 Herramientas de Documentos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "IA y utilidades para tus archivos",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Tab Row for all 6 tools
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = ObsidianCard,
                contentColor = ElectricCyan,
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = ElectricCyan,
                        height = 2.5.dp
                    )
                },
                divider = {}
            ) {
                DocumentToolTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) ElectricCyan else TextSecondaryDark
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ElectricCyan else TextSecondaryDark
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    DocumentToolTab.FILL_FORM -> FillPdfFormView(
                        onSendAiPrompt = { p, u ->
                            onSendAiPrompt(p, u)
                            onDismiss()
                        }
                    )
                    DocumentToolTab.SUMMARIZE -> SummarizeDocView(
                        onSendAiPrompt = { p, u ->
                            onSendAiPrompt(p, u)
                            onDismiss()
                        }
                    )
                    DocumentToolTab.SPELLCHECK -> SpellcheckView(
                        onSendAiPrompt = { p, u ->
                            onSendAiPrompt(p, u)
                            onDismiss()
                        }
                    )
                    DocumentToolTab.TRANSLATE -> TranslateDocView(
                        onSendAiPrompt = { p, u ->
                            onSendAiPrompt(p, u)
                            onDismiss()
                        }
                    )
                    DocumentToolTab.COMPRESS -> CompressPdfView()
                    DocumentToolTab.MERGE_SPLIT -> MergeSplitPdfView()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// -------------------------------------------------------------
// 1. 📝 RELLENAR FORMULARIOS PDF
// -------------------------------------------------------------
@Composable
private fun FillPdfFormView(
    onSendAiPrompt: (String, Uri?) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var userDataInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment ?: "formulario.pdf"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📝 Rellenar Formularios PDF",
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sube tu formulario en PDF. La IA detectará todos los campos requeridos (nombre, identificación, fecha, datos de contacto, casillas) y los completará de forma ordenada con tus datos.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // File selection button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { filePicker.launch(arrayOf("application/pdf")) }
                .border(1.dp, if (selectedUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
            color = ObsidianCard
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedUri != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = if (selectedUri != null) EmeraldGreen else ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedUri != null) fileName ?: "PDF Seleccionado" else "Seleccionar Formulario PDF",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (selectedUri != null) "Toca para cambiar de archivo" else "Toca para examinar archivos PDF de tu dispositivo",
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Datos para completar el formulario (opcional):",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryDark,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = userDataInput,
            onValueChange = { userDataInput = it },
            placeholder = {
                Text(
                    "Ej: Nombre: Juan Pérez, DNI: 12345678, Dirección: Av. Central 45, Tel: 555-0199...",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianCard,
                unfocusedContainerColor = ObsidianCard,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = ObsidianCardBorder,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val prompt = buildString {
                    append("Por favor actúa como especialista en procesamiento de formularios PDF. ")
                    append("Analiza el documento PDF adjunto, detecta exhaustivamente todos los campos del formulario (campos de texto, casillas, tablas, firmas requeridas) ")
                    append("y completa el formulario de forma profesional y estructurada.")
                    if (userDataInput.isNotBlank()) {
                        append("\n\nUtiliza los siguientes datos proporcionados por el usuario:\n$userDataInput")
                    } else {
                        append("\n\nSi faltan datos específicos, rellénalos con valores de ejemplo realistas o marca claramente qué debe colocar el usuario.")
                    }
                    append("\n\nPresenta el formulario completamente lleno y estructurado, listo para revisión y posterior descarga en PDF.")
                }
                onSendAiPrompt(prompt, selectedUri)
            },
            enabled = selectedUri != null || userDataInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Detectar Campos y Rellenar con IA", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 2. 📄 RESUMIR DOCUMENTOS LARGOS (Corto / Detallado)
// -------------------------------------------------------------
@Composable
private fun SummarizeDocView(
    onSendAiPrompt: (String, Uri?) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var manualText by remember { mutableStateOf("") }
    var summaryType by remember { mutableStateOf("Corto") } // "Corto" or "Detallado"

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment ?: "documento"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📄 Resumir Documentos Largos",
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sube un archivo (PDF, Word, TXT) o pega un texto largo. La IA extraerá SOLO lo más importante en puntos clave claros.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Options: Resumen Corto vs Resumen Detallado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { summaryType = "Corto" }
                    .border(1.dp, if (summaryType == "Corto") ElectricCyan else ObsidianCardBorder, RoundedCornerShape(10.dp)),
                color = if (summaryType == "Corto") ElectricCyan.copy(alpha = 0.15f) else ObsidianCard
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚡ Resumen Corto", fontWeight = FontWeight.Bold, color = if (summaryType == "Corto") ElectricCyan else TextPrimaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("3 a 5 puntos clave ultra concisos", color = TextSecondaryDark, fontSize = 11.sp)
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { summaryType = "Detallado" }
                    .border(1.dp, if (summaryType == "Detallado") RadiantViolet else ObsidianCardBorder, RoundedCornerShape(10.dp)),
                color = if (summaryType == "Detallado") RadiantViolet.copy(alpha = 0.15f) else ObsidianCard
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📑 Resumen Detallado", fontWeight = FontWeight.Bold, color = if (summaryType == "Detallado") RadiantViolet else TextPrimaryDark, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Análisis estructurado por secciones", color = TextSecondaryDark, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Upload Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    filePicker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
                }
                .border(1.dp, if (selectedUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
            color = ObsidianCard
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedUri != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = if (selectedUri != null) EmeraldGreen else ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedUri != null) fileName ?: "Archivo Seleccionado" else "Subir Archivo (PDF o Word)",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (selectedUri != null) "Toca para cambiar de archivo" else "O pega el texto abajo si prefieres",
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = manualText,
            onValueChange = { manualText = it },
            placeholder = { Text("O pega aquí el texto que deseas resumir...", color = TextSecondaryDark, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianCard,
                unfocusedContainerColor = ObsidianCard,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = ObsidianCardBorder,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val prompt = if (summaryType == "Corto") {
                    "Por favor realiza un RESUMEN CORTO y sintético de este texto/documento. Extrae ÚNICAMENTE los 3 a 5 puntos clave más importantes, en viñetas directas, claras y fáciles de comprender al instante." +
                            if (manualText.isNotBlank()) "\n\nTexto:\n$manualText" else ""
                } else {
                    "Por favor realiza un RESUMEN DETALLADO y completo de este texto/documento. Organízalo con:\n1. Resumen ejecutivo general\n2. Puntos clave desglosados por sección o tema\n3. Conclusiones y aspectos críticos." +
                            if (manualText.isNotBlank()) "\n\nTexto:\n$manualText" else ""
                }
                onSendAiPrompt(prompt, selectedUri)
            },
            enabled = selectedUri != null || manualText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar Resumen $summaryType", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 3. ✏️ CORRECTOR DE ORTOGRAFÍA Y GRAMÁTICA
// -------------------------------------------------------------
@Composable
private fun SpellcheckView(
    onSendAiPrompt: (String, Uri?) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment ?: "documento"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "✏️ Corrector de Ortografía y Gramática",
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Escribe o sube un texto. La IA lo corregirá al 100% y te mostrará tanto el texto impecable como la lista detallada de correcciones realizadas.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Upload Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    filePicker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
                }
                .border(1.dp, if (selectedUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
            color = ObsidianCard
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedUri != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = if (selectedUri != null) EmeraldGreen else AmberGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (selectedUri != null) fileName ?: "Archivo cargado" else "Subir documento para corregir",
                    color = TextPrimaryDark,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Escribe o pega aquí el texto a corregir...", color = TextSecondaryDark, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianCard,
                unfocusedContainerColor = ObsidianCard,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = ObsidianCardBorder,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val prompt = "Por favor realiza una corrección ortográfica, gramatical, de puntuación y de estilo exhaustiva de este texto/documento.\n\n" +
                        "Debes estructurar tu respuesta exactamente con estas dos secciones:\n\n" +
                        "### ✅ Texto Corregido:\n" +
                        "(Aquí coloca el texto completo corregido y pulido profesionalmente)\n\n" +
                        "### 📋 Lista de Cambios Realizados:\n" +
                        "(Aquí enumera cada corrección con una breve explicación de por qué se cambió, qué regla aplicó o qué error se subsanó)" +
                        if (textInput.isNotBlank()) "\n\nTexto original:\n$textInput" else ""
                onSendAiPrompt(prompt, selectedUri)
            },
            enabled = selectedUri != null || textInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Spellcheck, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Corregir Ortografía y Gramática", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 4. 🌐 TRADUCIR DOCUMENTOS COMPLETOS
// -------------------------------------------------------------
@Composable
private fun TranslateDocView(
    onSendAiPrompt: (String, Uri?) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("Inglés") }

    val languages = listOf("Español", "Inglés", "Portugués", "Francés", "Alemán")

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment ?: "documento"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🌐 Traducir Documentos Completos",
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Traduce cualquier archivo o texto al idioma que elijas manteniendo estrictamente el formato original, viñetas, títulos y párrafos.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Idioma de destino:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryDark,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            languages.forEach { lang ->
                val isSelected = targetLanguage == lang
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else ObsidianCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else ObsidianCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { targetLanguage = lang }
                ) {
                    Text(
                        text = when (lang) {
                            "Español" -> "🇪🇸 ES"
                            "Inglés" -> "🇺🇸 EN"
                            "Portugués" -> "🇧🇷 PT"
                            "Francés" -> "🇫🇷 FR"
                            "Alemán" -> "🇩🇪 DE"
                            else -> lang
                        },
                        color = if (isSelected) ElectricCyan else TextSecondaryDark,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Upload Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    filePicker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
                }
                .border(1.dp, if (selectedUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
            color = ObsidianCard
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedUri != null) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = if (selectedUri != null) EmeraldGreen else RadiantViolet,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedUri != null) fileName ?: "Archivo cargado" else "Subir documento para traducir",
                        color = TextPrimaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "PDF, Word (.docx) o texto plano",
                        color = TextSecondaryDark,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("O escribe/pega el texto que deseas traducir...", color = TextSecondaryDark, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianCard,
                unfocusedContainerColor = ObsidianCard,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = ObsidianCardBorder,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val prompt = "Por favor traduce COMPLETAMENTE el siguiente texto/documento al idioma $targetLanguage.\n" +
                        "Mantén estrictamente el formato original, la estructura de párrafos, títulos, listas y terminología especializada con máxima naturalidad y fluidez profesional.\n\n" +
                        if (textInput.isNotBlank()) "Texto a traducir:\n$textInput" else ""
                onSendAiPrompt(prompt, selectedUri)
            },
            enabled = selectedUri != null || textInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Traducir a $targetLanguage", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 5. 📦 COMPRIMIR / REDUCIR TAMAÑO DE PDF
// -------------------------------------------------------------
@Composable
private fun CompressPdfView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var isCompressing by remember { mutableStateOf(false) }
    var compressResult by remember { mutableStateOf<CompressResult?>(null) }
    var qualitySlider by remember { mutableFloatStateOf(75f) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment ?: "documento.pdf"
            compressResult = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📦 Comprimir y Reducir Tamaño de PDF",
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reduce significativamente el peso de tus archivos PDF manteniendo una excelente nitidez y calidad visual, ideal para enviar por WhatsApp o correo.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PDF Picker
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { filePicker.launch(arrayOf("application/pdf")) }
                .border(1.dp, if (selectedUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
            color = ObsidianCard
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedUri != null) fileName ?: "PDF Seleccionado" else "Seleccionar archivo PDF para comprimir",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (selectedUri != null) "Listo para procesar" else "Toca para elegir de tu dispositivo",
                        color = TextSecondaryDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quality slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nivel de optimización:",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryDark,
                fontSize = 12.sp
            )
            Text(
                text = "${qualitySlider.toInt()}% calidad",
                color = ElectricCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Slider(
            value = qualitySlider,
            onValueChange = { qualitySlider = it },
            valueRange = 50f..95f,
            steps = 8,
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = ObsidianCardBorder
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                val uri = selectedUri ?: return@Button
                isCompressing = true
                scope.launch {
                    val result = PdfToolsManager.compressPdf(context, uri, qualitySlider.toInt())
                    isCompressing = false
                    result.onSuccess { res ->
                        compressResult = res
                        Toast.makeText(context, "✅ PDF comprimido con éxito (${res.percentSaved}% menos)", Toast.LENGTH_SHORT).show()
                    }.onFailure { ex ->
                        Toast.makeText(context, "Error al comprimir: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = selectedUri != null && !isCompressing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isCompressing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Comprimiendo PDF...")
            } else {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Comprimir PDF Ahora", fontWeight = FontWeight.Bold)
            }
        }

        // Result Card
        if (compressResult != null) {
            val res = compressResult!!
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldGreen, RoundedCornerShape(14.dp)),
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "¡PDF Comprimido Exitosamente!",
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Original", color = TextSecondaryDark, fontSize = 11.sp)
                            Text(formatBytes(res.originalSizeBytes), color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Comprimido", color = TextSecondaryDark, fontSize = 11.sp)
                            Text(formatBytes(res.compressedSizeBytes), color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ahorro", color = TextSecondaryDark, fontSize = 11.sp)
                            Text("-${res.percentSaved}%", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            ShareUtils.shareFile(context, res.file, "application/pdf", "Compartir PDF Comprimido")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir por WhatsApp o Correo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. 🔗 UNIR O DIVIDIR PDFS
// -------------------------------------------------------------
@Composable
private fun MergeSplitPdfView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subMode by remember { mutableStateOf("Unir") } // "Unir" or "Dividir"

    // Merge state
    val mergeUris = remember { mutableStateListOf<Uri>() }
    var isMerging by remember { mutableStateOf(false) }
    var mergeResult by remember { mutableStateOf<MergeResult?>(null) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            mergeUris.clear()
            mergeUris.addAll(uris)
            mergeResult = null
        }
    }

    // Split state
    var splitUri by remember { mutableStateOf<Uri?>(null) }
    var splitPageCount by remember { mutableIntStateOf(1) }
    var startPageText by remember { mutableStateOf("1") }
    var endPageText by remember { mutableStateOf("1") }
    var isSplitting by remember { mutableStateOf(false) }
    var splitResult by remember { mutableStateOf<SplitResult?>(null) }

    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            splitUri = uri
            splitResult = null
            scope.launch {
                val total = PdfToolsManager.getPdfPageCount(context, uri)
                splitPageCount = total
                startPageText = "1"
                endPageText = total.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Toggle SubMode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { subMode = "Unir" }
                    .border(1.dp, if (subMode == "Unir") ElectricCyan else ObsidianCardBorder, RoundedCornerShape(10.dp)),
                color = if (subMode == "Unir") ElectricCyan.copy(alpha = 0.15f) else ObsidianCard
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.CallMerge, contentDescription = null, tint = if (subMode == "Unir") ElectricCyan else TextSecondaryDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🔗 Unir Varios PDFs", fontWeight = FontWeight.Bold, color = if (subMode == "Unir") ElectricCyan else TextPrimaryDark, fontSize = 12.sp)
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { subMode = "Dividir" }
                    .border(1.dp, if (subMode == "Dividir") RadiantViolet else ObsidianCardBorder, RoundedCornerShape(10.dp)),
                color = if (subMode == "Dividir") RadiantViolet.copy(alpha = 0.15f) else ObsidianCard
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.CallSplit, contentDescription = null, tint = if (subMode == "Dividir") RadiantViolet else TextSecondaryDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✂️ Dividir PDF", fontWeight = FontWeight.Bold, color = if (subMode == "Dividir") RadiantViolet else TextPrimaryDark, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (subMode == "Unir") {
            // Unir PDFs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { multiPdfPicker.launch(arrayOf("application/pdf")) }
                    .border(1.dp, if (mergeUris.isNotEmpty()) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
                color = ObsidianCard
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CallMerge, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mergeUris.isNotEmpty()) "${mergeUris.size} archivos PDF seleccionados" else "Seleccionar PDFs para unir",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Selecciona 2 o más archivos para combinarlos en uno solo",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    isMerging = true
                    scope.launch {
                        val res = PdfToolsManager.mergePdfs(context, mergeUris)
                        isMerging = false
                        res.onSuccess { r ->
                            mergeResult = r
                            Toast.makeText(context, "✅ PDFs combinados en un archivo de ${r.totalPages} páginas", Toast.LENGTH_SHORT).show()
                        }.onFailure { ex ->
                            Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = mergeUris.size >= 2 && !isMerging,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF090D16)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isMerging) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ObsidianBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Combinando archivos...")
                } else {
                    Icon(imageVector = Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unir ${mergeUris.size} PDFs en uno", fontWeight = FontWeight.Bold)
                }
            }

            if (mergeResult != null) {
                val res = mergeResult!!
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldGreen, RoundedCornerShape(14.dp)),
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("✅ ¡PDF Combinado Listo!", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${res.totalPages} páginas en total • Tamaño: ${formatBytes(res.sizeBytes)}", color = TextSecondaryDark, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                ShareUtils.shareFile(context, res.file, "application/pdf", "Compartir PDF Unido")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir o Guardar PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

        } else {
            // Dividir PDF
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { singlePdfPicker.launch(arrayOf("application/pdf")) }
                .border(1.dp, if (splitUri != null) EmeraldGreen else ObsidianCardBorder, RoundedCornerShape(12.dp)),
                color = ObsidianCard
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CallSplit, contentDescription = null, tint = RadiantViolet, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (splitUri != null) "PDF Cargado (${splitPageCount} páginas)" else "Seleccionar PDF para dividir",
                            color = TextPrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (splitUri != null) "Toca para cambiar de archivo" else "Toca para examinar archivos",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (splitUri != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("Rango de páginas a extraer (1 a $splitPageCount):", color = TextSecondaryDark, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startPageText,
                        onValueChange = { startPageText = it.filter { c -> c.isDigit() } },
                        label = { Text("Desde pág.") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    OutlinedTextField(
                        value = endPageText,
                        onValueChange = { endPageText = it.filter { c -> c.isDigit() } },
                        label = { Text("Hasta pág.") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianCard,
                            unfocusedContainerColor = ObsidianCard,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val uri = splitUri ?: return@Button
                        val start = startPageText.toIntOrNull() ?: 1
                        val end = endPageText.toIntOrNull() ?: splitPageCount
                        isSplitting = true
                        scope.launch {
                            val res = PdfToolsManager.splitPdf(context, uri, start, end)
                            isSplitting = false
                            res.onSuccess { r ->
                                splitResult = r
                                Toast.makeText(context, "✅ Extraídas ${r.pageCount} páginas", Toast.LENGTH_SHORT).show()
                            }.onFailure { ex ->
                                Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSplitting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RadiantViolet, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSplitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extrayendo páginas...")
                    } else {
                        Icon(imageVector = Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dividir y Guardar Rango", fontWeight = FontWeight.Bold)
                    }
                }

                if (splitResult != null) {
                    val res = splitResult!!
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldGreen, RoundedCornerShape(14.dp)),
                        color = ObsidianCard,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("✅ ¡PDF Dividido con Éxito!", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${res.rangeText} (${res.pageCount} páginas) • ${formatBytes(res.sizeBytes)}", color = TextSecondaryDark, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    ShareUtils.shareFile(context, res.file, "application/pdf", "Compartir PDF Dividido")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compartir o Guardar PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}
