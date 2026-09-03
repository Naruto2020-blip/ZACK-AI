package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.util.ProcessedAttachment
import java.io.File

@Composable
fun AttachmentActionBar(
    attachedFile: ProcessedAttachment?,
    isProcessingFile: Boolean,
    onRemoveAttachment: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    if (isProcessingFile) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Leyendo y procesando contenido del documento...",
                    color = ElectricCyan,
                    fontSize = 12.sp
                )
            }
        }
    } else if (attachedFile != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Attachment Preview Card
            Surface(
                color = ObsidianCard,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (attachedFile.isImage) {
                        AsyncImage(
                            model = attachedFile.uri,
                            contentDescription = "Vista previa imagen",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val isPdf = attachedFile.name.endsWith(".pdf", true)
                        val isWord = attachedFile.name.endsWith(".docx", true) || attachedFile.name.endsWith(".doc", true)
                        val isExcel = attachedFile.name.endsWith(".xlsx", true) || attachedFile.name.endsWith(".xls", true) || attachedFile.name.endsWith(".csv", true)
                        val isPpt = attachedFile.name.endsWith(".pptx", true) || attachedFile.name.endsWith(".ppt", true)

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isPdf -> Color(0xFFEF4444)
                                        isWord -> Color(0xFF2563EB)
                                        isExcel -> Color(0xFF16A34A)
                                        isPpt -> Color(0xFFEA580C)
                                        else -> Color(0xFF059669)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isPdf -> Icons.Default.PictureAsPdf
                                    isWord -> Icons.Default.Description
                                    isExcel -> Icons.Default.TableChart
                                    isPpt -> Icons.Default.Slideshow
                                    else -> Icons.Default.Article
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachedFile.name,
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val fileStatusDesc = when {
                            attachedFile.isImage -> "Foto lista para OCR y Análisis Visual"
                            attachedFile.name.endsWith(".pdf", true) -> "Documento PDF (${attachedFile.pageCount} págs) listo"
                            attachedFile.name.endsWith(".xlsx", true) || attachedFile.name.endsWith(".csv", true) -> "Hoja de cálculo Excel (Tablas y cálculos leídos)"
                            attachedFile.name.endsWith(".pptx", true) -> "Presentación PowerPoint (Diapositivas leídas)"
                            attachedFile.name.endsWith(".docx", true) -> "Documento Word (.docx) listo"
                            !attachedFile.extractedText.isNullOrBlank() -> "Texto extraído (${attachedFile.extractedText.length} caracteres)"
                            else -> "Archivo listo para procesar con IA"
                        }
                        Text(
                            text = fileStatusDesc,
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onRemoveAttachment,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Quitar archivo",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Prompt Chips (Resume, Preguntas, Extraer Datos, Buscar)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    QuickActionChip(
                        label = "📄 Resumir",
                        onClick = { onQuickAction("Por favor, haz un resumen claro, conciso y estructurado de este documento en pocas líneas destacando lo más importante.") }
                    )
                }
                item {
                    QuickActionChip(
                        label = "📋 Extraer Datos",
                        onClick = { onQuickAction("Extrae y tabula de forma organizada todos los datos clave: fechas, nombres, montos, números, requisitos y puntos críticos.") }
                    )
                }
                item {
                    QuickActionChip(
                        label = "🔍 Puntos Críticos",
                        onClick = { onQuickAction("Analiza este archivo e identifica los puntos más importantes, cláusulas clave o aspectos a tener en cuenta.") }
                    )
                }
                item {
                    QuickActionChip(
                        label = "💡 Explicar Sencillo",
                        onClick = { onQuickAction("Explica el contenido de este documento en lenguaje sencillo y fácil de entender paso a paso.") }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    onClick: () -> Unit
) {
    val isDark = isAppDark()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1E293B) else ObsidianSubtle,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) ElectricCyan.copy(alpha = 0.4f) else DeepIndigo.copy(alpha = 0.4f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isDark) ElectricCyan else DeepIndigo,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun FilePickerMenu(
    onFileSelected: (Uri) -> Unit,
    onTakePhoto: (Uri) -> Unit
) {
    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // File Picker Launcher for PDFs, DOCX, TXT, Images
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onFileSelected(uri)
        }
    }

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            onTakePhoto(tempPhotoUri!!)
        }
    }

    // Permission Launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara para tomar fotos", Toast.LENGTH_SHORT).show()
        }
    }

    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .size(40.dp)
                .testTag("attach_file_button")
        ) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Adjuntar archivo o foto",
                tint = ElectricCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(ObsidianCard)
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Tomar foto de documento", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Cámara directa con OCR", color = TextSecondaryDark, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        val uri = createTempImageUri(context)
                        tempPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Documento PDF / Word", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lee texto completo y tablas (.pdf, .docx)", color = TextSecondaryDark, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    docPickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "text/plain",
                            "text/*"
                        )
                    )
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Excel (.xlsx / .csv)", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lee tablas, datos, cálculos y celdas", color = TextSecondaryDark, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    docPickerLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "text/csv",
                            "application/octet-stream"
                        )
                    )
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Slideshow,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("PowerPoint (.pptx)", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lee diapositivas y presentaciones", color = TextSecondaryDark, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    docPickerLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/vnd.ms-powerpoint",
                            "application/octet-stream"
                        )
                    )
                }
            )

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Imagen / Galería", color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("JPG, PNG, WebP para OCR", color = TextSecondaryDark, fontSize = 10.sp)
                        }
                    }
                },
                onClick = {
                    showMenu = false
                    docPickerLauncher.launch(arrayOf("image/*"))
                }
            )
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "captured_doc_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
