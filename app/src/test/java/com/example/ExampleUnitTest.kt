package com.example

import com.example.util.DocumentCleaner
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDocumentCleanerRemovesPreamblesExamplesAndBranding() {
    val rawSample = """
      Para redactar la carta adecuada, aquí tienes el formato listo.
      Solo debes completar los espacios entre corchetes con tu información personal.
      
      # Créame una carta para el IMAS
      
      [Lugar, ej: San José], [Fecha, ej: 2 de setiembre de 2026]
      
      Señores
      Instituto Mixto de Ayuda Social (IMAS)
      Presente.
      
      Estimados señores:
      
      Por medio de la presente, yo [Tu Nombre Completo], portador de la cédula de identidad número [Cédula, ej: 1-2345-6789], vecino de [Dirección, ej: Desamparados], me dirijo respetuosamente a ustedes para solicitar ayuda económica para [ej: Alquiler, alimentación y medicamentos].
      
      Agradeciendo de antemano la atención brindada a esta solicitud, se despide atentamente,
      
      _____________________________
      [Tu Nombre Completo]
      Cédula: [Cédula]
      Teléfono: [Teléfono]
      
      Recomendaciones:
      - Recuerda adjuntar copia de la cédula y recibo de luz.
      - Presentar en la oficina más cercana.
      
      Generado con ZACK AI · Página 1
    """.trimIndent()

    val cleaned = DocumentCleaner.cleanLetterDocument(rawSample, userPrompt = "Créame una carta para el IMAS")

    // Assertions
    assertFalse("Should not contain 'Para redactar'", cleaned.contains("Para redactar", ignoreCase = true))
    assertFalse("Should not contain 'Solo debes completar'", cleaned.contains("Solo debes completar", ignoreCase = true))
    assertFalse("Should not contain 'Créame una carta'", cleaned.contains("Créame una carta", ignoreCase = true))
    assertFalse("Should not contain 'ej:'", cleaned.contains("ej:", ignoreCase = true))
    assertFalse("Should not contain 'ZACK AI'", cleaned.contains("ZACK AI", ignoreCase = true))
    assertFalse("Should not contain 'Recomendaciones'", cleaned.contains("Recomendaciones", ignoreCase = true))
    assertFalse("Should not contain 'Generado con'", cleaned.contains("Generado con", ignoreCase = true))

    // Valid formal letter content must be preserved
    assertTrue("Should retain recipient 'Instituto Mixto de Ayuda Social'", cleaned.contains("Instituto Mixto de Ayuda Social"))
    assertTrue("Should retain salutation 'Estimados señores:'", cleaned.contains("Estimados señores:"))
    assertTrue("Should retain clean bracket placeholder '[Tu Nombre Completo]'", cleaned.contains("[Tu Nombre Completo]"))
    assertTrue("Should retain signature placeholder", cleaned.contains("_____________________________"))
  }
}

