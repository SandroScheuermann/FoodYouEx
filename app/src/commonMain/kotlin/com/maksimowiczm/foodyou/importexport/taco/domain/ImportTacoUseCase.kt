package com.maksimowiczm.foodyou.importexport.taco.domain

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.importexport.domain.entity.ProductField
import com.maksimowiczm.foodyou.importexport.domain.usecase.ImportCsvProductUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface ImportTacoUseCase {
    suspend fun import(): Flow<Int>
}

internal class ImportTacoUseCaseImpl(
    private val tacoRepository: TacoRepository,
    private val importCsvProductUseCase: ImportCsvProductUseCase,
) : ImportTacoUseCase {
    override suspend fun import(): Flow<Int> = flow {
        var count = 0
        importCsvProductUseCase
            .import(
                mapper = order,
                stream = tacoRepository.readCsvFile(),
                source = FoodSource.Type.Taco,
                skipHeader = true,
            )
            .collect { emit(count++) }
    }

    private val order =
        listOf(
            ProductField.Name,
            ProductField.Brand,
            ProductField.Barcode,
            ProductField.Proteins,
            ProductField.Carbohydrates,
            ProductField.Fats,
            ProductField.Energy,
            ProductField.SaturatedFats,
            ProductField.TransFats,
            ProductField.MonounsaturatedFats,
            ProductField.PolyunsaturatedFats,
            ProductField.Omega3,
            ProductField.Omega6,
            ProductField.Sugars,
            ProductField.Salt,
            ProductField.DietaryFiber,
            ProductField.Cholesterol,
            ProductField.Caffeine,
            ProductField.VitaminA,
            ProductField.VitaminB1,
            ProductField.VitaminB2,
            ProductField.VitaminB3,
            ProductField.VitaminB5,
            ProductField.VitaminB6,
            ProductField.VitaminB7,
            ProductField.VitaminB9,
            ProductField.VitaminB12,
            ProductField.VitaminC,
            ProductField.VitaminD,
            ProductField.VitaminE,
            ProductField.VitaminK,
            ProductField.Manganese,
            ProductField.Magnesium,
            ProductField.Potassium,
            ProductField.Calcium,
            ProductField.Copper,
            ProductField.Zinc,
            ProductField.Sodium,
            ProductField.Iron,
            ProductField.Phosphorus,
            ProductField.Selenium,
            ProductField.Iodine,
            ProductField.PackageWeight,
            ProductField.ServingWeight,
        )
}
