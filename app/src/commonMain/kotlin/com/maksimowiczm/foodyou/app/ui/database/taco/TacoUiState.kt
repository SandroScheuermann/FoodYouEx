package com.maksimowiczm.foodyou.app.ui.database.taco

internal sealed interface TacoUiState {
    data object Ready : TacoUiState

    data class Importing(val progress: Float) : TacoUiState

    data object Finished : TacoUiState
}
