package jp.co.terumo.tracelink.rp902app.domain.upload

import kotlinx.coroutines.flow.StateFlow

interface UploadRetryQueue {
    val pendingUploads: StateFlow<List<PendingUpload>>

    suspend fun enqueueFailure(
        payload: InventoryUploadPayload,
        failedAtEpochMillis: Long,
        message: String,
    )

    suspend fun markAttemptFailed(
        pendingUploadId: String,
        failedAtEpochMillis: Long,
        message: String,
    )

    suspend fun remove(pendingUploadId: String)

    suspend fun clear()
}
