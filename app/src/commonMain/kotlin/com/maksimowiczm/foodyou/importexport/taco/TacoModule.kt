package com.maksimowiczm.foodyou.importexport.taco

import com.maksimowiczm.foodyou.importexport.taco.domain.ImportTacoUseCase
import com.maksimowiczm.foodyou.importexport.taco.domain.ImportTacoUseCaseImpl
import com.maksimowiczm.foodyou.importexport.taco.domain.TacoRepository
import com.maksimowiczm.foodyou.importexport.taco.infrastructure.ComposeTacoRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val importExportTacoModule = module {
    factoryOf(::ImportTacoUseCaseImpl).bind<ImportTacoUseCase>()
    factoryOf(::ComposeTacoRepository).bind<TacoRepository>()
}
