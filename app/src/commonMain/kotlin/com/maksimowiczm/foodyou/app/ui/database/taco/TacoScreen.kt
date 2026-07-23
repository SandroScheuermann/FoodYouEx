package com.maksimowiczm.foodyou.app.ui.database.taco

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import foodyou.app.generated.resources.Res
import foodyou.app.generated.resources.action_done
import foodyou.app.generated.resources.action_import
import foodyou.app.generated.resources.description2_taco
import foodyou.app.generated.resources.headline_taco
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TacoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TacoViewModel = koinViewModel()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_taco)) },
                navigationIcon = {
                    ArrowBackIconButton(
                        onClick = onBack,
                        enabled = uiState !is TacoUiState.Importing,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                TacoUiState.Ready ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.description2_taco),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = viewModel::import) {
                            Text(stringResource(Res.string.action_import))
                        }
                    }

                is TacoUiState.Importing ->
                    CircularProgressIndicator(progress = { uiState.progress.coerceIn(0f, 1f) })

                TacoUiState.Finished ->
                    Button(onClick = onBack) { Text(stringResource(Res.string.action_done)) }
            }
        }
    }
}
