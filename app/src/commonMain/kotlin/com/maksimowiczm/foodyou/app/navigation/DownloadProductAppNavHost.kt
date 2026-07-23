package com.maksimowiczm.foodyou.app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maksimowiczm.foodyou.app.navigation.DownloadProductAppNavHost.CreateProduct
import com.maksimowiczm.foodyou.app.navigation.DownloadProductAppNavHost.OpenFoodFactsLogin
import com.maksimowiczm.foodyou.app.navigation.DownloadProductAppNavHost.UsdaApiKey
import com.maksimowiczm.foodyou.app.navigation.DownloadProductAppNavHost.DownloadAiSettings
import com.maksimowiczm.foodyou.app.ui.database.externaldatabases.OpenFoodFactsLoginDialog
import com.maksimowiczm.foodyou.app.ui.database.externaldatabases.UpdateUsdaApiKeyDialog
import com.maksimowiczm.foodyou.app.ui.ai.AiSettingsScreen
import com.maksimowiczm.foodyou.app.ui.food.product.CreateProductScreen
import com.maksimowiczm.foodyou.ai.domain.repository.AiCredentialsRepository
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Composable
fun DownloadProductAppNavHost(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    url: String,
    modifier: Modifier = Modifier.Companion,
) {
    val navController = rememberNavController()
    val aiCredentialsRepository: AiCredentialsRepository = koinInject()
    val aiCredentials by aiCredentialsRepository.observeCredentials().collectAsStateWithLifecycle(null)

    NavHost(
        navController = navController,
        startDestination = CreateProduct(url),
        modifier = modifier,
    ) {
        dialog<UsdaApiKey> {
            UpdateUsdaApiKeyDialog(
                onDismissRequest = { navController.popBackStackInclusive<UsdaApiKey>() },
                onSave = { navController.popBackStackInclusive<UsdaApiKey>() },
            )
        }
        dialog<OpenFoodFactsLogin> {
            OpenFoodFactsLoginDialog(
                onDismissRequest = { navController.popBackStackInclusive<OpenFoodFactsLogin>() },
                onSave = { navController.popBackStackInclusive<OpenFoodFactsLogin>() },
            )
        }
        forwardBackwardComposable<DownloadAiSettings> {
            AiSettingsScreen(onBack = { navController.popBackStackInclusive<DownloadAiSettings>() })
        }
        forwardBackwardComposable<CreateProduct> {
            val (url) = it.toRoute<CreateProduct>()

            CreateProductScreen(
                onBack = onBack,
                onCreate = { onCreate() },
                onUpdateUsdaApiKey = { navController.navigateSingleTop(UsdaApiKey) },
                onUpdateOpenFoodFactsCredentials = {
                    navController.navigateSingleTop(OpenFoodFactsLogin)
                },
                onAiSettings = { navController.navigateSingleTop(DownloadAiSettings) },
                hasAiCredentials = aiCredentials != null,
                url = url,
            )
        }
    }
}

private object DownloadProductAppNavHost {
    @Serializable object UsdaApiKey

    @Serializable data class CreateProduct(val url: String)

    @Serializable object OpenFoodFactsLogin

    @Serializable object DownloadAiSettings
}
