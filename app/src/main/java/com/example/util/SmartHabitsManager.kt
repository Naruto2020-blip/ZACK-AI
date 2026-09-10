package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gestor inteligente de hábitos y sugerencias predictivas.
 * Aprende de los patrones del usuario (días, horas, frecuencia y temas),
 * sugiere amablemente antes de que se lo pida y reduce la frecuencia si el usuario no interactúa.
 */
object SmartHabitsManager {

    private const val PREFS_NAME = "zack_ai_habits_prefs"
    private const val KEY_HABIT_EVENTS = "habit_events"
    private const val KEY_IGNORED_COUNTS = "ignored_counts"
    private const val KEY_COOLDOWNS = "cooldowns"
    private const val KEY_CUSTOMARY_HOUR = "customary_reminder_hour"

    data class SmartSuggestion(
        val id: String,
        val text: String,
        val promptToSend: String,
        val category: String
    )

    data class DetectedCommitment(
        val title: String,
        val suggestedHourText: String,
        val estimatedTimestamp: Long? = null
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Registra un hábito cada vez que el usuario interactúa.
     */
    fun recordUserInteraction(context: Context, userText: String) {
        if (userText.isBlank()) return
        val prefs = getPrefs(context)
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
        val hour = cal.get(Calendar.HOUR_OF_DAY) // 0..23

        val category = categorizeIntent(userText)

        try {
            val raw = prefs.getString(KEY_HABIT_EVENTS, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }
            val now = System.currentTimeMillis()

            var found = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("category") == category &&
                    obj.optInt("dayOfWeek") == dayOfWeek &&
                    Math.abs(obj.optInt("hour") - hour) <= 2
                ) {
                    obj.put("count", obj.optInt("count", 0) + 1)
                    obj.put("lastSeen", now)
                    found = true
                    break
                }
            }

            if (!found) {
                val newObj = JSONObject().apply {
                    put("category", category)
                    put("dayOfWeek", dayOfWeek)
                    put("hour", hour)
                    put("count", 1)
                    put("lastSeen", now)
                }
                array.put(newObj)
            }

            // Keep max 40 habit patterns to avoid bloat
            if (array.length() > 40) {
                val pruned = JSONArray()
                for (i in (array.length() - 40) until array.length()) {
                    pruned.put(array.get(i))
                }
                prefs.edit().putString(KEY_HABIT_EVENTS, pruned.toString()).apply()
            } else {
                prefs.edit().putString(KEY_HABIT_EVENTS, array.toString()).apply()
            }

            // If it's a reminder or task, learn customary hour
            if (category == "recordatorios" || userText.contains("recuérdame", ignoreCase = true) || userText.contains("alarma", ignoreCase = true)) {
                prefs.edit().putInt(KEY_CUSTOMARY_HOUR, hour).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Obtiene la hora habitual para recordatorios del usuario (ej: "8:00 AM" o "9:00 AM").
     */
    fun getCustomaryReminderTimeFormatted(context: Context): String {
        val prefs = getPrefs(context)
        val hour = prefs.getInt(KEY_CUSTOMARY_HOUR, 8)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }
        val sdf = SimpleDateFormat("h:mm a", Locale.forLanguageTag("es"))
        return sdf.format(cal.time)
    }

    /**
     * Devuelve una sugerencia predictiva proactiva si detecta un patrón repetido hoy,
     * respetando el principio de "No ser insistente".
     */
    fun getProactiveSuggestion(context: Context): SmartSuggestion? {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        try {
            val raw = prefs.getString(KEY_HABIT_EVENTS, "[]") ?: "[]"
            val array = try {
                JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val day = obj.optInt("dayOfWeek")
                val hour = obj.optInt("hour")
                val count = obj.optInt("count", 0)
                val category = obj.optString("category")
                val suggestionId = "sug_${category}_$day"

                // Check if in cooldown
                val cooldownUntil = prefs.getLong("cd_$suggestionId", 0L)
                if (now < cooldownUntil) {
                    continue
                }

                // If pattern repeated at least twice for this day/time
                if (day == currentDay && Math.abs(hour - currentHour) <= 2 && count >= 2) {
                    val customaryTime = getCustomaryReminderTimeFormatted(context)
                    val dayName = when (day) {
                        Calendar.MONDAY -> "los lunes"
                        Calendar.TUESDAY -> "los martes"
                        Calendar.WEDNESDAY -> "los miércoles"
                        Calendar.THURSDAY -> "los jueves"
                        Calendar.FRIDAY -> "los viernes"
                        Calendar.SATURDAY -> "los sábados"
                        Calendar.SUNDAY -> "los domingos"
                        else -> "a esta hora"
                    }

                    return when (category) {
                        "agenda_lunes" -> SmartSuggestion(
                            id = suggestionId,
                            text = "Sueles organizar tus pendientes $dayName a esta hora, ¿te ayudo a preparar tu semana?",
                            promptToSend = "Ayúdame a organizar mis tareas y pendientes prioritarios para esta semana.",
                            category = "Agenda"
                        )
                        "compras" -> SmartSuggestion(
                            id = suggestionId,
                            text = "Sueles revisar tu lista de compras $dayName, ¿te ayudo a chequear qué falta?",
                            promptToSend = "Revisemos mi lista de compras y sugerencias de despensa.",
                            category = "Compras"
                        )
                        "recordatorios" -> SmartSuggestion(
                            id = suggestionId,
                            text = "Sueles revisar tus recordatorios $dayName a las $customaryTime, ¿te muestro lo pendiente?",
                            promptToSend = "Muéstrame mis recordatorios y tareas pendientes para hoy.",
                            category = "Recordatorios"
                        )
                        "estudio" -> SmartSuggestion(
                            id = suggestionId,
                            text = "Sueles repasar temas de estudio $dayName a esta hora, ¿en qué te gustaría enfocarte hoy?",
                            promptToSend = "¿En qué podemos profundizar hoy para mi sesión de estudio?",
                            category = "Estudio"
                        )
                        "imagenes" -> SmartSuggestion(
                            id = suggestionId,
                            text = "Sueles crear imágenes e ideas visuales $dayName, ¿tienes alguna idea para diseñar?",
                            promptToSend = "Quiero crear una nueva imagen visual artística.",
                            category = "Creatividad"
                        )
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Si el usuario descarta o ignora una sugerencia, se reduce la frecuencia aumentando el período de enfriamiento.
     */
    fun onSuggestionDismissed(context: Context, suggestionId: String) {
        val prefs = getPrefs(context)
        val ignores = prefs.getInt("ign_$suggestionId", 0) + 1
        prefs.edit().putInt("ign_$suggestionId", ignores).apply()

        // Enfriamiento exponencial: 1era vez = 2 días, 2da vez = 5 días, 3ra+ = 10 días
        val backoffDays = when (ignores) {
            1 -> 2L
            2 -> 5L
            else -> 10L
        }
        val cooldownMillis = System.currentTimeMillis() + (backoffDays * 24 * 60 * 60 * 1000L)
        prefs.edit().putLong("cd_$suggestionId", cooldownMillis).apply()
    }

    /**
     * Si el usuario acepta la sugerencia, se restablece el contador de ignoradas.
     */
    fun onSuggestionAccepted(context: Context, suggestionId: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putInt("ign_$suggestionId", 0)
            .putLong("cd_$suggestionId", System.currentTimeMillis() + (12 * 60 * 60 * 1000L)) // Próxima sugerencia tras al menos 12 horas
            .apply()
    }

    /**
     * Detecta si un mensaje del usuario menciona un compromiso, cita o tarea pendiente.
     */
    fun detectCommitmentInText(context: Context, message: String): DetectedCommitment? {
        val lower = message.lowercase(Locale.getDefault())
        val taskPatterns = listOf(
            "tengo que ", "debo ", "tengo reunión", "tengo cita", "tengo que entregar",
            "hay que comprar", "necesito recordar", "recuérdame", "acordarme de", "compromiso con",
            "tengo cita médica", "ir al banco", "pagar el", "pagar la", "entregar reporte"
        )

        val hasTaskPattern = taskPatterns.any { lower.contains(it) }
        if (!hasTaskPattern) return null

        val customaryTime = getCustomaryReminderTimeFormatted(context)

        // Limpiar para obtener título corto
        var cleanedTitle = message.trim()
        if (cleanedTitle.length > 60) {
            cleanedTitle = cleanedTitle.take(57) + "..."
        }

        return DetectedCommitment(
            title = cleanedTitle,
            suggestedHourText = customaryTime,
            estimatedTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
        )
    }

    private fun categorizeIntent(text: String): String {
        val l = text.lowercase(Locale.getDefault())
        return when {
            l.contains("compra") || l.contains("supermercado") || l.contains("leche") || l.contains("despensa") || l.contains("precio") -> "compras"
            l.contains("tarea") || l.contains("pendiente") || l.contains("semana") || l.contains("lunes") || l.contains("organizar") -> "agenda_lunes"
            l.contains("recuérdame") || l.contains("recordatorio") || l.contains("alarma") || l.contains("cita") -> "recordatorios"
            l.contains("imagen") || l.contains("dibujo") || l.contains("diseña") || l.contains("foto de") || l.contains("fondo de pantalla") -> "imagenes"
            l.contains("explica") || l.contains("tutor") || l.contains("examen") || l.contains("estudiar") || l.contains("resumen") -> "estudio"
            l.contains("carta") || l.contains("oficio") || l.contains("solicitud") || l.contains("correo formal") -> "documentos"
            else -> "general"
        }
    }
}
