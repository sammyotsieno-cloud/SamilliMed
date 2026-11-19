package org.samillimed.app.data.catalog

/** What kind of billable item a ServiceItem is. Lab tests are registered, listed and billed like services. */
object ServiceKind {
    const val SERVICE = "SERVICE"
    const val LAB = "LAB"
}

val ServiceItem.isLab: Boolean get() = kind == ServiceKind.LAB

private fun Product.ingredientCount(): Int = ingredients.count { it.genericName.isNotBlank() }

/**
 * Orders products for search suggestions: single-ingredient medicines first, then combinations,
 * then everything else. Within each group the best match comes first (a name or generic name that
 * starts with the search, then a word that starts with it, then any other match), then A to Z.
 */
fun rankedForSearch(products: List<Product>, query: String): List<Product> {
    val q = query.trim().lowercase()
    fun group(p: Product): Int = when (p.ingredientCount()) {
        1 -> 0
        0 -> 2
        else -> 1
    }
    fun startsWord(text: String): Boolean =
        text.split(' ', '/', '-', '+', ',').any { it.startsWith(q) }
    fun match(p: Product): Int {
        if (q.isEmpty()) return 0
        val name = p.name.lowercase()
        val generics = p.genericNames.lowercase()
        return when {
            name.startsWith(q) || generics.startsWith(q) -> 0
            startsWord(name) || startsWord(generics) -> 1
            else -> 2
        }
    }
    return products.sortedWith(
        compareBy<Product>({ group(it) }, { match(it) }, { it.name.lowercase() }),
    )
}
