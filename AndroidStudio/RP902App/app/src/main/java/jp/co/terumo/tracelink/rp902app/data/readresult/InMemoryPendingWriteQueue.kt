package jp.co.terumo.tracelink.rp902app.data.readresult

import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.PendingWrite
import jp.co.terumo.tracelink.rp902app.domain.readresult.PendingWriteQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 結果登録に失敗した bundle をメモリ上に保持する pending write queue。
 *
 * アプリ再起動で消えるため production 向け永続化ではない。契約を分けているので、
 * 長期運用ではこの class を Room などの実装に差し替える。
 */
class InMemoryPendingWriteQueue : PendingWriteQueue {
    private val _pendingWrites = MutableStateFlow<List<PendingWrite>>(emptyList())
    override val pendingWrites: StateFlow<List<PendingWrite>> = _pendingWrites.asStateFlow()

    override suspend fun enqueueFailure(
        bundle: ReadResultRegistrationBundle,
        failedAtEpochMillis: Long,
        message: String,
    ) {
        // DB と同じ idempotency key で local queue も重複を抑止する。
        val pendingWriteId = bundle.idempotencyKey
        _pendingWrites.update { current ->
            if (current.any { pending -> pending.id == pendingWriteId }) {
                current.map { pending ->
                    if (pending.id == pendingWriteId) {
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
                current + PendingWrite(
                    id = pendingWriteId,
                    bundle = bundle,
                    queuedAtEpochMillis = failedAtEpochMillis,
                    lastAttemptAtEpochMillis = failedAtEpochMillis,
                    attemptCount = 1,
                    lastErrorMessage = message,
                )
            }
        }
    }

    override suspend fun markAttemptFailed(
        pendingWriteId: String,
        failedAtEpochMillis: Long,
        message: String,
    ) {
        _pendingWrites.update { current ->
            current.map { pending ->
                if (pending.id == pendingWriteId) {
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

    override suspend fun remove(pendingWriteId: String) {
        _pendingWrites.update { current ->
            current.filterNot { pending -> pending.id == pendingWriteId }
        }
    }

    override suspend fun clear() {
        _pendingWrites.value = emptyList()
    }
}
