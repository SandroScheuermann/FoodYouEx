package com.maksimowiczm.foodyou.app.ui.food.product.nutritionlabel

import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelBasis
import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelExtraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NormalizeNutritionLabelTest {
    @Test
    fun keepsValuesAlreadyBasedOn100Grams() {
        val normalized = extraction(NutritionLabelBasis.Per100g).normalizePer100g()

        assertEquals(10.0, normalized?.protein)
        assertEquals(20.0, normalized?.carbs)
        assertEquals(5.0, normalized?.fat)
        assertEquals(165.0, normalized?.energy)
    }

    @Test
    fun normalizesServingValuesAndPreservesNulls() {
        val normalized =
            extraction(NutritionLabelBasis.PerServing, servingGrams = 40.0)
                .copy(fat = null)
                .normalizePer100g()

        assertEquals(25.0, normalized?.protein)
        assertEquals(50.0, normalized?.carbs)
        assertNull(normalized?.fat)
        assertEquals(412.5, normalized?.energy)
    }

    @Test
    fun rejectsUnknownBasisOrServingWithoutWeight() {
        assertNull(extraction(NutritionLabelBasis.Unknown).normalizePer100g())
        assertNull(
            extraction(NutritionLabelBasis.PerServing, servingGrams = null).normalizePer100g()
        )
    }

    private fun extraction(
        basis: NutritionLabelBasis,
        servingGrams: Double? = 100.0,
    ) =
        NutritionLabelExtraction(
            basis = basis,
            servingGrams = servingGrams,
            protein = 10.0,
            carbs = 20.0,
            fat = 5.0,
            energy = 165.0,
            warnings = emptyList(),
        )
}
