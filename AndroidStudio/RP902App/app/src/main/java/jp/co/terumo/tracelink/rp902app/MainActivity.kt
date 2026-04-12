package jp.co.terumo.tracelink.rp902app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import jp.co.terumo.tracelink.rp902app.data.AppContainer
import jp.co.terumo.tracelink.rp902app.ui.app.Rp902App
import jp.co.terumo.tracelink.rp902app.ui.theme.TraceLink_RP902AppTheme
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModel
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = AppContainer()
        val inventoryViewModel = ViewModelProvider(
            this,
            InventoryViewModelFactory(appContainer.inventoryRepository()),
        )[InventoryViewModel::class.java]
        setContent {
            TraceLink_RP902AppTheme {
                Rp902App(inventoryViewModel = inventoryViewModel)
            }
        }
    }
}
