package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import com.maksimowiczm.foodyou.ai.domain.service.MealPhotoEstimator
import com.maksimowiczm.foodyou.ai.domain.service.NutritionLabelExtractor
import com.maksimowiczm.foodyou.ai.infrastructure.gemini.GeminiClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.onClose

internal fun Module.aiInfrastructureModule() {
    val qualifier = named(GeminiClient::class.qualifiedName!!)
    single(qualifier) {
            HttpClient {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }
    singleOf(::DataStoreAiCredentialsRepository).bind<AiCredentialsRepository>()
    single {
            GeminiClient(
                httpClient = get(qualifier),
                credentialsRepository = get(),
                networkConfig = get(),
                logger = get(),
            )
        }
        .binds(arrayOf(MealPhotoEstimator::class, NutritionLabelExtractor::class))
}
