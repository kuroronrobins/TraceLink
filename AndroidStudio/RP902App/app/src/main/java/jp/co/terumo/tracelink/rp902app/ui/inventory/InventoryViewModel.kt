package jp.co.terumo.tracelink.rp902app.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepositoryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Inventory 画面用の ViewModel。
 *
 * Repository が持つ domain/data state を、画面が表示しやすい `InventoryUiState` に投影する。
 * ボタン操作はここで coroutine に乗せて Repository へ渡すが、reader SDK や upload transport の
 * 詳細は扱わない。これにより Composable は状態表示と callback 通知だけに集中できる。
 */
class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {
    val uiState: StateFlow<InventoryUiState> = inventoryRepository.state
        .map { repositoryState -> repositoryState.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = inventoryRepository.state.value.toUiState(),
        )

    fun connect() {
        runRepositoryCommand {
            inventoryRepository.connect()
        }
    }

    fun disconnect() {
        runRepositoryCommand {
            inventoryRepository.disconnect()
        }
    }

    fun startInventory() {
        runRepositoryCommand {
            inventoryRepository.startInventory()
        }
    }

    fun stopInventory() {
        runRepositoryCommand {
            inventoryRepository.stopInventory()
        }
    }

    fun clearSession() {
        runRepositoryCommand {
            inventoryRepository.clearSession()
        }
    }

    fun uploadSession() {
        runRepositoryCommand {
            inventoryRepository.uploadSession()
        }
    }

    fun retryPendingUploads() {
        runRepositoryCommand {
            inventoryRepository.retryPendingUploads()
        }
    }

    override fun onCleared() {
        inventoryRepository.close()
        super.onCleared()
    }

    private fun runRepositoryCommand(command: suspend () -> Unit) {
        // 画面操作ごとに viewModelScope で起動する。
        // Repository 側が例外をログ付きで扱うため、UI は状態更新を待つだけでよい。
        viewModelScope.launch {
            command()
        }
    }
}

// UI state は repository state の「表示用コピー」。
// ここで upload pending 件数など、画面で扱いやすい形に変換する。
private fun InventoryRepositoryState.toUiState(): InventoryUiState = InventoryUiState(
    connectionState = connectionState,
    isInventoryRunning = isInventoryRunning,
    tags = tags,
    uploadState = uploadState,
    pendingUploadCount = pendingUploads.size,
    logs = logs,
)

class InventoryViewModelFactory(
    private val inventoryRepository: InventoryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return InventoryViewModel(inventoryRepository = inventoryRepository) as T
    }
}
