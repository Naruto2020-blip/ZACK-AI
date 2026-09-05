package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ShoppingCategory
import com.example.data.model.ShoppingItem
import com.example.ui.theme.*
import com.example.util.ShareUtils
import com.example.util.ShoppingCategorizer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListSheet(
    items: List<ShoppingItem>,
    onAddItems: (String) -> Unit,
    onToggleItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearBought: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var showConfirmNewListDialog by remember { mutableStateOf(false) }
    var showDownloadShareDialog by remember { mutableStateOf(false) }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = spokenMatches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                onAddItems(spoken)
                Toast.makeText(context, "✅ Elementos agregados y organizados automáticamente", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val totalCount = items.size
    val boughtCount = items.count { it.isBought }
    val progress = if (totalCount > 0) boughtCount.toFloat() / totalCount else 0f

    // Fixed categories as requested: 🥩 Carnicería · 🥬 Feria/Verduras · 🏪 Palí/Supermercado · 📦 Varios
    val fixedCategories = listOf(
        ShoppingCategory.MEAT,
        ShoppingCategory.VEGGIES,
        ShoppingCategory.SUPERMARKET,
        ShoppingCategory.OTHERS
    )

    // Dialog for "Nueva Lista" (Empiezas de cero)
    if (showConfirmNewListDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmNewListDialog = false },
            title = {
                Text(
                    text = "📋 ¿Empezar Nueva Lista?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
            },
            text = {
                Text(
                    text = "Se borrarán todos los productos actuales de la lista para empezar desde cero.",
                    color = TextSecondaryDark,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showConfirmNewListDialog = false
                        Toast.makeText(context, "Lista reiniciada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Empezar de cero", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmNewListDialog = false }) {
                    Text("Cancelar", color = TextSecondaryDark)
                }
            },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog for "Descargar / Compartir Lista"
    if (showDownloadShareDialog) {
        val formattedContent = remember(items) { ShoppingCategorizer.formatListForSharing(items) }
        AlertDialog(
            onDismissRequest = { showDownloadShareDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📥 Descargar o Enviar Lista",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tu lista organizada por categorías está lista:",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(8.dp)),
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(
                                    text = formattedContent,
                                    fontSize = 12.sp,
                                    color = TextPrimaryDark,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action: Copiar al portapapeles
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                ShareUtils.copyToClipboard(context, formattedContent, "Lista de Compras")
                                showDownloadShareDialog = false
                            }
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        color = ElectricCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("📋 Copiar al Portapapeles", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElectricCyan)
                                Text("Listo para pegar en WhatsApp o notas", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action: Compartir por WhatsApp / Enviar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                ShareUtils.shareViaWhatsApp(context, formattedContent)
                                showDownloadShareDialog = false
                            }
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        color = EmeraldGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("💬 Enviar por WhatsApp / Mensajes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldGreen)
                                Text("Compartir directamente con contactos", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action: Guardar archivo .txt descargable
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                try {
                                    val dir = File(context.cacheDir, "listas_compras")
                                    if (!dir.exists()) dir.mkdirs()
                                    val file = File(dir, "Lista_Compras_${System.currentTimeMillis()}.txt")
                                    file.writeText(formattedContent)
                                    ShareUtils.shareFile(context, file, "text/plain", "Descargar Lista de Compras")
                                    Toast.makeText(context, "Archivo de lista generado", Toast.LENGTH_SHORT).show()
                                    showDownloadShareDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .border(1.dp, RadiantViolet.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        color = RadiantViolet.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = RadiantViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("📄 Guardar archivo (.txt)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RadiantViolet)
                                Text("Descargar archivo de texto al dispositivo", fontSize = 11.sp, color = TextSecondaryDark)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadShareDialog = false }) {
                    Text("Cerrar", color = TextSecondaryDark)
                }
            },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

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
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AmberGold.copy(alpha = 0.15f))
                            .border(1.dp, AmberGold.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🛒", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lista de Compras",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Organizada automáticamente por lugares",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Bar:
            // 📋 Nueva lista | 📥 Descargar lista | 🗑️ Limpiar comprados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 📋 Nueva lista
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showConfirmNewListDialog = true }
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(8.dp))
                        .testTag("shopping_new_list_button"),
                    color = ObsidianCard,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PostAdd,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📋 Nueva lista",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            maxLines = 1
                        )
                    }
                }

                // 📥 Descargar lista
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = items.isNotEmpty()) { showDownloadShareDialog = true }
                        .border(
                            1.dp,
                            if (items.isNotEmpty()) ElectricCyan.copy(alpha = 0.4f) else ObsidianCardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("shopping_download_list_button"),
                    color = if (items.isNotEmpty()) ElectricCyan.copy(alpha = 0.10f) else ObsidianCard.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = if (items.isNotEmpty()) ElectricCyan else TextSecondaryDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📥 Descargar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (items.isNotEmpty()) ElectricCyan else TextSecondaryDark,
                            maxLines = 1
                        )
                    }
                }

                // 🗑️ Limpiar comprados
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = boughtCount > 0) {
                            onClearBought()
                            Toast.makeText(context, "Se borraron los productos comprados", Toast.LENGTH_SHORT).show()
                        }
                        .border(
                            1.dp,
                            if (boughtCount > 0) Color(0xFFEF4444).copy(alpha = 0.4f) else ObsidianCardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("shopping_clear_bought_button"),
                    color = if (boughtCount > 0) Color(0xFFEF4444).copy(alpha = 0.10f) else ObsidianCard.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = if (boughtCount > 0) Color(0xFFEF4444) else TextSecondaryDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "🗑️ Limpiar comprados",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (boughtCount > 0) Color(0xFFEF4444) else TextSecondaryDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Row: Text field + 🎙️ Voice button + ➕ Add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Escribe o di: 2 kg carne, tomates, leche...",
                            color = TextSecondaryDark.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shopping_input_field"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedBorderColor = RadiantViolet,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputText.isNotBlank()) {
                                onAddItems(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 🎙️ Voice dictation button
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Di lo que necesitas comprar (ej. carne, tomates, leche, pollo...)")
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "El dictado por voz no está disponible. Puedes escribir los productos.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .border(1.dp, RadiantViolet.copy(alpha = 0.5f), CircleShape)
                        .testTag("shopping_voice_button"),
                    color = RadiantViolet.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Dictar por voz",
                            tint = RadiantViolet,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ➕ Add button
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(enabled = inputText.isNotBlank()) {
                            if (inputText.isNotBlank()) {
                                onAddItems(inputText)
                                inputText = ""
                            }
                        }
                        .border(
                            1.dp,
                            if (inputText.isNotBlank()) ElectricCyan else ObsidianCardBorder,
                            CircleShape
                        )
                        .testTag("shopping_add_button"),
                    color = if (inputText.isNotBlank()) ElectricCyan else ObsidianCard,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar a la lista",
                            tint = if (inputText.isNotBlank()) DarkBackground else TextSecondaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Quick Example Chips (Tap to auto-add or test)
            if (items.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    val quickExamples = listOf(
                        "🥩 Carne, tomates, leche, pollo, frijoles, plátanos, pan, cebolla",
                        "🍗 2 kg de pollo, 1 kilo de papas, 3 leches",
                        "🥗 Lechuga, aguacate, pepino, limón, queso"
                    )
                    items(quickExamples) { example ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val cleaned = example.replace("🥩 ", "").replace("🍗 ", "").replace("🥗 ", "")
                                    onAddItems(cleaned)
                                }
                                .border(0.8.dp, ObsidianCardBorder, RoundedCornerShape(8.dp)),
                            color = ObsidianCard,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = example,
                                fontSize = 11.sp,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Summary if there are items
            if (totalCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$boughtCount de $totalCount productos comprados",
                        fontSize = 12.sp,
                        color = if (boughtCount == totalCount && totalCount > 0) EmeraldGreen else TextSecondaryDark,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = if (boughtCount == totalCount && totalCount > 0) EmeraldGreen else ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (boughtCount == totalCount) EmeraldGreen else ElectricCyan,
                    trackColor = ObsidianCardBorder
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Main List separated by categories:
            // 🥩 Carnicería · 🥬 Feria/Verduras · 🏪 Palí/Supermercado · 📦 Varios
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(text = "🛒", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tu lista de compras está vacía",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Escribe o toca el micrófono 🎙️ y di lo que necesitas (ej. \"Carne, tomates, leche, pollo, frijoles, plátanos, pan, cebolla\"). La app lo separa sola en cada lugar.",
                            color = TextSecondaryDark,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    fixedCategories.forEach { category ->
                        val categoryItems = items.filter { it.category == category }
                        if (categoryItems.isNotEmpty()) {
                            item(key = "header_${category.id}") {
                                CategorySectionHeader(
                                    category = category,
                                    itemCount = categoryItems.size,
                                    boughtCount = categoryItems.count { it.isBought }
                                )
                            }

                            items(categoryItems, key = { it.id }) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onToggle = { onToggleItem(item.id) },
                                    onDelete = { onDeleteItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(
    category: ShoppingCategory,
    itemCount: Int,
    boughtCount: Int
) {
    val categoryColor = Color(category.colorHex)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        color = categoryColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = category.emoji, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = categoryColor
                )
            }

            Surface(
                color = categoryColor.copy(alpha = 0.20f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (boughtCount > 0) "$boughtCount/$itemCount" else "$itemCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val isBought = item.isBought
    val animatedBg by animateColorAsState(
        targetValue = if (isBought) ObsidianCard.copy(alpha = 0.45f) else ObsidianCard,
        label = "item_bg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() }
            .border(
                1.dp,
                if (isBought) EmeraldGreen.copy(alpha = 0.35f) else ObsidianCardBorder,
                RoundedCornerShape(10.dp)
            ),
        color = animatedBg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkmark circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isBought) EmeraldGreen else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isBought) EmeraldGreen else TextSecondaryDark.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBought) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Comprado",
                        tint = DarkBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Item Name & Quantity
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.quantity.isNotBlank()) {
                        Surface(
                            color = RadiantViolet.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, RadiantViolet.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = item.quantity,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RadiantViolet,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = item.name,
                        fontSize = 14.sp,
                        fontWeight = if (isBought) FontWeight.Normal else FontWeight.Medium,
                        color = if (isBought) TextSecondaryDark else TextPrimaryDark,
                        textDecoration = if (isBought) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isBought) {
                    Text(
                        text = "✅ Comprado",
                        fontSize = 10.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Delete individual item
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Eliminar de la lista",
                    tint = TextSecondaryDark.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
