package com.maksimowiczm.foodyou.ai.domain.entity

data class MealPhotoEstimate(
    val label: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val estimatedWeightGrams: Double?,
    val components: List<String>,
    val confidence: AiConfidence,
    val warnings: List<String>,
)

enum class AiConfidence {
    Low,
    Medium,
    High,
}
