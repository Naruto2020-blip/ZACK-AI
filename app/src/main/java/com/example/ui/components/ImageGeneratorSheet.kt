package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.GeneratedAiImage
import com.example.util.ImageGenerationManager
import com.example.util.ShareUtils
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGeneratorSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var promptText by remember { mutableStateOf("") }
    var selectedAspectRatio by remember { mutableStateOf("1:1") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentGeneratedImage by remember { mutableStateOf<GeneratedAiImage?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showFullscreenZoom by remember { mutableStateOf(false) }

    // Historial de creaciones de esta sesión
    val sessionHistory = remember { mutableStateListOf<GeneratedAiImage>() }

    // Launcher para dictado por voz de la descripción
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                promptText = if (promptText.isBlank()) spoken else "$promptText $spoken"
            }
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES").toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe la imagen que deseas crear...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "El reconocimiento de voz no está disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerGeneration() {
        if (promptText.isBlank()) {
            Toast.makeText(context, "Por favor escribe qué imagen deseas crear", Toast.LENGTH_SHORT).show()
            return
        }
        isGenerating = true
        errorMessage = null

        coroutineScope.launch {
            val result = ImageGenerationManager.generateImage(
                userPrompt = promptText,
                aspectRatio = selectedAspectRatio
            )
            isGenerating = false

            result.fold(
                onSuccess = { generated ->
                    currentGeneratedImage = generated
                    sessionHistory.add(0, generated)
                    Toast.makeText(context, "✨ Imagen creada exactamente como la pediste", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Ocurrió un error inesperado al generar la imagen"
                }
            )
        }
    }

    // Modal Bottom Sheet completo
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        dragHandle = null,
        modifier = Modifier
            .fillMaxHeight(0.96f)
            .statusBarsPadding()
            .testTag("image_generator_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // =========================================================================
            // 1. TOP HEADER CON TÍTULO Y BOTÓN CERRAR
            // =========================================================================
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(0.5.dp, ObsidianCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(NeonPurple, ElectricCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Creador de Imágenes IA",
                                color = TextPrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Exacta y fiel · Sin marcas de agua",
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_image_generator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondaryDark
                        )
                    }
                }
            }

            // =========================================================================
            // 2. CONTENIDO SCROLLABLE (PREVIEW, INPUT, CATEGORÍAS, OPCIONES)
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {

                // Chips de inspiración según lo solicitado por el usuario:
                // Logos e íconos, tareas/presentaciones, ilustraciones para cuentos, diseños de carteles, fondos de pantalla
                Text(
                    text = "Inspiración rápida (toca para rellenar un ejemplo):",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val inspirationExamples = listOf(
                    "🏷️ Logo" to "Logo minimalista para cafetería con una taza humeante, líneas doradas y fondo verde esmeralda oscuro",
                    "📚 Tarea" to "Infografía de la célula eucariota con núcleo violeta, mitocondrias rojas y membrana celular detallada",
                    "📖 Cuento" to "Ilustración de un zorro curioso con bufanda amarilla en un bosque encantado con luciérnagas",
                    "🎨 Cartel" to "Cartel moderno para festival de música de verano con palmeras tropicales, sol naranja y olas turquesa",
                    "📱 Fondo" to "Fondo de pantalla de montañas nevadas bajo un cielo estrellado con auroras boreales verdes y moradas"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(inspirationExamples) { (label, promptExample) ->
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ObsidianCardBorder),
                            modifier = Modifier.clickable {
                                promptText = promptExample
                            }
                        ) {
                            Text(
                                text = label,
                                color = TextPrimaryDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Campo de descripción en español
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            text = "Describe en español todo lo que quieres: objetos, colores exactos, formas, cantidades, estilo (ej: Un logo con un tigre geométrico azul y dorado sobre fondo blanco)...",
                            color = TextSecondaryDark.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("image_prompt_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (promptText.isNotBlank()) {
                                IconButton(onClick = { promptText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar texto",
                                        tint = TextSecondaryDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { startVoiceInput() }) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Dictar por voz",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Selector de Proporción / Aspect Ratio
                Text(
                    text = "Proporción de la imagen:",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratios = listOf(
                        "1:1" to "1:1 Cuadrado",
                        "16:9" to "16:9 Panorámico",
                        "9:16" to "9:16 Vertical",
                        "4:3" to "4:3 Estándar"
                    )

                    ratios.forEach { (ratioCode, label) ->
                        val isSelected = selectedAspectRatio == ratioCode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedAspectRatio = ratioCode }
                                .border(
                                    1.dp,
                                    if (isSelected) NeonPurple else ObsidianCardBorder,
                                    RoundedCornerShape(8.dp)
                                ),
                            color = if (isSelected) NeonPurple.copy(alpha = 0.18f) else ObsidianCard
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TextPrimaryDark else TextSecondaryDark,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // =========================================================================
                // ESTADO DE CARGA / GENERACIÓN
                // =========================================================================
                if (isGenerating) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = NeonPurple,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "🎨 Generando imagen con IA...",
                                color = TextPrimaryDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Respetando fielmente colores, formas y estilo sin marcas de agua",
                                color = TextSecondaryDark,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // =========================================================================
                // ERROR SI OCURRE
                // =========================================================================
                errorMessage?.let { error ->
                    Surface(
                        color = Color(0xFF331111),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ $error",
                                color = TextPrimaryDark,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // =========================================================================
                // IMAGEN GENERADA RESULTANTE CON ACCIONES
                // =========================================================================
                currentGeneratedImage?.let { generated ->
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        border = BorderStroke(1.dp, ObsidianCardBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Badge superior
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = EmeraldGreen.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "✨ Sin marcas de agua · Lista para usar",
                                        color = EmeraldGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showFullscreenZoom = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Ver en grande",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Visualizador de la imagen
                            val imageRatio = when (generated.aspectRatio) {
                                "16:9" -> 16f / 9f
                                "9:16" -> 9f / 16f
                                "4:3" -> 4f / 3f
                                else -> 1f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(imageRatio)
                                    .background(Color.Black)
                                    .clickable { showFullscreenZoom = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = generated.bitmap.asImageBitmap(),
                                    contentDescription = generated.prompt,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Texto de la descripción creada
                            Text(
                                text = "“${generated.prompt}”",
                                color = TextSecondaryDark,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(color = ObsidianCardBorder, thickness = 0.5.dp)

                            // BOTONES DE ACCIÓN: 📥 Guardar en el teléfono | 🔄 Generar otra versión | 📤 Compartir
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 📥 Guardar en el teléfono
                                Button(
                                    onClick = {
                                        val saved = ShareUtils.saveBitmapToGallery(context, generated.bitmap, "ZackAI_Imagen")
                                        if (saved) {
                                            Toast.makeText(context, "✅ ¡Imagen guardada en la galería de tu teléfono!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "No se pudo guardar la imagen en la galería", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_image_phone_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = DarkBackground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "📥 Guardar",
                                        color = DarkBackground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 🔄 Generar otra versión con la misma descripción
                                OutlinedButton(
                                    onClick = {
                                        triggerGeneration()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("regenerate_image_button"),
                                    border = BorderStroke(1.dp, NeonPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🔄 Otra versión",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 📤 Compartir
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            ShareUtils.shareBitmap(context, generated.bitmap, "Compartir imagen creada")
                                        }
                                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp)),
                                    color = ObsidianSurface
                                ) {
                                    Box(
                                        modifier = Modifier.padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Compartir",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // HISTORIAL DE CREACIONES EN ESTA SESIÓN
                // =========================================================================
                if (sessionHistory.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tus creaciones de esta sesión (${sessionHistory.size}):",
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessionHistory) { item ->
                            val isCurrent = item.id == currentGeneratedImage?.id
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        2.dp,
                                        if (isCurrent) NeonPurple else ObsidianCardBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentGeneratedImage = item
                                        promptText = item.prompt
                                        selectedAspectRatio = item.aspectRatio
                                    }
                            ) {
                                Image(
                                    bitmap = item.bitmap.asImageBitmap(),
                                    contentDescription = item.prompt,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // =========================================================================
            // 3. BOTÓN SIEMPRE VISIBLE: "CREAR IMAGEN" (FIJO AL PIE DEL MODAL)
            // =========================================================================
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(0.5.dp, ObsidianCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { triggerGeneration() },
                        enabled = !isGenerating && promptText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_image_always_visible_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            disabledContainerColor = ObsidianCard
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Creando imagen...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (promptText.isNotBlank()) Color.White else TextSecondaryDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Crear imagen",
                                color = if (promptText.isNotBlank()) Color.White else TextSecondaryDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIÁLOGO PANTALLA COMPLETA PARA VER LA IMAGEN EN MÁXIMA RESOLUCIÓN
    // =========================================================================
    if (showFullscreenZoom && currentGeneratedImage != null) {
        Dialog(
            onDismissRequest = { showFullscreenZoom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = currentGeneratedImage!!.bitmap.asImageBitmap(),
                    contentDescription = currentGeneratedImage!!.prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )

                // Botón cerrar arriba a la derecha
                IconButton(
                    onClick = { showFullscreenZoom = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }

                // Botón guardar abajo
                Button(
                    onClick = {
                        val saved = ShareUtils.saveBitmapToGallery(context, currentGeneratedImage!!.bitmap)
                        if (saved) {
                            Toast.makeText(context, "✅ ¡Imagen guardada en la galería!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = DarkBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar en el teléfono",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
