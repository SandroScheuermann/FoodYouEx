package com.maksimowiczm.foodyou.ai.domain.service

import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.entity.MealPhotoEstimate
import com.maksimowiczm.foodyou.common.result.Result

interface MealPhotoEstimator {
    suspend fun estimate(
        image: AiImage,
        knownWeightGrams: Double? = null,
    ): Result<MealPhotoEstimate, AiAnalysisError>
}
