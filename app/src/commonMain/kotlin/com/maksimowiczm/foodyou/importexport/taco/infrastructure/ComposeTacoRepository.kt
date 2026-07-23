package com.maksimowiczm.foodyou.importexport.taco.infrastructure

import com.maksimowiczm.foodyou.app.generated.resources.Res
import com.maksimowiczm.foodyou.importexport.taco.domain.TacoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

internal class ComposeTacoRepository : TacoRepository {
    override suspend fun readCsvFile(): Flow<Byte> =
        Res.readBytes("files/taco/data.csv").toList().asFlow()
}
