package com.example.util

import java.util.Locale

object DocumentCleaner {

    /**
     * Limpia completamente una carta o documento formal dejando ÚNICAMENTE
     * el contenido oficial, limpio y listo para usar.
     *
     * Elimina:
     * - Frases introductorias ("Para redactar la carta adecuada...", "Solo debes completar...", etc.)
     * - Peticiones del usuario usadas como título (ej. "Créame una carta para el IMAS")
     * - Toda mención de "ZACK AI"
     * - Ejemplos dentro de corchetes ([ej: San José] -> [Lugar], [Fecha, ej: ...] -> [Fecha])
     * - Secciones finales de recomendaciones, notas o instrucciones
     */
    fun cleanLetterDocument(rawContent: String, userPrompt: String? = null): String {
        if (rawContent.isBlank()) return rawContent

        var text = rawContent.replace("\r\n", "\n").replace("\r", "\n")

        // 1. Eliminar menciones de "ZACK AI"
        text = text.replace("ZACK AI", "")
            .replace("Generado con  •", "")
            .replace("Generado por", "")
            .replace("Generado con", "")

        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        // Patrones que marcan el inicio de la carta formal
        val formalStartPatterns = listOf(
            Regex("^(?:\\[?\\s*(?:Lugar|Fecha|Ciudad|Provincia)\\b|[A-ZÁÉÍÓÚ][a-záéíóú]+,\\s*\\d{1,2}|\\d{1,2}\\s+de\\s+[a-záéíóú]+|San José|Alajuela|Heredia|Cartago|Puntarenas|Guanacaste|Limón|Bogotá|México|Santiago|Buenos Aires|Lima)", RegexOption.IGNORE_CASE),
            Regex("^(?:Señor|Señora|Señores|Estimado|Estimada|Estimados|A quien corresponda|A quien interese|Al Despacho|Dirigido a|Instituto|Ministerio|Juzgado|Banco|Dirección|Gerencia|Junta Directiva|Licenciado|Doctor|Dr\\.|Lic\\.|Ing\\.)", RegexOption.IGNORE_CASE),
            Regex("^(?:Asunto|Referencia|Ref)\\s*:", RegexOption.IGNORE_CASE),
            Regex("^(?:Por medio de la presente|El suscrito|La suscrita|Yo,\\s*\\[|Yo,\\s*[A-ZÁÉÍÓÚ])", RegexOption.IGNORE_CASE),
            Regex("^(?:CARTA DE|SOLICITUD DE|CONSTANCIA|DECLARACIÓN JURADA|PODER ESPECIAL|MEMORIAL|AUTORIZACIÓN)", RegexOption.IGNORE_CASE)
        )

        // Patrones de líneas de introducción que DEBEN eliminarse
        val preamblePatterns = listOf(
            Regex("^(?:Para redactar|Para elaborar|Para realizar|Para solicitar|Para presentar)", RegexOption.IGNORE_CASE),
            Regex("^(?:Solo debes|Debes completar|Asegúrate de|Recuerda completar|Llena los espacios)", RegexOption.IGNORE_CASE),
            Regex("^(?:A continuación|Aquí tienes|Te presento|Adjunto|Te comparto)", RegexOption.IGNORE_CASE),
            Regex("^(?:Con gusto|Por supuesto|Claro que sí|Hola|Espero te sirva)", RegexOption.IGNORE_CASE),
            Regex("^(?:Esta carta|Este modelo|Este formato|Esta plantilla)", RegexOption.IGNORE_CASE),
            Regex("^(?:#+\\s*)?(?:Créame|Crea|Redacta|Hazme|Elabora|Hacer|Solicitud de carta|Carta para)", RegexOption.IGNORE_CASE)
        )

        // Patrones de títulos basados en la petición del usuario
        val promptClean = userPrompt?.trim()?.lowercase(Locale.getDefault()) ?: ""

        // Secciones finales a cortar completamente (Recomendaciones, notas, etc.)
        val endSectionPatterns = listOf(
            Regex("^(?:#+\\s*|\\*+|[-_]{2,}\\s*)?(?:Recomendaciones|Recomendación|Notas|Nota|Aclaraciones|Aclaración|Consejos|Pasos\\s+a\\s+seguir|Pasos\\s+siguientes|Instrucciones|Documentos\\s+a\\s+adjuntar|Requisitos\\s+adicionales)(?:\\s*:)?\\s*(?:\\*+)?$", RegexOption.IGNORE_CASE),
            Regex("^(?:#+\\s*|\\*+)?(?:Recuerda que|Importante\\s*:|Ten en cuenta que)\\s*(?:\\*+)?$", RegexOption.IGNORE_CASE)
        )

        var letterStarted = false
        var reachedEndSection = false

        for (line in lines) {
            val trimmed = line.trim()

            // Si llegamos a una sección de recomendaciones o notas, ignorar todo lo que sigue
            if (letterStarted && endSectionPatterns.any { it.containsMatchIn(trimmed) }) {
                reachedEndSection = true
                break
            }

            if (reachedEndSection) break

            // Si la carta no ha comenzado, evaluar si esta línea es el inicio
            if (!letterStarted) {
                // Descartar líneas vacías iniciales
                if (trimmed.isEmpty()) continue

                // Descartar si coincide con el prompt del usuario (ej: "Créame una carta para el IMAS")
                if (promptClean.isNotBlank() && trimmed.lowercase(Locale.getDefault()).contains(promptClean)) {
                    continue
                }

                // Descartar si es un preámbulo o instrucción
                if (preamblePatterns.any { it.containsMatchIn(trimmed) }) {
                    continue
                }

                // Comprobar si coincide con el inicio formal
                if (formalStartPatterns.any { it.containsMatchIn(trimmed) }) {
                    letterStarted = true
                    cleanedLines.add(line)
                    continue
                }

                // Si no coincide con preámbulo ni inicio pero parece texto formal, empezar
                if (!trimmed.startsWith("#") && !trimmed.contains("debes completar", ignoreCase = true)) {
                    letterStarted = true
                    cleanedLines.add(line)
                }
            } else {
                // Ya estamos dentro de la carta: verificar que no sea una instrucción intercalada
                if (trimmed.startsWith("Recomendaciones", ignoreCase = true) ||
                    trimmed.startsWith("Nota:", ignoreCase = true) ||
                    trimmed.startsWith("Notas:", ignoreCase = true)
                ) {
                    reachedEndSection = true
                    break
                }

                cleanedLines.add(line)
            }
        }

        val resultText = if (cleanedLines.isNotEmpty()) {
            cleanedLines.joinToString("\n")
        } else {
            // En caso de que el filtro haya sido demasiado estricto, usar el texto original sin preámbulos
            lines.filter { l ->
                val t = l.trim()
                !preamblePatterns.any { it.containsMatchIn(t) } &&
                !endSectionPatterns.any { it.containsMatchIn(t) }
            }.joinToString("\n")
        }

        // 2. Limpiar ejemplos entre corchetes: [Lugar, ej: San José] -> [Lugar]
        val cleanedBrackets = cleanBrackets(resultText)

        // 3. Limpieza final de títulos no deseados o marcas residuales
        return cleanedBrackets
            .replace(Regex("(?i)^#+\\s*Créame\\s+una\\s+carta[^\n]*\n?"), "")
            .replace(Regex("(?i)^#+\\s*Carta\\s+para\\s+el\\s+IMAS[^\n]*\n?"), "")
            .replace(Regex("(?i)\n#+\\s*Créame\\s+una\\s+carta[^\n]*"), "")
            .trim()
    }

