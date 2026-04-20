package jp.co.terumo.tracelink.rp902app.ui.inventory

import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryTag
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogEntry
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderConnectionState
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationState

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
    val registrationState: RegistrationState = RegistrationState.Idle,
    val pendingWriteCount: Int = 0,
    val logs: List<AppLogEntry> = emptyList(),
)

fun RegistrationState.displayText(): String = when (this) {
    RegistrationState.Idle -> "Ready"
    RegistrationState.Registering -> "Registering"
    is RegistrationState.Completed -> "Completed: $sessionId"
    is RegistrationState.Failed -> "Failed: $message"
}
