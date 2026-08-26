package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AttachmentActionBar
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.FilePickerMenu
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.RadiantViolet
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // TTS Setup
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("es-ES")
            }
        }
        tts = textToSpeech
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = spokenText
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Handle snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                sessions = sessions,
                currentSessionId = uiState.currentSessionId,
                onSelectSession = { sessionId ->
                    viewModel.selectSession(sessionId)
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.createNewSession()
                    coroutineScope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onClearAll = {
                    viewModel.clearAllSessions()
                    coroutineScope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showSettingsSheet = true
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            containerColor = ObsidianBackground,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Custom App Bar
                ChatTopBar(
                    title = "ZACK AI",
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onSettingsClick = { showSettingsSheet = true },
                    onNewChatClick = { viewModel.createNewSession() }
                )

                // Chat Messages List or Empty Starter State
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.messages.isEmpty()) {
                        EmptyChatState(
                            onSuggestionSelected = { prompt ->
                                inputText = prompt
                                viewModel.sendMessage(prompt)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_message_list"),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                ChatMessageBubble(
                                    message = message,
                                    sessionTitle = uiState.currentSessionTitle,
                                    onSpeak = { text ->
                                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "msg_${message.id}")
                                    }
                                )
                            }

                            // Typing / Generating Indicator
                            if (uiState.isGenerating) {
                                item {
                                    GeneratingIndicator()
                                }
                            }
                        }
                    }
                }

                // Attached file preview and quick action buttons
                AttachmentActionBar(
                    attachedFile = uiState.attachedFile,
                    isProcessingFile = uiState.isProcessingFile,
                    onRemoveAttachment = { viewModel.removeAttachedFile() },
                    onQuickAction = { promptAction ->
                        viewModel.sendMessage(inputText, customActionPrefix = promptAction)
                        inputText = ""
                    }
                )

                // Bottom Chat Input Field Bar (Clean & Focused)
                ChatInputBar(
                    inputText = inputText,
                    onTextChanged = { inputText = it },
                    onSend = {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendMessage(toSend)
                    },
                    onVoiceRecord = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFileSelected = { uri ->
                        viewModel.attachFileUri(uri)
                    },
                    onTakePhoto = { uri ->
                        viewModel.attachFileUri(uri)
                    },
                    isGenerating = uiState.isGenerating
                )
            }
        }
    }

    // Settings Bottom Sheet Modal
    if (showSettingsSheet) {
        SettingsSheet(
            uiState = uiState,
            onDismiss = { showSettingsSheet = false },
            onSetPersona = { persona ->
                viewModel.setSystemPersona(persona)
            },
            onSaveApiKey = { newKey ->
                viewModel.saveApiKey(newKey)
            }
        )
    }
}

@Composable
fun ChatTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    Surface(
        color = ObsidianBackground,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("menu_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menú",
                        tint = TextPrimaryDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("settings_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = CyanAccent
                    )
                }

                IconButton(
                    onClick = onNewChatClick,
                    modifier = Modifier.testTag("new_chat_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nuevo Chat",
                        tint = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    onSuggestionSelected: (String) -> Unit
) {
    val suggestions = listOf(
        "⚡ Explica un concepto complejo en términos sencillos",
        "💻 Escribe una función Kotlin limpia para ordenar colecciones",
        "🧠 Analiza los pros y contras de una arquitectura limpia",
        "✍️ Redacta un correo profesional solicitando una reunión de estrategia"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Icon Orb
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = ObsidianBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Suggestions Title
        Text(
            text = "Sugerencias de inicio rápido:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryDark,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Suggestion Chips Column
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { prompt ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSuggestionSelected(prompt) }
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp)),
                    color = ObsidianCard
                ) {
                    Text(
                        text = prompt,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GeneratingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ObsidianBackground,
                strokeWidth = 2.dp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ObsidianCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generando respuesta...",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanAccent,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceRecord: () -> Unit,
    onFileSelected: (android.net.Uri) -> Unit,
    onTakePhoto: (android.net.Uri) -> Unit,
    isGenerating: Boolean
) {
    Surface(
        color = ObsidianBackground,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach File / Camera button
            FilePickerMenu(
                onFileSelected = onFileSelected,
                onTakePhoto = onTakePhoto
            )

            // Voice record button
            IconButton(
                onClick = onVoiceRecord,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("voice_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Grabar voz",
                    tint = ElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Main Text Input
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = "Escribe o sube PDF, Word, fotos...",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ObsidianCardBorder,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    cursorColor = ElectricCyan
                ),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputText.isNotBlank() && !isGenerating) onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isGenerating) {
                            Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                        }
                    )
                    .clickable(enabled = (inputText.isNotBlank() || isGenerating.not()) && !isGenerating) {
                        onSend()
                    }
                    .testTag("send_message_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondaryDark
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank()) ObsidianBackground else TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
