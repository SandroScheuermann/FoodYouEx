package com.maksimowiczm.foodyou.ai.domain.entity

sealed interface AiAnalysisError {
    data object MissingApiKey : AiAnalysisError

    data object ApiKeyRejected : AiAnalysisError

    data object RateLimited : AiAnalysisError

    data object RequestRejected : AiAnalysisError

    data object Network : AiAnalysisError

    data object InvalidImage : AiAnalysisError

    data object InvalidResponse : AiAnalysisError

    data object ProviderUnavailable : AiAnalysisError
}
