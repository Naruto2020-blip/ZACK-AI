package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String,
    val badge: String
) {
    PDF(
        extension = "pdf",
        mimeType = "application/pdf",
        displayName = "PDF",
        description = "Formato listo para imprimir y compartir",
        badge = "PDF"
    ),
    WORD(
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        displayName = "Word (.docx)",
        description = "Documento editable de Microsoft Word",
        badge = "DOCX"
    ),
    POWERPOINT(
        extension = "pptx",
        mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        displayName = "PowerPoint (.pptx)",
        description = "Presentación con diapositivas por idea",
        badge = "PPTX"
    ),
    EXCEL(
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        displayName = "Excel (.xlsx)",
        description = "Tablas y datos organizados en hojas",
        badge = "XLSX"
    )
}

data class ExportResult(
    val file: File,
    val uri: Uri,
    val fileName: String,
    val format: ExportFormat,
    val success: Boolean,
    val error: String? = null
)

object DocumentExporter {

    /**
     * Generates a context-aware filename based on the conversation/text topic.
     * Example: "Carta de renuncia - 26-08-2026.pdf" or "Resumen de ventas - 26-08-2026.docx"
     */
    fun generateDocumentName(content: String, format: ExportFormat, sessionTitle: String? = null): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        var rawTitle = ""

        // 1. Try explicit session title if not blank
        if (!sessionTitle.isNullOrBlank() && sessionTitle != "Conversación" && sessionTitle != "Nueva Conversación") {
            rawTitle = sessionTitle.trim()
        }

