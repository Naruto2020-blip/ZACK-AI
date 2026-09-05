package com.example.util

import com.example.data.model.ShoppingCategory
import com.example.data.model.ShoppingItem
import java.text.Normalizer
import java.util.Locale

object ShoppingCategorizer {

    private val MEAT_KEYWORDS = setOf(
        // Res y carnes rojas
        "carne", "res", "carne de res", "carne molida", "carne picada", "posta", "bistec", "bistek",
        "lomo", "solomillo", "costilla", "costillas", "chuleta", "chuletas", "falda", "cecina",
        "osobuco", "milanesa", "chicharron", "chicharrones", "menudo", "mondongo", "higado",
        "lengua", "rabo", "panceta", "bacon", "tocino", "morcilla", "entraña",
        // Pollo y aves
        "pollo", "pechuga", "pechugas", "alita", "alitas", "muslo", "muslos", "menudos",
        "gallina", "pavo", "nuggets", "filete de pollo", "milanesa de pollo",
        // Cerdo
        "cerdo", "posta de cerdo", "costilla de cerdo", "chuleta de cerdo", "lomo de cerdo",
        "tocineta", "carne de cerdo",
        // Embutidos y fríos
        "salchicha", "salchichas", "salchichon", "chorizo", "chorizos", "jamon", "mortadela",
        "pate", "salami", "pepperoni", "prosciutto",
        // Pescados y mariscos
        "pescado", "corvina", "tilapia", "salmon", "atun fresco", "pargo", "camaron", "camarones",
        "langostino", "langostinos", "calamar", "calamares", "pulpo", "marisco", "mariscos",
        "almeja", "almejas", "mejillon", "mejillones", "filete de pescado", "trucha"
    )

    private val VEGGIES_KEYWORDS = setOf(
        // Hortalizas y verduras
        "tomate", "tomates", "jitomate", "jitomates", "cebolla", "cebollas", "cebollin", "cebollino",
        "culantro", "cilantro", "chile", "chiles", "chile dulce", "pimiento", "pimientos",
        "ajo", "ajos", "lechuga", "lechugas", "repollo", "repollos", "col", "brocoli", "coliflor",
        "espinaca", "espinacas", "apio", "zanahoria", "zanahorias", "pepino", "pepinos",
        "remolacha", "remolachas", "rabano", "rabanos", "ayote", "zapallo", "calabacin",
        "zuchini", "zucchini", "chayote", "chayotes", "elote", "elotes", "maiz dulce",
        "vainica", "vainicas", "judias", "ejote", "ejotes", "berenjena", "berenjenas",
        "alcachofa", "alcachofas", "esparrago", "esparragos", "hongo", "hongos", "champinon",
        "champinones", "setas", "albahaca", "perejil",
        // Tubérculos y plátanos
        "papa", "papas", "patata", "patatas", "platano", "platanos", "platano maduro",
        "platano verde", "banano", "bananos", "guineo", "guineos", "yuca", "yucas",
        "camote", "camotes", "batata", "batatas", "name", "tiquisque", "malanga", "jengibre",
        // Frutas
        "aguacate", "aguacates", "palta", "paltas", "limon", "limones", "lima", "limas",
        "naranja", "naranjas", "mandarina", "mandarinas", "manzana", "manzanas", "uva", "uvas",
        "fresa", "fresas", "mora", "moras", "sandia", "sandias", "melon", "melones",
        "pina", "pinas", "papaya", "papayas", "mango", "mangos", "maracuya", "carambola",
        "guanabana", "guayaba", "guayabas", "kiwi", "kiwis", "pera", "peras", "durazno",
        "duraznos", "melocoton", "ciruela", "ciruelas", "arandano", "arandanos", "cereza",
        "cerezas", "coco", "cocos"
    )

