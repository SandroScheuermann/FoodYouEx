package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsState
import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class GeminiClientTest {
    @Test
    fun parsesStrictMealResponseAndUsesHeaderCredential() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("test-api-key-that-is-long-enough", request.headers["x-goog-api-key"])
            assertTrue(request.url.encodedPath.endsWith(":generateContent"))
            respond(
                content = MEAL_ENVELOPE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client(engine).estimate(AiImage(byteArrayOf(1, 2, 3), "image/jpeg"))

        val success = assertIs<Result.Success<*, *>>(result)
        val estimate = success.data as com.maksimowiczm.foodyou.ai.domain.entity.MealPhotoEstimate
        assertEquals("Rice and chicken", estimate.label)
        assertEquals(520.0, estimate.calories)
    }

    @Test
    fun mapsRateLimitWithoutExposingProviderBody() = runBlocking {
        val engine = MockEngine { respond("quota", HttpStatusCode.TooManyRequests) }
        val result = client(engine).extract(AiImage(byteArrayOf(1), "image/png"))

        assertEquals(
            AiAnalysisError.RateLimited,
            assertIs<Result.Error<*, *>>(result).error,
        )
    }

    @Test
    fun rejectsUnsupportedImageBeforeNetworkCall() = runBlocking {
        val engine = MockEngine { error("Network should not be called") }
        val result = client(engine).extract(AiImage(byteArrayOf(1), "image/gif"))

        assertEquals(
            AiAnalysisError.InvalidImage,
            assertIs<Result.Error<*, *>>(result).error,
        )
    }

    private fun client(engine: MockEngine) =
        GeminiClient(
            httpClient =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                },
            credentialsRepository = Credentials,
            networkConfig = object : NetworkConfig { override val userAgent = "FoodYou/Test" },
            logger = NoOpLogger,
        )

    private object Credentials : AiCredentialsRepository {
        override suspend fun saveApiKey(apiKey: String) = Unit
        override suspend fun loadApiKey() = "test-api-key-that-is-long-enough"
        override suspend fun clearApiKey() = Unit
        override fun observeCredentials(): Flow<AiCredentialsState?> = flowOf(null)
    }

    private object NoOpLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private companion object {
        const val MEAL_ENVELOPE =
            """{"candidates":[{"content":{"parts":[{"text":"{\"label\":\"Rice and chicken\",\"calories\":520,\"protein\":42,\"carbs\":55,\"fat\":14,\"estimatedWeightGrams\":410,\"components\":[\"rice\",\"chicken\"],\"confidence\":\"high\",\"warnings\":[]}"}]}}]}"""
    }
}