        // 2. If empty, extract the first prominent heading or line from content
        if (rawTitle.isBlank()) {
            val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines) {
                // Ignore code fences or prompt prefixes
                if (line.startsWith("```") || line.startsWith("[📷") || line.startsWith("[📂")) continue
                val cleanLine = line
                    .replace(Regex("^#+\\s*"), "") // remove markdown headers
                    .replace(Regex("^\\*+\\s*"), "") // remove bullets
                    .replace(Regex("^\\d+\\.\\s*"), "") // remove numbered lists
                    .replace(Regex("[*`_~#]"), "") // remove markdown formatting
                    .trim()

                if (cleanLine.isNotBlank()) {
                    rawTitle = cleanLine
                    break
                }
            }
        }

        if (rawTitle.isBlank()) {
            rawTitle = "Documento"
        }

        // Clean user prompt phrases and brand names
        rawTitle = rawTitle
            .replace(Regex("(?i)^Créame\\s+una\\s+carta\\s+para\\s+el\\s+"), "Carta ")
            .replace(Regex("(?i)^Créame\\s+una\\s+carta\\s+para\\s+"), "Carta ")
            .replace(Regex("(?i)^Créame\\s+una\\s+carta\\s+de\\s+"), "Carta ")
            .replace(Regex("(?i)^Carta\\s+para\\s+el\\s+IMAS.*"), "Carta IMAS")
            .replace(Regex("(?i)^Carta\\s+para\\s+"), "Carta ")
            .replace(Regex("(?i)ZACK AI"), "")
            .trim()

        if (rawTitle.isBlank()) {
            rawTitle = "Documento"
        }

        // Sanitize for file system
        var sanitized = rawTitle
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (sanitized.length > 35) {
            sanitized = sanitized.take(35).trim()
        }

        // Capitalize first letter
        if (sanitized.isNotEmpty()) {
            sanitized = sanitized.substring(0, 1).uppercase(Locale.getDefault()) + sanitized.substring(1)
        }

        return "$sanitized - $dateStr.${format.extension}"
    }

    /**
     * Main entry point to export a document and trigger download/share.
     */
    suspend fun exportAndShare(
        context: Context,
        content: String,
        format: ExportFormat,
        sessionTitle: String? = null,
        signatureBitmap: Bitmap? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val cleanContent = DocumentCleaner.cleanLetterDocument(content, sessionTitle)
            val fileName = generateDocumentName(cleanContent, format, sessionTitle)
            val docDir = File(context.cacheDir, "documents")
            if (!docDir.exists()) {
                docDir.mkdirs()
            }
            val outFile = File(docDir, fileName)

            when (format) {
                ExportFormat.PDF -> generatePdf(context, cleanContent, outFile, fileName, signatureBitmap)
                ExportFormat.WORD -> generateWordDocx(cleanContent, outFile, fileName, signatureBitmap)
                ExportFormat.POWERPOINT -> generatePowerPointPptx(cleanContent, outFile, fileName)
                ExportFormat.EXCEL -> generateExcelXlsx(cleanContent, outFile, fileName)
            }

            // Save copy to Android system Downloads folder
            saveToDownloads(context, outFile, fileName, format.mimeType)

            // Get FileProvider Uri
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, outFile)

            // Trigger system share / open intent on main thread
            withContext(Dispatchers.Main) {
                openOrShareFile(context, uri, fileName, format)
            }

            ExportResult(
                file = outFile,
                uri = uri,
                fileName = fileName,
                format = format,
                success = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al generar ${format.displayName}: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            val dummyFile = File(context.cacheDir, "error.${format.extension}")
            ExportResult(
                file = dummyFile,
                uri = Uri.EMPTY,
                fileName = "error.${format.extension}",
                format = format,
                success = false,
                error = e.message
            )
        }
    }

    // =========================================================================
    // 1. PDF GENERATION (Native Android PdfDocument)
    // =========================================================================

    private fun generatePdf(
        context: Context,
        content: String,
        outFile: File,
        docTitle: String,
        signatureBitmap: Bitmap? = null
    ) {
        val pdfDoc = PdfDocument()
        val pageWidth = 595 // Standard A4 width in points
        val pageHeight = 842 // Standard A4 height in points
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(15, 23, 42) // Dark Slate
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headingPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(51, 65, 85)
            textSize = 10.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val codePaint = Paint().apply {
            color = AndroidColor.rgb(15, 23, 42)
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        val headerAccentPaint = Paint().apply {
            color = AndroidColor.rgb(99, 102, 241) // Indigo accent
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = AndroidColor.rgb(148, 163, 184)
            textSize = 8.5f
            isAntiAlias = true
        }

        val tableBorderPaint = Paint().apply {
            color = AndroidColor.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val tableHeaderBgPaint = Paint().apply {
            color = AndroidColor.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin + 10f

        fun finishCurrentPage() {
            // Draw clean footer without any app brand (only page number if multi-page)
            if (pageNumber > 1) {
                val footerText = "Página $pageNumber"
                canvas.drawText(footerText, margin, pageHeight - 25f, footerPaint)
            }
            pdfDoc.finishPage(page)
        }

        fun startNewPage() {
            finishCurrentPage()
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDoc.startPage(pageInfo)
            canvas = page.canvas
            y = margin + 20f
        }

        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Check if page needs to break
            if (y > pageHeight - 50f) {
                startNewPage()
            }

            if (trimmed.isEmpty()) {
                y += 10f
                i++
                continue
            }

            // Headings
            if (trimmed.startsWith("#")) {
                val headerLevel = trimmed.takeWhile { it == '#' }.length
                val headerText = trimmed.removePrefix("#".repeat(headerLevel)).trim()
                    .replace(Regex("[*`_]"), "")
                y += 6f
                if (y > pageHeight - 50f) startNewPage()
                canvas.drawText(headerText, margin, y, if (headerLevel == 1) titlePaint else headingPaint)
                y += if (headerLevel == 1) 20f else 16f
                i++
                continue
            }

            // Tables (Markdown table detection)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val rowLine = lines[i].trim()
                    // Skip separator row like |---|---|
                    if (!rowLine.replace("|", "").trim().all { it == '-' || it == ':' || it == ' ' }) {
                        val cells = rowLine.split("|")
                            .filterIndexed { idx, _ -> idx > 0 && idx < rowLine.split("|").lastIndex }
                            .map { it.trim().replace(Regex("[*`_]"), "") }
                        if (cells.isNotEmpty()) {
                            tableRows.add(cells)
                        }
                    }
                    i++
                }

                if (tableRows.isNotEmpty()) {
                    val cols = tableRows.maxOf { it.size }
                    val colWidth = contentWidth / cols
                    val rowHeight = 20f

                    if (y + (tableRows.size * rowHeight) > pageHeight - 40f) {
                        startNewPage()
                    }

                    tableRows.forEachIndexed { rIdx, row ->
                        if (y + rowHeight > pageHeight - 40f) {
                            startNewPage()
                        }
                        val isHeader = (rIdx == 0)
                        if (isHeader) {
                            canvas.drawRect(margin, y - 14f, margin + contentWidth, y + (rowHeight - 14f), tableHeaderBgPaint)
                        }
                        row.forEachIndexed { cIdx, cell ->
                            val cellX = margin + (cIdx * colWidth)
                            canvas.drawRect(cellX, y - 14f, cellX + colWidth, y + (rowHeight - 14f), tableBorderPaint)
                            val cleanCell = if (cell.length > 22) cell.take(20) + ".." else cell
                            canvas.drawText(cleanCell, cellX + 6f, y, if (isHeader) boldBodyPaint else bodyPaint)
                        }
                        y += rowHeight
                    }
                    y += 8f
                }
                continue
            }

            // Bullet points or Numbered lists
            val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
            val isNumbered = Regex("^\\d+\\.\\s+").containsMatchIn(trimmed)

            if (isBullet || isNumbered) {
                val prefix = if (isBullet) "•  " else trimmed.takeWhile { it != ' ' } + " "
                val itemText = if (isBullet) trimmed.substring(2).trim() else trimmed.replace(Regex("^\\d+\\.\\s+"), "").trim()
                val cleanText = itemText.replace(Regex("[*`_]"), "")

                val wrapped = wrapText(cleanText, contentWidth - 18f, bodyPaint)
                wrapped.forEachIndexed { wIdx, textLine ->
                    if (y > pageHeight - 40f) startNewPage()
                    if (wIdx == 0) {
                        canvas.drawText(prefix, margin + 4f, y, boldBodyPaint)
                        canvas.drawText(textLine, margin + 20f, y, bodyPaint)
                    } else {
                        canvas.drawText(textLine, margin + 20f, y, bodyPaint)
                    }
                    y += 14f
                }
                i++
                continue
            }

            // Regular paragraph text
            val cleanParagraph = trimmed.replace(Regex("[*`_]"), "")
            val wrapped = wrapText(cleanParagraph, contentWidth, bodyPaint)
            wrapped.forEach { textLine ->
                if (y > pageHeight - 40f) startNewPage()
                canvas.drawText(textLine, margin, y, bodyPaint)
                y += 14f
            }
            y += 4f
            i++
        }

        // Draw signature if provided
        if (signatureBitmap != null) {
            val sigWidth = 140f
            val sigHeight = 60f
            if (y + sigHeight + 40f > pageHeight - 40f) {
                startNewPage()
            }
            y += 16f
            val sigX = margin + 20f
            val destRect = Rect(sigX.toInt(), y.toInt(), (sigX + sigWidth).toInt(), (y + sigHeight).toInt())
            canvas.drawBitmap(signatureBitmap, null, destRect, null)
            y += sigHeight + 4f

            val sigLinePaint = Paint().apply {
                color = AndroidColor.rgb(100, 116, 139)
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawLine(sigX, y, sigX + sigWidth + 40f, y, sigLinePaint)
            y += 12f
            canvas.drawText("FIRMA / CONFORMIDAD", sigX, y, boldBodyPaint)
            y += 10f
        }

        finishCurrentPage()

        FileOutputStream(outFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf(text) else lines
    }

    // =========================================================================
    // 2. WORD (.docx) GENERATION (Valid OpenXML Zip Package)
    // =========================================================================

    private fun generateWordDocx(
        content: String,
        outFile: File,
        docTitle: String,
        signatureBitmap: Bitmap? = null
    ) {
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            // [Content_Types].xml
            addZipEntry(
                zip,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
            )

            // _rels/.rels
            addZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )

            // word/_rels/document.xml.rels
            addZipEntry(
                zip,
                "word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            // word/styles.xml
            addZipEntry(
                zip,
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Segoe UI" w:hAnsi="Segoe UI" w:cs="Segoe UI"/>
                <w:sz w:val="22"/>
                <w:color w:val="334155"/>
            </w:rPr>
        </w:rPrDefault>
    </w:docDefaults>
</w:styles>"""
            )

            // Build word/document.xml with formatted headings, lists, tables and paragraphs
            val docXml = StringBuilder()
            docXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>
""")

            val lines = content.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.trim()

                if (trimmed.isEmpty()) {
                    docXml.append("<w:p/>")
                    i++
                    continue
                }

                // Headings
                if (trimmed.startsWith("#")) {
                    val level = trimmed.takeWhile { it == '#' }.length
                    val hText = trimmed.removePrefix("#".repeat(level)).trim().replace(Regex("[*`_]"), "")
                    val sz = if (level == 1) "32" else if (level == 2) "28" else "24"
                    val color = if (level == 1) "1E293B" else "475569"
                    docXml.append("""
<w:p>
    <w:pPr><w:spacing w:before="200" w:after="80"/></w:pPr>
    <w:r>
        <w:rPr><w:b/><w:sz w:val="$sz"/><w:color w:val="$color"/></w:rPr>
        <w:t>${escapeXml(hText)}</w:t>
    </w:r>
</w:p>
""")
                    i++
                    continue
                }

                // Tables
                if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                    val tableRows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        val rLine = lines[i].trim()
                        if (!rLine.replace("|", "").trim().all { it == '-' || it == ':' || it == ' ' }) {
                            val cells = rLine.split("|")
                                .filterIndexed { idx, _ -> idx > 0 && idx < rLine.split("|").lastIndex }
                                .map { it.trim().replace(Regex("[*`_]"), "") }
                            if (cells.isNotEmpty()) tableRows.add(cells)
                        }
                        i++
                    }

                    if (tableRows.isNotEmpty()) {
                        docXml.append("""
<w:tbl>
    <w:tblPr>
        <w:tblW w:w="5000" w:type="pct"/>
        <w:tblBorders>
            <w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
        </w:tblBorders>
    </w:tblPr>
""")
                        tableRows.forEachIndexed { rIdx, row ->
                            val isHeader = (rIdx == 0)
                            docXml.append("<w:tr>")
                            row.forEach { cell ->
                                docXml.append("<w:tc><w:tcPr>")
                                if (isHeader) {
                                    docXml.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F5F9\"/>")
                                }
                                docXml.append("<w:tcMar><w:top w:w=\"100\"/><w:bottom w:w=\"100\"/><w:left w:w=\"120\"/><w:right w:w=\"120\"/></w:tcMar>")
                                docXml.append("</w:tcPr><w:p><w:r>")
                                if (isHeader) docXml.append("<w:rPr><w:b/><w:color w:val=\"0F172A\"/></w:rPr>")
                                docXml.append("<w:t>${escapeXml(cell)}</w:t></w:r></w:p></w:tc>")
                            }
                            docXml.append("</w:tr>")
                        }
                        docXml.append("</w:tbl>")
                    }
                    continue
                }

                // Bullets and numbers
                val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                val isNumbered = Regex("^\\d+\\.\\s+").containsMatchIn(trimmed)

                if (isBullet || isNumbered) {
                    val bulletSym = if (isBullet) "• " else trimmed.takeWhile { it != ' ' } + " "
                    val bText = if (isBullet) trimmed.substring(2).trim() else trimmed.replace(Regex("^\\d+\\.\\s+"), "").trim()
                    val cleanB = bText.replace(Regex("[*`_]"), "")
                    docXml.append("""
<w:p>
    <w:pPr><w:ind w:left="360" w:hanging="240"/><w:spacing w:after="60"/></w:pPr>
    <w:r><w:rPr><w:b/><w:color w:val="6366F1"/></w:rPr><w:t>${escapeXml(bulletSym)}</w:t></w:r>
    <w:r><w:t>${escapeXml(cleanB)}</w:t></w:r>
</w:p>
""")
                    i++
                    continue
                }

                // Normal paragraph
                val cleanPara = trimmed.replace(Regex("[*`_]"), "")
                docXml.append("""
<w:p>
    <w:pPr><w:spacing w:after="100"/></w:pPr>
    <w:r><w:t>${escapeXml(cleanPara)}</w:t></w:r>
</w:p>
""")
                i++
            }

            docXml.append("""
</w:body>
</w:document>
""")
            addZipEntry(zip, "word/document.xml", docXml.toString())
        }
    }

    // =========================================================================
    // 3. POWERPOINT (.pptx) GENERATION (Slide per Key Idea)
    // =========================================================================

    private fun generatePowerPointPptx(content: String, outFile: File, docTitle: String) {
        val cleanTitle = docTitle.replace(Regex(" - \\d{2}-\\d{2}-\\d{4}\\.pptx$"), "")

        // Parse content into logical slide units (each idea / header / section = slide)
        data class SlideData(val title: String, val points: List<String>)

        val slides = mutableListOf<SlideData>()
        val lines = content.lines()

        var currentSlideTitle = ""
        var currentPoints = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            if (trimmed.startsWith("#")) {
                if (currentSlideTitle.isNotBlank() || currentPoints.isNotEmpty()) {
                    slides.add(SlideData(currentSlideTitle.ifBlank { "Idea Principal" }, currentPoints.toList()))
                    currentPoints.clear()
                }
                currentSlideTitle = trimmed.replace(Regex("^#+\\s*"), "").replace(Regex("[*`_]"), "").trim()
            } else {
                val cleanPoint = trimmed
                    .replace(Regex("^[-*•]\\s*"), "")
                    .replace(Regex("^\\d+\\.\\s*"), "")
                    .replace(Regex("[*`_]"), "")
                    .trim()
                if (cleanPoint.isNotBlank()) {
                    currentPoints.add(cleanPoint)
                }
            }
        }

        if (currentSlideTitle.isNotBlank() || currentPoints.isNotEmpty()) {
            slides.add(SlideData(currentSlideTitle.ifBlank { "Detalles" }, currentPoints.toList()))
        }

        // If no sections found, split points across slides (max 4 points per slide)
        if (slides.isEmpty() || (slides.size == 1 && slides[0].points.size > 4)) {
            val allPts = if (slides.isEmpty()) currentPoints else slides[0].points
            slides.clear()
            val chunks = allPts.chunked(4)
            chunks.forEachIndexed { idx, chunk ->
                slides.add(SlideData(if (idx == 0) cleanTitle else "Punto ${idx + 1}: Continuación", chunk))
            }
        }

        val totalSlides = 1 + slides.size // Cover + content slides

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            // [Content_Types].xml
            val contentTypes = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
    <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
    <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
