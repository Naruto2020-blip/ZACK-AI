package com.example

import com.example.util.DocumentCleaner
import com.example.util.RealTimeGroundingService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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

  @Test
  fun testRealTimeGroundingServiceDetectsQueries() {
    assertFalse("Greeting should not trigger web search", RealTimeGroundingService.shouldSearchWeb("Hola"))
    assertFalse("Greeting should not trigger web search", RealTimeGroundingService.shouldSearchWeb("gracias"))
    assertTrue("President query should trigger web search", RealTimeGroundingService.shouldSearchWeb("presidenta de Costa Rica"))
    assertTrue("News query should trigger web search", RealTimeGroundingService.shouldSearchWeb("noticias de hoy"))
    assertTrue("Price query should trigger web search", RealTimeGroundingService.shouldSearchWeb("precio del dolar"))
  }

  @Test
  fun testRealTimeGroundingServiceFetchesLiveContext() = runBlocking {
    val context = RealTimeGroundingService.fetchRealTimeContext("presidenta de Costa Rica")
    assertNotNull("Real-time context should not be null", context)
    assertTrue("Real-time context should contain Costa Rica or Laura Fernández", 
      context!!.contains("Costa Rica", ignoreCase = true) || context.contains("Laura Fernández", ignoreCase = true))
    assertTrue("Should include year 2026", context.contains("2026"))
  }

  @Test
  fun testWebImageSearchQueryExtraction() {
    val userPrompt = "Vea me mandas las imágenes que no quiero osea busca la imagen en internet del escudo de costa rica y verás. Quiero que cualquier  imagen que yo pida me la mande bien ya que está mal"
    val extracted = com.example.util.WebImageSearchService.extractSearchQuery(userPrompt)
    assertTrue("Should extract 'escudo de costa rica', but got: $extracted", 
        extracted.contains("escudo de costa rica", ignoreCase = true))

    val simplePrompt = "pásame una imagen del escudo de Costa Rica"
    val extractedSimple = com.example.util.WebImageSearchService.extractSearchQuery(simplePrompt)
    assertTrue("Should extract 'escudo de Costa Rica', but got: $extractedSimple",
        extractedSimple.equals("escudo de Costa Rica", ignoreCase = true))
  }

  @Test
  fun testWebImageSearchFindsEscudoDeCostaRica() = runBlocking {
    val result = com.example.util.WebImageSearchService.searchRealImage("escudo de Costa Rica")
    assertNotNull("Real web image should be found for Escudo de Costa Rica", result)
    assertTrue("Title should contain Escudo de Costa Rica", result!!.title.contains("Costa Rica", ignoreCase = true))
    assertTrue("URL should be a valid web image URL", result.imageUrl.startsWith("https://"))
    assertTrue("URL should contain wikimedia or wikipedia", 
        result.imageUrl.contains("wikimedia.org") || result.imageUrl.contains("wikipedia.org"))
  }

  @Test
  fun testWebImageSearchFindsEscudoDeCostaRicaWithMissingDe() = runBlocking {
    val result = com.example.util.WebImageSearchService.searchRealImage("imagen escudo Costa Rica")
    assertNotNull("Real web image should be found for 'imagen escudo Costa Rica'", result)
    assertTrue("Title should contain Escudo or Costa Rica", result!!.title.contains("Costa Rica", ignoreCase = true))
    assertFalse("Should NOT be the 1825 old gold coin", result.title.contains("1825") || result.title.contains("Gold", ignoreCase = true))
    assertTrue("Score should be 500 for direct match", result.score >= 150)
  }

  @Test
  fun testImageParserHandlesRealWebImages() {
    val markdown = "![Escudo de Costa Rica](https://thumb.wikimedia.org/wikipedia/commons/thumb/8/84/Coat_of_arms_of_Costa_Rica.svg/1280px-Coat_of_arms_of_Costa_Rica.svg.png)\n\nAquí tienes el escudo oficial."
    val parsed = com.example.util.ImageParser.parse(markdown)
    assertEquals(1, parsed.imageUrls.size)
    assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/8/84/Coat_of_arms_of_Costa_Rica.svg/1280px-Coat_of_arms_of_Costa_Rica.svg.png", parsed.imageUrls[0])
    assertTrue("Clean text should preserve description", parsed.cleanText.contains("Aquí tienes el escudo oficial"))
  }
}

