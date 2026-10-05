package core.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRecognitionServiceTest {
    @Test
    fun normalizeMakesComparableIdentityText() {
        assertEquals(
            "amoxicillin 500 mg capsules",
            ProductRecognitionService.normalize("Amoxicillin 500-MG / Capsules")
        )
    }
}
