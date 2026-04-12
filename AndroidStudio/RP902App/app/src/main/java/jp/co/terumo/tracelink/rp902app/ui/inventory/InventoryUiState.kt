package jp.co.terumo.tracelink.rp902app.ui.inventory

import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState

data class InventoryUiState(
    val connectionState: ReaderConnectionState = ReaderConnectionState.Disconnected,
    val isInventoryRunning: Boolean = false,
    val tags: List<InventoryTag> = emptyList(),
    val uploadState: UploadState = UploadState.Idle,
    val pendingUploadCount: Int = 0,
    val logs: List<AppLogEntry> = emptyList(),
)

fun UploadState.displayText(): String = when (this) {
    UploadState.Idle -> "Ready"
    UploadState.Uploading -> "Uploading"
    is UploadState.Completed -> "Completed: $sessionId"
    is UploadState.Failed -> "Failed: $message"
}
