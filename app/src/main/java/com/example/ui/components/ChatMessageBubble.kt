package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.ui.theme.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.util.DocumentExporter
import com.example.util.ExportFormat
import android.graphics.Bitmap
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.example.util.DocumentSignatureDetector
import com.example.util.DocumentCleaner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    onSpeak: (String) -> Unit = {},
    isSpeaking: Boolean = false,
    onToggleSpeak: () -> Unit = { onSpeak(message.content) },
    modifier: Modifier = Modifier,
    sessionTitle: String? = null,
    onToggleFavorite: (Long, Boolean) -> Unit = { _, _ -> }
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var showCascadeDetails by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Smart Signature State
    var showSignaturePad by remember { mutableStateOf(false) }
    var showSignaturePlacement by remember { mutableStateOf(false) }
    var currentSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val isSignableDocument = remember(message.content, message.isError) {
        !message.isError && DocumentSignatureDetector.isSignableDocument(message.content)
    }

    val displayContent = remember(message.content, message.isError, isSignableDocument) {
        if (!message.isError && (isSignableDocument || message.content.contains("Para redactar", ignoreCase = true) || message.content.contains("[ej:", ignoreCase = true))) {
            DocumentCleaner.cleanLetterDocument(message.content, sessionTitle)
        } else {
            message.content
        }
    }

    val timeFormat12h = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat12h.format(Date(message.timestamp)) }
    val formattedDateTime = remember(message.timestamp) { dateFormat.format(Date(message.timestamp)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User Bubble
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = Color.Unspecified,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(DeepIndigo, Color(0xFF4F46E5))
                            ),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(DeepIndigo, RadiantViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Usuario",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // AI Assistant Bubble
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // AI Avatar with dynamic glowing gradient
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "IA",
                        tint = if (isAppDark()) DarkBackground else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Card(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ObsidianCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (message.isError) AmberGold.copy(alpha = 0.35f) else ObsidianCardBorder,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Top timestamp header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Message Text Body
                        if (message.isError) {
                            SelectionContainer {
                                Text(
                                    text = message.content,
                                    color = TextPrimaryDark,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            SelectionContainer {
                                MarkdownContent(
                                    text = displayContent,
                                    textColor = TextPrimaryDark
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Actions Bar (Date/Time 12h, Download Button, Copy & Read Aloud)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // ✍️ Firmar button (ONLY if signable document: letters, resignations, contracts, requests, agreements, affidavits)
                                if (isSignableDocument && message.content.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldGreen.copy(alpha = if (currentSignatureBitmap != null) 0.35f else 0.18f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (currentSignatureBitmap != null) EmeraldGreen else EmeraldGreen.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (currentSignatureBitmap != null) {
                                                    showSignaturePlacement = true
                                                } else {
                                                    showSignaturePad = true
                                                }
                                            }
                                            .testTag("sign_document_button_${message.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (currentSignatureBitmap != null) Icons.Default.Check else Icons.Default.Draw,
                                                contentDescription = "Firmar",
                                                tint = EmeraldGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (currentSignatureBitmap != null) "✍️ Firmado" else "✍️ Firmar",
                                                color = EmeraldGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // 📥 Descargar button (only if not an error)
                                if (!message.isError && message.content.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DeepIndigo.copy(alpha = 0.3f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DeepIndigo.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showExportSheet = true }
                                            .testTag("download_button_${message.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isExporting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(13.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = ElectricCyan
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Exportando...",
                                                    color = ElectricCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = "Descargar",
                                                    tint = ElectricCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "📥 Descargar",
                                                    color = ElectricCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                // 🔊 Altavoz (Leer respuesta en voz alta / Detener lectura)
                                if (isSpeaking) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = RadiantViolet.copy(alpha = 0.25f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, RadiantViolet),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onToggleSpeak() }
                                            .testTag("stop_speak_button_${message.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Detener lectura",
                                                tint = RadiantViolet,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "⏹️ Detener",
                                                color = RadiantViolet,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onSpeak(displayContent) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("speak_message_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Leer en voz alta",
                                            tint = TextSecondaryDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Documento", displayContent)
                                        clipboard.setPrimaryClip(clip)
                                        isCopied = true
                                        Toast.makeText(context, "Respuesta copiada", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            delay(2000L)
                                            isCopied = false
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("copy_message_button")
                                ) {
                                    if (isCopied) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Copiado",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar respuesta",
                                            tint = TextSecondaryDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // ⭐ Botón de Favorito
                                if (!message.isError && message.content.isNotBlank()) {
                                    IconButton(
                                        onClick = { onToggleFavorite(message.id, !message.isFavorite) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("favorite_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (message.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (message.isFavorite) "Quitar de favoritos" else "Guardar en favoritos",
                                            tint = if (message.isFavorite) AmberGold else TextSecondaryDark,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // 📤 Botón de Compartir (WhatsApp, Correo, Portapapeles)
                                if (!message.isError && message.content.isNotBlank()) {
                                    IconButton(
                                        onClick = { showShareSheet = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("share_message_button_${message.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Compartir respuesta",
                                            tint = TextSecondaryDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet de Compartir
    if (showShareSheet) {
        ShareMessageSheet(
            content = displayContent,
            onDismiss = { showShareSheet = false }
        )
    }

    // Modal Bottom Sheet with the 4 export formats
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState,
            containerColor = ObsidianCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (isAppDark()) Color(0xFF475569) else Color(0xFFCBD5E1))
                )
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            ExportFormatSelectorSheet(
                onSelectFormat = { format ->
                    showExportSheet = false
                    scope.launch {
                        isExporting = true
                        DocumentExporter.exportAndShare(
                            context = context,
                            content = displayContent,
                            format = format,
                            sessionTitle = sessionTitle,
                            signatureBitmap = currentSignatureBitmap
                        )
                        isExporting = false
                    }
                },
                onDismiss = { showExportSheet = false }
            )
        }
    }

    // 1. Signature Pad Dialog (Draw with finger)
    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSignatureConfirmed = { bitmap ->
                currentSignatureBitmap = bitmap
                showSignaturePad = false
                showSignaturePlacement = true
                Toast.makeText(context, "Firma guardada. Ajusta su tamaño y posición.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Signature Placement & Adjustment Dialog
    if (showSignaturePlacement && currentSignatureBitmap != null) {
        SignaturePlacementDialog(
            signatureBitmap = currentSignatureBitmap!!,
            documentContent = displayContent,
            onDismiss = { showSignaturePlacement = false },
            onReSign = {
                showSignaturePlacement = false
                showSignaturePad = true
            },
            onConfirmAndDownload = { _, _ ->
                showSignaturePlacement = false
                Toast.makeText(context, "Firma fijada en el documento. Ya puedes descargarlo.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ExportFormatSelectorSheet(
    onSelectFormat: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📥 Descargar Documento",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Elige el formato en el que deseas guardar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 Options Grid/List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExportFormatOptionCard(
                format = ExportFormat.PDF,
                badgeColor = Color(0xFFEF4444), // Red for PDF
                icon = Icons.Default.PictureAsPdf,
                onClick = { onSelectFormat(ExportFormat.PDF) }
            )

            ExportFormatOptionCard(
                format = ExportFormat.WORD,
                badgeColor = Color(0xFF2563EB), // Blue for Word
                icon = Icons.Default.Description,
                onClick = { onSelectFormat(ExportFormat.WORD) }
            )

            ExportFormatOptionCard(
                format = ExportFormat.POWERPOINT,
                badgeColor = Color(0xFFEA580C), // Orange for PowerPoint
                icon = Icons.Default.Slideshow,
                onClick = { onSelectFormat(ExportFormat.POWERPOINT) }
            )

            ExportFormatOptionCard(
                format = ExportFormat.EXCEL,
                badgeColor = Color(0xFF16A34A), // Green for Excel
                icon = Icons.Default.TableChart,
                onClick = { onSelectFormat(ExportFormat.EXCEL) }
            )
        }
    }
}

@Composable
fun ExportFormatOptionCard(
    format: ExportFormat,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
            .testTag("export_option_${format.extension}"),
        color = if (isAppDark()) ObsidianBackground.copy(alpha = 0.7f) else ObsidianSubtle,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = format.displayName,
                    tint = badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.displayName,
                        color = TextPrimaryDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.2f),
                        modifier = Modifier.padding(1.dp)
                    ) {
                        Text(
                            text = format.badge,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = format.description,
                    color = TextSecondaryDark,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = ElectricCyan.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
