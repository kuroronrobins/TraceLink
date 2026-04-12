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
        viewModelScope.launch {
            command()
        }
    }
}

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
