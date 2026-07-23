package com.maksimowiczm.foodyou.ai.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsState
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DataStoreAiCredentialsRepository(
    private val dataStore: DataStore<Preferences>,
    private val masterCrypto: MasterCrypto,
) : AiCredentialsRepository {
    override suspend fun saveApiKey(apiKey: String) {
        require(apiKey.length in API_KEY_LENGTH)
        val encrypted = masterCrypto.encrypt(apiKey.encodeToByteArray())
        dataStore.edit {
            it[apiKeyPreference] = encrypted
            it[apiKeySuffixPreference] = apiKey.takeLast(4)
        }
    }

    override suspend fun loadApiKey(): String? = decode(dataStore.data.first())?.first

    override suspend fun clearApiKey() {
        dataStore.edit {
            it.remove(apiKeyPreference)
            it.remove(apiKeySuffixPreference)
        }
    }

    override fun observeCredentials(): Flow<AiCredentialsState?> =
        dataStore.data.map { preferences ->
            decode(preferences)?.let { (_, suffix) -> AiCredentialsState(suffix) }
        }

    private suspend fun decode(preferences: Preferences): Pair<String, String>? {
        val encrypted = preferences[apiKeyPreference] ?: return null
        val suffix = preferences[apiKeySuffixPreference] ?: return null
        return try {
            val apiKey = masterCrypto.decrypt(encrypted).decodeToString()
            if (apiKey.length !in API_KEY_LENGTH || !apiKey.endsWith(suffix)) null
            else apiKey to suffix
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val API_KEY_LENGTH = 20..500
        val apiKeyPreference = byteArrayPreferencesKey("ai:gemini:api_key")
        val apiKeySuffixPreference = stringPreferencesKey("ai:gemini:api_key_suffix")
    }
}
