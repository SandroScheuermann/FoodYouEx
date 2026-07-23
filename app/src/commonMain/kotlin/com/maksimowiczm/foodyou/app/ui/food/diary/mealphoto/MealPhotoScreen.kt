package com.maksimowiczm.foodyou.app.ui.food.diary.mealphoto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.entity.AiAnalysisError
import com.maksimowiczm.foodyou.ai.domain.entity.AiConfidence
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.app.ui.common.image.AiImagePreview
import com.maksimowiczm.foodyou.app.ui.common.image.rememberImageAcquisitionActions
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.QuickAddForm
import com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.rememberQuickAddFormState
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_analyze_meal
import foodyou.app.generated.resources.action_cancel
import foodyou.app.generated.resources.action_choose_photo
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.action_take_photo
import foodyou.app.generated.resources.description_ai_error_generic
import foodyou.app.generated.resources.description_ai_image_upload
import foodyou.app.generated.resources.description_meal_photo_framing
import foodyou.app.generated.resources.description_total_weight_optional
import foodyou.app.generated.resources.headline_meal_photo
import foodyou.app.generated.resources.label_components
import foodyou.app.generated.resources.label_confidence
import foodyou.app.generated.resources.label_warnings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlinx.datetime.LocalDate

@Composable
fun MealPhotoScreen(
    date: LocalDate,
    mealId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MealPhotoViewModel = koinViewModel { parametersOf(date, mealId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weight = rememberTextFieldState()
    val acquisition =
        rememberImageAcquisitionActions(
            onImage = viewModel::selectImage,
            onError = { viewModel.imageError() },
        )
    LaunchedCollectWithLifecycle(viewModel.saved) { onSaved() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_meal_photo)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(Res.string.description_meal_photo_framing))
                    Text(
                        stringResource(Res.string.description_ai_image_upload),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = acquisition.takePhoto) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Text(stringResource(Res.string.action_take_photo))
                        }
                        OutlinedButton(onClick = acquisition.pickImage) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Text(stringResource(Res.string.action_choose_photo))
                        }
                    }
                    val image =
                        when (val value = state) {
                            MealPhotoUiState.Idle -> null
                            is MealPhotoUiState.ImageSelected -> value.image
                            is MealPhotoUiState.Analyzing -> value.image
                            is MealPhotoUiState.Review -> value.image
                            is MealPhotoUiState.Error -> value.image
                        }
                    image?.let {
                        AiImagePreview(
                            image = it,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                    when (val value = state) {
                        MealPhotoUiState.Idle -> Unit
                        is MealPhotoUiState.ImageSelected,
                        is MealPhotoUiState.Error -> {
                            if (value is MealPhotoUiState.Error) {
                                Text(
                                    text = value.error.userMessage(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            OutlinedTextField(
                                state = weight,
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        stringResource(
                                            Res.string.description_total_weight_optional
                                        )
                                    )
                                },
                                lineLimits = TextFieldLineLimits.SingleLine,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Button(
                                onClick = {
                                    val knownWeight =
                                        weight.text.toString().replace(',', '.').toDoubleOrNull()
                                            ?.takeIf { it.isFinite() && it > 0 }
                                    viewModel.analyze(knownWeight)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.action_analyze_meal))
                            }
                        }
                        is MealPhotoUiState.Analyzing -> {
                            LoadingIndicator()
                            OutlinedButton(
                                onClick = viewModel::cancelAnalysis,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.action_cancel))
                            }
                        }
                        is MealPhotoUiState.Review ->
                            key(value.id) {
                                val estimate = value.estimate
                                val form =
                                    rememberQuickAddFormState(
                                        name = estimate.label,
                                        proteins = estimate.protein,
                                        carbohydrates = estimate.carbs,
                                        fats = estimate.fat,
                                        energy = estimate.calories,
                                        autoCalculateEnergy = false,
                                    )
                                Text(
                                    stringResource(Res.string.label_confidence) +
                                        ": " +
                                        estimate.confidence.name.lowercase()
                                )
                                if (estimate.components.isNotEmpty()) {
                                    Text(
                                        stringResource(Res.string.label_components) +
                                            ": " +
                                            estimate.components.joinToString()
                                    )
                                }
                                if (estimate.warnings.isNotEmpty()) {
                                    Text(
                                        stringResource(Res.string.label_warnings) +
                                            ": " +
                                            estimate.warnings.joinToString("\n")
                                    )
                                }
                                QuickAddForm(form)
                                val energyFormatter = LocalEnergyFormatter.current
                                Button(
                                    onClick = {
                                        viewModel.save(
                                            name = form.name.value,
                                            energy =
                                                form.energy.value?.let(energyFormatter::toKcal)
                                                    ?: 0.0,
                                            proteins = form.proteins.value ?: 0.0,
                                            carbohydrates = form.carbohydrates.value ?: 0.0,
                                            fats = form.fats.value ?: 0.0,
                                        )
                                    },
                                    enabled = form.isValid,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(Res.string.action_save))
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisError.userMessage(): String =
    stringResource(Res.string.description_ai_error_generic)
