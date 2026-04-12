package jp.co.terumo.tracelink.rp902app.domain.upload

/**
 * 送信に失敗し、再送待ちになっている upload payload。
 *
 * 現在の in-memory queue では `id` に `sessionId` を使い、同じ session の失敗を重複登録しない。
 */
data class PendingUpload(
    val id: String,
    val payload: InventoryUploadPayload,
    val queuedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorMessage: String,
)
