package com.example.util

data class ParsedMessageContent(
    val imageUrls: List<String>,
    val cleanText: String
)

object ImageParser {
    // Regex for markdown images: ![alt](url) - allows spaces in URLs until closing parenthesis
    private val markdownImageRegex = Regex("""!\[(.*?)\]\((https?://[^\)]+)\)""", RegexOption.IGNORE_CASE)

    // Regex for HTML img tags: <img ... src="url" ...>
    private val htmlImgRegex = Regex("""<img[^>]+src=["'](https?://[^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    // Regex for markdown links pointing to images or pollinations: [text](https://...image...)
    private val markdownLinkImageRegex = Regex("""\[(.*?)\]\((https?://[^\)]*(?:image\.pollinations\.ai|\.(?:png|jpg|jpeg|webp|gif))[^\)]*)\)""", RegexOption.IGNORE_CASE)

    // Regex for standalone raw URLs ending in image extensions or pollinations
    private val rawImageUrlRegex = Regex("""https?://[^\s\)\]<>"']+(?:image\.pollinations\.ai[^\s\)\]<>"']*|\.(?:png|jpg|jpeg|webp|gif)(?:\?[^\s\)\]<>"']*)?)""", RegexOption.IGNORE_CASE)

    fun parse(content: String, originalPrompt: String? = null): ParsedMessageContent {
        val urls = LinkedHashSet<String>()

        fun addSanitized(rawUrl: String) {
            val trimmed = rawUrl.trim().trimEnd('.', ',', ';')
            if (isValidImageUrl(trimmed)) {
                val finalUrl = if (trimmed.contains("image.pollinations.ai", ignoreCase = true)) {
                    ImagePromptBuilder.sanitizePollinationsUrl(trimmed, originalPrompt)
                } else {
                    trimmed
                }
                urls.add(finalUrl)
            }
        }

        // 1. Match markdown images: ![alt](url)
        markdownImageRegex.findAll(content).forEach { match ->
            val url = match.groupValues[2].trim()
            addSanitized(url)
        }

        // 2. Match markdown links pointing to images: [alt](url)
        markdownLinkImageRegex.findAll(content).forEach { match ->
            val url = match.groupValues[2].trim()
            addSanitized(url)
        }

        // 3. Match HTML img tags: <img src="url">
        htmlImgRegex.findAll(content).forEach { match ->
            val url = match.groupValues[1].trim()
            addSanitized(url)
        }

        // 4. Match raw image URLs
        rawImageUrlRegex.findAll(content).forEach { match ->
            val url = match.value.trim().trimEnd('.', ',', ';')
            addSanitized(url)
        }

        // Clean the text to REMOVE all image codes, links and raw URLs from the visible text:
        var text = content
        text = markdownImageRegex.replace(text, "")
        text = markdownLinkImageRegex.replace(text, "")
        text = htmlImgRegex.replace(text, "")
        text = rawImageUrlRegex.replace(text, "")

        // Clean residual text leftovers like "Aquí tienes la imagen:", "Link:", "Enlace:", etc.
        text = text.replace(Regex("""(?im)^(?:aquí|aqui)?\s*(?:tienes|está|esta)?\s*(?:la|el)?\s*(?:imagen|foto|enlace|link|dibujo)\s*:\s*$"""), "")
        text = text.replace(Regex("""\[\s*\]\(\s*\)"""), "")
        text = text.replace(Regex("""\n{3,}"""), "\n\n").trim()

        return ParsedMessageContent(
            imageUrls = urls.toList(),
            cleanText = text
        )
    }

    private fun isValidImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
