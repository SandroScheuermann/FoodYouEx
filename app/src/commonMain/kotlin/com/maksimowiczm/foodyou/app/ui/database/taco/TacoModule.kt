package com.maksimowiczm.foodyou.app.ui.database.taco

import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.Module

internal fun Module.tacoModule() {
    viewModelOf(::TacoViewModel)
}
