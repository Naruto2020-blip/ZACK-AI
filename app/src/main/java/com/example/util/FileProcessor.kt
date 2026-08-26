package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ProcessedAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val base64Data: String? = null,
    val extractedText: String? = null,
    val pageCount: Int = 1
)

object FileProcessor {

    suspend fun processUri(context: Context, uri: Uri): ProcessedAttachment = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val name = getFileName(context, uri)
        val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromExtension(name)
        val sizeBytes = getFileSize(context, uri)

        val isImage = mimeType.startsWith("image/")

        if (isImage) {
            // Convert / downscale image to base64 jpeg
            val base64 = processImageToBase64(context, uri)
            return@withContext ProcessedAttachment(
                uri = uri,
                name = name,
                mimeType = "image/jpeg",
                sizeBytes = sizeBytes,
                isImage = true,
                base64Data = base64,
                extractedText = null
            )
        }

        // PDF Handling
        if (mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
            val (pdfText, pageCount, pdfBase64) = processPdf(context, uri)
            return@withContext ProcessedAttachment(
                uri = uri,
                name = name,
                mimeType = "application/pdf",
                sizeBytes = sizeBytes,
                isImage = false,
                base64Data = pdfBase64,
                extractedText = pdfText,
                pageCount = pageCount
            )
        }

        // DOCX Handling
        if (name.endsWith(".docx", ignoreCase = true) || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
            val docxText = extractTextFromDocx(context, uri)
            return@withContext ProcessedAttachment(
                uri = uri,
                name = name,
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                sizeBytes = sizeBytes,
                isImage = false,
                extractedText = docxText
            )
        }

        // Plain text / Markdown / CSV / JSON
        val plainText = readPlainText(context, uri)
        return@withContext ProcessedAttachment(
            uri = uri,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isImage = false,
            extractedText = plainText
        )
    }

    private fun processImageToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original == null) return null

            // Downscale to max 1600px width/height while keeping aspect ratio
            val maxDimension = 1600
            val width = original.width
            val height = original.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val scaleW = maxDimension.toFloat() / width
                val scaleH = maxDimension.toFloat() / height
                minOf(scaleW, scaleH)
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(original, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                original
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun processPdf(context: Context, uri: Uri): Triple<String?, Int, String?> {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val base64 = if (bytes != null && bytes.size < 15 * 1024 * 1024) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null

            // Try reading page count using PdfRenderer
            var pageCount = 1
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    renderer.close()
                }
            } catch (e: Exception) {
                // Ignore renderer exception
            } finally {
                pfd?.close()
            }

            // Extract plain ascii text markers if available in stream
            val quickText = if (bytes != null) {
                val textCandidate = String(bytes, Charsets.ISO_8859_1)
                extractTextFromRawPdf(textCandidate)
            } else null

            return Triple(quickText, pageCount, base64)
        } catch (e: Exception) {
            e.printStackTrace()
            return Triple(null, 1, null)
        }
    }

    private fun extractTextFromRawPdf(raw: String): String? {
        val sb = StringBuilder()
        val streamRegex = Regex("""stream[\r\n]+([\s\S]*?)[\r\n]+endstream""")
        val matches = streamRegex.findAll(raw)
        for (match in matches) {
            val content = match.groupValues[1]
            // Extract Tj / TJ strings
            val textMatches = Regex("""\((.*?)\)\s*Tj""").findAll(content)
            for (tm in textMatches) {
                val line = tm.groupValues[1].replace("\\n", "\n").replace("\\(", "(").replace("\\)", ")")
                if (line.isNotBlank()) sb.append(line).append(" ")
            }
        }
        val result = sb.toString().trim()
        return if (result.length > 30) result else null
    }

    private fun extractTextFromDocx(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            val sb = StringBuilder()

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zipInputStream.bufferedReader().readText()
                    // Extract text from <w:t>...</w:t> tags
                    val textPattern = Regex("""<w:t[^>]*>(.*?)</w:t>""")
                    val matches = textPattern.findAll(xmlContent)
                    for (m in matches) {
                        sb.append(m.groupValues[1]).append(" ")
                    }
                    break
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()
            sb.toString().trim().ifBlank { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readPlainText(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "archivo"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    size = it.getLong(index)
                }
            }
        }
        return size
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