    /**
     * Limpia los corchetes para que queden solo los nombres de campos formales:
     * - [Lugar, ej: San José] -> [Lugar]
     * - [Fecha, ej: 15 de marzo...] -> [Fecha]
     * - [Tu Nombre Completo, ej: Juan Pérez] -> [Tu Nombre Completo]
     * - [ej: San José] -> [Lugar]
     * - [ej: Alquiler o alimentación] -> [Motivo]
     * - [ej: 100.000 colones] -> [Monto]
     */
    fun cleanBrackets(text: String): String {
        return Regex("\\[([^\\]]+)\\]").replace(text) { match ->
            val inner = match.groupValues[1].trim()
            val cleaned = when {
                // Caso: [Campo, ej: Ejemplo] o [Campo, ejemplo: ...] o [Campo (ej: ...)]
                inner.contains(Regex(",\\s*(?:ej|ejemplo|ejem)\\.?:", RegexOption.IGNORE_CASE)) -> {
                    inner.replace(Regex(",\\s*(?:ej|ejemplo|ejem)\\.?:.*$", RegexOption.IGNORE_CASE), "").trim()
                }
                inner.contains(Regex("\\s*\\((?:ej|ejemplo|ejem)\\.?:.*\\)$", RegexOption.IGNORE_CASE)) -> {
                    inner.replace(Regex("\\s*\\((?:ej|ejemplo|ejem)\\.?:.*\\)$", RegexOption.IGNORE_CASE), "").trim()
                }
                inner.contains(Regex(",\\s*(?:por ejemplo|ejemplos)\\b:?", RegexOption.IGNORE_CASE)) -> {
                    inner.replace(Regex(",\\s*(?:por ejemplo|ejemplos)\\b:?.*$", RegexOption.IGNORE_CASE), "").trim()
                }
                // Caso: [ej: San José] o [ejemplo: Alquiler]
                inner.startsWith("ej:", ignoreCase = true) ||
                inner.startsWith("ej.:", ignoreCase = true) ||
                inner.startsWith("ejemplo:", ignoreCase = true) -> {
                    val value = inner.substringAfter(":").trim()
                    when {
                        value.contains("San José", ignoreCase = true) ||
                        value.contains("ciudad", ignoreCase = true) ||
                        value.contains("provincia", ignoreCase = true) ||
                        value.contains("lugar", ignoreCase = true) -> "Lugar"

                        value.contains("alquiler", ignoreCase = true) ||
                        value.contains("aliment", ignoreCase = true) ||
                        value.contains("gasto", ignoreCase = true) ||
                        value.contains("médico", ignoreCase = true) ||
                        value.contains("motivo", ignoreCase = true) ||
                        value.contains("situación", ignoreCase = true) -> "Motivo"

                        value.contains("colones", ignoreCase = true) ||
                        value.contains("dólar", ignoreCase = true) ||
                        value.contains("monto", ignoreCase = true) ||
                        value.contains("dinero", ignoreCase = true) -> "Monto"

                        value.contains("cédula", ignoreCase = true) ||
                        value.contains("identidad", ignoreCase = true) ||
                        value.contains("dni", ignoreCase = true) -> "Cédula"

                        value.contains("fecha", ignoreCase = true) ||
                        value.contains("día", ignoreCase = true) ||
                        value.contains("mes", ignoreCase = true) ||
                        value.contains("año", ignoreCase = true) ||
                        Regex("(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|setiembre|septiembre|octubre|noviembre|diciembre)", RegexOption.IGNORE_CASE).containsMatchIn(value) -> "Fecha"

                        value.contains("nombre", ignoreCase = true) ||
                        value.contains("pérez", ignoreCase = true) ||
                        value.contains("juan", ignoreCase = true) -> "Tu Nombre Completo"

                        value.contains("dirección", ignoreCase = true) ||
                        value.contains("casa", ignoreCase = true) ||
                        value.contains("barrio", ignoreCase = true) -> "Dirección"

                        else -> "Campo a Llenar"
                    }
                }
                else -> inner
            }
            "[$cleaned]"
        }
    }
}