""")
            for (s in 1..totalSlides) {
                contentTypes.append("<Override PartName=\"/ppt/slides/slide$s.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n")
            }
            contentTypes.append("</Types>")
            addZipEntry(zip, "[Content_Types].xml", contentTypes.toString())

            // _rels/.rels
            addZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            )

            // ppt/_rels/presentation.xml.rels
            val presRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
""")
            for (s in 1..totalSlides) {
                presRels.append("<Relationship Id=\"rId${s + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$s.xml\"/>\n")
            }
            presRels.append("</Relationships>")
            addZipEntry(zip, "ppt/_rels/presentation.xml.rels", presRels.toString())

            // ppt/presentation.xml
            val presXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>
    <p:sldIdLst>
""")
            for (s in 1..totalSlides) {
                presXml.append("<p:sldId id=\"${255 + s}\" r:id=\"rId${s + 1}\"/>\n")
            }
            presXml.append("""
    </p:sldIdLst>
    <p:sldSz cx="9144000" cy="5143500"/>
</p:presentation>
""")
            addZipEntry(zip, "ppt/presentation.xml", presXml.toString())

            // ppt/slideMasters/slideMaster1.xml
            addZipEntry(
                zip,
                "ppt/slideMasters/slideMaster1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
    <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""
            )

            // ppt/slideMasters/_rels/slideMaster1.xml.rels
            addZipEntry(
                zip,
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
            )

            // ppt/slideLayouts/slideLayout1.xml
            addZipEntry(
                zip,
                "ppt/slideLayouts/slideLayout1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
    <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
</p:sldLayout>"""
            )

            // Slide 1: Cover Slide
            addZipEntry(zip, "ppt/slides/_rels/slide1.xml.rels", slideRelXml())
            val coverXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
