package com.maksimowiczm.foodyou.app.ui.food.diary.mealphoto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.entity.MealPhotoEstimate
import com.maksimowiczm.foodyou.ai.domain.service.MealPhotoEstimator
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal sealed interface MealPhotoUiState {
    data object Idle : MealPhotoUiState

    data class ImageSelected(val image: AiImage) : MealPhotoUiState

    data class Analyzing(val image: AiImage) : MealPhotoUiState

    data class Review(val id: Long, val image: AiImage, val estimate: MealPhotoEstimate) :
        MealPhotoUiState

    data class Error(val image: AiImage?, val error: AiAnalysisError) : MealPhotoUiState
}

internal class MealPhotoViewModel(
    private val date: LocalDate,
    private val mealId: Long,
    private val estimator: MealPhotoEstimator,
    private val manualDiaryEntryRepository: ManualDiaryEntryRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MealPhotoUiState>(MealPhotoUiState.Idle)
    val state: StateFlow<MealPhotoUiState> = mutableState.asStateFlow()

    private val savedChannel = Channel<Unit>()
    val saved = savedChannel.receiveAsFlow()
    private var analysisJob: Job? = null
    private var requestId = 0L

    fun selectImage(image: AiImage) {
        analysisJob?.cancel()
        requestId++
        mutableState.value = MealPhotoUiState.ImageSelected(image)
    }

    fun imageError() {
        mutableState.value = MealPhotoUiState.Error(currentImage(), AiAnalysisError.InvalidImage)
    }

    fun analyze(knownWeightGrams: Double?) {
        val image = currentImage() ?: return
        analysisJob?.cancel()
        val id = ++requestId
        analysisJob =
            viewModelScope.launch {
                mutableState.value = MealPhotoUiState.Analyzing(image)
                when (val result = estimator.estimate(image, knownWeightGrams)) {
                    is Result.Success ->
                        if (id == requestId) {
                            mutableState.value = MealPhotoUiState.Review(id, image, result.data)
                        }
                    is Result.Error ->
                        if (id == requestId) {
                            mutableState.value = MealPhotoUiState.Error(image, result.error)
                        }
                }
            }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        requestId++
        currentImage()?.let { mutableState.value = MealPhotoUiState.ImageSelected(it) }
    }

    fun save(
        name: String,
        energy: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
    ) {
        viewModelScope.launch {
            manualDiaryEntryRepository.insert(
                name = name,
                mealId = mealId,
                date = date,
                nutritionFacts =
                    NutritionFacts(
                        energy = energy.toNutrientValue(),
                        proteins = proteins.toNutrientValue(),
                        carbohydrates = carbohydrates.toNutrientValue(),
                        fats = fats.toNutrientValue(),
                    ),
                createdAt = dateProvider.now(),
            )
            savedChannel.send(Unit)
        }
    }

    private fun currentImage(): AiImage? =
        when (val value = mutableState.value) {
            MealPhotoUiState.Idle -> null
            is MealPhotoUiState.ImageSelected -> value.image
            is MealPhotoUiState.Analyzing -> value.image
            is MealPhotoUiState.Review -> value.image
            is MealPhotoUiState.Error -> value.image
        }
}
