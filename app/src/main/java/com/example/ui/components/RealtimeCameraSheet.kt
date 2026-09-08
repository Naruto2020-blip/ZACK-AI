package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.util.CameraLensManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun RealtimeCameraSheet(
    onDismiss: () -> Unit,
    onSendResultToChat: (String, Bitmap?) -> Unit,
    onCreateReminder: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Se requiere permiso de cámara para el escaneo en vivo", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedMode by remember { mutableStateOf(CameraLensManager.LensMode.AUTO) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isBackCamera by remember { mutableStateOf(true) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<CameraLensManager.LensAnalysisResult?>(null) }
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Animación de barrido láser
    val infiniteTransition = rememberInfiniteTransition(label = "laser_scanner")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    fun captureAndAnalyze() {
        val capture = imageCapture ?: return
        if (isAnalyzing) return

        isAnalyzing = true
        errorMessage = null
        analysisResult = null

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val (base64, bitmap) = CameraLensManager.processImageProxyToBase64(image)
                    image.close()

                    lastCapturedBitmap = bitmap

                    coroutineScope.launch {
                        val result = CameraLensManager.analyzeImage(
                            context = context,
                            base64Jpeg = base64,
                            mode = selectedMode,
                            bitmap = bitmap
                        )
                        isAnalyzing = false
                        result.onSuccess {
                            analysisResult = it
                        }.onFailure { err ->
                            errorMessage = err.localizedMessage ?: "Error al procesar la imagen"
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    coroutineScope.launch {
                        isAnalyzing = false
                        errorMessage = "Error al capturar imagen: ${exception.localizedMessage}"
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (hasCameraPermission) {
            // Camera Preview via CameraX
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val cameraSelector = if (isBackCamera) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraControl = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            cameraControl?.cameraControl?.enableTorch(isTorchOn)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    cameraControl?.cameraControl?.enableTorch(isTorchOn)
                }
            )

            // Scanning Overlay / Reticle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = size.width * 0.85f
                val boxHeight = size.height * 0.55f
                val left = (size.width - boxWidth) / 2f
                val top = (size.height - boxHeight) / 2f - 40.dp.toPx()

                // Línea láser escaneando activamente
                if (isAnalyzing) {
                    val laserY = top + (boxHeight * laserYRatio)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, ElectricCyan, Color.White, ElectricCyan, Color.Transparent)
                        ),
                        start = Offset(left, laserY),
                        end = Offset(left + boxWidth, laserY),
                        strokeWidth = 4.dp.toPx()
                    )
                }

                // Esquinas del visor (HUD futurista)
                val cornerLength = 32.dp.toPx()
                val cornerWidth = 3.5.dp.toPx()
                val cornerColor = ElectricCyan

                // Top-Left
                drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), cornerWidth)
                drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), cornerWidth)

                // Top-Right
                drawLine(cornerColor, Offset(left + boxWidth, top), Offset(left + boxWidth - cornerLength, top), cornerWidth)
                drawLine(cornerColor, Offset(left + boxWidth, top), Offset(left + boxWidth, top + cornerLength), cornerWidth)

                // Bottom-Left
                drawLine(cornerColor, Offset(left, top + boxHeight), Offset(left + cornerLength, top + boxHeight), cornerWidth)
                drawLine(cornerColor, Offset(left, top + boxHeight), Offset(left, top + boxHeight - cornerLength), cornerWidth)

                // Bottom-Right
                drawLine(cornerColor, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth - cornerLength, top + boxHeight), cornerWidth)
                drawLine(cornerColor, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth, top + boxHeight - cornerLength), cornerWidth)
            }
        } else {
            // Permission Request State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Permiso de Cámara Requerido",
                    color = TextPrimaryDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para leer textos al instante, identificar objetos, códigos QR y facturas en tiempo real, permite el acceso a tu cámara.",
                    color = TextSecondaryDark,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text("Conceder Permiso de Cámara", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Controls Bar (Transparent Gradient)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }

                // Center Title
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📷 Visión en Tiempo Real",
                            color = ElectricCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Flash and Flip
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        IconButton(onClick = {
                            isTorchOn = !isTorchOn
                            cameraControl?.cameraControl?.enableTorch(isTorchOn)
                        }) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (isTorchOn) ElectricCyan else Color.White
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        IconButton(onClick = {
                            isBackCamera = !isBackCamera
                            // Trigger rebind
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cambiar cámara",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Bottom Area: Mode Selector & Shutter Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector Carousel (Horizontal Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraLensManager.LensMode.values().forEach { mode ->
                    val isSelected = mode == selectedMode
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) ElectricCyan else Color.Black.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.clickable {
                            selectedMode = mode
                            analysisResult = null
                            errorMessage = null
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${mode.emoji} ${mode.title}",
                                color = if (isSelected) DarkBackground else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shutter / Scan Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(listOf(ElectricCyan, NeonPurple, ElectricCyan))
                        )
                        .clickable(enabled = !isAnalyzing) {
                            captureAndAnalyze()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = ElectricCyan,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Escanear al Instante",
                                tint = ElectricCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = if (isAnalyzing) "Analizando con IA en tiempo real..." else "Toca para escanear al instante",
                color = if (isAnalyzing) ElectricCyan else Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Floating Result Card
        AnimatedVisibility(
            visible = analysisResult != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            analysisResult?.let { res ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(max = 420.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard.copy(alpha = 0.96f)),
                    border = BorderStroke(1.5.dp, ElectricCyan),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${res.mode.emoji} ${res.detectedCategory}",
                                    color = ElectricCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ElectricCyan.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${res.latencyMs / 1000.0}s",
                                        color = ElectricCyan,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { analysisResult = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar resultado",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Result Text (Scrollable)
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (res.mode == CameraLensManager.LensMode.QR_BARCODE && res.barcodeDetails != null) {
                                val details = res.barcodeDetails
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // 📌 1. NÚMERO DEL CÓDIGO (Grande, claro y con tipo de código)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.2.dp, ElectricCyan.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "TIPO: ${details.codeType.uppercase()}",
                                                    color = ElectricCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = ElectricCyan.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "IDENTIFICADOR OFICIAL",
                                                        color = ElectricCyan,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = details.codeNumber,
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.5.sp
                                            )
                                        }
                                    }

                                    // 📌 2. INFORMACIÓN DEL PRODUCTO (Obtenida por consulta del código)
                                    if (!details.productName.isNullOrBlank() || !details.brand.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                            border = BorderStroke(1.dp, Color(0xFF334155)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "INFORMACIÓN DEL PRODUCTO (Por Código)",
                                                    color = AmberGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)

                                                details.productName?.let {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text("• Producto: ", color = TextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text(it, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                details.brand?.let {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text("• Marca: ", color = TextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text(it, color = TextPrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                                details.category?.let {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text("• Categoría: ", color = TextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text(it, color = TextPrimaryDark, fontSize = 13.sp)
                                                    }
                                                }
                                                details.presentation?.let {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text("• Presentación: ", color = TextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text(it, color = TextPrimaryDark, fontSize = 13.sp)
                                                    }
                                                }
                                                details.countryOrigin?.let {
                                                    Row(modifier = Modifier.fillMaxWidth()) {
                                                        Text("• País / GS1: ", color = TextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                        Text(it, color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = res.textResult,
                                            color = TextPrimaryDark,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            } else if (res.mode == CameraLensManager.LensMode.MEDICINE) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // ⚠️ ACLARACIÓN MÉDICA OBLIGATORIA Y VISIBLE
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = AmberGold.copy(alpha = 0.15f),
                                        border = BorderStroke(1.2.dp, AmberGold.copy(alpha = 0.8f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("⚠️", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Solo información de referencia — no sustituye indicación médica profesional",
                                                color = AmberGold,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    // Si trae código de barras leído y corroborado
                                    val barcodeNum = res.medicineDetails?.barcodeNumber
                                    if (!barcodeNum.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("🏁 Código de barras leído: ", color = TextSecondaryDark, fontSize = 11.sp)
                                                    Text(
                                                        text = barcodeNum,
                                                        color = ElectricCyan,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                Text("CONFIRMADO", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Text(
                                        text = res.textResult,
                                        color = TextPrimaryDark,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = res.textResult,
                                    color = TextPrimaryDark,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy button
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Reconocimiento Cámara", res.textResult))
                                    Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar", fontSize = 12.sp, color = Color.White)
                            }

                            // If detected URL, Open In Browser
                            if (!res.detectedUrl.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.detectedUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir Web", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            // If Commitment or invoice, quick reminder
                            if (res.isCommitment || res.mode == CameraLensManager.LensMode.INVOICE_RECEIPT) {
                                Button(
                                    onClick = {
                                        onCreateReminder(res.textResult.take(80))
                                        Toast.makeText(context, "Recordatorio programado", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Recordar", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            // Send To Chat
                            Button(
                                onClick = {
                                    val summaryPrompt = "📷 Reconocimiento de Cámara [${res.detectedCategory}]:\n\n${res.textResult}"
                                    onSendResultToChat(summaryPrompt, lastCapturedBitmap)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Al Chat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBackground
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error Banner
        AnimatedVisibility(
            visible = errorMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ $err",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
