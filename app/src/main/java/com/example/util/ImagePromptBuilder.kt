package com.example.util

import java.net.URLDecoder
import java.net.URLEncoder

object ImagePromptBuilder {

    private val imageRequestPatterns = listOf(
        "genera una imagen", "generar imagen", "generame una imagen", "genérame una imagen",
        "crea una imagen", "crear una imagen", "crear imagen", "créame una imagen", "creame una imagen",
        "dibuja", "dibújame", "dibujame", "haz una imagen", "hazme una imagen",
        "haz un dibujo", "hazme un dibujo", "muéstrame una imagen", "muestrame una imagen",
        "quiero una imagen", "quiero ver", "foto de", "imagen de", "foto del", "imagen del",
        "busca la imagen", "buscar la imagen", "busca una imagen", "buscar una imagen", "busca imagen",
        "pásame una imagen", "pasame una imagen", "pásame una foto", "pasame una foto",
        "mándame una imagen", "mandame una imagen", "mándame una foto", "mandame una foto",
        "envíame una imagen", "enviame una imagen", "envíame una foto", "enviame una foto",
        "escudo de", "bandera de",
        "ilustra", "ilustración de", "ilustracion de", "pinta", "píntame", "pintame",
        "pintura de", "retrato de",
        "draw", "generate an image", "create an image", "make a picture", "picture of",
        "photo of", "illustration of", "paint a picture"
    )

    fun isImageRequest(prompt: String): Boolean {
        val p = prompt.lowercase().trim()
        return imageRequestPatterns.any { p.contains(it) }
    }

    fun cleanPromptText(prompt: String): String {
        return prompt
            .replace(Regex("(?i)^(?:por\\s+favor\\s+)?(?:genera|generar|generame|genérame|crea|crear|creame|créame|dibuja|dibújame|dibujame|haz|hazme|muéstrame|muestrame|quiero(?:\\s+ver)?|pinta|píntame|pintame)\\s+(?:una\\s+|un\\s+)?(?:imagen|foto|dibujo|ilustración|ilustracion|cuadro|pintura|retrato)?\\s*(?:de|sobre)?\\s*"), "")
            .trim()
            .ifBlank { prompt.trim() }
    }

