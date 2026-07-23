package com.maksimowiczm.foodyou.ai.domain.repository

import kotlinx.coroutines.flow.Flow

data class AiCredentialsState(val suffix: String)

interface AiCredentialsRepository {
    suspend fun saveApiKey(apiKey: String)

    suspend fun loadApiKey(): String?

    suspend fun clearApiKey()

    fun observeCredentials(): Flow<AiCredentialsState?>
}
