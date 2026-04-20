package jp.co.terumo.tracelink.rp902app.domain.readresult

/**
 * PostgreSQL への結果登録に失敗し、再実行待ちになっている書き込み。
 *
 * 現在の in-memory queue では `id` に `(deviceId, sessionId)` の文字列表現を使い、
 * PostgreSQL の冪等性 key と同じ単位で重複登録を避ける。
 */
data class PendingWrite(
    val id: String,
    val bundle: ReadResultRegistrationBundle,
    val queuedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorMessage: String,
)
