package com.maksimowiczm.foodyou.ai.domain.service

import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelExtraction
import com.maksimowiczm.foodyou.common.result.Result

interface NutritionLabelExtractor {
    suspend fun extract(image: AiImage): Result<NutritionLabelExtraction, AiAnalysisError>
}
