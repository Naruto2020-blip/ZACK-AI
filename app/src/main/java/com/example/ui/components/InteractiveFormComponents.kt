package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSubtle
import com.example.ui.theme.RadiantViolet
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.theme.isAppDark

// ==========================================
// 1. DATA MODELS & PARSERS
// ==========================================

data class ExamData(
    val title: String,
    val subject: String,
    val questions: List<String>,
    val introNote: String = ""
)

fun parseInteractiveExam(content: String): ExamData? {
    if (content.isBlank()) return null
    // If it's already a graded feedback message with corrections, don't show the exam input again
    if (content.contains("Calificación final", ignoreCase = true) ||
        (content.contains("✅ Correcta", ignoreCase = true) && content.contains("❌ Incorrecta", ignoreCase = true))
    ) {
        return null
    }

    // Pattern 1: Explicit tag [EXAMEN_INTERACTIVO] ... [/EXAMEN_INTERACTIVO]
    if (content.contains("[EXAMEN_INTERACTIVO]", ignoreCase = true)) {
        val block = content.substringAfter("[EXAMEN_INTERACTIVO]")
            .substringBefore("[/EXAMEN_INTERACTIVO]")
            .trim()

        var title = "Examen de Conocimientos"
        var subject = "General"
        val questions = mutableListOf<String>()

        val lines = block.lines()
        var parsingQuestions = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (line.startsWith("TITULO:", ignoreCase = true) || line.startsWith("TÍTULO:", ignoreCase = true)) {
                title = line.substringAfter(":").trim()
            } else if (line.startsWith("MATERIA:", ignoreCase = true) || line.startsWith("TEMA:", ignoreCase = true)) {
                subject = line.substringAfter(":").trim()
            } else if (line.startsWith("PREGUNTAS", ignoreCase = true)) {
                parsingQuestions = true
            } else if (line.matches(Regex("^\\d+[\\.\\)]\\s+.*"))) {
                val qText = line.replaceFirst(Regex("^\\d+[\\.\\)]\\s+"), "").trim()
                if (qText.isNotBlank()) questions.add(qText)
                parsingQuestions = true
            } else if (parsingQuestions && questions.isNotEmpty() && line.startsWith("-")) {
                // If it's an option or subtext, append to last question
                val last = questions.removeAt(questions.size - 1)
                questions.add("$last\n$line")
            }
        }

        if (questions.isNotEmpty()) {
            return ExamData(title = title, subject = subject, questions = questions)
        }
    }

    // Pattern 2: Natural exam format without tags (e.g. "Examen de...", "Cuestionario de...")
    val isExamTitle = content.contains("Examen de", ignoreCase = true) ||
            content.contains("Cuestionario de", ignoreCase = true) ||
            content.contains("Prueba de", ignoreCase = true) ||
            (content.contains("Examen", ignoreCase = true) && content.contains("Pregunta 1", ignoreCase = true))

    if (isExamTitle) {
        val questions = mutableListOf<String>()
        var title = "Examen de Práctica"
        var subject = "General"

        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if ((line.startsWith("#") || line.contains("Examen", ignoreCase = true)) && line.length in 10..80 && questions.isEmpty()) {
                title = line.replace("#", "").trim()
            } else if (line.startsWith("Materia:", ignoreCase = true) || line.startsWith("Tema:", ignoreCase = true)) {
                subject = line.substringAfter(":").trim()
            } else if (line.matches(Regex("^\\d+[\\.\\)]\\s+.*")) || line.matches(Regex("^Pregunta\\s+\\d+[:\\.]?\\s+.*", RegexOption.IGNORE_CASE))) {
                val qText = line.replaceFirst(Regex("^(\\d+[\\.\\)]|Pregunta\\s+\\d+[:\\.]?)\\s+", RegexOption.IGNORE_CASE), "").trim()
                if (qText.isNotBlank()) questions.add(qText)
            }
        }

        if (questions.size >= 2) {
            return ExamData(title = title, subject = subject, questions = questions)
        }
    }

    return null
}

