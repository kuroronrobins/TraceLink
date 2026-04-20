package jp.co.terumo.tracelink.rp902app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import jp.co.terumo.tracelink.rp902app.data.AppContainerFactory
import jp.co.terumo.tracelink.rp902app.ui.app.Rp902App
import jp.co.terumo.tracelink.rp902app.ui.theme.TraceLink_RP902AppTheme
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModel
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModelFactory
import jp.co.terumo.tracelink.rp902app.ui.settings.SettingsViewModel
import jp.co.terumo.tracelink.rp902app.ui.settings.SettingsViewModelFactory

/**
 * Android アプリの起動点。
 *
 * ここでは依存関係を `AppContainer` から受け取り、画面に渡す ViewModel を作るだけに留める。
 * reader 接続、inventory、結果登録などの実処理を Activity に置かないことで、
 * 画面のライフサイクルと業務ロジックが混ざるのを避けている。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // このアプリではまだ DI framework を入れていないため、
        // AppContainerFactory は通常 fake mode を返し、debug smoke だけ PostgreSQL mode に切り替える。
        val appContainer = AppContainerFactory.create()
        val inventoryViewModel = ViewModelProvider(
            this,
            InventoryViewModelFactory(appContainer.inventoryRepository()),
        )[InventoryViewModel::class.java]
        val settingsViewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(
                readerSettingsRepository = appContainer.readerSettingsRepository(),
                readerRuntimeStateRepository = appContainer.readerRuntimeStateRepository(),
            ),
        )[SettingsViewModel::class.java]
        setContent {
            TraceLink_RP902AppTheme {
                Rp902App(
                    inventoryViewModel = inventoryViewModel,
                    settingsViewModel = settingsViewModel,
                )
            }
        }
    }
}
