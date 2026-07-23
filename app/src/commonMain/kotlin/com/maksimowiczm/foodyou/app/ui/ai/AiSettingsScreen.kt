package com.maksimowiczm.foodyou.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_hide_password
import foodyou.app.generated.resources.action_remove_ai_key
import foodyou.app.generated.resources.action_save
import foodyou.app.generated.resources.action_show_password
import foodyou.app.generated.resources.description_ai_key
import foodyou.app.generated.resources.description_ai_privacy
import foodyou.app.generated.resources.description_ai_status_configured
import foodyou.app.generated.resources.headline_ai_features
import foodyou.app.generated.resources.headline_gemini_api_key
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AiSettingsViewModel = koinViewModel()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val apiKey = rememberTextFieldState()
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    val keyLength by remember { androidx.compose.runtime.derivedStateOf { apiKey.text.length } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_ai_features)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.description_ai_key))
            Text(stringResource(Res.string.description_ai_privacy))
            credentials?.let {
                Text(stringResource(Res.string.description_ai_status_configured, it.suffix))
            }
            SecureTextField(
                state = apiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.headline_gemini_api_key)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textObfuscationMode =
                    if (keyVisible) TextObfuscationMode.Visible
                    else TextObfuscationMode.RevealLastTyped,
                trailingIcon = {
                    IconButton(
                        onClick = { keyVisible = !keyVisible },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector =
                                if (keyVisible) Icons.Outlined.VisibilityOff
                                else Icons.Outlined.Visibility,
                            contentDescription =
                                stringResource(
                                    if (keyVisible) Res.string.action_hide_password
                                    else Res.string.action_show_password
                                ),
                        )
                    }
                },
            )
            Button(
                onClick = {
                    viewModel.save(apiKey.text.toString())
                    apiKey.edit { replace(0, length, "") }
                },
                enabled = keyLength in 20..500,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_save))
            }
            if (credentials != null) {
                OutlinedButton(
                    onClick = viewModel::remove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(stringResource(Res.string.action_remove_ai_key))
                }
            }
        }
    }
}
