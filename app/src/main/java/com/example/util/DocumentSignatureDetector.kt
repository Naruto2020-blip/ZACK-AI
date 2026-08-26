package com.example.util

import java.util.Locale

object DocumentSignatureDetector {

    // Strong keywords indicating document type requiring signature
    private val SIGNABLE_DOCUMENT_KEYWORDS = listOf(
        "carta de renuncia",
        "carta formal",
        "carta poder",
        "contrato de",
        "contrato individual",
        "contrato laboral",
        "contrato mercantil",
        "contrato privado",
        "acuerdo de confidencialidad",
        "acuerdo de voluntades",
        "solicitud formal",
        "solicitud de empleo",
        "solicitud de permiso",
        "solicitud de vacaciones",
        "autorización formal",
        "declaración jurada",
        "declaración de impuestos",
        "recibo de dinero",
        "recibo de pago",
        "recibo de entrega",
        "pagaré",
        "acta de entrega",
        "acta de compromiso",
        "memorial de solicitud",
        "constancia de trabajo",
        "convenio de",
        "mandato",
        "carta de recomendación",
        "carta de consentimiento",
        "consentimiento informado",
        "poder notarial",
        "finiquito",
        "pacto de no competencia",
        "acuerdo mutuo"
    )

    // Keywords or patterns specifically asking for signature at the end or within document
    private val EXPLICIT_SIGNATURE_MARKERS = listOf(
        "firma del solicitante",
        "firma del trabajador",
        "firma del empleado",
        "firma del arrendador",
        "firma del arrendatario",
        "firma del comprador",
        "firma del vendedor",
        "firma del testigo",
        "firma de conformidad",
        "firma y aclaración",
        "firma y sello",
        "firma:",
        "atentamente,",
        "cordialmente,",
        "firmado por:",
        "conforme:",
        "acepto conforme",
        "doy fe",
        "por la presente acepto",
        "suscriben el presente",
        "en prueba de conformidad",
        "firmamos en duplicado",
        "en fe de lo cual"
    )

    // Negative triggers: Informational, tasks, calculations, summaries, tutorials, code
    private val NON_SIGNABLE_INDICATORS = listOf(
        "cálculo:",
        "solución paso a paso",
        "código kotlin",
        "código python",
        "código javascript",
        "explicación:",
        "resumen del tema",
        "ejercicio 1",
        "ejercicio 2",
        "pregunta 1",
        "pregunta 2",
        "investigación sobre",
        "historia de",
        "definición de",
        "ingredientes:",
        "receta de",
        "tabla comparativa",
        "ventajas y desventajas"
    )

    /**
     * Determines whether the given text represents a signable formal document
     * (contracts, resignations, formal letters, requests, agreements, affidavits, etc.)
     * and should NOT show for general chat, Q&A, math, explanations, code, or searches.
     */
    fun isSignableDocument(text: String): Boolean {
        if (text.isBlank() || text.length < 50) return false

        val lower = text.lowercase(Locale.ROOT)

        // 1. If explicit non-signable indicators are predominant, return false
        val hasNegativeIndicators = NON_SIGNABLE_INDICATORS.any { lower.contains(it) }
        if (hasNegativeIndicators && !lower.contains("contrato") && !lower.contains("carta de renuncia")) {
            return false
        }

        // 2. Check for signature lines (e.g. "_______" or "-------------------" or "................")
        val hasUnderlineSignature = Regex("[_─—–.]{5,}").containsMatchIn(text)

        // 3. Check for signable document titles / body markers
        val hasSignableDocKeyword = SIGNABLE_DOCUMENT_KEYWORDS.any { lower.contains(it) }

        // 4. Check for explicit signature phrases
        val hasSignaturePhrase = EXPLICIT_SIGNATURE_MARKERS.any { lower.contains(it) }

        // 5. Letter closing patterns (e.g. "Atentamente,\n[Nombre]" or "Cordialmente,\n[Nombre]")
        val hasFormalLetterClosing = Regex("(?i)(atentamente|cordialmente|quedo a sus órdenes|sin otro particular|saludos cordiales)[,\\s\\n]+").containsMatchIn(text)

        // Decision logic:
        // Case A: Is an explicit signable doc type (contract, resignation, letter, affidavit, receipt, agreement)
        if (hasSignableDocKeyword) {
            return true
        }

        // Case B: Contains explicit signature markers (e.g. "Firma del solicitante", "En prueba de conformidad")
        if (hasSignaturePhrase) {
            return true
        }

        // Case C: Has an underline / blank line together with a formal closing or acknowledgment
        if (hasUnderlineSignature && (hasFormalLetterClosing || lower.contains("firma") || lower.contains("dni") || lower.contains("cédula") || lower.contains("rut"))) {
            return true
        }

        return false
    }
}
