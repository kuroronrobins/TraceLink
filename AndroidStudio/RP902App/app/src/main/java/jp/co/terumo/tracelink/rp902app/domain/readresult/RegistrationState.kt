package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * 読取結果登録操作の状態。
 *
 * Inventory 画面の登録状態表示とボタン有効化に使う。pending write の中身そのものは
 * `PendingWriteQueue.pendingWrites` が source of truth。
 */
sealed interface RegistrationState {
    data object Idle : RegistrationState
    data object Registering : RegistrationState
    data class Completed(val sessionId: String) : RegistrationState
    data class Failed(val message: String) : RegistrationState
}
