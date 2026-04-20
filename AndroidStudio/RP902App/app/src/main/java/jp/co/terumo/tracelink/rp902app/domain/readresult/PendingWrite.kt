package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * PostgreSQL への結果登録に失敗し、再実行待ちになっている書き込み。
 *
 * 現在の in-memory queue では `id` に `sessionId` を使い、同じ session の失敗を重複登録しない。
 */
data class PendingWrite(
    val id: String,
    val bundle: ReadResultRegistrationBundle,
    val queuedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorMessage: String,
)
