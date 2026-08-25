package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricCyan

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val blocks = parseMarkdownBlocks(text)
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language
                    )
                }
                is MarkdownBlock.Header -> {
                    Text(
                        text = block.text,
                        style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(6.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = formatInlineMarkdown(block.text, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class ListItem(val text: String) : MarkdownBlock()
    data class Code(val code: String, val language: String?) : MarkdownBlock()
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            // Code block start
            val lang = trimmed.removePrefix("```").trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeLines.add(lines[index])
                index++
            }
            // Skip the closing ```
            if (index < lines.size) index++
            result.add(MarkdownBlock.Code(codeLines.joinToString("\n"), lang))
        } else if (trimmed.startsWith("### ")) {
            result.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            index++
        } else if (trimmed.startsWith("## ")) {
            result.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            index++
        } else if (trimmed.startsWith("# ")) {
            result.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            index++
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("^\\d+\\.\\s.*"))) {
            val content = trimmed.replace(Regex("^[-*\\d.]+\\s+"), "")
            result.add(MarkdownBlock.ListItem(content))
            index++
        } else {
            result.add(MarkdownBlock.Paragraph(line))
            index++
        }
    }

    return result
}

fun formatInlineMarkdown(text: String, defaultColor: Color) = buildAnnotatedString {
    var cursor = 0
    val regex = Regex("(\\*\\*|`)(.*?)\\1")
    val matches = regex.findAll(text)

    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1
        val delimiter = match.groupValues[1]
        val content = match.groupValues[2]

        if (start > cursor) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(text.substring(cursor, start))
            }
        }

        if (delimiter == "**") {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                append(content)
            }
        } else if (delimiter == "`") {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = ElectricCyan,
                    background = Color(0xFF1E293B)
                )
            ) {
                append(" $content ")
            }
        }

        cursor = end
    }

    if (cursor < text.length) {
        withStyle(SpanStyle(color = defaultColor)) {
            append(text.substring(cursor))
        }
    }
}
