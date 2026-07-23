package com.maksimowiczm.foodyou.importexport.taco.domain

import kotlinx.coroutines.flow.Flow

interface TacoRepository {
    companion object {
        const val FOOD_COUNT = 597
    }

    suspend fun readCsvFile(): Flow<Byte>
}
