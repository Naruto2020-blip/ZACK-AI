package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CompressResult(
    val file: File,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val percentSaved: Int,
    val pageCount: Int
)

data class MergeResult(
    val file: File,
    val totalPages: Int,
    val sourceCount: Int,
    val sizeBytes: Long
)

data class SplitResult(
    val file: File,
    val pageCount: Int,
    val rangeText: String,
    val sizeBytes: Long
)

object PdfToolsManager {

    suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext 1
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            count
        } catch (e: Exception) {
            e.printStackTrace()
            1
        } finally {
            pfd?.close()
        }
    }

    suspend fun compressPdf(
        context: Context,
        uri: Uri,
        compressionQuality: Int = 75 // 0 to 100
    ): Result<CompressResult> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        try {
            val originalSize = getFileSize(context, uri)
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo PDF."))

            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            if (pageCount == 0) {
                renderer.close()
                return@withContext Result.failure(Exception("El PDF no contiene páginas."))
            }

            val newPdfDoc = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val originalWidth = page.width
                val originalHeight = page.height

                // Target dimension for optimal size and clarity (WhatsApp / Email standard 1200 max)
                val maxDim = 1200
                val scale = if (originalWidth > maxDim || originalHeight > maxDim) {
                    val sw = maxDim.toFloat() / originalWidth
                    val sh = maxDim.toFloat() / originalHeight
                    minOf(sw, sh)
                } else 1.0f

                val renderWidth = (originalWidth * scale).toInt().coerceAtLeast(400)
                val renderHeight = (originalHeight * scale).toInt().coerceAtLeast(400)

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                // Compress bitmap to JPEG stream
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, compressionQuality, stream)
                bitmap.recycle()

                val compressedBitmap = BitmapFactory.decodeStream(ByteArrayInputStream(stream.toByteArray()))

                // Add to new PDF Document
                val pageInfo = PdfDocument.PageInfo.Builder(renderWidth, renderHeight, i + 1).create()
                val pdfPage = newPdfDoc.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(compressedBitmap, 0f, 0f, paint)
                newPdfDoc.finishPage(pdfPage)
                compressedBitmap.recycle()
            }
            renderer.close()

            // Save new PDF
            val outDir = File(context.cacheDir, "pdf_tools")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "pdf_comprimido_${System.currentTimeMillis()}.pdf")
            val fos = FileOutputStream(outFile)
            newPdfDoc.writeTo(fos)
            fos.flush()
            fos.close()
            newPdfDoc.close()

            val compressedSize = outFile.length()
            val percentSaved = if (originalSize > 0) {
                (((originalSize - compressedSize).toDouble() / originalSize.toDouble()) * 100).toInt().coerceAtLeast(0)
            } else 0

            Result.success(
                CompressResult(
                    file = outFile,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = compressedSize,
                    percentSaved = percentSaved,
                    pageCount = pageCount
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pfd?.close()
        }
    }

    suspend fun mergePdfs(
        context: Context,
        uris: List<Uri>
    ): Result<MergeResult> = withContext(Dispatchers.IO) {
        if (uris.size < 2) {
            return@withContext Result.failure(Exception("Debes seleccionar al menos 2 archivos PDF para unir."))
        }

        try {
            val newPdfDoc = PdfDocument()
            var pageIndex = 0
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            for (uri in uris) {
                var pfd: ParcelFileDescriptor? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: continue
                    val renderer = PdfRenderer(pfd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val w = page.width
                        val h = page.height
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, pageIndex + 1).create()
                        val pdfPage = newPdfDoc.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                        newPdfDoc.finishPage(pdfPage)
                        bitmap.recycle()
                        pageIndex++
                    }
                    renderer.close()
                } finally {
                    pfd?.close()
                }
            }

            if (pageIndex == 0) {
                newPdfDoc.close()
                return@withContext Result.failure(Exception("No se pudieron leer páginas de los PDFs seleccionados."))
            }

            val outDir = File(context.cacheDir, "pdf_tools")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "pdf_unido_${System.currentTimeMillis()}.pdf")
            val fos = FileOutputStream(outFile)
            newPdfDoc.writeTo(fos)
            fos.flush()
            fos.close()
            newPdfDoc.close()

            Result.success(
                MergeResult(
                    file = outFile,
                    totalPages = pageIndex,
                    sourceCount = uris.size,
                    sizeBytes = outFile.length()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun splitPdf(
        context: Context,
        uri: Uri,
        startPage1Based: Int,
        endPage1Based: Int
    ): Result<SplitResult> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(Exception("No se pudo abrir el PDF."))

            val renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            val start = startPage1Based.coerceIn(1, total)
            val end = endPage1Based.coerceIn(start, total)

            val newPdfDoc = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var newIdx = 0

            for (i in (start - 1) until end) {
                val page = renderer.openPage(i)
                val w = page.width
                val h = page.height
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, newIdx + 1).create()
                val pdfPage = newPdfDoc.startPage(pageInfo)
                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                newPdfDoc.finishPage(pdfPage)
                bitmap.recycle()
                newIdx++
            }
            renderer.close()

            val outDir = File(context.cacheDir, "pdf_tools")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "pdf_dividido_p${start}_a_p${end}_${System.currentTimeMillis()}.pdf")
            val fos = FileOutputStream(outFile)
            newPdfDoc.writeTo(fos)
            fos.flush()
            fos.close()
            newPdfDoc.close()

            Result.success(
                SplitResult(
                    file = outFile,
                    pageCount = newIdx,
                    rangeText = "Páginas $start a $end",
                    sizeBytes = outFile.length()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pfd?.close()
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
