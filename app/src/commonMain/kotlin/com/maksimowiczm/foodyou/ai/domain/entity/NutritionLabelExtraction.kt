package com.maksimowiczm.foodyou.ai.domain.entity

data class NutritionLabelExtraction(
    val basis: NutritionLabelBasis,
    val servingGrams: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val energy: Double?,
    val warnings: List<String>,
)

enum class NutritionLabelBasis {
    Per100g,
    PerServing,
    Unknown,
}
