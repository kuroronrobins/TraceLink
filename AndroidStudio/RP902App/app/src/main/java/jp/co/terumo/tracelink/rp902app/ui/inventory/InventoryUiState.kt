package jp.co.terumo.tracelink.rp902app.ui.inventory

import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState

data class InventoryUiState(
    val connectionState: ReaderConnectionState = ReaderConnectionState.Disconnected,
    val isInventoryRunning: Boolean = false,
    val tags: List<InventoryTag> = emptyList(),
    val uploadState: UploadState = UploadState.Idle,
    val logs: List<String> = emptyList(),
)

sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Completed(val sessionId: String) : UploadState
    data class Failed(val message: String) : UploadState
}

fun UploadState.displayText(): String = when (this) {
    UploadState.Idle -> "Ready"
    UploadState.Uploading -> "Uploading"
    is UploadState.Completed -> "Completed: $sessionId"
    is UploadState.Failed -> "Failed: $message"
}

