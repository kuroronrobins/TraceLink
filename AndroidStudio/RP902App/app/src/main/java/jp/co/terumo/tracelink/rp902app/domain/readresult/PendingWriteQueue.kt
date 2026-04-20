package jp.co.terumo.tracelink.rp902app.domain.readresult

import kotlinx.coroutines.flow.StateFlow

/**
 * PostgreSQL への結果登録に失敗した bundle をあとで再実行するための queue 契約。
 *
 * 現在は in-memory 実装だが、長期運用では Room などの durable storage に差し替える想定。
 * Repository からはこの契約だけを見せる。
 */
interface PendingWriteQueue {
    val pendingWrites: StateFlow<List<PendingWrite>>

    suspend fun enqueueFailure(
        bundle: ReadResultRegistrationBundle,
        failedAtEpochMillis: Long,
        message: String,
    )

    suspend fun markAttemptFailed(
        pendingWriteId: String,
        failedAtEpochMillis: Long,
        message: String,
    )

    suspend fun remove(pendingWriteId: String)

    suspend fun clear()
}
