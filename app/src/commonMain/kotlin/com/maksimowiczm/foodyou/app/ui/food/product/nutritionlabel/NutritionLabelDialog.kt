package com.maksimowiczm.foodyou.app.ui.food.product.nutritionlabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.image.AiImagePreview
import com.maksimowiczm.foodyou.app.ui.common.image.rememberImageAcquisitionActions
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun NutritionLabelDialog(
    onDismissRequest: () -> Unit,
    onApply: (NormalizedNutritionLabel) -> Unit,
    hasAiCredentials: Boolean,
    onConfigureAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NutritionLabelViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val acquisition = rememberImageAcquisitionActions(
        onImage = viewModel::selectImage,
        onError = { viewModel.imageError() },
    )
    val dismiss = {
        viewModel.reset()
        onDismissRequest()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.headline_nutrition_label_photo)) },
        confirmButton = {
            val review = state as? NutritionLabelUiState.Review
            if (review != null) {
                TextButton(onClick = { onApply(review.normalized); dismiss() }) {
                    Text(stringResource(Res.string.action_apply_values))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.description_nutrition_label_framing))
                Text(
                    stringResource(Res.string.description_ai_image_upload),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (hasAiCredentials) acquisition.takePhoto() else onConfigureAi()
                        },
                    ) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                        Text(stringResource(Res.string.action_take_photo))
                    }
                    OutlinedButton(
                        onClick = {
                            if (hasAiCredentials) acquisition.pickImage() else onConfigureAi()
                        },
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Text(stringResource(Res.string.action_choose_photo))
                    }
                }
                val image = when (val value = state) {
                    NutritionLabelUiState.Idle -> null
                    is NutritionLabelUiState.ImageSelected -> value.image
                    is NutritionLabelUiState.Analyzing -> value.image
                    is NutritionLabelUiState.Review -> value.image
                    is NutritionLabelUiState.Error -> value.image
                }
                image?.let { AiImagePreview(it, null, Modifier.fillMaxWidth().height(180.dp)) }
                when (val value = state) {
                    NutritionLabelUiState.Idle -> Unit
                    is NutritionLabelUiState.ImageSelected ->
                        Button(onClick = viewModel::analyze, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.action_analyze_label))
                        }
                    is NutritionLabelUiState.Analyzing -> LoadingIndicator()
                    is NutritionLabelUiState.Error -> {
                        Text(
                            stringResource(Res.string.description_ai_error_generic),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = viewModel::analyze, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.action_analyze_label))
                        }
                    }
                    is NutritionLabelUiState.Review ->
                        if (value.warnings.isNotEmpty()) {
                            Text(
                                stringResource(Res.string.label_warnings) + ": " +
                                    value.warnings.joinToString("\n")
                            )
                        }
                }
            }
        },
    )
}
