package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiConfidence
import com.maksimowiczm.foodyou.ai.domain.entity.AiImage
import com.maksimowiczm.foodyou.ai.domain.entity.MealPhotoEstimate
import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelBasis
import com.maksimowiczm.foodyou.ai.domain.entity.NutritionLabelExtraction
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import com.maksimowiczm.foodyou.ai.domain.service.MealPhotoEstimator
import com.maksimowiczm.foodyou.ai.domain.service.NutritionLabelExtractor
import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.userAgent
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class GeminiClient(
    private val httpClient: HttpClient,
    private val credentialsRepository: AiCredentialsRepository,
    private val networkConfig: NetworkConfig,
    private val logger: Logger,
) : MealPhotoEstimator, NutritionLabelExtractor {
    override suspend fun estimate(
        image: AiImage,
        knownWeightGrams: Double?,
    ): Result<MealPhotoEstimate, AiAnalysisError> {
        if (knownWeightGrams != null && (!knownWeightGrams.isFinite() || knownWeightGrams <= 0)) {
            return Err(AiAnalysisError.RequestRejected)
        }
        val prompt =
            MEAL_PROMPT +
                if (knownWeightGrams == null) "\nO peso total nao foi informado."
                else "\nO peso total conhecido e $knownWeightGrams g e deve ser a referencia principal."
        return generate(image, prompt, mealSchema) { text ->
            val response = contractJson.decodeFromString<MealResponse>(text)
            response.toDomain()
        }
    }

    override suspend fun extract(
        image: AiImage
    ): Result<NutritionLabelExtraction, AiAnalysisError> =
        generate(image, LABEL_PROMPT, labelSchema) { text ->
            contractJson.decodeFromString<LabelResponse>(text).toDomain()
        }

    private suspend fun <T> generate(
        image: AiImage,
        prompt: String,
        schema: JsonElement,
        parse: (String) -> T?,
    ): Result<T, AiAnalysisError> {
        if (image.bytes.isEmpty() || image.bytes.size > MAX_IMAGE_BYTES || image.mimeType !in MIMES) {
            return Err(AiAnalysisError.InvalidImage)
        }
        val apiKey = credentialsRepository.loadApiKey() ?: return Err(AiAnalysisError.MissingApiKey)
        val response =
            try {
                httpClient.post("$API_URL/models/$MODEL:generateContent") {
                    header("x-goog-api-key", apiKey)
                    userAgent(networkConfig.userAgent)
                    contentType(ContentType.Application.Json)
                    setBody(
                        GenerateRequest(
                            contents =
                                listOf(
                                    Content(
                                        parts =
                                            listOf(
                                                Part(text = prompt),
                                                Part(
                                                    inlineData =
                                                        InlineData(
                                                            mimeType = image.mimeType,
                                                            data = Base64.Default.encode(image.bytes),
                                                        )
                                                ),
                                            )
                                    )
                                ),
                            generationConfig =
                                GenerationConfig(
                                    responseMimeType = "application/json",
                                    responseJsonSchema = schema,
                                ),
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                logger.w(TAG) { "Gemini request failed: ${error::class.simpleName}" }
                return Err(AiAnalysisError.Network)
            }

        if (response.status != HttpStatusCode.OK) return Err(response.status.toAnalysisError())

        return try {
            val envelope = providerJson.decodeFromString<GenerateResponse>(response.bodyAsText())
            if (envelope.promptFeedback?.blockReason != null) {
                Err(AiAnalysisError.RequestRejected)
            } else {
                val text =
                    envelope.candidates
                        .asSequence()
                        .flatMap { it.content?.parts.orEmpty().asSequence() }
                        .mapNotNull { it.text?.takeIf(String::isNotBlank) }
                        .firstOrNull()
                val value = text?.let(parse)
                if (value == null) Err(AiAnalysisError.InvalidResponse) else Ok(value)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.w(TAG) { "Gemini response was invalid: ${error::class.simpleName}" }
            Err(AiAnalysisError.InvalidResponse)
        }
    }

    private fun HttpStatusCode.toAnalysisError(): AiAnalysisError =
        when (value) {
            401, 403 -> AiAnalysisError.ApiKeyRejected
            429 -> AiAnalysisError.RateLimited
            404, in 500..599 -> AiAnalysisError.ProviderUnavailable
            408 -> AiAnalysisError.Network
            else -> AiAnalysisError.RequestRejected
        }

    private fun MealResponse.toDomain(): MealPhotoEstimate? {
        val normalizedLabel = label.trim()
        if (normalizedLabel.isEmpty() || normalizedLabel.length > 100) return null
        if (!calories.validMacro() || !protein.validMacro() || !carbs.validMacro() || !fat.validMacro()) return null
        if (estimatedWeightGrams != null && (!estimatedWeightGrams.isFinite() || estimatedWeightGrams <= 0)) return null
        val normalizedComponents = components.normalizeStrings(12, 100) ?: return null
        val normalizedWarnings = warnings.normalizeStrings(8, 200) ?: return null
        return MealPhotoEstimate(
            label = normalizedLabel,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            estimatedWeightGrams = estimatedWeightGrams,
            components = normalizedComponents,
            confidence =
                when (confidence) {
                    "low" -> AiConfidence.Low
                    "medium" -> AiConfidence.Medium
                    "high" -> AiConfidence.High
                    else -> return null
                },
            warnings = normalizedWarnings,
        )
    }

    private fun LabelResponse.toDomain(): NutritionLabelExtraction? {
        if (!servingGrams.validOptionalPositive()) return null
        if (!protein.validOptionalMacro() || !carbs.validOptionalMacro() || !fat.validOptionalMacro() || !energy.validOptionalMacro()) return null
        val normalizedWarnings = warnings.normalizeStrings(6, 200) ?: return null
        return NutritionLabelExtraction(
            basis =
                when (basis) {
                    "per_100g" -> NutritionLabelBasis.Per100g
                    "per_serving" -> NutritionLabelBasis.PerServing
                    "unknown" -> NutritionLabelBasis.Unknown
                    else -> return null
                },
            servingGrams = servingGrams,
            protein = protein,
            carbs = carbs,
            fat = fat,
            energy = energy,
            warnings = normalizedWarnings,
        )
    }

    private fun Double.validMacro() = isFinite() && this in 0.0..99_999.0

    private fun Double?.validOptionalMacro() = this == null || validMacro()

    private fun Double?.validOptionalPositive() = this == null || (isFinite() && this > 0)

    private fun List<String>.normalizeStrings(maxCount: Int, maxLength: Int): List<String>? {
        if (size > maxCount) return null
        return map {
            val normalized = it.trim()
            if (normalized.isEmpty() || normalized.length > maxLength) return null
            normalized
        }
    }

    @Serializable private data class GenerateRequest(val contents: List<Content>, val generationConfig: GenerationConfig)

    @Serializable private data class Content(val parts: List<Part>)

    @Serializable private data class Part(val text: String? = null, val inlineData: InlineData? = null)

    @Serializable private data class InlineData(val mimeType: String, val data: String)

    @Serializable private data class GenerationConfig(val responseMimeType: String, val responseJsonSchema: JsonElement)

    @Serializable private data class GenerateResponse(val candidates: List<Candidate> = emptyList(), val promptFeedback: PromptFeedback? = null)

    @Serializable private data class Candidate(val content: CandidateContent? = null)

    @Serializable private data class CandidateContent(val parts: List<CandidatePart> = emptyList())

    @Serializable private data class CandidatePart(val text: String? = null)

    @Serializable private data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    private data class MealResponse(
        val label: String,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val estimatedWeightGrams: Double?,
        val components: List<String>,
        val confidence: String,
        val warnings: List<String>,
    )

    @Serializable
    private data class LabelResponse(
        val basis: String,
        val servingGrams: Double?,
        val protein: Double?,
        val carbs: Double?,
        val fat: Double?,
        val energy: Double?,
        val warnings: List<String>,
    )

    private companion object {
        const val API_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MODEL = "gemini-3.1-flash-lite"
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val TAG = "GeminiClient"
        val MIMES = setOf("image/jpeg", "image/png", "image/webp")
        val providerJson = Json { ignoreUnknownKeys = true }
        val contractJson = Json { ignoreUnknownKeys = false }

        const val MEAL_PROMPT =
            "Analise a refeicao fotografada e estime os totais da porcao inteira, nunca por 100 g. " +
                "Considere oleos, molhos, recheios e ingredientes ocultos. Evite precisao inventada, " +
                "reduza a confianca e gere alertas quando houver incerteza."
        const val LABEL_PROMPT =
            "Leia esta tabela nutricional brasileira. Extraia energia em kcal, proteina, carboidratos " +
                "e gorduras totais na mesma base. Priorize 100 g, nunca use %VD nem confunda gorduras " +
                "totais com saturadas ou trans. Preserve decimais e retorne null quando ilegivel."

        val mealSchema =
            objectSchema(
                required = listOf("label", "calories", "protein", "carbs", "fat", "estimatedWeightGrams", "components", "confidence", "warnings"),
                properties =
                    mapOf(
                        "label" to stringSchema(100),
                        "calories" to numberSchema(),
                        "protein" to numberSchema(),
                        "carbs" to numberSchema(),
                        "fat" to numberSchema(),
                        "estimatedWeightGrams" to nullableNumberSchema(),
                        "components" to stringArraySchema(12, 100),
                        "confidence" to enumSchema(listOf("low", "medium", "high")),
                        "warnings" to stringArraySchema(8, 200),
                    ),
            )
        val labelSchema =
            objectSchema(
                required = listOf("basis", "servingGrams", "protein", "carbs", "fat", "energy", "warnings"),
                properties =
                    mapOf(
                        "basis" to enumSchema(listOf("per_100g", "per_serving", "unknown")),
                        "servingGrams" to nullableNumberSchema(),
                        "protein" to nullableNumberSchema(),
                        "carbs" to nullableNumberSchema(),
                        "fat" to nullableNumberSchema(),
                        "energy" to nullableNumberSchema(),
                        "warnings" to stringArraySchema(6, 200),
                    ),
            )

        fun objectSchema(required: List<String>, properties: Map<String, JsonElement>) =
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                put("properties", buildJsonObject { properties.forEach { (key, value) -> put(key, value) } })
            }

        fun stringSchema(maxLength: Int) = buildJsonObject { put("type", "string"); put("maxLength", maxLength) }
        fun numberSchema() = buildJsonObject { put("type", "number"); put("minimum", 0); put("maximum", 99_999) }
        fun nullableNumberSchema() = buildJsonObject { put("type", buildJsonArray { add(JsonPrimitive("number")); add(JsonPrimitive("null")) }); put("minimum", 0) }
        fun enumSchema(values: List<String>) = buildJsonObject { put("type", "string"); put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) }
        fun stringArraySchema(maxItems: Int, maxLength: Int) =
            buildJsonObject {
                put("type", "array")
                put("maxItems", maxItems)
                put("items", stringSchema(maxLength))
            }
    }
}
