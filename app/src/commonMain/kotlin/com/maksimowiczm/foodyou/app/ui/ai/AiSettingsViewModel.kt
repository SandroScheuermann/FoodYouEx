package com.maksimowiczm.foodyou.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AiSettingsViewModel(
    private val credentialsRepository: AiCredentialsRepository
) : ViewModel() {
    val credentials: StateFlow<AiCredentialsState?> =
        credentialsRepository
            .observeCredentials()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(apiKey: String) {
        viewModelScope.launch { credentialsRepository.saveApiKey(apiKey) }
    }

    fun remove() {
        viewModelScope.launch { credentialsRepository.clearApiKey() }
    }
}