    private val spanishToEnglishVocab = listOf(
        // Sujetos & Personajes
        "astronauta" to "astronaut",
        "samurai" to "samurai warrior",
        "ninja" to "stealth ninja",
        "guerrero" to "warrior",
        "caballero" to "knight",
        "rey" to "king",
        "reina" to "queen",
        "princesa" to "princess",
        "príncipe" to "prince",
        "mago" to "wizard",
        "bruja" to "witch",
        "robot" to "futuristic robot",
        "cíborg" to "cyborg",
        "ciborg" to "cyborg",
        "alienígena" to "alien",
        "extraterrestre" to "extraterrestrial alien",
        "dragón" to "majestic dragon",
        "dragon" to "majestic dragon",
        "monstruo" to "creature",
        "persona" to "person",
        "hombre" to "man",
        "mujer" to "woman",
        "niño" to "young boy",
        "niña" to "young girl",
        "anciano" to "elderly man",
        "abuelo" to "grandfather",
        "abuela" to "grandmother",

        // Animales
        "gato" to "cat",
        "perro" to "dog",
        "caballo" to "horse",
        "león" to "lion",
        "leon" to "lion",
        "tigre" to "tiger",
        "lobo" to "wolf",
        "oso" to "bear",
        "águila" to "eagle",
        "aguila" to "eagle",
        "pájaro" to "bird",
        "pajaro" to "bird",
        "ave" to "bird",
        "elefante" to "elephant",
        "jirafa" to "giraffe",
        "mono" to "monkey",
        "delfín" to "dolphin",
        "delfin" to "dolphin",
        "ballena" to "whale",
        "tiburón" to "shark",
        "tiburon" to "shark",
        "pez" to "fish",
        "mariposa" to "butterfly",
        "zorro" to "fox",
        "conejo" to "rabbit",
        "ciervo" to "deer",
        "venado" to "deer",

        // Vehículos & Objetos
        "coche deportivo" to "modern sports car",
        "auto deportivo" to "modern sports car",
        "coche" to "car",
        "auto" to "car",
        "carro" to "car",
        "moto" to "motorcycle",
        "motocicleta" to "motorcycle",
        "bicicleta" to "bicycle",
        "avión" to "airplane",
        "avion" to "airplane",
        "barco" to "ship",
        "nave espacial" to "sci-fi spaceship",
        "nave" to "spaceship",
        "espada" to "sword",
        "katana" to "japanese katana sword",
        "armadura" to "ornate armor",
        "escudo" to "shield",
        "gafas de sol" to "sunglasses",
        "lentes de sol" to "sunglasses",
        "gafas" to "glasses",
        "lentes" to "glasses",
        "sombrero" to "hat",
        "casco" to "helmet",
        "corona" to "royal crown",
        "capa" to "flowing cape",
        "traje" to "suit",
        "vestido" to "dress",
        "pelota" to "ball",
        "balón" to "ball",
        "balon" to "ball",
        "guitarra" to "guitar",
        "piano" to "piano",
        "libro" to "book",
        "teléfono" to "smartphone",
        "computadora" to "computer",

        // Entornos & Lugares
        "en marte" to "on the planet Mars with red dusty terrain",
        "marte" to "planet Mars red surface",
        "en la luna" to "on the surface of the moon with Earth visible in the black sky",
        "luna" to "moon",
        "espacio" to "deep outer space with colorful nebula and stars",
        "playa" to "tropical sandy beach with turquoise ocean waves",
        "montaña" to "majestic snow-capped mountain",
        "montañas" to "mountain range",
        "bosque" to "dense enchanted forest with sunbeams",
        "selva" to "lush tropical jungle",
        "desierto" to "vast desert dunes under clear sky",
        "ciudad" to "vibrant metropolis city",
        "calle" to "city street",
        "templo" to "ancient temple",
        "castillo" to "medieval castle",
        "isla" to "tropical island",
        "río" to "scenic river",
        "rio" to "scenic river",
        "lago" to "crystal clear lake",
        "cielo" to "sky",

        // Clima, Iluminación & Tiempo
        "atardecer" to "dramatic golden hour sunset",
        "puesta de sol" to "golden sunset",
        "amanecer" to "warm morning sunrise",
        "noche" to "night sky with brilliant stars",
        "nieve" to "falling snow winter wonderland",
        "lluvia" to "rainy atmosphere with glistening puddles",
        "tormenta" to "dramatic thunderstorm with lightning",
        "fuego" to "fiery flames",
        "agua" to "water",
        "otoño" to "autumn with colorful falling leaves",
        "primavera" to "blooming spring flowers",

        // Colores
        "rojo" to "red",
        "roja" to "red",
        "azul" to "blue",
        "verde" to "green",
        "amarillo" to "yellow",
        "amarilla" to "yellow",
        "blanco" to "white",
        "blanca" to "white",
        "negro" to "black",
        "negra" to "black",
        "dorado" to "golden",
        "dorada" to "golden",
        "plateado" to "silver",
        "plateada" to "silver",
        "morado" to "purple",
        "morada" to "purple",
        "violeta" to "violet",
        "naranja" to "vibrant orange",
        "rosa" to "pink",
        "rosado" to "pink",
        "rosada" to "pink",
        "gris" to "gray",
        "marrón" to "brown",
        "marron" to "brown",
        "café" to "brown",
        "oscuro" to "dark",
        "brillante" to "bright glowing",

        // Estilos artísticos
        "fotografía" to "professional photorealistic photograph",
        "fotografia" to "professional photorealistic photograph",
        "realista" to "ultra realistic photorealistic 8k",
        "hiperrealista" to "hyperrealistic award winning photography",
        "dibujo animado" to "vibrant cartoon animation style",
        "caricatura" to "caricature cartoon style",
        "anime" to "japanese anime style manga art",
        "óleo" to "detailed oil painting on canvas",
        "oleo" to "detailed oil painting on canvas",
        "acuarela" to "delicate watercolor painting with soft gradients",
        "cyberpunk" to "cyberpunk futuristic neon glow aesthetic",
        "3d" to "3D digital render cinematic lighting Octane Render",
        "pixel art" to "retro pixel art 16 bit",
        "cómic" to "graphic novel comic book illustration",
        "comic" to "graphic novel comic book illustration",

        // Acciones & Preposiciones
        "montando" to "riding",
        "volando" to "flying through the air",
        "corriendo" to "running fast",
        "saltando" to "jumping",
        "comiendo" to "eating",
        "bebiendo" to "drinking",
        "durmiendo" to "sleeping peacefully",
        "jugando" to "playing happily",
        "mirando" to "looking at",
        "leyendo" to "reading",
        "caminando" to "walking",
        "sonriendo" to "smiling brightly",
        "con" to "with",
        "en" to "in",
        "sobre" to "on",
        "bajo" to "under",
        "y" to "and",
        "de" to "of"
    )