    private val SUPERMARKET_KEYWORDS = setOf(
        // Lácteos y huevos
        "leche", "leches", "pinito", "leche en polvo", "leche condensada", "leche evaporada",
        "natilla", "crema dulce", "crema", "mantequilla", "margarina", "queso", "quesos",
        "quesillo", "queso crema", "queso turrialba", "queso mozzarella", "yogurt", "yogur",
        "yogures", "huevo", "huevos", "helado", "helados",
        // Granos y harinas
        "frijol", "frijoles", "frijoles negros", "frijoles rojos", "arroz", "arroz blanco",
        "lenteja", "lentejas", "garbanzo", "garbanzos", "pasta", "pastas", "espagueti",
        "espaguetis", "spaghetti", "macarron", "macarrones", "fideo", "fideos", "ramen",
        "sopa", "sopas", "harina", "harina de trigo", "masa", "maseca", "avena", "cereal",
        "cereales", "corn flakes", "maicena", "maizena",
        // Panadería
        "pan", "panes", "baguette", "pan molde", "pan cuadrado", "pan blanco", "pan integral",
        "pan dulce", "tortilla", "tortillas", "galleta", "galletas", "tostada", "tostadas",
        "reposteria",
        // Despensa y condimentos
        "azucar", "sal", "aceite", "aceite de cocina", "aceite de oliva", "manteca", "cafe",
        "te", "cocoa", "cacao", "chocolate", "chocolates", "mayonesa", "ketchup", "salsa de tomate",
        "mostaza", "salsa lizano", "lizano", "salsa inglesa", "salsa soya", "salsa de soya",
        "vinagre", "oregano", "comino", "achiote", "consome", "cubito", "cubitos", "pimienta",
        "canela", "polvo de hornear", "levadura", "atun", "atun en lata", "sardina", "sardinas",
        "enlatado", "enlatados", "maiz en lata", "mermelada", "jalea", "miel", "sirope", "aceituna",
        "aceitunas",
        // Bebidas
        "agua", "aguas", "refresco", "refrescos", "gaseosa", "gaseosas", "coca cola", "pepsi",
        "fresco", "jugo", "jugos", "te frio", "cerveza", "cervezas", "vino", "vinos",
        // Aseo personal
        "jabon", "jabon de bano", "champu", "shampoo", "acondicionador", "desodorante",
        "pasta de dientes", "pasta dental", "cepillo de dientes", "enjuague bucal",
        "papel higienico", "papel sanitario", "toallas sanitarias", "afeitadora", "rastrillo",
        "crema de afeitar", "locion", "crema", "toallitas humedas",
        // Limpieza del hogar
        "detergente", "jabon en polvo", "cloro", "lejia", "desinfectante", "fabuloso", "ajax",
        "suavizante", "suavitel", "lavaplatos", "jabon liquido", "esponja", "esponjas",
        "bolsas de basura", "bolsa de basura", "servilleta", "servilletas", "toallas de cocina",
        "papel toalla", "papel aluminio", "papel encerado"
    )

