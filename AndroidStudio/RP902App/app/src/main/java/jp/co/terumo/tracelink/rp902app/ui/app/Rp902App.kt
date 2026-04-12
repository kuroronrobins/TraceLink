package jp.co.terumo.tracelink.rp902app.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryScreen
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModel
import jp.co.terumo.tracelink.rp902app.ui.logs.LogsScreen
import jp.co.terumo.tracelink.rp902app.ui.settings.SettingsScreen
import jp.co.terumo.tracelink.rp902app.ui.settings.SettingsViewModel

private enum class AppRoute(
    val label: String,
    val title: String,
) {
    Inventory(
        label = "Inventory",
        title = "Inventory session",
    ),
    Logs(
        label = "Logs",
        title = "Event log",
    ),
    Settings(
        label = "Settings",
        title = "Reader settings",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Rp902App(
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by inventoryViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    var selectedRouteName by rememberSaveable { mutableStateOf(AppRoute.Inventory.name) }
    val selectedRoute = AppRoute.valueOf(selectedRouteName)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TraceLink RP902")
                        Text(
                            text = selectedRoute.title,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppRoute.entries.forEach { route ->
                    NavigationBarItem(
                        selected = selectedRoute == route,
                        onClick = { selectedRouteName = route.name },
                        icon = { Text(route.label.take(1)) },
                        label = { Text(route.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedRoute) {
            AppRoute.Inventory -> InventoryScreen(
                uiState = uiState,
                onConnect = inventoryViewModel::connect,
                onDisconnect = inventoryViewModel::disconnect,
                onStartInventory = inventoryViewModel::startInventory,
                onStopInventory = inventoryViewModel::stopInventory,
                onUpload = inventoryViewModel::uploadSession,
                onRetryPendingUploads = inventoryViewModel::retryPendingUploads,
                onClearSession = inventoryViewModel::clearSession,
                modifier = Modifier.padding(innerPadding),
            )

            AppRoute.Logs -> LogsScreen(
                logs = uiState.logs,
                modifier = Modifier.padding(innerPadding),
            )

            AppRoute.Settings -> SettingsScreen(
                uiState = settingsUiState,
                onGatewayModeChange = settingsViewModel::updateGatewayMode,
                onReaderAddressChange = settingsViewModel::updateReaderAddressInput,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