    fun translateAndEnrichPrompt(cleanInput: String): String {
        var text = cleanInput.trim()

        // Replace multi-word terms first, then single words
        spanishToEnglishVocab.sortedByDescending { it.first.length }.forEach { (es, en) ->
            val pattern = Regex("(?i)\\b${Regex.escape(es)}\\b")
            text = pattern.replace(text, en)
        }

        // Quality boosters for pristine, 100% faithful and high fidelity render
        val qualityBoosters = "highly detailed, masterpiece, sharp focus, vibrant colors, 8k resolution, cinematic lighting, professional composition"

        return "$text, $qualityBoosters"
    }

    fun buildCleanPollinationsUrl(userPrompt: String, seed: Int = (System.currentTimeMillis() % 100000).toInt()): String {
        val clean = cleanPromptText(userPrompt)
        val enriched = translateAndEnrichPrompt(clean)
        val encodedPrompt = URLEncoder.encode(enriched, "UTF-8").replace("+", "%20")

        val negativePrompt = "watermark%2Ctext%2Clogo%2Csignature%2Cusername%2Cad%2Cadvertising%2Cwords%2Cletters%2Ctrademark%2Ccopyright%2Cblurry%2Cdistorted%2Clow+quality"

        return "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&nofeed=true&negative_prompt=$negativePrompt&seed=$seed"
    }

    fun sanitizePollinationsUrl(rawUrl: String, originalPrompt: String? = null): String {
        return try {
            val urlWithoutBrackets = rawUrl.trim().trim('<', '>', '{', '}', '(', ')')
            if (!urlWithoutBrackets.contains("image.pollinations.ai", ignoreCase = true)) {
                return urlWithoutBrackets
            }

            // Extract prompt part between "/prompt/" and "?" (or end of url)
            val promptStartIndex = urlWithoutBrackets.indexOf("/prompt/")
            if (promptStartIndex == -1) {
                return buildCleanPollinationsUrl(originalPrompt ?: "beautiful scene")
            }

            val afterPrompt = urlWithoutBrackets.substring(promptStartIndex + "/prompt/".length)
            val queryIndex = afterPrompt.indexOf("?")
            val rawPromptPart = if (queryIndex != -1) afterPrompt.substring(0, queryIndex) else afterPrompt

            // Decode to inspect
            val decodedPrompt = try {
                URLDecoder.decode(rawPromptPart.replace("+", " "), "UTF-8")
            } catch (e: Exception) {
                rawPromptPart
            }

            // If prompt is placeholder or empty, fallback to originalPrompt
            val effectivePrompt = if (decodedPrompt.isBlank() || decodedPrompt.contains("prompt_en_ingles", ignoreCase = true)) {
                originalPrompt ?: "beautiful detailed artwork"
            } else {
                decodedPrompt
            }

            val cleanPrompt = cleanPromptText(effectivePrompt)
            val enriched = translateAndEnrichPrompt(cleanPrompt)
            val encodedPrompt = URLEncoder.encode(enriched, "UTF-8").replace("+", "%20")

            val seed = (System.currentTimeMillis() % 100000).toInt()
            val negativePrompt = "watermark%2Ctext%2Clogo%2Csignature%2Cusername%2Cad%2Cadvertising%2Cwords%2Cletters%2Ctrademark%2Ccopyright%2Cblurry%2Cdistorted%2Clow+quality"

            "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&nofeed=true&negative_prompt=$negativePrompt&seed=$seed"
        } catch (e: Exception) {
            buildCleanPollinationsUrl(originalPrompt ?: "beautiful artwork")
        }
    }
}
