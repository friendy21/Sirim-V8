package com.sirim.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sirim.scanner.data.AppContainer
import com.sirim.scanner.data.preferences.StartupPage
import com.sirim.scanner.ui.common.AuthenticationDialog
import com.sirim.scanner.ui.screens.feedback.FeedbackScreen
import com.sirim.scanner.ui.screens.qrcode.QrScannerScreen
import com.sirim.scanner.ui.screens.settings.SettingsScreen
import com.sirim.scanner.ui.screens.sku.SkuRecordFormScreen
import com.sirim.scanner.ui.screens.sku.SkuRecordFormViewModel
import com.sirim.scanner.ui.screens.sku.SkuScannerScreen
import com.sirim.scanner.ui.screens.storage.StorageHubScreen
import com.sirim.scanner.ui.screens.storage.StorageHubViewModel
import com.sirim.scanner.ui.screens.startup.StartupScreen
import com.sirim.scanner.ui.theme.SirimScannerTheme
import com.sirim.scanner.ui.viewmodel.PreferencesViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy {
        (application as SirimScannerApplication).container
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SirimScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SirimApp(container = container)
                }
            }
        }
    }
}

sealed class Destinations(val route: String) {
    data object StartupResolver : Destinations("startup_resolver")
    data object Startup : Destinations("startup")
    data object QrScanner : Destinations("qr_scanner")
    data object SkuScanner : Destinations("sku_scanner")
    data object Storage : Destinations("storage")
    data object SkuRecordForm : Destinations("sku_record_form")
    data object Settings : Destinations("settings")
    data object Feedback : Destinations("feedback")
}

@Composable
fun SirimApp(container: AppContainer) {
    val navController = rememberNavController()
    NavGraph(container = container, navController = navController)
}

@Composable
private fun NavGraph(container: AppContainer, navController: NavHostController) {
    val preferencesViewModel: PreferencesViewModel = viewModel(
        factory = PreferencesViewModel.Factory(container.preferencesManager)
    )
    val preferences by preferencesViewModel.preferences.collectAsState()
    val isSessionValid by preferencesViewModel.isSessionValid.collectAsState()
    val authError by preferencesViewModel.authError.collectAsState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestAuthentication(afterAuth: () -> Unit, forcePrompt: Boolean = false) {
        if (!forcePrompt && isSessionValid) {
            afterAuth()
        } else {
            if (forcePrompt && isSessionValid) {
                preferencesViewModel.logout()
            }
            pendingAction = afterAuth
            showAuthDialog = true
        }
    }

    LaunchedEffect(isSessionValid) {
        if (isSessionValid && pendingAction != null) {
            pendingAction?.invoke()
            pendingAction = null
            showAuthDialog = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            preferencesViewModel.checkSessionExpiry()
        }
    }
    NavHost(navController = navController, startDestination = Destinations.StartupResolver.route) {
        composable(Destinations.StartupResolver.route) {
            LaunchedEffect(preferences.startupPage) {
                val target = when (preferences.startupPage) {
                    StartupPage.AskEveryTime -> Destinations.Startup.route
                    StartupPage.SirimScannerV2 -> Destinations.QrScanner.route
                    StartupPage.SkuScanner -> Destinations.SkuScanner.route
                    StartupPage.Storage -> Destinations.Storage.route
                }
                navController.navigate(target) {
                    popUpTo(Destinations.StartupResolver.route) { inclusive = true }
                }
            }
        }
        composable(Destinations.Startup.route) {
            StartupScreen(
                onOpenQrScanner = { navController.navigate(Destinations.QrScanner.route) },
                onOpenSkuScanner = { navController.navigate(Destinations.SkuScanner.route) },
                onOpenStorage = { navController.navigate(Destinations.Storage.route) },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) }
            )
        }
        composable(Destinations.QrScanner.route) {
            QrScannerScreen(
                onBack = { navController.popBackStack() },
                onRecordSaved = { _ ->
                    navController.navigate(Destinations.Storage.route) {
                        popUpTo(Destinations.QrScanner.route) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Destinations.Settings.route) },
                repository = container.repository,
                analyzer = container.qrAnalyzer,
                sessionTracker = container.preferencesManager
            )
        }
        composable(Destinations.SkuScanner.route) {
            SkuScannerScreen(
                onBack = { navController.popBackStack() },
                onRecordSaved = { recordId ->
                    navController.navigate("${Destinations.SkuRecordForm.route}?recordId=$recordId") {
                        popUpTo(Destinations.SkuScanner.route) { inclusive = true }
                    }
                },
                repository = container.repository,
                analyzer = container.barcodeAnalyzer,
                appScope = container.applicationScope,
                exportManager = container.exportManager,
                sessionTracker = container.preferencesManager
            )
        }
        composable(
            route = Destinations.SkuRecordForm.route + "?recordId={recordId}",
            arguments = listOf(
                navArgument("recordId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val viewModel: SkuRecordFormViewModel = viewModel(
                factory = SkuRecordFormViewModel.Factory(container.repository)
            )
            val recordId = backStackEntry.arguments?.getLong("recordId")?.takeIf { it > 0 }
            SkuRecordFormScreen(
                viewModel = viewModel,
                recordId = recordId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onRetake = {
                    navController.navigate(Destinations.SkuScanner.route) {
                        popUpTo(Destinations.SkuRecordForm.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Destinations.Storage.route) {
            val viewModel: StorageHubViewModel = viewModel(
                factory = StorageHubViewModel.Factory(container.repository)
            )
            StorageHubScreen(
                viewModel = viewModel,
                onRequireAuthentication = { forcePrompt, action ->
                    requestAuthentication(action, forcePrompt)
                },
                onBack = { navController.popBackStack() },
                onOpenQrScanner = {
                    navController.navigate(Destinations.QrScanner.route)
                }
            )
        }
        composable(Destinations.Settings.route) {
            SettingsScreen(
                preferences = preferences,
                authError = authError,
                onStartupSelected = preferencesViewModel::setStartupPage,
                onAuthenticate = { username, password ->
                    preferencesViewModel.authenticate(username, password)
                },
                onLogout = preferencesViewModel::logout,
                onDismissAuthError = preferencesViewModel::clearAuthError,
                onBack = { navController.popBackStack() },
                onOpenFeedback = { navController.navigate(Destinations.Feedback.route) }
            )
        }
        composable(Destinations.Feedback.route) {
            FeedbackScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }

    AuthenticationDialog(
        visible = showAuthDialog,
        error = authError,
        onDismiss = {
            showAuthDialog = false
            pendingAction = null
            preferencesViewModel.clearAuthError()
        },
        onAuthenticate = { username, password ->
            preferencesViewModel.authenticate(username, password)
        }
    )
}
