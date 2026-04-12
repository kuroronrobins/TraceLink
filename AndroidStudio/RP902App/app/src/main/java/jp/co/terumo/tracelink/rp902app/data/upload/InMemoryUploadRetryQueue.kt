package jp.co.terumo.tracelink.rp902app.data.upload

import jp.co.terumo.tracelink.rp902app.domain.upload.InventoryUploadPayload
import jp.co.terumo.tracelink.rp902app.domain.upload.PendingUpload
import jp.co.terumo.tracelink.rp902app.domain.upload.UploadRetryQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * upload 失敗 payload をメモリ上に保持する retry queue。
 *
 * アプリ再起動で消えるため production 向け永続化ではない。契約を分けているので、
 * 長期運用ではこの class を DataStore/Room などの実装に差し替える。
 */
class InMemoryUploadRetryQueue : UploadRetryQueue {
    private val _pendingUploads = MutableStateFlow<List<PendingUpload>>(emptyList())
    override val pendingUploads: StateFlow<List<PendingUpload>> = _pendingUploads.asStateFlow()

    override suspend fun enqueueFailure(
        payload: InventoryUploadPayload,
        failedAtEpochMillis: Long,
        message: String,
    ) {
        // sessionId を local de-duplication key として扱う。
        // 同じ session の再失敗は queue entry を増やさず attemptCount を更新する。
        val pendingUploadId = payload.sessionId
        _pendingUploads.update { current ->
            if (current.any { pending -> pending.id == pendingUploadId }) {
                current.map { pending ->
                    if (pending.id == pendingUploadId) {
                        pending.copy(
                            lastAttemptAtEpochMillis = failedAtEpochMillis,
                            attemptCount = pending.attemptCount + 1,
                            lastErrorMessage = message,
                        )
                    } else {
                        pending
                    }
                }
            } else {
                current + PendingUpload(
                    id = pendingUploadId,
                    payload = payload,
                    queuedAtEpochMillis = failedAtEpochMillis,
                    lastAttemptAtEpochMillis = failedAtEpochMillis,
                    attemptCount = 1,
                    lastErrorMessage = message,
                )
            }
        }
    }

    override suspend fun markAttemptFailed(
        pendingUploadId: String,
        failedAtEpochMillis: Long,
        message: String,
    ) {
        _pendingUploads.update { current ->
            current.map { pending ->
                if (pending.id == pendingUploadId) {
                    pending.copy(
                        lastAttemptAtEpochMillis = failedAtEpochMillis,
                        attemptCount = pending.attemptCount + 1,
                        lastErrorMessage = message,
                    )
                } else {
                    pending
                }
            }
        }
    }

    override suspend fun remove(pendingUploadId: String) {
        _pendingUploads.update { current ->
            current.filterNot { pending -> pending.id == pendingUploadId }
        }
    }

    override suspend fun clear() {
        _pendingUploads.value = emptyList()
    }
}
