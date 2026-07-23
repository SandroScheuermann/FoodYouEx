package com.maksimowiczm.foodyou.app.ui.ai

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

internal fun Module.aiUi() {
    viewModelOf(::AiSettingsViewModel)
}
