package jp.co.terumo.tracelink.rp902app.domain.upload

data class PendingUpload(
    val id: String,
    val payload: InventoryUploadPayload,
    val queuedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorMessage: String,
)
