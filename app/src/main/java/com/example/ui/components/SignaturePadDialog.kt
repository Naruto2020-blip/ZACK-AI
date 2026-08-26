package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.RoseRed
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

data class StrokePath(
    val points: List<Offset>,
    val strokeWidth: Float = 6f
)

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSignatureConfirmed: (Bitmap) -> Unit
) {
    val paths = remember { mutableStateListOf<StrokePath>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var hasContent by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(20.dp))
                .testTag("signature_dialog"),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Firma aquí con el dedo",
                                color = TextPrimaryDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dibuja tu firma en el recuadro blanco",
                                color = TextSecondaryDark,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
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

                // Canvas Area (Pure white background for clean ink rendering)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                ) {
                    // Guide baseline line
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baselineY = size.height * 0.78f
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(20f, baselineY),
                            end = Offset(size.width - 20f, baselineY),
                            strokeWidth = 1.5f
                        )
                    }

                    // Interactive drawing canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints = listOf(offset)
                                        hasContent = true
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.isNotEmpty()) {
                                            paths.add(StrokePath(currentPoints))
                                            currentPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Draw finalized paths
                        for (stroke in paths) {
                            val path = androidx.compose.ui.graphics.Path()
                            if (stroke.points.size > 1) {
                                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (p in stroke.points.drop(1)) {
                                    path.lineTo(p.x, p.y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF0F172A), // Deep ink navy/black
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Draw current active stroke
                        if (currentPoints.size > 1) {
                            val activePath = androidx.compose.ui.graphics.Path()
                            activePath.moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (p in currentPoints.drop(1)) {
                                activePath.lineTo(p.x, p.y)
                            }
                            drawPath(
                                path = activePath,
                                color = Color(0xFF0F172A),
                                style = Stroke(
                                    width = 6f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (!hasContent && paths.isEmpty() && currentPoints.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✍️ Traza tu firma aquí",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: 🔄 Borrar | ✅ Aceptar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            paths.clear()
                            currentPoints = emptyList()
                            hasContent = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_signature_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondaryDark
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔄 Borrar",
                            color = TextSecondaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                val bitmap = createSignatureBitmap(paths.toList())
                                onSignatureConfirmed(bitmap)
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("accept_signature_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color.White
                        ),
                        enabled = hasContent || paths.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✅ Aceptar",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts captured vector stroke paths into an Android Bitmap with transparent background.
 */
private fun createSignatureBitmap(paths: List<StrokePath>): Bitmap {
    val width = 600
    val height = 260
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        color = android.graphics.Color.rgb(15, 23, 42) // #0F172A
        strokeWidth = 7f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Determine bounding box of the strokes to fit cleanly
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE

    for (sp in paths) {
        for (pt in sp.points) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
    }

    if (minX >= maxX || minY >= maxY) {
        return bitmap
    }

    val srcWidth = (maxX - minX).coerceAtLeast(10f)
    val srcHeight = (maxY - minY).coerceAtLeast(10f)

    val padding = 30f
    val scaleX = (width - padding * 2) / srcWidth
    val scaleY = (height - padding * 2) / srcHeight
    val scale = minOf(scaleX, scaleY).coerceIn(0.5f, 2.5f)

    val targetCenterX = width / 2f
    val targetCenterY = height / 2f
    val srcCenterX = minX + srcWidth / 2f
    val srcCenterY = minY + srcHeight / 2f

    canvas.save()
    canvas.translate(targetCenterX - srcCenterX * scale, targetCenterY - srcCenterY * scale)
    canvas.scale(scale, scale)

    for (sp in paths) {
        if (sp.points.size > 1) {
            val path = Path()
            path.moveTo(sp.points[0].x, sp.points[0].y)
            for (p in sp.points.drop(1)) {
                path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, paint)
        }
    }

    canvas.restore()
    return bitmap
}