// ------------------------------------------
// JOB / HR FORM DATA MODEL & PARSER
// ------------------------------------------

data class JobFormSection(
    val id: String,
    val title: String,
    val hint: String,
    val iconEmoji: String = "📝",
    val minLines: Int = 3
)

data class JobFormData(
    val formType: String,
    val title: String,
    val subtitle: String,
    val sections: List<JobFormSection>
)

fun parseInteractiveJobForm(content: String): JobFormData? {
    if (content.isBlank()) return null
    // If it's already a generated document (e.g. contains signature block, formal greetings or finished CV), don't prompt form again
    if (content.contains("Atentamente,", ignoreCase = true) ||
        content.contains("CURRÍCULUM VITAE", ignoreCase = true) && content.contains("EXPERIENCIA LABORAL", ignoreCase = true) && !content.contains("[FORMULARIO_LABORAL")
    ) {
        // Only return null if it's already the compiled final text
        if (!content.contains("[FORMULARIO_LABORAL", ignoreCase = true)) {
            return null
        }
    }

    // Pattern 1: Tagged format [FORMULARIO_LABORAL: ...] ... [/FORMULARIO_LABORAL]
    if (content.contains("[FORMULARIO_LABORAL", ignoreCase = true)) {
        val block = content.substringAfter("[FORMULARIO_LABORAL")
            .substringAfter("]")
            .substringBefore("[/FORMULARIO_LABORAL]")
            .trim()

        var title = "Formulario de Documento Laboral"
        var subtitle = "Escribe dentro de cada cuadro para armar tu documento profesional"
        val sections = mutableListOf<JobFormSection>()

        val lines = block.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("TITULO:", ignoreCase = true)) {
                title = line.substringAfter(":").trim()
            } else if (line.startsWith("SUBTITULO:", ignoreCase = true)) {
                subtitle = line.substringAfter(":").trim()
            } else if (line.startsWith("-") && line.contains(":")) {
                val secTitle = line.removePrefix("-").substringBefore(":").trim()
                val secHint = line.substringAfter(":").trim()
                val emoji = when {
                    secTitle.contains("Personal", ignoreCase = true) -> "👤"
                    secTitle.contains("Objetivo", ignoreCase = true) -> "🎯"
                    secTitle.contains("Experiencia", ignoreCase = true) -> "💼"
                    secTitle.contains("Formaci", ignoreCase = true) || secTitle.contains("Educaci", ignoreCase = true) -> "🎓"
                    secTitle.contains("Habilidad", ignoreCase = true) -> "⚡"
                    secTitle.contains("Empresa", ignoreCase = true) -> "🏢"
                    secTitle.contains("Motivo", ignoreCase = true) -> "📌"
                    else -> "📝"
                }
                sections.add(
                    JobFormSection(
                        id = secTitle.lowercase().replace(" ", "_"),
                        title = secTitle,
                        hint = secHint,
                        iconEmoji = emoji,
                        minLines = if (secTitle.contains("Experiencia", ignoreCase = true)) 4 else 3
                    )
                )
            }
        }

        if (sections.isNotEmpty()) {
            return JobFormData("CUSTOM", title, subtitle, sections)
        }
    }

    // Pattern 2: Detected Request for Curriculum sections
    val mentionsCvFields = content.contains("Datos personales", ignoreCase = true) &&
            content.contains("Experiencia laboral", ignoreCase = true) &&
            (content.contains("Formación académica", ignoreCase = true) || content.contains("Habilidades", ignoreCase = true))

    if (mentionsCvFields) {
        return JobFormData(
            formType = "CURRICULUM",
            title = "Formulario para Currículum Vitae",
            subtitle = "Escribe dentro de cada cuadro para armar tu CV profesional completo",
            sections = listOf(
                JobFormSection(
                    id = "datos_personales",
                    title = "Datos personales",
                    hint = "Nombre completo, teléfono, correo, ciudad/país, cédula (opcional)",
                    iconEmoji = "👤",
                    minLines = 3
                ),
                JobFormSection(
                    id = "objetivo_profesional",
                    title = "Objetivo profesional",
                    hint = "Breve descripción de tu perfil profesional, fortalezas y metas de trabajo",
                    iconEmoji = "🎯",
                    minLines = 3
                ),
                JobFormSection(
                    id = "experiencia_laboral",
                    title = "Experiencia laboral",
                    hint = "Empresas donde has trabajado, cargos, años/fechas y principales responsabilidades o logros",
                    iconEmoji = "💼",
                    minLines = 4
                ),
                JobFormSection(
                    id = "formacion_academica",
                    title = "Formación académica",
                    hint = "Carreras, títulos, colegios/universidades, cursos o certificaciones y años",
                    iconEmoji = "🎓",
                    minLines = 3
                ),
                JobFormSection(
                    id = "habilidades",
                    title = "Habilidades y destrezas",
                    hint = "Habilidades técnicas, habilidades blandas, herramientas informáticas e idiomas",
                    iconEmoji = "⚡",
                    minLines = 3
                )
            )
        )
    }

    // Pattern 3: Detected Request for Labor Letter / Carta de renuncia / solicitud
    val mentionsLetterFields = (content.contains("carta de renuncia", ignoreCase = true) ||
            content.contains("carta laboral", ignoreCase = true) ||
            content.contains("solicitud de empleo", ignoreCase = true)) &&
            (content.contains("nombre", ignoreCase = true) && content.contains("empresa", ignoreCase = true))

    if (mentionsLetterFields) {
        return JobFormData(
            formType = "CARTA",
            title = "Formulario para Documento Laboral",
            subtitle = "Completa los datos en cada cuadro para redactar tu documento formal",
            sections = listOf(
                JobFormSection(
                    id = "remitente",
                    title = "Tus datos (Remitente)",
                    hint = "Nombre completo, número de cédula, teléfono y dirección",
                    iconEmoji = "👤",
                    minLines = 3
                ),
                JobFormSection(
                    id = "destinatario",
                    title = "Datos de la Empresa / Destinatario",
                    hint = "Nombre de la empresa, nombre del jefe o departamento (ej: Recursos Humanos)",
                    iconEmoji = "🏢",
                    minLines = 2
                ),
                JobFormSection(
                    id = "motivo_cargo",
                    title = "Cargo y Motivo",
                    hint = "Cargo que desempeñas y motivo principal de la carta (renuncia, permiso, aumento, etc.)",
                    iconEmoji = "📌",
                    minLines = 3
                ),
                JobFormSection(
                    id = "fechas_detalles",
                    title = "Fechas y detalles clave",
                    hint = "Fecha de inicio/efectiva, último día de trabajo, preaviso o condiciones específicas",
                    iconEmoji = "📅",
                    minLines = 3
                )
            )
        )
    }

    return null
}

