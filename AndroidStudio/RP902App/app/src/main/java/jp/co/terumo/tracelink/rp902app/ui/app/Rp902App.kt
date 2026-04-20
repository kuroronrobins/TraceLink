package jp.co.terumo.tracelink.rp902app.ui.app

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import jp.co.terumo.tracelink.rp902app.data.reader.bluetooth.AndroidBluetoothStatusReader
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

/**
 * アプリの画面 shell。
 *
 * 下部 navigation で Inventory / Logs / Settings を切り替え、Android runtime permission の
 * launcher もここで扱う。reader 接続や結果登録の実処理は ViewModel 経由で Repository に渡し、
 * Composable から vendor SDK や data layer を直接触らない構成にしている。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Rp902App(
    inventoryViewModel: InventoryViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by inventoryViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    var selectedRouteName by rememberSaveable { mutableStateOf(AppRoute.Inventory.name) }
    val selectedRoute = AppRoute.valueOf(selectedRouteName)

    // Android の permission dialog は UI から起動する必要がある。
    // ただし、許可結果は app-owned な ReaderRuntimeState に変換してから gateway preflight で使う。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        settingsViewModel.updatePermissionSnapshot(grantResults)
        settingsViewModel.updateBluetoothState(AndroidBluetoothStatusReader.read(context))
    }

    // Settings 画面の表示と real gateway の接続可否判定が同じ情報を見るよう、
    // permission と Bluetooth 状態は ViewModel の runtime state に集約する。
    val refreshPreflight = {
        val grantResults = settingsUiState.requiredManifestPermissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        settingsViewModel.updatePermissionSnapshot(grantResults)
        settingsViewModel.updateBluetoothState(AndroidBluetoothStatusReader.read(context))
    }

    LaunchedEffect(settingsUiState.requiredManifestPermissions) {
        refreshPreflight()
    }

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
                onRegister = inventoryViewModel::registerCurrentSessionResults,
                onRetryPendingWrites = inventoryViewModel::retryPendingWrites,
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
                onRequestPermissions = {
                    permissionLauncher.launch(settingsUiState.missingManifestPermissions.toTypedArray())
                },
                onRefreshPreflight = refreshPreflight,
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
