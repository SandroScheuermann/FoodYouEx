package com.maksimowiczm.foodyou.app.ui.food.product.nutritionlabel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.service.NutritionLabelExtractor
import com.maksimowiczm.foodyou.common.result.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface NutritionLabelUiState {
    data object Idle : NutritionLabelUiState
    data class ImageSelected(val image: AiImage) : NutritionLabelUiState
    data class Analyzing(val image: AiImage) : NutritionLabelUiState
    data class Review(
        val image: AiImage,
        val normalized: NormalizedNutritionLabel,
        val warnings: List<String>,
    ) : NutritionLabelUiState
    data class Error(val image: AiImage?, val error: AiAnalysisError) : NutritionLabelUiState
}

internal class NutritionLabelViewModel(private val extractor: NutritionLabelExtractor) : ViewModel() {
    private val mutableState = MutableStateFlow<NutritionLabelUiState>(NutritionLabelUiState.Idle)
    val state: StateFlow<NutritionLabelUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var requestId = 0L

    fun selectImage(image: AiImage) {
        job?.cancel()
        requestId++
        mutableState.value = NutritionLabelUiState.ImageSelected(image)
    }

    fun imageError() {
        mutableState.value = NutritionLabelUiState.Error(currentImage(), AiAnalysisError.InvalidImage)
    }

    fun analyze() {
        val image = currentImage() ?: return
        job?.cancel()
        val id = ++requestId
        job = viewModelScope.launch {
            mutableState.value = NutritionLabelUiState.Analyzing(image)
            when (val result = extractor.extract(image)) {
                is Result.Success -> {
                    val normalized = result.data.normalizePer100g()
                    if (id == requestId) {
                        mutableState.value =
                            if (normalized == null) {
                                NutritionLabelUiState.Error(image, AiAnalysisError.InvalidResponse)
                            } else {
                                NutritionLabelUiState.Review(
                                    image,
                                    normalized,
                                    result.data.warnings,
                                )
                            }
                    }
                }
                is Result.Error ->
                    if (id == requestId) {
                        mutableState.value = NutritionLabelUiState.Error(image, result.error)
                    }
            }
        }
    }

    fun reset() {
        job?.cancel()
        requestId++
        mutableState.value = NutritionLabelUiState.Idle
    }

    private fun currentImage(): AiImage? =
        when (val value = mutableState.value) {
            NutritionLabelUiState.Idle -> null
            is NutritionLabelUiState.ImageSelected -> value.image
            is NutritionLabelUiState.Analyzing -> value.image
            is NutritionLabelUiState.Review -> value.image
            is NutritionLabelUiState.Error -> value.image
        }
}
