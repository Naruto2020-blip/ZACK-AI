package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.isAppDark
import java.net.URLEncoder

private data class QuickBookmark(
    val title: String,
    val url: String,
    val iconName: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserSheet(
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onSendToChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var urlInput by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isHomeView by remember { mutableStateOf(initialUrl.isBlank()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val quickBookmarks = remember {
        listOf(
            QuickBookmark("Google", "https://www.google.com", "G", ElectricCyan),
            QuickBookmark("Wikipedia", "https://es.wikipedia.org", "W", Color(0xFF64748B)),
            QuickBookmark("Google News", "https://news.google.com", "N", Color(0xFF3B82F6)),
            QuickBookmark("GitHub", "https://github.com", "GH", Color(0xFFA855F7)),
            QuickBookmark("Reddit", "https://www.reddit.com", "R", Color(0xFFFF4500)),
            QuickBookmark("YouTube", "https://www.youtube.com", "YT", Color(0xFFEF4444)),
            QuickBookmark("Stack Overflow", "https://stackoverflow.com", "SO", Color(0xFFF59E0B)),
            QuickBookmark("BBC Mundo", "https://www.bbc.com/mundo", "BBC", Color(0xFFDC2626))
        )
    }

    fun sanitizeAndLoadUrl(input: String) {
        focusManager.clearFocus()
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> {
                val encoded = try { URLEncoder.encode(trimmed, "UTF-8") } catch (_: Exception) { trimmed }
                "https://www.google.com/search?q=$encoded"
            }
        }
        urlInput = targetUrl
        currentUrl = targetUrl
        isHomeView = false
        loadError = null
        webViewInstance?.loadUrl(targetUrl)
    }

    // Interceptar botón 'atrás' físico del dispositivo
    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true && !isHomeView) {
            webViewInstance?.goBack()
        } else if (!isHomeView) {
            isHomeView = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = DarkTextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isAppDark()) Color(0xFF475569) else Color(0xFFCBD5E1))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier
            .fillMaxHeight(0.96f)
            .imePadding()
            .testTag("web_browser_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
        ) {
            // ==========================================
            // BARRA SUPERIOR: URL + NAVEGACIÓN + ACCIONES
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ObsidianCard,
                border = BorderStroke(1.dp, ObsidianCardBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // Fila 1: Flechas navegación + Omnibar + Botón IA + Cerrar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Atrás
                        IconButton(
                            onClick = {
                                if (webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                } else {
                                    isHomeView = true
                                }
                            },
                            enabled = canGoBack || !isHomeView,
                            modifier = Modifier.size(36.dp).testTag("browser_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Página anterior",
                                tint = if (canGoBack || !isHomeView) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Adelante
                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(36.dp).testTag("browser_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Página siguiente",
                                tint = if (canGoForward) DarkTextPrimary else DarkTextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Inicio (Home)
                        IconButton(
                            onClick = {
                                isHomeView = true
                                urlInput = ""
                            },
                            modifier = Modifier.size(36.dp).testTag("browser_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Inicio del navegador",
                                tint = if (isHomeView) ElectricCyan else DarkTextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Barra de Dirección / Búsqueda
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("browser_url_input"),
                            placeholder = {
                                Text(
                                    text = "Buscar o escribir URL...",
                                    fontSize = 12.sp,
                                    color = DarkTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = DarkTextPrimary
                            ),
                            leadingIcon = {
                                if (currentUrl.startsWith("https://")) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Conexión segura",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Buscar",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                if (urlInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { urlInput = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Borrar",
                                            tint = DarkTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { sanitizeAndLoadUrl(urlInput) },
                                onDone = { sanitizeAndLoadUrl(urlInput) }
                            ),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground,
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianCardBorder,
                                cursorColor = ElectricCyan
                            )
                        )

                        // Recargar / Ir
                        IconButton(
                            onClick = {
                                if (urlInput != currentUrl && urlInput.isNotBlank()) {
                                    sanitizeAndLoadUrl(urlInput)
                                } else {
                                    webViewInstance?.reload()
                                }
                            },
                            modifier = Modifier.size(36.dp).testTag("browser_reload_button")
                        ) {
                            Icon(
                                imageVector = if (urlInput != currentUrl && urlInput.isNotBlank()) Icons.Default.Search else Icons.Default.Refresh,
                                contentDescription = "Recargar o buscar",
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Cerrar
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).testTag("browser_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar navegador",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Fila 2: Título de página actual + Acciones rápidas con Zack AI
                    if (!isHomeView && currentUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (pageTitle.isNotBlank()) pageTitle else currentUrl,
                                    color = DarkTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Enviar a Zack AI para resumir o analizar
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val prompt = "Por favor analiza y resume esta página web:\n${pageTitle.ifBlank { "Página" }}\nURL: $currentUrl"
                                            onSendToChat(prompt)
                                            onDismiss()
                                        }
                                        .testTag("browser_send_to_ai_button"),
                                    color = NeonPurple.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, NeonPurple.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Analizar con IA",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Consultar con Zack AI",
                                            color = NeonPurple,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Compartir URL
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir enlace"))
                                    },
                                    modifier = Modifier.size(28.dp).testTag("browser_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir enlace",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Abrir en navegador externo
                                IconButton(
                                    onClick = {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se pudo abrir navegador externo", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp).testTag("browser_open_external_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Abrir externamente",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Barra de progreso de carga
            AnimatedVisibility(
                visible = isLoading && progress < 1f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = ElectricCyan,
                    trackColor = ObsidianBackground
                )
            }

            // ==========================================
            // CUERPO: PANTALLA DE INICIO O VISTA WEB
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isHomeView) {
                    // ----------------------------------------
                    // PANTALLA DE INICIO: MARCADORES Y ACCESOS
                    // ----------------------------------------
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.12f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Navegador Web",
                                tint = ElectricCyan,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Navegador Web Integrado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Navega por internet, busca información o consulta cualquier página directamente con Zack AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Título de sección
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SITIOS POPULARES Y MARCADORES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cuadrícula de accesos rápidos
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(quickBookmarks) { bookmark ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { sanitizeAndLoadUrl(bookmark.url) }
                                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp)),
                                    color = ObsidianCard,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(bookmark.color.copy(alpha = 0.16f))
                                                .border(1.dp, bookmark.color.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = bookmark.iconName,
                                                color = bookmark.color,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = bookmark.title,
                                            fontSize = 11.sp,
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Tarjeta de sugerencia con Zack AI
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(NeonPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Integrado con tu Asistente",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = DarkTextPrimary
                                    )
                                    Text(
                                        text = "Cuando visites cualquier artículo o noticia, toca 'Consultar con Zack AI' para que te explique o resuma el contenido.",
                                        fontSize = 11.sp,
                                        color = DarkTextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ----------------------------------------
                    // VISTA WEB (ANDROID WEBVIEW)
                    // ----------------------------------------
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setupWebViewSettings(this)

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            url?.let {
                                                currentUrl = it
                                                urlInput = it
                                            }
                                            canGoBack = view?.canGoBack() == true
                                            canGoForward = view?.canGoForward() == true
                                            loadError = null
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            url?.let {
                                                currentUrl = it
                                                urlInput = it
                                            }
                                            pageTitle = view?.title ?: ""
                                            canGoBack = view?.canGoBack() == true
                                            canGoForward = view?.canGoForward() == true
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val nextUrl = request?.url?.toString() ?: ""
                                            if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                                                return false // Cargar en el mismo WebView
                                            }
                                            // Esquemas externos (mailto, tel, intent, etc.)
                                            return try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl))
                                                ctx.startActivity(intent)
                                                true
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                isLoading = false
                                                loadError = "No se pudo cargar la página. Verifica tu conexión o la dirección web."
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            progress = newProgress / 100f
                                            if (newProgress >= 100) {
                                                isLoading = false
                                            }
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            title?.let { pageTitle = it }
                                        }
                                    }

                                    webViewInstance = this
                                    if (currentUrl.isNotBlank()) {
                                        loadUrl(currentUrl)
                                    }
                                }
                            },
                            update = { webView ->
                                webViewInstance = webView
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("browser_webview")
                        )

                        // En caso de error de carga
                        if (loadError != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ObsidianBackground)
                                    .padding(24.dp),
                                color = ObsidianBackground
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Error al cargar la página",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = DarkTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = loadError ?: "",
                                        fontSize = 13.sp,
                                        color = DarkTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { webViewInstance?.reload() },
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                                        ) {
                                            Text("Reintentar", fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { isHomeView = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianCard, contentColor = DarkTextPrimary)
                                        ) {
                                            Text("Ir al Inicio")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
            } catch (_: Exception) {}
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewSettings(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        userAgentString = userAgentString.replace("; wv", "") // Mejor compatibilidad con sitios móviles
    }
}
