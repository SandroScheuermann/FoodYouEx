package com.maksimowiczm.foodyou.ai

import com.maksimowiczm.foodyou.ai.infrastructure.aiInfrastructureModule
import org.koin.dsl.module

val aiModule = module { aiInfrastructureModule() }
