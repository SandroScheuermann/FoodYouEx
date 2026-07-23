package com.maksimowiczm.foodyou.app.ui.food.product.nutritionlabel

import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelBasis
import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelExtraction
import kotlin.math.round

internal data class NormalizedNutritionLabel(
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val energy: Double?,
)

internal fun NutritionLabelExtraction.normalizePer100g(): NormalizedNutritionLabel? {
    val multiplier =
        when (basis) {
            NutritionLabelBasis.Per100g -> 1.0
            NutritionLabelBasis.PerServing -> {
                val serving = servingGrams ?: return null
                if (!serving.isFinite() || serving <= 0) return null
                100.0 / serving
            }
            NutritionLabelBasis.Unknown -> return null
        }
    return NormalizedNutritionLabel(
        protein = protein.normalize(multiplier),
        carbs = carbs.normalize(multiplier),
        fat = fat.normalize(multiplier),
        energy = energy.normalize(multiplier),
    )
}

private fun Double?.normalize(multiplier: Double): Double? =
    this?.let { round(it * multiplier * 1_000) / 1_000 }
