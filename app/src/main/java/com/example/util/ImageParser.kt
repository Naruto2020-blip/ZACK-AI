package com.example.util

data class ParsedMessageContent(
    val imageUrls: List<String>,
    val cleanText: String
)

object ImageParser {
    // Regex for markdown images: ![alt](url)
    private val markdownImageRegex = Regex("""!\[(.*?)\]\((https?://[^\s\)]+)\)""", RegexOption.IGNORE_CASE)

    // Regex for HTML img tags: <img ... src="url" ...>
    private val htmlImgRegex = Regex("""<img[^>]+src=["'](https?://[^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    // Regex for markdown links pointing to images or pollinations: [text](https://...image...)
    private val markdownLinkImageRegex = Regex("""\[(.*?)\]\((https?://[^\s\)]+(?:image\.pollinations\.ai|\.(?:png|jpg|jpeg|webp|gif))[^\s\)]*)\)""", RegexOption.IGNORE_CASE)

    // Regex for standalone raw URLs ending in image extensions or pollinations
    private val rawImageUrlRegex = Regex("""https?://[^\s\)\]<>"']+(?:image\.pollinations\.ai[^\s\)\]<>"']*|\.(?:png|jpg|jpeg|webp|gif)(?:\?[^\s\)\]<>"']*)?)""", RegexOption.IGNORE_CASE)

    fun parse(content: String): ParsedMessageContent {
        val urls = LinkedHashSet<String>()

        // 1. Match markdown images: ![alt](url)
        markdownImageRegex.findAll(content).forEach { match ->
            val url = match.groupValues[2].trim()
            if (isValidImageUrl(url)) urls.add(url)
        }

        // 2. Match markdown links pointing to images: [alt](url)
        markdownLinkImageRegex.findAll(content).forEach { match ->
            val url = match.groupValues[2].trim()
            if (isValidImageUrl(url)) urls.add(url)
        }

        // 3. Match HTML img tags: <img src="url">
        htmlImgRegex.findAll(content).forEach { match ->
            val url = match.groupValues[1].trim()
            if (isValidImageUrl(url)) urls.add(url)
        }

        // 4. Match raw image URLs
        rawImageUrlRegex.findAll(content).forEach { match ->
            val url = match.value.trim().trimEnd('.', ',', ';')
            if (isValidImageUrl(url)) urls.add(url)
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
