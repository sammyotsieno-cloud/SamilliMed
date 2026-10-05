package core.domain.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProductTaxonomyV1Test {
    @Test
    fun defaultTaxonomyContainsTenStableRoots() {
        assertEquals(10, DefaultProductTaxonomyV1.roots.size)
        assertTrue(DefaultProductTaxonomyV1.roots.all { it.id.startsWith("CAT_") })
        assertEquals(10, DefaultProductTaxonomyV1.roots.map { it.id }.toSet().size)
    }
}
