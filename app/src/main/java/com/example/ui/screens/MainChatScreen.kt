package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
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
import com.example.util.AudioRecordManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    // TTS Setup & Speaking State
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Español Latino preferente
                val latinoLocale = Locale("es", "MX")
                val langResult = tts?.setLanguage(latinoLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale("es")
                }
            }
        }
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                coroutineScope.launch { speakingMessageId = null }
            }
            override fun onError(utteranceId: String?) {
                coroutineScope.launch { speakingMessageId = null }
            }
        })
        tts = textToSpeech
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Toggle Speak response in clear Latin Spanish (Only on explicit click, never auto)
    fun toggleSpeak(messageId: String, content: String) {
        if (speakingMessageId == messageId) {
            tts?.stop()
            speakingMessageId = null
        } else {
            tts?.stop()
            val cleanText = cleanMarkdownForSpeech(content)
            speakingMessageId = messageId
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
        }
    }

    // Voice recognition launcher (Mic / dictado de texto en el campo)
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

    // 📤 2. ENVIAR AUDIO — Grabación de voz original sin transcribir
    val audioRecordManager = remember { AudioRecordManager(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableIntStateOf(0) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = audioRecordManager.startRecording()
            if (started) {
                isRecordingAudio = true
                recordingDurationSeconds = 0
            } else {
                Toast.makeText(context, "No se pudo iniciar la grabación de audio", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Se requiere permiso de micrófono para enviar tu voz original", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingDurationSeconds = 0
            while (isActive && isRecordingAudio) {
                delay(1000L)
                recordingDurationSeconds++
            }
        }
    }

    fun startAudioRecording() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val started = audioRecordManager.startRecording()
            if (started) {
                isRecordingAudio = true
                recordingDurationSeconds = 0
            } else {
                Toast.makeText(context, "No se pudo iniciar la grabación de audio", Toast.LENGTH_SHORT).show()
            }
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cancelAudioRecording() {
        audioRecordManager.cancelRecording()
        isRecordingAudio = false
        recordingDurationSeconds = 0
    }

    fun sendAudioRecording() {
        coroutineScope.launch {
            val duration = recordingDurationSeconds
            val result = audioRecordManager.stopAndGetAudio()
            isRecordingAudio = false
            recordingDurationSeconds = 0

            if (result != null && result.base64.isNotBlank()) {
                viewModel.sendRawAudioMessage(
                    audioBase64 = result.base64,
                    mimeType = result.mimeType,
                    durationSeconds = duration.coerceAtLeast(1)
                )
            } else {
                Toast.makeText(context, "Audio demasiado breve o no guardado", Toast.LENGTH_SHORT).show()
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
                currentPersona = uiState.systemPersona,
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
                },
                onSelectPersona = { persona ->
                    viewModel.setSystemPersona(persona)
                    coroutineScope.launch { drawerState.close() }
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
                                    isSpeaking = speakingMessageId == message.id.toString(),
                                    onToggleSpeak = {
                                        toggleSpeak(message.id.toString(), message.content)
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
                    isGenerating = uiState.isGenerating,
                    isRecordingAudio = isRecordingAudio,
                    recordingDurationSeconds = recordingDurationSeconds,
                    onStartAudioRecord = { startAudioRecording() },
                    onCancelAudioRecord = { cancelAudioRecording() },
                    onSendAudioRecord = { sendAudioRecording() }
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

private fun cleanMarkdownForSpeech(text: String): String {
    return text
        .replace(Regex("""```[\s\S]*?```"""), " Código omitido ")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""[*#_~>]"""), "")
        .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
        .replace(Regex("""https?://\S+"""), "")
        .trim()
}

@Composable
fun ChatTopBar(
    title: String,
    onMenuClick: () -> Unit,
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
    isGenerating: Boolean,
    isRecordingAudio: Boolean = false,
    recordingDurationSeconds: Int = 0,
    onStartAudioRecord: () -> Unit = {},
    onCancelAudioRecord: () -> Unit = {},
    onSendAudioRecord: () -> Unit = {}
) {
    Surface(
        color = ObsidianBackground,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isRecordingAudio) {
            // Live Audio Recording Interface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grabando voz...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF831843),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", recordingDurationSeconds / 60, recordingDurationSeconds % 60),
                            color = Color(0xFFF472B6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cancel Audio Recording button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF334155),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onCancelAudioRecord() }
                            .testTag("cancel_audio_record_button"),
                        contentColor = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar grabación",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Send Raw Audio button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Unspecified,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFEC4899), RadiantViolet)))
                            .clickable { onSendAudioRecord() }
                            .testTag("submit_audio_record_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar audio original",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enviar Audio",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Standard Chat Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Attach File / Camera button (📎 / ➕)
                FilePickerMenu(
                    onFileSelected = onFileSelected,
                    onTakePhoto = onTakePhoto
                )

                // 2. Mic button (🎤 Dictado a texto en el campo)
                IconButton(
                    onClick = onVoiceRecord,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictar texto",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 3. 📤 ENVIAR AUDIO — PREGUNTA EN VOZ ORIGINAL (Graba y envía el audio tal cual sin transcribir)
                IconButton(
                    onClick = onStartAudioRecord,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("send_raw_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Enviar pregunta en voz original",
                        tint = Color(0xFFF472B6),
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
                            text = "Escribe o sube PDF, Word, Excel, PPT...",
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
                        .size(42.dp)
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
}