<p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="0F172A"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
        <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
        <p:grpSpPr/>
        <p:sp>
            <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr/></p:nvSpPr>
            <p:spPr><a:xfrm><a:off x="762000" y="1600000"/><a:ext cx="7620000" cy="1800000"/></a:xfrm></p:spPr>
            <p:txBody>
                <a:bodyPr anchor="ctr"/>
                <a:p>
                    <a:pPr algn="ctr"/>
                    <a:r><a:rPr sz="3600" b="1"><a:solidFill><a:srgbClr val="38BDF8"/></a:solidFill></a:rPr><a:t>${escapeXml(cleanTitle)}</a:t></a:r>
                </a:p>
            </p:txBody>
        </p:sp>
    </p:spTree>
</p:cSld>
</p:sld>"""
            addZipEntry(zip, "ppt/slides/slide1.xml", coverXml)

            // Subsequent Slides
            slides.forEachIndexed { sIdx, slide ->
                val slideNum = sIdx + 2
                addZipEntry(zip, "ppt/slides/_rels/slide$slideNum.xml.rels", slideRelXml())

                val slideXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
<p:cSld>
    <p:bg><p:bgPr><a:solidFill><a:srgbClr val="F8FAFC"/></a:solidFill></p:bgPr></p:bg>
    <p:spTree>
        <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
        <p:grpSpPr/>
        <!-- Header Bar -->
        <p:sp>
            <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
            <p:spPr><a:xfrm><a:off x="500000" y="400000"/><a:ext cx="8144000" cy="800000"/></a:xfrm></p:spPr>
            <p:txBody>
                <a:bodyPr anchor="t"/>
                <a:p><a:r><a:rPr sz="2600" b="1"><a:solidFill><a:srgbClr val="1E293B"/></a:solidFill></a:rPr><a:t>${escapeXml(slide.title)}</a:t></a:r></a:p>
            </p:txBody>
        </p:sp>
        <!-- Body Points -->
        <p:sp>
            <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
            <p:spPr><a:xfrm><a:off x="600000" y="1400000"/><a:ext cx="7944000" cy="3200000"/></a:xfrm></p:spPr>
            <p:txBody>
                <a:bodyPr anchor="t"/>
""")
                slide.points.take(5).forEach { point ->
                    slideXml.append("""
                <a:p>
                    <a:pPr lvl="0" marL="300000"><a:buChar char="•"/></a:pPr>
                    <a:r><a:rPr sz="1600"><a:solidFill><a:srgbClr val="334155"/></a:solidFill></a:rPr><a:t>${escapeXml(point)}</a:t></a:r>
                </a:p>
""")
                }
                slideXml.append("""
            </p:txBody>
        </p:sp>
    </p:spTree>
</p:cSld>
</p:sld>""")
                addZipEntry(zip, "ppt/slides/slide$slideNum.xml", slideXml.toString())
            }
        }
    }

    private fun slideRelXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""

    // =========================================================================
    // 4. EXCEL (.xlsx) GENERATION (Structured Sheets & Tables)
    // =========================================================================

    private fun generateExcelXlsx(content: String, outFile: File, docTitle: String) {
        val cleanTitle = docTitle.replace(Regex(" - \\d{2}-\\d{2}-\\d{4}\\.xlsx$"), "")

        // Parse markdown tables or extract key data rows
        val tableRows = mutableListOf<List<String>>()
        val lines = content.lines()
        var hasMarkdownTables = false

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("|") && line.endsWith("|")) {
                if (!line.replace("|", "").trim().all { it == '-' || it == ':' || it == ' ' }) {
                    val cells = line.split("|")
                        .filterIndexed { idx, _ -> idx > 0 && idx < line.split("|").lastIndex }
                        .map { it.trim().replace(Regex("[*`_]"), "") }
                    if (cells.isNotEmpty()) {
                        tableRows.add(cells)
                        hasMarkdownTables = true
                    }
                }
            }
            i++
        }

        // If no markdown tables exist, parse structured bullet points or paragraphs
        if (!hasMarkdownTables) {
            tableRows.add(listOf("Nº", "Elemento / Concepto", "Detalle / Descripción"))
            var count = 1
            for (l in lines) {
                val tr = l.trim()
                if (tr.isBlank() || tr.startsWith("#")) continue
                val clean = tr.replace(Regex("^[-*•]\\s*"), "").replace(Regex("^\\d+\\.\\s*"), "").replace(Regex("[*`_]"), "").trim()
                if (clean.isNotBlank()) {
                    if (clean.contains(":") || clean.contains(" - ")) {
                        val splitChar = if (clean.contains(":")) ":" else " - "
                        val parts = clean.split(splitChar, limit = 2)
                        tableRows.add(listOf(count.toString(), parts[0].trim(), parts.getOrNull(1)?.trim() ?: ""))
                    } else {
                        tableRows.add(listOf(count.toString(), "Item $count", clean))
                    }
                    count++
                }
            }
        }

        if (tableRows.isEmpty()) {
            tableRows.add(listOf("Registro", "Contenido"))
            tableRows.add(listOf("1", content.take(100)))
        }

        val sharedStrings = mutableListOf<String>()
        fun getStringId(str: String): Int {
            val idx = sharedStrings.indexOf(str)
            return if (idx >= 0) idx else {
                sharedStrings.add(str)
                sharedStrings.size - 1
            }
        }

        fun colLetter(colIdx: Int): String {
            var temp = colIdx
            val sb = StringBuilder()
            while (temp >= 0) {
                sb.insert(0, ('A'.code + (temp % 26)).toChar())
                temp = (temp / 26) - 1
            }
            return sb.toString()
        }

        // Build worksheet XML
        val sheetXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <cols>
        <col min="1" max="1" width="8" customWidth="1"/>
        <col min="2" max="2" width="28" customWidth="1"/>
        <col min="3" max="10" width="35" customWidth="1"/>
    </cols>
    <sheetData>