    private fun normalize(text: String): String {
        val n = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    /**
     * Extracts quantity prefix if present (e.g. "2 kg de carne" -> ("2 kg", "carne"))
     */
    fun extractQuantityAndName(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        val regex = Regex("""^(\d+(?:[.,]\d+)?(?:\s*(?:/\s*\d+))?\s*(?:kg|kilos?|kilo|libras?|lbs?|gramos?|g|litros?|l|unidades?|unids?|unid|un|latas?|bolsas?|paquetes?|cajas?|botellas?|docenas?|manojo|gajos?)?|\b(?:un|una|dos|tres|cuatro|cinco|medio|media)\s+(?:kilo|kilos|libra|libras|litro|litros|bolsa|bolsas|lata|latas|paquete|paquetes)?)\s*(?:de\s+)?(.*)$""", RegexOption.IGNORE_CASE)

        val match = regex.find(trimmed)
        if (match != null) {
            val qty = match.groupValues[1].trim()
            val name = match.groupValues[2].trim()
            if (name.isNotEmpty()) {
                return Pair(qty, capitalizeFirst(name))
            }
        }
        return Pair("", capitalizeFirst(trimmed))
    }

    /**
     * Classifies an item into one of the 4 categories:
     * MEAT (🥩 CARNICERÍA), VEGGIES (🥬 FERIA / VERDULERÍA), SUPERMARKET (🏪 PALÍ / SUPERMERCADO), OTHERS (📦 VARIOS)
     */
    fun classifyCategory(itemName: String): ShoppingCategory {
        val norm = normalize(itemName)
        val words = norm.split(Regex("""[\s,.-]+""")).filter { it.isNotBlank() }

        // Exact multi-word or full phrase matching first
        for (keyword in MEAT_KEYWORDS) {
            if (norm == keyword || norm.startsWith("$keyword ") || norm.endsWith(" $keyword") || norm.contains(" $keyword ")) {
                return ShoppingCategory.MEAT
            }
        }
        for (keyword in VEGGIES_KEYWORDS) {
            if (norm == keyword || norm.startsWith("$keyword ") || norm.endsWith(" $keyword") || norm.contains(" $keyword ")) {
                return ShoppingCategory.VEGGIES
            }
        }
        for (keyword in SUPERMARKET_KEYWORDS) {
            if (norm == keyword || norm.startsWith("$keyword ") || norm.endsWith(" $keyword") || norm.contains(" $keyword ")) {
                return ShoppingCategory.SUPERMARKET
            }
        }

        // Single word token matching
        for (word in words) {
            if (MEAT_KEYWORDS.contains(word)) return ShoppingCategory.MEAT
            if (VEGGIES_KEYWORDS.contains(word)) return ShoppingCategory.VEGGIES
            if (SUPERMARKET_KEYWORDS.contains(word)) return ShoppingCategory.SUPERMARKET
        }

        // Default to VARIOS as required
        return ShoppingCategory.OTHERS
    }

    /**
     * Splits raw user input (e.g. "Carne, tomates, leche, pollo, frijoles, plátanos, pan, cebolla")
     * into a list of classified ShoppingItems.
     */
    fun parseShoppingInput(text: String): List<ShoppingItem> {
        if (text.isBlank()) return emptyList()

        // Replace separators: newlines, semicolons, and natural Spanish conjunction " y " / " e "
        // But avoid splitting inside words like "mayonesa"
        var processed = text
            .replace("\n", ",")
            .replace(";", ",")
            .replace(Regex("""\s+y\s+""", RegexOption.IGNORE_CASE), ",")
            .replace(Regex("""\s+e\s+""", RegexOption.IGNORE_CASE), ",")

        val tokens = processed.split(",").map { it.trim() }.filter { it.isNotBlank() }

        return tokens.map { token ->
            val (qty, cleanName) = extractQuantityAndName(token)
            val category = classifyCategory(cleanName.ifEmpty { token })
            ShoppingItem(
                name = cleanName.ifEmpty { capitalizeFirst(token) },
                quantity = qty,
                category = category,
                isBought = false
            )
        }
    }

    private fun capitalizeFirst(str: String): String {
        if (str.isEmpty()) return str
        return str.substring(0, 1).uppercase(Locale.getDefault()) + str.substring(1)
    }

    /**
     * Builds clean, beautiful formatted text representation of the shopping list
     * ready to download or send via WhatsApp / SMS / Telegram.
     */
    fun formatListForSharing(items: List<ShoppingItem>): String {
        if (items.isEmpty()) return "🛒 Mi Lista de Compras está vacía."

        val sb = StringBuilder()
        sb.append("🛒 *MI LISTA DE COMPRAS*\n\n")

        val fixedCategories = listOf(
            ShoppingCategory.MEAT,
            ShoppingCategory.VEGGIES,
            ShoppingCategory.SUPERMARKET,
            ShoppingCategory.OTHERS
        )

        for (cat in fixedCategories) {
            val catItems = items.filter { it.category == cat }
            if (catItems.isNotEmpty()) {
                sb.append("${cat.emoji} *${cat.title}*\n")
                catItems.forEach { item ->
                    val check = if (item.isBought) "✅" else "▫️"
                    val qtyText = if (item.quantity.isNotBlank()) "(${item.quantity}) " else ""
                    val boughtTag = if (item.isBought) " ~(Comprado)~" else ""
                    sb.append("$check $qtyText${item.name}$boughtTag\n")
                }
                sb.append("\n")
            }
        }

        val total = items.size
        val bought = items.count { it.isBought }
        sb.append("📊 Resumen: $bought de $total comprados")
        return sb.toString().trim()
    }
}
