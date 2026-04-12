package jp.co.terumo.tracelink.rp902app.domain.upload

import kotlinx.coroutines.flow.StateFlow

/**
 * upload 失敗 payload をあとで再送するための queue 契約。
 *
 * 現在は in-memory 実装だが、長期運用では DataStore や Room などの durable storage に
 * 差し替える想定。Repository からはこの契約だけを見せる。
 */
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