""")

        // Row 1: Document Title Header
        sheetXml.append("<row r=\"1\"><c r=\"A1\" t=\"s\" s=\"1\"><v>${getStringId(cleanTitle)}</v></c></row>\n")

        // Rows
        tableRows.forEachIndexed { rIdx, row ->
            val excelRowNum = rIdx + 3
            val isHeader = (rIdx == 0)
            val styleIdx = if (isHeader) "1" else "2"
            sheetXml.append("<row r=\"$excelRowNum\">")
            row.forEachIndexed { cIdx, cellValue ->
                val ref = "${colLetter(cIdx)}$excelRowNum"
                val sId = getStringId(cellValue)
                sheetXml.append("<c r=\"$ref\" t=\"s\" s=\"$styleIdx\"><v>$sId</v></c>")
            }
            sheetXml.append("</row>\n")
        }
        sheetXml.append("</sheetData></worksheet>")

        // Shared strings XML
        val sharedXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">
""")
        for (s in sharedStrings) {
            sharedXml.append("<si><t>${escapeXml(s)}</t></si>\n")
        }
        sharedXml.append("</sst>")

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            // [Content_Types].xml
            addZipEntry(
                zip,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
    <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
            )

            // _rels/.rels
            addZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            // xl/_rels/workbook.xml.rels
            addZipEntry(
                zip,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            // xl/workbook.xml
            addZipEntry(
                zip,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets><sheet name="Datos" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
            )

            // xl/styles.xml
            addZipEntry(
                zip,
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="3">
        <font><sz val="11"/><name val="Segoe UI"/><color rgb="FF334155"/></font>
        <font><b/><sz val="12"/><name val="Segoe UI"/><color rgb="FF0F172A"/></font>
        <font><sz val="10.5"/><name val="Segoe UI"/><color rgb="FF334155"/></font>
    </fonts>
    <fills count="3">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
    </fills>
    <borders count="2">
        <border><left/><right/><top/><bottom/></border>
        <border>
            <left style="thin"><color rgb="FFCBD5E1"/></left>
            <right style="thin"><color rgb="FFCBD5E1"/></right>
            <top style="thin"><color rgb="FFCBD5E1"/></top>
            <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
        </border>
    </borders>
    <cellXfs count="3">
        <xf fontId="0" fillId="0" borderId="0"/>
        <xf fontId="1" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
        <xf fontId="2" fillId="0" borderId="1" applyFont="1" applyBorder="1"/>
    </cellXfs>
</styleSheet>"""
            )

            // xl/sharedStrings.xml
            addZipEntry(zip, "xl/sharedStrings.xml", sharedXml.toString())

            // xl/worksheets/sheet1.xml
            addZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml.toString())
        }
    }

    // =========================================================================
    // HELPER FUNCTIONS & MEDIA STORE INTEGRATION
    // =========================================================================

    private fun addZipEntry(zip: ZipOutputStream, path: String, xmlContent: String) {
        val entry = ZipEntry(path)
        zip.putNextEntry(entry)
        val bytes = xmlContent.toByteArray(StandardCharsets.UTF_8)
        zip.write(bytes, 0, bytes.size)
        zip.closeEntry()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun saveToDownloads(context: Context, srcFile: File, fileName: String, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val destFile = File(downloadsDir, fileName)
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }
        } catch (e: Exception) {
            // Ignored if permission restricted, FileProvider still works seamlessly
            e.printStackTrace()
        }
    }

    private fun openOrShareFile(context: Context, uri: Uri, fileName: String, format: ExportFormat) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, "Documento exportado: $fileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Descargar / Compartir $fileName")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Toast.makeText(context, "📥 Guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Documento guardado: $fileName", Toast.LENGTH_SHORT).show()
        }
    }
}