// ==========================================
// 2. JETPACK COMPOSE UI COMPONENTS
// ==========================================

/**
 * Interactive Exam Card with wide answer boxes under each question
 * and a "✅ Corregir y calificar" button at the end.
 */
@Composable
fun InteractiveExamCard(
    exam: ExamData,
    onSubmitAnswers: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val answers = rememberSaveable { mutableStateMapOf<Int, String>() }
    var submitted by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
            .testTag("interactive_exam_card"),
        color = ObsidianCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCyan, RadiantViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Examen",
                        tint = if (isAppDark()) DarkBackground else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        fontSize = 16.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Materia: ${exam.subject}",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianSubtle
                        ) {
                            Text(
                                text = "${exam.questions.size} preguntas",
                                color = TextSecondaryDark,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "📌 Escribe tu respuesta dentro de cada cuadro. Al finalizar, presiona el botón para corregir y calificar.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Questions with broad text boxes
            exam.questions.forEachIndexed { index, question ->
                val qNumber = index + 1
                val answer = answers[index] ?: ""

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // Question Header
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$qNumber",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Broad response box
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answers[index] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_answer_input_$qNumber"),
                        minLines = 3,
                        maxLines = 8,
                        placeholder = {
                            Text(
                                text = "Escribe aquí tu respuesta a la pregunta $qNumber...",
                                color = TextSecondaryDark.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSubtle.copy(alpha = 0.5f),
                            unfocusedContainerColor = ObsidianSubtle.copy(alpha = 0.3f),
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button: "✅ Corregir y calificar"
            Button(
                onClick = {
                    submitted = true
                    val compiledPrompt = buildString {
                        appendLine("Por favor corrige y califica el siguiente examen:")
                        appendLine("📌 Título: ${exam.title}")
                        appendLine("📌 Materia: ${exam.subject}")
                        appendLine("Total de preguntas: ${exam.questions.size}")
                        appendLine()
                        exam.questions.forEachIndexed { i, q ->
                            val userAns = answers[i]?.trim().orEmpty()
                            appendLine("${i + 1}. Pregunta: $q")
                            appendLine("👉 Mi respuesta: ${if (userAns.isNotBlank()) userAns else "(Sin responder)"}")
                            appendLine()
                        }
                        appendLine("Por favor evalúa mis respuestas con las correctas:")
                        appendLine("- Para cada pregunta indica: ✅ Correcta | ❌ Incorrecta + explicación")
                        appendLine("- Al final entrega la Calificación final: Nota: __ / __ (__%)")
                    }
                    onSubmitAnswers(compiledPrompt)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_exam_grade_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✅ Corregir y calificar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Interactive Job / HR Document Form with wide input boxes for each section
 * and a "✅ Generar documento" button at the end.
 */
@Composable
fun InteractiveJobFormCard(
    jobForm: JobFormData,
    onSubmitDocument: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionAnswers = rememberSaveable { mutableStateMapOf<String, String>() }
    var submitted by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
            .testTag("interactive_job_form_card"),
        color = ObsidianCard
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(RadiantViolet, ElectricCyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = "Trabajo",
                        tint = if (isAppDark()) DarkBackground else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = jobForm.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        fontSize = 16.sp
                    )
                    Text(
                        text = jobForm.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Form Sections with broad boxes
            jobForm.sections.forEach { section ->
                val currentText = sectionAnswers[section.id] ?: ""

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${section.iconEmoji} ${section.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { sectionAnswers[section.id] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("job_form_section_${section.id}"),
                        minLines = section.minLines,
                        maxLines = 10,
                        placeholder = {
                            Text(
                                text = section.hint,
                                color = TextSecondaryDark.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSubtle.copy(alpha = 0.5f),
                            unfocusedContainerColor = ObsidianSubtle.copy(alpha = 0.3f),
                            focusedBorderColor = RadiantViolet,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button: "✅ Generar documento"
            Button(
                onClick = {
                    submitted = true
                    val compiledPrompt = buildString {
                        appendLine("Por favor genera mi documento laboral completo y definitivo con la siguiente información que he completado en los cuadros:")
                        appendLine("📌 Tipo de documento: ${jobForm.title}")
                        appendLine()
                        jobForm.sections.forEach { sec ->
                            val userVal = sectionAnswers[sec.id]?.trim().orEmpty()
                            appendLine("🔹 ${sec.title}:")
                            appendLine(if (userVal.isNotBlank()) userVal else "(No especificado)")
                            appendLine()
                        }
                        appendLine("Por favor organiza y redacta el documento profesional formal completo, impecable, elocuente y listo para presentar sin texto introductorio innecesario.")
                    }
                    onSubmitDocument(compiledPrompt)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_job_document_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadiantViolet
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✅ Generar documento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}
