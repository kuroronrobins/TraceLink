package jp.co.terumo.tracelink.rp902app.ui.inventory

import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadState

/**
 * Inventory 画面が表示するための状態。
 *
 * domain/data の状態を ViewModel が投影したもので、画面固有の表示都合だけを持つ。
 * 真の状態更新は Repository 側で行う。
 */
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
