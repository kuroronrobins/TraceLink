package jp.co.terumo.tracelink.rp902app.domain.upload

/**
 * upload 操作の状態。
 *
 * Inventory 画面の Upload 表示とボタン有効化に使う。retry queue の中身そのものは
 * `UploadRetryQueue.pendingUploads` が source of truth。
 */
sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Completed(val sessionId: String) : UploadState
    data class Failed(val message: String) : UploadState
}
