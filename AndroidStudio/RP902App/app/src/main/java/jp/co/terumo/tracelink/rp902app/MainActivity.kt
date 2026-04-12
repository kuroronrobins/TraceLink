package jp.co.terumo.tracelink.rp902app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryRoute
import jp.co.terumo.tracelink.rp902app.ui.inventory.InventoryViewModel
import jp.co.terumo.tracelink.rp902app.ui.theme.TraceLink_RP902AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val inventoryViewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        setContent {
            TraceLink_RP902AppTheme {
                InventoryRoute(viewModel = inventoryViewModel)
            }
        }
    }
}
