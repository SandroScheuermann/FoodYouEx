package com.maksimowiczm.foodyou.app.ui.database.taco

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.importexport.taco.domain.ImportTacoUseCase
import com.maksimowiczm.foodyou.importexport.taco.domain.TacoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TacoViewModel(private val importTacoUseCase: ImportTacoUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<TacoUiState>(TacoUiState.Ready)
    val uiState = _uiState.asStateFlow()

    private val mutex = Mutex()

    fun import() {
        if (mutex.isLocked) return

        viewModelScope.launch {
            mutex.withLock {
                _uiState.value = TacoUiState.Importing(0f)
                importTacoUseCase.import().collectLatest { count ->
                    _uiState.value =
                        TacoUiState.Importing(
                            progress = (count + 1).toFloat() / TacoRepository.FOOD_COUNT
                        )
                }
                delay(200)
                _uiState.value = TacoUiState.Finished
            }
        }
    }
}
